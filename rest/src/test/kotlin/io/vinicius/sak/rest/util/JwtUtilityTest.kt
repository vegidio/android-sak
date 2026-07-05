package io.vinicius.sak.rest.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.Date

class JwtUtilityTest {
    // Builds a fake (unsigned) JWT with the given payload JSON string
    private fun buildJwt(payloadJson: String): String {
        val header =
            Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray())
        return "$header.$payload.fakesignature"
    }

    @Test
    fun `expiryDate - valid JWT with future exp returns correct Date`() {
        val expSeconds = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val token = buildJwt("""{"sub":"user1","exp":$expSeconds}""")
        val result = JwtUtility.expiryDate(token)
        assertEquals(expSeconds * 1000L, result?.time)
    }

    @Test
    fun `expiryDate - JWT with past exp returns past Date`() {
        val expSeconds = (System.currentTimeMillis() / 1000) - 3600 // 1 hour ago
        val token = buildJwt("""{"sub":"user1","exp":$expSeconds}""")
        val result = JwtUtility.expiryDate(token)
        assertTrue(result!!.before(Date()))
    }

    @Test
    fun `expiryDate - malformed token with wrong part count returns null`() {
        assertNull(JwtUtility.expiryDate("notavalidtoken"))
        assertNull(JwtUtility.expiryDate("only.twoparts"))
    }

    @Test
    fun `expiryDate - missing exp claim returns null`() {
        val token = buildJwt("""{"sub":"user1","iat":1700000000}""")
        assertNull(JwtUtility.expiryDate(token))
    }

    @Test
    fun `expiryDate - invalid base64 payload returns null`() {
        assertNull(JwtUtility.expiryDate("header.!!!invalid!!!.signature"))
    }
}