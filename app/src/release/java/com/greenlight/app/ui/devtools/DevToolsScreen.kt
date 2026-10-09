package com.greenlight.app.ui.devtools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.greenlight.app.AppContainer

/** Release builds ship no dev tools or test accounts. */
@Composable
fun DevToolsScreen(container: AppContainer, onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}
