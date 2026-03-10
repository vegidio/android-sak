package io.vinicius.sak.view.input

import androidx.compose.runtime.Composable

/**
 * A styled text field built on top of Material3 [androidx.compose.material3.OutlinedTextField].
 *
 * @param value Current text value.
 * @param onValueChange Called when the text changes.
 * @param label Optional floating label above the field.
 *
 * TODO: add `placeholder: String` — hint text when the field is empty
 * TODO: add `errorMessage: String?` — shows error text below the field when non-null
 * TODO: add `keyboardType: KeyboardType` — controls the soft keyboard type
 * TODO: add `trailingIcon: @Composable (() -> Unit)?` — optional end icon (e.g., clear button)
 * TODO: apply SakTheme colors and shape tokens
 */
@Composable
fun SakTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
) {
    // TODO: implement with Material3 OutlinedTextField + SakTheme styling:
    //   OutlinedTextField(
    //       value = value,
    //       onValueChange = onValueChange,
    //       label = { Text(label) },
    //       isError = errorMessage != null,
    //       supportingText = errorMessage?.let { { Text(it) } },
    //       colors = OutlinedTextFieldDefaults.colors(
    //           focusedBorderColor = SakTheme.colors.primary
    //       )
    //   )
}
