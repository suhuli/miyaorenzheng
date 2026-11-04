# 正式签名与后续升级

## 本次交付

当前安装包仍为测试签名，包名保持 `io.github.vvb2060.keyattestation.community.fix`。
构建环境保留了 2.0.1 的测试密钥；交付前会比对两版签名证书摘要，只有一致才声称可覆盖升级。
测试私钥不加入源码、ZIP 或 Git 仓库。GitHub 临时构建默认的 debug 私钥每个 Runner 不同，不保证互相覆盖升级。

## 正式发行需要由仓库所有者保管私钥

本版 release 已移除“沿用 debug 签名”的危险默认值。没有正式密钥时不会生成伪装为正式签名的安装包。
如需从测试签名迁移为生产签名，Android 一般不能直接同包覆盖安装；应先导出所需公开数据后卸载测试版，或为正式发行使用独立 applicationId。不要承诺绕过 Android 签名校验。

创建自己的签名密钥（在你自己的安全机器上执行）：

```sh
keytool -genkeypair -v -keystore release.keystore -alias release -keyalg RSA -keysize 3072 -validity 10000
```

保管密钥与恢复副本，不要通过聊天发送。构建参数只从环境变量读取：

- `KEYSTORE_PATH`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

运行 `./gradlew :app:assembleRelease`。

## GitHub Actions

配置 GitHub `production` environment 并启用人工审批保护（需要仓库所有者设置）。保存四个 secrets：

- `KEYSTORE_BASE64`：keystore 的 Base64 编码
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

手动运行 `Signed release (owner configured)` 工作流。缺少任一 secret 会失败，不会退回 debug 签名。私钥仅在 Runner 临时目录存在，结束时清理。后续版本保持同一包名、同一生产签名、递增 versionCode 即可正常升级。
