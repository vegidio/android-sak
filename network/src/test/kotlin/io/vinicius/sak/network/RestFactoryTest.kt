package io.vinicius.sak.network

import io.vinicius.sak.network.internal.CountriesService
import org.junit.Test

class RestFactoryTest {
    @Test
    fun blah() {
        val service = CountriesService()
        service.findByCode("BRA")
    }
}