package com.routinecheck.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.repository.AppRegistryRepository
import com.routinecheck.app.data.repository.InstalledAppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val appRegistry: AppRegistryRepository
) : ViewModel() {
    private val _apps = MutableStateFlow<List<InstalledAppInfo>?>(null)
    val apps: StateFlow<List<InstalledAppInfo>?> = _apps.asStateFlow()

    init {
        viewModelScope.launch { _apps.value = appRegistry.getInstalledApps(forceRefresh = true) }
    }
}

/**
 * ② 앱 등록 — 설치된 앱 검색/다중 선택 (루틴 편집, 챗헤드 앱 관리에서 공용).
 * 확인을 누르면 기존에 고른 앱은 원래 순서를 지키고, 새로 고른 앱은 뒤에 붙습니다.
 */
@Composable
fun AppPickerDialog(
    title: String,
    initialSelected: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (List<InstalledAppInfo>) -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel()
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initialSelected.map { it.packageName }) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "닫기") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        val byPkg = (apps.orEmpty() + initialSelected).associateBy { it.packageName }
                        onConfirm(selected.mapNotNull { byPkg[it] })
                    }) { Text("${selected.size}개 선택") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("앱 이름 검색") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val filtered = remember(list, query) {
                        if (query.isBlank()) list else list.filter { it.label.contains(query.trim(), ignoreCase = true) }
                    }
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            val isSelected = app.packageName in selected
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selected.remove(app.packageName) else selected.add(app.packageName)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                AppIcon(app.packageName, app.label, 40.dp)
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(checked = isSelected, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
