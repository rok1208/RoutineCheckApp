package com.routinecheck.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routinecheck.app.BuildConfig
import com.routinecheck.app.data.repository.AlertMode
import com.routinecheck.app.data.repository.ChatHeadSettingsRepository
import com.routinecheck.app.data.repository.ChatHeadStyle
import com.routinecheck.app.data.repository.EdgeSide
import com.routinecheck.app.data.repository.OnCompleteBehavior
import com.routinecheck.app.ui.Brand
import com.routinecheck.app.ui.common.ChatHeadPreview
import com.routinecheck.app.ui.common.SectionHeader
import com.routinecheck.app.ui.common.enableChatHead
import com.routinecheck.app.ui.common.rememberPermissionRequester
import androidx.compose.material.icons.rounded.School

/** ⑦ 챗헤드 설정 + 권한 상태 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageChatHeadItems: () -> Unit,
    /** v0.6.0: 모든 화면의 코치마크 가이드를 처음부터 다시 보기 */
    onReplayGuide: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val permissionRequester = rememberPermissionRequester()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setIcon(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로") }
                }
            )
        }
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("챗헤드 사용", style = MaterialTheme.typography.titleMedium)
                    Text("끄면 화면에서 사라지고, 재부팅 후에도 다시 뜨지 않아요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = s.enabled,
                    onCheckedChange = { on ->
                        // 켤 때는 "다른 앱 위에 표시" 권한부터 확인
                        if (on) permissionRequester.enableChatHead { viewModel.setEnabled(true) } else viewModel.setEnabled(false)
                    }
                )
            }

            SectionHeader("펼침 방식")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatHeadStyle.values().forEach { style ->
                    StyleCard(
                        style = style,
                        selected = s.uiStyle == style,
                        onClick = { viewModel.setStyle(style) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SectionHeader("챗헤드 모양")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatHeadPreview(s.iconUri, 56.dp, opacity = s.opacity)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("투명도 ${(s.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    var sliderValue by remember(s.opacity) { mutableFloatStateOf(s.opacity) }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { viewModel.setOpacity(sliderValue) },
                        valueRange = ChatHeadSettingsRepository.MIN_OPACITY..ChatHeadSettingsRepository.MAX_OPACITY
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("이미지 변경") }
                if (s.iconUri != null) TextButton(onClick = { viewModel.resetIcon() }) { Text("기본 아이콘으로") }
            }

            SectionHeader("가장자리 바 위치")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EdgeSide.values().forEachIndexed { index, side ->
                    SegmentedButton(
                        selected = s.edgeSide == side,
                        onClick = { viewModel.setEdgeSide(side) },
                        shape = SegmentedButtonDefaults.itemShape(index, EdgeSide.values().size)
                    ) { Text(side.label) }
                }
            }
            Text(
                "챗헤드를 화면 끝까지 밀면 얇은 바로 줄어들고, 바를 화면 안쪽으로 밀면 다시 원이 돼요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            SectionHeader("루틴을 모두 확인했을 때")
            OnCompleteBehavior.values().forEach { behavior ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = s.onCompleteBehavior == behavior, onClick = { viewModel.setOnComplete(behavior) })
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(selected = s.onCompleteBehavior == behavior, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(behavior.label)
                }
            }

            SectionHeader("챗헤드 항목")
            OutlinedCard(onClick = onManageChatHeadItems, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("앱 · 웹사이트 · 폴더 관리", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }

            SectionHeader("루틴 알림 방식")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AlertMode.values().forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = s.alertMode == mode,
                        onClick = { viewModel.setAlertMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, AlertMode.values().size)
                    ) { Text(mode.label) }
                }
            }
            Text(
                "루틴 시작 시각에 울리는 알림의 방식이에요. 알림을 받을지는 루틴마다 \"시작 알림\"에서 고를 수 있고, " +
                    "휴대폰이 무음·진동 모드면 휴대폰 설정이 먼저 적용돼요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            SectionHeader("가이드")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReplayGuide)
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("가이드 다시 보기", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "홈·루틴·통계 등 각 화면을 처음 열 때 나오는 기능 설명을 다시 보여드려요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }

            SectionHeader("권한")
            Text(
                "꼭 필요한 권한은 '다른 앱 위에 표시' 하나예요. 나머지는 그 기능을 쓸 때 물어보고, 여기서 언제든 바꿀 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            permissions.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { runCatching { context.startActivity(item.intent) } }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        if (item.granted) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = when {
                            item.granted -> Brand.Green
                            item.required -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title + if (item.required) " (필수)" else " (권장)", style = MaterialTheme.typography.bodyLarge)
                        Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (item.granted) "허용됨" else "설정", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }

            Text(
                "루틴 체크 v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }
}

/** 캐러셀 / 컨테이너를 고르는 카드 (작은 그림으로 차이를 보여줌) */
@Composable
private fun StyleCard(style: ChatHeadStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (style == ChatHeadStyle.CAROUSEL) CarouselSketch() else ContainerSketch()
            }
            Spacer(Modifier.height(8.dp))
            Text(style.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CarouselSketch() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(0.55f, 0.8f, 1f, 0.8f, 0.55f).forEach { scale ->
                Box(
                    Modifier
                        .width(64.dp)
                        .height(14.dp)
                        .scale(scale)
                        .alpha(scale)
                        .background(
                            if (scale == 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(5.dp)
                        )
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(18.dp).background(Brand.Blue, CircleShape))
    }
}

@Composable
private fun ContainerSketch() {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            Modifier
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(4) {
                Box(Modifier.width(58.dp).height(11.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp)))
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(18.dp).background(Brand.Blue, CircleShape))
    }
}
