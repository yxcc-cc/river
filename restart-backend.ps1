Stop-Process -Name java -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
$env:JWT_SECRET="RiverbankJwtSecretKey2026ForESP32Project123456"
Start-Process -FilePath "java" -ArgumentList "-Xmx512m","-jar","riverbank-system-0.0.1-SNAPSHOT.jar","--spring.datasource.username=root","--spring.datasource.password=a3765067","--spring.web.resources.static-locations=file:C:/www/" -WorkingDirectory "C:\Users\Administrator\Documents" -RedirectStandardOutput "C:\Users\Administrator\Documents\app.log" -RedirectStandardError "C:\Users\Administrator\Documents\app.err.log" -WindowStyle Hidden
Start-Sleep -Seconds 10
Get-Content "C:\Users\Administrator\Documents\app.log" -Tail 5