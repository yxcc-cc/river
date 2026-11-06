Get-Process nginx,java,mysqld -ErrorAction SilentlyContinue | Select-Object Name, Id | Format-Table -AutoSize
Write-Output "---8080 check---"
$conn = Test-NetConnection -ComputerName 127.0.0.1 -Port 8080 -WarningAction SilentlyContinue
Write-Output ("localhost:8080 open = " + $conn.TcpTestSucceeded)