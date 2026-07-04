package io.vinicius.sak.rest.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Reads every interface annotated with [io.vinicius.sak.rest.annotation.Service] and generates, per service:
 *
 *  1. an `internal` Retrofit interface (`<Name>Retrofit`) — a copy of each method whose return type `T` is rewritten to
 *     `retrofit2.Response<T>`, preserving the Retrofit annotations. This is what Retrofit's runtime proxy is built from.
 *  2. a public client (`<Name>Client`) — mirrors each method's parameters, returns `RestResponse<T>`, and delegates to
 *     the Retrofit service, wrapping the result via `RestResponse.from(...)`.
 */
class RestServiceProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(SERVICE_ANNOTATION).toList()
        val (valid, deferred) = symbols.partition { it.validate() }

        valid.filterIsInstance<KSClassDeclaration>().forEach { generate(it) }

        return deferred
    }

    private fun generate(service: KSClassDeclaration) {
        if (service.classKind != ClassKind.INTERFACE) {
            logger.error("@Service can only be applied to an interface.", service)
            return
        }

        val packageName = service.packageName.asString()
        val serviceName = service.simpleName.asString()
        val retrofitName = "${serviceName}Retrofit"
        val clientName = "${serviceName}Client"
        val retrofitClass = ClassName(packageName, retrofitName)

        val classResolver = service.typeParameters.toTypeParameterResolver()
        val functions = service.getDeclaredFunctions().toList()

        val retrofitInterface = TypeSpec
            .interfaceBuilder(retrofitName)
            .addModifiers(KModifier.INTERNAL)
            .addKdoc("Generated from [%L]. Do not edit.", serviceName)
        val clientMethods = mutableListOf<FunSpec>()

        for (function in functions) {
            if (Modifier.SUSPEND !in function.modifiers) {
                logger.error("@Service methods must be `suspend`.", function)
                continue
            }

            val funcResolver = function.typeParameters.toTypeParameterResolver(classResolver)
            val bodyType = function.returnType!!
                .resolve()
                .toTypeName(funcResolver)
                .copy(nullable = false)
            val funcName = function.simpleName.asString()

            retrofitInterface.addFunction(buildRetrofitFunction(function, funcResolver, bodyType))
            clientMethods += buildClientMethod(function, funcName, funcResolver, bodyType)
        }

        val retrofitFile = FileSpec
            .builder(packageName, retrofitName)
            .addType(retrofitInterface.build())
            .build()

        val clientFile = FileSpec
            .builder(packageName, clientName)
            .addType(buildClient(clientName, retrofitClass, clientMethods))
            .build()

        val originating = listOfNotNull(service.containingFile)
        retrofitFile.writeTo(codeGenerator, aggregating = false, originatingKSFiles = originating)
        clientFile.writeTo(codeGenerator, aggregating = false, originatingKSFiles = originating)
    }

    /** Copy of the user's method with the Retrofit annotations preserved and the return type wrapped in `Response<T>`. */
    private fun buildRetrofitFunction(
        function: KSFunctionDeclaration,
        funcResolver: TypeParameterResolver,
        bodyType: TypeName,
    ): FunSpec {
        val builder = FunSpec
            .builder(function.simpleName.asString())
            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            .returns(RETROFIT_RESPONSE.parameterizedBy(bodyType))

        function.annotations.forEach { builder.addAnnotation(it.toAnnotationSpec()) }

        function.parameters.forEach { param ->
            val paramSpec = ParameterSpec.builder(
                param.name!!.asString(),
                param.type.toTypeName(funcResolver),
            )
            param.annotations.forEach { paramSpec.addAnnotation(it.toAnnotationSpec()) }
            builder.addParameter(paramSpec.build())
        }

        return builder.build()
    }

    /** Public facade method: same signature (minus Retrofit annotations), returns `RestResponse<T>`. */
    private fun buildClientMethod(
        function: KSFunctionDeclaration,
        funcName: String,
        funcResolver: TypeParameterResolver,
        bodyType: TypeName,
    ): FunSpec {
        val builder = FunSpec
            .builder(funcName)
            .addModifiers(KModifier.SUSPEND)
            .returns(REST_RESPONSE.parameterizedBy(bodyType))

        function.parameters.forEach { param ->
            builder.addParameter(param.name!!.asString(), param.type.toTypeName(funcResolver))
        }

        val callArgs = function.parameters.joinToString(", ") { it.name!!.asString() }
        builder.addStatement("return %T.from(service.$funcName($callArgs))", REST_RESPONSE)

        return builder.build()
    }

    private fun buildClient(
        clientName: String,
        retrofitClass: ClassName,
        methods: List<FunSpec>,
    ): TypeSpec {
        val primaryConstructor = FunSpec
            .constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
            .addParameter("client", REST_CLIENT)
            .addParameter("ownsClient", BOOLEAN)
            .build()

        val closeFunction = FunSpec
            .builder("close")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("if (ownsClient) client.close()")
            .build()

        return TypeSpec
            .classBuilder(clientName)
            .addKdoc("Generated REST client. Do not edit.")
            .addSuperinterface(AUTO_CLOSEABLE)
            .primaryConstructor(primaryConstructor)
            .addProperty(
                PropertySpec
                    .builder("client", REST_CLIENT, KModifier.PRIVATE)
                    .initializer("client")
                    .build(),
            ).addProperty(
                PropertySpec
                    .builder("ownsClient", BOOLEAN, KModifier.PRIVATE)
                    .initializer("ownsClient")
                    .build(),
            ).addProperty(
                PropertySpec
                    .builder("service", retrofitClass, KModifier.PRIVATE)
                    .initializer("client.retrofit.create(%T::class.java)", retrofitClass)
                    .build(),
            ).addFunction(buildOwningConstructor())
            .addFunction(
                FunSpec
                    .constructorBuilder()
                    .addParameter("client", REST_CLIENT)
                    .callThisConstructor(CodeBlock.of("client"), CodeBlock.of("false"))
                    .build(),
            ).addFunctions(methods)
            .addFunction(closeFunction)
            .build()
    }

    /**
     * The owning constructor: mirrors [RestClient]'s primary constructor parameters (see [CONFIG_PARAMS]) and forwards
     * them positionally, so callers pass options directly — `baseUrl` mandatory, everything else optional — instead of a
     * wrapper object. The defaults are kept in sync with [RestClient] manually.
     */
    private fun buildOwningConstructor(): FunSpec {
        val builder = FunSpec.constructorBuilder()
        CONFIG_PARAMS.forEach { param ->
            val spec = ParameterSpec.builder(param.name, param.type)
            param.default?.let { spec.defaultValue(it) }
            builder.addParameter(spec.build())
        }

        val forwarded = CONFIG_PARAMS.joinToString(", ") { it.name }
        builder.callThisConstructor(
            CodeBlock.of("%T($forwarded)", REST_CLIENT),
            CodeBlock.of("true"),
        )

        return builder.build()
    }

    /** A single [RestClient] constructor parameter to replicate on the generated client. */
    private data class ConfigParam(
        val name: String,
        val type: TypeName,
        val default: CodeBlock?,
    )

    private companion object {
        const val SERVICE_ANNOTATION = "io.vinicius.sak.rest.annotation.Service"

        val REST_CLIENT = ClassName("io.vinicius.sak.rest", "RestClient")
        val REST_RESPONSE = ClassName("io.vinicius.sak.rest", "RestResponse")
        val RETROFIT_RESPONSE = ClassName("retrofit2", "Response")
        val AUTO_CLOSEABLE = ClassName("kotlin", "AutoCloseable")

        val RETRY_POLICY = ClassName("io.vinicius.sak.rest", "RetryPolicy")
        val CACHE_POLICY = ClassName("io.vinicius.sak.rest", "CachePolicy")
        val DURATION = ClassName("kotlin.time", "Duration")
        val SECONDS = MemberName(DURATION.nestedClass("Companion"), "seconds")
        val TOKEN_PROVIDER = LambdaTypeName
            .get(returnType = STRING.copy(nullable = true))
            .copy(nullable = true, suspending = true)
        val TOKEN_REFRESHER = LambdaTypeName
            .get(returnType = BOOLEAN)
            .copy(nullable = true, suspending = true)

        // Mirrors RestClient's primary constructor — same names, types, and order. Keep in sync.
        val CONFIG_PARAMS = listOf(
            ConfigParam("baseUrl", STRING, null),
            ConfigParam("defaultHeaders", MAP.parameterizedBy(STRING, STRING), CodeBlock.of("emptyMap()")),
            ConfigParam("retryPolicy", RETRY_POLICY, CodeBlock.of("%T()", RETRY_POLICY)),
            ConfigParam("cachePolicy", CACHE_POLICY, CodeBlock.of("%T()", CACHE_POLICY)),
            ConfigParam("tokenProvider", TOKEN_PROVIDER, CodeBlock.of("null")),
            ConfigParam("tokenRefresher", TOKEN_REFRESHER, CodeBlock.of("null")),
            ConfigParam("preemptiveRefresh", DURATION, CodeBlock.of("%L.%M", 60, SECONDS)),
            ConfigParam("connectTimeout", DURATION, CodeBlock.of("%L.%M", 30, SECONDS)),
            ConfigParam("readTimeout", DURATION, CodeBlock.of("%L.%M", 30, SECONDS)),
        )
    }
}