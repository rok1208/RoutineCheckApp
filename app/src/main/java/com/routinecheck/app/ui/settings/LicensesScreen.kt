package com.routinecheck.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routinecheck.app.R

/** 앱에 포함된 오픈소스 라이브러리 (모두 Apache License 2.0) */
private data class OssLibrary(val name: String, val owner: String, val url: String)

private val LIBRARIES = listOf(
    OssLibrary("Android Jetpack (AndroidX Core · Activity · Lifecycle · Navigation · SavedState)", "The Android Open Source Project", "https://developer.android.com/jetpack/androidx"),
    OssLibrary("Jetpack Compose (UI · Foundation · Material 3)", "The Android Open Source Project", "https://developer.android.com/jetpack/compose"),
    OssLibrary("Material Icons (Compose Material Icons Extended)", "Google LLC", "https://fonts.google.com/icons"),
    OssLibrary("Room", "The Android Open Source Project", "https://developer.android.com/training/data-storage/room"),
    OssLibrary("Dagger · Hilt", "The Dagger Authors", "https://dagger.dev/hilt"),
    OssLibrary("AndroidX Hilt Navigation Compose", "The Android Open Source Project", "https://developer.android.com/jetpack/androidx/releases/hilt"),
    OssLibrary("Kotlin Standard Library", "JetBrains s.r.o. and Kotlin Programming Language contributors", "https://kotlinlang.org"),
    OssLibrary("kotlinx.coroutines", "JetBrains s.r.o.", "https://github.com/Kotlin/kotlinx.coroutines")
)

/** 설정 → 오픈소스 라이선스 (v0.7.0, Apache-2.0 고지 의무) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val licenseText = remember {
        runCatching {
            context.resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    var showFullText by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("오픈소스 라이선스") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    "루틴 체크는 아래 오픈소스 소프트웨어를 사용합니다. 모두 Apache License 2.0을 따릅니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            items(LIBRARIES) { lib ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(lib.name, style = MaterialTheme.typography.bodyLarge)
                    Text("Copyright © ${lib.owner}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lib.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("Apache License 2.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFullText = !showFullText }
                        .padding(vertical = 16.dp)
                ) {
                    Text("Apache License 2.0 전문", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (showFullText) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                }
            }
            if (showFullText) {
                item {
                    Text(
                        licenseText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }
            }
        }
    }
}
