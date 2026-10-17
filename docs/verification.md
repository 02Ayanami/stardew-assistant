# 验证记录

验证日期：2026-10-17。环境：macOS、Java 17、Node 24、Docker Desktop。

## 已通过

| 验证 | 结果 |
| --- | --- |
| 后端单元测试 | 50 项通过，无跳过 |
| 真实 MySQL 8.4 + Milvus 2.5.10 集成测试 | 5 项通过，无跳过 |
| 前端测试 | 8 项通过 |
| TypeScript 类型检查与 Vite 生产构建 | 通过 |
| Chromium 浏览器 | 4 项通过：聊天与历史、五类表单、手机布局与历史入口、失败保留输入并重试 |
| 资料导入 | 322 个实体、169 条攻略；重复导入跳过，失败后可以重试 |
| 正式 jar 启动 | 对新项目 MySQL 建表成功；`/api/health` 返回 200 |
| 未配置密钥 | 正式接口返回 503 / MODEL_NOT_CONFIGURED，失败后历史仍为 0 条 |
| 部署配置 | Compose 配置校验通过，独立 MySQL / Milvus / etcd / MinIO 已启动验证 |
| 原项目隔离 | 初始记录的 499 个实际文件逐个 SHA256 核对，无变化；旧项目六个容器 ID 保持不变且仍运行 |
| 生产包边界 | jar 中不包含测试模型、浏览器测试服务器或 Alibaba Agent；主代码不包含 PostgreSQL、JdbcTemplate、关键词或混合检索实现 |

后端测试命令：在 `backend` 下运行 `./mvnw verify`。前端命令：`npm test -- --run`、`npm run build`、`npm run test:e2e`。

浏览器截图保存在本机 `.local/screenshots/`，不提交 Git。

## 模型验证边界

上述自动化测试使用**真实 MySQL、真实 Milvus、测试替身聊天模型与嵌入模型**。替身仅位于 `backend/src/test`，不进入生产 jar。嵌入测试向量只用于验证数据流、索引读写和筛选，不能证明真实语义检索质量。

经用户明确授权，已从旧项目迁移 `ZHIPU_API_KEY`、`CHAT_MODEL`、`EMBEDDING_MODEL`、`EMBEDDING_DIMENSION` 四项配置，保留新项目的 MySQL、端口和数据卷设置。旧配置文件未修改，密钥未写入日志或文档。

正式资料已使用真实嵌入模型导入：322 个实体、169 条攻略，状态 READY。真实聊天和作物收益工具调用已返回成功；完整冒烟结果见文末记录。本次是少量真实请求检查，不能视为模型质量的全面评测。

## 真实模型复核步骤

1. 在本项目 `.env` 填写有效 `ZHIPU_API_KEY`，确认聊天与嵌入模型均可用。
2. 运行 `./scripts/import.sh`，确认导入状态 READY、实体 322、攻略 169；这一步会使用嵌入服务额度。
3. 在页面发送普通问候，再问“春天有哪些可以种植的作物？”。
4. 提问“春季1日到9日种植2株防风草，预算40金币，收益多少？”，核对实际工具参数与结果卡片：在当前资料基线下总成本80、收入140、净收益60。
5. 缺少预算或日期时应追问；补充条件后继续。换成送礼问题，不能继续沿用种植日期与预算。
6. 提问需要攻略解释的问题，核对返回片段、真实来源与规则／经验分类；没有足够依据时不应捏造来源。
7. 刷新后核对已完成消息保留；断网或模型错误时页面保留输入，先查看历史再决定重试。

## Docker 应用镜像

前后端镜像均构建成功。新项目六个容器已通过 Compose 启动检查；网页 http://127.0.0.1:8081 可访问，通过 Nginx 请求 `/api/health` 返回 200，`/api/status` 当前确认为纯向量检索、模型密钥已配置及资料 READY。

本机镜像构建使用已有镜像仓库的 Maven / Java 17 / Nginx 基础镜像，以及兼容的 Node 22 构建镜像；本机前端构建另外使用 Node 24。Dockerfile 默认使用标准镜像名，前端默认 Node 24。构建参数仅改变镜像来源，不改变业务配置。


本机镜像验证命令（使用已有镜像仓库）：

```sh
docker compose build --build-arg BACKEND_BUILD_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 --build-arg BACKEND_RUNTIME_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre backend
docker compose build --build-arg FRONTEND_BUILD_IMAGE=docker.m.daocloud.io/library/node:22-alpine --build-arg FRONTEND_RUNTIME_IMAGE=docker.m.daocloud.io/library/nginx:1.28-alpine frontend
docker compose up -d --no-build --wait backend frontend
```

当前正式 MySQL 使用重新导入的游戏资料，正式 collection 使用真实嵌入向量；测试资料和替身向量没有写入正式游戏表或正式 collection。


## 真实供应商冒烟结果（配置迁移后）

- 普通聊天：HTTP 200，真实聊天模型正常回答。
- 攻略检索：HTTP 200，两次 `searchGuide` 均成功，返回 4 个实际来源。
- 作物计算：HTTP 200，`queryGameData` 和 `calculateCropProfit` 均成功；防风草示例净收益为 60 金币。
- 实测发现的中文分类和作物规则参数问题，已通过工具 schema 的分类枚举和规则字段约束修正；非法检索分类会先返回补充提示，不调用嵌入服务。
- 原始与修正后响应仅保存在被 Git 忽略的 `.local/live-smoke*.json`，不包含密钥。

## 页面外观复核

页面与文档统一使用“星露谷助手 · Valley Companion”。直接复用原项目的基础样式、布局、主题和纯展示农场窗景组件，保留木色导航、纸张侧栏、欢迎页、输入框和工具左右分栏。原项目仅作只读参考。

本次前端构建、8 项前端测试和 4 项浏览器测试通过，覆盖同步聊天、来源详情、五类表单、历史刷新、失败重试和手机端布局。桌面与手机截图已人工检查。仅重新部署新项目的前端容器；后端、正式资料和向量索引未更改。原项目 499 个文件的哈希与基线一致。

## 交互与异常复查

复查并修复以下问题：

- 导航仅改变内存页面而未同步地址：现在工具和帮助页支持直接打开、刷新及浏览器前后退；从工具进入聊天后刷新仍留在聊天。
- 提交成功后依赖额外历史请求展示答案：现在先展示已保存的 POST 响应，随后同步历史；历史暂不可用时保留答案并提示，不重复提交。
- 读取会话期间仍可提交：现在完成读取前禁止提交及重复切换，避免消息发送到先前会话。
- 代理返回 HTML 错误时出现 JSON 解析错误：现在显示 HTTP 状态及重试提示，保留输入。

验证：50 项后端单元测试、5 项真实 MySQL / Milvus 集成测试、8 项前端单元测试及 8 项浏览器测试全部通过。浏览器测试使用测试模型，不消耗真实聊天额度。已核对原项目 499 个文件哈希无变化，正式资料仍为 READY，322 个实体和 169 条攻略。
