package com.horain.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class RoutingLlmClientTest {

    private ComplexityClassifier classifier;
    private LlmClient clientSimple;
    private LlmClient clientComplex;
    private LlmClient clientVeryComplex;
    private RoutingLlmClient router;

    @BeforeEach
    void setUp() {
        classifier = mock(ComplexityClassifier.class);
        clientSimple = mock(LlmClient.class);
        clientComplex = mock(LlmClient.class);
        clientVeryComplex = mock(LlmClient.class);
        when(clientSimple.isConfigured()).thenReturn(true);
        when(clientComplex.isConfigured()).thenReturn(true);
        when(clientVeryComplex.isConfigured()).thenReturn(true);
        router = new RoutingLlmClient(
                classifier,
                clientSimple, clientComplex, clientVeryComplex,
                "gpt-4o-mini", "o4-mini", "gpt-5.4");
    }

    @Test
    void resolveClient_classifiesAndDelegatesToSimple() {
        when(classifier.classify(any(), any())).thenReturn(ComplexityLevel.SIMPLE);
        when(clientSimple.chat(anyList(), anyList())).thenReturn(new LlmResponse("Ok", null, "stop"));

        List<ChatMessage> messages = List.of(ChatMessage.user("yes"));
        LlmResponse response = router.chat(messages, List.of());

        assertEquals("Ok", response.content());
        verify(classifier).classify("yes", "");
        verify(clientSimple).chat(messages, List.of());
        verify(clientComplex, never()).chat(anyList(), anyList());
        assertEquals("gpt-4o-mini", router.getLastSelectedModel());
    }

    @Test
    void resolveClient_classifiesAndDelegatesToComplex() {
        when(classifier.classify(any(), any())).thenReturn(ComplexityLevel.COMPLEX);
        when(clientComplex.chat(anyList(), anyList())).thenReturn(new LlmResponse("Which one?", null, "stop"));

        List<ChatMessage> messages = List.of(ChatMessage.user("30 min on HatCast"));
        LlmResponse response = router.chat(messages, List.of());

        assertEquals("Which one?", response.content());
        verify(clientComplex).chat(messages, List.of());
        assertEquals("o4-mini", router.getLastSelectedModel());
    }

    @Test
    void resolveClient_reusesClientOnSecondCall() {
        when(classifier.classify(any(), any())).thenReturn(ComplexityLevel.SIMPLE);
        when(clientSimple.chat(anyList(), anyList())).thenReturn(new LlmResponse("Ok", null, "stop"));

        List<ChatMessage> messages1 = List.of(ChatMessage.user("yes"));
        router.chat(messages1, List.of());
        List<ChatMessage> messages2 = List.of(ChatMessage.user("yes"), ChatMessage.assistant("Ok"), ChatMessage.user(""));
        router.chat(messages2, List.of());

        verify(classifier, times(1)).classify(any(), any());
        verify(clientSimple, times(2)).chat(anyList(), anyList());
    }

    @Test
    void clearRequestScope_resetsSelection() {
        when(classifier.classify(any(), any())).thenReturn(ComplexityLevel.SIMPLE);
        when(clientSimple.chat(anyList(), anyList())).thenReturn(new LlmResponse("Ok", null, "stop"));

        router.chat(List.of(ChatMessage.user("yes")), List.of());
        assertNotNull(router.getLastSelectedModel());

        router.clearRequestScope();
        assertNull(router.getLastSelectedModel());

        when(classifier.classify(any(), any())).thenReturn(ComplexityLevel.COMPLEX);
        when(clientComplex.chat(anyList(), anyList())).thenReturn(new LlmResponse("Done", null, "stop"));
        router.chat(List.of(ChatMessage.user("new request")), List.of());
        assertEquals("o4-mini", router.getLastSelectedModel());
        verify(classifier, times(2)).classify(any(), any());
    }

    @Test
    void isConfigured_trueWhenAnyClientConfigured() {
        when(clientSimple.isConfigured()).thenReturn(false);
        when(clientComplex.isConfigured()).thenReturn(false);
        when(clientVeryComplex.isConfigured()).thenReturn(true);
        assertTrue(router.isConfigured());
    }
}
