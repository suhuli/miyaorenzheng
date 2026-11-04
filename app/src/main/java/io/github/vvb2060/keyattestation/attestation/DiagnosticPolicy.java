package io.github.vvb2060.keyattestation.attestation;
/** Pure policies shared by UI, reminders, and tests. */
public final class DiagnosticPolicy {
    private DiagnosticPolicy() {}
    public static boolean fresh(long now,long fetched) {
        return fetched>0 && now>=fetched && now-fetched<86400000L;
    }
    public static String expiry(long now,long expires) {
        long diff=expires-now;
        if(diff<=0) return "已到期";
        if(diff<=3L*86400000L) return "3 天内到期";
        if(diff<=7L*86400000L) return "7 天内到期";
        return "暂无临期提醒";
    }
    public static String revocation(boolean listed,boolean fresh) {
        return listed?"撤销/暂停列表命中":fresh?"最新列表未命中":"当前状态未确认";
    }
}
