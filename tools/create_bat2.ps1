$content = "@echo off`r`ncd /d D:\project0528`r`n`"C:\Users\小余\AppData\Local\Programs\Python\Launcher\py.exe`" -u tools\vision_multi.py`r`npause`r`n"
$bytes = [System.Text.Encoding]::GetEncoding('gbk').GetBytes($content)
$path = Join-Path $env:USERPROFILE 'Desktop\start_ai.bat'
[System.IO.File]::WriteAllBytes($path, $bytes)
Write-Host "OK: $path"
