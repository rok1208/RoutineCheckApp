package com.routinecheck.app.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 설치된 앱 하나의 표시용 정보 */
data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

/**
 * PackageManager를 감싸서 "설치된 앱 목록"을 제공하는 Repository.
 * ③ 앱 등록 화면에서 사용합니다.
 * 담당: 이현규
 *
 * Android 11+ 에서는 매니페스트의 <queries> 선언 범위 안의 앱만 조회 가능하니
 * 필요에 따라 AndroidManifest.xml의 <queries> 태그를 넓혀야 할 수 있습니다.
 */
@Singleton
class AppRegistryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // 시스템 앱 제외 (필요시 조정)
            .map { InstalledAppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label }
    }
}
