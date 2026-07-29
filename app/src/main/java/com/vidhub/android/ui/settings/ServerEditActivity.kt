package com.vidhub.android.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.vidhub.android.R
import com.vidhub.android.data.local.ServerConfigStore
import com.vidhub.android.data.repository.VideoRepository
import com.vidhub.android.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 添加/编辑服务器。
 * 保存前通过 /api/env/password 校验密码；网络不可达时允许二次点击强制保存。
 */
@AndroidEntryPoint
class ServerEditActivity : FragmentActivity() {

    @Inject lateinit var serverConfigStore: ServerConfigStore
    @Inject lateinit var repository: VideoRepository

    private lateinit var titleView: TextView
    private lateinit var inputName: EditText
    private lateinit var inputUrl: EditText
    private lateinit var inputPassword: EditText
    private lateinit var statusView: TextView
    private lateinit var btnSave: Button

    /** 编辑中的服务器 ID；null 表示新增 */
    private var editingId: String? = null

    /** 网络错误后允许用户再次点击强制保存 */
    private var forceSaveArmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_edit)

        titleView = findViewById(R.id.edit_title)
        inputName = findViewById(R.id.input_name)
        inputUrl = findViewById(R.id.input_url)
        inputPassword = findViewById(R.id.input_password)
        statusView = findViewById(R.id.edit_status)
        btnSave = findViewById(R.id.btn_save)

        editingId = intent.getStringExtra(Constants.EXTRA_SERVER_ID)
        val editing = editingId?.let { serverConfigStore.getServer(it) }
        if (editing != null) {
            titleView.setText(R.string.server_edit_title_edit)
            inputName.setText(editing.name)
            inputUrl.setText(editing.url)
            inputPassword.setText(editing.password)
        } else {
            titleView.setText(R.string.server_edit_title_add)
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
        btnSave.setOnClickListener { onSaveClicked() }
    }

    private fun onSaveClicked() {
        val name = inputName.text.toString().trim()
        val url = inputUrl.text.toString().trim()
        val password = inputPassword.text.toString()

        if (name.isEmpty() || url.isEmpty() || password.isEmpty()) {
            showStatus(getString(R.string.server_field_required), isError = true)
            return
        }

        if (forceSaveArmed) {
            saveAndFinish(name, url, password)
            return
        }

        btnSave.isEnabled = false
        showStatus(getString(R.string.server_verify_testing), isError = false)

        lifecycleScope.launch {
            when (repository.verifyServer(url, password)) {
                VideoRepository.VerifyResult.OK -> {
                    showStatus(getString(R.string.server_verify_ok), isError = false)
                    saveAndFinish(name, url, password)
                }
                VideoRepository.VerifyResult.WRONG_PASSWORD -> {
                    showStatus(getString(R.string.server_verify_wrong_password), isError = true)
                    btnSave.isEnabled = true
                }
                VideoRepository.VerifyResult.NO_PASSWORD_ON_SERVER -> {
                    showStatus(getString(R.string.server_verify_no_password), isError = true)
                    btnSave.isEnabled = true
                }
                VideoRepository.VerifyResult.NETWORK_ERROR -> {
                    showStatus(
                        getString(R.string.server_verify_network_error) + "（再点一次「保存」强制添加）",
                        isError = true,
                    )
                    forceSaveArmed = true
                    btnSave.isEnabled = true
                }
            }
        }
    }

    private fun saveAndFinish(name: String, url: String, password: String) {
        val id = editingId
        if (id == null) {
            serverConfigStore.addServer(name, url, password)
        } else {
            val existing = serverConfigStore.getServer(id)
            if (existing != null) {
                serverConfigStore.updateServer(existing.copy(name = name, url = url, password = password))
            }
        }
        finish()
    }

    private fun showStatus(text: String, isError: Boolean) {
        statusView.visibility = View.VISIBLE
        statusView.text = text
        statusView.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                this,
                if (isError) R.color.error else R.color.active_server_indicator,
            )
        )
    }
}
