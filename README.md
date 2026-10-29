# API Quality Guard

面向个人用户的 API 质量守护平台：导入接口文档 → 生成/增强测试用例 → 执行与断言 → 质量评分 → 门禁判定 → AI 故障诊断与自然语言问答


##功能

- **接口资产**：手动维护接口、Swagger / OpenAPI 导入，按方法+路径幂等去重
- **用例生成**：规则引擎四类策略（正常值/边界值/必填缺失/类型异常）+ AI 增强复杂场景
- **测试执行**：单用例调试、测试计划批量执行、有界线程池、协作式取消、重启恢复
- **质量评分**：功能/性能/稳定性三维加权，评分明细可复现，等级判定
- **质量门禁**：总分/失败数/异常数阈值，计划级与请求级覆盖，检查记录留档，CI 脚本
- **报告**：运行报告、平台概览、四维质量评分、风险接口
- **AI**：失败结果诊断、AI 用例增强、AI 问答助手


## 技术栈

| 部分 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、MyBatis-Plus、Flyway、MySQL 8、Redis 7 |
| Agent | Python 3.13、FastAPI、LangChain、Pydantic Settings |
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router、ECharts |
| 部署 | Docker Compose、Nginx |

## 目录

```text
api-quality-guard-backend/    Spring Boot 主平台
api-quality-guard-agent/      Python 诊断/问答 Agent
api-quality-guard-frontend/   Vue 前端
deploy/                       手工数据库脚本等
docker-compose.yml            一键部署
scripts/ci/quality-gate.sh    门禁 CI 脚本
```

## 本地开发

前置：MySQL 8、Redis 7、JDK 21、Node.js 20+、Python 3.13+。

1. 后端配置 `api-quality-guard-backend/src/main/resources/application-local.yml`（已 gitignore）：
   ```yaml
   spring:
     datasource:
       username: your_db_user
       password: your_db_password
   aqg:
     internal-token: your-internal-token
   ```
2. 启动后端：
   ```bash
   cd api-quality-guard-backend
   mvn spring-boot:run
   ```
3. 启动 Agent（先复制 `api-quality-guard-agent/.env.example` 为 `.env` 并填入模型配置和同一个 `AQG_INTERNAL_TOKEN`）：
   ```bash
   cd api-quality-guard-agent
   python -m venv .venv
   .venv/Scripts/pip install -r requirements.txt   # Windows
   .venv/Scripts/uvicorn app.main:app --host 0.0.0.0 --port 8090
   ```
4. 启动前端：
   ```bash
   cd api-quality-guard-frontend
   npm install
   npm run dev
   ```
5. 访问 http://localhost:5173 。默认管理员 `admin / admin123`（首次启动自动创建；可用 `AQG_ADMIN_USERNAME` / `AQG_ADMIN_PASSWORD` 覆盖，已有账号不会被覆盖）

> 模型接口地址：没有模型 Key或本地部署模型 时可以设置 `AGENT_LLM_MOCK=true` 联调页面和链路


## CI 门禁

平台与测试计划已就绪后：

```bash
AQG_USERNAME=admin AQG_PASSWORD=your-password \
  bash scripts/ci/quality-gate.sh <planId>
```

GitHub Actions 需要配置 `AQG_USERNAME` / `AQG_PASSWORD` secrets，参考 `.github/workflows/quality-gate.yml`。

## 测试

```bash
cd api-quality-guard-backend && mvn test
cd api-quality-guard-frontend && npm run build
cd api-quality-guard-agent && python -m py_compile app/main.py
```

后端测试使用本地 MySQL 和 Redis；默认 Redis 15 号库用于认证/隔离测试
