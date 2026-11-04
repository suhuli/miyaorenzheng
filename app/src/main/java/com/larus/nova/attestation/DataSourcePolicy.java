package com.larus.nova.attestation;

/** Persisted user preference; invalid/absent values safely use the official-first default. */
public final class DataSourcePolicy {
    public static final String KEY = "data_source_priority";
    public static final String OFFICIAL_FIRST = "official_first";
    public static final String MIRROR_FIRST = "mirror_first";
    private DataSourcePolicy() {}
    public static String normalize(String value) {
        return MIRROR_FIRST.equals(value) ? MIRROR_FIRST : OFFICIAL_FIRST;
    }
    public static String label(String value) {
        return MIRROR_FIRST.equals(normalize(value))
            ? "jsDelivr → Google 官方 → 本地缓存" : "Google 官方 → jsDelivr → 本地缓存";
    }
}
