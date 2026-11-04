package com.larus.nova.attestation;

import com.larus.nova.dashboard.DashboardSmokeTest;
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
        accept(syntheticRoot(),status("bb"),System.currentTimeMillis()+60_000,"仓库镜像");
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
        assertEquals(time,OfficialData.fetchedAt());assertTrue(OfficialData.source().equals("仓库镜像"));
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
        assertTrue(OfficialData.source().equals("仓库镜像"));assertFalse(OfficialData.entries().has("bb"));
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

    private void mirrorFirst() {
        RuntimeEnvironment.getApplication().getSharedPreferences("dashboard",0).edit()
            .putString(DataSourcePolicy.KEY,DataSourcePolicy.MIRROR_FIRST).commit();
    }
    @Test public void mirrorPreferenceUsesMirrorAndDoesNotContactGoogleOnSuccess() throws Exception {
        mirrorFirst();assertTrue(refresh(mirror(roots(),status("aa"),System.currentTimeMillis()-1000)));
        assertEquals(OfficialData.MIRROR_MANIFEST,calls.get(0));assertEquals(2,calls.size());
        assertFalse(calls.contains(OfficialData.ROOT_URL));assertTrue(OfficialData.source().equals("仓库镜像"));
        assertEquals("",OfficialData.error());
    }
    @Test public void mirrorFailureFallsBackToOfficialAndReplacesAllData() throws Exception {
        String r=roots();accept(syntheticRoot(),status("bb"),System.currentTimeMillis()+1000,"仓库镜像");
        mirrorFirst();assertTrue(refresh(url->{if(url.equals(OfficialData.MIRROR_MANIFEST))throw new IOException("offline");
            return url.equals(OfficialData.ROOT_URL)?r:status("aa");}));
        assertEquals(List.of(OfficialData.MIRROR_MANIFEST,OfficialData.ROOT_URL,OfficialData.STATUS_URL),calls);
        assertEquals("Google 官方直连",OfficialData.source());assertFalse(OfficialData.entries().has("bb"));
    }
    @Test public void mirrorPreferenceAllFailuresKeepCacheAndTimestamp() throws Exception {
        long time=System.currentTimeMillis()-1000;accept(roots(),status("aa"),time,"Google 官方直连");String before=OfficialData.exportBundle();
        mirrorFirst();assertFalse(refresh(url->{throw new IOException("offline");}));
        assertEquals(before,OfficialData.exportBundle());assertEquals(time,OfficialData.fetchedAt());
        assertEquals(List.of(OfficialData.MIRROR_MANIFEST,OfficialData.ROOT_URL),calls);
    }
    @Test public void invalidPreferenceDefaultsToOfficialFirst() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("dashboard",0).edit().putString(DataSourcePolicy.KEY,"invalid").commit();
        String r=roots();assertTrue(refresh(url->url.equals(OfficialData.ROOT_URL)?r:status("aa")));
        assertEquals(List.of(OfficialData.ROOT_URL,OfficialData.STATUS_URL),calls);
    }
    @Test public void switchBackToOfficialOverridesPreviouslyAcceptedMirror() throws Exception {
        String r=roots();mirrorFirst();assertTrue(refresh(mirror(syntheticRoot(),status("bb"),System.currentTimeMillis()-1000)));
        RuntimeEnvironment.getApplication().getSharedPreferences("dashboard",0).edit().putString(DataSourcePolicy.KEY,DataSourcePolicy.OFFICIAL_FIRST).commit();
        calls.clear();assertTrue(refresh(url->url.equals(OfficialData.ROOT_URL)?r:status("aa")));
        assertEquals(OfficialData.ROOT_URL,calls.get(0));assertFalse(OfficialData.entries().has("bb"));
    }
    @Test public void unchangedPreferredMirrorSkipsPayloadAndGoogle() throws Exception {
        mirrorFirst();var d=mirror(roots(),status("aa"),System.currentTimeMillis()-1000);
        assertTrue(refresh(d));calls.clear();assertTrue(refresh(d));
        assertEquals(List.of(OfficialData.MIRROR_MANIFEST),calls);
    }

    @Test public void explicitMirrorPreferenceCanReplaceMoreRecentlyFetchedOfficialData() throws Exception {
        String r=roots();long time=System.currentTimeMillis()-3600000;
        accept(r,status("bb"),System.currentTimeMillis(),"Google 官方直连");mirrorFirst();
        assertTrue(refresh(mirror(r,status("aa"),time)));
        assertTrue(OfficialData.source().equals("仓库镜像"));assertEquals(time,OfficialData.fetchedAt());
        assertTrue(OfficialData.entries().has("aa"));assertFalse(OfficialData.entries().has("bb"));
        assertFalse(calls.contains(OfficialData.ROOT_URL));
    }
    @Test public void preferredMirrorStillCannotRollBackPreviouslyAcceptedMirrorVersion() throws Exception {
        String r=roots();long time=System.currentTimeMillis()-1000;
        accept(r,status("bb"),time,"仓库镜像");mirrorFirst();
        assertFalse(refresh(mirror(r,status("aa"),time-1000)));
        assertEquals(time,OfficialData.fetchedAt());assertTrue(OfficialData.entries().has("bb"));
    }
    @Test public void explicitMirrorPreferenceStillRejectsExpiredMirrorAndUsesOfficial() throws Exception {
        String r=roots();var d=mirror(r,status("bb"),System.currentTimeMillis()-25*3600000L);mirrorFirst();
        assertTrue(refresh(url->{if(url.equals(OfficialData.ROOT_URL))return r;
            if(url.equals(OfficialData.STATUS_URL))return status("aa");return d.get(url);}));
        assertEquals("Google 官方直连",OfficialData.source());assertFalse(OfficialData.entries().has("bb"));
    }
    @Test public void switchingBackToOfficialDoesNotReuseMirrorPreference() throws Exception {
        String r=roots();long mirrorTime=System.currentTimeMillis()-3600000;
        accept(r,status("bb"),System.currentTimeMillis(),"Google 官方直连");mirrorFirst();
        assertTrue(refresh(mirror(r,status("aa"),mirrorTime)));
        RuntimeEnvironment.getApplication().getSharedPreferences("dashboard",0).edit().putString(DataSourcePolicy.KEY,DataSourcePolicy.OFFICIAL_FIRST).commit();
        calls.clear();assertTrue(refresh(url->url.equals(OfficialData.ROOT_URL)?r:status("cc")));
        assertEquals(OfficialData.ROOT_URL,calls.get(0));assertEquals("Google 官方直连",OfficialData.source());assertFalse(OfficialData.entries().has("aa"));
    }

    @Test public void mirrorRollbackGuardSurvivesOfficialSwitchAndCacheReload() throws Exception {
        String r=roots();long time=System.currentTimeMillis()-3600000;mirrorFirst();
        assertTrue(refresh(mirror(r,status("aa"),time)));
        RuntimeEnvironment.getApplication().getSharedPreferences("dashboard",0).edit().putString(DataSourcePolicy.KEY,DataSourcePolicy.OFFICIAL_FIRST).commit();
        assertTrue(refresh(url->url.equals(OfficialData.ROOT_URL)?r:status("bb")));
        var f=OfficialData.class.getDeclaredField("current");f.setAccessible(true);f.set(null,null);
        OfficialData.initialize(RuntimeEnvironment.getApplication());mirrorFirst();calls.clear();
        assertFalse(refresh(mirror(r,status("cc"),time-1000)));
        assertEquals("Google 官方直连",OfficialData.source());assertTrue(OfficialData.entries().has("bb"));
    }
    @Test public void repositoryEndpointsAndRequestsAreDirectRawOnly() throws Exception {
        assertEquals("https://raw.githubusercontent.com/suhuli/miyaorenzheng/data/",OfficialData.MIRROR_BASE);
        assertEquals(OfficialData.MIRROR_BASE+"manifest.json",OfficialData.MIRROR_MANIFEST);
        mirrorFirst();assertTrue(refresh(mirror(roots(),status("aa"),System.currentTimeMillis()-1000)));
        assertEquals(2,calls.size());
        for(String url:calls)assertTrue(url.startsWith(OfficialData.MIRROR_BASE));
        assertEquals("仓库镜像",OfficialData.source());
    }
    @Test public void legacyProviderCacheRefetchesDirectAtSameVersion() throws Exception {
        long time=System.currentTimeMillis()-1000;
        accept(roots(),status("aa"),time,"仓库镜像 · jsDelivr");
        var f=OfficialData.class.getDeclaredField("current");f.setAccessible(true);f.set(null,null);
        OfficialData.initialize(RuntimeEnvironment.getApplication());
        assertEquals("仓库镜像（旧版缓存）",OfficialData.source());assertEquals(time,OfficialData.fetchedAt());
        mirrorFirst();assertTrue(refresh(mirror(roots(),status("aa"),time)));
        assertEquals(2,calls.size());assertEquals("仓库镜像",OfficialData.source());assertEquals(time,OfficialData.fetchedAt());
    }
    @Test public void legacyProviderWatermarkStillRejectsOlderDirectMirror() throws Exception {
        long time=System.currentTimeMillis()-1000;
        accept(roots(),status("aa"),time,"仓库镜像 · jsDelivr");
        var f=OfficialData.class.getDeclaredField("current");f.setAccessible(true);f.set(null,null);
        OfficialData.initialize(RuntimeEnvironment.getApplication());mirrorFirst();
        assertFalse(refresh(mirror(roots(),status("bb"),time-1000)));
        assertEquals(time,OfficialData.fetchedAt());assertTrue(OfficialData.entries().has("aa"));
    }
}
