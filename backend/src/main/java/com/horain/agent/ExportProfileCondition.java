package com.horain.agent;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;

/**
 * Condition for enabling ExportEvalCandidatesRunner when "export" is among active profiles
 * (e.g. export alone or export,postgres).
 */
public class ExportProfileCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        return Arrays.stream(env.getActiveProfiles()).anyMatch("export"::equals);
    }
}
