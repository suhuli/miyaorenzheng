package com.larus.nova.attestation;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import com.larus.nova.R;

/** Official HTTPS first, trusted repository mirror second, local cache last. No device uploads. */
public final class OfficialData {
    public static final String MIRROR_BASE = "https://raw.githubusercontent.com/suhuli/miyaorenzheng/data/";
    public static final String MIRROR_MANIFEST = MIRROR_BASE + "manifest.json";
    public static final String ROOT_URL = "https://android.googleapis.com/attestation/root";
    public static final String STATUS_URL = "https://android.googleapis.com/attestation/status";
    private static final long MAX_AGE = 24L * 60 * 60 * 1000;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static volatile Snapshot current;
    private static volatile String lastError = "";
    private static String rawRoots, rawStatus;
    private static long latestMirrorFetchedAt;
    private static String lastChange = "尚无在线更新记录";
    public static long fetchedAt() { return current.fetchedAt(); }
    public static String source() { return current.source(); }
    public static String changes() { return lastChange; }
    public static String error() { return lastError; }
    public static int rootCount() { return current.keys().size(); }
    public static int entryCount() { return current.entries().length(); }
    public record Snapshot(Set<String> keys, JSONObject entries, long fetchedAt, String source) {}
    private OfficialData() {}

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) {
            if (out.size() + n > MAX_BYTES) throw new IOException("Official response too large");
            out.write(b, 0, n);
        }
        return out.toString("UTF-8");
    }
    private static Snapshot parse(String roots, String status, long time, String source) throws Exception {
        JSONArray array = new JSONArray(roots);
        if (array.length() == 0 || array.length() > 100) throw new IOException("Invalid root set");
        Set<String> keys = new HashSet<>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (int i = 0; i < array.length(); i++) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(array.getString(i).getBytes(StandardCharsets.UTF_8)));
            if (cert.getBasicConstraints() < 0) throw new CertificateException("Root is not a CA");
            cert.verify(cert.getPublicKey());
            keys.add(android.util.Base64.encodeToString(cert.getPublicKey().getEncoded(), 2));
        }
        JSONObject entries = new JSONObject(status).getJSONObject("entries");
        for (Iterator<String> it = entries.keys(); it.hasNext();) {
            String key = it.next();
            if (!key.matches("[0-9a-fA-F]+")) throw new IOException("Invalid revocation serial");
            String state = entries.getJSONObject(key).getString("status");
            if (!state.equals("REVOKED") && !state.equals("SUSPENDED")) throw new IOException("Invalid revocation state");
        }
        return new Snapshot(Collections.unmodifiableSet(keys), entries, time, source);
    }
    public static synchronized void initialize(Context context) {
        if (current != null) return;
        latestMirrorFetchedAt = 0;
        try (InputStream r = context.getResources().openRawResource(R.raw.roots);
             InputStream s = context.getResources().openRawResource(R.raw.status)) {
            rawRoots = read(r); rawStatus = read(s);
            current = parse(rawRoots, rawStatus, 0, "内置官方快照（非实时）");
        } catch (Exception e) { throw new IllegalStateException("Invalid bundled trust data", e); }
        // Migrate validated 2.3 cache without resetting its provenance or age.
        for (String name : new String[]{"trust-store.json", "official-data.json"}) {
            android.util.AtomicFile file = new android.util.AtomicFile(new File(context.getFilesDir(), name));
            try (InputStream in = file.openRead()) {
                JSONObject cache = new JSONObject(read(in));
                Snapshot candidate = parse(cache.getString("roots"), cache.getString("status"),
                    cache.getLong("time"), migratedSource(cache.optString("source", "官方 HTTPS 缓存")));
                current = candidate; rawRoots = cache.getString("roots"); rawStatus = cache.getString("status");
                latestMirrorFetchedAt = Math.max(0, cache.optLong("mirror_version",
                    isMirrorSource(candidate.source()) ? candidate.fetchedAt() : 0));
                lastChange = cache.optString("change", "缓存恢复，暂无差异记录");
                if (name.equals("official-data.json")) {
                    persist(context, rawRoots, rawStatus, candidate.fetchedAt(), candidate.source(), lastChange);
                    file.delete();
                }
                break;
            } catch (FileNotFoundException ignored) {
            } catch (Exception e) { lastError = "Cache rejected: " + e.getClass().getSimpleName(); }
        }
    }
    // Legacy provider labels are read only for cache migration, never used as endpoints.
    private static boolean isMirrorSource(String source) {
        return source.startsWith("仓库镜像") || source.contains("jsDelivr");
    }
    private static String migratedSource(String source) {
        return source.contains("jsDelivr") ? "仓库镜像（旧版缓存）" : source;
    }
    private static String download(String url) throws IOException {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(10000);
        c.setInstanceFollowRedirects(false);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) { return read(in); }
        } finally { c.disconnect(); }
    }
    @FunctionalInterface
    interface Downloader { String get(String url) throws IOException; }

    public static synchronized boolean refresh(Context context, boolean force) {
        return refresh(context, force, OfficialData::download);
    }
    public static String priority(Context context) {
        return DataSourcePolicy.normalize(context.getSharedPreferences("dashboard", Context.MODE_PRIVATE)
            .getString(DataSourcePolicy.KEY, DataSourcePolicy.OFFICIAL_FIRST));
    }
    public static String priorityLabel(Context context) { return DataSourcePolicy.label(priority(context)); }
    // Package-private transport seam permits deterministic fallback/authority regression tests.
    static synchronized boolean refresh(Context context, boolean force, Downloader downloader) {
        initialize(context);
        boolean mirrorFirst = priority(context).equals(DataSourcePolicy.MIRROR_FIRST);
        String firstError;
        try {
            if (mirrorFirst) fetchMirror(context, downloader, true); else fetchOfficial(context, downloader);
            lastError = ""; return true;
        } catch (Exception e) { firstError = e.getClass().getSimpleName() + ": " + e.getMessage(); }
        try {
            if (mirrorFirst) fetchOfficial(context, downloader); else fetchMirror(context, downloader, false);
            lastError = (mirrorFirst ? "镜像不可用，已回退 Google 官方：" : "官方不可用，已使用仓库镜像：") + firstError;
            return true;
        } catch (Exception e) {
            String secondError = e.getClass().getSimpleName() + ": " + e.getMessage();
            lastError = "官方：" + (mirrorFirst ? secondError : firstError)
                + "\n镜像：" + (mirrorFirst ? firstError : secondError);
            return false; // Keep the last validated local snapshot and its original age.
        }
    }
    private static void fetchOfficial(Context context, Downloader downloader) throws Exception {
        String roots = downloader.get(ROOT_URL), status = downloader.get(STATUS_URL);
        // A complete valid official response is authoritative, regardless of mirror timestamps.
        accept(context, roots, status, System.currentTimeMillis(), "Google 官方直连");
    }
    private static void fetchMirror(Context context, Downloader downloader, boolean explicitlyPreferred) throws Exception {
        long now = System.currentTimeMillis();
        MirrorManifest manifest = MirrorManifest.parse(downloader.get(MIRROR_MANIFEST), now);
        if (now - manifest.fetchedAt() > MAX_AGE || manifest.fetchedAt() > now)
            throw new IOException("Mirror snapshot is stale or from the future");
        // Fetch timestamps from different sources are NOT comparable version numbers.
        // An explicit mirror preference may replace newer direct-Google data with a valid
        // <=24h mirror snapshot. Preserve the original age. Never roll back mirror-to-mirror.
        if (manifest.fetchedAt() < latestMirrorFetchedAt)
            throw new IOException("仓库返回已接受镜像之前的旧版本");
        boolean switchingFromOfficial = explicitlyPreferred && current.source().equals("Google 官方直连");
        if (manifest.fetchedAt() < current.fetchedAt() && !switchingFromOfficial)
            throw new IOException("镜像早于当前缓存，保留较新数据");
        if (manifest.fetchedAt() != current.fetchedAt() || !current.source().equals("仓库镜像")) {
            String body = downloader.get(MIRROR_BASE + manifest.path()); manifest.check(body);
            JSONObject bundle = new JSONObject(body);
            accept(context, bundle.getJSONArray("roots").toString(), bundle.getJSONObject("status").toString(),
                manifest.fetchedAt(), "仓库镜像");
        }
    }
    private static void accept(Context context,String roots,String status,long time,String source) throws Exception {
        // Both official and explicitly trusted repository updates replace complete datasets.
        Snapshot next=parse(roots,status,time,source);
        Set<String> added=new HashSet<>(next.keys());added.removeAll(current.keys());
        Set<String> removed=new HashSet<>(current.keys());removed.removeAll(next.keys());
        String change="根公钥：新增 "+added.size()+"，移除 "+removed.size()
            +"；撤销条目："+current.entries().length()+" → "+next.entries().length()
            +"\n新增根 SPKI SHA-256："+fingerprints(added)+"\n移除根 SPKI SHA-256："+fingerprints(removed);
        persist(context,roots,status,time,source,change);
        if (isMirrorSource(source)) latestMirrorFetchedAt = Math.max(latestMirrorFetchedAt, time);
        rawRoots=roots;rawStatus=status;lastChange=change;current=next;
    }
    private static String fingerprints(Set<String> keys) throws Exception {
        var result = new ArrayList<String>();
        for (String key:keys) {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(android.util.Base64.decode(key,2));
            var text = new StringBuilder(); for(byte b:bytes) text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            result.add(text.toString());
        }
        return result.isEmpty()?"无":result.toString();
    }
    private static void persist(Context context,String roots,String status,long time,String source,String change) throws Exception {
        byte[] bytes = new JSONObject().put("roots",roots).put("status",status).put("time",time)
            .put("source",source).put("change",change)
            .put("mirror_version", Math.max(latestMirrorFetchedAt, isMirrorSource(source) ? time : 0))
            .toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length>MAX_BYTES) throw new IOException("Bundle too large");
        android.util.AtomicFile file = new android.util.AtomicFile(new File(context.getFilesDir(),"trust-store.json"));
        FileOutputStream out = null;
        try { out=file.startWrite();out.write(bytes);file.finishWrite(out); }
        catch(IOException e) { if(out!=null)file.failWrite(out);throw e; }
    }
    public static synchronized String exportBundle() throws Exception {
        return new JSONObject().put("roots",new JSONArray(rawRoots)).put("status",new JSONObject(rawStatus))
            .put("note","Public data only. Imported files cannot establish online freshness.").toString(2);
    }
    public static synchronized void importBundle(Context context,InputStream input) throws Exception {
        initialize(context);
        JSONObject bundle=new JSONObject(read(input));
        String roots=bundle.getJSONArray("roots").toString(),status=bundle.getJSONObject("status").toString();
        Snapshot imported=parse(roots,status,0,"用户离线导入（来源未在线验证）");
        if(!current.keys().containsAll(imported.keys()) || !imported.keys().containsAll(current.keys()))
            throw new IOException("离线文件不得替换信任根；新增根请通过官方 HTTPS 更新");
        // A file must never erase a revocation we already know.
        JSONObject merged=new JSONObject(current.entries().toString());
        for(Iterator<String> it=imported.entries().keys();it.hasNext();) {String k=it.next();merged.put(k,imported.entries().get(k));}
        String combined=new JSONObject().put("entries",merged).toString();
        Snapshot next=parse(rawRoots,combined,0,imported.source());
        String change="手动导入：根集合未变；撤销条目保守合并；在线新鲜度未确认";
        persist(context,rawRoots,combined,0,next.source(),change);
        current=next;rawStatus=combined;lastChange=change;lastError="";
    }
    public static boolean isFresh() {
        Snapshot s = current;
        return s != null && DiagnosticPolicy.fresh(System.currentTimeMillis(), s.fetchedAt());
    }

    public static boolean matches(byte[] publicKey) {
        Snapshot s = current;
        return s != null && s.keys().contains(android.util.Base64.encodeToString(publicKey, 2));
    }
    public static JSONObject entries() { return current.entries(); }
    public static String describe() { return describe(com.larus.nova.AppApplication.app); }
    public static String describe(Context context) {
        Snapshot s = current;
        return "顺序：" + priorityLabel(context) + "\nMirror: " + MIRROR_MANIFEST + "\n信任模式：完全信任指定仓库；镜像无签名，可自动更新根公钥集合\nRoots: " + ROOT_URL + "\nRevocation: " + STATUS_URL + "\nSource: " + s.source()
            + "\n官方抓取时间: " + (s.fetchedAt() == 0 ? "Bundled; not online-verified" : new Date(s.fetchedAt()))
            + "\nFresh (<24h): " + isFresh() + "\nRoot keys: " + s.keys().size()
            + "\n更新差异：" + lastChange
            + "\nLast update error: " + (lastError.isEmpty() ? "None" : lastError)
            + "\n仅下载公开信任数据，不上传证书或设备标识。离线/过期数据不能证明当前未撤销。";
    }
}
