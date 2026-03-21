package com.horain.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * Integration test: when all three model levels are configured, the LlmClient bean
 * is a RoutingLlmClient.
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "llm.api-key=test-key",
        "llm.model-simple=gpt-4o-mini",
        "llm.model-complex=o4-mini",
        "llm.model-very-complex=gpt-5.4"
    ]
)
class MultiModelRoutingIntegrationTest {

    @Autowired
    private lateinit var llmClient: LlmClient

    @Test
    fun llmClientIsRoutingLlmClientWhenThreeModelsConfigured() {
        assertThat(llmClient).isInstanceOf(RoutingLlmClient::class.java)
        assertThat(llmClient.isConfigured()).isTrue()
    }
}
