@echo off
chcp 65001 >/dev/null
echo 正在关闭防火墙...
netsh advfirewall set allprofiles state off
echo 防火墙已关闭！可以开始演示了。
pause
