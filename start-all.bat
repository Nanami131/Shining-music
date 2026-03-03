@echo off
chcp 65001
title Shining Music Force Start
set "LOG_FILE=%~dp0start-all.log"
echo ========================================== > "%LOG_FILE%"
echo Shining Music 一键启动日志 >> "%LOG_FILE%"
echo ========================================== >> "%LOG_FILE%"

:: ==========================================
:: 管理员权限自提升脚本 确保脚本以管理员身份执行 从而使全部中间件可以一键启动
:: ==========================================
:check_Permissions
    echo 正在检查管理员权限...
    call :log 正在检查管理员权限...
    net session >nul 2>&1
    if %errorLevel% == 0 (
        echo 已成功获取管理员权限。
        call :log 已成功获取管理员权限。
    ) else (
        echo 正在请求管理员权限...
        call :log 正在请求管理员权限...
        powershell -Command "Start-Process '%0' -Verb RunAs"
        exit /b
    )

    :: 切换到脚本所在目录
    cd /d "%~dp0"

echo ==========================================
echo       Shining Music 一键启动脚本
echo ==========================================
echo.
call :log Shining Music 一键启动脚本开始执行。

echo [阶段 1/2] 正在启动基础中间件 (Infrastructure)...
call :log [阶段 1/2] 正在启动基础中间件 (Infrastructure)...

echo 1. 启动 MinIO...
call :log 1. 启动 MinIO...
start "MinIO Server" /D "D:\minio\bin" .\minio.exe server D:\minio --console-address :9090
timeout /t 3 >nul
call :check_window "MinIO Server" "MinIO"

echo 2. 启动 Redis...
call :log 2. 启动 Redis...
start "Redis Server" /D "D:\softwares\Redis-x64-3.2.100" redis-server.exe
timeout /t 2 >nul
call :check_window "Redis Server" "Redis"

echo 3. 启动 RabbitMQ...
call :log 3. 启动 RabbitMQ...
start "RabbitMQ Server" /D "E:\softwares\Rabbit\rabbitmq_server-4.0.7\sbin" cmd /c rabbitmq-server.bat
timeout /t 2 >nul
call :check_window "RabbitMQ Server" "RabbitMQ"

echo 4. 启动 Nacos...
call :log 4. 启动 Nacos...
start "Nacos Server" /D "E:\softwares\nacos-server-2.5.1\nacos\bin" cmd /c startup.cmd -m standalone
call :check_window "Nacos Server" "Nacos"

echo.
echo 所有中间件启动指令已发送。
echo 正在等待 20 秒让 Nacos 完成初始化...
call :log 所有中间件启动指令已发送，等待 20 秒让 Nacos 完成初始化...
timeout /t 20

echo.
echo ==========================================
echo [阶段 2/2] 正在启动应用服务 (Microservices)...
echo ==========================================
call :log [阶段 2/2] 正在启动应用服务 (Microservices)...

echo [1/7] 正在启动 Gateway Service...
call :log [1/7] 正在启动 Gateway Service...
start "Gateway Service" cmd /k "cd gateway-service && mvn spring-boot:run -e"
timeout /t 8 >nul
call :check_window "Gateway Service" "Gateway Service"

echo [2/7] 正在启动 User Service...
call :log [2/7] 正在启动 User Service...
start "User Service" cmd /k "cd user-service && mvn spring-boot:run -e"
timeout /t 8 >nul
call :check_window "User Service" "User Service"

echo [3/7] 正在启动 Music Service...
call :log [3/7] 正在启动 Music Service...
start "Music Service" cmd /k "cd music-service && mvn spring-boot:run -e"
timeout /t 8 >nul
call :check_window "Music Service" "Music Service"

echo [4/7] 正在启动 Community Service...
call :log [4/7] 正在启动 Community Service...
start "Community Service" cmd /k "cd community-service && mvn spring-boot:run -e"
timeout /t 8 >nul
call :check_window "Community Service" "Community Service"

rem echo [5/7] 正在启动 Recommend Service...
rem start "Recommend Service" cmd /k "cd recommend-service && mvn spring-boot:run -e"

echo [6/7] 正在启动 Statistics Service...
call :log [6/7] 正在启动 Statistics Service...
start "Statistics Service" cmd /k "cd statistics-service && mvn spring-boot:run -e"
timeout /t 8 >nul
call :check_window "Statistics Service" "Statistics Service"

echo [7/7] 正在启动 前端 (Shining UI)...
call :log [7/7] 正在启动 前端 (Shining UI)...
start "Shining UI" cmd /k "cd shining-ui && npm run dev"
call :check_window "Shining UI" "Shining UI"

echo.
echo ==========================================
echo    所有服务已启动！
echo    - MinIO: http://localhost:9090
echo    - Nacos: http://localhost:8848/nacos
echo    请查看各窗口日志监控启动状态。
echo ==========================================
call :log 所有服务启动指令已发送。MinIO=http://localhost:9090, Nacos=http://localhost:8848/nacos
pause

goto :eof

:log
echo %*>>"%LOG_FILE%"
goto :eof

:check_window
tasklist /v | findstr /i /c:"%~1" >nul
if %errorlevel%==0 (
    call :log [OK] %~2 窗口已启动
) else (
    call :log [FAIL] %~2 窗口未检测到
)
goto :eof

