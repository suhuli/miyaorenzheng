package com.larus.nova.attestation;
import com.larus.nova.dashboard.DashboardSmokeTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=DashboardSmokeTest.TestApp.class)
public class UpdateOptimizationTest {
    private android.content.Context app(){return RuntimeEnvironment.getApplication();}
    private OfficialData.Downloader official(AtomicInteger count) throws Exception {
        var b=new JSONObject(OfficialData.exportBundle());String r=b.getJSONArray("roots").toString(),s=b.getJSONObject("status").toString();
        return url->{count.incrementAndGet();if(url.equals(OfficialData.ROOT_URL))return r;if(url.equals(OfficialData.STATUS_URL))return s;throw new IOException("offline mirror");};
    }
    @Test public void automaticSuccessIsThrottledWithoutChangingDataAge() throws Exception {
        var n=new AtomicInteger();var d=official(n);assertTrue(OfficialData.refresh(app(),false,d));long age=OfficialData.fetchedAt();
        assertTrue(OfficialData.refresh(app(),false,d));assertEquals(2,n.get());assertEquals(age,OfficialData.fetchedAt());assertTrue(OfficialData.updateStatus().contains("未联网"));
    }
    @Test public void manualRefreshBypassesSuccessCooldown() throws Exception {
        var n=new AtomicInteger();var d=official(n);assertTrue(OfficialData.refresh(app(),false,d));assertTrue(OfficialData.refresh(app(),true,d));assertEquals(4,n.get());
    }
    @Test public void automaticFailuresBackOffButManualStillRetries() {
        var n=new AtomicInteger();OfficialData.Downloader d=url->{n.incrementAndGet();throw new IOException("offline");};
        assertFalse(OfficialData.refresh(app(),false,d));String error=OfficialData.error();assertFalse(OfficialData.refresh(app(),false,d));assertEquals(2,n.get());assertEquals(error,OfficialData.error());
        assertFalse(OfficialData.refresh(app(),true,d));assertEquals(4,n.get());assertEquals(0,OfficialData.fetchedAt());
    }
    @Test public void changedPreferenceBypassesAutomaticCooldown() throws Exception {
        var n=new AtomicInteger();var d=official(n);assertTrue(OfficialData.refresh(app(),false,d));
        app().getSharedPreferences("dashboard",0).edit().putString(DataSourcePolicy.KEY,DataSourcePolicy.MIRROR_FIRST).commit();
        assertTrue(OfficialData.refresh(app(),false,d));assertEquals(5,n.get());assertTrue(OfficialData.error().contains("已回退 Google"));
    }
    @Test public void expiredCooldownAllowsAutomaticRetry() throws Exception {
        var n=new AtomicInteger();var d=official(n);assertTrue(OfficialData.refresh(app(),false,d));
        var f=OfficialData.class.getDeclaredField("lastCheckNanos");f.setAccessible(true);f.setLong(null,System.nanoTime()-TimeUnit.MINUTES.toNanos(6));
        assertTrue(OfficialData.refresh(app(),false,d));assertEquals(4,n.get());
    }
    @Test public void networkWaitDoesNotBlockDataExport() throws Exception {
        var ctx=app();var n=new AtomicInteger();var d=official(n);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var update=pool.submit(()->OfficialData.refresh(ctx,true,url->{if(url.equals(OfficialData.ROOT_URL)){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("test timeout");}catch(InterruptedException e){throw new IOException(e);}}return d.get(url);}));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertNotNull(pool.submit(OfficialData::exportBundle).get(1,TimeUnit.SECONDS));
            assertTrue(OfficialData.updateStatus().contains("官方根证书"));release.countDown();assertTrue(update.get(5,TimeUnit.SECONDS));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test public void slowSourcesReturnWithinBudgetAndPreserveCache() throws Exception {
        long age=OfficialData.fetchedAt();String before=OfficialData.exportBundle();long start=System.nanoTime();
        assertFalse(OfficialData.refresh(app(),true,url->{try{Thread.sleep(2000);}catch(InterruptedException e){throw new IOException(e);}throw new IOException("offline");},40));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1500);
        assertEquals(age,OfficialData.fetchedAt());assertEquals(before,OfficialData.exportBundle());assertTrue(OfficialData.error().contains("超时"));
    }
    @Test public void lateNetworkResultCannotPublishAfterTimeout() throws Exception {
        var n=new AtomicInteger();var d=official(n);String before=OfficialData.exportBundle();var release=new CountDownLatch(1);var done=new CountDownLatch(1);
        try {
            assertFalse(OfficialData.refresh(app(),true,url->{if(!url.equals(OfficialData.ROOT_URL))throw new IOException("offline");
                try {while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){}}return d.get(url);}finally{done.countDown();}},40));
            release.countDown();assertTrue(done.await(2,TimeUnit.SECONDS));assertEquals(before,OfficialData.exportBundle());assertEquals(0,OfficialData.fetchedAt());
        } finally {release.countDown();}
    }
    @Test public void describesPhaseTimingsAfterCompletion() throws Exception {
        var n=new AtomicInteger();assertTrue(OfficialData.refresh(app(),true,official(n)));
        String info=OfficialData.describe(app());assertTrue(info.contains("阶段耗时"));assertTrue(info.contains("获取官方根证书"));assertTrue(info.contains("总用时"));
    }
    @Test public void interruptedDownloadStopsReading() throws Exception {
        var read=OfficialData.class.getDeclaredMethod("read",java.io.InputStream.class);read.setAccessible(true);
        var in=new java.io.ByteArrayInputStream(new byte[20000]) {
            @Override public synchronized int read(byte[] b,int off,int len) {
                int n=super.read(b,off,len);Thread.currentThread().interrupt();return n;
            }
        };
        try {
            read.invoke(null,in);fail("cancelled stream must stop");
        } catch(java.lang.reflect.InvocationTargetException e) {assertTrue(e.getCause() instanceof IOException);}
        finally {Thread.interrupted();}
    }
    @Test public void overlappingSamePriorityRequestsShareOneResult() throws Exception {
        var ctx=app();var n=new AtomicInteger();var d=official(n);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();var result=new java.util.concurrent.atomic.AtomicBoolean();
        Thread second=new Thread(()->result.set(OfficialData.refresh(ctx,true,d)));
        try {
            var first=pool.submit(()->OfficialData.refresh(ctx,true,url->{if(url.equals(OfficialData.ROOT_URL)){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}}return d.get(url);}));
            assertTrue(entered.await(5,TimeUnit.SECONDS));second.start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(second.getState()!=Thread.State.BLOCKED && System.nanoTime()<deadline)Thread.sleep(2);
            assertEquals(Thread.State.BLOCKED,second.getState());
            release.countDown();assertTrue(first.get(5,TimeUnit.SECONDS));second.join(5000);
            assertFalse(second.isAlive());assertTrue(result.get());assertEquals(2,n.get());
        } finally {release.countDown();pool.shutdownNow();second.join(5000);}
    }
}
