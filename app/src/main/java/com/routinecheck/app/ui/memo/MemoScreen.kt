package com.routinecheck.app.ui.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.repository.MemoRepository
import com.routinecheck.app.util.DateKey
import com.routinecheck.app.util.DayMask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class MemoUiState(val date: LocalDate, val text: String = "", val loaded: Boolean = false, val saved: Boolean = true)

@HiltViewModel
class MemoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MemoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        MemoUiState(date = runCatching { DateKey.parse(savedStateHandle.get<String>("date")!!) }.getOrDefault(LocalDate.now()))
    )
    val state: StateFlow<MemoUiState> = _state.asStateFlow()
    private var saveJob: Job? = null

    init { load(_state.value.date) }

    private fun load(date: LocalDate) {
        viewModelScope.launch {
            _state.value = MemoUiState(date = date, text = repository.getMemo(date), loaded = true)
        }
    }

    /** 입력할 때마다 0.6초 뒤 자동 저장 */
    fun onTextChange(text: String) {
        _state.value = _state.value.copy(text = text, saved = false)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            saveNow()
        }
    }

    fun changeDay(delta: Long) {
        viewModelScope.launch {
            saveNow()
            load(_state.value.date.plusDays(delta))
        }
    }

    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch { saveNow() }
    }

    private suspend fun saveNow() = withContext(NonCancellable) {
        val s = _state.value
        if (!s.loaded || s.saved) return@withContext
        repository.saveMemo(s.date, s.text)
        if (_state.value.date == s.date && _state.value.text == s.text) _state.value = _state.value.copy(saved = true)
    }

    override fun onCleared() {
        // 화면을 닫을 때 마지막 입력도 저장 (viewModelScope는 이미 취소되므로 별도 처리)
        val s = _state.value
        if (s.loaded && !s.saved) {
            kotlinx.coroutines.runBlocking { repository.saveMemo(s.date, s.text) }
        }
    }
}

/** 주간 달력에서 날짜를 누르면 열리는 메모지 (자동 저장) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoScreen(onBack: () -> Unit, viewModel: MemoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    val d = state.date
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${d.monthValue}월 ${d.dayOfMonth}일 (${DayMask.shortName(d.dayOfWeek)}) 메모") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.flush(); onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.changeDay(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "전날") }
                    IconButton(onClick = { viewModel.changeDay(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "다음날") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFFFF4C2), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                if (state.text.isEmpty()) {
                    Text("이날 기억할 일을 적어 두세요", color = Color(0xFF8A7B3A), style = MaterialTheme.typography.bodyLarge)
                }
                BasicTextField(
                    value = state.text,
                    onValueChange = viewModel::onTextChange,
                    enabled = state.loaded,
                    textStyle = TextStyle(color = Color(0xFF3B3415), fontSize = 17.sp, lineHeight = 26.sp),
                    cursorBrush = SolidColor(Color(0xFF3B3415)),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                if (state.saved) "저장됨" else "저장 중…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
