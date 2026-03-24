package com.horain.auth

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.util.ReflectionTestUtils

class ApiKeyFilterTest {

    @Test
    fun `rejects request without bearer token`() {
        val filter = ApiKeyFilter()
        ReflectionTestUtils.setField(filter, "expectedApiKey", "test-key")
        val request = MockHttpServletRequest("POST", "/mcp")
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
    }

    @Test
    fun `accepts request with valid bearer token`() {
        val filter = ApiKeyFilter()
        ReflectionTestUtils.setField(filter, "expectedApiKey", "test-key")
        val request = MockHttpServletRequest("POST", "/mcp")
        request.addHeader("Authorization", "Bearer test-key")
        val response = MockHttpServletResponse()
        var chainInvoked = false
        val chain = FilterChain { _, _ -> chainInvoked = true }

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        assertEquals(true, chainInvoked)
    }
}
