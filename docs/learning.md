# 新手阅读路线

先不要从所有文件开始。每次只沿一个场景看下去，能解释输入、处理、输出就可以暂停。

## 第一站：只看一次作物收益计算

1. `backend/src/main/java/dev/stardew/assistant/crop/CropScenario.java`：输入是什么。
2. 同目录 `CropCalculator.calculate`：买种子、收获、补种、累计收入。
3. 同目录 `CropProjection.java`：结果是什么。
4. 对照 `backend/src/test/java/dev/stardew/assistant/crop/CropCalculatorTest.java` 手算一例。

合成例子：种 2 株，种子每株 10、卖价每株 30、生长 4 天、春1到春9、预算20。两次收获，总成本40、收入120、净利润80。这是测试数字，不是游戏作物价格。能解释为什么累计成本可以大于初始预算，就掌握了这一小段。

## 第二站：页面怎样调到这段 Java

主链：`frontend/src/components/ToolForm.vue` → `frontend/src/api/client.ts` → `ApiController.tool` → `ChatService.direct` → `ToolService.execute` → `CropTools` → `CropCalculator`。

Controller 接收请求；Service 组织处理；计算器实现规则。先只看 `calculateCropProfit` 分支，不必同时读其余工具。

## 第三站：MyBatis 和 MySQL

主链：`GameDataRepository` → `GameMapper` → `backend/src/main/resources/mapper/GameMapper.xml` → `game_entity`。

读懂按名称或别名查一条作物，随后看 `ChatMapper.xml` 如何保存两条消息。游戏属性使用 JSON 是为了沿用资料，不要求先学复杂 JSON 查询。Mapper XML 使用 `#{}` 绑定参数。

## 第四站：AI 为什么会调用 Java

主链：`ChatService.chat` → `ModelGateway` / `ZhipuModelGateway` → 原生 tool call → `ToolInputGuard` → `ToolService` → tool response → 模型答案。

只需先区分：模型提出工具名称和参数；Java 校验并计算；模型负责解释。`tools.json` 描述模型可以使用的工具，不是业务算法。参数核验从用户历史临时重建，没有额外的任务数据库。

## 第五站：RAG 怎样找资料

导入：`DataImportService` 读取攻略 → `EmbeddingClient` 变成向量 → `VectorStore` 保存。

提问：`GuideSearchService` 将问题变向量 → `MilvusVectorStore.search` 查相似片段 → 模型结合片段回答。

先理解“文字变向量，按相似度找资料”；无需立即研究 Milvus、etcd、MinIO 内部实现。项目只用这一条检索路线。

## 最后再看错误与测试

`ApiErrors` 负责错误响应；`ChatService` 的次数和时限防止无限调用；短事务只保存完整交互。测试源码中的 `TestProviders` 是替身，生产代码中不会调用它。

每站的完成标准：能找到入口，说明输入从哪里来、结果到哪里去，自己改一个小规则并验证。看不懂时，用具体方法名继续追问，不要求一次读完整个项目。
