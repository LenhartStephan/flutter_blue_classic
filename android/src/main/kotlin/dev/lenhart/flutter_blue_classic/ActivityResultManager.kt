package dev.lenhart.flutter_blue_classic

import android.content.Intent
import io.flutter.plugin.common.PluginRegistry

class ActivityResultManager : PluginRegistry.ActivityResultListener {
    private var resultCallback: ((Int, Int, Intent?) -> Unit)? = null

    fun setResultCallback(callback: ((Int, Int, Intent?) -> Unit)?) {
        this.resultCallback = callback
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        resultCallback?.invoke(requestCode, resultCode, data)
        return resultCallback != null
    }
}
