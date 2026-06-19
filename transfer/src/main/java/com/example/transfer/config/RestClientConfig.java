package com.example.transfer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a {@link RestClient.Builder} for the outbound service clients. Defined explicitly because
 * the auto-configured builder isn't available by default here, and so each client can set its own
 * base URL from the shared builder.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
