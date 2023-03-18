package io.vinicius.sak.ktx

fun String.propercase(): String {
    return this.split(" ")
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}