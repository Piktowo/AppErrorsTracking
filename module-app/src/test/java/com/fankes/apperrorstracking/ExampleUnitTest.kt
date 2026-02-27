package com.fankes.apperrorstracking

import com.fankes.apperrorstracking.utils.factory.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

data class TestPayload(val value: String)

class ExampleUnitTest {

    @Test
    fun `toEntity returns null for blank json`() {
        assertNull("".toEntity<TestPayload>())
        assertNull("   ".toEntity<TestPayload>())
    }

    @Test
    fun `toEntity parses valid json`() {
        assertEquals(TestPayload("ok"), "{\"value\":\"ok\"}".toEntity<TestPayload>())
    }
}
