@echo off
chcp 65001
title Shining Music Force Start
set "LOG_FILE=%~dp0start-all.log"
set "MINIO_DATA_DIR=D:\minio\shining-runtime-data"
set "ES_HOME=E:\softwares\elasticsearch-9.0.3-windows-x86_64\elasticsearch-9.0.3"
echo ========================================== > "%LOG_FILE%"
echo Shining Music 一键启动日志 >> "%LOG_FILE%"
echo ========================================== >> "%LOG_FILE%"

:: ==========================================
:: 管理员权限自提升脚本 确保脚本以管理员身份执行 从而使全部中间件可以一键启动
:: ==========================================
:check_Permissions
    echo 正在检查管理员权限...
    >>"%LOG_FILE%" echo(正在检查管理员权限...
    net session >nul 2>&1
    if %errorLevel% == 0 (
        echo 已成功获取管理员权限。
        >>"%LOG_FILE%" echo(已成功获取管理员权限。
    ) else (
        echo 正在请求管理员权限...
        >>"%LOG_FILE%" echo(正在请求管理员权限...
        powershell -Command "Start-Process '%0' -Verb RunAs"
        exit /b
    )

    :: 切换到脚本所在目录
    cd /d "%~dp0"

echo ==========================================
echo       Shining Music 一键启动脚本
echo ==========================================
echo.
>>"%LOG_FILE%" echo(Shining Music 一键启动脚本开始执行。

echo [阶段 1/2] 正在启动基础中间件 Infrastructure...
>>"%LOG_FILE%" echo([阶段 1/2] 正在启动基础中间件 Infrastructure...

echo 1. 检查 MySQL...
>>"%LOG_FILE%" echo(1. 检查 MySQL...
sc query MySQL82 | findstr /i "RUNNING" >nul
if %errorlevel%==0 (
    echo MySQL82 已运行。
    >>"%LOG_FILE%" echo(MySQL82 已运行。
) else (
    echo 正在启动 MySQL82...
    >>"%LOG_FILE%" echo(正在启动 MySQL82...
    net start MySQL82
)
call :wait_port 3306 "MySQL82" 30

echo 2. 启动 MinIO...
>>"%LOG_FILE%" echo(2. 启动 MinIO...
start "MinIO Server" /D "D:\minio\bin" cmd /k minio.exe server "%MINIO_DATA_DIR%" --console-address :9090
call :wait_port 9000 "MinIO" 30

echo 3. 启动 Redis...
>>"%LOG_FILE%" echo(3. 启动 Redis...
start "Redis Server" /D "D:\softwares\Redis-x64-3.2.100" cmd /k redis-server.exe
call :wait_port 6379 "Redis" 15

echo 4. 启动 RabbitMQ...
>>"%LOG_FILE%" echo(4. 启动 RabbitMQ...
start "RabbitMQ Server" /D "E:\softwares\Rabbit\rabbitmq_server-4.0.7\sbin" cmd /k rabbitmq-server.bat
call :wait_port 5672 "RabbitMQ" 45

echo 5. 启动 Elasticsearch...
>>"%LOG_FILE%" echo(5. 启动 Elasticsearch...
start "Elasticsearch Server" /D "%ES_HOME%\bin" cmd /k elasticsearch.bat
call :wait_port 9200 "Elasticsearch" 120

echo 6. 启动 Nacos...
>>"%LOG_FILE%" echo(6. 启动 Nacos...
start "Nacos Server" /D "E:\softwares\nacos-server-2.5.1\nacos\bin" cmd /k startup.cmd -m standalone
call :wait_port 8848 "Nacos" 120

echo.
echo 所有中间件启动指令已发送。
echo 正在继续启动应用服务...
>>"%LOG_FILE%" echo(所有中间件启动指令已发送，继续启动应用服务。

echo.
echo ==========================================
echo [阶段 2/2] 正在启动应用服务 Microservices...
echo ==========================================
>>"%LOG_FILE%" echo([阶段 2/2] 正在启动应用服务 Microservices...

echo [1/7] 正在启动 Gateway Service...
>>"%LOG_FILE%" echo([1/7] 正在启动 Gateway Service...
start "Gateway Service" cmd /k "cd gateway-service && mvn spring-boot:run -e"
call :wait_port 8080 "Gateway Service" 120

echo [2/7] 正在启动 User Service...
>>"%LOG_FILE%" echo([2/7] 正在启动 User Service...
start "User Service" cmd /k "cd user-service && mvn spring-boot:run -e"
call :wait_port 8081 "User Service" 120

echo [3/7] 正在启动 Music Service...
>>"%LOG_FILE%" echo([3/7] 正在启动 Music Service...
start "Music Service" cmd /k "cd music-service && mvn spring-boot:run -e"
call :wait_port 8082 "Music Service" 120

echo [4/7] 正在启动 Community Service...
>>"%LOG_FILE%" echo([4/7] 正在启动 Community Service...
start "Community Service" cmd /k "cd community-service && mvn spring-boot:run -e"
call :wait_port 8083 "Community Service" 120

echo [5/7] 正在启动 Recommend Service...
>>"%LOG_FILE%" echo([5/7] 正在启动 Recommend Service...
start "Recommend Service" cmd /k "cd recommend-service && mvn spring-boot:run -e"
call :wait_port 8085 "Recommend Service" 120

echo [6/7] 正在启动 Statistics Service...
>>"%LOG_FILE%" echo([6/7] 正在启动 Statistics Service...
start "Statistics Service" cmd /k "cd statistics-service && mvn spring-boot:run -e"
call :wait_port 8084 "Statistics Service" 120

echo [7/7] 正在启动 前端 Shining UI...
>>"%LOG_FILE%" echo([7/7] 正在启动 前端 Shining UI...
start "Shining UI" cmd /k "cd shining-ui && npm run dev"
call :wait_port 5173 "Shining UI" 60

echo.
echo ==========================================
echo    所有服务已启动！
echo    - MinIO: http://localhost:9090
echo    - Nacos: http://localhost:8848/nacos
echo    - Elasticsearch: http://localhost:9200
echo    - Shining UI: http://localhost:5173
echo    请查看各窗口日志监控启动状态。
echo ==========================================
>>"%LOG_FILE%" echo(所有服务启动指令已发送。MinIO=http://localhost:9090, Nacos=http://localhost:8848/nacos, Elasticsearch=http://localhost:9200, Shining UI=http://localhost:5173
pause

goto :eof

:wait_port
set "PORT=%~1"
set "NAME=%~2"
set "WAIT_SECONDS=%~3"
set /a "WAIT_COUNT=0"
:wait_port_loop
powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>&1
if %errorlevel%==0 (
    >>"%LOG_FILE%" echo([OK] %NAME% 端口 %PORT% 已监听
    goto :eof
)
if %WAIT_COUNT% GEQ %WAIT_SECONDS% (
    >>"%LOG_FILE%" echo([FAIL] %NAME% 端口 %PORT% 未监听
    goto :eof
)
set /a "WAIT_COUNT+=2"
timeout /t 2 >nul
goto :wait_port_loop
