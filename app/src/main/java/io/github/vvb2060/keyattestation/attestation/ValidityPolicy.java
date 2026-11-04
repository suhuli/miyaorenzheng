package io.github.vvb2060.keyattestation.attestation;
/** Conservative official factory-key exception; never waive not-yet-valid dates or RKP. */
public final class ValidityPolicy {
    private ValidityPolicy() {}
    public static boolean allowExpired(boolean pinnedLegacyRoot, boolean knownFactoryPath,
                                        boolean provisioningPresent, boolean strict) {
        return pinnedLegacyRoot && knownFactoryPath && !provisioningPresent && !strict;
    }
}
