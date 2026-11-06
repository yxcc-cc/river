@echo off
chcp 936 >nul
echo ============================================
echo   [1/4] 打包后端 (mvn package)...
echo ============================================
cd /d D:\project0528\backend
call mvn package -DskipTests -q
if errorlevel 1 (echo 打包失败! & pause & exit /b 1)

echo [2/4] 上传重启脚本...
scp "D:\project0528\restart-backend.ps1" "Administrator@47.111.116.134:C:\Users\Administrator\Documents\restart-backend.ps1"

echo [3/4] 上传 jar...
scp "D:\project0528\backend\target\riverbank-system-0.0.1-SNAPSHOT.jar" "Administrator@47.111.116.134:C:\Users\Administrator\Documents\riverbank-system-0.0.1-SNAPSHOT.jar"

echo [4/4] 远程重启后端...
ssh Administrator@47.111.116.134 "powershell -ExecutionPolicy Bypass -File C:\Users\Administrator\Documents\restart-backend.ps1"

echo.
echo 完成! 浏览器刷新 http://47.111.116.134:8080 即可
pause