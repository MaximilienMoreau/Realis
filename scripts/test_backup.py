"""Exercise archive round-trip with real GnuPG; Docker I/O is isolated using fixtures."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).parent / f'{name}.py')
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


class BackupTest(unittest.TestCase):
    def test_encrypted_backup_and_restore_keep_key_and_capture(self):
        backup, restore = module('backup'), module('restore')
        real_run = subprocess.run
        calls = []
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'gnupg').mkdir(mode=0o700)
            (root / '.env').write_text('ENCRYPTION_KEY=test-key-not-a-production-secret\n')
            (root / 'passphrase').write_text('test-backup-passphrase-longer-than-thirty-two-bytes')
            captures = io.BytesIO()
            with tarfile.open(fileobj=captures, mode='w:gz') as archive:
                info = tarfile.TarInfo('user/capture.enc'); info.size = 7
                archive.addfile(info, io.BytesIO(b'capture'))
            def fake_run(args, **kwargs):
                calls.append(args)
                if args[0] != 'docker':
                    return real_run(args, **kwargs)
                if args[-1].startswith('pg_dump'):
                    kwargs['stdout'].write(b'postgres-test-dump')
                elif '-czf' in args:
                    kwargs['stdout'].write(captures.getvalue())
                elif args[-1].startswith('pg_restore'):
                    self.assertEqual(kwargs['stdin'].read(), b'postgres-test-dump')
                elif '-xzf' in args:
                    self.assertEqual(kwargs['stdin'].read(), captures.getvalue())
                return subprocess.CompletedProcess(args, 0)
            previous = Path.cwd()
            try:
                os.chdir(root)
                with patch.dict(os.environ, {'GNUPGHOME': str(root / 'gnupg')}), \
                     patch('subprocess.run', side_effect=fake_run), \
                     patch('subprocess.check_output', side_effect=lambda args, **kw: 'backend\npostgres\n' if args[0] == 'docker' else 'test-revision\n'), \
                     patch.object(sys, 'argv', ['backup', '--destination', str(root / 'backups'), '--passphrase-file', str(root / 'passphrase')]), \
                     contextlib.redirect_stdout(io.StringIO()):
                    backup.main()
                    archive = next((root / 'backups').glob('*.gpg'))
                    self.assertNotIn(b'test-key', archive.read_bytes())
                    self.assertFalse(list((root / 'backups').glob('*.partial')))
                    with patch.object(sys, 'argv', ['restore', str(archive), '--passphrase-file', str(root / 'passphrase'), '--confirm-replace-database']):
                        restore.main()
                    self.assertEqual((root / 'restored-deployment.env').read_text(), (root / '.env').read_text())
                self.assertIn(['docker', 'compose', 'start', 'backend'], calls)
                self.assertEqual(sum(c == ['docker', 'compose', 'start', 'backend'] for c in calls), 1)
            finally:
                os.chdir(previous)

    def test_retention_uses_snapshot_date_and_preserves_other_files(self):
        from datetime import datetime, timezone
        prune = module('prune_backups')
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            old = root / 'realis-20260101T000000Z.tar.gpg'; old.write_bytes(b'x')
            fresh = root / 'realis-20260215T000000Z.tar.gpg'; fresh.write_bytes(b'y')
            other = root / 'other.gpg'; other.write_bytes(b'z')
            prune.prune(root, datetime(2026, 2, 20, tzinfo=timezone.utc))
            self.assertFalse(old.exists()); self.assertTrue(fresh.exists()); self.assertTrue(other.exists())

    def test_backup_failure_restarts_backend_and_publishes_nothing(self):
        backup = module('backup')
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); (root / '.env').write_text('secret=fake'); (root / 'key').write_text('x' * 48)
            calls = []
            def fail_dump(args, **kw):
                calls.append(args)
                if args[-1].startswith('pg_dump'):
                    raise subprocess.CalledProcessError(1, args)
            previous = Path.cwd()
            try:
                os.chdir(root)
                with patch.object(backup, 'run', side_effect=fail_dump), patch('subprocess.check_output', return_value='backend\n'), \
                     patch.object(sys, 'argv', ['backup', '--destination', str(root / 'backups'), '--passphrase-file', str(root / 'key')]):
                    with self.assertRaises(subprocess.CalledProcessError): backup.main()
                self.assertIn(['docker', 'compose', 'start', 'backend'], calls)
                self.assertEqual(list((root / 'backups').iterdir()), [])
            finally:
                os.chdir(previous)

if __name__ == '__main__':
    unittest.main()
