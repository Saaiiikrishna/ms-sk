package com.mysillydreams.catalogservice.kafka.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String topic, String key, Object payload) {
        log.debug("Sending message to Kafka topic: {}, key: {}", topic, key);
        ListenableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);

        future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
            @Override
            public void onSuccess(SendResult<String, Object> result) {
                log.info("Sent message=[{}] with offset=[{}] to topic=[{}]", payload, result.getRecordMetadata().offset(), topic);
            }

            @Override
            public void onFailure(Throwable ex) {
                log.error("Unable to send message=[{}] to topic=[{}] due to : {}", payload, topic, ex.getMessage(), ex);
                // Consider retry mechanisms or dead-letter queue (DLQ) handling here or at a higher level
            }
        });
    }

    public void sendMessage(String topic, Object payload) {
        sendMessage(topic, null, payload);
    }
}
