# FuckBiliFolds 项目概况

## 目标
1. 去掉哔哩哔哩评论“折叠卡”
2. 把折叠评论拉出来按默认时间顺序排列
3. 给被折叠评论加标记（根评论不标）

## 当前方案
- 通过 Xposed/LSPosed Hook 评论数据流，在列表层将 `vv.r1`（折叠卡）替换成真实评论列表。
- 折叠评论来源：
  1. 自动发 `FoldListReq` 拉折叠列表
  2. 如果折叠返回只有 1 条，则再用该条的 `rootId` 发 `DetailListReq`，这相当于“复制评论链接进入详情”效果，能拿到完整折叠评论。
- 拉到的数据缓存到 `FOLD_CACHE_BY_OFFSET`，用 offset 映射到折叠卡替换。
- 替换后对评论按时间排序（仅默认排序模式），并去重。
- 被折叠评论打“折叠”标记，根评论不标。

## 关键 Hook 点
- `ReplyMossKtxKt.suspendFoldList`：折叠请求入口
- `ReplyMoss.foldList`：折叠响应
- `com.bilibili.app.comment3.data.source.v1.e` 的 `A0/F0/D0`：数据源映射
- `CommentListAdapter.b1`：评论列表提交点（替换折叠卡）
- `vv.r1`：折叠卡模型

## 核心逻辑文件
- `app/src/main/java/io/github/frisk1127/bilifolds/BiliFoldsHook.java`

## 最新关键改动
1. `auto.fetch` 如果只拿到 1 条折叠评论 → 自动发 `DetailListReq`（`fetchDetailList`）
2. 缓存时改用当前 offset（`curOffset`），避免错位
3. 替换时去重 + 按时间重排
4. 折叠评论标记（根评论不标）

## 当前状态
- 已能拉到 `auto.detail` 列表（日志显示 20 条）
- 替换逻辑已接入，但仍需验证 UI 是否完全符合目标
- 如 UI 仍不正常，需要结合截图/日志继续定位
