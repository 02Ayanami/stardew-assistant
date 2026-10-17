# API 接口说明

全部位于 `/api`，请求与响应为 JSON。聊天和表单同步完成，正常返回 HTTP 200。错误格式：`{code,message}`。

| 方法与路径 | 输入 / 输出 |
| --- | --- |
| `POST /sessions` | 创建空会话，返回 `{id,title,createdAt,updatedAt}` |
| `GET /sessions?page=1&limit=20` | 非空会话历史，`{items,page,limit}`，limit 1–100 |
| `GET /sessions/{id}` | `{session,messages}`，消息按 ID 升序 |
| `POST /sessions/{id}/messages` | `{message}`，正文 1–8000 字 |
| `POST /sessions/{id}/tools/{toolName}` | `{args}`，仅支持五类表单工具 |
| `GET /tool-options?tool=工具名称` | `{tool,options}`，当前资料中的候选项 |
| `GET /health` | HTTP 服务可用性，不代表模型密钥有效 |
| `GET /status` | 导入状态、数量、模型配置；不会发送模型请求 |

一次成功交互返回：

```json
{
  "sessionId": "UUID",
  "messageId": 2,
  "answer": "结果已整理",
  "toolResults": [{
    "name": "calculateCropProfit",
    "args": {},
    "result": {"status":"SUCCESS","data":[],"sources":[],"assumptions":[],"errorCode":null}
  }],
  "sources": []
}
```

`toolResults` 包含实际使用参数。历史消息也包含 `toolResults` 和 `sources`。来源中的旧 `snapshotId` 仅为复制资料的出处元数据，不是新项目可切换的版本或请求参数。

工具参数定义统一放在 `backend/src/main/resources/tools.json`。前端五个表单：`calculateCropProfit`、`expandMaterialRequirements`、`findFish`、`queryGiftPreferences`、`getBundleMissing`。AI 还可调用 `queryGameData`、`listCrops`、`searchGuide`。

缺少业务条件可返回 `NEEDS_INPUT`，不应与服务异常混淆。`NO_DATA` 表示当前资料没有匹配，不代表游戏不存在。`UNSUPPORTED` 表示超出已实现规则。

HTTP 400：请求格式或参数错误；404：会话不存在；409：同会话忙；422：模型或工具调用达到上限；502：模型服务失败；503：资料或模型未就绪；504：处理超时。

不提供运行 ID、任务取消、SSE、事件游标或条件版本接口。继续讨论工具结果，只需在同一个 session 中发送下一条问题。
