package fr.heliocraft.refinr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRetry
public class RetryConfiguration {
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Retry policy: 3 attempts
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests.class, true);
        retryableExceptions.put(org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError.class, true);
        retryableExceptions.put(org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Exponential backoff: 1s, 2s, 4s
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}