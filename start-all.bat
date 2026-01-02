@echo off
chcp 65001
echo ==========================================
echo       Shining Music 一键启动脚本
echo ==========================================
echo.
echo 注意: 请确保 Nacos, MySQL, Redis, MinIO, RabbitMQ 等基础中间件已启动。
echo.

echo [1/7] 正在启动 Gateway Service...
start "Gateway Service" cmd /k "cd gateway-service && mvn spring-boot:run"

echo [2/7] 正在启动 User Service...
start "User Service" cmd /k "cd user-service && mvn spring-boot:run"

echo [3/7] 正在启动 Music Service...
start "Music Service" cmd /k "cd music-service && mvn spring-boot:run"

echo [4/7] 正在启动 Community Service...
start "Community Service" cmd /k "cd community-service && mvn spring-boot:run"

echo [5/7] 正在启动 Recommend Service...
start "Recommend Service" cmd /k "cd recommend-service && mvn spring-boot:run"

echo [6/7] 正在启动 Statistics Service...
start "Statistics Service" cmd /k "cd statistics-service && mvn spring-boot:run"

echo [7/7] 正在启动 前端 (Shining UI)...
start "Shining UI" cmd /k "cd shining-ui && npm run dev"

echo.
echo ==========================================
echo    所有服务启动命令已执行
echo    请查看弹出的命令行窗口监控启动日志
echo ==========================================
pause
