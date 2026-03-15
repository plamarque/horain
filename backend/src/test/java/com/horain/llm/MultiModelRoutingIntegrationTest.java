package com.horain.llm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: when all three model levels are configured, the LlmClient bean
 * is a RoutingLlmClient.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "llm.api-key=test-key",
        "llm.model-simple=gpt-4o-mini",
        "llm.model-complex=o4-mini",
        "llm.model-very-complex=gpt-5.4"
})
class MultiModelRoutingIntegrationTest {

    @Autowired
    private LlmClient llmClient;

    @Test
    void llmClientIsRoutingLlmClientWhenThreeModelsConfigured() {
        assertThat(llmClient).isInstanceOf(RoutingLlmClient.class);
        assertThat(llmClient.isConfigured()).isTrue();
    }
}
