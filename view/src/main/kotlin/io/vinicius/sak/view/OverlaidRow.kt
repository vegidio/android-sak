package io.vinicius.sak.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
inline fun OverlaidRow(
    modifier: Modifier = Modifier,
    state: Any? = null,
    overlaidStates: OverlaidStates = mutableMapOf(),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit
) {
    Box {
        Row(
            modifier = modifier,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
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
    OverlaidRow(
        overlaidStates = mutableMapOf(
            1 to {
                Box(modifier = Modifier.size(60.dp, 40.dp).background(Color.Red))
            }
        ),
        modifier = Modifier.size(60.dp, 40.dp).background(Color.Green)
    ) {}
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
private fun DefaultPreview2() {
    OverlaidRow(
        state = 1,
        overlaidStates = mutableMapOf(
            1 to {
                Box(modifier = Modifier.size(60.dp, 40.dp).background(Color.Red))
            }
        ),
        modifier = Modifier.size(60.dp, 40.dp).background(Color.Green)
    ) {}
}