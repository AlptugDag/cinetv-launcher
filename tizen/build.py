#!/usr/bin/env python3
"""Builds CineTV for Samsung TVs (Tizen): CineTV-tizen.wgt, the app package the TV installs.

    python3 tizen/build.py                 # → CineTV-tizen.wgt, ready for install
    python3 tizen/build.py --sign PROFILE  # signed with a certificate profile
"""
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TIZEN_DIR = os.path.join(ROOT, 'tizen')
WGT = os.path.join(ROOT, 'CineTV-tizen.wgt')
REMOTE_ASSETS = os.path.join(ROOT, 'launcher', 'assets', 'remote')


def build():
    print('Packaging CineTV for Samsung Tizen...')
    with zipfile.ZipFile(WGT, 'w', zipfile.ZIP_DEFLATED) as z:
        # 1. Tizen manifest and icon
        z.write(os.path.join(TIZEN_DIR, 'config.xml'), 'config.xml')
        z.write(os.path.join(TIZEN_DIR, 'icon.png'), 'icon.png')
        
        # 2. Web launcher bundle
        if os.path.exists(REMOTE_ASSETS):
            for root, _, files in os.walk(REMOTE_ASSETS):
                for f in files:
                    if f == 'icon.png' and root == REMOTE_ASSETS:
                        continue
                    full = os.path.join(root, f)
                    rel = os.path.relpath(full, REMOTE_ASSETS)
                    z.write(full, rel)
                    
    print(f'Successfully built {os.path.basename(WGT)} ({os.path.getsize(WGT) // 1024} KB)')
    print('Ready to install via: python3 tizen/install.py <SAMSUNG_TV_IP>')


if __name__ == '__main__':
    build()
