package com.horain.agent

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Condition for enabling ExportEvalCandidatesRunner when "export" is among active profiles
 * (e.g. export alone or export,postgres).
 */
class ExportProfileCondition : Condition {

    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val env = context.environment
        return env.activeProfiles.any { it == "export" }
    }
}
