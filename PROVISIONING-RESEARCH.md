# Provisioning 扩展研究与实现边界 · 2.4.3

## 查阅依据

- AOSP Key and ID attestation / provisioning information extension：
  https://source.android.com/docs/security/features/keystore/attestation#provisioninginfo_extension_schema
  本次查阅公开 schema 列出 1: certificates issued、4: validated attested entity、可选 6: is lost device，并说明无版本映射可添加可选字段。
- Android 官方验证库，固定查看提交 0e3ac2fdd54799d779e1979a96fbcba0361b2089：
  https://github.com/android/keyattestation/blob/0e3ac2fdd54799d779e1979a96fbcba0361b2089/src/main/kotlin/Extension.kt
  该提交 ProvisioningInfoMap 只抽取 certificatesIssued；不能从这个实现推导字段 5 的正式业务名。
- 上游 vvb2060/KeyAttestation ProvisioningInfo 实现：
  https://github.com/vvb2060/KeyAttestation/blob/master/app/src/main/java/io/github/vvb2060/keyattestation/attestation/ProvisioningInfo.java
  旧实现抽取 1 与旧制造商字段 3，其他字段只记日志。本项目保留已有 3 兼容，并支持公开 4/6。

## 针对嵌套字段的结论

样本结构显示外层数字键 5 下还有 1、2 等子键。结构可解析，不应再只显示“其他字段 X 项”。但本次公开资料检索未找到可靠来源确定 5.1/5.2 的正式名称或用途。APPROVED 字面可以译为已批准，却不能据此推导成 Play Integrity 通过、系统当前安全、未 Root、启动状态验证等结论。十六进制外观或 32 字节长度也不能单独证明它就是 SHA-256、VBMeta 摘要或设备标识。

实现通用 CBOR 结构化展示：5（映射）→5.1/5.2，保留值类型；字节串转 hex，文本不冒充字节串，数组保留索引，null/false/0 不混淆。保留语义待确认提示，不按猜测设置绿灯，也不反向填充缺失的 validated_attested_entity。

在拿到可核验的 Google/厂商定义后再增加具体语义映射。未经确认的值只在本地完整详情/主页展示，不新增到默认脱敏报告字段白名单。源码、测试和公开附件使用合成值，不包含用户提供的具体指纹。

## 显示与限制

主页卡片顺序：证书 → 设备 → 配置扩展。配置扩展是最后一张信息卡，操作按钮与数据源页脚仍在下方。

扩展解析输入上限 256 KiB，展开最大 32 层、1024 节点，超出时标明解析失败。仍按原逻辑验证链/根/日期/撤销和扩展位置；解析出字段不等于认证业务含义已确认。
