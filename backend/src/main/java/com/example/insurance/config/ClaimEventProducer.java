package com.example.insurance.config;

import com.example.insurance.dto.ClaimScoredEvent;
import com.example.insurance.dto.ClaimSubmittedEvent;
import com.example.insurance.entity.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Component
public class ClaimEventProducer {

    private static final String CLAIMS_SUBMITTED_TOPIC = "claims-submitted";
    private static final String CLAIMS_SCORED_TOPIC = "claims-scored";

    private final KafkaTemplate<String,Object> kafkaTemplate;

    @Autowired
    public ClaimEventProducer(KafkaTemplate<String,Object> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishClaimSubmitted(ClaimSubmittedEvent event){
        kafkaTemplate.send(CLAIMS_SUBMITTED_TOPIC, event.getClaimId().toString(), event);
    }

    public void publishClaimScored(ClaimScoredEvent event){
        kafkaTemplate.send(CLAIMS_SCORED_TOPIC,event.getClaimId().toString(),event);
    }

}
