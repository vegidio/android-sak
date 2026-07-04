package io.vinicius.sak.rest.compiler

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * KSP entry point. Registered via `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`.
 */
class RestServiceProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RestServiceProcessor(environment.codeGenerator, environment.logger)
}