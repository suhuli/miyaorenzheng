package com.larus.nova.attestation;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiagnosticPolicyTest {
    @Test public void bundledNotFresh() {assertFalse(DiagnosticPolicy.fresh(100,0));}
    @Test public void rollbackNotFresh() {assertFalse(DiagnosticPolicy.fresh(10,20));}
    @Test public void expiredCacheNotFresh() {assertFalse(DiagnosticPolicy.fresh(86400001L,1));}
    @Test public void recentCacheFresh() {assertTrue(DiagnosticPolicy.fresh(100,1));}
    @Test public void expiredReminder() {assertEquals("已到期",DiagnosticPolicy.expiry(100,99));}
    @Test public void threeDayBoundary() {assertEquals("3 天内到期",DiagnosticPolicy.expiry(1,1+3L*86400000));}
    @Test public void sevenDayBoundary() {assertEquals("7 天内到期",DiagnosticPolicy.expiry(1,1+7L*86400000));}
    @Test public void distantNoReminder() {assertEquals("暂无临期提醒",DiagnosticPolicy.expiry(1,2+7L*86400000));}
    @Test public void staleNotClear() {assertEquals("当前状态未确认",DiagnosticPolicy.revocation(false,false));}
    @Test public void listedOverridesFreshness() {assertEquals("撤销/暂停列表命中",DiagnosticPolicy.revocation(true,false));}
}
