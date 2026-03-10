package io.vinicius.sak.view.button

import androidx.compose.runtime.Composable

/**
 * A styled button component built on top of Material3 [androidx.compose.material3.Button].
 *
 * @param text Label text displayed inside the button.
 * @param onClick Called when the button is tapped.
 * @param enabled Controls whether the button responds to user input.
 *
 * TODO: add `loading: Boolean` — shows a CircularProgressIndicator inside the button
 * TODO: add `variant: SakButtonVariant` — supports Primary, Secondary, Outlined, Text
 * TODO: apply SakTheme colors and shape tokens
 */
@Composable
fun SakButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    // TODO: implement with Material3 Button + SakTheme styling:
    //   Button(
    //       onClick = onClick,
    //       enabled = enabled,
    //       colors = ButtonDefaults.buttonColors(
    //           containerColor = SakTheme.colors.primary
    //       ),
    //       shape = SakTheme.shapes.button
    //   ) {
    //       Text(text = text, style = SakTheme.typography.labelLarge)
    //   }
}
