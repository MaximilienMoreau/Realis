#!/usr/bin/env python3
"""Consistent, encrypted Compose backup. Run from the repository root."""
import argparse
import datetime as dt
import os
from pathlib import Path
import subprocess
import tempfile
import tarfile


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--passphrase-file', type=Path, required=True)
    args = parser.parse_args()
    os.umask(0o077)
    if not args.passphrase_file.is_file() or not Path('.env').is_file():
        parser.error('A passphrase file and the deployment .env are required.')
    if len(args.passphrase_file.read_bytes().strip()) < 32:
        parser.error('Use a random backup passphrase of at least 32 bytes.')
    destination = args.destination.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    final = destination / f'realis-{stamp}.tar.gpg'
    partial = final.with_suffix('.partial')
    # Stop writers so PostgreSQL and captures represent the same state.
    running = subprocess.check_output(['docker', 'compose', 'ps', '--status', 'running', '--services'], text=True).split()
    restart = 'backend' in running
    with tempfile.TemporaryDirectory(prefix='realis-backup-') as temp:
        stage = Path(temp)
        try:
            if restart:
                run(['docker', 'compose', 'stop', 'backend'])
            with (stage / 'database.dump').open('wb') as out:
                run(['docker', 'compose', 'exec', '-T', 'postgres', 'sh', '-c',
                     'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc'], stdout=out)
            with (stage / 'captures.tar.gz').open('wb') as out:
                run(['docker', 'compose', 'run', '--rm', '--no-deps', '-T', '--entrypoint', 'tar',
                     'backend', '-C', '/data/captures', '-czf', '-', '.'], stdout=out)
            # Configuration includes the encryption key: only ship in the encrypted archive.
            (stage / 'deployment.env').write_bytes(Path('.env').read_bytes())
            (stage / 'revision.txt').write_text(subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True))
            with tarfile.open(stage / 'snapshot.tar', 'w') as archive:
                for name in ('database.dump', 'captures.tar.gz', 'deployment.env', 'revision.txt'):
                    archive.add(stage / name, arcname=name)
            run(['gpg', '--batch', '--yes', '--pinentry-mode', 'loopback', '--passphrase-file', str(args.passphrase_file.resolve()),
                 '--symmetric', '--cipher-algo', 'AES256', '--output', str(partial), str(stage / 'snapshot.tar')])
            partial.replace(final)
        finally:
            partial.unlink(missing_ok=True)
            if restart:
                run(['docker', 'compose', 'start', 'backend'])
    # Bound the lifetime of this tool's snapshots, including copies on remote storage (see operations.md).
    cutoff = dt.datetime.now().timestamp() - 30 * 86400
    for item in destination.glob('realis-*.tar.gpg'):
        if item.stat().st_mtime < cutoff:
            item.unlink()
    print(f'Encrypted snapshot: {final}')

if __name__ == '__main__':
    main()
