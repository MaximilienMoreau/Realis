#!/usr/bin/env python3
"""Restore a Realis backup to an isolated Compose deployment; leave the application stopped."""
import argparse
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('backup', type=Path)
    parser.add_argument('--passphrase-file', type=Path, required=True)
    parser.add_argument('--confirm-replace-database', action='store_true')
    args = parser.parse_args()
    if not args.confirm_replace_database:
        parser.error('Restoration replaces the target database. Use an isolated deployment and --confirm-replace-database.')
    os.umask(0o077)
    run = lambda argv, **kw: subprocess.run(argv, check=True, **kw)
    with tempfile.TemporaryDirectory(prefix='realis-restore-') as temp:
        stage = Path(temp)
        run(['gpg', '--batch', '--pinentry-mode', 'loopback', '--passphrase-file', str(args.passphrase_file.resolve()),
             '--output', str(stage / 'snapshot.tar'), '--decrypt', str(args.backup.resolve())])
        with tarfile.open(stage / 'snapshot.tar') as archive:
            expected = {'database.dump', 'captures.tar.gz', 'deployment.env', 'revision.txt'}
            members = archive.getmembers()
            if {m.name for m in members} != expected or any(not m.isfile() for m in members):
                raise ValueError('Unexpected archive contents')
            for member in members:
                source = archive.extractfile(member)
                with (stage / member.name).open('wb') as out:
                    import shutil
                    shutil.copyfileobj(source, out)
        with tarfile.open(stage / 'captures.tar.gz') as captures:
            for member in captures.getmembers():
                parts = Path(member.name).parts
                if Path(member.name).is_absolute() or '..' in parts or not (member.isfile() or member.isdir()):
                    raise ValueError('Unsafe capture archive')
        if Path('restored-deployment.env').exists():
            raise ValueError('Review or remove the previous restored-deployment.env before restoring again')
        run(['docker', 'compose', 'run', '--rm', '--no-deps', '-T', '--entrypoint', 'sh', 'backend', '-c',
             'test -z "$(find /data/captures -mindepth 1 -print -quit)"'])
        run(['docker', 'compose', 'stop', 'frontend', 'backend'])
        # Do not overwrite current secrets automatically: save the source configuration for review.
        config = Path('restored-deployment.env')
        with config.open('xb') as out:
            out.write((stage / 'deployment.env').read_bytes())
        with (stage / 'database.dump').open('rb') as source:
            run(['docker', 'compose', 'exec', '-T', 'postgres', 'sh', '-c',
                 'pg_restore --clean --if-exists --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], stdin=source)
        with (stage / 'captures.tar.gz').open('rb') as source:
            run(['docker', 'compose', 'run', '--rm', '--no-deps', '-T', '--entrypoint', 'tar',
                 'backend', '-C', '/data/captures', '-xzf', '-'], stdin=source)
    print('Restored; application remains stopped. Match the encryption key with restored-deployment.env,')
    print('reapply deletions since the snapshot, verify file hashes and timestamps, then reopen service.')

if __name__ == '__main__':
    main()
