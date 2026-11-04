package com.larus.nova.attestation;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
public class MirrorManifestTest {
    String body="{\"fetched_at\":1000}";
    JSONObject manifest() throws Exception {
        var hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)))hash.append(String.format("%02x",b&255));
        return new JSONObject().put("schema",1).put("fetched_at",1000).put("bundle_path","snapshots/1000.json").put("sha256",hash.toString()).put("bytes",body.length());
    }
    @Test public void validManifestAndBody() throws Exception {MirrorManifest.parse(manifest().toString(),1000).check(body);}
    @Test public void externalUrlRejected() throws Exception {var m=manifest().put("bundle_path","https://evil.example/x").toString();assertThrows(Exception.class,()->MirrorManifest.parse(m,1000));}
    @Test public void traversalRejected() throws Exception {var m=manifest().put("bundle_path","snapshots/../x.json").toString();assertThrows(Exception.class,()->MirrorManifest.parse(m,1000));}
    @Test public void futureTimestampRejected() throws Exception {var m=manifest().put("fetched_at",999999999L).toString();assertThrows(Exception.class,()->MirrorManifest.parse(m,1000));}
    @Test public void hugeBundleRejected() throws Exception {var m=manifest().put("bytes",8*1024*1024+1).toString();assertThrows(Exception.class,()->MirrorManifest.parse(m,1000));}
    @Test public void tamperedBodyRejected() throws Exception {var m=MirrorManifest.parse(manifest().toString(),1000);assertThrows(Exception.class,()->m.check(body.replace("1000","2000")));}
    @Test public void oldMirrorTimeDoesNotBecomeNow() throws Exception {var m=MirrorManifest.parse(manifest().toString(),100000000L);assertEquals(1000,m.fetchedAt());assertFalse(DiagnosticPolicy.fresh(100000000L,m.fetchedAt()));}
    @Test public void unsupportedSchemaRejected() throws Exception {var m=manifest().put("schema",2).toString();assertThrows(Exception.class,()->MirrorManifest.parse(m,1000));}
}
