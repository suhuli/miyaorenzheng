package com.larus.nova.attestation;
import java.security.cert.X509Certificate;
public final class ProvisioningInfo {
    private final ProvisioningFields fields;
    private ProvisioningInfo(ProvisioningFields fields) { this.fields = fields; }
    public static ProvisioningInfo get(X509Certificate cert) {
        var bytes = cert.getExtensionValue("1.3.6.1.4.1.11129.2.1.30");
        if (bytes==null) return null;
        try {
            var asn1 = Asn1Utils.getAsn1EncodableFromBytes(bytes);
            return new ProvisioningInfo(ProvisioningFields.parse(Asn1Utils.getByteArrayFromAsn1(asn1)));
        } catch (Exception e) { return null; }
    }
    public Long getCertsIssued() { return fields.certsIssued; }
    public String getManufacturer() { return fields.manufacturer; }
    public String getValidatedAttestedEntity() { return fields.validatedAttestedEntity; }
    public Boolean getLostDevice() { return fields.lostDevice; }
    public java.util.Map<String,String> getUnknownFields() { return java.util.Collections.unmodifiableMap(fields.unknown); }
    public java.util.List<ProvisioningFields.ExtraField> getExtraFields() { return java.util.Collections.unmodifiableList(fields.extraFields); }
    public String describe() {
        return "过去 30 天颁发近似数：" + value(fields.certsIssued)
            + "\n受认证来源：" + value(fields.validatedAttestedEntity)
            + "\n遗失设备标记：" + (fields.lostDevice==null?"未提供":fields.lostDevice?"证书标记为遗失设备":"未标记为遗失（不构成当前未遗失证明）")
            + "\n制造商（旧字段）：" + value(fields.manufacturer)
            + (fields.unknown.isEmpty()?"":"\n附加配置字段（语义待确认）：\n"+extraDetails())
            + "\n这是证书签发时的快照，不是实时使用次数或用户数量。";
    }
    private String extraDetails() {
        StringBuilder text = new StringBuilder();
        for (String item : fields.unknown.values()) { if (text.length() > 0) text.append("\n"); text.append(item); }
        return text.toString();
    }
    private String value(Object value) { return value==null?"未提供":value.toString(); }
}
