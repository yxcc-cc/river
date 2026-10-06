import os

desktop = os.path.join(os.path.expanduser('~'), 'Desktop')
bat_path = os.path.join(desktop, 'start_face.bat')

lines = [
    '@echo off',
    'cd /d D:\\project0528',
    '"C:\\Users\\小余\\AppData\\Local\\Programs\\Python\\Python312\\python.exe" -u tools\\face_service.py',
    'pause',
]

content = '\r\n'.join(lines) + '\r\n'
with open(bat_path, 'wb') as f:
    f.write(content.encode('gbk'))

print('OK:', bat_path)
