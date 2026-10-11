package com.routinecheck.app.ui.common

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.routinecheck.app.data.repository.Badge
import com.routinecheck.app.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PackageManager 아이콘 로딩은 느려서 메모리 캐시를 둡니다 (앱 화면·챗헤드 공용) */
object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(160)

    fun peek(packageName: String, sizePx: Int): ImageBitmap? = cache.get("$packageName@$sizePx")

    suspend fun load(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        val key = "$packageName@$sizePx"
        val cached = cache.get(key)
        if (cached != null) return cached
        return withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }
}

/** 설치된 앱 아이콘. 못 불러오면 이름 첫 글자로 대체 */
@Composable
fun AppIcon(
    packageName: String,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(AppIconCache.peek(packageName, sizePx), packageName, sizePx) {
        if (value == null) value = AppIconCache.load(context, packageName, sizePx)
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = label, modifier = modifier.size(size))
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(size / 4)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.take(1),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 빨간 알림 배지 (숫자 또는 "!") */
@Composable
fun BadgeDot(badge: Badge, modifier: Modifier = Modifier, minSize: Dp = 18.dp) {
    Box(
        modifier = modifier
            .size(minSize)
            .background(Brand.Badge, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badge.text,
            color = Color.White,
            fontSize = if (badge.text.length > 2) 8.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
