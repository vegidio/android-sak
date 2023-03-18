package io.vinicius.sak.view

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun OverlaidLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: Any? = null,
    overlaidStates: OverlaidStates = mutableMapOf(),
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit
) {
    Box {
        LazyVerticalGrid(
            columns = columns,
            modifier = modifier,
            state = gridState,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
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
    OverlaidLazyVerticalGrid(
        columns = GridCells.Fixed(count = 3),
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
    OverlaidLazyVerticalGrid(
        columns = GridCells.Fixed(count = 3),
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