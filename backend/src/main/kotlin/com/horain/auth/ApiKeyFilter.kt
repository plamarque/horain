package com.horain.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Simple API key filter.
 * Expects Authorization: Bearer HORAIN_DEV_KEY.
 * Rejects request with 401 if invalid or missing.
 */
@Component
class ApiKeyFilter : OncePerRequestFilter() {

    @Value("\${horain.api-key:HORAIN_DEV_KEY}")
    private lateinit var expectedApiKey: String

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI == "/health") {
            filterChain.doFilter(request, response)
            return
        }
        // CORS preflight: browser sends OPTIONS without Authorization; must pass through.
        if (request.method.equals("OPTIONS", ignoreCase = true)) {
            filterChain.doFilter(request, response)
            return
        }
        val authHeader = request.getHeader(AUTH_HEADER)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header")
            return
        }
        val token = authHeader.substring(BEARER_PREFIX.length).trim()
        if (expectedApiKey != token) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key")
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val AUTH_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
