@echo off
chcp 65001 >nul
cd /d "%~dp0.."

rem 有些机器的 PATH 里没有 System32，先补上
set "PATH=%SystemRoot%\System32;%SystemRoot%;%SystemRoot%\System32\Wbem;%PATH%"

if not exist "mvnw.cmd" (
  echo 找不到 mvnw.cmd，请确认这个脚本在项目的 deploy 目录里。
  pause
  exit /b 1
)

echo 正在启动 make me better，第一次要下依赖，会慢一点……
echo 浏览器窗口会在十几秒后自己弹出来。关掉这个窗口就等于停掉服务。
echo.

start "" /b cmd /c "%~dp0open.cmd"
call mvnw.cmd spring-boot:run
