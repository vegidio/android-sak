package io.vinicius.sak.network.adapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LocalDateAdapter(pattern: String = "yyyy-MM-dd") {
    private val formatter = DateTimeFormatter.ofPattern(pattern)

    @FromJson
    fun fromJson(string: String) = LocalDate.parse(string, formatter)

    @ToJson
    fun toJson(value: LocalDate) = value.format(formatter)
}