package com.routinecheck.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * 날짜 입력 (v0.5.0 일정용).
 *  [2026] 년 [10] 월 [15] 일   📅
 * 숫자를 눌러 직접 입력하거나(시각 입력과 같은 방식), 오른쪽 달력 버튼으로 달력에서 고를 수 있습니다.
 * 달력 창에서도 연필 버튼으로 숫자 입력 모드로 바꿀 수 있어요.
 * 월을 바꿨는데 그 달에 없는 날짜면(예: 2월 31일) 그 달 마지막 날로 맞춥니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInput(
    date: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthFocus = remember { FocusRequester() }
    val dayFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var showPicker by remember { mutableStateOf(false) }
    val style = MaterialTheme.typography.titleLarge

    fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val ym = YearMonth.of(year, month)
        return ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        DigitField(
            value = date.year,
            max = 2099,
            length = 4,
            width = 76.dp,
            textStyle = style,
            canCommit = { it.length == 4 && it.toInt() >= 2000 },
            isComplete = { it.length == 4 },
            onCommit = { y -> onChange(safeDate(y, date.monthValue, date.dayOfMonth)) },
            onNext = { monthFocus.requestFocus() },
            imeAction = ImeAction.Next,
            description = "연도"
        )
        Text("년", modifier = Modifier.padding(horizontal = 4.dp))
        DigitField(
            value = date.monthValue,
            max = 12,
            width = 48.dp,
            textStyle = style,
            canCommit = { it.toInt() >= 1 },
            isComplete = { it.length == 2 || it[0] >= '2' },
            onCommit = { m -> onChange(safeDate(date.year, m, date.dayOfMonth)) },
            onNext = { dayFocus.requestFocus() },
            imeAction = ImeAction.Next,
            description = "월",
            modifier = Modifier.focusRequester(monthFocus)
        )
        Text("월", modifier = Modifier.padding(horizontal = 4.dp))
        DigitField(
            value = date.dayOfMonth,
            max = 31,
            width = 48.dp,
            textStyle = style,
            canCommit = { it.toInt() >= 1 },
            isComplete = { it.length == 2 || it[0] >= '4' },
            onCommit = { d -> onChange(safeDate(date.year, date.monthValue, d)) },
            onNext = { focusManager.clearFocus() },
            imeAction = ImeAction.Done,
            description = "일",
            modifier = Modifier.focusRequester(dayFocus)
        )
        Text("일", modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { showPicker = true }) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = "달력에서 날짜 고르기")
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("취소") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
