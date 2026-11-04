package com.larus.nova.dashboard

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.larus.nova.AppApplication
import com.larus.nova.BuildConfig
import com.larus.nova.R
import com.larus.nova.attestation.*
import com.larus.nova.home.HomeActivity
import com.larus.nova.home.HomeViewModel
import com.larus.nova.repository.AttestationData
import com.larus.nova.repository.BaseData
import com.larus.nova.util.Status
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native Material dashboard. The original expert inspector remains available separately. */
open class DashboardActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("dashboard", MODE_PRIVATE) }
    private lateinit var model: HomeViewModel
    private lateinit var page: LinearLayout
    private lateinit var navigation: BottomNavigationView
    private lateinit var progress: LinearProgressIndicator
    private var current: BaseData? = null
    private var tab = 1
    private var loading = true
    private var updating = false
    private var refreshPending = false
    private var error: String? = null
    private var source = "本机生成"
    private var pendingText = ""
    private val importChain = registerForActivityResult(GetContent()) { uri ->
        if (uri != null) { source = "外部证书导入"; model.load(uri) }
    }
    private val saveChain = registerForActivityResult(CreateDocument("application/x-pkcs7-certificates")) { uri -> model.save(uri) }
    private val saveText = registerForActivityResult(CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val text = pendingText
            AppApplication.executor.execute {
                runCatching { contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("无法写入文件") }
                    .onSuccess { toast("文件已保存") }.onFailure { toast("保存失败：${it.message}") }
            }
        }
        pendingText = ""
    }
    private val importData = registerForActivityResult(GetContent()) { uri ->
        if (uri != null) {
            AppApplication.officialExecutor.execute {
                val result = runCatching { contentResolver.openInputStream(uri)?.use { OfficialData.importBundle(applicationContext, it) } ?: error("无法读取文件") }
                runOnUiThread {
                    current?.revalidate(); render()
                    result.onSuccess { message("导入完成", "离线文件未取得官方在线新鲜度；已保留已有撤销条目。") }
                        .onFailure { message("未导入", it.message ?: "文件格式错误") }
                }
            }
        }
    }
    private val notificationPermission = registerForActivityResult(RequestPermission()) { granted ->
        prefs.edit().putBoolean("reminders", granted).apply()
        ExpiryWorker.schedule(this, granted)
        if (!granted) toast("通知未授权；首页仍显示临期信息")
        render()
    }

    override fun onCreate(state: Bundle?) {
        delegate.localNightMode = getSharedPreferences("dashboard", MODE_PRIVATE).getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(state)
        tab = state?.getInt("tab") ?: 1
        pendingText = state?.getString("exportText") ?: ""
        source = state?.getString("source") ?: "本机生成"
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(color(R.color.dash_background)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(22), dp(16), dp(22), dp(12)) }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text(getString(R.string.app_name), 23, true))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text(BuildConfig.VERSION_NAME, 12, true, R.color.dash_accent).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(R.color.dash_accent_soft, 20)
            contentDescription = "本机诊断，不是服务端设备完整性认证"
        })
        root.addView(top)
        progress = LinearProgressIndicator(this).apply { isIndeterminate = true; trackThickness = dp(3) }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24)) }
        scroll.addView(page); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = BottomNavigationView(this).apply {
            setBackgroundColor(color(R.color.dash_surface)); labelVisibilityMode = 1
            menu.add(0, 1, 0, "概览").setIcon(R.drawable.ic_dash_overview)
            menu.add(0, 2, 1, "证书链").setIcon(R.drawable.ic_dash_chain)
            menu.add(0, 3, 2, "记录").setIcon(R.drawable.ic_dash_history)
            menu.add(0, 4, 3, "设置").setIcon(R.drawable.ic_dash_settings)
            selectedItemId = tab
            setOnItemSelectedListener { tab = it.itemId; render(); scroll.scrollTo(0, 0); true }
        }
        navigation = bottom
        root.addView(bottom); setContentView(root)
        attachModel()
        render()
    }
    protected open fun attachModel() {
        model = ViewModelProvider(this, HomeViewModel.Factory)[HomeViewModel::class.java]
        model.getAttestationData().observe(this) { result ->
            loading = result.status == Status.LOADING
            if (result.status == Status.SUCCESS) {
                current = result.data; error = null
                current?.let { data ->
                    data.revalidate()
                    if (prefs.getBoolean("history", true)) remember(snapshot(data))
                    val expiry = rkpExpiry(data)
                    if (source == "本机生成") prefs.edit().putLong("rkp_expiry", if (data.status == RootPublicKey.Status.GOOGLE_RKP) expiry ?: 0 else 0).apply()
                }
            } else if (result.status == Status.ERROR) {
                current = null; error = result.error?.message ?: "设备未提供可解析的证明"
            }
            render()
        }
        if (prefs.getBoolean("auto_update", true)) refresh(false)
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putInt("tab", tab); state.putString("exportText", pendingText); state.putString("source", source)
        super.onSaveInstanceState(state)
    }
    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) {
            val options = getSharedPreferences("settings", MODE_PRIVATE)
            model.preferStrongBox = options.getBoolean("prefer_strongbox", true)
            model.preferAttestKey = options.getBoolean("prefer_attest_key", true)
        }
        if (::page.isInitialized) { current?.revalidate(); render() }
        if (::model.isInitialized && prefs.getBoolean("auto_update", true)) refresh(false)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun rounded(id: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color(id)); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Int = 14, bold: Boolean = false, tint: Int = R.color.dash_text) = TextView(this).apply {
        this.text = value; textSize = size.toFloat(); setTextColor(color(tint)); setLineSpacing(dp(3).toFloat(), 1f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun heading(title: String, subtitle: String) {
        page.addView(text(title, 23, true).apply { setPadding(0, 0, 0, dp(16)) })
    }
    private fun card(title: String, body: String? = null, action: (() -> Unit)? = null, tint: Int = R.color.dash_text): LinearLayout {
        val card = MaterialCardView(this).apply {
            radius = dp(22).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = color(R.color.dash_outline)
            setCardBackgroundColor(color(R.color.dash_surface))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(17), dp(18), dp(17)) }
        box.addView(text(title, 16, true, tint))
        body?.let { box.addView(text(it, 13, false, R.color.dash_muted).apply { setPadding(0, dp(8), 0, 0) }) }
        card.addView(box); page.addView(card)
        action?.let { card.isClickable = true; card.isFocusable = true; card.setOnClickListener { it() } }
        return box
    }
    private fun button(parent: LinearLayout, label: String, secondary: Boolean = false, action: () -> Unit) {
        val b = if (secondary) MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle) else MaterialButton(this)
        b.text = label; b.cornerRadius = dp(14); b.isAllCaps = false
        b.minHeight = dp(48); b.setOnClickListener { action() }
        parent.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }
    private fun row(parent: LinearLayout, title: String, value: String, tint: Int = R.color.dash_text) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; setPadding(0, dp(9), 0, dp(9)) }
        r.addView(text(title, 13, false, R.color.dash_muted), LinearLayout.LayoutParams(0, -2, .43f))
        r.addView(text(value, 13, true, tint), LinearLayout.LayoutParams(0, -2, .57f))
        parent.addView(r)
    }
    private fun render() {
        if (!::page.isInitialized) return
        page.removeAllViews(); progress.visibility = if (loading || updating) View.VISIBLE else View.INVISIBLE
        when (tab) { 1 -> overview(); 2 -> chain(); 3 -> history(); 4 -> settings() }
    }
    private fun resultCard(title: String, reason: String, level: Int, loadingState: Boolean = false) {
        val bg = if (loadingState) R.color.dash_accent_soft else when (level) {
            CertificateVerdict.NORMAL -> R.color.verdict_good_bg
            CertificateVerdict.ERROR -> R.color.verdict_bad_bg
            else -> R.color.verdict_warn_bg
        }
        val fg = if (loadingState) R.color.dash_accent else when (level) {
            CertificateVerdict.NORMAL -> R.color.verdict_good_fg
            CertificateVerdict.ERROR -> R.color.verdict_bad_fg
            else -> R.color.verdict_warn_fg
        }
        val box = card(title, tint = fg)
        val outer = box.parent as MaterialCardView
        outer.setCardBackgroundColor(color(bg)); outer.strokeWidth = 0
        box.removeAllViews()
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(if (loadingState) "···" else when (level) { 0 -> "✓"; 2 -> "×"; else -> "!" }, 42, true, fg), LinearLayout.LayoutParams(dp(62), dp(66)))
        head.addView(text(title, 27, true, fg), LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(head)
        box.addView(text(reason, 14, false, fg).apply { setPadding(0, dp(4), 0, dp(6)) })
        if (!loadingState && current != null) {
            box.addView(text("${source} · ${current!!.certs.size} 张证书  ›", 12, false, fg))
            outer.isClickable = true; outer.isFocusable = true
            outer.setOnClickListener { navigation.selectedItemId = 2 }
        }
    }
    private fun overview() {
        if (loading) { resultCard("检测中", "正在读取硬件密钥证明", 1, true); return }
        val data = current
        if (data == null) {
            resultCard("检测失败", error ?: "未取得可解析的证书", CertificateVerdict.ERROR)
            val actions = card("操作")
            button(actions, "重新检测") { source = "本机生成"; model.load() }
            button(actions, "导入证书", true) { importChain.launch("*/*") }; return
        }
        val fields = snapshot(data)
        val att = (data as? AttestationData)?.showAttestation
        val trust = att?.rootOfTrust
        val now = System.currentTimeMillis()
        val root = data.certs.firstOrNull()?.issuer
        val trusted = root == RootPublicKey.Status.GOOGLE || root == RootPublicKey.Status.GOOGLE_RKP || root == RootPublicKey.Status.KNOX || root == RootPublicKey.Status.OEM
        val verdict = CertificateVerdict.evaluate(
            data.certs.isNotEmpty() && data.certs.all { it.isSignatureValid }, trusted,
            data.certs.any { it.revocation != null },
            data.certs.all { it.cert.notBefore.time <= now && (it.cert.notAfter.time >= now || it.isLegacyExpiredAccepted) },
            data.certs.any { it.isLegacyExpiredAccepted }, OfficialData.isFresh(),
            att == null || att.attestationSecurityLevel == Attestation.KM_SECURITY_LEVEL_SOFTWARE || trust == null || !trust.isDeviceLocked || trust.verifiedBootState != 0,
            data.certs.any { it.provisioningInfo?.lostDevice == true })
        resultCard(verdict.title(), verdict.reason(), verdict.level())
        val cert = card("证书")
        row(cert, "密钥类型", if (data.hasRkpEvidence()) "RKP 远程配置" else "未发现 RKP 扩展")
        row(cert, "信任根", fields.getString("根来源"))
        row(cert, "签名", if (data.certs.all { it.isSignatureValid }) "通过" else "失败", if (data.certs.all { it.isSignatureValid }) R.color.dash_accent else R.color.dash_error)
        row(cert, "有效期", when { data.certs.any { it.isLegacyExpiredAccepted } -> "旧证书兼容"; data.certs.any { it.cert.notAfter.time < now || it.cert.notBefore.time > now } -> "异常"; else -> "正常" })
        row(cert, "撤销检查", fields.getString("撤销检查"))
        rkpExpiry(data)?.let { row(cert, "RKP 到期", date(it), if (it - now < 7L * 86400000) R.color.dash_warning else R.color.dash_text) }
        val device = card("设备")
        row(device, "证明级别", att?.let { Attestation.securityLevelToString(it.attestationSecurityLevel) } ?: "未提供")
        row(device, "启动锁", trust?.let { if (it.isDeviceLocked) "已锁定" else "未锁定" } ?: "未提供")
        row(device, "启动状态", trust?.let { RootOfTrust.verifiedBootStateToString(it.verifiedBootState) } ?: "未提供")
        row(device, "安全补丁", att?.teeEnforced?.osPatchLevel?.toString() ?: "未提供")
        provisioningCard(data.certs.mapIndexedNotNull { index, info ->
            if (info.cert.getExtensionValue("1.3.6.1.4.1.11129.2.1.30") != null)
                Pair(index + 1, info.provisioningInfo) else null
        })
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val refreshButton = MaterialButton(this).apply { text = "重新检测"; cornerRadius = dp(14); setOnClickListener { source = "本机生成"; model.load() } }
        val detailButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "查看证书"; cornerRadius = dp(14); setOnClickListener { navigation.selectedItemId = 2 } }
        actionRow.addView(refreshButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
        actionRow.addView(detailButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        page.addView(actionRow)
        page.addView(text(if (updating) "数据更新中…" else "${OfficialData.source()} · ${if (OfficialData.fetchedAt() == 0L) "未更新" else date(OfficialData.fetchedAt())}", 11, false, R.color.dash_muted).apply { setPadding(0, dp(12), 0, 0) })
    }
    private fun provisioningCard(items: List<Pair<Int, ProvisioningInfo?>>) {
        val box = card("配置扩展")
        if (items.isEmpty()) {
            row(box, "状态", "未发现配置扩展")
            return
        }
        items.forEach { (position, info) ->
            row(box, "所在证书", "第 $position 张（从根计数）")
            if (info == null) {
                row(box, "状态", "扩展存在，解析失败", R.color.dash_warning)
            } else {
                row(box, "30 天颁发近似数", info.certsIssued?.toString() ?: "未提供")
                row(box, "受认证来源", info.validatedAttestedEntity ?: "未提供")
                row(box, "遗失设备字段", info.lostDevice?.let { if (it) "标记为遗失设备" else "未标记（非实时）" } ?: "未提供",
                    if (info.lostDevice == true) R.color.dash_error else R.color.dash_text)
                info.manufacturer?.let { row(box, "制造商（旧字段）", it) }
                if (info.extraFields.isNotEmpty()) {
                    info.extraFields.forEach { field -> row(box, "字段 ${field.path()} · ${field.type()}", field.value()) }
                    box.addView(text("附加字段的业务含义尚未确认，保留原文。", 12, false, R.color.dash_muted))
                } else {
                    info.unknownFields.forEach { (key, value) -> row(box, "字段 $key", value) }
                }
                button(box, "查看第 $position 张配置详情", true) { message("配置扩展", info.describe()) }
            }
        }
        box.addView(text("签发时快照，非实时计数或设备状态。", 12, false, R.color.dash_muted))
    }
    private fun chain() {
        heading("证书链", "从信任根到目标密钥 · 每条连接单独验证")
        val data = current
        if (data == null || loading) { card("暂无可显示的链", "请先完成检测或导入。"); return }
        
        data.certs.forEachIndexed { index, info ->
            val role = when { index == 0 -> "信任根"; info.attestation != null -> if (index == data.certs.lastIndex) "目标证明" else "App Attest Key"; info.provisioningInfo != null -> "RKP 配置扩展"; else -> "中间证书" }
            val label = if (index == 0) "根公钥" else "由上一级签发"
            val c = card("${index + 1}  /  $role", "$label · ${if (info.isSignatureValid) "签名通过" else "签名/路径失败"}", tint = if (info.isSignatureValid) R.color.dash_text else R.color.dash_error)
            row(c, "证书主体", info.cert.subjectX500Principal.name)
            row(c, "有效期", info.validityLabel)
            row(c, "截止时间", date(info.cert.notAfter.time))
            if (info.revocation != null) row(c, "撤销条目", info.revocation.toString(), R.color.dash_error)
            info.securityException?.let { row(c, "错误位置", it.message ?: it.javaClass.simpleName, R.color.dash_error) }
            info.provisioningInfo?.let { p ->
                row(c, "30 天颁发近似数", p.certsIssued?.toString() ?: "未提供")
                row(c, "受认证来源", p.validatedAttestedEntity ?: "未提供")
                row(c, "遗失设备字段", p.lostDevice?.let { if (it) "证书标记为遗失设备" else "未标记（非实时保证）" } ?: "未提供", if (p.lostDevice == true) R.color.dash_error else R.color.dash_text)
            }
            button(c, "查看完整证书详情", true) { message("第 ${index + 1} 张证书", certificateDetails(info)) }
            if (index < data.certs.lastIndex) page.addView(text("│   ${if (data.certs[index + 1].isSignatureValid) "下一连接签名通过" else "下一连接失败"}", 12, false, R.color.dash_muted).apply { setPadding(dp(18), 0, 0, dp(12)) })
        }
        val actions = card("导出与检查")
        button(actions, "导入证书", true) { importChain.launch("*/*") }
        button(actions, "脱敏报告", true) { exportReport(true) }
        button(actions, "保存证书链（PKCS#7）", true) { saveChain.launch("nova-chain.p7b") }
        button(actions, "完整报告（含标识）", true) { exportReport(false) }
        button(actions, "打开高级字段检查器", true) { startActivity(Intent(this, HomeActivity::class.java)) }
    }
    private fun certificateDetails(info: CertificateInfo): String = buildString {
        append("主体：${info.cert.subjectX500Principal}\n发行者：${info.cert.issuerX500Principal}\n序列号：${info.cert.serialNumber.toString(16)}\n")
        append("有效期：${date(info.cert.notBefore.time)} — ${date(info.cert.notAfter.time)}\n策略结果：${info.validityLabel}\n")
        append("签名：${info.isSignatureValid}\n证书 SHA-256：${hash(info.cert.encoded)}\n")
        append("撤销：${info.revocation ?: DiagnosticPolicy.revocation(false, OfficialData.isFresh())}\n")
        info.provisioningInfo?.let { append("\n配置扩展：\n${it.describe()}\n") }
        info.attestation?.let { append("\n解析证明：\n$it\n") }
        info.securityException?.let { append("\n错误：${it.message}") }
    }
    private fun snapshot(data: BaseData): JSONObject {
        val certs = data.certs
        val att = (data as? AttestationData)?.showAttestation
        val trust = att?.rootOfTrust
        val root = certs.firstOrNull()
        return JSONObject().apply {
            put("记录时间", System.currentTimeMillis().toString()); put("数据对象", source)
            put("配置类型", if (data.hasRkpEvidence()) "检测到 RKP 配置证据" else "无法确定（未发现可验证扩展）")
            put("根来源", when (root?.issuer) {
                RootPublicKey.Status.GOOGLE, RootPublicKey.Status.GOOGLE_RKP -> "Google 官方根"
                RootPublicKey.Status.AOSP -> "AOSP 测试根（非生产信任）"
                RootPublicKey.Status.KNOX -> "Samsung Knox 根"
                RootPublicKey.Status.OEM -> "设备 OEM 根"
                else -> "未知根（不作为官方信任）"
            })
            put("根指纹", root?.let { hash(it.cert.encoded) } ?: "未提供")
            put("叶指纹", certs.lastOrNull()?.let { hash(it.cert.encoded) } ?: "未提供")
            put("签名路径", if (certs.isNotEmpty() && certs.all { it.isSignatureValid }) "全部连接通过" else "存在失败连接")
            put("有效期策略", when { certs.any { it.cert.notBefore.time > System.currentTimeMillis() || (it.cert.notAfter.time < System.currentTimeMillis() && !it.isLegacyExpiredAccepted) } -> "存在过期或尚未生效证书"; certs.any { it.isLegacyExpiredAccepted } -> "旧工厂链兼容（存在已过期证书）"; else -> "全部在有效期内" })
            put("撤销检查", if (certs.any { it.revocation != null }) DiagnosticPolicy.revocation(true, OfficialData.isFresh())
                else if (certs.any { !it.isSignatureValid }) "未完成（先检查签名/路径）"
                else DiagnosticPolicy.revocation(false, OfficialData.isFresh()))
            put("安全级别", att?.let { Attestation.securityLevelToString(it.attestationSecurityLevel) } ?: "未提供")
            put("启动锁", trust?.isDeviceLocked?.toString() ?: "未提供")
            put("启动状态", trust?.let { RootOfTrust.verifiedBootStateToString(it.verifiedBootState) } ?: "未提供")
            put("补丁字段", att?.teeEnforced?.osPatchLevel?.toString() ?: "未提供")
            put("证书数量", certs.size.toString())
        }
    }
    private fun remember(snap: JSONObject) {
        val list = records()
        if (list.length() > 0 && list.getJSONObject(0).optString("叶指纹") == snap.optString("叶指纹") && list.getJSONObject(0).optString("数据对象") == snap.optString("数据对象")) return
        val next = JSONArray().put(snap)
        for (i in 0 until minOf(list.length(), 29)) next.put(list.get(i))
        prefs.edit().putString("snapshots", next.toString()).apply()
    }
    private fun records(): JSONArray = runCatching { JSONArray(prefs.getString("snapshots", "[]")) }.getOrDefault(JSONArray())
    private fun map(json: JSONObject): Map<String, String> = json.keys().asSequence().associateWith { json.optString(it) }
    private fun history() {
        heading("本地记录", "最多保存 30 次结构化摘要，不保存私钥或原始证书。")
        val list = records()
        if (list.length() == 0) card("还没有记录", "完成检测后记录会保存在本机。可在设置中关闭或清空。")
        if (list.length() >= 2) {
            val changes = SnapshotDiff.compare(map(list.getJSONObject(1)), map(list.getJSONObject(0)))
            card("最近两次 · ${changes.size} 项变化", if (changes.isEmpty()) "摘要字段未变化" else changes.take(4).joinToString("\n"), { compareDialog(list.getJSONObject(1), list.getJSONObject(0)) })
        }
        for (i in 0 until list.length()) {
            val snap = list.getJSONObject(i)
            card(date(snap.optString("记录时间").toLongOrNull() ?: 0), "${snap.optString("数据对象")} · ${snap.optString("根来源")}\n${snap.optString("配置类型")}\n${snap.optString("签名路径")}", {
                val choices = arrayOf("查看摘要", "与另一条记录比较")
                MaterialAlertDialogBuilder(this).setTitle("记录操作").setItems(choices) { _, which ->
                    if (which == 0) message("本地摘要", map(snap).entries.joinToString("\n") { "${it.key}：${it.value}" })
                    else {
                        val indices = (0 until list.length()).filter { it != i }
                        if (indices.isEmpty()) toast("至少需要两条记录")
                        else MaterialAlertDialogBuilder(this).setTitle("选择另一条记录")
                            .setItems(indices.map { date(list.getJSONObject(it).optString("记录时间").toLong()) }.toTypedArray()) { _, selected -> compareDialog(list.getJSONObject(indices[selected]), snap) }.show()
                    }
                }.show()
            })
        }
        if (list.length() > 0) {
            val b = card("管理记录")
            button(b, "清空全部记录", true) { confirm("清空记录？", "仅删除本地摘要，不删除系统密钥。") { prefs.edit().remove("snapshots").apply(); render() } }
        }
    }
    private fun compareDialog(before: JSONObject, after: JSONObject) {
        val diff = SnapshotDiff.compare(map(before), map(after))
        message("摘要对比", "证书指纹变化可能是正常轮换、App 密钥重建或检测模式变化，不代表泄露。不同数据对象不应视为同一设备连续观测。\n\n" + if (diff.isEmpty()) "未发现摘要变化" else diff.joinToString("\n\n"))
    }
    private fun toggle(parent: LinearLayout, title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
        val s = MaterialSwitch(this).apply { text = title; isChecked = checked; minHeight = dp(52); setTextColor(color(R.color.dash_text)); setOnCheckedChangeListener { _, v -> change(v) } }
        parent.addView(s); parent.addView(text(description, 12, false, R.color.dash_muted).apply { setPadding(0, 0, 0, dp(12)) })
    }
    private fun settings() {
        heading("设置", "清楚知道数据从哪里来、保存在哪里。")
        val data = card("信任数据", if (OfficialData.isFresh()) "有效" else "过期 / 未更新")
        row(data, "当前数据来源", OfficialData.source())
        row(data, "信任根公钥", OfficialData.rootCount().toString())
        row(data, "撤销条目", OfficialData.entryCount().toString())
        row(data, "数据抓取时间", if (OfficialData.fetchedAt() == 0L) "未在线确认" else date(OfficialData.fetchedAt()))
        button(data, if (updating) "更新中…" else "立即更新数据") { refresh(true) }
        if (OfficialData.error().isNotEmpty()) row(data, "更新提示", OfficialData.error())
        button(data, "更新详情", true) { message("数据来源与更新记录", OfficialData.describe(this@DashboardActivity)) }
        button(data, "导出公开数据包", true) {
            runCatching { pendingText = OfficialData.exportBundle(); saveText.launch("nova-trust-data.json") }.onFailure { toast(it.message ?: "导出失败") }
        }
        button(data, "导入离线数据包", true) {
            confirm("离线导入限制", "只接受根集合与本机已知官方根一致的数据包。撤销条目保守合并，不能删除已有条目；导入不等于官方在线确认。") { importData.launch("*/*") }
        }
        button(data, "数据源优先级：${OfficialData.priorityLabel(this)}", true) {
            val values = arrayOf(com.larus.nova.attestation.DataSourcePolicy.OFFICIAL_FIRST, com.larus.nova.attestation.DataSourcePolicy.MIRROR_FIRST)
            val labels = values.map { com.larus.nova.attestation.DataSourcePolicy.label(it) }.toTypedArray()
            MaterialAlertDialogBuilder(this).setTitle("数据源优先级")
                .setSingleChoiceItems(labels, values.indexOf(OfficialData.priority(this))) { dialog, index ->
                    prefs.edit().putString(com.larus.nova.attestation.DataSourcePolicy.KEY, values[index]).apply()
                    dialog.dismiss(); render()
                    if (::model.isInitialized) refresh(true) else toast("已保存")
                }.setNegativeButton("取消", null).show()
        }
        toggle(data, "启动时检查更新", "按所选优先级更新，失败保留本地缓存。", prefs.getBoolean("auto_update", true)) { prefs.edit().putBoolean("auto_update", it).apply() }
        val privacy = card("隐私与提醒")
        toggle(privacy, "保留本地历史", "最多 30 条，可随时关闭或清空。", prefs.getBoolean("history", true)) { prefs.edit().putBoolean("history", it).apply() }
        toggle(privacy, "证书临期通知", "上次记录的 RKP 证书在 7 天内到期时提醒。", prefs.getBoolean("reminders", false)) {
            if (it && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else { prefs.edit().putBoolean("reminders", it).apply(); ExpiryWorker.schedule(this, it) }
        }
        toggle(privacy, "严格有效期模式", "开启后不使用旧工厂证书过期兼容策略。", prefs.getBoolean("strict_expiry", false)) {
            prefs.edit().putBoolean("strict_expiry", it).apply(); current?.revalidate(); render()
        }
        val mode = card("检测选项")
        if (::model.isInitialized) {
            if (model.hasStrongBox) toggle(mode, "优先使用 StrongBox", "重新检测后生效。", model.preferStrongBox) { model.preferStrongBox = it }
            if (model.hasAttestKey) toggle(mode, "使用 App Attest Key", "使用应用自建证明密钥。", model.preferAttestKey) { model.preferAttestKey = it }
        }
        button(mode, "高级工具与完整字段", true) { startActivity(Intent(this, HomeActivity::class.java)) }
        button(mode, "外观：跟随系统 / 浅色 / 深色", true) {
            MaterialAlertDialogBuilder(this).setTitle("外观").setItems(arrayOf("跟随系统", "浅色", "深色")) { _, n ->
                val value = intArrayOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)[n]
                prefs.edit().putInt("theme", value).apply(); delegate.localNightMode = value
            }.show()
        }
        card("关于", "${BuildConfig.VERSION_NAME}", {
            message("关于与限制", "上游：vvb2060/KeyAttestation\n社区仓库：suhuli/miyaorenzheng\n\n本机受控环境可能篡改解析和显示。最终安全决定应在独立可信环境中验证。\n原有主动 RKP 工具需要 Shizuku，并在确认后向指定服务提交配置数据；普通识别无需该操作。")
        })
    }
    private fun refresh(force: Boolean) {
        if (updating) { if (force) refreshPending = true; return }
        updating = true; render()
        AppApplication.officialExecutor.execute {
            val success = OfficialData.refresh(applicationContext, force)
            runOnUiThread {
                updating = false
                if (refreshPending) { refreshPending = false; refresh(true); return@runOnUiThread }
                current?.revalidate(); render()
                if (force) toast(if (success) "数据检查完成 · ${OfficialData.source()}" else "更新失败，保留旧快照；详情见设置")
            }
        }
    }
    private fun exportReport(redacted: Boolean) {
        val data = current ?: return toast("请先完成一次检测")
        fun save() {
            pendingText = buildString {
                append("${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}\n${date(System.currentTimeMillis())}\n模式：${if (redacted) "脱敏摘要" else "完整诊断"}\n")
                append("本机诊断，不是服务端挑战验证或设备完整性保证。\n")
                if (redacted) {
                    val safe = snapshot(data)
                    for ((key, value) in ReportFields.redact(map(safe))) append("$key：$value\n")
                    append("已省略：证书主体/序列号/指纹、原始证明、挑战、应用标识和启动哈希。剩余字段组合仍可能具有可关联性。\n")
                    append("官方数据在线新鲜度：${OfficialData.isFresh()}\n")
                } else {
                    append("${OfficialData.describe(this@DashboardActivity)}\n${data.rkpSummary}\n")
                    data.certs.forEachIndexed { index, cert -> append("\n证书 ${index + 1}\n${certificateDetails(cert)}\n") }
                }
            }
            saveText.launch(if (redacted) "nova-summary.txt" else "nova-report-full.txt")
        }
        if (redacted) save() else confirm("导出完整诊断报告？", "可能包含设备标识、证书指纹、挑战与应用信息。仅写入你选择的位置，不自动上传。") { save() }
    }
    private fun rkpExpiry(data: BaseData): Long? {
        if (!data.hasRkpEvidence()) return null
        // Only provisioning cert and the immediately subsequent attestation issuer, not long-lived root/app leaf.
        val i = data.certs.indexOfFirst { it.provisioningInfo != null }
        if (i < 0) return null
        return data.certs.subList(i, minOf(i + 2, data.certs.size)).minOfOrNull { it.cert.notAfter.time }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun date(time: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
    private fun message(title: String, body: String) { MaterialAlertDialogBuilder(this).setTitle(title).setMessage(body).setPositiveButton("知道了", null).show().apply { findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true) } }
    private fun confirm(title: String, body: String, action: () -> Unit) { MaterialAlertDialogBuilder(this).setTitle(title).setMessage(body).setPositiveButton("继续") { _, _ -> action() }.setNegativeButton("取消", null).show() }
    private fun toast(text: String) = runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
}
