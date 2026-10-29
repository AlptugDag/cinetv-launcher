#!/usr/bin/env python3
"""Installs CineTV on a Samsung Smart TV (Tizen) over Wi-Fi without Tizen Studio or Samsung SDB.

    python3 tizen/install.py 192.168.1.50              # install/update and launch
    python3 tizen/install.py 192.168.1.50 --uninstall  # remove

Before installing:
1. On Samsung TV: Open Apps, press 1 2 3 4 5 on the remote to open Developer Mode.
2. Toggle Developer Mode ON, enter your computer's local IP address as Host PC IP, and reboot the TV.
3. Run: python3 tizen/install.py <TV_IP>
"""
import os
import socket
import struct
import sys
import time
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WGT = os.path.join(ROOT, 'CineTV-tizen.wgt')
PORT = 26101
MAX_DATA = 4096


class Sdb:
    def __init__(self, ip):
        print(f"Connecting to Samsung Tizen TV at {ip}:{PORT}...")
        self.sock = socket.create_connection((ip, PORT), timeout=15)
        self.buf = b''
        self.next_id = 1
        self.okay, self.inbox, self.closed = {}, {}, set()
        self.send(b'CNXN', 0x01000000, MAX_DATA, b'host::\0')
        cmd, _, max_data, data = self.read()
        if cmd != b'CNXN':
            raise ConnectionError(f'TV answered {cmd} instead of connecting')
        self.max = min(max_data, MAX_DATA)
        self.banner = data.rstrip(b'\0').decode('utf-8', 'replace')
        print(f"Connected to Samsung TV: {self.banner}")

    def send(self, cmd, arg0, arg1, data=b''):
        c = struct.unpack('<I', cmd)[0]
        self.sock.sendall(struct.pack('<6I', c, arg0, arg1, len(data), sum(data) & 0xFFFFFFFF, c ^ 0xFFFFFFFF) + data)

    def _recv(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(65536)
            if not chunk:
                raise ConnectionError('TV closed the connection')
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def read(self):
        cmd, arg0, arg1, n, _, _ = struct.unpack('<6I', self._recv(24))
        return struct.pack('<I', cmd), arg0, arg1, self._recv(n) if n else b''

    def pump(self):
        cmd, remote, local, data = self.read()
        if cmd == b'OKAY':
            self.okay[local] = remote
        elif cmd == b'WRTE':
            self.inbox[local] = self.inbox.get(local, b'') + data
            self.send(b'OKAY', local, remote)
        elif cmd == b'CLSE':
            self.closed.add(local)

    def open(self, service):
        local, self.next_id = self.next_id, self.next_id + 1
        self.send(b'OPEN', local, 0, service.encode() + b'\0')
        while local not in self.okay:
            if local in self.closed:
                raise ConnectionError('TV refused ' + service)
            self.pump()
        return local, self.okay.pop(local)

    def shell(self, cmd):
        s = self.open('shell:' + cmd)
        local, _ = s
        while local not in self.closed:
            self.pump()
        return self.inbox.pop(local, b'').decode('utf-8', 'replace')


def install(ip, uninstall=False):
    if not uninstall and not os.path.exists(WGT):
        print(f"{WGT} not found. Building now...")
        os.system(f"python3 {os.path.join(ROOT, 'tizen', 'build.py')}")

    sdb = Sdb(ip)
    app_id = "CineTvApp.CineTV"

    if uninstall:
        print(f"Uninstalling {app_id}...")
        res = sdb.shell(f"0 vd_appuninstall {app_id}")
        print("Result:", res.strip())
        return

    print("Pushing package to TV...")
    remote_path = "/tmp/CineTV-tizen.wgt"
    with open(WGT, 'rb') as f:
        pkg_data = f.read()

    # SDB sync push
    s = sdb.open('sync:')
    local, remote = s
    send_header = b'SEND' + struct.pack('<I', len(remote_path) + 5) + f"{remote_path},0644".encode()
    sdb.send(b'WRTE', local, remote, send_header)
    
    chunk_size = 4096
    for i in range(0, len(pkg_data), chunk_size):
        chunk = pkg_data[i:i + chunk_size]
        data_packet = b'DATA' + struct.pack('<I', len(chunk)) + chunk
        sdb.send(b'WRTE', local, remote, data_packet)

    done_packet = b'DONE' + struct.pack('<I', int(time.time()))
    sdb.send(b'WRTE', local, remote, done_packet)
    sdb.send(b'CLSE', local, remote)

    print("Installing CineTV on Tizen...")
    res = sdb.shell(f"0 vd_appinstall {app_id} {remote_path}")
    print("Install output:", res.strip())

    print("Launching CineTV...")
    sdb.shell(f"0 was_execute {app_id}")
    print("CineTV launched successfully on Samsung TV!")


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 tizen/install.py <TV_IP_ADDRESS> [--uninstall]")
        sys.exit(1)
    ip = sys.argv[1]
    uninstall = "--uninstall" in sys.argv
    install(ip, uninstall)
