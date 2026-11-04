package io.github.vvb2060.keyattestation.dashboard;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import io.github.vvb2060.keyattestation.attestation.OfficialData;
import org.json.JSONObject;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=DashboardSmokeTest.TestApp.class)
public class OfficialDataTest {
    @Test public void exportedDataReimportsOnlyAsOffline() throws Exception {
        var app=RuntimeEnvironment.getApplication();
        var data=OfficialData.exportBundle();
        OfficialData.importBundle(app,new ByteArrayInputStream(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertFalse(OfficialData.isFresh());assertEquals(0,OfficialData.fetchedAt());
        assertTrue(OfficialData.source().contains("未在线验证"));
    }
    @Test public void importCannotEraseKnownRevocations() throws Exception {
        var app=RuntimeEnvironment.getApplication();
        int count=OfficialData.entryCount();
        var bundle=new JSONObject(OfficialData.exportBundle());
        bundle.put("status",new JSONObject().put("entries",new JSONObject()));
        OfficialData.importBundle(app,new ByteArrayInputStream(bundle.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(count,OfficialData.entryCount());
    }
    @Test public void arbitrarySelfSignedRootCannotBecomeTrusted() throws Exception {
        var bundle=new JSONObject(OfficialData.exportBundle());
        byte[] cert;try(var in=getClass().getResourceAsStream("/chains/new-0.der")){cert=in.readAllBytes();}
        String pem="-----BEGIN CERTIFICATE-----\n"+java.util.Base64.getMimeEncoder().encodeToString(cert)+"\n-----END CERTIFICATE-----\n";
        bundle.getJSONArray("roots").put(pem);
        assertThrows(IOException.class,()->OfficialData.importBundle(RuntimeEnvironment.getApplication(),new ByteArrayInputStream(bundle.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    @Test public void unknownRevocationStatusRejected() throws Exception {
        var bundle=new JSONObject(OfficialData.exportBundle());
        bundle.getJSONObject("status").getJSONObject("entries").put("deadbeef",new JSONObject().put("status","UNREVOKED"));
        assertThrows(IOException.class,()->OfficialData.importBundle(RuntimeEnvironment.getApplication(),new ByteArrayInputStream(bundle.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    @Test public void oversizedImportRejectedBeforeParsing() {
        var bytes=new byte[8*1024*1024+1];
        assertThrows(IOException.class,()->OfficialData.importBundle(RuntimeEnvironment.getApplication(),new ByteArrayInputStream(bytes)));
    }
}
