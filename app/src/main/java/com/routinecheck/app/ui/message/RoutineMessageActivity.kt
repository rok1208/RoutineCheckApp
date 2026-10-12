package com.routinecheck.app.ui.message

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routinecheck.app.MainActivity
import com.routinecheck.app.chathead.ChatHeadController
import com.routinecheck.app.domain.TimeText
import com.routinecheck.app.ui.RoutineCheckTheme

/** 화면에 보여줄 루틴/일정 메시지 한 건 */
data class RoutineMessage(
    val routineId: Long,
    val name: String,
    val startTime: String,
    val message: String,
    val isSchedule: Boolean,
    /** "챗헤드 자동 펼치기" 루틴: 메시지를 확인하면 챗헤드를 펼침 */
    val expandChatHead: Boolean = false
)

/**
 * 루틴/일정이 실행될 때 사용 중인 앱 위로 메시지를 띄우는 창 (v0.5.0).
 * RoutineAlarmReceiver 가 루틴에 메시지가 있을 때 엽니다.
 * "다른 앱 위에 표시" 권한이 있어서 앱이 꺼져 있어도 바로 띄울 수 있고, 잠금 화면 위에도 보입니다.
 * 같은 시각에 여러 루틴이 울리면 하나씩 차례로 보여줍니다.
 */
class RoutineMessageActivity : ComponentActivity() {

    private val queue = mutableStateListOf<RoutineMessage>()
    private var expandAfterClose = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        readMessage(intent)?.let { queue += it }
        if (queue.isEmpty()) {
            finish()
            return
        }
        setContent {
            RoutineCheckTheme {
                val current = queue.firstOrNull()
                if (current != null) {
                    MessageDialog(
                        message = current,
                        remaining = queue.size - 1,
                        onConfirm = ::next,
                        onOpenApp = {
                            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val msg = readMessage(intent) ?: return
        queue.removeAll { it.routineId == msg.routineId }
        queue += msg
    }

    private fun next() {
        val done = queue.removeFirstOrNull()
        if (done?.expandChatHead == true) expandAfterClose = true
        if (queue.isEmpty()) {
            if (expandAfterClose) ChatHeadController.show(this, expand = true)
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun readMessage(intent: Intent?): RoutineMessage? {
        intent ?: return null
        val text = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() } ?: return null
        return RoutineMessage(
            routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L),
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            startTime = intent.getStringExtra(EXTRA_TIME) ?: "00:00",
            message = text,
            isSchedule = intent.getBooleanExtra(EXTRA_IS_SCHEDULE, false),
            expandChatHead = intent.getBooleanExtra(EXTRA_EXPAND, false)
        )
    }

    companion object {
        private const val EXTRA_ROUTINE_ID = "routineId"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_TIME = "time"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_IS_SCHEDULE = "isSchedule"
        private const val EXTRA_EXPAND = "expandChatHead"

        fun intent(context: Context, msg: RoutineMessage): Intent =
            Intent(context, RoutineMessageActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ROUTINE_ID, msg.routineId)
                .putExtra(EXTRA_NAME, msg.name)
                .putExtra(EXTRA_TIME, msg.startTime)
                .putExtra(EXTRA_MESSAGE, msg.message)
                .putExtra(EXTRA_IS_SCHEDULE, msg.isSchedule)
                .putExtra(EXTRA_EXPAND, msg.expandChatHead)

        /** 백그라운드에서 띄우기 (실패하면 알림만 남음). true = 띄우기 요청 성공 */
        fun show(context: Context, msg: RoutineMessage): Boolean =
            runCatching { context.startActivity(intent(context, msg)) }.isSuccess
    }
}

@Composable
private fun MessageDialog(
    message: RoutineMessage,
    remaining: Int,
    onConfirm: () -> Unit,
    onOpenApp: () -> Unit
) {
    // 바깥(어두운 배경)을 눌러도 닫히지 않게: 메시지를 놓치지 않도록 "확인"으로만 닫음
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (message.isSchedule) Icons.Rounded.Event else Icons.Rounded.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (message.isSchedule) "일정 · " else "루틴 · ") + TimeText.display(message.startTime),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    message.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text(
                        message.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
                if (remaining > 0) {
                    Text(
                        "메시지 ${remaining}개가 더 있어요",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    TextButton(onClick = onOpenApp) { Text("앱 열기") }
                    Button(onClick = onConfirm) { Text(if (remaining > 0) "다음" else "확인") }
                }
            }
        }
    }
}
