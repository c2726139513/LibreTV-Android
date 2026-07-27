package com.vidhub.android.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.vidhub.android.R
import com.vidhub.android.data.remote.dto.SourceInfo
import com.vidhub.android.data.repository.FetchSourcesResult
import com.vidhub.android.model.CustomSource
import com.vidhub.android.model.ServerConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.settings_fragment) {

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var serverList: VerticalGridView
    private lateinit var addButton: Button
    private lateinit var clearHistoryButton: Button
    private lateinit var emptyText: TextView
    private var serverAdapter: ServerAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        serverList = view.findViewById(R.id.server_list)
        addButton = view.findViewById(R.id.btn_add_server)
        clearHistoryButton = view.findViewById(R.id.btn_clear_history)
        emptyText = view.findViewById(R.id.empty_text)

        serverAdapter = ServerAdapter(
            onEdit = { server -> showEditServerDialog(server) },
            onDelete = { server -> showDeleteConfirmDialog(server) },
            onActivate = { server -> viewModel.setActiveServer(server.id) },
            onManageSources = { server -> showSourceManageDialog(server) }
        )
        serverList.adapter = serverAdapter

        addButton.setOnClickListener {
            showAddServerDialog()
        }

        clearHistoryButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("清除播放历史")
                .setMessage("确认清除所有播放历史？")
                .setPositiveButton("确认") { _, _ -> viewModel.clearWatchHistory() }
                .setNegativeButton("取消", null)
                .show()
        }

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.servers.collect { servers ->
                        serverAdapter?.submitList(servers)
                        emptyText.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun showAddServerDialog() {
        showServerDialog(null)
    }

    private fun showEditServerDialog(server: ServerConfig) {
        showServerDialog(server)
    }

    private fun showServerDialog(existing: ServerConfig?) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val nameInput = EditText(context).apply {
            hint = "服务器名称"
            setText(existing?.name ?: "")
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }
        layout.addView(nameInput)

        val urlInput = EditText(context).apply {
            hint = "服务器地址 (https://...)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(existing?.url ?: "")
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }
        layout.addView(urlInput)

        val passwordInput = EditText(context).apply {
            hint = "密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(existing?.password ?: "")
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }
        layout.addView(passwordInput)

        AlertDialog.Builder(context)
            .setTitle(if (existing == null) "添加服务器" else "编辑服务器")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()
                if (name.isBlank() || url.isBlank()) {
                    Toast.makeText(context, "名称和地址不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing != null) {
                    viewModel.updateServer(existing.id, name, url, password)
                } else {
                    viewModel.addServer(name, url, password)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteConfirmDialog(server: ServerConfig) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除服务器")
            .setMessage("确认删除「${server.name}」？")
            .setPositiveButton("删除") { _, _ -> viewModel.removeServer(server.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSourceManageDialog(server: ServerConfig) {
        val context = requireContext()
        val loadingDialog = AlertDialog.Builder(context)
            .setTitle("管理源 - ${server.name}")
            .setMessage("正在获取源列表...")
            .setCancelable(false)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.fetchSources(server)
            loadingDialog.dismiss()
            when (result) {
                is FetchSourcesResult.Success -> showSourceCheckDialog(server, result.sources)
                is FetchSourcesResult.Error -> {
                    Toast.makeText(context, "获取源列表失败: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSourceCheckDialog(server: ServerConfig, sources: List<SourceInfo>) {
        val context = requireContext()
        val selectedKeys = server.enabledSources.toMutableSet()
        val customSources = server.customSources.toMutableList()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        fun rebuildSourceList() {
            layout.removeAllViews()

            layout.addView(TextView(context).apply {
                text = "服务器源"
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 16f
                setPadding(0, 0, 0, 12)
            })

            sources.forEach { source ->
                layout.addView(CheckBox(context).apply {
                    text = source.name
                    isChecked = selectedKeys.contains(source.key)
                    setTextColor(0xFFFFFFFF.toInt())
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedKeys.add(source.key)
                        else selectedKeys.remove(source.key)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
            }

            if (customSources.isNotEmpty()) {
                layout.addView(TextView(context).apply {
                    text = "自定义源"
                    setTextColor(0xFFAAAAAA.toInt())
                    textSize = 16f
                    setPadding(0, 16, 0, 8)
                })

                customSources.forEachIndexed { index, cs ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 4, 0, 4)
                    }
                    row.addView(CheckBox(context).apply {
                        text = "${cs.name} (${cs.url.take(30)}...)"
                        isChecked = true
                        isEnabled = false
                        setTextColor(0xFFFFFFFF.toInt())
                        layoutParams = LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                        )
                    })
                    row.addView(Button(context).apply {
                        text = "删除"
                        setTextColor(0xFFEF5350.toInt())
                        setOnClickListener {
                            customSources.removeAt(index)
                            rebuildSourceList()
                        }
                    })
                    layout.addView(row)
                }
            }

            layout.addView(Button(context).apply {
                text = "＋ 添加自定义源"
                setTextColor(0xFF1E90FF.toInt())
                setOnClickListener { showAddCustomSourceDialog(customSources) { rebuildSourceList() } }
            })
        }

        rebuildSourceList()

        val scrollView = android.widget.ScrollView(context).apply {
            addView(layout)
        }

        AlertDialog.Builder(context)
            .setTitle("选择启用的源")
            .setView(scrollView)
            .setPositiveButton("保存") { _, _ ->
                val enabled = sources
                    .filter { selectedKeys.contains(it.key) }
                    .map { it.key }
                viewModel.updateServerSources(server.id, enabled, customSources.toList())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddCustomSourceDialog(
        customSources: MutableList<CustomSource>,
        onRefresh: () -> Unit
    ) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val nameInput = EditText(context).apply {
            hint = "源名称"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }
        layout.addView(nameInput)
        val urlInput = EditText(context).apply {
            hint = "CMS API 地址"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(urlInput)

        AlertDialog.Builder(context)
            .setTitle("添加自定义源")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (name.isBlank() || url.isBlank()) {
                    Toast.makeText(context, "名称和地址不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                customSources.add(CustomSource(name = name, url = url))
                onRefresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

class ServerAdapter(
    private val onEdit: (ServerConfig) -> Unit,
    private val onDelete: (ServerConfig) -> Unit,
    private val onActivate: (ServerConfig) -> Unit,
    private val onManageSources: (ServerConfig) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    private var servers = listOf<ServerConfig>()

    fun submitList(list: List<ServerConfig>) {
        servers = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val nameText = TextView(parent.context).apply {
            id = View.generateViewId()
            textSize = 18f
        }
        val urlText = TextView(parent.context).apply {
            id = View.generateViewId()
            textSize = 14f
        }
        val buttonLayout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val activateBtn = Button(parent.context).apply { text = "切换" }
        val manageSourcesBtn = Button(parent.context).apply { text = "管理源" }
        val editBtn = Button(parent.context).apply { text = "编辑" }
        val deleteBtn = Button(parent.context).apply { text = "删除" }

        buttonLayout.addView(activateBtn)
        buttonLayout.addView(manageSourcesBtn)
        buttonLayout.addView(editBtn)
        buttonLayout.addView(deleteBtn)
        view.addView(nameText)
        view.addView(urlText)
        view.addView(buttonLayout)

        return ViewHolder(view, nameText, urlText, activateBtn, manageSourcesBtn, editBtn, deleteBtn)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val server = servers[position]
        holder.nameText.text = buildString {
            append(server.name)
            if (server.isActive) append(" ✓")
        }
        holder.urlText.text = server.url
        holder.activateBtn.setOnClickListener { onActivate(server) }
        holder.manageSourcesBtn.setOnClickListener { onManageSources(server) }
        holder.editBtn.setOnClickListener { onEdit(server) }
        holder.deleteBtn.setOnClickListener { onDelete(server) }
    }

    override fun getItemCount() = servers.size

    class ViewHolder(
        view: View,
        val nameText: TextView,
        val urlText: TextView,
        val activateBtn: Button,
        val manageSourcesBtn: Button,
        val editBtn: Button,
        val deleteBtn: Button
    ) : RecyclerView.ViewHolder(view)
}
