package com.routinecheck.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 시각 직접 입력 (시계 다이얼 없이 숫자만).
 *  [오전/오후]  [07] : [30]
 * 시·분 숫자를 누르면 칸이 비워지고 키패드가 올라옵니다. 두 자리를 다 치면 다음 칸으로 넘어가요.
 * 시 칸에 13~23을 치면 자동으로 오후로, 0을 치면 오전 12시로 바뀝니다.
 *
 * @param hour 0~23 (24시간제)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInput(
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPm = hour >= 12
    val hour12 = (hour % 12).let { if (it == 0) 12 else it }
    val minuteFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FilterChip(selected = !isPm, onClick = { if (isPm) onChange(hour - 12, minute) }, label = { Text("오전") })
            FilterChip(selected = isPm, onClick = { if (!isPm) onChange(hour + 12, minute) }, label = { Text("오후") })
        }
        Spacer(Modifier.width(16.dp))
        DigitField(
            value = hour12,
            max = 23,
            isComplete = { it.length == 2 || it[0] >= '3' },
            onCommit = { n ->
                val h24 = when {
                    n >= 13 -> n
                    n == 0 -> 0
                    else -> (n % 12) + if (isPm) 12 else 0
                }
                onChange(h24, minute)
            },
            onNext = { minuteFocus.requestFocus() },
            imeAction = ImeAction.Next,
            description = "시"
        )
        Text(
            ":",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        DigitField(
            value = minute,
            max = 59,
            isComplete = { it.length == 2 || it[0] >= '6' },
            onCommit = { n -> onChange(hour, n) },
            onNext = { focusManager.clearFocus() },
            imeAction = ImeAction.Done,
            description = "분",
            modifier = Modifier.focusRequester(minuteFocus)
        )
    }
}

/** 두 자리 숫자 칸. 누르면 비워지고(이전 값은 흐리게 표시), 범위를 넘는 숫자는 받지 않음 */
@Composable
private fun DigitField(
    value: Int,
    max: Int,
    isComplete: (String) -> Boolean,
    onCommit: (Int) -> Unit,
    onNext: () -> Unit,
    imeAction: ImeAction,
    description: String,
    modifier: Modifier = Modifier
) {
    /** null = 입력 중이 아님(현재 값 표시), "" 이상 = 입력 중인 글자 */
    var editing by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    val shown = "%02d".format(value)
    val style = MaterialTheme.typography.displaySmall.copy(
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )

    BasicTextField(
        value = editing ?: shown,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(2)
            val n = digits.toIntOrNull()
            if (n != null && n > max) return@BasicTextField
            editing = digits
            if (n != null) {
                onCommit(n)
                if (isComplete(digits)) onNext()
            }
        },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onNext() }),
        modifier = modifier
            .width(84.dp)
            .semantics { contentDescription = "$description 입력" }
            .onFocusChanged { state ->
                if (state.isFocused && !focused) editing = ""
                if (!state.isFocused && focused) editing = null
                focused = state.isFocused
            },
        decorationBox = { inner ->
            Box(
                Modifier
                    .background(
                        if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (editing == "") {
                    Text(shown, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)))
                }
                inner()
            }
        }
    )
}
