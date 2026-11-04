# TVBox 双端配置与播放记录同步设计（tvbox.sync/1）

> 版本 1.4 | 2026-11-04 | 状态：方案已审查修订，待编码
> 本文件在 `TVBox-mobile` 与 `TVboxOSC` 两个仓库各存一份，**内容必须完全一致**（修改时同步更新两份并比对 SHA-256）。
>
> | 简称 | 仓库根目录 | 说明 |
> |---|---|---|
> | M | `E:/Workspaces/person/TVBox-mobile` | 手机端，`applicationId com.kertou.tvplayer` / namespace `com.github.tvbox.osc` |
> | T | `E:/Workspaces/person/TVboxOSC` | Android TV 桌面端，`com.kukuqi.tvbox.osc` |

## 0. 修订摘要

| 版本 | 类别 | 修订 |
|---|---|---|
| v1.2 | **致命** | `HttpURLConnection` 只支持 `[GET, POST, HEAD, OPTIONS, PUT, DELETE, TRACE, PATCH]`，`PROPFIND`/`MKCOL`/`COPY`/`MOVE` 抛 `ProtocolException`；曾改为扁平文件方案 |
| v1.2 | **数据丢失** | 首次同步"以本机为准"= `PUT(local 全量)` 会覆盖云端，改为并集合并（§8.2） |
| v1.2 | 边界/测试/依赖 | 补 §15 边界表、§16 七类测试、修正"零新增依赖"表述、删除 6 项过度设计 |
| v1.3 | **依赖决策** | 按用户决策**统一 OkHttp 到 5.5.0**，恢复完整 WebDAV 方法（`MKCOL`/`PROPFIND`/`COPY`/`MOVE`），取消"用户必须手动建目录"限制；新增 §19 统一专项与 P0.0 门禁 |
| v1.3 | 第二轮边界 | 修 2MB 阈值无自愈、oplog 无限增长、首次并发创建、冲突副本/回滚点并发写、缺依赖树与网络回归门禁、`SyncHttp` 接口不得暴露 `okhttp3.*` 类型、T 端 CI 不编译 `androidTest` 导致 3.x 残留漏检 |
| **v1.4** | **业务逻辑** | 订阅删除遵循 `content` 组原子 LWW，被覆盖方写入 `conflicts.json`，解决三方并集导致已删源死灰复燃（僵尸源）缺陷（§9.2） |
| **v1.4** | **用户体验** | 退出播放/切集/停止播放时立即触发 Flush 同步（跳过 30s debounce），规避切后台休眠与设备间续播时延（§14） |
| **v1.4** | **防封保护** | 增加单机写入最小冷却间隔（5~10 秒），新增 HTTP 429 `Retry-After` 退避保护，避免坚果云等受限服务频控封禁（§10.5、§15） |
| **v1.4** | **技术栈对齐** | 按用户决策**全面现代化技术栈**：两端统一至 Gradle 9.7.1 + AGP 9.3.1 + JDK 21 + Kotlin 2.2.20 + `libs.versions.toml` 统一依赖版本库 + Java 21 脱糖（§19） |

## 1. 目标与 V1 范围

**目标**：任意 N 台客户端通过用户自建的第三方存储（V1 = WebDAV，后续 S3）双向同步「订阅/播放等配置」「观看历史」「收藏」「播放进度（续播）」，无中心服务器、无账号体系，不丢、不重、可处理并发修改。

**V1 做**：统一 OkHttp 版本；WebDAV 存储（自动建目录、完整方法）；多端对等 + `spaceId` 多空间；语义化 JSON 快照 + 本地脏增量；记录级合并 + 删除墓碑/批量清空 + 乐观锁；配置事务组 + 冲突副本；设置页配置与手动同步；启动/写入自动同步；配置变更后首页立即重载；播放设置默认同步；完整 JVM 单测与依赖门禁。

**V1 不做**：S3（预留接口）、端到端加密、局域网 `do=sync` 桥接、T 端引擎 `tv` 库的 `History`/`Keep`、离线媒体文件、订阅配置缓存文件、`csp.jar`、云端历史条数上限、WebDAV Digest 认证。

## 2. 现状核查与复用评估

| 能力 | 位置 | 判定 |
|---|---|---|
| 局域网 `POST /action?do=sync` | T `engine/core/.../server/process/Action.java:135-237` | 只作用于引擎新库 `tv` 的 `History`/`Keep`，而 UI 读旧库 `tvbox.v3.db`；缺 UI 入口、自动触发、认证、TLS、墓碑。**不同步用户可见数据** |
| `BackupManager`（`.tv` gzip） | T `engine/core/.../db/BackupManager.java:21-103` | 实现完但无调用者 |
| 本地备份还原 | M/T `app/.../ui/dialog/BackupDialog.java` | 手动导出 `sqlite` + `hawk` 平铺 JSON，整库覆盖 + 重启 |
| WebDAV/SMB 依赖 | T `gradle/libs.versions.toml:48`、`engine/catvod/build.gradle:31` | 声明 `sardine-android:0.9` / `smbj`，Java/Kotlin 零调用 |
| 配置推送 `do=api/push` | M/T `server/InputRequestProcess.java` | `push` 标注"暂未实现"；`TYPE_API_URL_CHANGE`/`TYPE_PUSH_URL` 无消费者 |

**可复用**：两端近乎一致的 `Hawk` 键、`tvbox.v3.db` 三表 `vodRecord`/`vodCollect`/`cache` 及 DAO、一致的进度键 `MD5(sourceKey + vodId + playFlag + playIndex + 集名)`、Hawk 加密存凭据、两端已有 `INTERNET`/`ACCESS_NETWORK_STATE`/`usesCleartextTraffic`（均已核实，Manifest 无需改动）、Gson 2.8.7。

**不可复用**：`sqlite` 二进制备份（两端 `@Database` 实体集不同，T 的 `restore()` 校验 `identity_hash` 会拒绝，整库覆盖丢数据、含 `cipher_key`）、`cid`/`History.key`（设备本地自增）、`RoomDataManger.getAllVodRecord()` 作为导出源（§7）、`CACHE_CONFIG_CHANGED=true` 作为重载信号（§11.1）。

**关键事实**：两端 UI 的历史/收藏/续播/进度全部读写旧库 + Hawk，T 的引擎 `tv` 库对 app UI 是未被消费的平行数据，故 V1 适配器约 95% 共用。`sourceKey` 来自订阅 `sites[].key`，只在单个订阅文档内唯一，跨端主键必须叠加配置标识。

## 3. 设备模型与 `spaceId`

协议**没有 M/T 角色、没有主从、没有端数量上限**。`deviceId` 是首次启动生成并持久化在 `sync_meta` 的 UUID；`platform` 只是展示标签与能力提示，不参与合并判定。两台手机、两台 TV、手机+TV+平板、同 APK 多实例、不同 fork 的安卓客户端，只要实现 `tvbox.sync/1` 并指向同一 WebDAV 目录 + 同一 `spaceId` 即可互相同步。

- `extras` 按平台命名空间隔离；`player.profile` 按 `getExistPlayerTypes()` 的**实际能力**映射（不按平台硬编码）。
- 跨 fork 差异靠 `payload` 原始 `VodInfo` JSON 透传；`configHash` 让两端用不同订阅也不串源。
- 硬约束：`normalize(apiUrl)` 规则、`schema` 版本、WebDAV 根目录、`spaceId` 一致。将来桌面/Web 端实现同一协议可直接加入，协议不变。
- `devices` 注册表记录 `name`/`platform`/`lastSeen`/`lastAckRevision`；**只保留最近 20 台或 `lastSeen` 90 天内**的设备。`lastAckRevision` 只用于检测云端被重置（§15），不用于墓碑回收。

**`spaceId`**：默认 `default`，取值 `[A-Za-z0-9_-]{1,32}`，禁止 `.`/`..`/路径分隔符（防路径穿越）。对象放在 `<root>/tvbox/<spaceId>/`（v1.3 由 `MKCOL` 自动创建）。切换空间视为新空间：本地确认点与 oplog 按空间隔离，重新走首次同步选择，`deviceId` 不变。

## 4. 已确认决策清单

| # | 决策 | 落地约束 |
|---|---|---|
| 1 | 播放设置默认同步 | `sync_scope_player_settings` 默认 `true`；语义化映射 + 本机能力校验（§6.4） |
| 2 | V1 忽略 T 端引擎 `tv` 库的 `History`/`Keep` | 后续追加 `EngineTvSyncAdapter`，协议不变 |
| 3 | 配置变更让首页立即重载 | 删配置缓存 + 重建首页（§11） |
| 4 | 设计文档两仓库各一份 | 内容一致，SHA-256 比对 |
| 5 | 协议 N 端对等 | §3 |
| 6 | 清空历史/收藏为全局删除，文案「清空所有设备记录」+ 二次确认 | 批量 `clears`（§8.3） |
| 7 | 同组配置冲突：后写胜 + 冲突副本 + 可强制覆盖 | 不弹窗打断 TV 观看（§9） |
| 8 | 云端历史不设上限；增加 `spaceId` | §3、§6.2 |
| 9 | 测试文件纳入范围、零不必要依赖、去过度设计 | §12、§16 |
| 10 | **引入统一版本的 OkHttp（用户决策）** | 统一到 5.5.0，恢复完整 WebDAV 方法；专项方案与门禁见 §19 |
| 11 | 订阅遵循 `content` 组 LWW | 彻底解决已删源被并集复活，被覆盖方记入 `conflicts.json`（§9.2） |
| 12 | 退出播放/切集立即 Flush 同步 | 播放中只记本地 oplog，退出/切集立即提交网络，消除切端续播时延（§14） |
| 13 | WebDAV 写入频控与 429 退避保护 | 最小写入间隔 5~10 秒，429 读取 `Retry-After` 暂停自动同步（§10.5、§15） |
| 14 | **双端技术栈全面对齐（用户决策）** | 统一升级至 Gradle 9.7.1 + AGP 9.3.1 + JDK 21 + Kotlin 2.2.20 + `libs.versions.toml`（§19） |

## 5. 总体架构

**同步模型：云端全量快照 + 本地脏增量 oplog。** 本地状态只是云端快照一个可能过期的投影，全量导出比对会回滚对端新数据；只有"本端真正改过"的项参与合并竞争，其余沿用云端。

```
┌ 纯 Java 层（包名 com.tvbox.sync，两端源码逐字复制）
│  约束：仅 Java 7 语法/API + Gson；禁止 org.json、android.*、java.time、
│        java.util.Base64、以及 okhttp3.* / okio.* 类型（见下）
│  协议  SyncSnapshot / SyncConfigGroup / SyncPlayer / SyncHistory
│        / SyncCollect / SyncProgress / SyncClear / SyncDevice
│        SyncProtocol（schema、normalize、configHash、makeId、validateSpaceId、checksum）
│  合并  SyncMerger（纯函数 merge(cloud, dirtyOps)）
│  状态  SyncOpLog（接口 + 内存实现；Android 侧 SP 实现注入）、SyncMeta
│  传输  SyncHttp 接口（execute(method,url,headers,body) -> SyncHttpResponse DTO）
│        SyncStorage（read/write/ensure/etag/copy/move）
│        WebDavStorage（基于 SyncHttp，使用统一版 OkHttp 支持的全部方法）
│  编排  SyncEngine（乐观锁循环、冲突副本、checksum 校验）
├ Android 平台层（两端各一份）
│  OkHttpSyncHttp（唯一依赖 okhttp3.* 的类）、SharedPrefsSyncStore、
│  SyncSettings(Hawk)、AppRoomSyncAdapter、SyncUiReloader、SyncTrigger、SyncDialog
└ 触发：启动拉取 + 写入 debounce + 手动
```

**关键分层约束**：`SyncHttp` 的请求/响应使用自定义 DTO（`SyncHttpRequest`/`SyncHttpResponse`），**接口签名里不出现 `okhttp3.*` 或 `okio.*` 类型**。这样纯 Java 层与合并层不依赖 OkHttp，JVM 单测可用内存 fake（§16），且将来换 HTTP 实现不影响协议。`okhttp3.Credentials.basic()`、`XmlPullParser` 等平台能力只在 `OkHttpSyncHttp` 内使用。

统一 OkHttp 的版本选择、两端改动清单、兼容门禁与回滚路径见 **§19**；该专项是 P0 的**前置门禁 P0.0**，未通过不得写同步代码。

## 6. 统一协议 `tvbox.sync/1`

### 6.1 完整字段（精简示例）

```json
{
  "schema": "tvbox.sync/1",
  "spaceId": "default",
  "revision": 128,
  "updatedAt": 1762214400000,
  "writer": "uuid-mobile",
  "checksum": "<SHA-256 of all fields except checksum>",
  "devices": {
    "uuid-mobile": {"name": "手机", "platform": "mobile",
                    "lastSeen": 1762214400000, "lastAckRevision": 128}
  },
  "config": {
    "groups": {
      "content": {"rev": 120, "writer": "uuid-mobile",
        "apiUrl": "https://example.com/config.json",
        "subscriptions": [{"name":"源A","url":"https://example.com/a.json",
                           "checked":true,"top":false,"builtIn":false}],
        "homeApi": "", "liveUrl": "", "epgUrl": "", "defaultParse": ""},
      "player": {"rev": 119, "writer": "uuid-tv",
        "profile": "exo", "render": 0, "scale": 0, "speed": 2.0,
        "subtitleSize": 24, "subtitleDelay": 0, "timeStep": 1,
        "background": 2, "codec": "硬解码", "purify": true, "ijkCache": false},
      "network": {"rev": 90, "writer": "uuid-mobile", "dohUrl": 0}
    },
    "extras": {
      "tv": {"wallpaper_url": "...", "saved_live_sources": "[]"},
      "mobile": {"download_wifi_only": false}
    }
  },
  "history": [
    {"id": "a1b2c3d4:siteKey:12345", "configHash": "a1b2c3d4",
     "sourceKey": "siteKey", "vodId": "12345", "title": "片名", "pic": "https://...",
     "playFlag": "线路1", "playIndex": 3, "playNote": "第4集", "position": 1234000,
     "rev": 127, "updatedAt": 1762210000000, "deleted": false,
     "payload": "{原始 VodInfo JSON}"}
  ],
  "collects": [
    {"id": "a1b2c3d4:siteKey:12345", "configHash": "a1b2c3d4",
     "sourceKey": "siteKey", "vodId": "12345", "title": "片名", "pic": "https://...",
     "type": 0, "rev": 100, "updatedAt": 1762200000000, "deleted": false}
  ],
  "progress": [
    {"key": "<MD5(progressKey)>", "position": 1234000, "duration": 0,
     "rev": 127, "updatedAt": 1762210000000, "deleted": false}
  ],
  "clears": {
    "history": {"rev": 60, "writer": "uuid-tv", "updatedAt": 1762100000000}
  }
}
```

| 字段 | 规则 |
|---|---|
| `revision` | 全局逻辑时钟，每次成功写 `+1`；记录级 `rev` 取写入时 `cloud.revision+1`。比较顺序 `rev > updatedAt > writer 字典序` |
| `checksum` | 除自身外全字段规范 JSON 的 SHA-256；读取时校验，失败视为快照损坏（§15） |
| `config.groups` | 三个事务组（§9），**只有 dirty 组参与合并** |
| `id` | `configHash + ":" + sourceKey + ":" + vodId`；`configHash = MD5(normalize(apiUrl)).substring(0,8)`，`normalize` 去掉 `;pk;` 后缀并 trim。禁止用 `cid`/`History.key` |
| `progress.key` | `MD5(progressKey)`，两端拼接算法一致，可原样写回 `cache` 表 |
| **进度唯一权威** | `progress[]` 是唯一进度源；`history[].position` 为派生展示字段，合并时从 `progress` 回填，不参与冲突判定 |
| `payload` | 各端原始 `VodInfo` JSON 透传，Gson 忽略未知字段 |
| `deleted` | 墓碑**只存 `id`/`rev`/`updatedAt`/`deleted`，不含 payload**；永久保留（§8.4）；"本地没有"永不表示删除 |
| `clears` | 按 `scope` 索引的 map，每个 scope 只留最大 `rev` 的一条（§8.3） |
| `extras` | 按 `platform` 隔离，对端不认识就不写入 |
| **永不上传** | `sync_*`、`cipher_key`、`crypto.KEY_256`、`Hawk2` 全量、`now_date`、`remote_tvbox_host`、`theme_tag`、`private_browsing` |

### 6.2 云端历史不设上限

历史/收藏/进度在快照里是按 `id`/`key` 索引的 map，同片反复观看只更新同一条；墓碑按 `id` 只留最新状态。不设条数上限可控。本机展示仍受 `HISTORY_NUM`（30/50/70）限制（§7）。体积保护见 §15：超阈值先自动压缩，压缩后仍超限才暂停并给出可操作入口（不是永久卡死）。

### 6.3 配置同步范围

| 类别 | 键 | 默认 |
|---|---|---|
| `content` 组 | `api_url`、`api_history`、`live_url`、`epg_url`、`home_api`、`parse_default` | 同步 |
| `player` 组 | `play_type`、`play_render`、`play_scale`、`video_speed`、`subtitle_text_size`、`subtitle_time_delay`、`play_time_step`、`background_play_type`、`video_purify`、`ijk_cache_play`、`ijk_codec` | 同步（决策 1） |
| `network` 组 | `doh_url` | 同步 |
| 界面/设备类 | `theme_tag`、`home_rec`、`poster_size`、`wallpaper_*`、`saved_*_sources`、`download_*`、`danmaku_*`、`source_descriptor_*`、`hot_search_*` 等 | 不同步，或进 `extras` |
| 隐私/本地态 | `private_browsing`、`now_date`、`remote_tvbox_host`、更新相关键 | 不同步 |

### 6.4 播放设置跨端映射

两端 `PLAY_TYPE` 数值语义不一致，且可选值依赖本机安装的播放器：

| profile | M `PLAY_TYPE` | T `PLAY_TYPE` | 说明 |
|---|---|---|---|
| `system` | 0 | — | 回退 `exo` |
| `ijk` | 1 | — | T 的 1 是 MPV，不可套用 |
| `mpv` | — | 1 | 回退 `exo` |
| `exo` | 2 | 0 或 2 | 两端恒可用，统一兜底 |
| `mx`/`reex`/`kodi`/`remote`/`vlc` | 10/11/12/13/14 | 同 | 依赖安装情况 |

`apply()` 用 `getExistPlayerTypes()` 校验映射值，不在集合内回退 `exo` 并记入 `skippedKeys`；值域钳制：`play_render` 0..1、`play_scale` 0..5、`video_speed` ∈ {2,3,4,5,6,8,10}、`subtitleSize` 12..60、`timeStep` ≤300、`background` 0..2、`doh_url` 0..6、`codec` ∈ 本机 `ijkCodes`。写入后补 `PlayerHelper.init()`、`doh_url` 变更需 `OkGoHelper.updateDns()`/`dnsOverHttps.setUrl(...)` + `IjkMediaPlayer.toggleDotPort(...)`、字幕发 `TYPE_SUBTITLE_SIZE_CHANGE`。

## 7. 本地存储适配约束（不丢、不重）

1. **导出/应用直走 DAO 全量，禁止 `RoomDataManger.getAllVodRecord()`**：它先 `reserver(hisNum)` 物理删除超出 30/50/70 条的行（`RoomDataManger.java:84-90`、`VodRecordDao.java:38-39`），还会把 `ApiConfig.get().getSource(sourceKey) == null` 的记录静默丢弃（`:101-103`）。
2. **`apply` 复用自增 `id`**：`VodRecord.id` 自增，`insert(REPLACE)` 只在 `id` 冲突时替换；直接 `new VodRecord()` 会让同一 `(sourceKey, vodId)` 出现两行。先 `getVodRecord(...)` 取回并复用 `id`，收藏同理。`cache` 表主键为 `key`，`save(REPLACE)` 天然幂等。
3. **只落地云端前 `hisNum` 条历史**，`record.updateTime` 写**云端 `updatedAt`**（非本机当前时间），否则同步记录会挤掉本机真实最近观看并产生"写入全量→被裁→再写入"震荡。**本地裁剪绝不生成墓碑**。
4. **删除必须显式墓碑/清空标记**：`HistoryActivity.kt:42` 删单条、`:58` 清空、`CollectActivity.kt` 清空收藏、`PlayFragment.java:1249` 重播清进度都是无痕物理删除，全部接入 oplog。
5. **配置合法性校验**：`apiUrl` 为空或非 `http(s)://` 时保留本机值并记 `skippedKeys`；`homeApi` 不在当前内存源列表时写空（让 `parseJson` 回退 `firstSite`），避免二次脏变更引发同步震荡。

`apply` 用 Room `@Transaction` 单事务；写云端成功才 apply；崩溃后重启从 `lastRevision` 重放 oplog。

## 8. 同步流程

### 8.1 本地脏增量 oplog

每次写/删追加一条到 `sync_meta`（无需 DB 迁移），**每条带 `spaceId`**：

```json
{"seq":12,"spaceId":"default",
 "entity":"history|collect|progress|config.content|config.player|config.network|extras",
 "id":"a1b2c3d4:siteKey:12345","op":"upsert|delete|clear","data":{...},"ts":1762210000000}
```

- `merge(cloud, dirtyOps)` 只对脏项参与竞争；成功后按 `seq <= lastAckSeq` 清理本空间已确认条目；失败/崩溃保留并重放。
- **oplog 增长控制**：同一 `(spaceId, entity, id)` 只保留最后一条 op（`upsert` 覆盖前值；`delete` 覆盖 `upsert`）；未确认条目上限 5000，达到后把最旧的 `upsert` 合并为一次全量基线上传，避免长期离线 + 高频观看导致无限增长。
- 同步开始时**冻结当前最大 `seq`**，期间新增的 op 留到下一轮，避免与用户操作竞态。

### 8.2 主流程（乐观锁循环，最多 5 次 + 抖动退避）

```
sync(mode = BIDIRECTIONAL | UPLOAD_ONLY | DOWNLOAD_ONLY)
 1  ensure() 逐级 MKCOL <root>/tvbox/<space>/（405 视为已存在）
 2  GET sync.json                  → 404 = 首次；200 取 body + ETag + 校验 checksum
 3  readOps(spaceId)              → 本空间未确认 oplog（冻结 seq 上限）
 4  首次/云端重置分支（见下）
 5  merged = SyncMerger.merge(cloud, ops)
      集合按 id/key 归并（rev → updatedAt → writer）；墓碑 rev 更大则 deleted
      配置仅 dirty 组参与组级 LWW；未 dirty 组沿用云端
      clears 按 scope 取最大 rev；progress 回填 history.position
      merged.revision = cloud.revision + 1；未变化不重写
 6  hash(merged) == hash(cloud)   → 跳过写，仅 apply
 7  写云端：优先 PUT 临时文件 + MOVE 原子替换（§10.2），
      带 If-Match "<etag>"（首次 If-None-Match: *）；不支持则降级 read-verify-write；
      两次写云端受 5~10s 最小冷却保护，遇 429 暂停自动同步（§10.5）
      412/409/428 → 回到 2 重新 GET 并重新 merge，最多 5 次
 8  写冲突副本 / lastgood 回滚点（best-effort，失败不阻断主流程）
 9  adapter.apply(merged)         → Room @Transaction REPLACE / 墓碑 delete / Hawk 写配置
10  reloader.onApplied(change)    → 配置变化时立即重载首页（§11）
11  ack：清 oplog、更新 lastRevision/lastHash/lastAckRevision/lastSeen
12  失败只提示，不改本地数据；同步任务串行（单线程 + 进程内锁），仅主进程执行
```

**首次/重置分支**：

- 云端 404：把本端记录作为 **dirty upsert 并集**写入新快照，**不是 `PUT(local 全量)`**。
- **多设备同时首次创建**：两个设备同时 `If-None-Match: *` 时只有一个成功，失败方收到 412/405 后回到步骤 2 重新 GET 并重新 merge（不允许直接覆盖）；5 次仍失败则提示"云端正被其它设备初始化"。
- 「以本机为准」= 本端记录作为脏项合并进云端（并集），云端其余保留；「以云端为准」= 只 apply 云端，本端未上传脏项**保留到下一轮**。
- 只有用户显式「重置本空间」才允许清空云端，需二次确认 + 先写 `lastgood`。
- `cloud.revision < local.lastAckRevision` → 判定云端被删除/重置：**不清 oplog**，强制走首次选择。

**模式语义**：`UPLOAD_ONLY` 只推脏项、不 apply 云端；`DOWNLOAD_ONLY` 只 apply 云端胜出项、不推脏项（保留待下轮）、不删本机记录（墓碑/clears 除外）。三者都遵循"本地没有 ≠ 删除"。

### 8.3 全局清空（决策 6）

清空历史/收藏写一条 `clears[scope] = {rev, writer, updatedAt}`。合并时凡 `rev <= clear.rev` 的记录标记 `deleted`，`rev > clear.rev` 的写入（清空后本端又看了一集）保留。按钮文案**「清空所有设备记录」** + 二次确认说明"将删除所有已同步设备上的记录"；本地立即清空，标记走 oplog。收藏与历史各自独立 `scope`。

### 8.4 墓碑回收：永久保留 + 压缩

v1.0 的"90 天 TTL"会让离线半年设备上线后复活已删数据。V1 改为**永久保留**，墓碑只存 `id`/`rev`/`updatedAt`/`deleted`，配合按 `id` 压缩控制体积；不按时间、不按设备 ack 回收。

## 9. 并发修改配置的策略

### 9.1 为什么逐字段 LWW 不可用

`apiUrl` ↔ `subscriptions` ↔ `homeApi` ↔ `defaultParse` 互相依赖。反例：A 换订阅到 X 并把 `homeApi` 设为 `siteX`，B 同时设为旧订阅的 `siteY`；逐字段合并得到 `apiUrl=X + homeApi=siteY` → 首页空白。而"配置整体一个 rev 谁新谁全赢"会丢掉 A 换订阅 / B 改播放器这类常见并发中的一方修改。

### 9.2 配置事务组

| 组 | 字段 | 合并 |
|---|---|---|
| `content` | `apiUrl`、`subscriptions`、`homeApi`、`liveUrl`、`epgUrl`、`defaultParse`、`parse_default` | 整组原子 LWW，按 `group.rev` |
| `player` | 整个 `player` 对象 | 整组原子 LWW |
| `network` | `dohUrl` | 整组原子 LWW |
| `extras.<platform>` | 单端特有 | 按平台隔离，各自 LWW |

- **不同组并发 → 都保留**；**同组并发 → 后写胜 + 不静默丢弃**（决策 7）：被覆盖一方的完整快照追加到 `conflicts.json`（单文件，内嵌最近 20 条，含双方值/写者/时间）。设置页「最近冲突」提示"云端配置由「客厅 TV」更新，本机改动已存入冲突记录"，提供「以本机为准强制覆盖」（把本地组 `rev` 设为 `cloud.revision+1` 重走循环）与纯文本查看差异（**不引入 diff 库、不弹窗打断观看**）。
- `subscriptions` 随 `content` 组整体**原子 LWW**（决策 11）：以获胜方的完整订阅列表为准（包含新增、删除、改名与排序）；被覆盖方的全部列表完整记入 `conflicts.json`（单文件最近 20 条），提供「以本机为准强制覆盖」与查阅对比。**禁止无墓碑的三方并集累加**，彻底避免手机端删除失效源后被云端或 TV 端复活为「僵尸源」。`apiUrl`/`homeApi` 遵循获胜组的值，`homeApi` 无效时回退 `firstSite`。
- **未 dirty 的组不上传**，避免"没改配置的一端把对端修改回滚"。
- `conflicts.json` / `lastgood.json` 为 **best-effort 写入**：多端同时写可能覆盖，用 `If-Match` + 1 次重试；失败只记日志，不阻断主快照同步。

### 9.3 N 端原子性

三台设备同时改不同组时，三次写被乐观锁串行化，每次冲突后重新 GET + 重新 merge，最终三组修改都在快照里。`revision` 全局单调，`rev` 来自 `revision` 而非墙钟。重试 5 次 + 抖动退避覆盖多端并发。

## 10. WebDAV 存储层（v1.3 恢复完整方法）

### 10.1 统一 OkHttp 后的能力

统一到 OkHttp 5.5.0 后可用 `Request.Builder.method(name, body)` 发送任意 HTTP 方法，v1.2 的方法白名单限制消失：

| 方法 | 用途 |
|---|---|
| `MKCOL` | `ensure()` 逐级创建 `<root>/tvbox/<spaceId>/` 与 `conflicts/`（405 视为已存在）；**用户不再需要手动建目录** |
| `GET` / `HEAD` | 读快照、读取响应头 |
| `PROPFIND Depth:0` | 当 GET 不带 ETag 时取 `<d:getetag>`；XML 解析在平台层用 Android 内置 `XmlPullParser`（不引第三方 XML 库），纯 Java 层只接收解析出的字符串 |
| `PUT` | 写快照 / 临时文件；带 `If-Match` / `If-None-Match: *` |
| `MOVE` | 临时文件 → `sync.json` 的原子替换（`Overwrite: T` + `If-Match`）；服务端不支持时降级 |
| `COPY` | 成功写入后覆盖一份 `lastgood.json` 回滚点（单份，不做 5 份轮换） |
| `DELETE` | 清理临时文件 / probe 文件 |

`SyncStorage` 接口：`read(name)`、`write(name, bytes, etag, create)`、`ensure()`、`etag(name)`、`copy(src, dst)`、`move(src, dst)`、`delete(name)`、`probe()`。

**目录布局**：

```
<root>/tvbox/<spaceId>/
  sync.json              权威快照
  conflicts.json         冲突副本（单文件，最近 20 条）
  lastgood.json          上一次成功写入的回滚点
  .tmp-<uuid>            原子替换临时文件，失败时 DELETE 清理
```

`spaceId` 已过白名单校验，路径拼接前再次校验不含 `..`。「测试连接」：`MKCOL` 探测 → `PUT` probe → `DELETE` probe，验证建目录与写权限。

### 10.2 原子写与乐观锁

1. 优先路径：`PUT sync.json.tmp-<uuid>`（无 `If-Match`）→ `MOVE` 到 `sync.json`，带 `Overwrite: T` 与 `If-Match: <etag>`；服务端不支持 `MOVE` 或返回 405/501 → 降级为直接 `PUT sync.json` + `If-Match`。
2. ETag 优先取 `GET` 响应头 `ETag`/`OC-ETag`，缺失时用 `PROPFIND Depth:0` 取 `getetag`。
3. 若服务端既不给 ETag 也不支持 `If-Match`（部分坚果云场景），降级 **read-verify-write**：记录 `hash(body)`，写前再 GET 比对，写后再 GET 校验 `writer == 本设备` 且 `revision == 期望`；不一致则重新 merge 重试。该窗口存在极小 TOCTOU，但能**检测**覆盖而非静默丢数据。
4. 临时文件残留：下次 `ensure()` 时按 `.tmp-` 前缀 `PROPFIND` 列出并 `DELETE`（best-effort）。
5. `lastgood.json` 与 `conflicts.json` 写入失败不阻断主流程；`lastgood` 自身也带 `checksum`，损坏时忽略。

### 10.3 重定向与认证

- 关闭自动跟随（`followRedirects(false)`），手动处理 301/302/307/308：**保留 method 与 body**，最多 3 跳；目标 host 与配置不一致时**丢弃 Authorization 并报错**（OkHttp 跨主机重定向默认会剥离 Authorization，但仍需显式校验最终 URL，防静默跳到第三方域）。
- Basic Auth 用 `okhttp3.Credentials.basic(user, password)`（v1.2 的"自实现 Base64"删除，OkHttp 内部用 okio 处理，不依赖 `java.util.Base64`）。
- 仅支持 Basic（应用密码），Digest 明确不支持并提示；强制 `https://`（`http://` 仅局域网且二次确认）。

### 10.4 配置项与安全

`sync_enabled`、`sync_backend`(webdav)、`sync_url`、`sync_user`、`sync_password`、`sync_space_id`(默认 `default`)、`sync_auto`、`sync_wifi_only`、`sync_scope_*`（默认全 true）。凭据只放请求头不落日志；`sync.*` 不进入快照；`cipher_key` 不上传；错误脱敏。

S3 扩展点：`SyncStorage.read/write/ensure` 可映射到 GET/PUT Object 与 bucket 创建，后续 `S3Storage`（SigV4 用 `Mac/HmacSHA256`）即可，协议与合并层零改动；条件写能力按后端实际支持实现。

### 10.5 频控保护与 HTTP 429 处理（决策 13）

国内用户最常用的 WebDAV 后端为坚果云与 Alist。坚果云免费版有极其严苛的单分钟/单日请求频率限制（单次同步含 PUT/MOVE/COPY 多个请求，高频触发极易封号返回 429）：

1. **单机写入最小冷却**：除首次初始化或用户手动点击「立即同步」外，自动同步在两次成功的写云端之间强制执行 **5~10 秒的最小冷却间隔**。冷却期内产生的新 oplog 积攒在本地，冷却倒计时结束后一次性合并上传。
2. **HTTP 429 处理**：
   - 收到 HTTP 429 (Too Many Requests) 或含有 `Retry-After` 头时，解析等待秒数（若无则默认退避 60 秒）；
   - **立即停止当前与后续的自动同步**，进入冷静期；本地 oplog 完好保留；
   - UI 状态显示「云端限频冷却中（XX 秒后可重试）」，不反复重试避免账号被长时间封禁；冷却结束后自动恢复。

## 11. 配置变更与首页立即重载（决策 3）

### 11.1 已核实的坑

- `CACHE_CONFIG_CHANGED = "use_cache"`，`true` 语义是「只读旧缓存、跳过网络」（M `MainActivity.kt:51` → `HomeFragment.kt:124` → `ApiConfig.java:152-160`），与"立即重载"相反。
- M 端 `ApiConfig.loadConfig(false, ...)` 在 `filesDir/MD5(apiUrl)` 缓存存在时仍「先旧缓存渲染 + 静默刷新，下次冷启动生效」（`:182-191`）→ **必须删除缓存文件**。
- 两端 `TYPE_API_URL_CHANGE(8)`/`TYPE_PUSH_URL(9)` 是死事件；`HomeFragment` 两端都未注册 EventBus。
- 现有重载都是**重建 Activity**：M 用 `FLAG_ACTIVITY_CLEAR_TASK` 新启 `MainActivity`（`HomeFragment.kt:351-358`）；T 用 `MainActivity.onResume()` 检测 `sourceUrl != Hawk.get(API_URL)` → `removeExtra(CACHE_CONFIG_CHANGED)` + `recreate()`（`:111-125`）。
- 无需 `ApiConfig.clear/reset()`：`parseJson()` 内部已清空 `sourceBeanList`/`parseBeanList`/`liveChannelGroupList` 并重设 `mHomeSource`。

### 11.2 `SyncUiReloader.onApplied(ConfigChange change)`

1. **删缓存**：删除 `filesDir/MD5.encode(apiUrl)`。
2. **播放设置生效**：`PlayerHelper.init()`；`dohChanged` → `OkGoHelper.updateDns()`/`dnsOverHttps.setUrl(...)` + `IjkMediaPlayer.toggleDotPort(...)`；`subtitleChanged` → `TYPE_SUBTITLE_SIZE_CHANGE`；提示 `skippedKeys`。
3. **首页重载**：M → `Intent(MainActivity)` + `CLEAR_TASK|NEW_TASK`，**不带** `use_cache` extra → 全新 `HomeFragment` → `loadConfig(false)` 联网 → `parseJson` → `getSort` → `initViewPager`。T → 向 `MainActivity` 发新增 `SyncConfigChangedEvent`（在 `MainActivity` 订阅）→ `removeExtra(CACHE_CONFIG_CHANGED)` + `recreate()`。
4. **播放中不打断**：`DetailActivity`/`PlayFragment`/`LocalPlayActivity` 在前台时只置 `pendingReload`，`MainActivity.onResume()` 检查后执行并清除。用户在 `SubscriptionActivity` 时配置写入延后到该页 `onPause` 之后。
5. **幂等**：`changed == false` 不重载；1000ms 内合并为一次。

## 12. 类结构（约 14 个）

| 类 | 职责 | 两端 |
|---|---|---|
| `model/SyncSnapshot.java` | 顶层 DTO（`spaceId`/`checksum`/`devices`/`clears`）+ Gson | 逐字相同 |
| `model/SyncConfigGroup.java` / `SyncPlayer.java` / `SyncHistory.java` / `SyncCollect.java` / `SyncProgress.java` / `SyncClear.java` / `SyncDevice.java` | 子 DTO | 逐字相同 |
| `SyncProtocol.java` | `SCHEMA`、normalize、configHash、makeId、validateSpaceId、checksum、schema 迁移 | 逐字相同 |
| `SyncMerger.java` | 纯函数 `merge(cloud, dirtyOps)`：事务组、三方并集、clears、墓碑、progress 回填 | 逐字相同 |
| `SyncOpLog.java` / `SyncStore.java` | 脏增量（接口 + 内存实现）；平台存储接口 | 逐字相同 |
| `SyncHttp.java` / `SyncHttpRequest.java` / `SyncHttpResponse.java` | 自定义 DTO 的 HTTP 抽象，**不暴露 okhttp3.* 类型** | 逐字相同 |
| `WebDavStorage.java` | GET/PUT/MKCOL/PROPFIND/MOVE/COPY/DELETE、乐观锁、重定向 | 逐字相同 |
| `SyncStorage.java` / `SyncEngine.java` | 门面；乐观锁循环、重试、冲突副本、checksum 校验 | 逐字相同 |
| `adapter/SyncAdapter.java` / `AppRoomSyncAdapter.java` | `exportDirty()`/`apply()`，§7 约束 | 95% 相同 |
| `OkHttpSyncHttp.java` / `SharedPrefsSyncStore.java` / `SyncSettings.java` | Android 平台实现；**唯一依赖 okhttp3.*** 的类 | 两端各一份 |
| `SyncUiReloader.java` / `SyncTrigger.java` / `ui/SyncDialog.java` | 重载、触发、UI | 两端各一份 |

**已删除的过度设计**：`legacyType`、`SyncTombstone.java`、ack 驱动墓碑回收、`listConflicts()`、5 份 backup 轮换、diff 组件、`org.json`、自实现 Base64。

共享策略：以 M 为权威源，T 复制同一目录；记录各文件 SHA-256，两端都跑同一套 JVM 单测防协议漂移。

## 13. 改动清单

### 13.1 M = `E:/Workspaces/person/TVBox-mobile`

| 文件 | 动作 |
|---|---|
| `app/src/main/java/com/tvbox/sync/**` | 新增，§12 全部 |
| `app/src/main/res/layout/dialog_sync.xml`、`values/strings.xml` | 新增（含 `spaceId`、冲突记录列表） |
| `util/HawkConfig.java` | 追加 `SYNC_*` 常量 |
| `res/layout/activity_setting.xml`（`llBackup` 约 564-576 行后） | 加 `llCloudSync` |
| `ui/activity/SettingActivity.kt`（`llBackup` 绑定 124-153 行） | 点击打开 `SyncDialog` |
| `base/App.java` | 延迟 2s 初始化 `SyncTrigger`，仅主进程 |
| `ui/activity/DetailActivity.java:760`、`ui/activity/LocalPlayActivity.java:198`、`ui/fragment/PlayFragment.java:219-230,1249`、`ui/activity/HistoryActivity.kt:42,58`、`ui/activity/CollectActivity.kt` | 写/删/清空后写 oplog（只发信号） |
| `ui/activity/HistoryActivity.kt:49-69`、`ui/activity/CollectActivity.kt` | 文案「清空所有设备记录」+ 二次确认 |
| `ui/activity/MainActivity.kt` | `onResume()` 消费 `pendingReload`；`onNewIntent()` 支持强制重载 |
| `ui/dialog/BackupDialog.java:141-150` | 导出 `Hawk2` 时**过滤 `sync_*` 键** |
| `app/build.gradle`、`player/build.gradle` | OkHttp 统一改造，见 §19.3 |
| `app/src/main/java/okhttp3/dnsoverhttps/**`、`util/OkGoHelper.java`、`util/js/Connect.java`、`util/urlhttp/BrotliInterceptor.java`、`com/github/catvod/net/OkHttp.java`、`player/.../OkHttpDataSource.java` 等 | OkHttp 5.x API 适配，见 §19.3 |
| `app/src/test/java/com/tvbox/sync/**` | **新增 7 个测试文件，见 §16** |
| `.github/workflows/test.yml` | 加依赖树断言与单测任务，见 §19.4 |

### 13.2 T = `E:/Workspaces/person/TVboxOSC`

| 文件 | 动作 |
|---|---|
| `app/src/main/java/com/tvbox/sync/**` | 新增，共用类逐字复制 + `AppRoomSyncAdapter`（`Subscription` 含 `builtIn`） |
| `app/src/main/res/layout/dialog_sync_tv.xml` + strings | 新增 |
| `res/layout/settings_content.xml`（`llBackup` 120-124 行） | 加 `llCloudSync` |
| `ui/settings/SettingsController.kt`（备份入口 187-216 行） | 加点击 |
| `base/App.java` | 延迟初始化 `SyncTrigger`；`NodeProcess.isRuntime(this)` 分支直接 return；仅主进程 |
| `ui/activity/DetailActivity.java:690-700`、`ui/fragment/PlayFragment.java:212-223`、`HistoryActivity.kt:42,56`、`CollectActivity.kt:45,60` | 变更通知 + oplog |
| `ui/activity/HistoryActivity.kt`、`ui/activity/CollectActivity.kt` | 清空文案与二次确认 |
| `ui/activity/MainActivity.kt` | `SyncConfigChangedEvent` 订阅 → `removeExtra(CACHE_CONFIG_CHANGED)` + `recreate()`；`onResume()` 消费 `pendingReload` |
| `ui/dialog/BackupDialog.java` | 过滤 `sync_*` 键 |
| `app/build.gradle`、`player/build.gradle`、`gradle/libs.versions.toml` | OkHttp 统一改造，见 §19.3 |
| `engine/core/.../NodeLoader.java:87`、`engine/catvod/.../Net.java:92`、`OkProxySelectorTest.java:223,237`、`FeatureRegressionTest.java:178`、app `BrotliInterceptor.java:17` | 残留 3.x 签名 / 内部 API 修复，见 §19.3 |
| `app/src/test/java/com/tvbox/sync/**` | 新增同一套 7 个测试文件（T 端 `junit:4.13.2` 已有，`app/build.gradle:161`） |
| `.github/workflows/build.yml` | 加依赖树断言、编译 `androidTest` 源码，见 §19.4 |

### 13.3 明确不改

`AppDataBase`/Room 版本与迁移、`AppDataManager.restore()`、`ApiConfig` 加载管线、`RefreshEvent` 既有 TYPE 语义、`sardine/smbj` 依赖声明（不新增调用）、Manifest 权限（两端 `ACCESS_NETWORK_STATE`/`ACCESS_WIFI_STATE`/`usesCleartextTraffic` 均已具备）、T 端 `engine/core/.../server/process/Action.java`。

实现时遵守 M 端 `HANDOFF.md:89-95`：不要出现 `OkGo.get(变量URL)`（Mimosa 误报）；并发用 `ExecutorService.submit(` 而非 `.execute(`；URL 拼接前校验不含 `..`。

## 14. 触发与 UI

- **手动**：设置页「云同步」→ 表单（URL/账号/应用密码/`spaceId`/自动同步/仅 Wi-Fi/同步范围）→「测试连接」「立即同步」「仅上传」「仅下载」，展示各设备状态、revision、「最近冲突」与「强制覆盖」。
- **自动**：`App.onCreate` 后延迟 2s + 网络可用时拉一次；**仅在主进程**。常规写历史/收藏/配置后 debounce 30s 触发；**播放途中进度高频写入只记本地 oplog，绝对不触发网络请求**；**退出播放器（`onDestroy`/`onBackPressed`）、切集或停止播放时，立即触发 Flush 同步（跳过 30s debounce，立即提交任务上传，决策 12）**，消除手机切 TV 续播的时延，并防止 Android 切后台进程休眠导致丢失；两次写入仍受 5~10s 最小冷却约束（§10.5）。不引入 WorkManager，用 `Handler` + 单线程 `ExecutorService.submit()`。
- **无痕浏览**：`PRIVATE_BROWSING` 开启时**跳过整个同步**，避免云端记录回填本地破坏无痕语义。
- **首次启用 / 切换 `spaceId`**：强制弹窗选「以本机为准 / 以云端为准」（均为并集，不会丢数据）。

## 15. 边界情况与异常处理

| 场景 | 处理 |
|---|---|
| 快照 JSON 截断/损坏 | 校验 `checksum`；失败 → 读 `lastgood.json` → 仍失败则提示"云端快照损坏，用本机覆盖 / 停止同步"，不自动覆盖 |
| 云端被删除/换空间后重连 | `cloud.revision < local.lastAckRevision` → 判定重置，不清 oplog，强制首次选择 |
| 401 / 403 | **停止自动同步**直到用户更新凭据（避免反复重试锁号）；403 提示目录权限 |
| 429 (Too Many Requests) | **停止自动同步**，解析 `Retry-After`（默认退避 60s）进入冷静期，本地 oplog 完好保留，UI 显示冷却倒计时，防止坚果云封号（§10.5） |
| 5xx / 超时 / 断网 | 指数退避（1s/2s/4s/8s，上限 60s），本地数据零损伤；状态栏显示上次成功时间 |
| 404 | 视为该空间首次同步（§8.2） |
| 多设备同时首次创建 | `If-None-Match: *` 只有一个成功，失败方回到 GET 重新 merge；5 次失败提示"云端正被其它设备初始化" |
| 301/302/307/308 | 关闭自动跟随，手动保留 method/body 最多 3 跳；跨域丢弃 Authorization 并报错 |
| 412/409/428 | 重新 GET + 重新 merge，最多 5 次；仍失败提示并发冲突，不覆盖 |
| 405/501（不支持 MOVE/PROPFIND） | 降级：直接 PUT + If-Match；ETag 仍可从 GET 头获取或走 read-verify-write |
| 无 ETag / 不支持 If-Match | read-verify-write 降级（§10.2），覆盖可被检测 |
| Digest 认证 / 自签证书 | 明确不支持并提示（Basic + 受信任 HTTPS） |
| `sync_url` 目录不存在 | `MKCOL` 逐级创建（v1.3）；创建失败提示用户检查根目录/权限 |
| 临时文件残留 | 下次 `ensure()` 时按 `.tmp-` 前缀列出并 DELETE（best-effort） |
| 快照 > 2MB | **先自动压缩**（同一 `id` 只留最新状态、剥离 tombstone 的多余字段、裁剪 `devices`、可选去掉超大 `payload`），压缩后仍超限才暂停自动上传并给出"清理空间 / 切换 spaceId / 查看占用"入口；有自愈路径，不永久卡死 |
| oplog 无限增长 | 同一 `(spaceId, entity, id)` 只留最后一条 op；未确认上限 5000，超过则合并为全量基线上传（§8.1） |
| `conflicts.json`/`lastgood.json` 并发写 | best-effort + `If-Match` 1 次重试；失败只记日志，不阻断主快照同步 |
| `apiUrl` 空/非法、`homeApi` 失效 | 保留本机值或写空回退 `firstSite`，记 `skippedKeys`（§7.5） |
| 同步期间用户改配置 | 冻结 oplog `seq` 上限，新增 op 下一轮处理 |
| 多进程 | 按进程名判断，仅主进程运行 `SyncTrigger`/写 oplog，避免 SP 跨进程丢数据 |
| 卸载重装 / 新设备 | 新 `deviceId`；首次同步按并集合并，不覆盖云端；`devices` 只留最近 20 台或 90 天内活跃 |
| 离线很久的设备上线 | 墓碑永久保留 + 按 `id` 压缩，已删数据不复活 |
| 播放中同步到新配置 | 只置 `pendingReload`，返回首页后重载，不打断播放 |
| 用户正在订阅页 | 配置 apply 延后到该页 `onPause` 之后 |
| 本地备份/恢复 | `BackupDialog` 导出 Hawk 时过滤 `sync_*`；恢复后同步凭据保持本机不变 |
| 切换 `spaceId` | oplog 按 `spaceId` 隔离，旧空间未上传条目不混入新空间 |
| 无痕浏览开启 | 跳过整个同步（§14） |
| **OkHttp 统一后网络栈回归** | 见 §19.4 门禁；DoH、订阅拉取、下载、DLNA、播放任一失败即回滚版本改动 |

## 16. 依赖审查与测试策略

### 16.1 依赖清单（v1.3 结论）

| 项 | 结论 |
|---|---|
| 运行时 | **统一到 OkHttp 5.5.0**（用户决策 10）。这是**修复既存分裂**（T 端实际已跑 5.5.0，声明却写 3.12.11），不是为同步功能新增无关依赖；收益是完整 WebDAV 方法与可维护的单一版本。详见 §19 |
| 不再成立 | v1.2 的"运行时零新增依赖"与"自实现 Base64"——改用 `Credentials.basic()`；`HttpURLConnectionSyncHttp` 改为 `OkHttpSyncHttp` |
| 不引入 | sardine/smbj 的新调用、Retrofit、WorkManager、MockWebServer、Robolectric、diff 库、第三方 XML 解析库、OkHttp 之外的 HTTP 客户端 |
| 纯 Java 层 API 约束 | 仅 Java 7 语法/API + Gson；禁止 `org.json`、`android.*`、`java.time`、`java.util.Base64`、**`okhttp3.*`/`okio.*` 类型**。平台能力走 `SyncHttp` 接口注入，否则 JVM 单测不可用 |
| okio | 删除两端显式 `okio:2.8.0`（与 OkHttp 5.x 的 okio 3.x 冲突），由 OkHttp 传递；直接 `okio.*` 调用按 §19.3 适配 |
| 测试依赖 | **M 端唯一新增 `testImplementation 'junit:junit:4.13.2'`**（test scope，不进 APK）；T 端已有（`:161`）。测试用 `SyncHttp` 内存 fake，不引入 MockWebServer |
| 构建门禁 | 两端 CI 加依赖树断言（只允许一个 okhttp/okio 版本），见 §19.4 |

### 16.2 测试文件清单（两端 `app/src/test/java/com/tvbox/sync/`）

| 文件 | 覆盖 |
|---|---|
| `SyncProtocolTest.java` | normalize（`;pk;`/trim）、configHash、makeId、`validateSpaceId`（含 `..`/斜杠拒绝）、schema 迁移、checksum |
| `SyncSnapshotTest.java` | Gson 往返、未知字段忽略、`clears` map、墓碑不含 payload、损坏 checksum 拒绝、`spaceId` 隔离 |
| `SyncMergerTest.java` | 并集、删除/清空、时钟漂移、同组/异组并发、三方订阅并集、首次并集不覆盖、幂等、progress 回填、revision 回退 |
| `SyncOpLogTest.java` | dirty 冻结、ack 清理、崩溃重放、**同 id 合并与 5000 上限**、按 `spaceId` 隔离、三种模式语义 |
| `SyncEngineTest.java` | 412 重试 5 次、**多设备首次并发创建**、MOVE 降级、无 ETag read-verify-write、401 停止自动同步、冲突副本/`lastgood` 失败不阻断、写云端失败不改本地 |
| `WebDavStorageTest.java` | MKCOL/PROPFIND/MOVE/COPY/DELETE 方法构造、`Credentials.basic`、`If-Match`/`If-None-Match`/`Overwrite` 头、重定向保 method、跨域丢凭据、2MB 自动压缩触发。**用 `SyncHttp` 内存 fake** |
| `AppRoomSyncAdapterTest.java` | §7 约束：复用自增 `id` 不产生重复行、只落地前 `hisNum` 条、`updateTime` 用云端值、`apiUrl`/`homeApi` 校验（内存 fake DAO，不依赖 Room） |

**集成/构建测试**：本地起 WebDAV 服务（Python `wsgidav` 或 Node 脚本）跑双端/三端联调；CI 脚本校验两端 `com/tvbox/sync/**` 源码 SHA-256 一致 + 依赖树单一版本 + 网络栈回归（§19.4）。

## 17. 阶段与验收

| 阶段 | 内容 |
|---|---|
| **P0.0 门禁** | **§19 OkHttp 统一专项**：依赖树实测、两端编译、网络栈回归；未通过不得写同步代码 |
| P0 | 协议 DTO + `SyncProtocol` + `SyncMerger` + §16 全部 JVM 单测 |
| P1 | `SyncOpLog` + `OkHttpSyncHttp`/`WebDavStorage` + `SyncEngine` + 设置页 UI + 手动同步 → 双端真实联调 |
| P2 | `SyncTrigger`、墓碑/批量清空、乐观锁与降级、冲突副本、首页重载、`spaceId`、§15 边界 |
| P3 | S3 SigV4、AES-GCM 端到端加密、LAN `do=sync` 桥接、T 端引擎 `tv` 库适配 |

验收用例（§16 单测全绿为硬门槛）：

1. 三台设备离线各看不同影片后联网，历史并集不丢、不产生重复行。
2. 同一影片两端进度不同，以 `rev` 大者续播；进度能在对端 `cache` 表命中。
3. 一端删一条 / 点「清空所有设备记录」，其他端同步后不复活；清空后本端新看的一集仍保留。
4. 一端换订阅：`subscriptions` 取并集、`apiUrl` 以较新组为准，首页立即重载并展示新站点。
5. A 改订阅、B 同时改播放器：两组都保留；A/B 同时改订阅：后写胜 + 冲突副本 + 可强制覆盖。
6. 播放设置同步后立即生效；`play_type` 不支持时回退 Exo 并提示。
7. 云端历史数百条时本机只落地 `hisNum` 条，反复同步无写入震荡、不误删云端记录。
8. `spaceId=default` 与 `family` 互不影响；切换空间重新走首次选择且旧空间 oplog 不混入。
9. 坚果云（无 ETag/无 If-Match/无 MOVE）并发写不静默丢数据；断网/密码错误/412 时本地数据零损伤。
10. 首次在**全新设备**同步选「以本机为准」，云端原有 300 条历史一条不少；两台设备同时首次初始化也能收敛。
11. 快照损坏时回退 `lastgood.json`，不自动覆盖、不丢本地数据；`lastgood` 写入失败不阻断同步。
12. 正在播放时同步到新配置：不打断播放，返回首页后重载；播放途中进度写入不触发同步。
13. 本地备份导出的 `hawk` 文件不含 `sync_password` 等 `sync_*` 键。
14. 两端 `com/tvbox/sync/**` 源码 SHA-256 一致，两端 JVM 单测全绿。
15. **WebDAV 目录不存在时自动 `MKCOL` 创建**并成功首同步；`PROPFIND` 可取到 ETag。
16. **两端 `./gradlew :app:dependencies` 只解析出单一 OkHttp/okio 版本**；OkGo、DoH、订阅拉取、下载、播放回归通过。

## 18. 风险与后续

- **OkHttp 统一是本次最大的范围扩张**：会触及 M 端整个网络栈（DoH、下载、DLNA、Exo DataSource），必须靠 §19.4 门禁与回滚路径兜住。
- **read-verify-write 的 TOCTOU 窗口**：无 ETag 服务端无法 100% 防并发覆盖，但能检测并重试/告警。
- **订阅页覆盖**：`SubscriptionActivity.onPause()` 会用旧 `mSelectedUrl` 覆盖 `API_URL`（§11.2）。
- **播放器静态缓存**：`getPlayersExistInfo()` 进程级缓存，安装/卸载后需重启进程。
- **本地裁剪与云端全量 / 时钟漂移 / `sourceKey` 冲突 / 隐私**：靠 §7、`rev`、`configHash`、HTTPS+Basic 处理；端到端加密列 P3。
- 后续 S3、引擎 `tv` 库、`/action?do=sync` 桥接只需新增适配器，协议不变。

## 19. 技术栈全面对齐与 OkHttp 统一专项（P0.0 门禁）

### 19.1 现状（已核实）

| | M | T |
|---|---|---|
| 声明 | `app/build.gradle:113`、`player/build.gradle:40` 硬编码 `okhttp:3.12.11`；`app:117` 显式 `okio:2.8.0`；`app:133` `okgo:3.0.4` | `app/build.gradle:182`、`player/build.gradle:36` 硬编码 `3.12.11`；`app:186` `okio:2.8.0`；`app:201` `okgo:3.0.4`；**`engine/catvod/build.gradle:25` `api libs.bundles.okhttp` = 5.5.0**（`gradle/libs.versions.toml:44,98-100,122`） |
| 实际解析 | 3.12.11 | **5.5.0**（`api` 传递到 app 编译/运行 classpath，Gradle 取最高） |
| 适配状态 | **未适配**：自带 `app/src/main/java/okhttp3/dnsoverhttps/{DnsOverHttps,DnsRecordCodec,BootstrapDns}.java`；大量 `okhttp3.internal.*`（`OkGoHelper.java:29-35,73,155`、`BrotliInterceptor.java:17`）；3.x `RequestBody.create(MediaType, ...)`（`OkHttpDataSource.java:420,423`、`util/js/Connect.java:78-83`） | **基本适配**：`OkGoHelper` 已改用 `java.util.Arrays.asList`/硬编码 UA/`Dns.SYSTEM`，engine 用 5.x `RequestBody` 签名，`okhttp-dnsoverhttps` 走 catalog artifact；但残留 3.x 签名（`NodeLoader.java:87`、`Net.java:92`、`OkProxySelectorTest.java:223,237`、`FeatureRegressionTest.java:178`）与 `RealResponseBody` 内部 API |
| 版本约束 | 无 toml、无 `resolutionStrategy` | 有默认 `libs` catalog、无 `resolutionStrategy` |
| 构建 | Gradle 8.13 / AGP 8.13.2 / Java 8 source / Kotlin 1.9.24 / **无 desugaring** | Gradle 9.7.1 / AGP 9.3.1 / Java 21 / Kotlin 2.2.20 / 有 desugaring |

### 19.2 技术栈基线决策：以 TVboxOSC 为准全面现代化（决策 10、14）

- **技术栈对齐**：M 端全面对齐 T 端现代基线——升级至 Gradle 9.7.1 + AGP 9.3.1 + JDK 21（`sourceCompatibility/targetCompatibility 21`，`jvmTarget = '21'`）+ Kotlin 2.2.20；引入 `gradle/libs.versions.toml`（Version Catalog）实现双端共用依赖版本定义；开启 `coreLibraryDesugaringEnabled true`（`desugar_jdk_libs_nio:2.1.5`）。
- **T 端实际已在运行 5.5.0**，源码已适配，统一只需清理声明与残留；降到 3.12.11 反而要回改 engine 的 5.x 签名。
- 统一到 4.12.0 会同时要求 M 端升级、T 端降级，两面改动，收益不比 5.5.0。
- 3.12.11 停留在 2019 年（TLS/安全更新滞后），不适合作为新功能的网络基座。
- **结论**：两端统一 `com.squareup.okhttp3:{okhttp,okhttp-dnsoverhttps,logging-interceptor}:5.5.0`，okio 由 OkHttp 传递（删除显式 2.8.0）。
- **不加 `resolutionStrategy.force`**：`force` 会掩盖 OkGo 3.0.4 基于 3.x 编译的假设；采用"改声明 + 依赖树断言"显式保证单一版本。

### 19.3 改动清单

**M 端（技术栈升级与网络适配，最高优先级）**

1. **工程技术栈对齐**：
   - 引入 `gradle/libs.versions.toml`（与 T 端对齐版本）；
   - 更新 `gradle/wrapper/gradle-wrapper.properties` 至 `gradle-9.7.1-bin.zip`；
   - 根 `build.gradle` 升级 AGP 至 9.3.1、Kotlin 至 2.2.20；
   - `app/build.gradle` 升级 `compileOptions` 与 `kotlinOptions` 至 Java 21，开启 `coreLibraryDesugaringEnabled true`；
   - 依赖项全面切换至 `libs.versions.toml` 引用。
2. `app/build.gradle` / `player/build.gradle` → 改用 `libs.okhttp`（`5.5.0`）；删除显式 `okio:2.8.0`；新增 `okhttp-dnsoverhttps:5.5.0`。
3. **删除** `app/src/main/java/okhttp3/dnsoverhttps/` 三个类（否则与 artifact duplicate class），`OkGoHelper.java` 改用 artifact 的 `DnsOverHttps`。
4. 替换 `okhttp3.internal.*` 用法：参考 T 端成熟实现，`Util.immutableList` → `Collections.unmodifiableList`/`Arrays.asList`，`Version.userAgent()` → 固定 UA 字符串，`Platform`/`PublicSuffixDatabase` → 不再依赖（改用 OkHttp 公开 API），`RealResponseBody` → 自定义 `ResponseBody` 包装（`BrotliInterceptor.java:17`）。
5. 3.x → 5.x `RequestBody.create` 签名：`OkHttpDataSource.java:420,423`、`util/js/Connect.java:78-83` 等；`okio.Okio.source/buffer`、`GzipSource`、`ByteString` 调用按 okio 3.x 适配。
6. `OkGo 3.0.4` 兼容性实测（见门禁）；必要时升级 OkGo 或改用项目自带的 `OkHttp` 封装。

**T 端（清理为主）**

1. `app/build.gradle:182` / `player/build.gradle:36` → 改用 `libs.okhttp`（5.5.0）；`app:186` 删除显式 `okio:2.8.0`；`gradle/libs.versions.toml:44` 保持 `5.5.0` 作为唯一版本源。
2. 修复残留 3.x 签名：`engine/core/.../NodeLoader.java:87`、`engine/catvod/.../Net.java:92`、`OkProxySelectorTest.java:223,237`、`app/src/androidTest/.../FeatureRegressionTest.java:178`；app `BrotliInterceptor.java:17` 的 `RealResponseBody`。
3. 核对 `sardine-android:0.9`、`smbj:0.15.0`、glide `okhttp3-integration`、本地 `libs/*.jar|aar` 是否传递其它 okhttp/okio 版本。

### 19.4 P0.0 门禁（未全过不得进入 P0）

| # | 检查 | 命令/方式 |
|---|---|---|
| 1 | 依赖树只出现单一 okhttp/okio 版本 | 两端 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`，脚本断言 `com.squareup.okhttp3:okhttp` 仅 5.5.0、`com.squareup.okio` 单一版本 |
| 2 | 两端可编译 | M `./gradlew assembleDebug`；T `./gradlew :app:assembleDebug :app:testDebugUnitTest`（沿用现有 CI） |
| 3 | T 端 androidTest 源码可编译 | T 的 CI 目前**不编译 androidTest**，需显式加 `:app:compileDebugAndroidTestJavaWithJavac` 或等价任务，防止 3.x 残留漏检 |
| 4 | 网络栈回归 | 手工/脚本：订阅拉取、DoH 解析、下载（HLS/普通）、DLNA、Exo 播放、OkGo 请求、代理与证书相关路径；两端各跑一遍 |
| 5 | 两端同步源码一致性 | 脚本比对 `com/tvbox/sync/**` SHA-256 |

**回滚路径**：若 M 端适配失败或回归不通过，回退到 **v1.2 的扁平文件 + `HttpURLConnectionSyncHttp` 方案**（该方案已完整设计：只用 GET/PUT/DELETE、用户手动建目录、`spaceId` 编码进文件名、read-verify-write 降级），同步协议与合并层无需改动，只需替换 `WebDavStorage` 与目录布局。此回滚不引入任何依赖变更。

## 附录 A 两端关键路径速查

| 用途 | M | T |
|---|---|---|
| 构建配置 | `app/build.gradle:12-13,113,117,133`（minSdk 24，无 desugaring/JUnit） | `app/build.gradle:37-38,112,160-162,182,186,201`；`gradle/libs.versions.toml:44,98-100,122`；`engine/catvod/build.gradle:25` |
| 配置常量 | `app/src/main/java/com/github/tvbox/osc/util/HawkConfig.java` | `app/src/main/java/com/kukuqi/tvbox/osc/util/HawkConfig.java` |
| Room 管理 | `.../cache/RoomDataManger.java`、`.../cache/VodRecordDao.java:18-19,38-39` | 同名同构（少 `downloadEpisode`） |
| 进度键 | `.../ui/fragment/PlayFragment.java:1244` | `.../ui/fragment/PlayFragment.java:1046-1047` |
| 历史写入 | `.../ui/activity/DetailActivity.java:760` | `.../ui/activity/DetailActivity.java:690-700` |
| 清空历史 | `.../ui/activity/HistoryActivity.kt:49-69` | `.../ui/activity/HistoryActivity.kt` |
| 设置入口 | `.../ui/activity/SettingActivity.kt` + `res/layout/activity_setting.xml` | `.../ui/settings/SettingsController.kt` + `res/layout/settings_content.xml` |
| 首页重载 | `.../ui/activity/MainActivity.kt`(singleTop) + `.../ui/fragment/HomeFragment.kt:122-188` | `.../ui/activity/MainActivity.kt:111-125` + `HomeFragment.kt:130-208` |
| 配置加载 | `.../api/ApiConfig.java:139`（缓存 `filesDir/MD5(apiUrl)`） | `.../api/ApiConfig.java:134`（`SourceManager.readConfig`） |
| 本地备份 | `.../ui/dialog/BackupDialog.java:141-150` | `.../ui/dialog/BackupDialog.java` |
| CI | `.github/workflows/test.yml`（`assemblerelease`） | `.github/workflows/build.yml`（`:app:assembleDebug` + `testDebugUnitTest`） |
| LAN 同步 | 无 | `engine/core/.../server/process/Action.java:135-237` |
