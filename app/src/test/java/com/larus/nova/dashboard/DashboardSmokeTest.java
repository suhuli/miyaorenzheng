package com.larus.nova.dashboard;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.larus.nova.R;
import com.larus.nova.attestation.OfficialData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

/** Native UI smoke only. No fake attestation is presented as a real device test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=DashboardSmokeTest.TestApp.class)
public class DashboardSmokeTest {
    public static class TestApp extends Application {
        @Override public void onCreate() { super.onCreate();
            try {var f=OfficialData.class.getDeclaredField("current");f.setAccessible(true);f.set(null,null);} catch(Exception e){throw new RuntimeException(e);}
            OfficialData.initialize(this); }
    }
    public static class SmokeActivity extends DashboardActivity {
        @Override protected void attachModel() { /* no hardware or network in UI smoke */ }
    }
    private ActivityController<SmokeActivity> activity() {
        var c=Robolectric.buildActivity(SmokeActivity.class);
        c.get().setTheme(R.style.DashboardTheme);
        return c.create().start().resume().visible();
    }
    private <T> T find(View v,Class<T> type) {
        if(type.isInstance(v)) return type.cast(v);
        if(v instanceof ViewGroup g) for(int i=0;i<g.getChildCount();i++) {T found=find(g.getChildAt(i),type);if(found!=null)return found;}
        return null;
    }
    private boolean containsText(View v,String needle) {
        if(v instanceof TextView t && t.getText().toString().contains(needle))return true;
        if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)if(containsText(g.getChildAt(i),needle))return true;
        return false;
    }
    @Test public void dashboardLaunchesWithFourSections() {
        try(var c=activity()) {var root=c.get().getWindow().getDecorView();var nav=find(root,BottomNavigationView.class);assertNotNull(nav);assertEquals(4,nav.getMenu().size());assertTrue(containsText(root,"密钥认证"));assertEquals("com.larus.nova",c.get().getPackageName());assertEquals("密钥认证",c.get().getString(R.string.app_name));}
    }
    @Test public void allSectionsNavigateWithoutHardware() {
        try(var c=activity()) {var root=c.get().getWindow().getDecorView();var nav=find(root,BottomNavigationView.class);
            nav.setSelectedItemId(2);assertTrue(containsText(root,"暂无可显示的链"));
            nav.setSelectedItemId(3);assertTrue(containsText(root,"本地记录"));
            nav.setSelectedItemId(4);assertTrue(containsText(root,"信任数据"));assertTrue(containsText(root,"严格有效期模式"));
            nav.setSelectedItemId(1);assertTrue(containsText(root,"检测中"));
        }
    }
    @Test public void resultStatesAreVisibleAndSloganRemoved() throws Exception {
        try(var c=activity()) {
            var method=DashboardActivity.class.getDeclaredMethod("resultCard",String.class,String.class,int.class,boolean.class);method.setAccessible(true);
            var root=c.get().getWindow().getDecorView();
            method.invoke(c.get(),"证书正常","四项检查通过",0,false);
            method.invoke(c.get(),"证书异常","签名失败",2,false);
            method.invoke(c.get(),"待确认","数据过期",1,false);
            assertTrue(containsText(root,"证书正常"));assertTrue(containsText(root,"证书异常"));assertTrue(containsText(root,"待确认"));
            assertFalse(containsText(root,"看清每一层信任"));
        }
    }
    @Test public void darkConfigurationLaunches() {
        RuntimeEnvironment.setQualifiers("night");
        try(var c=activity()) {assertNotNull(find(c.get().getWindow().getDecorView(),BottomNavigationView.class));}
    }

    private View priorityButton(View v) {
        if(v instanceof com.google.android.material.button.MaterialButton b && b.getText().toString().startsWith("数据源优先级："))return v;
        if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=priorityButton(g.getChildAt(i));if(found!=null)return found;}
        return null;
    }
    @Test public void priorityDialogSavesChoiceAcrossActivityRecreation() {
        try(var c=activity()) {
            var root=c.get().getWindow().getDecorView();find(root,BottomNavigationView.class).setSelectedItemId(4);
            View button=priorityButton(root);assertNotNull(button);button.performClick();
            var dialog=(androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertEquals(0,dialog.getListView().getCheckedItemPosition());
            dialog.getListView().performItemClick(null,1,1);
            assertEquals(com.larus.nova.attestation.DataSourcePolicy.MIRROR_FIRST,OfficialData.priority(c.get()));
        }
        try(var c=activity()) {
            var root=c.get().getWindow().getDecorView();find(root,BottomNavigationView.class).setSelectedItemId(4);
            assertTrue(((TextView)priorityButton(root)).getText().toString().contains("jsDelivr → Google 官方"));
        }
    }
    @Test public void cancelPriorityDialogDoesNotChangeDefault() {
        try(var c=activity()) {
            var root=c.get().getWindow().getDecorView();find(root,BottomNavigationView.class).setSelectedItemId(4);priorityButton(root).performClick();
            var dialog=(androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
            assertEquals(com.larus.nova.attestation.DataSourcePolicy.OFFICIAL_FIRST,OfficialData.priority(c.get()));
        }
    }

    @Test public void manualRefreshWhileUpdatingQueuesASecondCheck() throws Exception {
        try(var c=activity()) {
            var busy=DashboardActivity.class.getDeclaredField("updating");busy.setAccessible(true);busy.setBoolean(c.get(),true);
            var refresh=DashboardActivity.class.getDeclaredMethod("refresh",boolean.class);refresh.setAccessible(true);refresh.invoke(c.get(),true);
            var pending=DashboardActivity.class.getDeclaredField("refreshPending");pending.setAccessible(true);assertTrue(pending.getBoolean(c.get()));
        }
    }

    public static class SyntheticData extends com.larus.nova.repository.BaseData {
        SyntheticData() { certs=java.util.List.of(); status=com.larus.nova.attestation.RootPublicKey.Status.NULL; }
    }
    @Test public void overviewKeepsCertificateDeviceAndActionsWithoutConfigCard() throws Exception {
        try(var c=activity()) {
            var current=DashboardActivity.class.getDeclaredField("current");current.setAccessible(true);current.set(c.get(),new SyntheticData());
            var loading=DashboardActivity.class.getDeclaredField("loading");loading.setAccessible(true);loading.setBoolean(c.get(),false);
            var render=DashboardActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(c.get());
            var root=c.get().getWindow().getDecorView();
            assertTrue(containsText(root,"证书"));assertTrue(containsText(root,"设备"));
            assertTrue(containsText(root,"重新检测"));assertTrue(containsText(root,"查看证书"));
            assertFalse(containsText(root,"配置扩展"));assertFalse(containsText(root,"30 天颁发近似数"));
        }
    }
    @Test public void launcherProvidesAdaptiveAndThemedIcons() {
        try(var c=activity()) {
            var drawable=c.get().getDrawable(R.drawable.ic_launcher);
            assertTrue(drawable instanceof android.graphics.drawable.AdaptiveIconDrawable);
            var icon=(android.graphics.drawable.AdaptiveIconDrawable)drawable;
            assertNotNull(icon.getBackground());assertNotNull(icon.getForeground());assertNotNull(icon.getMonochrome());
            assertEquals(R.drawable.ic_launcher,c.get().getApplicationInfo().icon);
        }
    }
}
