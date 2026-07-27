package com.vidhub.android.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.navigation.Router
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
            onActivate = { server -> viewModel.setActiveServer(server.id) }
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

        val cmsSourcesInput = EditText(context).apply {
            hint = "CMS 源地址（每行一个）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(existing?.cmsSources?.joinToString("\n") ?: "")
            minLines = 3
        }
        layout.addView(cmsSourcesInput)

        AlertDialog.Builder(context)
            .setTitle(if (existing == null) "添加服务器" else "编辑服务器")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()
                val cmsSources = cmsSourcesInput.text.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (name.isBlank() || url.isBlank()) {
                    Toast.makeText(context, "名称和地址不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing != null) {
                    viewModel.updateServer(existing.id, name, url, password, cmsSources)
                } else {
                    viewModel.addServer(name, url, password, cmsSources)
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
}

class ServerAdapter(
    private val onEdit: (ServerConfig) -> Unit,
    private val onDelete: (ServerConfig) -> Unit,
    private val onActivate: (ServerConfig) -> Unit
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
        val editBtn = Button(parent.context).apply { text = "编辑" }
        val deleteBtn = Button(parent.context).apply { text = "删除" }

        buttonLayout.addView(activateBtn)
        buttonLayout.addView(editBtn)
        buttonLayout.addView(deleteBtn)
        view.addView(nameText)
        view.addView(urlText)
        view.addView(buttonLayout)

        return ViewHolder(view, nameText, urlText, activateBtn, editBtn, deleteBtn)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val server = servers[position]
        holder.nameText.text = buildString {
            append(server.name)
            if (server.isActive) append(" ✓")
        }
        holder.urlText.text = server.url
        holder.activateBtn.setOnClickListener { onActivate(server) }
        holder.editBtn.setOnClickListener { onEdit(server) }
        holder.deleteBtn.setOnClickListener { onDelete(server) }
    }

    override fun getItemCount() = servers.size

    class ViewHolder(
        view: View,
        val nameText: TextView,
        val urlText: TextView,
        val activateBtn: Button,
        val editBtn: Button,
        val deleteBtn: Button
    ) : RecyclerView.ViewHolder(view)
}
