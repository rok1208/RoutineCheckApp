package com.routinecheck.app.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.routinecheck.app.MainActivity
import com.routinecheck.app.domain.AppLanguage
import com.routinecheck.app.domain.I18n
import java.util.Locale

/**
 * 앱 언어 설정 저장 · 적용 (v0.8.0)
 *
 * 앱 프로세스가 시작될 때(RoutineCheckApp.attachBaseContext) 저장된 언어를 I18n 에 적용합니다.
 * 언어를 바꾸면 앱을 다시 시작해서 화면 · 챗헤드 · 알림 문구가 모두 새 언어로 만들어지게 합니다.
 */
object LanguageSettings {
    private const val PREFS_NAME = "app_language"
    private const val KEY_LANGUAGE = "language"

    fun get(context: Context): AppLanguage =
        AppLanguage.fromCode(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        )

    /** 저장된 설정 + 휴대폰 언어로 현재 언어를 정함 */
    fun applySaved(context: Context) {
        I18n.apply(get(context), Locale.getDefault().language)
    }

    /** 언어를 저장하고 앱을 다시 시작 (챗헤드가 켜져 있었다면 앱이 다시 열릴 때 새 언어로 다시 뜸) */
    fun change(activity: Activity, language: AppLanguage) {
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language.code).commit()
        val intent = Intent(activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
