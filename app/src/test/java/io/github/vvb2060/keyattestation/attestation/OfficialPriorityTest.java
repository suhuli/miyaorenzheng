package io.github.vvb2060.keyattestation.attestation;

import io.github.vvb2060.keyattestation.dashboard.DashboardSmokeTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=DashboardSmokeTest.TestApp.class)
public class OfficialPriorityTest {
    private final List<String> calls = new ArrayList<>();
    private JSONObject bundled() throws Exception { return new JSONObject(OfficialData.exportBundle()); }
    private String roots() throws Exception { return bundled().getJSONArray("roots").toString(); }
    private String status(String serial) { return "{\"entries\":{\""+serial+"\":{\"status\":\"REVOKED\"}}}"; }
    private boolean refresh(OfficialData.Downloader d) {
        return OfficialData.refresh(RuntimeEnvironment.getApplication(),true,url->{calls.add(url);return d.get(url);});
    }
    private void accept(String roots,String status,long time,String source) throws Exception {
        var m=OfficialData.class.getDeclaredMethod("accept",android.content.Context.class,String.class,String.class,long.class,String.class);
        m.setAccessible(true);m.invoke(null,RuntimeEnvironment.getApplication(),roots,status,time,source);
    }
    private String syntheticRoot() throws Exception {
        try(var in=getClass().getResourceAsStream("/chains/new-0.der")) {
            return new JSONArray().put("-----BEGIN CERTIFICATE-----\n"+Base64.getMimeEncoder().encodeToString(in.readAllBytes())+"\n-----END CERTIFICATE-----\n").toString();
        }
    }
    private OfficialData.Downloader mirror(String roots,String status,long time) throws Exception {
        String body=new JSONObject().put("schema",1).put("fetched_at",time).put("roots",new JSONArray(roots))
            .put("status",new JSONObject(status)).toString();
        String path="snapshots/"+time+".json";
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",b&255));
        String manifest=new JSONObject().put("schema",1).put("fetched_at",time).put("bundle_path",path)
            .put("sha256",hash.toString()).put("bytes",bytes.length).toString();
        return url->{
            if(url.equals(OfficialData.MIRROR_MANIFEST))return manifest;
            if(url.equals(OfficialData.MIRROR_BASE+path))return body;
            throw new IOException("Official unavailable");
        };
    }
    @Test public void officialIsFirstAndSuccessNeverRequestsMirror() throws Exception {
        String r=roots();assertTrue(refresh(url->{if(url.equals(OfficialData.ROOT_URL))return r;
            if(url.equals(OfficialData.STATUS_URL))return status("aa");throw new AssertionError("Must not consult mirror");}));
        assertEquals(List.of(OfficialData.ROOT_URL,OfficialData.STATUS_URL),calls);
        assertEquals("Google 官方直连",OfficialData.source());assertEquals("",OfficialData.error());assertTrue(OfficialData.isFresh());
    }
    @Test public void officialReplacesMirrorEvenWhenMirrorTimestampIsNewer() throws Exception {
        String officialRoots=roots();
        accept(syntheticRoot(),status("bb"),System.currentTimeMillis()+60_000,"仓库镜像 · jsDelivr");
        assertTrue(refresh(url->url.equals(OfficialData.ROOT_URL)?officialRoots:status("aa")));
        assertEquals(new JSONArray(officialRoots).length(),OfficialData.rootCount());
        assertEquals("Google 官方直连",OfficialData.source());
        assertTrue(OfficialData.entries().has("aa"));assertFalse(OfficialData.entries().has("bb"));
        assertEquals(1,OfficialData.entryCount());
    }
    @Test public void officialStatusFailureDoesNotApplyPartialRoots() throws Exception {
        String before=OfficialData.exportBundle();long time=OfficialData.fetchedAt();String different=syntheticRoot();
        assertFalse(refresh(url->{if(url.equals(OfficialData.ROOT_URL))return different;throw new IOException("offline");}));
        assertEquals(before,OfficialData.exportBundle());assertEquals(time,OfficialData.fetchedAt());
        assertEquals(List.of(OfficialData.ROOT_URL,OfficialData.STATUS_URL,OfficialData.MIRROR_MANIFEST),calls);
    }
    @Test public void officialFailureUsesMirrorAndPreservesOfficialFetchTime() throws Exception {
        long time=System.currentTimeMillis()-5000;var d=mirror(roots(),status("ab"),time);
        assertTrue(refresh(d));assertEquals(OfficialData.ROOT_URL,calls.get(0));assertEquals(OfficialData.MIRROR_MANIFEST,calls.get(1));
        assertEquals(time,OfficialData.fetchedAt());assertTrue(OfficialData.source().contains("jsDelivr"));
        assertTrue(OfficialData.error().contains("官方不可用"));
    }
    @Test public void mirrorRemainsFullyTrustedForRootUpdates() throws Exception {
        assertTrue(refresh(mirror(syntheticRoot(),status("aa"),System.currentTimeMillis()-1000)));
        assertEquals(1,OfficialData.rootCount());
    }
    @Test public void invalidOfficialDatasetFallsBackToMirror() throws Exception {
        var fallback=mirror(roots(),status("aa"),System.currentTimeMillis()-1000);
        assertTrue(refresh(url->{if(url.equals(OfficialData.ROOT_URL))return "[]";
            if(url.equals(OfficialData.STATUS_URL))return status("bb");return fallback.get(url);}));
        assertTrue(OfficialData.source().contains("jsDelivr"));assertFalse(OfficialData.entries().has("bb"));
    }
    @Test public void allNetworkFailurePreservesCacheAndAge() throws Exception {
        long time=System.currentTimeMillis()-10_000;accept(roots(),status("ac"),time,"Google 官方直连");
        String before=OfficialData.exportBundle();assertFalse(refresh(url->{throw new IOException("offline");}));
        assertEquals(before,OfficialData.exportBundle());assertEquals(time,OfficialData.fetchedAt());
        assertTrue(OfficialData.error().contains("官方："));assertTrue(OfficialData.error().contains("镜像："));
    }
    @Test public void staleMirrorDoesNotOverwriteLocalCache() throws Exception {
        String before=OfficialData.exportBundle();assertFalse(refresh(mirror(syntheticRoot(),status("aa"),System.currentTimeMillis()-25*60*60*1000L)));
        assertEquals(before,OfficialData.exportBundle());assertEquals(0,OfficialData.fetchedAt());
    }
    @Test public void olderMirrorDoesNotOverwriteNewerOfficialCache() throws Exception {
        long time=System.currentTimeMillis()-1000;String r=roots();accept(r,status("bb"),time,"Google 官方直连");
        assertFalse(refresh(mirror(syntheticRoot(),status("aa"),time-1000)));
        assertEquals(time,OfficialData.fetchedAt());assertTrue(OfficialData.entries().has("bb"));
    }
    @Test public void unchangedMirrorSkipsPayloadButStillAttemptsOfficialFirst() throws Exception {
        long time=System.currentTimeMillis()-1000;var d=mirror(roots(),status("aa"),time);
        assertTrue(refresh(d));calls.clear();assertTrue(refresh(d));
        assertEquals(List.of(OfficialData.ROOT_URL,OfficialData.MIRROR_MANIFEST),calls);
    }
}
