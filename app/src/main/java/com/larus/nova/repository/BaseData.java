package com.larus.nova.repository;

import java.util.List;

import com.larus.nova.attestation.CertificateInfo;
import com.larus.nova.attestation.RootPublicKey;

public abstract class BaseData {
    protected List<CertificateInfo> certs;
    protected RootPublicKey.Status status;

    protected void init(List<CertificateInfo> certs) {
        this.certs = certs;
        if (certs.isEmpty()) {
            this.status = RootPublicKey.Status.NULL;
            return;
        }

        var root = certs.get(0).getCert();
        boolean factory = false, provisioning = false;
        for (var info : certs) {
            var name = info.getCert().getSubjectX500Principal().getName();
            if (info.getAttestation() == null && java.util.regex.Pattern.compile("(^|,)T=(TEE|StrongBox|STRONGBOX)(,|$)").matcher(name).find()) factory = true;
            if (info.getCert().getExtensionValue("1.3.6.1.4.1.11129.2.1.30") != null) provisioning = true;
        }
        boolean strict = com.larus.nova.AppApplication.app
                .getSharedPreferences("dashboard", 0).getBoolean("strict_expiry", false);
        boolean allow = com.larus.nova.attestation.ValidityPolicy.allowExpired(
            com.larus.nova.attestation.OfficialData.matches(root.getPublicKey().getEncoded())
                && RootPublicKey.isLegacyGoogleKey(root.getPublicKey().getEncoded()), factory, provisioning, strict);
        var parent = root;
        for (var info : certs) { info.revalidate(parent, allow); parent = info.getCert(); }

        var status = certs.get(0).getIssuer();
        for (var cert : certs) {
            if (cert.getStatus() != CertificateInfo.CERT_NORMAL) {
                status = RootPublicKey.Status.FAILED;
                break;
            }
        }
        if (status == RootPublicKey.Status.GOOGLE && hasRkpEvidence()) {
            status = RootPublicKey.Status.GOOGLE_RKP;
        }
        this.status = status;
    }

    public void revalidate() { if (certs != null) init(certs); }

    public boolean hasRkpEvidence() {
        boolean[] p = new boolean[certs.size()], a = new boolean[certs.size()];
        for (int i = 0; i < certs.size(); i++) {
            p[i] = certs.get(i).getCert().getExtensionValue("1.3.6.1.4.1.11129.2.1.30") != null;
            if (p[i] && certs.get(i).getProvisioningInfo() == null) return false;
            a[i] = certs.get(i).getAttestation() != null;
        }
        return com.larus.nova.attestation.RkpPolicy.hasAdjacentEvidence(p, a);
    }

    public String getRkpSummary() {
        boolean verified = status == RootPublicKey.Status.GOOGLE_RKP;
        String result = verified ? "RKP：已识别（官方根 + 有效链 + 相邻 provisioning 扩展）"
                : hasRkpEvidence() ? "RKP：存在配置扩展，但链未通过官方根/有效性验证，不可信"
                : "RKP：无法确定；未发现可验证配置扩展，不能据此判定为工厂密钥";
        for (var cert : certs) {
            var p = cert.getProvisioningInfo();
            if (p != null) result += "\n" + p.describe();
        }
        return result + "\n当前撤销数据：" + (com.larus.nova.attestation.OfficialData.isFresh()
                ? "24 小时内更新" : "离线/过期，当前撤销状态未确认")
                + "\n本机自测不是远程可信验证，RKP 不等于设备完整性认证。";
    }

    public List<CertificateInfo> getCerts() {
        return certs;
    }

    public RootPublicKey.Status getStatus() {
        return status;
    }
}
