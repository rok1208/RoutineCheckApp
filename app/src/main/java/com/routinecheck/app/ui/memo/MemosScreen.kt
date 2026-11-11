package com.routinecheck.app.ui.memo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routinecheck.app.domain.DayMask
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.routinecheck.app.domain.tr

private val Paper = Color(0xFFFFF4C9)
private val Ink = Color(0xFF3B3320)

/** 전체 메모: 작성한 모든 메모를 월별로 모아 보고, 검색 · 바로 고치기 · 삭제 · 홈에서 그 날짜 열기 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemosScreen(
    onBack: () -> Unit,
    onOpenInHome: (LocalDate) -> Unit,
    viewModel: MemosViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<MemoItem?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flush() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr("전체 메모"))
                        if (state.loaded) {
                            Text(
                                tr("{0}개", state.total),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.stopEdit(); onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("뒤로"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(tr("메모 내용 검색")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = tr("검색어 지우기")) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            if (state.loaded && state.items.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (state.query.isBlank()) tr("아직 작성한 메모가 없어요. 홈의 주간 달력에서 날짜를 눌러 메모를 남겨 보세요.")
                        else tr("'{0}'이(가) 들어간 메모가 없어요.", state.query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }

            // 월별로 묶어서 표시
            state.items.groupBy { it.date.year to it.date.monthValue }.forEach { (ym, monthItems) ->
                item(key = "h-${ym.first}-${ym.second}") {
                    Text(
                        tr("{0}년 {1}월", ym.first, ym.second),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                items(monthItems, key = { it.date.toString() }) { item ->
                    val editing = state.editing?.takeIf { it.date == item.date }
                    MemoCard(
                        item = item,
                        editing = editing,
                        onStartEdit = { viewModel.startEdit(item) },
                        onChange = viewModel::onEditChange,
                        onDone = viewModel::stopEdit,
                        onOpenInHome = {
                            viewModel.stopEdit()
                            onOpenInHome(item.date)
                        },
                        onDelete = { confirmDelete = item }
                    )
                }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(tr("{0} 메모를 삭제할까요?", dateLabel(item.date))) },
            text = { Text(tr("삭제한 메모는 되돌릴 수 없어요.")) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(item.date); confirmDelete = null }) {
                    Text(tr("삭제"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("취소")) } }
        )
    }
}

@Composable
private fun MemoCard(
    item: MemoItem,
    editing: EditingMemo?,
    onStartEdit: () -> Unit,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    onOpenInHome: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateLabel(item.date),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                relativeLabel(item.date)?.let {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = Ink.copy(alpha = 0.08f)) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenInHome) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = tr("홈에서 이 날짜 보기"), tint = Ink)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = tr("메모 삭제"), tint = Ink.copy(alpha = 0.7f))
                }
            }

            if (editing == null) {
                Text(
                    item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp)
                        .clickable(onClick = onStartEdit)
                )
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                BasicTextField(
                    value = editing.text,
                    onValueChange = onChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                    cursorBrush = SolidColor(Ink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp)
                        .heightIn(min = 96.dp)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (editing.text.isEmpty()) {
                                Text(
                                    tr("내용을 모두 지우면 이 메모는 삭제돼요"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink.copy(alpha = 0.4f)
                                )
                            }
                            inner()
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                    Text(
                        if (editing.saved) tr("저장됨") else tr("저장 중…"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDone) { Text(tr("완료"), color = Ink) }
                }
            }
        }
    }
}

/** "10월 9일 (금)" */
private fun dateLabel(date: LocalDate): String =
    tr("{0}월 {1}일 ({2})", date.monthValue, date.dayOfMonth, DayMask.shortName(date.dayOfWeek))

/** 오늘 · 어제 · 내일 · N일 전 · N일 뒤 (2주 넘게 차이 나면 표시 안 함) */
private fun relativeLabel(date: LocalDate): String? {
    val diff = ChronoUnit.DAYS.between(LocalDate.now(), date)
    return when {
        diff == 0L -> tr("오늘")
        diff == -1L -> tr("어제")
        diff == 1L -> tr("내일")
        diff in -14L..-2L -> tr("{0}일 전", -diff)
        diff in 2L..14L -> tr("{0}일 뒤", diff)
        else -> null
    }
}
