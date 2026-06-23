package com.corebank.digital_banking.infra.config;

import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class OpenApiConfig {

    @Bean
    public OperationCustomizer addIdempotencyKeyHeader() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() != null) {
                for (Parameter parameter : operation.getParameters()) {
                    if ("Idempotency-Key".equalsIgnoreCase(parameter.getName())) {
                        parameter.getSchema().setDefault(UUID.randomUUID().toString());
                    }
                }
            }
            return operation;
        };
    }
}
