Remove-Item -Path "C:\www\assets" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "C:\www\index.html" -Force -ErrorAction SilentlyContinue
Expand-Archive -Path "C:\Users\Administrator\Documents\dist.zip" -DestinationPath "C:\www" -Force
Remove-Item -Path "C:\Users\Administrator\Documents\dist.zip" -Force
Get-ChildItem "C:\www" | Select-Object Name