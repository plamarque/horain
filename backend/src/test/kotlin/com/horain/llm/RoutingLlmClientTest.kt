package com.horain.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RoutingLlmClientTest {

    private lateinit var classifier: ComplexityClassifier
    private lateinit var clientSimple: LlmClient
    private lateinit var clientComplex: LlmClient
    private lateinit var clientVeryComplex: LlmClient
    private lateinit var router: RoutingLlmClient

    @BeforeEach
    fun setUp() {
        classifier = mock(ComplexityClassifier::class.java)
        clientSimple = mock(LlmClient::class.java)
        clientComplex = mock(LlmClient::class.java)
        clientVeryComplex = mock(LlmClient::class.java)
        `when`(clientSimple.isConfigured()).thenReturn(true)
        `when`(clientComplex.isConfigured()).thenReturn(true)
        `when`(clientVeryComplex.isConfigured()).thenReturn(true)
        router = RoutingLlmClient(
            classifier,
            clientSimple, clientComplex, clientVeryComplex,
            "gpt-4o-mini", "o4-mini", "gpt-5.4"
        )
    }

    @Test
    fun resolveClient_classifiesAndDelegatesToSimple() {
        `when`(classifier.classify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(ComplexityLevel.SIMPLE)
        `when`(clientSimple.chat(anyList(), anyList())).thenReturn(LlmResponse("Ok", null, "stop"))

        val messages = listOf(ChatMessage.user("yes"))
        val response = router.chat(messages, emptyList())

        assertEquals("Ok", response.content)
        verify(classifier).classify("yes", "")
        verify(clientSimple).chat(messages, emptyList())
        verify(clientComplex, never()).chat(anyList(), anyList())
        assertEquals("gpt-4o-mini", router.getLastSelectedModel())
    }

    @Test
    fun resolveClient_classifiesAndDelegatesToComplex() {
        `when`(classifier.classify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(ComplexityLevel.COMPLEX)
        `when`(clientComplex.chat(anyList(), anyList())).thenReturn(LlmResponse("Which one?", null, "stop"))

        val messages = listOf(ChatMessage.user("30 min on HatCast"))
        val response = router.chat(messages, emptyList())

        assertEquals("Which one?", response.content)
        verify(clientComplex).chat(messages, emptyList())
        assertEquals("o4-mini", router.getLastSelectedModel())
    }

    @Test
    fun resolveClient_reusesClientOnSecondCall() {
        `when`(classifier.classify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(ComplexityLevel.SIMPLE)
        `when`(clientSimple.chat(anyList(), anyList())).thenReturn(LlmResponse("Ok", null, "stop"))

        val messages1 = listOf(ChatMessage.user("yes"))
        router.chat(messages1, emptyList())
        val messages2 = listOf(ChatMessage.user("yes"), ChatMessage.assistant("Ok"), ChatMessage.user(""))
        router.chat(messages2, emptyList())

        verify(classifier, times(1)).classify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
        verify(clientSimple, times(2)).chat(anyList(), anyList())
    }

    @Test
    fun clearRequestScope_resetsSelection() {
        `when`(classifier.classify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(ComplexityLevel.SIMPLE)
        `when`(clientSimple.chat(anyList(), anyList())).thenReturn(LlmResponse("Ok", null, "stop"))

        router.chat(listOf(ChatMessage.user("yes")), emptyList())
        assertNotNull(router.getLastSelectedModel())

        router.clearRequestScope()
        assertNull(router.getLastSelectedModel())

        `when`(classifier.classify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(ComplexityLevel.COMPLEX)
        `when`(clientComplex.chat(anyList(), anyList())).thenReturn(LlmResponse("Done", null, "stop"))
        router.chat(listOf(ChatMessage.user("new request")), emptyList())
        assertEquals("o4-mini", router.getLastSelectedModel())
        verify(classifier, times(2)).classify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun isConfigured_trueWhenAnyClientConfigured() {
        `when`(clientSimple.isConfigured()).thenReturn(false)
        `when`(clientComplex.isConfigured()).thenReturn(false)
        `when`(clientVeryComplex.isConfigured()).thenReturn(true)
        assertTrue(router.isConfigured())
    }
}
