package io.vinicius.sak.util.internal

import io.vinicius.sak.util.PrivateFlow

class Repository : PrivateFlow {
    val name = privateStateFlow("Vinicius")
    val age = privateSharedFlow<Int>()

    init {
        name.mutable = "Egidio"
        age.tryEmit(10)
    }
}