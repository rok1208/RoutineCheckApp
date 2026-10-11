package com.routinecheck.app.chathead

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.routinecheck.app.data.repository.Badge
import com.routinecheck.app.data.repository.ChatHeadStyle
import com.routinecheck.app.data.repository.EdgeSide
import com.routinecheck.app.domain.ChatHeadEntry
import com.routinecheck.app.ui.common.AppIcon
import com.routinecheck.app.ui.common.BadgeDot
import com.routinecheck.app.ui.theme.Brand
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** 챗헤드 오버레이 크기 상수 (서비스의 WindowManager 계산과 반드시 일치해야 함) */
object HeadDimens {
    val Bubble = 56.dp
    /** 원형 아이콘 창 크기 (진행 링/배지 여백 포함) */
    val HeadWindow = 64.dp
    val BarWidth = 24.dp
    val BarHeight = 88.dp
    val ItemHeight = 68.dp
    val CardWidth = 236.dp
    const val SIDE_ITEMS = 2
    val HeaderHeight = 40.dp
    val FooterHeight = 52.dp
    val ContainerWidth = 272.dp
}

/* ------------------------------------------------------------------ */
/* ③ 기본 원형 아이콘                                                    */
/* ------------------------------------------------------------------ */

@Composable
fun BubbleHead(state: OverlayState, modifier: Modifier = Modifier) {
    val progress = state.progress
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(HeadDimens.HeadWindow), contentAlignment = Alignment.Center) {
        // 진행 중 루틴이 있으면 원 둘레에 진행률 링
        if (progress != null) {
            Canvas(modifier = Modifier.size(HeadDimens.HeadWindow - 2.dp)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Color.White.copy(alpha = 0.55f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
                )
                drawArc(
                    color = primary,
                    startAngle = -90f, sweepAngle = 360f * progress.fraction, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        val icon = state.customIcon
        Box(
            modifier = Modifier
                .size(HeadDimens.Bubble - 4.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Brand.Mint, Brand.Blue))),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = "챗헤드",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Rounded.Apps, contentDescription = "챗헤드", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        state.totalBadge?.let { badge ->
            BadgeDot(badge, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp), minSize = 20.dp)
        }
    }
}

/* ------------------------------------------------------------------ */
/* ④ 가장자리 축소 바                                                    */
/* ------------------------------------------------------------------ */

@Composable
fun EdgeBar(state: OverlayState) {
    Box(modifier = Modifier.width(HeadDimens.BarWidth).height(HeadDimens.BarHeight)) {
        Box(
            modifier = Modifier
                .align(if (state.side == EdgeSide.RIGHT) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 3.dp)
                .width(7.dp)
                .height(68.dp)
                .background(Brand.Mint.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
        )
        if (state.totalBadge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(8.dp)
                    .background(Brand.Badge, CircleShape)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* 드래그 중 표시되는 제거 영역                                             */
/* ------------------------------------------------------------------ */

@Composable
fun TrashTarget(highlighted: Boolean) {
    val size by animateDpAsState(if (highlighted) 76.dp else 62.dp, label = "trash")
    Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .background(if (highlighted) Brand.Badge else Color(0xCC1C2630), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "챗헤드 제거", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

/* ------------------------------------------------------------------ */
/* ⑤⑥ 확장 화면                                                         */
/* ------------------------------------------------------------------ */

data class ExpandedActions(
    val onDismiss: () -> Unit,
    val onLaunch: (ChatHeadEntry) -> Unit,
    val onToggleCheck: (ChatHeadEntry) -> Unit,
    val onOpenApp: () -> Unit,
    val onManageApps: () -> Unit,
    val onOpenSettings: () -> Unit
)

@Composable
fun ExpandedPanel(state: OverlayState, actions: ExpandedActions) {
    val noRipple = remember { MutableInteractionSource() }
    Box(modifier = Modifier.fillMaxSize()) {
        // 배경을 어둡게 해서 챗헤드가 주인공이 되도록. 바깥을 누르면 닫힘
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brand.Scrim.copy(alpha = 0.45f))
                .clickable(interactionSource = noRipple, indication = null, onClick = actions.onDismiss)
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val anchorY = with(density) { state.anchorY.toDp() }
            val isCarousel = state.settings.uiStyle == ChatHeadStyle.CAROUSEL
            val bodyHeight = if (isCarousel) HeadDimens.ItemHeight * (HeadDimens.SIDE_ITEMS * 2 + 1)
            else containerHeight(state.entries.size)
            val totalHeight = HeadDimens.HeaderHeight + bodyHeight + HeadDimens.FooterHeight
            // 원형 아이콘이 원래 있던 높이에 오도록 배치 (화면 밖으로 나가지 않게 보정)
            val wantedTop = if (isCarousel) anchorY - HeadDimens.HeaderHeight - bodyHeight / 2
            else anchorY - HeadDimens.HeaderHeight - HeadDimens.HeadWindow / 2
            val top = wantedTop.coerceIn(24.dp, (maxHeight - totalHeight - 24.dp).coerceAtLeast(24.dp))

            val alignRight = state.side == EdgeSide.RIGHT
            Column(
                modifier = Modifier
                    .align(if (alignRight) Alignment.TopEnd else Alignment.TopStart)
                    .offset(y = top),
                horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
            ) {
                PanelHeader(state, alignRight)

                Row(verticalAlignment = if (isCarousel) Alignment.CenterVertically else Alignment.Top) {
                    val bubble: @Composable () -> Unit = {
                        BubbleHead(
                            state,
                            modifier = Modifier.clickable(interactionSource = noRipple, indication = null, onClick = actions.onDismiss)
                        )
                    }
                    if (!alignRight) bubble()
                    if (state.entries.isEmpty()) {
                        EmptyCard(actions.onManageApps, Modifier.width(HeadDimens.CardWidth))
                    } else if (isCarousel) {
                        WheelCarousel(state, alignRight, actions)
                    } else {
                        ContainerList(state, actions)
                    }
                    if (alignRight) bubble()
                }

                PanelFooter(alignRight, isCarousel, actions)
            }
        }
    }
}

private fun containerHeight(count: Int): Dp =
    (64.dp * min(count.coerceAtLeast(1), 6)) + 16.dp

@Composable
private fun PanelHeader(state: OverlayState, alignRight: Boolean) {
    Box(
        modifier = Modifier
            .height(HeadDimens.HeaderHeight)
            .padding(horizontal = if (alignRight) 72.dp else 72.dp),
        contentAlignment = Alignment.Center
    ) {
        val progress = state.progress
        Surface(shape = RoundedCornerShape(50), color = Color(0xE61C2630)) {
            Text(
                text = if (progress != null) "${progress.title}  ${progress.done}/${progress.total}"
                else "진행 중인 루틴 없음",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PanelFooter(alignRight: Boolean, isCarousel: Boolean, actions: ExpandedActions) {
    Column(
        modifier = Modifier
            .height(HeadDimens.FooterHeight)
            .padding(horizontal = 72.dp),
        horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FooterChip(Icons.Rounded.Home, "앱 열기", actions.onOpenApp)
            FooterChip(Icons.Rounded.Tune, "앱 관리", actions.onManageApps)
            FooterChip(Icons.Rounded.Settings, "설정", actions.onOpenSettings)
        }
        Text(
            text = if (isCarousel) "위아래로 돌려 고르고 가운데를 탭 · 길게 눌러 체크" else "탭해서 열기 · 길게 눌러 체크",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun FooterChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = Color(0xE61C2630), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/* ---------------- ⑤ 휠 캐러셀 (기본) ---------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelCarousel(state: OverlayState, alignRight: Boolean, actions: ExpandedActions) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val maxDistance = HeadDimens.SIDE_ITEMS + 0.5f

    // 뷰포트 가운데에 가장 가까운 카드 = 선택된 앱
    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: 0
        }
    }
    var firstTick by remember { mutableStateOf(true) }
    LaunchedEffect(centerIndex) {
        if (firstTick) firstTick = false
        else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // 휠이 한 칸 넘어갈 때 "틱"
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = HeadDimens.ItemHeight * HeadDimens.SIDE_ITEMS),
        modifier = Modifier
            .width(HeadDimens.CardWidth)
            .height(HeadDimens.ItemHeight * (HeadDimens.SIDE_ITEMS * 2 + 1))
    ) {
        itemsIndexed(state.entries, key = { _, e -> e.packageName }) { index, entry ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeadDimens.ItemHeight)
                    .graphicsLayer {
                        // 가운데에서 멀어질수록 작아지고 흐려짐 (회전목마 느낌)
                        val info = listState.layoutInfo
                        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        val distance = if (item == null) maxDistance
                        else (abs(item.offset + item.size / 2f - center) / item.size).coerceAtMost(maxDistance)
                        val scale = 1f - 0.15f * distance
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.33f * distance
                        transformOrigin = TransformOrigin(if (alignRight) 1f else 0f, 0.5f)
                    }
            ) {
                CarouselCard(
                    entry = entry,
                    badge = state.badges[entry.packageName],
                    selected = index == centerIndex,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (index == centerIndex) actions.onLaunch(entry)
                            else scope.launch { listState.animateScrollToItem(index) }
                        },
                        onLongClick = { actions.onToggleCheck(entry) }
                    )
                )
            }
        }
    }
}

@Composable
private fun CarouselCard(entry: ChatHeadEntry, badge: Badge?, selected: Boolean, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (selected) 8.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(modifier)
    ) {
        EntryRow(entry, badge, iconSize = 40.dp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/* ---------------- ⑥ 컨테이너 (대체 스타일) ---------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContainerList(state: OverlayState, actions: ExpandedActions) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .width(HeadDimens.ContainerWidth)
            .heightIn(max = containerHeight(state.entries.size))
    ) {
        LazyColumn(contentPadding = PaddingValues(8.dp)) {
            itemsIndexed(state.entries, key = { _, e -> e.packageName }) { _, entry ->
                EntryRow(
                    entry = entry,
                    badge = state.badges[entry.packageName],
                    iconSize = 36.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = { actions.onLaunch(entry) },
                            onLongClick = { actions.onToggleCheck(entry) }
                        )
                        .padding(horizontal = 10.dp)
                )
            }
        }
    }
}

/* ---------------- 공통 ---------------- */

@Composable
private fun EntryRow(entry: ChatHeadEntry, badge: Badge?, iconSize: Dp, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            AppIcon(entry.packageName, entry.label, iconSize)
            if (badge != null) {
                BadgeDot(badge, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (entry.isChecked) {
                    true -> "${entry.routineName} · 확인함"
                    false -> "${entry.routineName} · 확인 전"
                    null -> "고정 앱"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        CheckIndicator(entry.isChecked)
    }
}

@Composable
private fun CheckIndicator(checked: Boolean?) {
    when (checked) {
        null -> Unit
        true -> Box(
            modifier = Modifier.size(22.dp).background(Brand.Green, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Check, contentDescription = "확인함", tint = Color.White, modifier = Modifier.size(15.dp)) }
        false -> Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color.Transparent, CircleShape)
                .then(Modifier.alpha(0.6f))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color = Color.Gray, style = Stroke(2.dp.toPx()), radius = size.minDimension / 2 - 1.dp.toPx())
            }
        }
    }
}

@Composable
private fun EmptyCard(onManageApps: () -> Unit, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier.padding(4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("챗헤드에 띄울 앱이 없어요", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "자주 쓰는 앱을 추가하면 여기서 바로 열 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, onClick = onManageApps) {
                Text(
                    "앱 추가하기",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
