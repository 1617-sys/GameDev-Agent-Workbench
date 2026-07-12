# Docker 一键启动

## 1. 第一次启动

在项目根目录执行：

```powershell
.\start-docker.ps1
```

如果根目录没有 `.env`，脚本会自动从 `.env.example` 复制一份 `.env`。

第一次生成 `.env` 后，先修改这些值：

```env
MYSQL_ROOT_PASSWORD=change_me
DB_PASSWORD=change_me
JWT_SECRET=gamedev-agent-workbench-docker-secret-change-me
LLM_API_KEY=your_deepseek_api_key
```

改完后再次执行：

```powershell
.\start-docker.ps1
```

## 2. 启动成功后访问

- 前端：http://localhost:5173
- Java 后端健康检查：http://localhost:8080/api/health
- Python Agent 文档：http://localhost:8000/docs
- MySQL：localhost:3307

## 3. 重要说明

`docker/mysql/init` 下的 SQL 只会在 MySQL 容器第一次初始化数据库时执行。

如果你之前已经启动过 Docker，并且后来新增了初始化 SQL，需要重建数据库卷：

```powershell
docker compose down -v
.\start-docker.ps1
```

注意：`docker compose down -v` 会删除 Docker 里的 MySQL 数据。

## 4. 常用命令

后台启动：

```powershell
docker compose up -d --build
```

查看容器：

```powershell
docker compose ps
```

查看日志：

```powershell
docker compose logs -f backend-java
docker compose logs -f python-agent
docker compose logs -f frontend-vue
docker compose logs -f mysql
```

停止服务：

```powershell
docker compose down
```
