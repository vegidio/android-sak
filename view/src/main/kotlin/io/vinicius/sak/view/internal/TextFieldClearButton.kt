package io.vinicius.sak.view.internal

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun TextFieldClearButton(onClear: () -> Unit) {
    Icon(
        imageVector = Icons.Default.Clear,
        contentDescription = "Clear",
        modifier = Modifier.clickable { onClear() }
    )
}