package io.vinicius.sak.network.adapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LocalDateTimeAdapter(pattern: String = "yyyy-MM-dd'T'HH:mm:ss'Z'") {
    private val formatter = DateTimeFormatter.ofPattern(pattern)

    @FromJson
    fun fromJson(string: String) = LocalDateTime.parse(string, formatter)

    @ToJson
    fun toJson(value: LocalDateTime) = value.format(formatter)
}