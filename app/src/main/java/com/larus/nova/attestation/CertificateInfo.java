package com.larus.nova.attestation;

import android.util.Log;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.List;

import com.larus.nova.AppApplication;

public class CertificateInfo {
    public static final int CERT_UNKNOWN = 0;
    public static final int CERT_SIGN = 1;
    public static final int CERT_REVOKED = 2;
    public static final int CERT_EXPIRED = 3;
    public static final int CERT_NORMAL = 4;

    private final X509Certificate cert;
    private RootPublicKey.Status issuer = RootPublicKey.Status.UNKNOWN;
    private int status = CERT_UNKNOWN;
    private GeneralSecurityException securityException;
    private Attestation attestation;
    private CertificateParsingException certException;

    private ProvisioningInfo provisioningInfo;
    private boolean signatureValid, legacyExpiredAccepted, allowExpired;
    private RevocationList revocation;
    public boolean isSignatureValid() { return signatureValid; }
    public boolean isLegacyExpiredAccepted() { return legacyExpiredAccepted; }
    public RevocationList getRevocation() { return revocation; }
    public String getValidityLabel() {
        if (legacyExpiredAccepted) return "已过期 · 旧工厂链兼容";
        if (cert.getNotBefore().after(new java.util.Date())) return "尚未生效";
        if (cert.getNotAfter().before(new java.util.Date())) return "已过期";
        return "有效期内";
    }

    private CertificateInfo(X509Certificate cert) {
        this.cert = cert;
    }

    public X509Certificate getCert() {
        return cert;
    }

    public RootPublicKey.Status getIssuer() {
        return issuer;
    }

    public int getStatus() {
        return status;
    }

    public GeneralSecurityException getSecurityException() {
        return securityException;
    }

    public Attestation getAttestation() {
        return attestation;
    }

    public CertificateParsingException getCertException() {
        return certException;
    }

    public ProvisioningInfo getProvisioningInfo() {
        return provisioningInfo;
    }

    private void checkIssuer() {
        var publicKey = cert.getPublicKey().getEncoded();
        issuer = RootPublicKey.check(publicKey);
    }

    private void checkStatus(PublicKey parentKey) {
        securityException = null;
        signatureValid = false; legacyExpiredAccepted = false; revocation = null;
        try {
            status = CERT_SIGN;
            cert.verify(parentKey);
            signatureValid = true;
            status = CERT_REVOKED;
            var certStatus = RevocationList.get(cert.getSerialNumber());
            revocation = certStatus;
            if (certStatus != null) {
                throw new CertificateException("Certificate revocation " + certStatus);
            }
            status = CERT_EXPIRED;
            try { cert.checkValidity(); }
            catch (java.security.cert.CertificateExpiredException e) {
                if (!allowExpired) throw e;
                legacyExpiredAccepted = true;
            }
            status = CERT_NORMAL;
        } catch (GeneralSecurityException e) {
            Log.e(AppApplication.TAG, "checkStatus", e);
            securityException = e;
        }
    }

    private boolean checkAttestation() {
        boolean terminate;
        provisioningInfo = ProvisioningInfo.get(cert);
        try {
            attestation = Attestation.loadFromCertificate(cert);
            // If key purpose included KeyPurpose::SIGN,
            // then it could be used to sign arbitrary data, including any tbsCertificate,
            // and so an attestation produced by the key would have no security properties.
            // If the parent certificate can attest that the key purpose is only KeyPurpose::ATTEST_KEY,
            // then the child certificate can be trusted.
            var purposes = attestation.getTeeEnforced().getPurposes();
            terminate = purposes == null || purposes.size() != 1
                    || !purposes.contains(AuthorizationList.KM_PURPOSE_ATTEST_KEY);
        } catch (CertificateParsingException e) {
            certException = e;
            terminate = false;
            provisioningInfo = ProvisioningInfo.get(cert);
        }
        return terminate;
    }

    public void revalidate(X509Certificate parent) { revalidate(parent, false); }

    public void revalidate(X509Certificate parent, boolean allowLegacyExpiry) {
        allowExpired = allowLegacyExpiry;
        checkStatus(parent.getPublicKey());
        if (!cert.getIssuerX500Principal().equals(parent.getSubjectX500Principal())) {
            status = CERT_SIGN;
            signatureValid = false;
            securityException = new CertificateException("Issuer/subject chain mismatch");
        }
        checkIssuer();
    }

    public static void parse(List<X509Certificate> certs, List<CertificateInfo> infoList) {
        var parent = certs.get(certs.size() - 1);
        for (int i = certs.size() - 1; i >= 0; i--) {
            var parentKey = parent.getPublicKey();
            var info = new CertificateInfo(certs.get(i));
            infoList.add(info);
            info.checkStatus(parentKey);
            if (!info.cert.getIssuerX500Principal().equals(parent.getSubjectX500Principal())) {
                info.status = CERT_SIGN;
                info.securityException = new CertificateException("Issuer/subject chain mismatch");
            }
            if (parent == info.cert) {
                info.checkIssuer();
            } else {
                parent = info.cert;
            }
            if (info.checkAttestation()) {
                break;
            }
        }
    }
}
