package io.vinicius.sak.network.internal

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Country(
    val region: String,
    val population: Int
)