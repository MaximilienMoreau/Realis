#!/usr/bin/env python3
"""Expire Realis snapshots independently of whether new backups succeed."""
import argparse
from datetime import datetime, timedelta, timezone
from pathlib import Path


def prune(directory: Path, now=None):
    cutoff = (now or datetime.now(timezone.utc)) - timedelta(days=30)
    for path in directory.glob('realis-*.tar.gpg'):
        try:
            date = datetime.strptime(path.name, 'realis-%Y%m%dT%H%M%SZ.tar.gpg').replace(tzinfo=timezone.utc)
        except ValueError:
            continue
        if date < cutoff and path.is_file() and not path.is_symlink():
            path.unlink()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    prune(parser.parse_args().directory)
