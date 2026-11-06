schtasks /create /tn "RiverbankStart" /tr "powershell.exe -ExecutionPolicy Bypass -File C:\Users\Administrator\Documents\start-all.ps1" /sc onstart /ru SYSTEM /rl highest /f
schtasks /run /tn "RiverbankStart"
Start-Sleep -Seconds 15
Write-Output "--- listening ports after task trigger ---"
netstat -ano | findstr ":8080"
netstat -ano | findstr ":8081"