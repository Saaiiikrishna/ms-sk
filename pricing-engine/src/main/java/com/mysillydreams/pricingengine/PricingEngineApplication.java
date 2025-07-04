package com.mysillydreams.pricingengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka // To enable detection of @KafkaListener annotations
public class PricingEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingEngineApplication.class, args);
    }

}
