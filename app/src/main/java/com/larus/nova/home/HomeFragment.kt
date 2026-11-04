package com.larus.nova.home

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.larus.nova.AppApplication
import com.larus.nova.BuildConfig
import com.larus.nova.R
import com.larus.nova.app.AlertDialogFragment
import com.larus.nova.app.AppFragment
import com.larus.nova.attestation.Attestation
import com.larus.nova.databinding.HomeBinding
import com.larus.nova.keystore.KeyStoreManager
import com.larus.nova.lang.AttestationException
import com.larus.nova.repository.AttestationData
import com.larus.nova.util.Status
import rikka.html.text.HtmlCompat
import rikka.html.text.toHtml
import rikka.shizuku.Shizuku
import rikka.widget.borderview.BorderView

class HomeFragment : AppFragment(), HomeAdapter.Listener, MenuProvider {

    private var _binding: HomeBinding? = null

    private val binding: HomeBinding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory }

    private val save = registerForActivityResult(CreateDocument("application/x-pkcs7-certificates")) {
        viewModel.save(it)
    }

    private val exportReport = registerForActivityResult(CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val data = viewModel.getAttestationData().value?.data
            val context = requireContext().applicationContext
            AppApplication.executor.execute {
                runCatching {
                    val text = buildString {
                        append("Key Attestation Community — local diagnostic report\n")
                        append("App version: ").append(BuildConfig.VERSION_NAME).append("\n")
                        append(java.util.Date()).append("\n")
                        append("本报告不是服务端挑战验证；请勿作为设备完整性保证。\n")
                        append(com.larus.nova.attestation.OfficialData.describe()).append("\n")
                        if (data != null) {
                            append("Chain status: ").append(data.status).append("\n")
                            append("Selected certificate path length: ").append(data.certs.size).append("\n")
                            append(data.rkpSummary).append("\n")
                            data.certs.forEach {
                                append("\nSubject: ").append(it.cert.subjectX500Principal)
                                append("\nIssuer: ").append(it.cert.issuerX500Principal)
                                append("\nSerial: ").append(it.cert.serialNumber.toString(16))
                                append("\nValidity: ").append(it.cert.notBefore).append(" — ").append(it.cert.notAfter)
                                append("\nStatus: ").append(it.status)
                                append("\nSHA-256: ").append(java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(it.cert.encoded).joinToString("") { b -> "%02x".format(b) })
                                it.attestation?.let { a -> append("\nParsed attestation:\n").append(a.toString()) }
                                append("\nError: ").append(it.securityException?.message ?: "none").append("\n")
                            }
                        }
                    }
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Unable to open document")
                }.onSuccess { AppApplication.toast("报告已导出") }
                    .onFailure { AppApplication.toast("导出失败：${it.message}") }
            }
        }
    }

    private fun refreshOfficial(force: Boolean) {
        val context = requireContext().applicationContext
        val owner = viewLifecycleOwner
        if (force) AppApplication.toast("正在更新官方数据…")
        AppApplication.officialExecutor.execute {
            val ok = com.larus.nova.attestation.OfficialData.refresh(context, force)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (force) AppApplication.toast(if (ok) "官方数据更新成功" else "更新失败，保留旧数据；详情见官方数据状态")
                if (owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    viewModel.getAttestationData().value?.data?.let { adapter.updateData(it) }
                }
            }
        }
    }

    private val load = registerForActivityResult(GetContent()) {
        viewModel.load(it)
    }

    private val import = registerForActivityResult(GetContent()) {
        viewModel.import(it)
    }

    private val adapter by lazy {
        HomeAdapter(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = HomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner)

        refreshOfficial(false)
        val context = view.context

        binding.list.borderVisibilityChangedListener = BorderView.OnBorderVisibilityChangedListener { top: Boolean, _: Boolean, _: Boolean, _: Boolean -> appActivity?.appBar?.setRaised(!top) }
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapter
        binding.list.addItemDecoration(HomeItemDecoration(context))

        viewModel.getAttestationData().observe(viewLifecycleOwner) { res ->
            when (res.status) {
                Status.SUCCESS -> {
                    binding.progress.isVisible = false
                    binding.list.isVisible = true
                    adapter.updateData(res.data!!)
                }
                Status.ERROR -> {
                    binding.progress.isVisible = false
                    binding.list.isVisible = true
                    adapter.updateData(res.error as AttestationException)
                }
                Status.LOADING -> {
                    binding.progress.isVisible = true
                    binding.list.isVisible = false
                }
            }
        }
    }

    override fun onAttestationInfoClick(data: Attestation) {
        val result = viewModel.getAttestationData().value!!.data!! as AttestationData
        result.showAttestation = data
        adapter.updateData(result)
    }

    override fun onRkpHostnameClick(data: String) {
        val context = requireContext()
        val dp24 = Math.round(24 * context.resources.displayMetrics.density)
        val dp18 = Math.round(18 * context.resources.displayMetrics.density)
        val editText = AppCompatEditText(context)
        editText.setHint(R.string.rkp_hostname_empty)
        editText.setText(data)
        editText.setPadding(dp24, dp18, dp24, dp18)
        editText.requestFocus()

        AlertDialog.Builder(context)
            .setView(editText)
            .setTitle(R.string.rkp_hostname)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.rkp(editText.text?.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCommonDataClick(data: Data) {
        val context = requireActivity()

        AlertDialogFragment.Builder(context)
            .title(data.title)
            .message(data.getMessage(context))
            .positiveButton(android.R.string.ok)
            .build()
            .show(context.supportFragmentManager)
    }

    override fun onPrepareMenu(menu: Menu) {
        menu.findItem(R.id.menu_use_shizuku).apply {
            isVisible = Shizuku.pingBinder()
            val received = KeyStoreManager.getRemoteKeyStore() != null
            if (!received) viewModel.preferShizuku = false
            isEnabled = received
            isChecked = viewModel.preferShizuku
        }

        menu.findItem(R.id.menu_use_strongbox)?.isVisible = !viewModel.preferSak
        menu.findItem(R.id.menu_use_attest_key)?.isVisible = !viewModel.preferSak
        menu.findItem(R.id.menu_import_attest_key)?.isVisible = !viewModel.preferSak
                && viewModel.preferAttestKey

        menu.setGroupVisible(R.id.menu_id_type_group, viewModel.preferShizuku)
        menu.findItem(R.id.menu_include_unique_id).isVisible =
            viewModel.preferShizuku && viewModel.canIncludeUniqueId
        menu.findItem(R.id.menu_rkp_test).isVisible =
            viewModel.preferShizuku && viewModel.canCheckRkp
        menu.findItem(R.id.menu_use_sak)?.isVisible =
            viewModel.preferShizuku && viewModel.canSak

        menu.findItem(R.id.menu_save).isVisible = viewModel.hasCertificates()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.home, menu)
        menu.findItem(R.id.menu_use_strongbox).isChecked = viewModel.preferStrongBox
        menu.findItem(R.id.menu_use_attest_key).isChecked = viewModel.preferAttestKey
        menu.findItem(R.id.menu_include_props).isChecked = viewModel.preferIncludeProps
        menu.findItem(R.id.menu_id_type_serial).isChecked = viewModel.preferIdAttestationSerial
        menu.findItem(R.id.menu_id_type_imei).isChecked = viewModel.preferIdAttestationIMEI
        menu.findItem(R.id.menu_id_type_meid).isChecked = viewModel.preferIdAttestationMEID
        menu.findItem(R.id.menu_include_unique_id).isChecked = viewModel.preferIncludeUniqueId
        menu.findItem(R.id.menu_use_sak).isChecked = viewModel.preferSak
        if (!viewModel.hasSak) {
            menu.removeItem(R.id.menu_use_sak)
        }
        if (!viewModel.hasStrongBox) {
            menu.removeItem(R.id.menu_use_strongbox)
        }
        if (!viewModel.hasAttestKey) {
            menu.removeItem(R.id.menu_use_attest_key)
            menu.removeItem(R.id.menu_import_attest_key)
        }
        if (!viewModel.hasDeviceIds) {
            menu.removeItem(R.id.menu_include_props)
            menu.removeItem(R.id.menu_id_type_serial)
            menu.removeItem(R.id.menu_id_type_imei)
            menu.removeItem(R.id.menu_id_type_meid)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            menu.removeItem(R.id.menu_include_props)
        }
        if (!viewModel.hasIMEI) {
            menu.removeItem(R.id.menu_id_type_imei)
        }
        if (!viewModel.hasMEID) {
            menu.removeItem(R.id.menu_id_type_meid)
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val status = !item.isChecked
        item.isChecked = status
        when (item.itemId) {
            R.id.menu_use_shizuku -> {
                viewModel.preferShizuku = status
                viewModel.load()
            }
            R.id.menu_use_sak -> {
                viewModel.preferSak = status
                viewModel.load()
            }
            R.id.menu_use_strongbox -> {
                viewModel.preferStrongBox = status
                viewModel.load()
            }
            R.id.menu_use_attest_key -> {
                viewModel.preferAttestKey = status
                viewModel.load()
            }
            R.id.menu_include_props -> {
                viewModel.preferIncludeProps = status
                viewModel.load()
            }
            R.id.menu_id_type_serial -> {
                viewModel.preferIdAttestationSerial = status
                viewModel.load()
            }
            R.id.menu_id_type_imei -> {
                viewModel.preferIdAttestationIMEI = status
                viewModel.load()
            }
            R.id.menu_id_type_meid -> {
                viewModel.preferIdAttestationMEID = status
                viewModel.load()
            }
            R.id.menu_include_unique_id -> {
                viewModel.preferIncludeUniqueId = status
                viewModel.load()
            }
            R.id.menu_rkp_test -> {
                AlertDialog.Builder(requireContext()).setTitle(R.string.rkp_test)
                    .setMessage("此主动测试需要 Shizuku，并会向配置的 RKP 服务提交 CSR/配置数据。日常证书 RKP 识别不需要此操作。是否继续？")
                    .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.rkp() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
            R.id.menu_reset -> {
                viewModel.load(true)
            }
            R.id.menu_save -> {
                save.launch("${Build.PRODUCT}-${AppApplication.TAG}.p7b")
            }
            R.id.menu_load -> {
                load.launch("*/*")
            }
            R.id.menu_import_attest_key -> {
                import.launch("text/xml")
            }
            R.id.menu_refresh_official -> { refreshOfficial(true) }
            R.id.menu_show_official -> {
                AlertDialog.Builder(requireContext()).setTitle(R.string.official_data)
                    .setMessage(com.larus.nova.attestation.OfficialData.describe())
                    .setPositiveButton(android.R.string.ok, null).show()
            }
            R.id.menu_export_report -> {
                AlertDialog.Builder(requireContext()).setTitle(R.string.export_report)
                    .setMessage("报告含证书主体、序列号及指纹，分享前请确认隐私。不会自动上传。")
                    .setPositiveButton(android.R.string.ok) { _, _ -> exportReport.launch("nova-report.txt") }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
            R.id.menu_about -> {
                showAboutDialog()
            }
            else -> return false
        }
        return true
    }

    private fun showAboutDialog() {
        val context = requireContext()
        val text = StringBuilder()
        val source = "<b><a href=\"${context.getString(R.string.github_url)}\">GitHub</a></b>"
        val shizuku = "<b><a href=\"${context.getString(R.string.shizuku_url)}\">Web</a></b>"
        text.append(BuildConfig.VERSION_NAME).append("<p>")
        text.append(getString(R.string.open_source_info, source, context.getString(R.string.license)))
        if (Shizuku.pingBinder()) {
            KeyStoreManager.requestPermission()
        } else if (KeyStoreManager.isShizukuInstalled()) {
            KeyStoreManager.requestBinder(context)
            text.append("<p>").append(context.getString(R.string.start_shizuku))
        } else {
            text.append("<p>").append(context.getString(R.string.install_shizuku, shizuku))
        }
        text.append("<p>").append(context.getString(R.string.copyright))
        val icon = context.getDrawable(R.drawable.ic_launcher)
        val dialog: Dialog = AlertDialog.Builder(context)
                .setView(rikka.material.R.layout.dialog_about)
                .show()
        dialog.findViewById<TextView>(rikka.material.R.id.design_about_info).isVisible = false
        dialog.findViewById<ImageView>(rikka.material.R.id.design_about_icon).setImageDrawable(icon)
        dialog.findViewById<TextView>(rikka.material.R.id.design_about_title).text = getString(R.string.app_name)
        dialog.findViewById<TextView>(rikka.material.R.id.design_about_version).apply {
            movementMethod = LinkMovementMethod.getInstance()
            this.text = text.toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
        }

    }
}
