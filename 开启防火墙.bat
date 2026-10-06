@echo off
chcp 65001 >/dev/null
echo 正在开启防火墙...
netsh advfirewall set allprofiles state on
echo 防火墙已开启！
pause
