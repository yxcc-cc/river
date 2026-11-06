@echo off
chcp 65001 >nul
title 河边预警 - 视频链路启动

echo ============================================
echo   河边安全预警系统 - 本地视频链路（多路）
echo ============================================
echo.

set PY=C:\Users\小余\AppData\Local\Programs\Python\Python312\python.exe
set CF=D:\apps\cloudflared\cloudflared.exe
set PROJ=D:\project0528

if not exist "%PY%" (
    echo [错误] 找不到 Python: %PY%
    pause & exit /b 1
)
if not exist "%CF%" (
    echo [错误] 找不到 cloudflared: %CF%
    pause & exit /b 1
)

echo [1/2] 启动 AI 检测（多路 YOLO + 8083 视频流）...
echo     设备清单在 tools\vision_multi.py 的 DEVICES 里改
start "vision-multi" "%PY%" -u "%PROJ%\tools\vision_multi.py"

echo     等待模型加载...
timeout /t 10 /nobreak >nul

echo [2/2] 启动 Cloudflare 隧道...
echo.
echo     ^>^>^> 下面这个 https://xxx.trycloudflare.com 就是公网入口 ^<^<^<
echo     ^>^>^> 如果它和上次不一样，记得改设备管理里的视频流地址 ^<^<^<
echo.
start "cloudflared-tunnel" "%CF%" tunnel --url http://localhost:8083 --no-autoupdate

echo.
echo 两个窗口已启动，别关它们。
echo.
echo 本机多路总览： http://localhost:8083/
echo 单路预览：     http://localhost:8083/stream?dev=2462ABEFC95C
echo 云端页面：     http://47.111.116.134:8080/monitor
echo.
pause
