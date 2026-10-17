# 星露谷助手 · Valley Companion

一个结合游戏资料、确定性工具与向量检索的星露谷 AI 助手：聊天、查攻略、算作物收益、展开制作材料、筛选鱼类、查询送礼偏好和献祭缺项。

技术栈：Java 17、Spring Boot 3.5.8、MyBatis 3.0.5、MySQL 8.4、Spring AI 1.1.2、Milvus 2.5.10、Vue 3 / TypeScript。

这是独立项目。原项目只作为资料和算法参考，代码、配置、Git、数据库和索引均不参与本项目运行。游戏资料、静态资源及相应字体许可证复制自原项目；每条资料保留原来源。

## 先理解这一条流程

```text
Vue 页面 → Controller → Service
                         ├─ Mapper XML → MySQL 游戏资料 / 聊天历史
                         ├─ Spring AI → 聊天模型 / 嵌入模型
                         ├─ Milvus → 攻略向量检索
                         └─ Java 工具 → 收益 / 材料 / 钓鱼 / 送礼 / 献祭
             ← 完整答案、工具结果、实际来源
```

只有向量检索，没有关键词或混合检索。只有普通同步请求，没有后台任务、SSE、事件回放、复杂 Agent 框架或多版本快照。AI 可连续调用工具提供建议，不做完整结构化计划的自动校验。

## 使用 Docker 启动

需要 Docker Desktop。首次构建会联网下载镜像与 Maven/npm 依赖。

```sh
cd /Users/haomehuang/Desktop/stardew-assistant-simple
test -f .env || cp .env.example .env
```

在新项目 `.env` 中填写 `ZHIPU_API_KEY`，确认账户可使用 `CHAT_MODEL` 和 `EMBEDDING_MODEL`。默认聊天模型 `glm-5.3-flash`，嵌入模型 `embedding-3`、1024 维。密钥只在后端使用，不进入前端或镜像。

```sh
./scripts/import.sh
```

该命令会启动新项目数据服务、构建后端、导入 322 个游戏实体和 169 条攻略，再启动页面。**首次导入会调用嵌入 API，消耗供应商额度。** 导入失败时保持数据未就绪，修正配置后重新执行即可。

- 页面：http://127.0.0.1:8081
- API：http://127.0.0.1:9911/api/health
- 配置及资料状态：http://127.0.0.1:8081/api/status

后续启动：`./scripts/start.sh`。停止并保留数据：`./scripts/stop.sh`。普通启动不会自动导入资料或调用模型。

资料、模型及维度均未改变且索引完整时，重复导入跳过嵌入调用。资料或嵌入模型改变后，运行 `./scripts/import.sh`；它先停止新应用，再重建本项目索引。历史消息仍保留当时的工具结果，后续问题查询当前资料，不支持历史资料版本回溯。

## 本机开发

需要 Java 17、Node 24、Docker。先使用上面的步骤完成资料导入，再停止新项目应用容器，数据服务继续运行：

```sh
docker compose stop backend frontend
```

分别在两个终端执行：

```sh
cd backend
./mvnw spring-boot:run
```

```sh
cd frontend
npm ci
npm run dev
```

开发页面：http://127.0.0.1:5174。后端默认连接 MySQL 3307、Milvus 19531。自定义端口时，另在 `.env` 设置匹配的 `DATABASE_URL` / `MILVUS_URI`；开发前端通过环境变量 `FRONTEND_PORT`、`API_TARGET` 配置。

无密钥时可以启动页面和 API；完成资料导入后，五个工具表单本身不调用聊天模型。聊天、向量检索和首次资料导入需要相应模型配置。

## 功能范围

- 作物：普通品质、每天浇水、无肥料和职业加成、固定数量补种、直接出售；每种作物独立比较。
- 材料：按已收录路线递归展开，抵扣库存，提示未知前置条件；不承诺全局最优路线。
- 钓鱼：普通钓竿条件，支持季节、天气、地点和 0600–2600 HHMM 时间筛选；不包含特殊鱼饵与 Mod。
- 送礼：个人例外优先，未知不默认为中立；不计算好感度。
- 献祭：单个普通收集包，分开核算已提交与未提交物品，明确品质；不跨包分配，不支持混合献祭。
- 聊天：读取最近 20 条消息；最多 8 轮模型交互、12 次工具执行、120 秒。来源与数值仍需核对，模型可能出错。

请求失败不自动重试，也不保存半轮消息。刷新只读取已完成的历史；连接中断后，应先查看历史确认结果，再决定是否重试。同一会话只允许一个处理中请求。

## 验证

```sh
# 无模型密钥的单元测试
(cd backend && ./mvnw test)
(cd frontend && npm test -- --run && npm run build)

# 真实 MySQL、Milvus 和浏览器联调
# 首次需要在 frontend 下运行 npx playwright install chromium
./scripts/verify.sh
```

后端集成测试使用 Testcontainers 创建临时 MySQL，向量测试只访问新项目 Milvus 的测试 collection。浏览器测试也使用临时 MySQL和独立测试 collection。**测试中的聊天与嵌入模型为替身，全部位于 test 源码，不进入生产 jar；测试通过不代表真实模型效果已经验证。**

真实模型冒烟步骤见 [验证记录](docs/verification.md)。接口见 [API](docs/api.md)，从哪里开始学见 [新手阅读路线](docs/learning.md)。

## 数据隔离

Compose 项目名为 `stardew-assistant-simple`，所有容器、数据卷均有独立前缀。默认数据库端口 3307、向量端口 19531、健康端口 19092。Milvus 的 etcd、MinIO 由 Compose 管理，不需要你先掌握它们的内部实现。

本项目脚本不删除数据卷，不访问旧 PostgreSQL，也不访问旧 Milvus。不要对旧项目执行任何迁移或清库命令。本项目面向本机单人单实例使用，没有账户系统。
