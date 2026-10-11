package com.routinecheck.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routinecheck.app.ui.common.ChatHeadPreview

/**
 * 온보딩 화면 UI. 상태는 파라미터로만 받고, 실제 권한 요청은 OnboardingActivity가 처리합니다.
 */
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onRequestPermission: (PermissionType) -> Unit,
    onFinish: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                ChatHeadPreview(iconUri = null, size = 72.dp)
                Spacer(Modifier.height(16.dp))
                Text("챗헤드 하나로 주요 앱을 바로", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "어느 화면에서든 챗헤드를 눌러 앱을 열고, 루틴 시간엔 확인할 앱을 먼저 보여드려요.\n시작하려면 아래 권한이 필요해요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(24.dp))
                uiState.steps.forEach { step ->
                    PermissionRow(step = step, onClick = { onRequestPermission(step.type) })
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val next = uiState.nextRequired
                    if (next != null) onRequestPermission(next.type) else onFinish()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (uiState.requiredGranted) "시작하기" else "필수 권한 허용하기")
            }
            if (uiState.requiredGranted && !uiState.allGranted) {
                Text(
                    "권장 항목은 나중에 설정에서 켤 수 있어요",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(step: PermissionStepUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (step.isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (step.isRequired) "필수" else "권장",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (step.isRequired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(step.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (step.isGranted) {
            Icon(Icons.Default.Check, contentDescription = "허용됨", tint = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onClick) { Text("허용") }
        }
    }
}
