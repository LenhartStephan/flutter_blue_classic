package dev.lenhart.flutter_blue_classic

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.PluginRegistry


class PermissionManager(private var context: Context, private var activity: Activity?) :
    PluginRegistry.RequestPermissionsResultListener {

    companion object {
        const val REQUEST_ENABLE_BT = 1337
    }

    private var lastPermissionRequestCode = 1
    private val callbackForRequestCode = HashMap<Int, ((Boolean, List<String>) -> Unit)>()

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    private var pendingRequestCount = 0

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): Boolean {

        if (requestCode < lastPermissionRequestCode) {
            val success =
                grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            val deniedPermissions = permissions.zip(grantResults.asIterable())
                .filter { it.second == PackageManager.PERMISSION_DENIED }.map { it.first }
            val callback = callbackForRequestCode.remove(requestCode)
            callback?.invoke(success, deniedPermissions)

            return true
        }
        return false
    }

    fun ensurePermissions(
        permissions: Array<String>,
        callback: ((Boolean, List<String>?) -> Unit)
    ) {
        val requestCode: Int = lastPermissionRequestCode
        lastPermissionRequestCode++
        callbackForRequestCode[requestCode] = callback
        val permissionsNeeded: MutableList<String> = ArrayList()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(permission)
            }
        }

        if (permissionsNeeded.isEmpty()) {
            callback.invoke(true, null)
            return
        }

        requestPermissions(requestCode, permissionsNeeded.toTypedArray())
    }

    private fun requestPermissions(requestCode: Int, permissions: Array<String>) {

        pendingRequestCount = permissions.size
        activity?.let {
            ActivityCompat.requestPermissions(
                it,
                permissions,
                requestCode
            )
        }
    }


}