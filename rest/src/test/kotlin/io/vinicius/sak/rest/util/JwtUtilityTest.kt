package io.vinicius.sak.rest.util

import java.util.Base64
import java.util.Date
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `isExpiringSoon - token expiring after threshold returns false`() {
        val expSeconds = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val token = buildJwt("""{"exp":$expSeconds}""")
        assertFalse(JwtUtility.isExpiringSoon(token, threshold = 60.seconds))
    }

    @Test
    fun `isExpiringSoon - token expiring within threshold returns true`() {
        val expSeconds = (System.currentTimeMillis() / 1000) + 30 // 30 seconds from now
        val token = buildJwt("""{"exp":$expSeconds}""")
        assertTrue(JwtUtility.isExpiringSoon(token, threshold = 60.seconds))
    }

    @Test
    fun `isExpiringSoon - already expired token returns true`() {
        val expSeconds = (System.currentTimeMillis() / 1000) - 100
        val token = buildJwt("""{"exp":$expSeconds}""")
        assertTrue(JwtUtility.isExpiringSoon(token, threshold = 60.seconds))
    }

    @Test
    fun `isExpiringSoon - token without exp claim treated as expired`() {
        val token = buildJwt("""{"sub":"user1"}""")
        assertTrue(JwtUtility.isExpiringSoon(token, threshold = 60.seconds))
    }

    @Test
    fun `isExpiringSoon - malformed token treated as expired`() {
        assertTrue(JwtUtility.isExpiringSoon("invalid", threshold = 60.seconds))
    }
}
