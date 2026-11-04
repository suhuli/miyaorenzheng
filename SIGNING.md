# 2.4.3 正式签名

本版为正式 `release` 构建：包名 `com.larus.nova`，应用名「密钥认证」，版本 2.4.3 / 243；不附加 community/debug 后缀，`debuggable=false`。开启代码压缩和资源缩减；保留依赖库 consumer keep rules 与 Worker 反射构造规则。

沿用 2.3.0 时所有者选择生成的独立生产密钥，不重新生成。2.4.0 可覆盖安装 2.3.0。使用 RSA-3072 / SHA256withRSA、PKCS12、别名 `nova-release`；不是 Android debug 密钥。私钥和随机生成的密码单独私下交付，不进入 Git/GitHub 发布、网盘或公开源码。

请立即下载并离线备份 `Nova-Release-Signing-PRIVATE.zip`，其中含私钥、密码与恢复说明。它能签署后续所有版本，必须私密保存。丢失密钥可能导致以后无法正常覆盖升级；泄露时他人可能伪造相同签名的版本。ZIP 内含密码，本身不是加密保险箱。

2.3 起的正式包名与 2.2 及更早测试版不同；2.4 保持 2.3 的包名和签名，只变更代码命名空间，保留同包内设置/历史及迁移后的缓存。后续正式版必须保持同一包名、同一生产签名，递增 versionCode。

## 本地构建

签名参数只从环境变量读取，不写入 Gradle 文件或仓库：

- KEYSTORE_PATH：nova-release.p12 的绝对路径
- KEYSTORE_PASSWORD
- KEY_ALIAS：nova-release
- KEY_PASSWORD：与 PKCS12 存储密码一致

运行 `./gradlew :app:assembleRelease`。缺失签名变量时打包直接失败，不会退回 debug 密钥，也不交付 unsigned APK。

## GitHub Actions

默认 App validation 仅运行 release 变体单元测试，不产生或上传 debug APK。要在 GitHub 构建相同生产签名，所有者需自行为 production environment 配置：KEYSTORE_BASE64、KEYSTORE_PASSWORD、KEY_ALIAS、KEY_PASSWORD；建议为该 environment 开启人工审批。

手动运行 Production signed APK 工作流。私钥仅在 Runner 临时目录存在，结束时清理；缺少 Secrets 会失败。本次没有将新私钥自动传到 GitHub Secrets。
