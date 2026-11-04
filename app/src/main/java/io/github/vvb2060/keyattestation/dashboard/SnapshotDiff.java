package io.github.vvb2060.keyattestation.dashboard;
import java.util.*;
public final class SnapshotDiff {
    private SnapshotDiff() {}
    public static List<String> compare(Map<String,String> before,Map<String,String> after) {
        Set<String> keys=new TreeSet<>(before.keySet()); keys.addAll(after.keySet());
        List<String> changes=new ArrayList<>();
        for(String key:keys) {
            if(key.equals("记录时间")) continue;
            String a=before.getOrDefault(key,"未提供"), b=after.getOrDefault(key,"未提供");
            if(!Objects.equals(a,b)) changes.add(key+"："+a+" → "+b);
        }
        return changes;
    }
}
