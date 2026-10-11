package com.routinecheck.app.ui.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.repository.MemoRepository
import com.routinecheck.app.domain.DateKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class MemoItem(val date: LocalDate, val content: String, val updatedAt: Long)

/** 지금 고치고 있는 메모 (한 번에 하나) */
data class EditingMemo(val date: LocalDate, val text: String, val saved: Boolean = true)

data class MemosUiState(
    val loaded: Boolean = false,
    val query: String = "",
    /** 검색어로 거른 목록 (최근 날짜부터) */
    val items: List<MemoItem> = emptyList(),
    /** 전체 메모 수 (검색과 상관없이) */
    val total: Int = 0,
    val editing: EditingMemo? = null
)

/**
 * 전체 메모 화면: 작성한 모든 메모를 날짜순으로 보고, 검색 · 바로 고치기 · 삭제.
 * 저장 방식은 홈 메모지와 같음 (입력 0.6초 뒤 자동 저장, 내용을 모두 지우면 메모 삭제).
 */
@HiltViewModel
class MemosViewModel @Inject constructor(
    private val repository: MemoRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val editing = MutableStateFlow<EditingMemo?>(null)
    private var saveJob: Job? = null

    val state: StateFlow<MemosUiState> = combine(repository.observeAll(), query, editing) { rows, q, edit ->
        var items = rows.map { MemoItem(DateKey.parse(it.date), it.content, it.updatedAt) }
        // 고치는 중에 내용을 다 지워서 DB에서 빠져도 카드는 그대로 둠
        if (edit != null && items.none { it.date == edit.date }) {
            items = (items + MemoItem(edit.date, edit.text, System.currentTimeMillis())).sortedByDescending { it.date }
        }
        val needle = q.trim()
        val filtered = if (needle.isEmpty()) items
        else items.filter { it.date == edit?.date || it.content.contains(needle, ignoreCase = true) }
        MemosUiState(loaded = true, query = q, items = filtered, total = rows.size, editing = edit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemosUiState())

    fun setQuery(value: String) { query.value = value }

    fun startEdit(item: MemoItem) {
        if (editing.value?.date == item.date) return
        flush()
        editing.value = EditingMemo(item.date, item.content)
    }

    fun onEditChange(text: String) {
        editing.update { it?.copy(text = text, saved = false) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            saveNow()
        }
    }

    fun stopEdit() {
        flush()
        editing.value = null
    }

    fun delete(date: LocalDate) {
        if (editing.value?.date == date) {
            saveJob?.cancel()
            editing.value = null
        }
        viewModelScope.launch { repository.deleteMemo(date) }
    }

    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch { saveNow() }
    }

    private suspend fun saveNow() = withContext(NonCancellable) {
        val e = editing.value ?: return@withContext
        if (e.saved) return@withContext
        repository.saveMemo(e.date, e.text)
        editing.update { if (it?.date == e.date && it.text == e.text) it.copy(saved = true) else it }
    }

    override fun onCleared() {
        val e = editing.value
        if (e != null && !e.saved) runBlocking { repository.saveMemo(e.date, e.text) }
    }
}
