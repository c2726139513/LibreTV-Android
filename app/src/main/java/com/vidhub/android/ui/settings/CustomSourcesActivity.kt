package com.vidhub.android.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.vidhub.android.R
import com.vidhub.android.data.local.ServerConfigStore
import com.vidhub.android.model.ApiSource
import com.vidhub.android.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

/**
 * 自定义数据源管理：为某台服务器增删用户自定义 CMS 源。
 * 带"网页详情地址"的源在请求详情时走服务端网页抓取模式（customDetail 参数）。
 */
@AndroidEntryPoint
class CustomSourcesActivity : FragmentActivity() {

    @Inject lateinit var serverConfigStore: ServerConfigStore

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView

    private var serverId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_sources)

        val id = intent.getStringExtra(Constants.EXTRA_SERVER_ID)
        val server = id?.let { serverConfigStore.getServer(it) }
        if (id == null || server == null) {
            finish()
            return
        }
        serverId = id

        findViewById<TextView>(R.id.custom_sources_title).text =
            "${getString(R.string.custom_sources_title)} · ${server.name}"
        listContainer = findViewById(R.id.custom_sources_list)
        emptyView = findViewById(R.id.custom_sources_empty)

        findViewById<Button>(R.id.btn_add_source).setOnClickListener { onAddClicked() }

        renderList()
    }

    private fun onAddClicked() {
        val name = findViewById<EditText>(R.id.input_source_name).text.toString().trim()
        val api = findViewById<EditText>(R.id.input_source_api).text.toString().trim()
        val detail = findViewById<EditText>(R.id.input_source_detail).text.toString().trim()

        if (name.isEmpty() || api.isEmpty()) {
            Toast.makeText(this, getString(R.string.server_field_required), Toast.LENGTH_SHORT).show()
            return
        }

        serverConfigStore.addCustomSource(
            serverId!!,
            ApiSource(
                key = "custom_${UUID.randomUUID()}",
                name = name,
                api = api,
                isCustom = true,
                detailUrl = detail.takeIf { it.isNotBlank() },
            ),
        )

        findViewById<EditText>(R.id.input_source_name).text.clear()
        findViewById<EditText>(R.id.input_source_api).text.clear()
        findViewById<EditText>(R.id.input_source_detail).text.clear()

        renderList()
    }

    private fun renderList() {
        val id = serverId ?: return
        val sources = serverConfigStore.getCustomSources(id)
        listContainer.removeAllViews()
        emptyView.visibility = if (sources.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        sources.forEach { source ->
            val itemView = inflater.inflate(R.layout.item_custom_source, listContainer, false)
            itemView.findViewById<TextView>(R.id.source_name).text = source.name
            itemView.findViewById<TextView>(R.id.source_api).text =
                source.api + (source.detailUrl?.let { "\n详情：$it" } ?: "")
            itemView.findViewById<Button>(R.id.btn_delete_source).setOnClickListener {
                serverConfigStore.removeCustomSource(id, source.key)
                renderList()
            }
            listContainer.addView(itemView)
        }
    }
}
