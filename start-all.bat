@echo off
chcp 65001
title Shining Music Force Start

:: ==========================================
:: 管理员权限自提升脚本 确保脚本以管理员身份执行 从而使全部中间件可以一键启动
:: ==========================================
:check_Permissions
    echo 正在检查管理员权限...
    net session >nul 2>&1
    if %errorLevel% == 0 (
        echo 已成功获取管理员权限。
    ) else (
        echo 正在请求管理员权限...
        powershell -Command "Start-Process '%0' -Verb RunAs"
        exit /b
    )

    :: 切换到脚本所在目录
    cd /d "%~dp0"

echo ==========================================
echo       Shining Music 一键启动脚本
echo ==========================================
echo.

echo [阶段 1/2] 正在启动基础中间件 (Infrastructure)...

echo 1. 启动 MinIO...
start "MinIO Server" /D "D:\minio\bin" .\minio.exe server D:\minio --console-address :9090
timeout /t 3 >nul

echo 2. 启动 Redis...
start "Redis Server" /D "D:\softwares\Redis-x64-3.2.100" redis-server.exe
timeout /t 2 >nul

echo 3. 启动 RabbitMQ...
start "RabbitMQ Server" /D "E:\softwares\Rabbit\rabbitmq_server-4.0.7\sbin" cmd /c rabbitmq-server.bat
timeout /t 2 >nul

echo 4. 启动 Nacos...
start "Nacos Server" /D "E:\softwares\nacos-server-2.5.1\nacos\bin" cmd /c startup.cmd -m standalone

echo.
echo 所有中间件启动指令已发送。
echo 正在等待 20 秒让 Nacos 完成初始化...
timeout /t 20

echo.
echo ==========================================
echo [阶段 2/2] 正在启动应用服务 (Microservices)...
echo ==========================================

echo [1/7] 正在启动 Gateway Service...
start "Gateway Service" cmd /k "cd gateway-service && mvn spring-boot:run -e"

echo [2/7] 正在启动 User Service...
start "User Service" cmd /k "cd user-service && mvn spring-boot:run -e"

echo [3/7] 正在启动 Music Service...
start "Music Service" cmd /k "cd music-service && mvn spring-boot:run -e"

echo [4/7] 正在启动 Community Service...
start "Community Service" cmd /k "cd community-service && mvn spring-boot:run -e"

rem echo [5/7] 正在启动 Recommend Service...
rem start "Recommend Service" cmd /k "cd recommend-service && mvn spring-boot:run -e"

echo [6/7] 正在启动 Statistics Service...
start "Statistics Service" cmd /k "cd statistics-service && mvn spring-boot:run -e"

echo [7/7] 正在启动 前端 (Shining UI)...
start "Shining UI" cmd /k "cd shining-ui && npm run dev"

echo.
echo ==========================================
echo    所有服务已启动！
echo    - MinIO: http://localhost:9090
echo    - Nacos: http://localhost:8848/nacos
echo    请查看各窗口日志监控启动状态。
echo ==========================================
pause
