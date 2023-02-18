package io.vinicius.sak.view

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun OverlaidLazyColumn(
    modifier: Modifier = Modifier,
    state: Any? = null,
    overlaidStates: OverlaidStates = mutableMapOf(),
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    Box {
        LazyColumn(
            modifier = modifier,
            state = listState,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
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
    OverlaidLazyColumn(
        overlaidStates = mutableMapOf(
            1 to {
                Box(
                    modifier = Modifier
                        .size(40.dp, 60.dp)
                        .background(Color.Red)
                )
            }
        ),
        modifier = Modifier
            .size(40.dp, 60.dp)
            .background(Color.Green)
    ) {}
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
private fun DefaultPreview2() {
    OverlaidLazyColumn(
        state = 1,
        overlaidStates = mutableMapOf(
            1 to {
                Box(
                    modifier = Modifier
                        .size(40.dp, 60.dp)
                        .background(Color.Red)
                )
            }
        ),
        modifier = Modifier
            .size(40.dp, 60.dp)
            .background(Color.Green)
    ) {}
}