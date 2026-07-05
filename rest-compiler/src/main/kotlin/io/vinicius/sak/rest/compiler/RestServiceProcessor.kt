package io.vinicius.sak.rest.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
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
import com.squareup.kotlinpoet.UNIT
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

        // Service-level defaults: applied to eligible methods that don't declare their own policy.
        val serviceCacheable = service.annotationNamed(CACHEABLE)
        val serviceRetry = service.annotationNamed(RETRY)

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

            validateRetryTarget(function)
            validateCacheTarget(function)

            val funcResolver = function.typeParameters.toTypeParameterResolver(classResolver)
            val bodyType = function.returnType!!
                .resolve()
                .toTypeName(funcResolver)
                .copy(nullable = false)

            retrofitInterface.addFunction(
                buildRetrofitFunction(function, funcResolver, bodyType, serviceCacheable, serviceRetry),
            )
            clientMethods += buildClientMethod(function, funcResolver, bodyType)
        }

        // maxEntries sizes the shared cache, so it's honored only from the service-level @Cacheable.
        val cacheMaxEntries = serviceCacheable?.let { readIntArg(it, "maxEntries") }

        val retrofitFile = FileSpec
            .builder(packageName, retrofitName)
            .addType(retrofitInterface.build())
            .build()

        val clientFile = FileSpec
            .builder(packageName, clientName)
            .addType(buildClient(clientName, retrofitClass, clientMethods, cacheMaxEntries))
            .build()

        val originating = listOfNotNull(service.containingFile)
        retrofitFile.writeTo(codeGenerator, aggregating = false, originatingKSFiles = originating)
        clientFile.writeTo(codeGenerator, aggregating = false, originatingKSFiles = originating)
    }

    /**
     * A method-level `@Retry`/`@NoRetry` may only be applied to idempotent verbs. Retrying a POST/PATCH could duplicate
     * a non-idempotent request, so it's a compile-time error — mirroring the iOS macro's constraint.
     */
    private fun validateRetryTarget(function: KSFunctionDeclaration) {
        val hasRetryAnnotation = function.hasAnnotation(RETRY) || function.hasAnnotation(NO_RETRY)
        if (hasRetryAnnotation && function.httpVerb()?.idempotent == false) {
            logger.error(
                "@Retry/@NoRetry can only be applied to idempotent methods (GET, PUT, DELETE), not POST/PATCH.",
                function,
            )
        }
    }

    /**
     * A method-level `@Cacheable` may only appear on a GET method, and may not set `maxEntries` — the latter sizes the
     * shared cache and is honored only from the service-level annotation. Both are compile-time errors, mirroring the
     * iOS macro's constraints.
     */
    private fun validateCacheTarget(function: KSFunctionDeclaration) {
        val cacheable = function.annotationNamed(CACHEABLE) ?: return

        if (function.httpVerb() != HttpVerb.GET) {
            logger.error("@Cacheable can only be applied to GET methods.", function)
        }

        val maxEntries = readIntArg(cacheable, "maxEntries")
        if (maxEntries != null && maxEntries != CACHE_UNLIMITED) {
            logger.error(
                "@Cacheable(maxEntries) is not allowed on a method; set it on the @Service interface instead — " +
                    "it sizes the shared cache.",
                function,
            )
        }
    }

    /**
     * Copy of the user's method with the Retrofit annotations preserved and the return type wrapped in`Response<T>`.
     */
    private fun buildRetrofitFunction(
        function: KSFunctionDeclaration,
        funcResolver: TypeParameterResolver,
        bodyType: TypeName,
        serviceCacheable: KSAnnotation?,
        serviceRetry: KSAnnotation?,
    ): FunSpec {
        val builder = FunSpec
            .builder(function.simpleName.asString())
            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            .returns(RETROFIT_RESPONSE.parameterizedBy(bodyType))

        // Method-level annotations (Retrofit verbs, @Cacheable/@Retry/@NoCache/@NoRetry, @SkipAuth) are copied
        // verbatim.
        function.annotations.forEach { builder.addAnnotation(it.toAnnotationSpec()) }

        // Propagate service-level defaults onto eligible methods that don't declare their own policy.
        val verb = function.httpVerb()
        val declaresCache = function.hasAnnotation(CACHEABLE) || function.hasAnnotation(NO_CACHE)
        val declaresRetry = function.hasAnnotation(RETRY) || function.hasAnnotation(NO_RETRY)

        if (serviceCacheable != null && verb == HttpVerb.GET && !declaresCache) {
            builder.addAnnotation(serviceCacheable.toAnnotationSpec())
        }

        if (serviceRetry != null && verb?.idempotent == true && !declaresRetry) {
            builder.addAnnotation(serviceRetry.toAnnotationSpec())
        }

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

    /**
     * Public facade method: same signature (minus Retrofit annotations), returns `RestResponse<T>`.
     */
    private fun buildClientMethod(
        function: KSFunctionDeclaration,
        funcResolver: TypeParameterResolver,
        bodyType: TypeName,
    ): FunSpec {
        val funcName = function.simpleName.asString()
        val builder = FunSpec
            .builder(funcName)
            .addModifiers(KModifier.SUSPEND)
            .returns(REST_RESPONSE.parameterizedBy(bodyType))

        val paramNames = function.parameters.map { it.name!!.asString() }
        paramNames.forEachIndexed { index, name ->
            builder.addParameter(name, function.parameters[index].type.toTypeName(funcResolver))
        }

        val callArgs = paramNames.joinToString(", ") { "%N" }
        val statementArgs = listOf(REST_CALL, funcName, *paramNames.toTypedArray()).toTypedArray()
        // Delegate through restCall so every failure maps onto the sealed RestError hierarchy.
        builder.addStatement("return %M { service.%N($callArgs) }", *statementArgs)

        return builder.build()
    }

    private fun buildClient(
        clientName: String,
        retrofitClass: ClassName,
        methods: List<FunSpec>,
        cacheMaxEntries: Int?,
    ): TypeSpec =
        TypeSpec
            .classBuilder(clientName)
            .addKdoc("Generated REST client. Do not edit.")
            .superclass(SERVICE_CLIENT)
            .primaryConstructor(buildConfigConstructor(cacheMaxEntries))
            .apply {
                // Forward each option to the ServiceClient base by name so a reorder of the base/RestClient
                // constructor fails to compile loudly.
                CONFIG_PARAMS.forEach { addSuperclassConstructorParameter("%L = %L", it.name, it.name) }
            }.addProperty(
                PropertySpec
                    .builder("service", retrofitClass, KModifier.PRIVATE)
                    // Uses the protected `retrofit` inherited from ServiceClient.
                    .initializer("retrofit.create(%T::class.java)", retrofitClass)
                    .build(),
            ).addFunctions(methods)
            .build()

    /**
     * The generated client's primary constructor: mirrors [RestClient]'s constructor parameters (see [CONFIG_PARAMS],
     * which the [ServiceClient] base forwards to), so callers pass options directly — `baseUrl` mandatory, everything
     * else optional. The defaults are kept in sync with [RestClient] manually.
     */
    private fun buildConfigConstructor(cacheMaxEntries: Int?): FunSpec {
        val builder = FunSpec.constructorBuilder()
        CONFIG_PARAMS.forEach { param ->
            val spec = ParameterSpec.builder(param.name, param.type)
            // The service-level @Cacheable(maxEntries) sizes the shared cache; fall back to the static default.
            val default = if (param.sizedByServiceCache && cacheMaxEntries != null) {
                CodeBlock.of("%L", cacheMaxEntries)
            } else {
                param.default
            }
            default?.let { spec.defaultValue(it) }
            builder.addParameter(spec.build())
        }
        return builder.build()
    }

    /**
     * A single [RestClient] constructor parameter to replicate on the generated client. When [sizedByServiceCache] is
     * set, the service-level `@Cacheable(maxEntries)` (when present) overrides [default] — this keeps the override tied
     * to the parameter object instead of matching its name as a string.
     */
    private data class ConfigParam(
        val name: String,
        val type: TypeName,
        val default: CodeBlock?,
        val sizedByServiceCache: Boolean = false,
    )

    private companion object {
        const val SERVICE_ANNOTATION = "io.vinicius.sak.rest.annotation.Service"

        const val CACHEABLE = "io.vinicius.sak.rest.annotation.Cacheable"
        const val NO_CACHE = "io.vinicius.sak.rest.annotation.NoCache"
        const val RETRY = "io.vinicius.sak.rest.annotation.Retry"
        const val NO_RETRY = "io.vinicius.sak.rest.annotation.NoRetry"

        // Mirrors Cacheable.UNLIMITED — the default that means "no method-level maxEntries was set".
        const val CACHE_UNLIMITED = -1

        val SERVICE_CLIENT = ClassName("io.vinicius.sak.rest", "ServiceClient")
        val REST_RESPONSE = ClassName("io.vinicius.sak.rest", "RestResponse")
        val REST_CALL = MemberName("io.vinicius.sak.rest", "restCall")
        val RETROFIT_RESPONSE = ClassName("retrofit2", "Response")
        val OKHTTP_RESPONSE = ClassName("okhttp3", "Response")

        val DURATION = ClassName("kotlin.time", "Duration")
        val SECONDS = MemberName(DURATION.nestedClass("Companion"), "seconds")
        val TOKEN_PROVIDER = LambdaTypeName
            .get(returnType = STRING.copy(nullable = true))
            .copy(nullable = true, suspending = true)
        val TOKEN_REFRESHER = LambdaTypeName
            .get(returnType = STRING)
            .copy(nullable = true, suspending = true)
        val IS_UNAUTHORIZED = LambdaTypeName
            .get(returnType = BOOLEAN, parameters = arrayOf(OKHTTP_RESPONSE))
        val LOGGING_TYPE = LambdaTypeName
            .get(returnType = UNIT, parameters = arrayOf(STRING))
            .copy(nullable = true)

        // Mirrors RestClient's primary constructor — same names, types, and order. The generated client forwards
        // these to the ServiceClient base, which forwards them to RestClient. Keep all three in sync.
        val CONFIG_PARAMS = listOf(
            ConfigParam("baseUrl", STRING, null),
            ConfigParam("defaultHeaders", MAP.parameterizedBy(STRING, STRING), CodeBlock.of("emptyMap()")),
            ConfigParam("cacheMaxEntries", INT, CodeBlock.of("%L", -1), sizedByServiceCache = true),
            ConfigParam("tokenProvider", TOKEN_PROVIDER, CodeBlock.of("null")),
            ConfigParam("tokenRefresher", TOKEN_REFRESHER, CodeBlock.of("null")),
            ConfigParam("preemptiveRefresh", DURATION.copy(nullable = true), CodeBlock.of("%L.%M", 60, SECONDS)),
            ConfigParam("isUnauthorized", IS_UNAUTHORIZED, CodeBlock.of("{ it.code == %L }", 401)),
            ConfigParam("connectTimeout", DURATION, CodeBlock.of("%L.%M", 30, SECONDS)),
            ConfigParam("readTimeout", DURATION, CodeBlock.of("%L.%M", 30, SECONDS)),
            ConfigParam("logging", LOGGING_TYPE, CodeBlock.of("null")),
        )
    }
}

/** Retrofit HTTP verbs the processor recognizes, and whether each is safe to retry. */
private enum class HttpVerb(
    val qualifiedName: String,
    val idempotent: Boolean,
) {
    GET("retrofit2.http.GET", idempotent = true),
    PUT("retrofit2.http.PUT", idempotent = true),
    DELETE("retrofit2.http.DELETE", idempotent = true),
    POST("retrofit2.http.POST", idempotent = false),
    PATCH("retrofit2.http.PATCH", idempotent = false),
}

/** Fully-qualified name of an annotation usage, or null if it can't be resolved. */
private fun KSAnnotation.qualifiedName(): String? =
    annotationType
        .resolve()
        .declaration.qualifiedName
        ?.asString()

private fun KSAnnotated.annotationNamed(qualifiedName: String): KSAnnotation? =
    annotations.firstOrNull { it.qualifiedName() == qualifiedName }

private fun KSAnnotated.hasAnnotation(qualifiedName: String): Boolean = annotationNamed(qualifiedName) != null

/** The Retrofit verb declared on [this], or null (e.g. custom `@HTTP`). */
private fun KSFunctionDeclaration.httpVerb(): HttpVerb? {
    val names = annotations.mapNotNull { it.qualifiedName() }.toSet()
    return HttpVerb.entries.firstOrNull { it.qualifiedName in names }
}

/** Reads a named `Int` argument from an annotation usage (KSP resolves defaults), or null if absent. */
private fun readIntArg(
    annotation: KSAnnotation,
    name: String,
): Int? = annotation.arguments.firstOrNull { it.name?.asString() == name }?.value as? Int