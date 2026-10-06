$lines = @(
    '@echo off',
    'cd /d D:\project0528',
    '"C:\Users\小余\AppData\Local\Programs\Python\Python312\python.exe" -u tools\vision_multi.py',
    'pause'
)
$text = ($lines -join "`r`n") + "`r`n"
$path = Join-Path $env:USERPROFILE 'Desktop\start_ai.bat'
[System.IO.File]::WriteAllText($path, $text, [System.Text.Encoding]::Default)
Write-Host "Created: $path"
