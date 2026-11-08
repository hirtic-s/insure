package com.example.insurance.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic claimsSubmittedTopic() {
        return TopicBuilder.name("claims-submitted")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic claimsScoredTopic() {
        return TopicBuilder.name("claims-scored")
                .partitions(3)
                .replicas(1)
                .build();
    }

}
