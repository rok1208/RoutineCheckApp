package com.routinecheck.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 설치된 앱 하나의 표시용 정보 */
data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

/**
 * 설치된 앱 목록 + 앱 실행.
 * 챗헤드로 "주요 앱을 모두 제어"하는 게 목표라, 시스템 앱이라도 런처에 아이콘이 있는 앱
 * (캘린더, 시계, 메시지, 크롬 등)은 모두 포함합니다.
 * Android 11+ 패키지 가시성: AndroidManifest의 <queries>(MAIN/LAUNCHER) 선언 필요 — 선언되어 있음.
 */
@Singleton
class AppRegistryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var cache: List<InstalledAppInfo>? = null

    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            cache?.takeIf { !forceRefresh }?.let { return@withContext it }
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            resolved
                .map { InstalledAppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .also { cache = it }
        }

    fun isInstalled(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    /** 앱 실행. 실행할 수 없으면(삭제됨 등) false */
    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
