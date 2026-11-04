package com.larus.nova.repository;

import static com.larus.nova.attestation.Attestation.KM_SECURITY_LEVEL_SOFTWARE;
import static com.larus.nova.lang.AttestationException.CODE_CANT_PARSE_CERT;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import com.larus.nova.attestation.Attestation;
import com.larus.nova.attestation.CertificateInfo;
import com.larus.nova.attestation.RootOfTrust;
import com.larus.nova.lang.AttestationException;

public class AttestationData extends BaseData {
    private final RootOfTrust rootOfTrust;
    private final boolean sw;
    public Attestation showAttestation;

    public RootOfTrust getRootOfTrust() {
        return rootOfTrust;
    }

    public boolean isSoftwareLevel() {
        return sw;
    }

    private AttestationData(List<CertificateInfo> certs) {
        init(certs);

        var info = certs.get(certs.size() - 1);
        var attestation = info.getAttestation();
        if (attestation != null) {
            this.showAttestation = attestation;
            this.rootOfTrust = attestation.getRootOfTrust();
            this.sw = attestation.getAttestationSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE;
        } else {
            throw new AttestationException(CODE_CANT_PARSE_CERT, info.getCertException());
        }
    }

    private static boolean signedBy(X509Certificate child, X509Certificate parent) {
        if (!child.getIssuerX500Principal().equals(parent.getSubjectX500Principal())) return false;
        try { child.verify(parent.getPublicKey()); return true; }
        catch (java.security.GeneralSecurityException e) { return false; }
    }

    static AttestationData parseCertificateChain(List<X509Certificate> certs) {
        return parseCertificateChain(certs, null);
    }

    static AttestationData parseCertificateChain(List<X509Certificate> certs, X509Certificate leaf) {
        var ordered = com.larus.nova.attestation.ChainOrder.select(
            certs, leaf, AttestationData::signedBy, c -> signedBy(c, c));
        var infoList = new ArrayList<CertificateInfo>(ordered.size());
        CertificateInfo.parse(ordered, infoList);
        return new AttestationData(infoList);
    }
}
