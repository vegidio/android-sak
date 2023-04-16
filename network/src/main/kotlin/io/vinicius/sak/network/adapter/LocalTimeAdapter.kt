package io.vinicius.sak.network.adapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class LocalTimeAdapter(pattern: String = "HH:mm:ss") {
    private val formatter = DateTimeFormatter.ofPattern(pattern)

    @FromJson
    fun fromJson(string: String) = LocalTime.parse(string, formatter)

    @ToJson
    fun toJson(value: LocalTime) = value.format(formatter)
}