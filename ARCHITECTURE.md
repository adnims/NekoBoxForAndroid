# NekoBoxForAndroid（adnims fork）架构分析

> **给未来的会话**：本文档 2026-07-25 生成，所有结论经交叉验证（GitHub API 查 fork 谱系 + clone 各 fork 逐 commit diff + 读全部 buildScript/workflows）。事实部分可放心引用；改代码前先看 §2 速查表 和 §5 修改手册。
> 本地工作区：`/root/nekobox` = adnims/NekoBoxForAndroid `main`；`/root/sing-box` = adnims/sing-box `1.12.x`（与 pin 同步）；`/root/libneko` = adnims/libneko（与 pin 同步）。三个仓库均有 git 写权限（凭证助手，token 在仓库外）。另 fetch 了 `upstream_main` 引用供 diff。

---

## 1. 总览

本仓库是 **NekoBox for Android**（前身 SagerNet，包名 `io.nekohasekai.sagernet` 是历史遗留命名）。三层结构 + 纯 GitHub Actions 构建（本地无需 Go/NDK）：

| 层 | 位置 | 语言 | 作用 |
|---|---|---|---|
| 应用层 | 本仓库 `app/` | Kotlin/Java | UI、VpnService、订阅/配置管理 |
| JNI 桥接层 | 本仓库 `libcore/` | Go | 把内核封装成 `app/libs/libcore.aar`（gobind 生成 Java 接口） |
| 内核 | **adnims/sing-box** fork（NekoBox 定制版 sing-box） | Go | sing-box 内核，含 NekoBox 扩展（libbox/group 等） |
| 辅助库 | MatsuriDayo/**libneko**（CI 拉上游，pin 固定） | Go | protect_server / speedtest 等 NekoBox Go 工具包 |

**谱系**（已验证）：

```
SagerNet → MatsuriDayo/NekoBoxForAndroid (upstream, 公开)
               └── adnims/NekoBoxForAndroid (本仓库)
MatsuriDayo/sing-box (公开, NekoBox 发行版)
               └── adnims/sing-box (fork, 1.12.x 分支有 UA 定制)
MatsuriDayo/libneko (公开)
               ├── adnims/libneko (fork, 与上游 0 差异, 未被 CI 使用)
               └── CI 实际 clone 上游
```

⚠️ 名词：内核是 **sing-box**（不是 mihomo/clash；构建 tag `with_clash_api` 只是 sing-box 的 clash 兼容 API）。"libnoke" 即 **libneko**。

## 2. 速查表（最重要的事实，先读这个）

| 项 | 值 |
|---|---|
| 内核 pin | `COMMIT_SING_BOX=9fa101f82c101c79b327cd6551b5ddf9ab8d8970`，在 **adnims/sing-box 的 `1.12.x` 分支**（基于 MatsuriDayo `1.12.19-neko-1`） |
| libneko pin | `COMMIT_LIBNEKO=1c47a3af71990a7b2192e03292b4d246c308ef0b`（MatsuriDayo/libneko main HEAD，2024-07） |
| pin 存放处 | `buildScript/lib/core/get_source_env.sh` |
| clone 地址 | `buildScript/lib/core/get_source.sh`：sing-box 指向 **adnims fork**；libneko 仍指**上游** |
| 内核 fork 的分支 | ⚠️ 默认分支 `def` 是旧快照、**0 自有提交**；真实工作分支 = **`1.12.x`** |
| 上游 | `MatsuriDayo/NekoBoxForAndroid`（公开），merge-base = tag `1.4.2` @ `5768494` |
| 应用标识 | 包名 `adnims.nb4a`，`VERSION_NAME=1.4.2-updateGrpc/ws/httpUserAgent`，`VERSION_CODE=47`（Gradle 实际 versionCode = ×5） |
| 品牌 | 英文 strings 已改 `NekoBox++`；**其它 locale（zh-rCN 等）仍写 NekoBox** |
| 签名 | `release.keystore`（仓库内明文）+ secret `LOCAL_PROPERTIES`（base64 的 KEYSTORE_PASS/ALIAS_NAME/ALIAS_PASS）——**已确认配置成功、已实际构建发布可用** |
| 构建入口 | 纯 GitHub Actions 手动触发：`preview.yml`（迭代）/ `release.yml`（发版，ghr 挂 release） |

## 3. 本 fork 相对上游的改动全集（已 diff）

### 3.1 adnims/sing-box `1.12.x` 分支：4 个提交，全部是默认 UA 改动

| commit | 文件 | 内容 |
|---|---|---|
| `b5c6a54` | `transport/v2raygrpclite/client.go` | 默认 UA: `grpc-go/1.48.0` → Chrome 126 |
| `35cae5e` | 同上 | Chrome 126 → Chrome 150 |
| `01f2ea3` | `transport/v2raywebsocket/client.go` | 默认 UA: `Go-http-client/1.1` → Chrome 150 |
| `9fa101f`（当前 pin） | `transport/v2rayhttpupgrade/client.go` | 请求无 UA 时补默认 Chrome 150 |

注：这些改的是**硬编码默认值**，v2ray 各 transport 没有可配置 UA 选项。

### 3.2 本仓库 `main`（相对上游 `1.4.2`）：app 的 Kotlin 源码 **0 行改动**

| 文件 | 改动 |
|---|---|
| `buildScript/lib/core/get_source.sh` | sing-box clone 地址改为 adnims fork |
| `buildScript/lib/core/get_source_env.sh` | pin 两次更新，现值见 §2 |
| `nb4a.properties` | 包名/版本/版本号（moe.nb4a→adnims.nb4a） |
| `app/src/main/res/values/strings.xml` | 品牌名 NekoBox → NekoBox++（仅英文） |
| `release.keystore` | 换成用户自己的 keystore |
| `.github/workflows/release.yml` | 删掉 Play AAB job 和 `play` input |

注意：订阅 `customUserAgent` 能力（`SubscriptionBean.customUserAgent`、`Constants.SUBSCRIPTION_USER_AGENT`、`RawUpdater` 里的 setUserAgent、`UserAgentPreference`）是**上游 NekoBox 原生功能**，不是本 fork 加的。

## 4. 目录结构

```
NekoBoxForAndroid/
├── app/                          # Android 应用（Kotlin 为主）
│   ├── build.gradle.kts        # setupApp()：flavors oss/fdroid/play/preview；ABI 4-split
│   ├── executableSo/            # 几乎空（.gitignore）；内嵌插件可执行文件放这里会被打进 APK
│   └── src/main/
│       ├── aidl/                # ISagerNetService 等前台服务 AIDL
│       ├── assets/
│       │   ├── yacd.zip         # 内嵌 "yacd" 可执行插件运行时（JS 插件）
│       │   ├── yacd.version.txt
│       │   ├── proxy_packagename.txt   # 外部插件包名发现清单
│       │   └── (CI 时) sing-box/{geoip,geosite}.db[xz]   # gitignored，由 assets.sh 下载
│       └── java/
│           ├── io/nekohasekai/sagernet/   # 主包（历史命名 SagerNet）
│           │   ├── SagerNet.kt          # Application 入口
│           │   ├── bg/                  # VpnService / ProxyService / AIDL 服务
│           │   │   └── proto/           # BoxInstance(调JNI) / ProxyInstance / TestInstance(测速)
│           │   │                        # TrafficUpdater / UrlTest / GuardedProcessPool
│           │   ├── database/            # Room SagerDatabase（Profile/Proxy/Rule/Group/
│           │   │                        # Subscription[含 customUserAgent]）+ DataStore(SharedPrefs)
│           │   ├── fmt/                 # 每种协议: Bean(Java, Kryo 序列化)+Fmt(Kotlin 解析器)
│           │   │                        # v2ray/vmess, trojan, trojan-go, shadowsocks, tuic,
│           │   │                        # hysteria, mieru, naive, ssh, socks, wireguard, chain, internal
│           │   │                        # TypeMap.kt=协议注册表; UniversalFmt=订阅/clash-yaml 解析
│           │   ├── group/               # GroupUpdater / RawUpdater（订阅拉取, 用 customUserAgent）
│           │   ├── plugin/             # 外部插件 APK + 内嵌 yacd 管理
│           │   ├── ui/                 # 全部 Activity/Fragment（主界面/配置/路由/工具…）
│           │   ├── widget/            # 自定义 Preference（GroupPreference/OutboundPreference/
│           │   │                        # UserAgentPreference…）
│           │   └── ktx/ utils/
│           ├── moe/matsuri/nb4a/       # NekoBox 扩展层（新代码都在这）
│           │   ├── NativeInterface.kt  # 实现 Go 侧 BoxPlatformInterface + NB4AInterface
│           │   ├── Protocols.kt        # nb4a 侧协议注册
│           │   ├── proxy/shadowtls|anytls|neko   # NekoBox 特有协议
│           │   ├── proxy/config/ConfigBean      # 内核全局配置
│           │   ├── SingBoxOptions.java/.kt      # 内核选项存取
│           │   └── ui/ utils/
│           └── com/github/shadowsocks/plugin/  # 遗留插件兼容代码
├── libcore/                    # ★ Go JNI 桥接模块（module 名 "libcore"）
│   ├── box.go box_include.go   # 调 sing-box adapter/boxapi
│   ├── nb4a.go                 # gobind 入口 New(NB4AInterface, BoxPlatformInterface, LocalDNSTransport, …)
│   ├── platform_java.go        # Go 侧定义 NB4AInterface（Java/Kotlin 实现）
│   ├── http.go                 # 内核内部 HTTP 客户端（含 SetUserAgent）
│   ├── assets*.go              # 启动时把 assets/sing-box/*.db、yacd.zip 从 APK 解到文件目录
│   ├── dns_box.go geoip.go geosite.go stun/ procfs/ device/ ech/
│   ├── go.mod                  # replace 到 ../.. 的 libneko、sing-box（CI clone 的并列目录）
│   └── build.sh init.sh        # 由 buildScript/lib/core.sh 驱动
├── buildScript/
│   ├── lib/core.sh             # CI 入口：init.sh + build.sh
│   ├── lib/core/get_source.sh          # clone sing-box(adnims) + libneko(上游) 到 pin SHA
│   ├── lib/core/get_source_env.sh     # ★ 两个 COMMIT_* pin 写在这
│   ├── lib/core/build.sh              # rel=1 → gomobile bind → app/libs/libcore.aar
│   ├── lib/assets.sh                 # 下载最新 sing-geoip/sing-geosite 到 app/src/main/assets/sing-box/
│   ├── init/action/gradle.sh         # CI: 先 assets.sh 再 gradle
│   ├── init/env*.sh                  # NDK 25.0.8775105 交叉编译器环境变量
│   └── fdroid/prebuild.sh
├── .github/workflows/
│   ├── release.yml            # workflow_dispatch(tag, publish) → assembleOssRelease → ghr 发 release
│   └── preview.yml           # workflow_dispatch → assemblePreviewRelease → artifact "APKs"
├── run                         # bash 入口路由（./run lib core / ./run init action gradle）
├── nb4a.properties            # PACKAGE_NAME / VERSION_NAME / PRE_VERSION_NAME / VERSION_CODE
├── release.keystore          # 用户自己的签名（明文入库；密码走 secret）
└── buildSrc/…/Helpers.kt     # Gradle DSL：flavors、签名注入、ABI、versionCode×5
```

## 5. 关键机制

### 5.1 JNI 桥（Go ⇄ Kotlin）

- `libcore/nb4a.go` 的 `New(...)` 参数是 **Go 侧定义**的接口（`NB4AInterface`、`BoxPlatformInterface`、`LocalDNSTransport`）；用 MatsuriDayo 定制版 gomobile（`MatsuriDayo/gomobile` 仓库 `master2` 分支，装成 `gomobile-matsuri`/`gobind-matsuri`）生成 Java 类打进 AAR。
- Kotlin 由 `moe.matsuri.nb4a.NativeInterface` 实现这些接口（TUN fd 保护、connection owner、资产策略、DNS 解析…）。
- 生成物在 AAR 里，Kotlin 中表现为 `libcore.Libcore`、`go.Seq`（gobind 的变长参数封装）等。
- 编译 tags：`with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api`。
- **内核新 option 要传到 UI = 三处同步**：sing-box option + libbox 序列化 + `libcore/` Go 代码 + Kotlin `SingBoxOptions`/`ConfigBean`。

### 5.2 运行时资产流

1. CI `buildScript/lib/assets.sh` 下载 SagerNet/sing-geoip、sing-geosite 最新 release 的 db（xz）→ `app/src/main/assets/sing-box/`（gitignored，只在构建机存在）
2. 打进 APK assets
3. 启动时 `libcore/assets_android.go` 的 `extractAssets()` 按 `NB4AInterface.useOfficialAssets()` 解压到应用目录/文件目录；`yacd.zip` 同理解压为可执行插件运行时

### 5.3 订阅/配置数据流

```
订阅 URL (可带 customUserAgent, RawUpdater 使用)
  → OkHttp 拉取 → UniversalFmt 解析（clash-yaml / v2ray base64 / neko 自定义）
  → Room (SagerDatabase)
  → bg/proto/BoxInstance.buildConfig()：Bean → sing-box json 配置
  → libcore JNI → TUN(VpnService) + 各协议
```

### 5.4 Actions 缓存机制（魔改必读）

`libcore.aar` 的 cache key = `hashFiles(workflows/*, golang_status, libcore_status)`，其中
- `golang_status` = `find buildScript libcore/*.sh | xargs cat | sha1sum`
- `libcore_status` = `git ls-files libcore | xargs cat | sha1sum`（只算 git 跟踪文件）

推论：
- 改 `buildScript/` 下任何 sh（**含 get_source_env.sh 的 pin**）→ golang_status 变 → 重编 Go ✅
- 改 `libcore/*.go` → 重编 Go ✅
- **只推 adnims/sing-box 新 commit 而不 bump get_source_env.sh 的 pin → 缓存命中 → 内核改动不生效！**（libneko 同理）
- Kotlin 改动不影响 libcore 缓存，gradle job 每次重编（仅 cache ~/.gradle）

### 5.5 两个 workflow（均手动触发）

- `preview.yml`：libcore job（缓存或 `./run lib core`）→ `./gradlew app:assemblePreviewRelease` → 取 arm64 APK 作 artifact（文件名含 `PRE_VERSION_NAME`，如 `NekoBox-pre-1.4.2-…`）
- `release.yml`（inputs: `tag` 必填 / `publish`）：同上但 `assembleOssRelease` 出 4 ABI → `ghr -delete` 清同名 tag 后挂 GitHub Release；`publish` 填 `y` 跳过发布只出 artifact

## 6. 修改手册（按目标定位）

| 想改什么 | 动哪里 | 生效要点 |
|---|---|---|
| 内核协议/传输（UA、选项、bug） | `adnims/sing-box` 的 **`1.12.x` 分支**（别碰 def）push → 本仓库 `get_source_env.sh` bump `COMMIT_SING_BOX` → 触发 preview | pin 变了才失效缓存 |
| 内核 option 传到 UI | sing-box option + libbox + 本仓库 `libcore/` Go + Kotlin `SingBoxOptions`/`ConfigBean` 四处同步 | 四处都要提交 |
| libneko 定制 | 改 `adnims/libneko`（当前与上游 0 差异）→ 本仓库 `get_source.sh` clone URL 改成 adnims/libneko → bump `COMMIT_LIBNEKO` | 不改 URL 则 fork 永远不被用 |
| 订阅/UA/分组逻辑 | 本仓库 `group/RawUpdater.kt`、`database/*`、`fmt/*`、`widget/UserAgentPreference.kt` | 推 main 即可 |
| UI/设置项 | `app/src/main/java/…/ui/*`、`res/xml/*`、`res/values*/strings.xml`（多语言目录齐全：ar/de/es/fa/fr/in/it/ja/ko/ru/tr/uk/zh-rCN/zh-rHK/zh-rTW…） | 品牌改动要覆盖所有 locale |
| 应用名/包名/版本 | `nb4a.properties`（versionCode 记得 ×5 逻辑） | — |
| 签名 | `release.keystore`（入库明文）+ secret `LOCAL_PROPERTIES`；**当前配置已验证可用** | 换 keystore 必须同步改 secret |

## 7. 构建验证方式

- 需要用户提供的 GitHub token 推代码 + 触发 workflow（或用户手动触发）
- 看 Actions 日志：`Native Build` 段是否重新执行（缓存未命中）→ Golang 状态 hash 变化即 pin 生效
- preview 出包在 artifact "APKs"；release 出 4 ABI APK 挂到对应 tag 的 GitHub Release

## 8. 工作规则（agent 必读）

- **不要每改一点就触发构建**（无意义等待，一轮约 8-10 分钟）。构建只在两种情况下进行：用户明确要求出包，或改动需要验证正确性。平时只改代码、本地自查（编译类检查/静态审查），攒批后一起验证。
- 推送代码可以随改随推（不影响用户），触发 Actions 按上述规则节制。
