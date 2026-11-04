# 2.0.1 最新验证记录

- APK 编译成功，签名校验成功。
- ChainOrderTest 13 项 + RkpPolicyTest 7 项，共 20 项测试，0 失败/错误。
- 新 EC 根/旧 RSA 根的双链结构已用合成 X.509 证书做回归验证；未用用户实际 DER 证书验签，未完成真机复测。详见 FIX-2.0.1.md。
- 本版包名 .community.fix，可与 2.0 并装。

---

以下为 2.0 初次交付记录及仍适用的真机验收范围：

# 验证记录与验收清单

## 本次实际完成

- `./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon`：BUILD SUCCESSFUL。
- RkpPolicyTest：7 项测试，0 失败、0 错误、0 跳过。覆盖相邻扩展、缺失扩展、非相邻扩展、根上扩展、最近扩展优先、异常参数和空链。
- `tools/check_official_snapshot.py`：2 个根证书的 CA 属性与自签名校验通过（RSA + EC）；1769 个撤销条目的序列号/状态格式检查通过。
- `apksigner verify --verbose`：通过，APK Signature Scheme v2，1 个测试签名者。
- APK 元数据核对：包名 io.github.vvb2060.keyattestation.community，版本 2.0.0-community-debug，minSdk 24 / targetSdk 35，含 INTERNET 权限。
- 编译仍有上游已弃用 Android API、Gradle 9 兼容和低内存 Metaspace 提示；当前 Gradle 8.12 构建成功。未声称全部 lint 无告警。

## 未完成、不能据此保证

- 没有真实 Android 设备/TEE/StrongBox/RKP 硬件运行验证。
- 没有声称已完成 APK 安装、UI 点击回归、所有厂商 ROM、Shizuku 私有接口兼容性测试。
- 官方接口数据是在构建环境实际下载并验证；App 内联网、断网/超时、缓存恢复需真机验收。
- 单元测试验证的是 RKP 证据位置政策，不代替证书解析器安全审计、完整 PKIX 验证、篡改系统抗攻击测试或生产认证。
- 没有正式发行签名，也没有上传到 GitHub 或应用商店。

## 真机建议逐项验收

1. Android 7+ 普通设备安装，启动生成证明；不支持硬件证明时应给出错误而非假结果。
2. Android 12+ 含 RKP 设备查看配置扩展与 RKP 判定；缺失扩展显示无法确定。
3. 支持 StrongBox 的设备切换并生成证明；无硬件时不应展示可用选项。
4. 联网更新后检查官方来源、更新时间及错误状态，保存证书再加载验证。
5. 飞行模式且无缓存/缓存过期时打开，确认有撤销状态未确认警告，不出现“已在线确认”的假结论。
6. 强制更新失败保留旧快照；缓存异常/时间回拨应回退保守状态。
7. 导入过期、签名损坏、撤销条目命中的真实测试链，确认失败；不能仅凭主题名称标 RKP。
8. 导出报告并打开，核对证书指纹/序列号/解析数据与实际证书。
9. 如需主动 RKP 测试，在支持设备上授权 Shizuku，确认联网提示和主机可信性后再测试。日常被动识别不需要此项。
