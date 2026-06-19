package com.example.account.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables caching in the full application context. Kept out of the main application class so web
 * slice tests ({@code @WebMvcTest}) — which have no CacheManager — don't try to wire caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
