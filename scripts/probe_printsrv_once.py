"""Одноразовый срез реальных PrintSrv через проброшенные туннели (127.0.0.1:19101-19108)."""
import json, socket, struct, sys, datetime

UNITS = {
    'hassia1': 19101, 'hassia2': 19102, 'hassia3': 19103, 'hassia4': 19104,
    'hassia5': 19105, 'hassia6': 19106, 'bosch': 19107, 'grunwald5': 19108,
}
DEVICES = ['scada', 'CamAgregation', 'CamChecker']

def query(port, device, timeout=5):
    body = json.dumps({'DeviceName': device, 'Command': 'QueryAll'}).encode('cp1251')
    frame = b'P001' + struct.pack('>I', len(body)) + body
    with socket.create_connection(('127.0.0.1', port), timeout=timeout) as s:
        s.sendall(frame)
        hdr = b''
        while len(hdr) < 8:
            chunk = s.recv(8 - len(hdr))
            if not chunk:
                raise ConnectionError('closed')
            hdr += chunk
        assert hdr[:4] == b'P001', hdr[:4]
        (n,) = struct.unpack('>I', hdr[4:])
        data = b''
        while len(data) < n:
            chunk = s.recv(min(n - len(data), 65536))
            if not chunk:
                raise ConnectionError('closed mid-body')
            data += chunk
    return json.loads(data.decode('cp1251'))

out = {}
ts = datetime.datetime.now().isoformat(timespec='seconds')
for unit, port in UNITS.items():
    entry = {}
    for dev in DEVICES:
        try:
            resp = query(port, dev)
            props = resp['Units']['u1']['Properties']
            if dev == 'scada':
                entry['scada'] = {k: v for k, v in props.items()
                                  if k in ('batch', 'lineerr') or 'Counter' in k
                                  or (k.endswith(('Connection', 'Error')) and v not in ('0', ''))}
            else:
                entry[dev] = {k: props.get(k) for k in
                              ('curitem', 'Total', 'Succeeded', 'Failed', 'BatchSucceeded', 'BatchFailed', 'ST')}
        except Exception as e:
            entry[dev] = {'error': str(e)}
    out[unit] = entry

print(json.dumps({'ts': ts, 'units': out}, ensure_ascii=False, indent=1))
