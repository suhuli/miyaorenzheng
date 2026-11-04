package com.larus.nova.dashboard;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import com.larus.nova.attestation.OfficialData;
import org.json.*;
import java.security.cert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=DashboardSmokeTest.TestApp.class)
public class MirrorTrustTest {
    @Test public void repositoryCanUpdateRootSetWithoutAppPinOrSignature() throws Exception {
        byte[] cert;try(var in=getClass().getResourceAsStream("/chains/new-0.der")){cert=in.readAllBytes();}
        String pem="-----BEGIN CERTIFICATE-----\n"+java.util.Base64.getMimeEncoder().encodeToString(cert)+"\n-----END CERTIFICATE-----\n";
        var app=RuntimeEnvironment.getApplication();
        var accept=OfficialData.class.getDeclaredMethod("accept",android.content.Context.class,String.class,String.class,long.class,String.class);accept.setAccessible(true);
        accept.invoke(null,app,new JSONArray().put(pem).toString(),"{\"entries\":{}}",System.currentTimeMillis(),"仓库镜像");
        var c=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(cert));
        org.junit.Assert.assertTrue(OfficialData.matches(c.getPublicKey().getEncoded()));
        org.junit.Assert.assertEquals(1,OfficialData.rootCount());
    }
}
