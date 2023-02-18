package io.vinicius.sak.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    chevron: @Composable () -> Unit = { DefaultChevron() },
    chevronInset: Boolean = false,
    divider: @Composable () -> Unit = { DefaultDivider() },
    content: @Composable () -> Unit
) {
    ConstraintLayout(modifier) {
        val (contentRef, chevronRef, dividerRef) = createRefs()

        Box(
            Modifier.constrainAs(contentRef) {
                width = Dimension.fillToConstraints
                start.linkTo(parent.start)
                end.linkTo(if (chevronInset) chevronRef.start else parent.end)
            }
        ) {
            content()
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .constrainAs(chevronRef) { end.linkTo(parent.end) }
        ) {
            chevron()
        }

        Box(
            Modifier.constrainAs(dividerRef) {
                centerHorizontallyTo(parent)
                bottom.linkTo(parent.bottom)
            }
        ) {
            divider()
        }
    }
}

@Composable
private fun DefaultChevron() {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
    )
}

@Composable
private fun DefaultDivider() {
    Divider(
        Modifier.fillMaxWidth(fraction = 0.9f)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
private fun DefaultPreview() {
    ListRow(Modifier.size(200.dp, 40.dp)) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp)
        ) {
            Text(text = "Lorem Ipsum")
        }
    }
}