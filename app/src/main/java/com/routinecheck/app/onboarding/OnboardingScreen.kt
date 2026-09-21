package com.routinecheck.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ① 온보딩 화면 UI. 노션 와이어프레임의 "필수 권한 안내" 화면에 대응합니다.
 *
 * 이 Composable은 상태를 직접 들고 있지 않고 전부 파라미터로 받습니다.
 * 실제 권한 요청(설정 화면 이동 등)은 OnboardingActivity에서 처리해 여기로 콜백만 넘겨주세요.
 * 담당: 변공록
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Text("🛡️", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "필수 권한 안내",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "루틴 체크가 정상적으로 동작하려면 아래 권한이 필요해요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            uiState.steps.forEach { step ->
                PermissionRow(
                    step = step,
                    onClick = { onRequestPermission(step.type) }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val next = uiState.nextUngranted
                    if (next != null) onRequestPermission(next.type) else onFinish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.allGranted) "시작하기" else "권한 허용하기")
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
                color = if (step.isGranted)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                step.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (step.isGranted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "허용됨",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            TextButton(onClick = onClick) {
                Text("허용")
            }
        }
    }
}
