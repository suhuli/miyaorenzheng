package com.larus.nova.dashboard;
import java.util.*;
/** Redaction is a whitelist, never fragile string replacement on raw attestation. */
public final class ReportFields {
    private ReportFields() {}
    public static final List<String> SAFE = Collections.unmodifiableList(Arrays.asList("数据对象","配置类型","根来源","签名路径","有效期策略","撤销检查","安全级别","启动锁","启动状态","补丁字段","证书数量"));
    public static Map<String,String> redact(Map<String,String> raw) {
        Map<String,String> result=new LinkedHashMap<>();
        for(String k:SAFE) if(raw.containsKey(k)) result.put(k,raw.get(k));
        return result;
    }
}
