package io.vinicius.sak.rest.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64
import java.util.Date
import kotlin.time.Duration

/**
 * Lightweight JWT utility that extracts the expiry claim without verifying the signature.
 *
 * Only the payload section (index 1) is decoded. No third-party JWT library is required.
 * Uses [java.util.Base64] which is available on Android API 26+ (minSdk is 26) and
 * also works in JVM unit tests.
 */
internal object JwtUtility {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses [token] and returns the [Date] represented by the `exp` claim,
     * or null if the token is malformed or the claim is absent.
     */
    fun expiryDate(token: String): Date? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null

            val payloadBytes = Base64.getUrlDecoder().decode(
                parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')
            )
            val payload = String(payloadBytes, Charsets.UTF_8)
            val exp = json.parseToJsonElement(payload)
                .jsonObject["exp"]
                ?.jsonPrimitive
                ?.longOrNull ?: return null

            Date(exp * 1_000L)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true if [token] expires within [threshold] from now,
     * or if the expiry claim cannot be parsed (treated as already expired).
     */
    fun isExpiringSoon(token: String, threshold: Duration): Boolean {
        val expiry = expiryDate(token) ?: return true
        return expiry.time - System.currentTimeMillis() <= threshold.inWholeMilliseconds
    }
}
