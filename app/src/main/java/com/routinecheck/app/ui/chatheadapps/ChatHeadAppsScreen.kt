package com.routinecheck.app.ui.chatheadapps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.local.entity.ChatHeadAppEntity
import com.routinecheck.app.data.repository.ChatHeadAppRepository
import com.routinecheck.app.data.repository.InstalledAppInfo
import com.routinecheck.app.ui.apppicker.AppPickerDialog
import com.routinecheck.app.ui.common.AppIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatHeadAppsViewModel @Inject constructor(
    private val repository: ChatHeadAppRepository
) : ViewModel() {
    val apps: StateFlow<List<ChatHeadAppEntity>> = repository.observeApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(apps: List<InstalledAppInfo>) = viewModelScope.launch { repository.saveApps(apps) }
    fun remove(packageName: String) = viewModelScope.launch { repository.remove(packageName) }
    fun move(packageName: String, delta: Int) = viewModelScope.launch { repository.move(packageName, delta) }
}

/** 챗헤드 전용 앱 관리: 루틴과 상관없이 항상 챗헤드에 뜨는 앱과 그 순서 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeadAppsScreen(
    onBack: () -> Unit,
    viewModel: ChatHeadAppsViewModel = hiltViewModel()
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("챗헤드 앱") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("앱 추가") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        "챗헤드를 펼치면 ① 진행 중인 루틴의 앱(확인 전 → 확인함) ② 이 목록의 앱 순서로 보여요.\n" +
                            "같은 앱이 루틴에도 있으면 루틴 쪽에 한 번만 보입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            if (apps.isEmpty()) {
                item {
                    Text(
                        "아직 등록된 앱이 없어요. 메신저, 캘린더, 지도처럼 자주 여는 앱을 추가해 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
                    AppIcon(app.packageName, app.appLabel, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.appLabel, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.move(app.packageName, -1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "앞으로")
                    }
                    IconButton(onClick = { viewModel.move(app.packageName, 1) }, enabled = index < apps.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "뒤로")
                    }
                    IconButton(onClick = { viewModel.remove(app.packageName) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "빼기")
                    }
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            title = "챗헤드 앱 선택",
            initialSelected = apps.map { InstalledAppInfo(it.packageName, it.appLabel) },
            onDismiss = { showPicker = false },
            onConfirm = {
                viewModel.save(it)
                showPicker = false
            }
        )
    }
}
