package com.routinecheck.app.chathead

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/** 앱 화면·리시버 어디서든 챗헤드를 켜고 끌 때 쓰는 진입점 */
object ChatHeadController {
    const val ACTION_SHOW = "com.routinecheck.app.chathead.SHOW"
    const val ACTION_EXPAND = "com.routinecheck.app.chathead.EXPAND"
    const val ACTION_STOP = "com.routinecheck.app.chathead.STOP"

    /** 오버레이 권한이 없으면 아무것도 하지 않고 false */
    fun show(context: Context, expand: Boolean = false): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        val intent = Intent(context, ChatHeadService::class.java)
            .setAction(if (expand) ACTION_EXPAND else ACTION_SHOW)
        return runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { Log.w("ChatHead", "챗헤드 서비스 시작 실패", it) }
            .isSuccess
    }

    fun hide(context: Context) {
        context.stopService(Intent(context, ChatHeadService::class.java))
    }
}
