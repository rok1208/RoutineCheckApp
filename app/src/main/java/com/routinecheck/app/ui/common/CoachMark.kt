package com.routinecheck.app.ui.common

import androidx.compose.animation.core.animateRectAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import android.content.SharedPreferences
import kotlinx.coroutines.delay

/**
 * 실제 화면 코치마크 가이드 (v0.6.0).
 *
 * 화면을 어둡게 덮고 설명할 버튼·영역만 밝게 뚫어 보여주면서 말풍선으로 하나씩 설명합니다.
 *  - 설명할 대상: `Modifier.coachTarget("home.chathead")` 로 위치를 등록
 *  - 화면별 가이드: `CoachTour(id = "home", steps = ...)` — 그 화면을 처음 열 때 한 번만 시작
 *  - 본 가이드는 SharedPreferences("coach_marks")에 기록, 설정의 "가이드 다시 보기"로 초기화
 *
 * 대상이 화면 밖이면 BringIntoViewRequester 로 스크롤해서 보여주고,
 * 대상을 찾지 못하면(목록이 비어 있는 등) 말풍선만 화면 가운데에 띄웁니다.
 */
data class CoachStep(
    /** 강조할 대상 키. null 이면 강조 없이 가운데 안내 */
    val target: String?,
    val title: String,
    val body: String,
    /** 이 단계를 보여주기 전에 할 일 (예: 목록을 대상 위치까지 스크롤) */
    val prepare: (suspend () -> Unit)? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Stable
class CoachMarkState(private val prefs: SharedPreferences) {
    /** 대상 키 → 창 기준 위치 */
    internal val targets = mutableStateMapOf<String, Rect>()
    internal val requesters = mutableMapOf<String, BringIntoViewRequester>()

    var tourId by mutableStateOf<String?>(null)
        private set
    var steps by mutableStateOf<List<CoachStep>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set

    /** "가이드 다시 보기"를 누를 때마다 1씩 증가 → 각 화면의 가이드가 다시 시작됨 */
    var resetVersion by mutableIntStateOf(0)
        private set

    val current: CoachStep? get() = if (tourId == null) null else steps.getOrNull(index)
    val isActive: Boolean get() = tourId != null

    fun isSeen(id: String): Boolean = prefs.getBoolean(KEY_PREFIX + id, false)

    /** 아직 안 본 가이드면 시작. 다른 가이드가 진행 중이면 시작하지 않음 */
    fun start(id: String, tourSteps: List<CoachStep>): Boolean {
        if (tourId != null || isSeen(id) || tourSteps.isEmpty()) return false
        steps = tourSteps
        index = 0
        tourId = id
        return true
    }

    fun next() {
        if (index < steps.lastIndex) index++ else finish()
    }

    fun prev() {
        if (index > 0) index--
    }

    /** 끝내기·건너뛰기: 다시 자동으로 뜨지 않게 기록 */
    fun finish() {
        tourId?.let { prefs.edit().putBoolean(KEY_PREFIX + it, true).apply() }
        tourId = null
        steps = emptyList()
        index = 0
    }

    /** 모든 화면의 가이드를 처음 상태로 */
    fun resetAll() {
        prefs.edit().clear().apply()
        tourId = null
        steps = emptyList()
        index = 0
        resetVersion++
    }

    companion object {
        const val PREFS_NAME = "coach_marks"
        private const val KEY_PREFIX = "seen_"
    }
}

/** 앱 전체에서 하나의 코치마크 상태를 공유 (AppRoot 에서 제공) */
val LocalCoachMarks = staticCompositionLocalOf<CoachMarkState?> { null }

/** 코치마크로 설명할 대상으로 등록 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.coachTarget(key: String): Modifier = composed {
    val state = LocalCoachMarks.current ?: return@composed this
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(key) {
        state.requesters[key] = requester
        onDispose {
            state.targets.remove(key)
            state.requesters.remove(key)
        }
    }
    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { state.targets[key] = it.boundsInWindow() }
}

/**
 * 화면별 가이드 시작. 화면을 처음 열었을 때(또는 "가이드 다시 보기" 뒤) 한 번 시작하고,
 * 화면을 벗어나면 진행 중이던 그 화면의 가이드는 본 것으로 처리하고 닫습니다.
 */
@Composable
fun CoachTour(id: String, steps: List<CoachStep>, enabled: Boolean = true) {
    val state = LocalCoachMarks.current ?: return
    val latestSteps by rememberUpdatedState(steps)
    LaunchedEffect(id, state.resetVersion, enabled) {
        if (!enabled) return@LaunchedEffect
        delay(450) // 화면 배치·첫 스크롤이 끝난 뒤 시작
        state.start(id, latestSteps)
    }
    DisposableEffect(id) {
        onDispose { if (state.tourId == id) state.finish() }
    }
}

/** 앱 화면 전체를 감싸고, 가이드가 진행 중이면 그 위에 코치마크를 그림 */
@Composable
fun CoachMarkHost(state: CoachMarkState, content: @Composable () -> Unit) {
    var hostOffset by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalCoachMarks provides state) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { hostOffset = it.positionInWindow() }) {
            content()
            val step = state.current
            if (step != null) {
                CoachOverlay(state = state, step = step, hostOffset = hostOffset)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoachOverlay(state: CoachMarkState, step: CoachStep, hostOffset: Offset) {
    val density = LocalDensity.current
    var ready by remember(state.tourId, state.index) { mutableStateOf(false) }

    LaunchedEffect(state.tourId, state.index) {
        runCatching { step.prepare?.invoke() }
        step.target?.let { key -> runCatching { state.requesters[key]?.bringIntoView() } }
        delay(220) // 스크롤 뒤 위치가 갱신될 시간
        ready = true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val pad = with(density) { 8.dp.toPx() }
        val raw = step.target?.let { state.targets[it] }?.translate(-hostOffset)
        // 화면 안에 보이는 대상만 강조
        val visible = raw?.takeIf { ready && it.width > 0f && it.bottom > 0f && it.top < height && it.right > 0f && it.left < width }
        val hole = visible?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
        val animated by animateRectAsState(hole ?: Rect(width / 2, height / 2, width / 2, height / 2), label = "coachHole")
        val showHole = hole != null
        val ring = MaterialTheme.colorScheme.primary

        // 어두운 막 + 강조 구멍. 아래 화면은 눌리지 않게 모든 터치를 소비
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
        ) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            if (showHole) {
                val r = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                drawRoundRect(Color.Transparent, animated.topLeft, animated.size, r, blendMode = BlendMode.Clear)
                drawRoundRect(ring, animated.topLeft, animated.size, r, style = Stroke(width = 2.dp.toPx()))
            }
        }

        if (ready) {
            val margin = with(density) { 16.dp.toPx() }
            val gap = with(density) { 12.dp.toPx() }
            val topInset = WindowInsets.systemBars.getTop(density).toFloat()
            val bottomInset = WindowInsets.systemBars.getBottom(density).toFloat()
            val maxCard = with(density) { 360.dp.roundToPx() }
            Layout(
                content = {
                    CoachBubble(
                        step = step,
                        position = state.index + 1,
                        total = state.steps.size,
                        onPrev = state::prev,
                        onNext = state::next,
                        onSkip = state::finish
                    )
                }
            ) { measurables, constraints ->
                val cardWidth = minOf(constraints.maxWidth - (margin * 2).toInt(), maxCard)
                val p = measurables.first().measure(Constraints(maxWidth = cardWidth.coerceAtLeast(0)))
                val minY = topInset + margin
                val maxY = height - bottomInset - margin - p.height
                val (x, y) = if (hole == null) {
                    (width - p.width) / 2 to (height - p.height) / 2
                } else {
                    val cx = (hole.center.x - p.width / 2).coerceIn(margin, (width - margin - p.width).coerceAtLeast(margin))
                    val below = hole.bottom + gap
                    val above = hole.top - gap - p.height
                    // 아래 공간이 넉넉하면 아래, 아니면 위. 둘 다 안 되면 화면 안으로 맞춤
                    val cy = when {
                        below <= maxY -> below
                        above >= minY -> above
                        else -> maxY
                    }
                    cx to cy.coerceIn(minY, maxY.coerceAtLeast(minY))
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    p.place(x.toInt(), y.toInt())
                }
            }
        }
    }
}

@Composable
private fun CoachBubble(
    step: CoachStep,
    position: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(Modifier.padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                "$position / $total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                step.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp)
            )
            Text(
                step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp)
            )
            // 진행 점
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 12.dp)) {
                repeat(total) { i ->
                    val c = if (i == position - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    Box(
                        Modifier
                            .size(width = if (i == position - 1) 14.dp else 6.dp, height = 6.dp)
                            .background(c, RoundedCornerShape(3.dp))
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                if (position < total) TextButton(onClick = onSkip) { Text("건너뛰기") }
                Spacer(Modifier.weight(1f))
                if (position > 1) TextButton(onClick = onPrev) { Text("이전") }
                Button(onClick = onNext, modifier = Modifier.padding(start = 4.dp)) {
                    Text(if (position < total) "다음" else "알겠어요")
                }
            }
        }
    }
}
