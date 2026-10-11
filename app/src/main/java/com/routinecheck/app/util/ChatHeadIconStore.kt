package com.routinecheck.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 사용자가 고른 챗헤드 이미지를 앱 내부 저장소에 정사각형 256px PNG로 복사해 둡니다.
 * (갤러리 Uri는 나중에 권한이 사라질 수 있어서, 복사본 경로를 DB에 저장)
 */
object ChatHeadIconStore {
    private const val FILE_NAME = "chathead_icon.png"
    private const val SIZE = 256

    /** @return 저장된 파일의 file:// 경로, 실패하면 null */
    fun save(context: Context, source: Uri): String? = runCatching {
        val original = context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val side = minOf(original.width, original.height)
        val cropped = Bitmap.createBitmap(
            original, (original.width - side) / 2, (original.height - side) / 2, side, side
        )
        val scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
        val file = File(context.filesDir, FILE_NAME)
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 같은 경로를 덮어쓰므로, 변경을 감지할 수 있게 쿼리에 시각을 붙입니다
        Uri.fromFile(file).toString() + "?v=" + System.currentTimeMillis()
    }.getOrNull()

    fun load(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val path = Uri.parse(uriString).path ?: return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    fun delete(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
