# Key Attestation Community 2.0.1

项目仓库：[suhuli/miyaorenzheng](https://github.com/suhuli/miyaorenzheng)。

基于 [vvb2060/KeyAttestation](https://github.com/vvb2060/KeyAttestation) 的非官方社区修改版。
上游基线：`b223a9be86cb2a747625d8e47f3456646d55bd7a`（1.8.4）。不是 Google 或上游发布的官方 App。
保留上游作者、版权声明与 Apache-2.0 许可，原说明见 README.upstream.md。

## 2.0.1 双证书链误判修复

2.0 在启用 App Attest Key 时，把两条完整独立链拼接；上游排序在多叶/重复根情况下回退原顺序，新增加的 issuer 校验因此在第二个根处报错。新 EC 根 RKP 和旧 RSA 根密钥都受此问题影响，并非仅新根识别问题，也不是本次报告中的有效期问题。

- 实际别名已经返回完整链时，不再追加独立 App Attest Key 链。
- 按证书内容去重，以实际生成的叶证书为目标，结合 issuer 名称与密码学签名构造单一路径；不再把多条链串接或回退原顺序。
- 需要 App Attest Key 补链时仍支持补链，且每条边继续验签。
- 无指定目标的导入若含多条独立链，明确要求分别导入，不猜测目标，也不放松安全校验。
- 保留官方根、有效期与撤销检查；13 项新增回归测试使用合成 X.509 证书（包含新 EC 根/旧 RSA 根的双完整链结构），没有冒充用户真实证书验证。
- 本次测试签名与 2.0 不同，因此修复版改用独立 `.community.fix` 包名并标记 Community Fix，可以并装；不能访问旧 App 私有数据/密钥，请在新版重新生成证明，必要时自行导入之前保存的公开证书。

## 新增/修正

- **官方数据**：Google 官方 `https://android.googleapis.com/attestation/root` 和 `https://android.googleapis.com/attestation/status`。打包时已从这两个接口获取快照，包含 RSA 与 EC 两个根证书、1769 个撤销条目；数量会随官方更新变化。
- **在线更新与缓存**：打开主界面时检查缓存，24 小时内不重复下载；也可以通过菜单强制更新。仅在前台启动检查，不是常驻后台定时同步。固定 HTTPS 来源、不跟随重定向、连接/读取超时、8 MiB 响应限制、解析后再原子保存成对数据。失败保留上次有效数据；时间异常/超过 24 小时/内置快照均提示当前撤销状态未确认。
- **根轮换**：根公钥集合来自官方根证书数组，不再只匹配旧 RSA 根。设备 OEM/AOSP/Knox 的原有识别保留，但不冒充官方 Google 根。
- **RKP 识别**：根到叶检查 `1.3.6.1.4.1.11129.2.1.30` provisioning 扩展，要求最近的配置扩展紧邻证明证书，展示 certs_issued / manufacturer（如果提供）。只有官方根及签名/有效期/撤销检查通过时才标记为“已识别”；扩展存在但链失败标记不可信；未找到证据标记“无法确定”，不武断标为工厂密钥。不能识别任意“远程私钥位置”，也不连接远端去提取密钥。
- **验证修正**：过期证书不再进入正常链状态；检查 issuer/subject 对应；只有硬件声明用途集合恰为 ATTEST_KEY 才允许沿自定义证明密钥继续解析，避免混合 SIGN 用途绕过；防御畸形 provisioning CBOR 引发的运行时异常。更新数据后重验当前显示链。
- **报告导出**：菜单导出 UTF-8 报告，含数据来源/更新时间、RKP 结论、证书主体/发行者/序列号/有效期/SHA-256、解析证明及错误信息。使用系统文件选择器，导出前隐私提示。
- **原有功能保留**：TEE/StrongBox、自定义 attest key、证书导入/导出、启动锁与安全级别、证明授权字段，以及需要设备支持和 Shizuku 权限的功能。主动 RKP 测试新增联网确认。
- **独立包名**：`io.github.vvb2060.keyattestation.community.fix`，可与原版并存。

## 使用

1. 安装 `KeyAttestation-Community-2.0.1-debug.apk`，Android 7.0/API 24 或以上。
2. 启动读取本机密钥证明。菜单“更新官方信任数据”可强制刷新；“官方数据来源与更新状态”查看错误和新鲜度。
3. 在证书链上方查看“RKP detection / 远程密钥识别”。识别本身不需要 Shizuku；是否有 RKP 证据取决于系统实际返回的证书链。
4. 菜单可切换 StrongBox、加载证书、保存证书，或导出文本报告。某些选项只在支持的系统/硬件和授权条件下显示。
5. 主动 RKP 测试属于上游高级功能，需要 Shizuku/平台接口，不等于被动证书识别，不能保证所有新系统都可用。

## 重要边界

- 这是**本机诊断工具**，不是 Play Integrity API，不证明设备绝对安全、不保证 Keybox 唯一，也不保证系统未被篡改。
- 不在可疑设备上做最终信任决定。官方建议在独立可信服务器验证证明，并校验服务端新鲜挑战。此版本没有实现服务端挑战协议、服务端验证服务或生产级完整 PKIX 策略引擎。
- 扩展证据/官方根识别与设备完整性是不同结论；当前撤销数据过期时，未命中条目不等于当前未撤销。
- 有些旧版 RKP 链可能没有可识别配置扩展，因此显示“无法确定”是有意的保守结果。
- 有效期采取严格失败策略；不会自动套用官方对特定过期工厂密钥的例外政策，可能比官方生产验证器更保守。
- 官方数据更新仅下载公开数据，不上传设备证明。**原有主动 RKP 测试**在确认后会向配置的 RKP 服务提交 CSR/配置数据；请勿向不可信自定义主机发送。
- 报告和证书可能带设备相关标识；只在自愿导出时写入所选位置，不自动上传。

## 构建

需要 JDK 21、Android SDK platform 35、Build Tools 35.0.1。

```sh
# 设置 ANDROID_HOME 或在 local.properties 写 sdk.dir=/你的SDK路径
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。Gradle 8.12、AGP 8.8.0、Kotlin 2.1.0 保持与可验证上游工具链兼容，语言级别修正为 2.1；没有未经验证地升级所有依赖到“最新”。
低内存环境默认限制 1 个 Gradle worker / 进程内 Kotlin 编译；内存充裕时可自行提高。
CI 已配置构建、20 项证据政策/证书链单元测试及上传 APK。

### 签名说明

附带 APK 是**debug 测试签名**，可安装但不作为正式商店发行包。未来自行构建使用不同测试签名时可能需卸载旧版。
正式发行请由你自己创建/保管 keystore，并修改 signingConfigs/buildTypes.release；不要把私钥提交到仓库。
**上游 release 默认沿用 debug 签名的设置仍存在，不能直接当成正式发行签名。**

### 数据快照检查

```sh
pip install cryptography
python tools/check_official_snapshot.py
```

验证快照根自签名、CA 属性和撤销数据格式，不伪造手机证明。已知测试范围见 TESTING.md。

## 官方参考

- https://developer.android.com/privacy-and-security/security-key-attestation
- https://source.android.com/docs/security/features/keystore/attestation
- https://android.googleapis.com/attestation/root
- https://android.googleapis.com/attestation/status

## 许可

Apache License 2.0，见 LICENSE；上游及第三方版权归各权利人所有。
