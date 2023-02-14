package io.vinicius.sak.network.internal

import io.vinicius.sak.network.LogHandler
import io.vinicius.sak.network.RestFactory
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

class CountriesService : RestFactory<CountriesContract>(
    klass = CountriesContract::class,
    baseUrl = "https://restcountries.com/v3.1/",
    logHandler = LogHandler({ println(it) }, "BODY")
) {
    fun findByCode(code: String) = api.findByCode(code)
}

interface CountriesContract {
    @GET("alpha/{code}")
    fun findByCode(@Path("code") code: String): Call<Country>
}