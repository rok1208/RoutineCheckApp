package com.routinecheck.app.chathead

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.routinecheck.app.MainActivity
import com.routinecheck.app.R
import com.routinecheck.app.data.repository.AppRegistryRepository
import com.routinecheck.app.data.repository.ChatHeadItemRepository
import com.routinecheck.app.data.repository.ChatHeadSettings
import com.routinecheck.app.data.repository.ChatHeadSettingsRepository
import com.routinecheck.app.data.repository.EdgeSide
import com.routinecheck.app.data.repository.NotificationBadgeRepository
import com.routinecheck.app.data.repository.OnCompleteBehavior
import com.routinecheck.app.data.repository.RoutineRepository
import com.routinecheck.app.data.repository.TodayRepository
import com.routinecheck.app.data.repository.TodaySnapshot
import com.routinecheck.app.data.repository.toPinned
import com.routinecheck.app.domain.ChatHeadEntry
import com.routinecheck.app.domain.ChatHeadKind
import com.routinecheck.app.domain.ChatHeadOrdering
import com.routinecheck.app.domain.DateKey
import com.routinecheck.app.system.NotificationChannels
import com.routinecheck.app.ui.Routes
import com.routinecheck.app.ui.RoutineCheckTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 챗헤드 오버레이 Foreground Service — 앱의 핵심 기능.
 *
 * 창(Window) 3개를 WindowManager로 직접 관리합니다.
 *  - headView  : ③ 원형 아이콘 / ④ 가장자리 바 (드래그·탭은 View 터치 리스너로 직접 처리)
 *  - panelView : ⑤ 휠 캐러셀 / ⑥ 컨테이너 확장 화면 (전체 화면, 배경 어둡게)
 *  - trashView : 원형 아이콘을 드래그하는 동안만 화면 아래에 뜨는 제거 영역
 *
 * 표시할 목록은 TodayRepository(진행 중 루틴) + 챗헤드 항목(앱·웹사이트·폴더) + 알림 배지 + 열어 둔 폴더 경로를
 * 합쳐서 ChatHeadOrdering 규칙("전용 목록 + 루틴 우선", 폴더 안에서는 그 폴더의 항목만)으로 만듭니다.
 */
@AndroidEntryPoint
class ChatHeadService : LifecycleService(), SavedStateRegistryOwner {

    @Inject lateinit var settingsRepository: ChatHeadSettingsRepository
    @Inject lateinit var todayRepository: TodayRepository
    @Inject lateinit var chatHeadItemRepository: ChatHeadItemRepository
    @Inject lateinit var badgeRepository: NotificationBadgeRepository
    @Inject lateinit var routineRepository: RoutineRepository
    @Inject lateinit var appRegistry: AppRegistryRepository

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private val state = MutableStateFlow(OverlayState())
    /** 열어 둔 폴더 id 경로. 비어 있으면 최상단 */
    private val folderIds = MutableStateFlow<List<Long>>(emptyList())

    private var headView: ComposeView? = null
    private lateinit var headParams: WindowManager.LayoutParams
    private var panelView: ComposeView? = null
    private var trashView: ComposeView? = null

    private var latestToday: TodaySnapshot? = null
    private var completedSeen: Set<Long>? = null
    private var lastIconUri: String? = "__unset__"
    private var snapAnimator: ValueAnimator? = null

    private val prefs by lazy { getSharedPreferences("chathead_position", Context.MODE_PRIVATE) }
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Float): Int = (v * density).roundToInt()

    // ---------------------------------------------------------------- 생명주기

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        NotificationChannels.ensure(this)
        startInForeground()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        createHeadWindow()
        observeData()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ChatHeadController.ACTION_STOP -> {
                // 먼저 "꺼짐"을 저장하면, 설정을 구독 중인 observeData()가 stopSelf()까지 처리합니다
                // (여기서 바로 stopSelf()를 부르면 저장 코루틴이 같이 취소될 수 있음)
                lifecycleScope.launch { settingsRepository.setEnabled(false) }
            }
            ChatHeadController.ACTION_EXPAND -> {
                if (state.value.mode == HeadMode.BAR) toBubbleAtEdge()
                expand()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        snapAnimator?.cancel()
        removeView(trashView); trashView = null
        removeView(panelView); panelView = null
        removeView(headView); headView = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 화면 회전 등: 원형 아이콘이 화면 밖으로 나가지 않게 다시 붙이기
        headView?.post {
            when (state.value.mode) {
                HeadMode.BAR -> toBar(state.value.side)
                else -> snapToEdge(animate = false)
            }
        }
    }

    private fun startInForeground() {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ChatHeadService::class.java).setAction(ChatHeadController.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, NotificationChannels.CHATHEAD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("챗헤드 실행 중")
            .setContentText("화면 위 챗헤드로 앱을 바로 열 수 있어요")
            .setContentIntent(openApp)
            .addAction(0, "챗헤드 끄기", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(
            this, FOREGROUND_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    // ---------------------------------------------------------------- 데이터

    private fun observeData() {
        lifecycleScope.launch {
            combine(
                todayRepository.observeToday(),
                chatHeadItemRepository.observeItems(),
                badgeRepository.observeBadges(),
                settingsRepository.observeSettings(),
                folderIds
            ) { today, rows, badges, settings, path ->
                latestToday = today
                val items = rows.map { it.toPinned() }
                val activeInputs = today.active.map { it.toActiveInput() }
                // 열어 둔 폴더가 지워졌으면 남아 있는 가장 깊은 폴더로
                val crumbs = ChatHeadOrdering.pathTo(items, path.lastOrNull())
                    .map { FolderCrumb(it.id, it.title) }
                    .takeIf { c -> c.map { it.id } == path } ?: validPrefix(items, path)
                val entries = ChatHeadOrdering.build(activeInputs, items, crumbs.lastOrNull()?.id)
                val allPackages = activeInputs.flatMap { r -> r.apps.map { it.first } }.toSet() +
                    items.mapNotNull { it.packageName }
                val active = today.active
                val progress = active.firstOrNull()?.let {
                    RoutineProgress(it.routine.name, it.done, it.total, extraCount = active.size - 1)
                }
                Frame(today, entries, crumbs, allPackages, badges, settings, progress)
            }.collect { f ->
                if (!f.settings.enabled) {
                    stopSelf()
                    return@collect
                }
                loadIconIfChanged(f.settings)
                if (f.crumbs.map { it.id } != folderIds.value) folderIds.value = f.crumbs.map { it.id }
                state.update {
                    it.copy(
                        entries = f.entries, folderPath = f.crumbs, allPackages = f.allPackages,
                        badges = f.badges, settings = f.settings, progress = f.progress
                    )
                }
                handleRoutineCompletion(f.today, f.settings)
            }
        }
    }

    private data class Frame(
        val today: TodaySnapshot,
        val entries: List<ChatHeadEntry>,
        val crumbs: List<FolderCrumb>,
        val allPackages: Set<String>,
        val badges: Map<String, com.routinecheck.app.data.repository.Badge>,
        val settings: ChatHeadSettings,
        val progress: RoutineProgress?
    )

    /** 경로 중 실제로 이어지는 앞부분만 남김 (중간 폴더가 지워졌거나 옮겨진 경우) */
    private fun validPrefix(items: List<com.routinecheck.app.domain.PinnedItem>, path: List<Long>): List<FolderCrumb> {
        val byId = items.associateBy { it.id }
        val result = mutableListOf<FolderCrumb>()
        var parent: Long? = null
        for (id in path) {
            val folder = byId[id] ?: break
            if (folder.kind != ChatHeadKind.FOLDER || folder.parentId != parent) break
            result += FolderCrumb(folder.id, folder.title)
            parent = folder.id
        }
        return result
    }

    private suspend fun loadIconIfChanged(settings: ChatHeadSettings) {
        if (settings.iconUri == lastIconUri) return
        lastIconUri = settings.iconUri
        val bitmap = withContext(Dispatchers.IO) { ChatHeadIconStore.load(settings.iconUri) }
        state.update { it.copy(customIcon = bitmap?.asImageBitmap()) }
    }

    /** 오늘 루틴이 방금 완료됐다면 설정된 동작(유지/축소/숨김) 적용 */
    private fun handleRoutineCompletion(today: TodaySnapshot, settings: ChatHeadSettings) {
        val completedNow = today.routines.filter { it.isComplete && it.hasStarted }.map { it.routine.routineId }.toSet()
        val previous = completedSeen
        completedSeen = completedNow
        if (previous == null) return // 서비스 시작 직후 첫 값은 기준점으로만 사용
        val newlyCompleted = today.routines.filter { it.routine.routineId in completedNow - previous }
        if (newlyCompleted.isEmpty()) return

        Toast.makeText(this, "${newlyCompleted.first().routine.name} 완료! 🎉", Toast.LENGTH_SHORT).show()
        if (today.active.isNotEmpty()) return // 아직 다른 루틴이 진행 중이면 그대로
        when (settings.onCompleteBehavior) {
            OnCompleteBehavior.KEEP -> Unit
            OnCompleteBehavior.MINIMIZE -> { collapse(); toBar(settings.edgeSide) }
            OnCompleteBehavior.HIDE -> { collapse(); stopSelf() } // 다음 루틴 알람 때 다시 표시됨
        }
    }

    // ---------------------------------------------------------------- 창 만들기

    private fun overlayType(): Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun baseParams(width: Int, height: Int, focusable: Boolean = false) = WindowManager.LayoutParams(
        width, height, overlayType(),
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun newComposeView(content: @Composable () -> Unit) =
        ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ChatHeadService)
            setViewTreeSavedStateRegistryOwner(this@ChatHeadService)
            setContent { RoutineCheckTheme(content = content) }
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun createHeadWindow() {
        val size = dp(64f)
        val (screenW, screenH) = screenSize()
        val savedSide = prefs.getString(KEY_SIDE, EdgeSide.RIGHT.name)?.let { runCatching { EdgeSide.valueOf(it) }.getOrNull() }
            ?: EdgeSide.RIGHT
        val savedY = prefs.getInt(KEY_Y, (screenH * 0.35f).toInt())
        headParams = baseParams(size, size).apply {
            x = if (savedSide == EdgeSide.RIGHT) screenW - size else 0
            y = clampY(savedY, size)
        }
        state.update { it.copy(side = savedSide) }

        val view = newComposeView {
            val s by state.collectAsState()
            // 투명도 설정은 원형 아이콘/바에만 적용 (확장 화면은 항상 선명하게)
            Box(modifier = Modifier.graphicsLayer { alpha = s.settings.opacity }) {
                when (s.mode) {
                    HeadMode.BAR -> EdgeBar(s)
                    else -> BubbleHead(s)
                }
            }
        }
        view.setOnTouchListener(HeadTouchListener())
        headView = view
        windowManager.addView(view, headParams)
        if (prefs.getBoolean(KEY_BAR, false)) toBar(savedSide)
    }

    private fun showPanel() {
        if (panelView != null) return
        val view = newComposeView {
            val s by state.collectAsState()
            ExpandedPanel(
                state = s,
                actions = ExpandedActions(
                    onDismiss = ::collapse,
                    onLaunch = ::launchEntry,
                    onToggleCheck = ::toggleEntryCheck,
                    onBack = { folderIds.update { it.dropLast(1) } },
                    onTop = { folderIds.value = emptyList() },
                    onOpenApp = { openMainApp(null) },
                    onManageApps = { openMainApp(Routes.CHATHEAD_ITEMS) },
                    onOpenSettings = { openMainApp(Routes.SETTINGS) }
                )
            )
        }
        panelView = view
        windowManager.addView(
            view,
            baseParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        )
    }

    private fun showTrash() {
        if (trashView != null) return
        val size = dp(84f)
        val (screenW, screenH) = screenSize()
        val view = newComposeView {
            val s by state.collectAsState()
            TrashTarget(highlighted = s.overTrash)
        }
        trashView = view
        windowManager.addView(view, baseParams(size, size).apply {
            x = (screenW - size) / 2
            y = screenH - size - dp(72f)
        })
    }

    private fun hideTrash() {
        removeView(trashView)
        trashView = null
        state.update { it.copy(overTrash = false) }
    }

    private fun removeView(view: View?) {
        if (view == null) return
        runCatching { windowManager.removeView(view) }
    }

    // ---------------------------------------------------------------- 상태 전환

    private fun expand() {
        val head = headView ?: return
        snapAnimator?.cancel()
        if (state.value.mode == HeadMode.BAR) toBubbleAtEdge()
        val side = if (headParams.x + headParams.width / 2 > screenSize().first / 2) EdgeSide.RIGHT else EdgeSide.LEFT
        state.update { it.copy(mode = HeadMode.EXPANDED, side = side, anchorY = headParams.y + headParams.height / 2) }
        snapToEdge(animate = false)
        showPanel()
        head.visibility = View.INVISIBLE // 확장 화면 안에 같은 자리에 원형 아이콘이 다시 그려짐
    }

    private fun collapse() {
        removeView(panelView)
        panelView = null
        folderIds.value = emptyList() // 다음에 펼치면 최상단부터
        headView?.visibility = View.VISIBLE
        if (state.value.mode == HeadMode.EXPANDED) state.update { it.copy(mode = HeadMode.BUBBLE) }
    }

    private fun toBar(side: EdgeSide) {
        val head = headView ?: return
        snapAnimator?.cancel()
        val (screenW, _) = screenSize()
        val w = dp(24f)
        val h = dp(88f)
        val centerY = headParams.y + headParams.height / 2
        headParams.width = w
        headParams.height = h
        headParams.x = if (side == EdgeSide.RIGHT) screenW - w else 0
        headParams.y = clampY(centerY - h / 2, h)
        state.update { it.copy(mode = HeadMode.BAR, side = side) }
        windowManager.updateViewLayout(head, headParams)
        savePosition()
    }

    private fun toBubbleAtEdge() {
        val head = headView ?: return
        val (screenW, _) = screenSize()
        val size = dp(64f)
        val centerY = headParams.y + headParams.height / 2
        headParams.width = size
        headParams.height = size
        headParams.x = if (state.value.side == EdgeSide.RIGHT) screenW - size else 0
        headParams.y = clampY(centerY - size / 2, size)
        state.update { it.copy(mode = HeadMode.BUBBLE) }
        windowManager.updateViewLayout(head, headParams)
        savePosition()
    }

    /** 원형 아이콘을 가까운 좌/우 가장자리로 붙이기 */
    private fun snapToEdge(animate: Boolean = true) {
        val head = headView ?: return
        val (screenW, _) = screenSize()
        val size = headParams.width
        val side = if (headParams.x + size / 2 > screenW / 2) EdgeSide.RIGHT else EdgeSide.LEFT
        val targetX = if (side == EdgeSide.RIGHT) screenW - size else 0
        headParams.y = clampY(headParams.y, headParams.height)
        state.update { it.copy(side = side) }
        snapAnimator?.cancel()
        if (!animate) {
            headParams.x = targetX
            windowManager.updateViewLayout(head, headParams)
            savePosition()
            return
        }
        snapAnimator = ValueAnimator.ofInt(headParams.x, targetX).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                headParams.x = it.animatedValue as Int
                if (headView != null) runCatching { windowManager.updateViewLayout(head, headParams) }
            }
            start()
        }
        savePosition()
    }

    private fun removeChatHead() {
        Toast.makeText(this, "챗헤드를 껐어요. 앱에서 다시 켤 수 있어요", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            settingsRepository.setEnabled(false) // 구독 중인 흐름이 stopSelf()까지 처리
        }
    }

    // ---------------------------------------------------------------- 앱 실행 / 체크

    private fun launchEntry(entry: ChatHeadEntry) {
        when (entry.kind) {
            ChatHeadKind.FOLDER -> {
                entry.itemId?.let { id -> folderIds.update { it + id } }
                return
            }
            ChatHeadKind.LINK -> {
                openLink(entry)
                return
            }
            ChatHeadKind.APP -> Unit
        }
        val pkg = entry.packageName ?: return
        val launched = appRegistry.launch(pkg)
        if (!launched) {
            Toast.makeText(this, "${entry.label}을(를) 열 수 없어요 (삭제되었을 수 있어요)", Toast.LENGTH_SHORT).show()
            return
        }
        collapse()
        lifecycleScope.launch {
            badgeRepository.clearBadge(pkg)
            if (entry.isRoutineApp) {
                routineRepository.markOpened(entry.routineIds, pkg, todayKey())
            }
        }
    }

    /** 웹사이트: 기본 브라우저(또는 그 주소를 여는 앱)로 열기 */
    private fun openLink(entry: ChatHeadEntry) {
        val url = entry.url ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            collapse()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "이 주소를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleEntryCheck(entry: ChatHeadEntry) {
        if (entry.kind != ChatHeadKind.APP) return
        val pkg = entry.packageName ?: return
        if (!entry.isRoutineApp) {
            Toast.makeText(this, "루틴에 포함된 앱만 체크할 수 있어요", Toast.LENGTH_SHORT).show()
            return
        }
        val newValue = entry.isChecked != true
        lifecycleScope.launch {
            entry.routineIds.forEach { routineRepository.setChecked(it, pkg, todayKey(), newValue) }
        }
    }

    private fun todayKey(): String = latestToday?.dateKey ?: DateKey.today()

    private fun openMainApp(route: String?) {
        collapse()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (route != null) intent.putExtra(MainActivity.EXTRA_START_ROUTE, route)
        startActivity(intent)
    }

    // ---------------------------------------------------------------- 터치 처리

    /**
     * 원형 아이콘/바의 드래그·탭.
     * 창 자체가 손가락을 따라 움직이므로 Compose 제스처 대신 rawX/rawY 기반 View 터치로 처리합니다.
     */
    private inner class HeadTouchListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(this@ChatHeadService).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    downX = event.rawX; downY = event.rawY
                    startX = headParams.x; startY = headParams.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    when (state.value.mode) {
                        HeadMode.BAR -> onBarMove(event, dx, dy)
                        HeadMode.BUBBLE -> onBubbleMove(event, dx, dy)
                        HeadMode.EXPANDED -> Unit
                    }
                }
                MotionEvent.ACTION_UP -> {
                    when (state.value.mode) {
                        HeadMode.BAR -> if (!dragging) toBubbleAtEdge() else savePosition()
                        HeadMode.BUBBLE -> onBubbleUp(event)
                        HeadMode.EXPANDED -> Unit
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging && state.value.mode == HeadMode.BUBBLE) { hideTrash(); snapToEdge() }
                    dragging = false
                }
            }
            return true
        }

        private fun onBubbleMove(event: MotionEvent, dx: Float, dy: Float) {
            if (!dragging && hypot(dx, dy) > touchSlop) {
                dragging = true
                showTrash()
            }
            if (!dragging) return
            headParams.x = startX + dx.toInt()
            headParams.y = startY + dy.toInt()
            headView?.let { windowManager.updateViewLayout(it, headParams) }
            val over = isOverTrash(event.rawX, event.rawY)
            if (over != state.value.overTrash) state.update { it.copy(overTrash = over) }
        }

        private fun onBubbleUp(event: MotionEvent) {
            if (!dragging) {
                expand()
                return
            }
            val overTrash = isOverTrash(event.rawX, event.rawY)
            hideTrash()
            if (overTrash) {
                removeChatHead()
                return
            }
            // 가장자리 끝까지 밀어 넣으면 얇은 바로 축소
            val (screenW, _) = screenSize()
            val edgeZone = dp(14f)
            when {
                event.rawX <= edgeZone -> toBar(EdgeSide.LEFT)
                event.rawX >= screenW - edgeZone -> toBar(EdgeSide.RIGHT)
                else -> snapToEdge()
            }
        }

        /** 바: 세로로는 가장자리를 따라 이동, 안쪽(화면 가운데)으로 밀면 원형으로 다시 나옴 */
        private fun onBarMove(event: MotionEvent, dx: Float, dy: Float) {
            val inward = if (state.value.side == EdgeSide.RIGHT) -dx else dx
            if (inward > dp(24f)) {
                // 원형으로 꺼내서 그대로 손가락을 따라 드래그 계속
                val size = dp(64f)
                headParams.width = size
                headParams.height = size
                headParams.x = (event.rawX - size / 2).toInt()
                headParams.y = (event.rawY - size / 2).toInt()
                state.update { it.copy(mode = HeadMode.BUBBLE) }
                headView?.let { windowManager.updateViewLayout(it, headParams) }
                downX = event.rawX; downY = event.rawY
                startX = headParams.x; startY = headParams.y
                dragging = true
                showTrash()
                return
            }
            if (!dragging && abs(dy) > touchSlop) dragging = true
            if (dragging) {
                headParams.y = clampY(startY + dy.toInt(), headParams.height)
                headView?.let { windowManager.updateViewLayout(it, headParams) }
            }
        }

        private fun isOverTrash(rawX: Float, rawY: Float): Boolean {
            val (screenW, screenH) = screenSize()
            val trashCenterX = screenW / 2f
            val trashCenterY = screenH - dp(72f) - dp(84f) / 2f
            return hypot(rawX - trashCenterX, rawY - trashCenterY) < dp(80f)
        }
    }

    // ---------------------------------------------------------------- 유틸

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    private fun clampY(y: Int, height: Int): Int {
        val (_, screenH) = screenSize()
        return y.coerceIn(dp(32f), (screenH - height - dp(48f)).coerceAtLeast(dp(32f)))
    }

    private fun savePosition() {
        prefs.edit()
            .putString(KEY_SIDE, state.value.side.name)
            .putInt(KEY_Y, headParams.y + headParams.height / 2 - dp(32f))
            .putBoolean(KEY_BAR, state.value.mode == HeadMode.BAR)
            .apply()
    }

    companion object {
        private const val FOREGROUND_ID = 1001
        private const val KEY_SIDE = "side"
        private const val KEY_Y = "y"
        private const val KEY_BAR = "bar"
    }
}
