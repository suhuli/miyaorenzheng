package io.github.vvb2060.keyattestation.attestation;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import io.github.vvb2060.keyattestation.R;

/** Only fixed official HTTPS endpoints; never uploads device or certificate data. */
public final class OfficialData {
    public static final String ROOT_URL = "https://android.googleapis.com/attestation/root";
    public static final String STATUS_URL = "https://android.googleapis.com/attestation/status";
    private static final long MAX_AGE = 24L * 60 * 60 * 1000;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static volatile Snapshot current;
    private static volatile String lastError = "";
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
            entries.getJSONObject(key).getString("status");
        }
        return new Snapshot(Collections.unmodifiableSet(keys), entries, time, source);
    }
    public static synchronized void initialize(Context context) {
        if (current != null) return;
        try (InputStream r = context.getResources().openRawResource(R.raw.roots);
             InputStream s = context.getResources().openRawResource(R.raw.status)) {
            current = parse(read(r), read(s), 0, "Bundled official snapshot / 内置官方快照");
        } catch (Exception e) { throw new IllegalStateException("Invalid bundled trust data", e); }
        try (InputStream in = new android.util.AtomicFile(new File(context.getFilesDir(), "official-data.json")).openRead()) {
            JSONObject cache = new JSONObject(read(in));
            current = parse(cache.getString("roots"), cache.getString("status"),
                cache.getLong("time"), "Official HTTPS cache / 官方 HTTPS 缓存");
        } catch (FileNotFoundException ignored) {
        } catch (Exception e) { lastError = "Cache rejected: " + e.getClass().getSimpleName(); }
    }
    private static String download(String url) throws IOException {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) { return read(in); }
        } finally { c.disconnect(); }
    }
    public static synchronized boolean refresh(Context context, boolean force) {
        initialize(context);
        if (!force && isFresh()) return true;
        try {
            String roots = download(ROOT_URL), status = download(STATUS_URL);
            long now = System.currentTimeMillis();
            Snapshot next = parse(roots, status, now, "Official HTTPS cache / 官方 HTTPS 缓存");
            byte[] bytes = new JSONObject().put("roots", roots).put("status", status)
                .put("time", now).toString().getBytes(StandardCharsets.UTF_8);
            android.util.AtomicFile file = new android.util.AtomicFile(new File(context.getFilesDir(), "official-data.json"));
            FileOutputStream out = null;
            try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
            catch (IOException e) { if (out != null) file.failWrite(out); throw e; }
            current = next; lastError = ""; return true;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage(); return false;
        }
    }
    public static boolean isFresh() {
        Snapshot s = current;
        long age = s == null ? -1 : System.currentTimeMillis() - s.fetchedAt();
        return s != null && s.fetchedAt() > 0 && age >= 0 && age < MAX_AGE;
    }
    public static boolean matches(byte[] publicKey) {
        Snapshot s = current;
        return s != null && s.keys().contains(android.util.Base64.encodeToString(publicKey, 2));
    }
    public static JSONObject entries() { return current.entries(); }
    public static String describe() {
        Snapshot s = current;
        return "Roots: " + ROOT_URL + "\nRevocation: " + STATUS_URL + "\nSource: " + s.source()
            + "\nFetched: " + (s.fetchedAt() == 0 ? "Bundled; not online-verified" : new Date(s.fetchedAt()))
            + "\nFresh (<24h): " + isFresh() + "\nRoot keys: " + s.keys().size()
            + "\nLast update error: " + (lastError.isEmpty() ? "None" : lastError)
            + "\n仅下载公开信任数据，不上传证书或设备标识。离线/过期数据不能证明当前未撤销。";
    }
}
