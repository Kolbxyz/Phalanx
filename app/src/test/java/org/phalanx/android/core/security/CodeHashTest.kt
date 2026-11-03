package org.phalanx.android.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHashTest {
    @Test
    fun `the code it was made from matches`() = assertTrue(CodeHash.matches("482913", CodeHash.create("482913")))

    @Test
    fun `another code does not match`() = assertFalse(CodeHash.matches("482914", CodeHash.create("482913")))

    @Test
    fun `two hashes of one code differ by their salt`() =
        assertFalse(CodeHash.create("1234").contentEquals(CodeHash.create("1234")))

    @Test
    fun `a truncated record matches nothing`() =
        assertFalse(CodeHash.matches("1234", CodeHash.create("1234").copyOf(20)))

    @Test
    fun `only 4 to 16 digits are valid`() {
        assertTrue(CodeHash.isValid("1234"))
        assertTrue(CodeHash.isValid("1234567890123456"))
        assertFalse(CodeHash.isValid("123"))
        assertFalse(CodeHash.isValid("12345678901234567"))
        assertFalse(CodeHash.isValid("12a4"))
    }
}
