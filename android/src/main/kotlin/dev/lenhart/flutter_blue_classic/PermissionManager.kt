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
    private val callbackForRequestCode = HashMap<Int, ((Boolean, List<String>?) -> Unit)>()

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    private var pendingRequestCount = 0

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): Boolean {
        val callback = callbackForRequestCode.remove(requestCode) ?: return false

        val success =
            grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        val deniedPermissions = permissions.zip(grantResults.asIterable())
            .filter { it.second == PackageManager.PERMISSION_DENIED }
            .map { it.first }

        callback.invoke(success, deniedPermissions)
        return true
    }

    fun ensurePermissions(
        permissions: Array<String>,
        callback: ((Boolean, List<String>?) -> Unit)
    ) {
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

        val currentActivity = activity
        if (currentActivity == null) {
            callback.invoke(false, permissionsNeeded)
            return
        }

        val requestCode: Int = lastPermissionRequestCode
        lastPermissionRequestCode++
        callbackForRequestCode[requestCode] = callback
        requestPermissions(currentActivity, requestCode, permissionsNeeded.toTypedArray())
    }

    private fun requestPermissions(activity: Activity, requestCode: Int, permissions: Array<String>) {

        pendingRequestCount = permissions.size
        ActivityCompat.requestPermissions(
            activity,
            permissions,
            requestCode
        )
    }


}