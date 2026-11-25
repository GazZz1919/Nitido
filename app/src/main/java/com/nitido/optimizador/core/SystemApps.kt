package com.nitido.optimizador.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings

/** Ayudas para consultar las apps instaladas. */
object SystemApps {

    @Suppress("DEPRECATION")
    fun installed(context: Context): List<ApplicationInfo> =
        try {
            context.packageManager.getInstalledApplications(0)
        } catch (e: Exception) {
            emptyList()
        }

    fun isSystem(info: ApplicationInfo): Boolean = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    fun label(context: Context, info: ApplicationInfo): String =
        try {
            info.loadLabel(context.packageManager).toString()
        } catch (e: Exception) {
            info.packageName
        }

    @Suppress("DEPRECATION")
    fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    @Suppress("DEPRECATION")
    fun firstInstallTime(context: Context, packageName: String): Long =
        try {
            context.packageManager.getPackageInfo(packageName, 0).firstInstallTime
        } catch (e: Exception) {
            0L
        }

    /** Launcher predeterminado: nunca se cierra ni se propone desinstalar. */
    @Suppress("DEPRECATION")
    fun defaultLauncher(context: Context): String? =
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        } catch (e: Exception) {
            null
        }

    /** Teclado predeterminado: tampoco se toca. */
    fun defaultKeyboard(context: Context): String? =
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
        } catch (e: Exception) {
            null
        }

    /** Apps que nunca deben cerrarse ni proponerse para desinstalar. */
    fun protectedPackages(context: Context): Set<String> =
        setOfNotNull(context.packageName, defaultLauncher(context), defaultKeyboard(context))
}
