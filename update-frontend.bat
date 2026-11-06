@echo off
chcp 936 >nul
echo ============================================
echo   [1/4] 打包前端 (npm run build)...
echo ============================================
cd /d D:\project0528\frontend
call npm run build
if errorlevel 1 (echo 打包失败! & pause & exit /b 1)

echo [2/4] 压缩 dist...
powershell -NoProfile -Command "Compress-Archive -Path 'D:\project0528\frontend\dist\*' -DestinationPath 'D:\dist.zip' -Force"

echo [3/4] 上传部署脚本 + dist.zip...
scp "D:\project0528\deploy-frontend.ps1" "Administrator@47.111.116.134:C:\Users\Administrator\Documents\deploy-frontend.ps1"
scp "D:\dist.zip" "Administrator@47.111.116.134:C:\Users\Administrator\Documents\dist.zip"

echo [4/4] 远程部署到 nginx...
ssh Administrator@47.111.116.134 "powershell -ExecutionPolicy Bypass -File C:\Users\Administrator\Documents\deploy-frontend.ps1"

echo.
echo 完成! 浏览器 Ctrl+F5 强刷 http://47.111.116.134:8080 即可
pause