package com.calldesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class CoreConfiguration {
    @Bean public Clock clock() { return Clock.systemUTC(); }
    @Bean public HttpClient httpClient() { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(); }
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService callScheduler() { return Executors.newScheduledThreadPool(4); }

    @Bean
    public WebMvcConfigurer apiCorsConfigurer(@Value("${CORS_ALLOWED_ORIGINS:}") String extraOrigins) {
        String[] origins = Stream.concat(Stream.of("http://localhost:3000"), Arrays.stream(extraOrigins.split(",")))
                .map(String::trim).filter(origin -> !origin.isBlank()).distinct().toArray(String[]::new);
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}
