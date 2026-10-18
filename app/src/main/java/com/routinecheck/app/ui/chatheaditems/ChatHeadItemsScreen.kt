package com.routinecheck.app.ui.chatheaditems

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routinecheck.app.domain.ChatHeadKind
import com.routinecheck.app.domain.LinkUtils
import com.routinecheck.app.domain.PinnedItem
import com.routinecheck.app.ui.common.AppPickerDialog
import com.routinecheck.app.ui.common.ChatHeadItemIcon
import com.routinecheck.app.ui.common.AppPermission
import com.routinecheck.app.ui.common.CoachStep
import com.routinecheck.app.ui.common.CoachTour
import com.routinecheck.app.ui.common.coachTarget
import com.routinecheck.app.ui.common.rememberPermissionRequester
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** 화면 위에 띄우는 대화상자 종류 */
private sealed interface ItemDialog {
    data object AddLink : ItemDialog
    data object NewFolder : ItemDialog
    data class EditLink(val item: PinnedItem) : ItemDialog
    data class RenameFolder(val item: PinnedItem) : ItemDialog
    data class Move(val item: PinnedItem) : ItemDialog
    data class ConfirmDelete(val item: PinnedItem) : ItemDialog
}

/**
 * 챗헤드 항목 관리 (앱 · 웹사이트 · 폴더).
 * 루틴과 상관없이 챗헤드에 항상 보이는 항목과 그 순서, 폴더 묶음을 정합니다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatHeadItemsScreen(
    onBack: () -> Unit,
    viewModel: ChatHeadItemsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ItemDialog?>(null) }
    val context = LocalContext.current
    val permissionRequester = rememberPermissionRequester()
    // 알림 배지(알림 접근)는 여기서 사용자가 켜려고 할 때 요청 (v0.6.0)
    var badgeOn by remember { mutableStateOf(AppPermission.NOTIFICATION_LISTENER.isGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { badgeOn = AppPermission.NOTIFICATION_LISTENER.isGranted(context) }

    CoachTour(
        id = "chathead_items",
        enabled = state.loaded,
        steps = listOf(
            CoachStep(
                target = "items.add",
                title = "항목 추가",
                body = "챗헤드에 띄울 앱과 웹사이트를 추가하고, 비슷한 것끼리 폴더로 묶을 수 있어요."
            ),
            CoachStep(
                target = "items.badge",
                title = "알림 배지",
                body = "켜면 메신저처럼 확인 안 한 알림이 있는 앱에 숫자 배지가 붙어요. 원할 때만 켜면 돼요."
            ),
            CoachStep(
                target = null,
                title = "순서 바꾸기",
                body = "각 항목 오른쪽 ⋮ 메뉴에서 위·아래로 옮기거나, 폴더로 옮기거나, 지울 수 있어요."
            )
        )
    )

    // 폴더 안에서 뒤로가기 = 한 단계 위로
    BackHandler(enabled = state.path.isNotEmpty()) { viewModel.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.path.lastOrNull()?.title ?: "챗헤드 항목") },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.back()) onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp)
        ) {
            item {
                Breadcrumb(state.path, onOpen = viewModel::openPath)
                if (state.path.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        OutlinedButton(onClick = { viewModel.back() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("이전으로")
                        }
                        OutlinedButton(onClick = viewModel::top) {
                            Icon(Icons.Rounded.VerticalAlignTop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("최상단으로")
                        }
                    }
                }
            }
            if (state.path.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(
                            "챗헤드를 펼치면 ① 진행 중인 루틴의 앱 ② 폴더 ③ 앱·웹사이트 순서로 보여요.\n" +
                                "폴더를 누르면 안의 항목이 열리고, 위쪽의 \"이전으로\"·\"최상단으로\"로 돌아올 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            if (state.path.isEmpty()) {
                item(key = "badge") {
                    BadgePermissionCard(
                        enabled = badgeOn,
                        onEnable = {
                            permissionRequester.request(listOf(AppPermission.NOTIFICATION_LISTENER), userInitiated = true) {
                                badgeOn = AppPermission.NOTIFICATION_LISTENER in it
                            }
                        },
                        modifier = Modifier.coachTarget("items.badge")
                    )
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).coachTarget("items.add")
                ) {
                    FilledTonalButton(onClick = { showPicker = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("앱 추가")
                    }
                    FilledTonalButton(onClick = { dialog = ItemDialog.AddLink }) {
                        Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("웹사이트 추가")
                    }
                    FilledTonalButton(onClick = { dialog = ItemDialog.NewFolder }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("폴더 만들기")
                    }
                }
            }
            if (state.loaded && state.isEmpty) {
                item {
                    Text(
                        if (state.path.isEmpty()) "아직 등록된 항목이 없어요. 메신저·캘린더 같은 앱이나 자주 가는 웹사이트를 추가하고, 비슷한 것끼리 폴더로 묶어 보세요."
                        else "빈 폴더예요. 위 버튼으로 앱·웹사이트·하위 폴더를 넣어 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            itemsIndexed(state.folders, key = { _, it -> "f${it.id}" }) { index, folder ->
                ItemRow(
                    item = folder,
                    subtitle = "폴더 · ${state.childCounts[folder.id] ?: 0}개",
                    canUp = index > 0,
                    canDown = index < state.folders.lastIndex,
                    onClick = { viewModel.openFolder(folder.id) },
                    onMove = { viewModel.move(folder.id, it) },
                    menu = listOf(
                        "열기" to { viewModel.openFolder(folder.id) },
                        "이름 바꾸기" to { dialog = ItemDialog.RenameFolder(folder) },
                        "다른 폴더로 옮기기" to { dialog = ItemDialog.Move(folder) },
                        "삭제" to { dialog = ItemDialog.ConfirmDelete(folder) }
                    )
                )
            }
            itemsIndexed(state.others, key = { _, it -> "i${it.id}" }) { index, item ->
                val isLink = item.kind == ChatHeadKind.LINK
                ItemRow(
                    item = item,
                    subtitle = if (isLink) item.url?.let { LinkUtils.hostOf(it) } ?: "웹사이트" else "앱",
                    canUp = index > 0,
                    canDown = index < state.others.lastIndex,
                    onClick = if (isLink) ({ dialog = ItemDialog.EditLink(item) }) else null,
                    onMove = { viewModel.move(item.id, it) },
                    menu = buildList<Pair<String, () -> Unit>> {
                        if (isLink) add("이름·주소 수정" to { dialog = ItemDialog.EditLink(item) })
                        add("다른 폴더로 옮기기" to { dialog = ItemDialog.Move(item) })
                        add("빼기" to { viewModel.delete(item.id); Unit })
                    }
                )
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            title = state.path.lastOrNull()?.let { "'${it.title}' 폴더 앱 선택" } ?: "챗헤드 앱 선택",
            initialSelected = state.appsHere,
            onDismiss = { showPicker = false },
            onConfirm = {
                viewModel.setApps(it)
                showPicker = false
            }
        )
    }

    when (val d = dialog) {
        null -> Unit
        ItemDialog.AddLink -> LinkDialog(
            title = "웹사이트 추가",
            initialName = "",
            initialUrl = "",
            onDismiss = { dialog = null },
            onConfirm = { name, url -> viewModel.addLink(name, url).also { if (it == null) dialog = null } }
        )
        is ItemDialog.EditLink -> LinkDialog(
            title = "웹사이트 수정",
            initialName = d.item.title,
            initialUrl = d.item.url.orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { name, url -> viewModel.editLink(d.item.id, name, url).also { if (it == null) dialog = null } }
        )
        ItemDialog.NewFolder -> NameDialog(
            title = "새 폴더",
            initial = "",
            onDismiss = { dialog = null },
            onConfirm = { name -> viewModel.createFolder(name).also { if (it == null) dialog = null } }
        )
        is ItemDialog.RenameFolder -> NameDialog(
            title = "폴더 이름 바꾸기",
            initial = d.item.title,
            onDismiss = { dialog = null },
            onConfirm = { name -> viewModel.renameFolder(d.item.id, name).also { if (it == null) dialog = null } }
        )
        is ItemDialog.Move -> {
            val destinations = state.destinationsFor(d.item)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("'${d.item.title}' 옮기기") },
                text = {
                    if (destinations.isEmpty()) {
                        Text("옮길 수 있는 폴더가 없어요. 먼저 폴더를 만들어 주세요.")
                    } else {
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            itemsIndexed(destinations, key = { _, it -> it.id ?: -1L }) { _, dest ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.moveTo(d.item.id, dest.id)
                                            dialog = null
                                        }
                                        .padding(start = (dest.depth * 16).dp, top = 10.dp, bottom = 10.dp)
                                ) {
                                    Icon(
                                        if (dest.id == null) Icons.Rounded.VerticalAlignTop else Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(dest.label)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("닫기") } }
            )
        }
        is ItemDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("'${d.item.title}' 폴더를 삭제할까요?") },
            text = { Text("폴더 안의 앱·웹사이트·하위 폴더는 지워지지 않고 한 단계 위로 옮겨져요.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(d.item.id); dialog = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("취소") } }
        )
    }
}

/** 최상단 › 업무 › 개발 (누르면 그 단계로 이동) */
@Composable
private fun Breadcrumb(path: List<PinnedItem>, onOpen: (Long?) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)
    ) {
        Crumb("최상단", current = path.isEmpty()) { onOpen(null) }
        path.forEachIndexed { index, folder ->
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Crumb(folder.title, current = index == path.lastIndex) { onOpen(folder.id) }
        }
    }
}

@Composable
private fun Crumb(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !current, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Composable
private fun ItemRow(
    item: PinnedItem,
    subtitle: String,
    canUp: Boolean,
    canDown: Boolean,
    onClick: (() -> Unit)?,
    onMove: (Int) -> Unit,
    menu: List<Pair<String, () -> Unit>>
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp)
    ) {
        ChatHeadItemIcon(item.kind, item.packageName, item.title, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onMove(-1) }, enabled = canUp) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "앞으로")
        }
        IconButton(onClick = { onMove(1) }, enabled = canDown) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "뒤로")
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "더보기") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menu.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label, color = if (label == "삭제" || label == "빼기") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                        onClick = { menuOpen = false; action() }
                    )
                }
            }
        }
    }
}

/** 웹사이트 이름·주소 입력. onConfirm이 오류 문구를 돌려주면 그대로 보여줌 */
@Composable
private fun LinkDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> String?
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("주소") },
                    placeholder = { Text("예) naver.com") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("이름 (비우면 주소로)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { error = onConfirm(name, url) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> String?) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20); error = null },
                label = { Text("폴더 이름") },
                placeholder = { Text("예) 업무, 쇼핑") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { error = onConfirm(name) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/** 알림 배지(알림 접근) 켜기 카드. 켜져 있으면 한 줄 안내만 */
@Composable
private fun BadgePermissionCard(enabled: Boolean, onEnable: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (enabled) "알림 배지 켜짐" else "알림 배지", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled) "확인 안 한 알림 수가 챗헤드 항목에 표시돼요"
                    else "확인 안 한 알림이 있는 앱에 숫자를 붙여 보여줘요 (알림 접근 필요)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!enabled) TextButton(onClick = onEnable) { Text("켜기") }
        }
    }
}
