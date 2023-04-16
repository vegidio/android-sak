package io.vinicius.sak.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.vinicius.sak.network.adapter.BigDecimalAdapter
import io.vinicius.sak.network.adapter.LocalDateAdapter
import io.vinicius.sak.network.adapter.LocalDateTimeAdapter
import io.vinicius.sak.network.adapter.LocalTimeAdapter
import retrofit2.Converter
import retrofit2.converter.moshi.MoshiConverterFactory

object GeneralConverterFactory {
    fun moshi(): Converter.Factory {
        val moshi = Moshi.Builder()
            .add(BigDecimalAdapter())
            .add(LocalDateAdapter())
            .add(LocalTimeAdapter())
            .add(LocalDateTimeAdapter())
            .addLast(KotlinJsonAdapterFactory()) // Needed to deserialize without @JsonClass
            .build()

        return MoshiConverterFactory.create(moshi)
    }
}