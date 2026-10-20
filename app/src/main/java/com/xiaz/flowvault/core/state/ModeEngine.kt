package com.xiaz.flowvault.core.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaz.flowvault.FlowVaultApp
import com.xiaz.flowvault.core.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ModeEngine(app: Application) : AndroidViewModel(app) {

    private val _mode = MutableStateFlow(GlobalMode.DAILY)
    val mode: StateFlow<GlobalMode> = _mode.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = store()?.get(KEY_MODE)
            GlobalMode.fromKey(stored)?.let { restored ->
                _mode.value = restored
                DebugLog.info("mode", "恢复上次的「${restored.label}」")
            }
        }
    }

    fun enter(next: GlobalMode) {
        if (next == _mode.value) return
        _mode.value = next
        DebugLog.info("mode", "切到「${next.label}」")
        viewModelScope.launch(Dispatchers.IO) {
            val saved = store()?.put(KEY_MODE, next.name) ?: false
            if (!saved) DebugLog.warn("mode", "「${next.label}」没能存下来，下次启动会回到默认态")
        }
    }

    private fun store() = (getApplication() as FlowVaultApp).settings()

    private companion object {
        const val KEY_MODE = "global_mode"
    }
}
