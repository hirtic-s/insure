package com.example.insurance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // Tight timeouts: an ML outage must fail fast so the fallback path keeps
    // scoring latency low instead of stalling a Kafka consumer thread.
    @Bean
    public RestClient mlServiceRestClient(
            @Value("${ml-service.base-url:http://localhost:8000}") String baseUrl,
            @Value("${ml-service.connect-timeout-ms:300}") long connectMs,
            @Value("${ml-service.read-timeout-ms:1500}") long readMs) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory(connectMs, readMs))
                .build();
    }

    // Briefing generation calls an LLM, so it gets a much longer read timeout.
    @Bean
    public RestClient ragServiceRestClient(
            @Value("${rag-service.base-url:http://localhost:8001}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory(1000, 45000))
                .build();
    }

    private static SimpleClientHttpRequestFactory factory(long connectMs, long readMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofMillis(connectMs));
        f.setReadTimeout(Duration.ofMillis(readMs));
        return f;
    }
}
