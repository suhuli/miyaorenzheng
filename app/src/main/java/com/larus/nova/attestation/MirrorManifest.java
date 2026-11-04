package com.larus.nova.attestation;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
/** Repository is the trust source by design. Hash is integrity, NOT authentication. */
public record MirrorManifest(long fetchedAt,String path,String sha256,int bytes) {
    public static MirrorManifest parse(String text,long now) throws Exception {
        if(text.length()>16384)throw new IllegalArgumentException("Manifest too large");
        JSONObject json=new JSONObject(text);
        long time=json.getLong("fetched_at");String path=json.getString("bundle_path"),hash=json.getString("sha256");
        int size=json.getInt("bytes");
        if(json.getInt("schema")!=1 || time<=0 || time>now+300000L || !path.matches("snapshots/[0-9]+\\.json")
            || !hash.matches("[0-9a-f]{64}") || size<1 || size>8*1024*1024)throw new IllegalArgumentException("Invalid mirror manifest");
        return new MirrorManifest(time,path,hash,size);
    }
    public void check(String body) throws Exception {
        byte[] data=body.getBytes(StandardCharsets.UTF_8);
        if(data.length!=bytes)throw new IllegalArgumentException("Mirror size mismatch");
        var text=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))text.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!text.toString().equals(sha256))throw new IllegalArgumentException("Mirror hash mismatch");
        if(new JSONObject(body).getLong("fetched_at")!=fetchedAt)throw new IllegalArgumentException("Mirror timestamp mismatch");
    }
}
