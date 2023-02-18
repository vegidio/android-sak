package io.vinicius.sak.util

import io.vinicius.sak.util.internal.Repository
import org.junit.Test

class PrivateFlowTest {
    @Test
    fun blah() {
        val repo = Repository()
        print(repo.name.value)
    }
}