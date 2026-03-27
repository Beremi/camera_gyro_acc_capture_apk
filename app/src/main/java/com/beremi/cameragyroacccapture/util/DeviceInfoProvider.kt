package com.beremi.cameragyroacccapture.util

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.beremi.cameragyroacccapture.session.DeviceInfoManifest

object DeviceInfoProvider {
    fun capture(context: Context): DeviceInfoManifest {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return DeviceInfoManifest(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            apiLevel = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            appVersionName = packageInfo.versionName ?: "0.0.0",
            appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        )
    }
}

