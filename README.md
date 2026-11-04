# 密钥认证 · Key Attestation Community 2.1

原生 Android 本机密钥证明诊断工具。Material 风格新首页、证书链可视化、本地历史对比、官方信任数据更新与 RKP 配置证据识别。

社区仓库：https://github.com/suhuli/miyaorenzheng

基于 [vvb2060/KeyAttestation](https://github.com/vvb2060/KeyAttestation) 的非官方社区修改版，上游基线 `b223a9be86cb2a747625d8e47f3456646d55bd7a`。保留上游作者、版权和 Apache-2.0 许可，见 LICENSE、README.upstream.md。不是 Google 或原作者的官方发行版。

## 四个分区

- **概览**：配置类型、根来源、签名路径、有效期策略、撤销检查分别显示；设备启动状态与安全级别不是一张“绝对安全”绿卡。
- **证书链**：根到目标证书的层级连接、每条连接验签、有效期、RKP 扩展和完整字段；原有高级工具独立保留。
- **记录**：本机最多 30 次摘要、任意两次对比、停止记录/一键清空；正常轮换不当成泄露。摘要仍含可关联指纹。
- **设置**：固定 Google HTTPS 官方数据更新、差异和错误、离线数据包导入导出、隐私、临期通知、浅/深色与跟随系统。

详细变更与安全边界见 **FEATURE-2.1.md**。2.0.1 的双完整链误判修复继续保留，见 FIX-2.0.1.md。

## 数据与判断依据

- 官方根：https://android.googleapis.com/attestation/root
- 官方撤销列表：https://android.googleapis.com/attestation/status
- 官方说明：https://developer.android.com/privacy-and-security/security-key-attestation
- RKP 扩展：https://source.android.com/docs/security/features/keystore/attestation#provisioninginfo_extension

RKP 识别来自证书配置扩展，并非“远程读取私钥”。支持 certs_issued、旧 manufacturer、validated_attested_entity、is_lost_device 和未知字段保留。30 天颁发数是签发时近似快照，不是实时使用次数或共享人数。

旧 Google RSA 公钥固定匹配、明确工厂路径、无 RKP 配置扩展时，支持保守的旧工厂链过期兼容；RKP/未知路径/尚未生效证书不放宽。可选择全部严格有效期模式。签名和撤销检查不关闭。

离线导入不取得在线新鲜度，不接受新任意信任根，不抹去本机已有撤销条目。8 MiB 数据限额、原子缓存和失败保留旧快照。导入格式见 FEATURE-2.1.md。

## 安装与权限

- Android 7.0 / API 24 以上。
- 测试版包名：`io.github.vvb2060.keyattestation.community.fix`，与 2.0.1 保持一致；须用同一签名才能覆盖升级。
- INTERNET 仅用于公开信任数据更新；原有主动 RKP 高级工具需确认后向所选服务提交配置数据。
- 临期通知默认关闭，Android 13+ 开启时请求通知权限。WorkManager 约每 12 小时检查上次本机 RKP 快照，受节电调度限制，不联网断言当前证书状态。
- 历史/报告不自动上传；完整导出先提示，默认提供白名单脱敏报告。系统自动备份关闭。

## 构建与测试

JDK 21、Android SDK platform 35、Build Tools 35.0.1。设置 ANDROID_HOME 或 local.properties 中 sdk.dir。

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

低内存环境建议分步（避免 Kotlin 编译器与 Robolectric 争用内存）：

```sh
./gradlew :app:assembleDebug :app:compileDebugUnitTestJavaWithJavac --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon '-Dorg.gradle.jvmargs=-Xmx320m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -XX:ActiveProcessorCount=2'
```

输出 `app/build/outputs/apk/debug/app-debug.apk`。完整测试记录和真机待验收清单见 TESTING.md。

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
