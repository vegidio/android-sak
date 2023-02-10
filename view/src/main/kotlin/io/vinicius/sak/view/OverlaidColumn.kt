package io.vinicius.sak.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
inline fun OverlaidColumn(
    modifier: Modifier = Modifier,
    state: Any? = null,
    overlaidStates: OverlaidStates = mutableMapOf(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        Column(
            modifier = modifier,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )

        state?.let {
            overlaidStates[it]?.invoke()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
private fun DefaultPreview1() {
    OverlaidColumn(
        overlaidStates = mutableMapOf(
            1 to {
                Box(modifier = Modifier.size(40.dp, 60.dp).background(Color.Red))
            }
        ),
        modifier = Modifier.size(40.dp, 60.dp).background(Color.Green)
    ) {}
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
private fun DefaultPreview2() {
    OverlaidColumn(
        state = 1,
        overlaidStates = mutableMapOf(
            1 to {
                Box(modifier = Modifier.size(40.dp, 60.dp).background(Color.Red))
            }
        ),
        modifier = Modifier.size(40.dp, 60.dp).background(Color.Green)
    ) {}
}