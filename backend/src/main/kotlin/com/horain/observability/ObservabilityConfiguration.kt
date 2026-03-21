package com.horain.observability

import com.horain.agent.AgentTurnService
import com.horain.observability.langsmith.LangSmithApiClient
import com.horain.observability.langsmith.LangSmithPendingFeedbackService
import com.horain.observability.langsmith.LangSmithTraceSink
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.Executor

@Configuration
@EnableConfigurationProperties(ObservabilityProperties::class)
class ObservabilityConfiguration {

    @Bean(name = ["observabilityExecutor"])
    fun observabilityExecutor(): Executor {
        val pool = ThreadPoolTaskExecutor()
        pool.corePoolSize = 1
        pool.maxPoolSize = 4
        pool.queueCapacity = 512
        pool.setThreadNamePrefix("observability-")
        pool.initialize()
        return pool
    }

    @Bean
    @Primary
    fun agentTraceSink(
        props: ObservabilityProperties,
        webClientBuilder: WebClient.Builder,
        agentTurnService: AgentTurnService,
        meterRegistry: MeterRegistry,
        @Qualifier("observabilityExecutor") executor: Executor
    ): AgentTraceSink {
        if (props.resolvedProvider() == ObservabilityProvider.LANGSMITH && props.langsmith.apiKey.isNotBlank()) {
            val api = LangSmithApiClient(props.langsmith, webClientBuilder)
            val pending = LangSmithPendingFeedbackService(api, meterRegistry)
            return LangSmithTraceSink(props, api, agentTurnService, pending, meterRegistry, executor)
        }
        return NoOpAgentTraceSink()
    }
}
