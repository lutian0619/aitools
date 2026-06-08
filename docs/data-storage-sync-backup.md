# 数据存储、同步和备份说明

这篇文档说明 `aitools` 当前的数据模型：电脑服务怎么保存数据，Web 和 Android 怎么同步，`latest.zip` 备份是什么，以及出问题时应该从哪里排查。

## 一句话版本

现在的数据工具采用同一套同步协议：

- 服务端主存储是 SQLite。
- 每个工具一套 entries 数据。
- Web 和 Android 通过 HTTP API 同步 entries。
- `latest.zip` 是导出和兼容备份，不再是主存储。
- `state/` 目录不再保留旧 JSON；JSON 只存在于 ZIP 导出包里。

## 目录位置

同步工具的数据都放在 `sync/` 下。当前启用同步的工具是：

| 工具 | 状态目录 | SQLite 主库 | 备份 ZIP |
| --- | --- | --- | --- |
| 一页日记 | `sync/diary/` | `sync/diary/state/diary.sqlite` | `sync/diary/latest.zip` |
| 日常格 | `sync/tracker/` | `sync/tracker/state/tracker.sqlite` | `sync/tracker/latest.zip` |

附件放在：

```text
sync/<stateName>/state/attachments/
```

历史备份放在：

```text
sync/<stateName>/history/
```

`stateName` 来自 `server/config/tools.json` 里的 `sync.stateName`。

## 服务端主存储

电脑服务运行在 `server/server.js`。启动时会为每个启用同步的工具打开对应 SQLite：

```text
sync/<stateName>/state/<toolId>.sqlite
```

SQLite 里主要有两张表：

```text
metadata
entries
```

`metadata` 保存工具状态，例如最近更新时间。`entries` 保存每条同步数据。

`entries` 表核心字段：

| 字段 | 含义 |
| --- | --- |
| `id` | entry 唯一 id，跨端同步的主键 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |
| `deleted_at` | 软删除时间；为空表示有效 |
| `version` | 版本号，越大越新 |
| `device_id` | 产生这条 entry 的设备 |
| `tags` | JSON 数组，服务端用于索引和导出 |
| `attachments` | JSON 数组，附件文件名 |
| `body` | 业务正文，字符串 |
| `body_hash` | 正文 hash，用于 manifest 比较 |

服务端仍然对外返回原来的 entry 结构，客户端不需要知道内部已经换成 SQLite。

## Entry 模型

所有同步数据都包装成 entry：

```json
{
  "id": 1780797764063,
  "createdAt": 1780797764063,
  "updatedAt": 1780800618764,
  "deletedAt": null,
  "version": 3,
  "deviceId": "web-tracker-1780797764064-288351",
  "tags": ["project", "number"],
  "attachments": [],
  "body": "{\"schema\":\"life-tracker.project.v1\",\"name\":\"体重\",\"kind\":\"number\",\"unit\":\"kg\",\"note\":\"\"}"
}
```

这里要区分两层：

- entry 外层是同步层，所有工具共用。
- `body` 是业务层，每个工具自己解释。

一页日记的 `body` 是日记正文。日常格的 `body` 是 JSON 字符串，例如事项或记录：

```json
{
  "schema": "life-tracker.record.v2",
  "projectId": "1780797764063",
  "projectName": "体重",
  "kind": "number",
  "date": "2026-06-07",
  "amount": 11,
  "unit": "kg",
  "content": "",
  "note": ""
}
```

## 同步 API

统一工具接口：

```text
GET  /api/tools/:id/manifest
GET  /api/tools/:id/entries
POST /api/tools/:id/entries
POST /api/tools/:id/entries/query
GET  /api/tools/:id/latest
PUT  /api/tools/:id/latest
```

一页日记还保留旧接口，兼容现有手机端：

```text
GET  /api/manifest
GET  /api/entries
POST /api/entries
POST /api/entries/query
GET  /api/latest
PUT  /api/latest
GET  /api/attachments/:filename
```

### `GET /manifest`

返回轻量清单，不返回完整正文：

```json
{
  "ok": true,
  "app": "daily-grid",
  "updatedAt": "2026-06-07T03:03:44.774Z",
  "entries": [
    {
      "id": "1780797764063",
      "version": 3,
      "updatedAt": 1780800618764,
      "deletedAt": null,
      "deviceId": "web-tracker-1780797764064-288351",
      "bodyHash": "...",
      "attachments": []
    }
  ]
}
```

用途是让客户端判断哪些 entry 需要拉取或提交。

### `GET /entries`

返回完整 entries。Web 端目前主要用这个接口刷新完整数据。

### `POST /entries/query`

客户端传 ids，服务端只返回这些 entries 和相关附件。Android 日记同步主要靠这个做增量拉取。

### `POST /entries`

客户端提交新增或更新的 entries。服务端按 `id` 找已有 entry，再按 `version` 合并。

合并规则：

- 服务端没有这个 `id`：插入。
- incoming `version` 更大：覆盖。
- incoming `version` 更小：忽略。
- `version` 相同且内容 hash 相同：接受，主要用于幂等同步。
- `version` 相同但内容不同：忽略，避免同版本冲突互相覆盖。

删除不是物理删除，而是写入 `deletedAt`。软删除会参与同步，避免某台设备又把旧数据同步回来。

## 客户端边界

### Web

Web 端正文数据不依赖浏览器本地存储。

- 一页日记 Web 读写 `/api/entries`。
- 日常格 Web 读写 `/api/tools/tracker/entries`。
- 浏览器 `localStorage` 只保存设备 id 等轻量状态。

这意味着：电脑服务关闭时，Web 端不能作为可靠主存储。

### Android

一页日记 Android 是本地优先：

- 本地使用 SQLite。
- 日记、标签、附件关系有本地表和索引。
- 同步时先比较 manifest，再拉取缺失或更新的 entries。
- 图片附件按文件名同步。

日常格 Android 目前更轻：

- 本地使用 `SharedPreferences` 保存 entries。
- 同步时和电脑服务交换 entries。
- 后续可以升级成本地 SQLite，但这和服务端 SQLite 是两件事。

## 备份 ZIP

`latest.zip` 是导出和兼容备份，里面通常包含：

```text
<toolId>.json
<toolId>.md
attachments/...
```

例如：

```text
tracker.json
tracker.md
```

一页日记旧备份里可能是：

```text
diary.json
diary.md
attachments/...
```

服务端每次收到有效写入后，会：

1. 更新 SQLite 主库。
2. 把旧 `latest.zip` 复制到 `history/`。
3. 从 SQLite 当前状态重新生成新的 `latest.zip`。

所以 `latest.zip` 可以手动导出、导入，也可以作为额外保险，但不是主库。

## 启动和迁移

服务启动时，对每个启用同步的工具执行初始化：

1. 如果 SQLite 已存在且里面有 entries，直接使用 SQLite。
2. 如果 SQLite 不存在或为空，但有 `latest.zip`，从 ZIP 导入。
3. 迁移后读写都走 SQLite。

`state/*.json` 不再保留，也不再自动参与启动恢复。需要人工查看 JSON 时，从 `latest.zip` 或 `history/` 里的 ZIP 解包。

## 附件

附件不放进 SQLite 正文里，放在：

```text
sync/<stateName>/state/attachments/
```

entry 里只保存附件文件名：

```json
{
  "attachments": ["img-20260606-1231-1780720305001.jpg"]
}
```

服务端会限制附件文件名，避免路径穿越。导出 ZIP 时，仍会把有效 entry 引用的附件打进 `attachments/`。

## 常见排查

### 看服务状态

```bash
curl -s http://127.0.0.1:8788/api/status
```

### 看某个工具完整 entries

```bash
curl -s http://127.0.0.1:8788/api/tools/tracker/entries
curl -s http://127.0.0.1:8788/api/entries
```

### 看 SQLite 里有多少数据

```bash
node - <<'NODE'
const Database = require('better-sqlite3');
for (const [name, file] of Object.entries({
  diary: 'sync/diary/state/diary.sqlite',
  tracker: 'sync/tracker/state/tracker.sqlite',
})) {
  const db = new Database(file, { readonly: true });
  const rows = db.prepare(
    'select count(*) entries, sum(case when deleted_at is null then 1 else 0 end) active from entries'
  ).get();
  console.log(name, rows);
}
NODE
```

### 查某条 entry

```bash
node - <<'NODE'
const Database = require('better-sqlite3');
const db = new Database('sync/tracker/state/tracker.sqlite', { readonly: true });
const row = db.prepare('select * from entries where id = ?').get('1780797764063');
console.log(row);
NODE
```

### 从备份恢复

可以把备份 ZIP 上传到：

```text
PUT /api/tools/:id/latest
PUT /api/latest
```

服务端会替换当前 `latest.zip`，再从 ZIP 导入 SQLite。导入前会把旧 `latest.zip` 复制到 `history/`。

## 当前限制

这套方案已经比纯 JSON 稳定，但仍有后续优化空间：

- `GET /entries` 仍是全量返回，数据极大时 Web 端会慢。
- `POST /entries` 当前仍按 state 全量合并后保存，可以继续优化成逐条 SQLite upsert。
- `latest.zip` 每次写入都会重建，数据和附件很多时会变慢。
- 日常格 Android 本地还不是 SQLite。

这些限制不影响当前个人局域网使用；如果数据量继续增长，下一步优先优化服务端的按 id 查询和逐条 upsert。

## 重要原则

- 主存储看 SQLite，不看 JSON。
- 备份看 `latest.zip` 和 `history/` 里的 ZIP。
- 同步冲突看 `id`、`version`、`bodyHash`。
- 删除看 `deletedAt`，不要直接把 entry 从数据库删掉。
- 修数据前先备份 `sync/<stateName>/` 整个目录。
