package com.larus.nova.attestation;

/** Pure evidence policy. Subject names and root algorithm are deliberately not inputs. */
public final class RkpPolicy {
    private RkpPolicy() {}
    public static boolean hasAdjacentEvidence(boolean[] provisioning, boolean[] attestation) {
        if (provisioning == null || attestation == null || provisioning.length != attestation.length) return false;
        for (int i = 1; i + 1 < provisioning.length; i++) {
            if (provisioning[i]) return attestation[i + 1]; // nearest to root only
        }
        return false;
    }
}
