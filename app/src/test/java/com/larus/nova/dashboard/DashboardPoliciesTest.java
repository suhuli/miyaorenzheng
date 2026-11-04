package com.larus.nova.dashboard;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class DashboardPoliciesTest {
    @Test public void redactionNeverIncludesIdentifiers() {var raw=new HashMap<String,String>();for(String key:List.of("叶指纹","根指纹","challenge","serial","bootHash","uniqueId","原始证明"))raw.put(key,"SECRET");raw.put("根来源","Google");var clean=ReportFields.redact(raw);assertEquals(Map.of("根来源","Google"),clean);assertFalse(clean.containsValue("SECRET"));}
    @Test public void emptySafeReport() {assertTrue(ReportFields.redact(Map.of("secret","hidden")).isEmpty());}
    @Test public void ignoredTimestamp() {assertTrue(SnapshotDiff.compare(Map.of("记录时间","1"),Map.of("记录时间","2")).isEmpty());}
    @Test public void meaningfulChanges() {assertEquals(List.of("根来源：A → B"),SnapshotDiff.compare(Map.of("根来源","A"),Map.of("根来源","B")));}
    @Test public void missingFieldExplicit() {assertEquals(List.of("安全级别：未提供 → TEE"),SnapshotDiff.compare(Map.of(),Map.of("安全级别","TEE")));}
}
