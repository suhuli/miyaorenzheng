# 密钥认证 2.4.2

原生 Android 密钥证明诊断工具。首页直接显示证书正常、异常或待确认及原因；保留证书链、本地历史、RKP 识别和导出。

社区仓库：https://github.com/suhuli/miyaorenzheng

基于 [vvb2060/KeyAttestation](https://github.com/vvb2060/KeyAttestation) 的非官方社区修改版，上游基线 `b223a9be86cb2a747625d8e47f3456646d55bd7a`。保留上游作者、版权和 Apache-2.0 许可，见 LICENSE、README.upstream.md。不是 Google 或原作者的官方发行版。

## 2.4.2 主页配置扩展卡片

主页在“证书”和“设备”之间显示“配置扩展”：所在证书、30 天颁发近似数、受认证来源、遗失设备字段、可选旧制造商及其他字段数量。支持多张证书的扩展，并可打开各自完整详情。未发现扩展、字段缺失、解析失败分别展示；不会把缺失的遗失字段当成 false，也不会把签发时快照宣称为实时状态。此卡显示证书内容，不单独为扩展来源背书；可信性以链/根/撤销验证结果为准。

## 2.4.1 修复

修复镜像优先被跨来源时间比较抵消、长期回退官方的问题。明确镜像优先时允许采用仍有效的仓库镜像，即便官方缓存抓取时间更近；不重置真实时间，不允许同一镜像流倒退（独立版本水位随缓存持久保存）。切换选项后立即更新，当前有请求时排队重试；设置区分当前数据来源并展示回退原因。

## 2.4.0 正式版

- 应用名改为「密钥认证」，包名仍为 `com.larus.nova`，沿用 2.3 正式签名，可覆盖升级。
- 设置 → 信任数据 → 数据源优先级：Google 官方 → jsDelivr → 本地缓存（默认），或 jsDelivr → Google 官方 → 本地缓存。
- 选择持久保存并立即触发更新；已有更新进行时完成后按新选择再检查。单次成功来源完整替换根和撤销数据，不混合两份来源；实际来源和抓取时间始终单独展示。过期镜像不使用；同一镜像流拒绝旧版本。明确选镜像优先时，可用有效镜像替换抓取时间更近的官方数据，镜像时间不重置。
- 自有 Java/Kotlin/AIDL 命名空间与目录迁移为 `com.larus.nova`，同步日志标签、诊断别名、导出文件名、组件和任务名称。合法开源署名、Google 协议/OID、系统 API 和固定镜像地址保留。
- 旧有效缓存自动迁移到 trust-store.json，保留时间/来源；历史和设置不清空。诊断密钥别名变化会产生新的 App 测试密钥，这不是硬件根轮换；旧密钥不被删除，旧导入的 Attest Key 如需继续使用应重新导入。
- 正式 release 构建、不可调试。改名不保证第三方无法通过行为/代码相似性等推断来源，不伪造证书字段、签名或硬件属性。

## 2.3.0 历史正式版

- 应用名：密钥认证增强版；包名：`com.larus.nova`；版本：2.3.0 / 230。
- 独立正式生产密钥签名、release 构建、不可调试，不再发布 debug APK；新包名独立安装，不覆盖旧包名的私有历史。
- **Google 官方 → jsDelivr → 本地缓存**。官方 root/status 均获取且验证成功后完整替换本地数据，立即采用官方结果；不再查询镜像，也不与过时数据合并。
- 官方失败才访问镜像；镜像失败保留缓存。镜像完全信任仓库的既有决策保留。
- 后续升级必须使用同一生产密钥。密钥私下交付，公开源码与发布附件均不含私钥/密码。
- 构建见 SIGNING.md，更新和信任模式见 MIRROR.md。

## 2.2 历史更新（数据优先级已在 2.3 反转）

- 首页改为醒目的绿色/红色/黄色结果卡，区分正常、真实异常和数据不足/兼容状态；不是“手机绝对安全”承诺。
- 删除首页口号、英文品牌行、各页说明副标题和重复说明卡。详细解释在证书/设置详情中按需查看。
- 2.2 曾采用 jsDelivr 优先；2.3 改为 Google 官方优先，失败才查镜像清单，最后保留缓存。
- **完全信任仓库，不做镜像签名、不限制仓库新增根**。SHA-256 只检查传输完整性，不是签名。详情见 MIRROR.md。
- GitHub 每 3 小时抓取官方 root/status，两份都验证成功才更新 data 分支。清单使用官方抓取时间，客户端不会把旧数据伪装成刚更新。

## 四个分区

- **概览**：配置类型、根来源、签名路径、有效期策略、撤销检查分别显示；设备启动状态与安全级别不是一张“绝对安全”绿卡。
- **证书链**：根到目标证书的层级连接、每条连接验签、有效期、RKP 扩展和完整字段；原有高级工具独立保留。
- **记录**：本机最多 30 次摘要、任意两次对比、停止记录/一键清空；正常轮换不当成泄露。摘要仍含可关联指纹。
- **设置**：固定 Google HTTPS 官方数据更新、差异和错误、离线数据包导入导出、隐私、临期通知、浅/深色与跟随系统。

当前镜像机制见 **MIRROR.md**，2.1 功能详见 **FEATURE-2.1.md**（历史版本说明）。2.0.1 的双完整链误判修复继续保留，见 FIX-2.0.1.md。

## 数据与判断依据

- 官方根：https://android.googleapis.com/attestation/root
- 官方撤销列表：https://android.googleapis.com/attestation/status
- 官方说明：https://developer.android.com/privacy-and-security/security-key-attestation
- RKP 扩展：https://source.android.com/docs/security/features/keystore/attestation#provisioninginfo_extension

RKP 识别来自证书配置扩展，并非“远程读取私钥”。支持 certs_issued、旧 manufacturer、validated_attested_entity、is_lost_device 和未知字段保留。30 天颁发数是签发时近似快照，不是实时使用次数或共享人数。

旧 Google RSA 公钥固定匹配、明确工厂路径、无 RKP 配置扩展时，支持保守的旧工厂链过期兼容；RKP/未知路径/尚未生效证书不放宽。可选择全部严格有效期模式。签名和撤销检查不关闭。

用户手动离线导入仍不取得在线新鲜度、不接受新任意根、不抹去已有撤销条目；此限制不适用于所有者明确指定完全信任的仓库镜像。8 MiB 数据限额、原子缓存和失败保留旧快照。导入格式见 FEATURE-2.1.md。

## 安装与权限

- Android 7.0 / API 24 以上。
- 正式版包名：`com.larus.nova`；与旧测试包名不同，独立安装。未来正式版同包名、同生产签名才能覆盖升级。
- INTERNET 用于 jsDelivr/Google 公开信任数据更新；原有主动 RKP 高级工具需确认后向所选服务提交配置数据。
- 临期通知默认关闭，Android 13+ 开启时请求通知权限。WorkManager 约每 12 小时检查上次本机 RKP 快照，受节电调度限制，不联网断言当前证书状态。
- 历史/报告不自动上传；完整导出先提示，默认提供白名单脱敏报告。系统自动备份关闭。

## 构建与测试

JDK 21、Android SDK platform 35、Build Tools 35.0.1。设置 ANDROID_HOME 或 local.properties 中 sdk.dir。

```sh
chmod +x gradlew
./gradlew :app:testReleaseUnitTest
# 设置 SIGNING.md 中的签名环境后生成正式安装包
./gradlew :app:assembleRelease --no-daemon
```

低内存环境建议分步（避免 Kotlin 编译器与 Robolectric 争用内存）：

```sh
./gradlew :app:assembleRelease :app:compileReleaseUnitTestJavaWithJavac --no-daemon
./gradlew :app:testReleaseUnitTest --no-daemon '-Dorg.gradle.jvmargs=-Xmx320m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -XX:ActiveProcessorCount=2'
```

输出 `app/build/outputs/apk/release/app-release.apk`。完整测试记录和真机待验收清单见 TESTING.md。Python 镜像测试运行 `python -m unittest discover -s tools/tests -v`（需 cryptography）。

快照独立检查：

```sh
pip install cryptography
python tools/check_official_snapshot.py
```

## 稳定签名与 CI

Debug 构建不等于生产发行。正式 release 不再退回 debug 签名；需要仓库所有者配置和保管正式签名材料。手动生产 CI 与迁移说明见 **SIGNING.md**。不要向聊天或代码仓库提交私钥、令牌、keybox.xml 或真实诊断报告。

## 不做的承诺

本版本按需求**不做服务端挑战验证**。本机环境可能被篡改，本地验证不能保证系统未被入侵，不是 Play Integrity，不证明 Keybox 唯一。需要做重要安全决定时，应在独立可信环境校验证明和新鲜挑战。

本地策略/模拟界面测试不等于真实 TEE/StrongBox/RKP/各 ROM 验证；没有依据不输出“密钥安全分数”。
