package com.larus.nova.attestation;
import com.larus.nova.dashboard.DashboardSmokeTest;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=DashboardSmokeTest.TestApp.class)
public class CacheMigrationTest {
    private File file(String name){return new File(RuntimeEnvironment.getApplication().getFilesDir(),name);}
    private void write(String name,String body)throws Exception {try(var out=new FileOutputStream(file(name))){out.write(body.getBytes(StandardCharsets.UTF_8));}}
    private void reload()throws Exception {var f=OfficialData.class.getDeclaredField("current");f.setAccessible(true);f.set(null,null);OfficialData.initialize(RuntimeEnvironment.getApplication());}
    private String cache(long time)throws Exception {
        var bundle=new JSONObject(OfficialData.exportBundle());return new JSONObject().put("roots",bundle.getJSONArray("roots").toString())
            .put("status","{\"entries\":{\"ab\":{\"status\":\"REVOKED\"}}}").put("time",time).put("source","Google 官方直连").put("change","preserved").toString();
    }
    @Test public void migratesValidCacheWithoutResettingTimeOrSource()throws Exception {
        long time=System.currentTimeMillis()-10000;write("official-data.json",cache(time));reload();
        assertEquals(time,OfficialData.fetchedAt());assertEquals("Google 官方直连",OfficialData.source());
        assertTrue(OfficialData.entries().has("ab"));assertTrue(file("trust-store.json").exists());assertFalse(file("official-data.json").exists());assertEquals("preserved",OfficialData.changes());
    }
    @Test public void atomicBackupOfLegacyCacheIsRecovered()throws Exception {
        long time=System.currentTimeMillis()-10000;write("official-data.json.bak",cache(time));reload();
        assertEquals(time,OfficialData.fetchedAt());assertTrue(file("trust-store.json").exists());assertFalse(file("official-data.json.bak").exists());
    }
    @Test public void invalidLegacyCacheDoesNotBecomeTrusted()throws Exception {
        write("official-data.json","{}");reload();assertEquals(0,OfficialData.fetchedAt());assertFalse(file("trust-store.json").exists());assertTrue(file("official-data.json").exists());
    }
    @Test public void validNewCacheWinsOverLegacyCopy()throws Exception {
        long time=System.currentTimeMillis()-10000;write("trust-store.json",cache(time));write("official-data.json","{}");reload();
        assertEquals(time,OfficialData.fetchedAt());assertTrue(OfficialData.entries().has("ab"));
    }
}
