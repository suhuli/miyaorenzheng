package io.github.vvb2060.keyattestation.attestation;
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
    public String describe() {
        return "过去 30 天颁发近似数：" + value(fields.certsIssued)
            + "\n受认证来源：" + value(fields.validatedAttestedEntity)
            + "\n遗失设备标记：" + (fields.lostDevice==null?"未提供":fields.lostDevice?"证书标记为遗失设备":"未标记为遗失（不构成当前未遗失证明）")
            + "\n制造商（旧字段）：" + value(fields.manufacturer)
            + (fields.unknown.isEmpty()?"":"\n未知扩展字段："+fields.unknown)
            + "\n这是证书签发时的快照，不是实时使用次数或用户数量。";
    }
    private String value(Object value) { return value==null?"未提供":value.toString(); }
}
