package com.mysillydreams.orderapi.messaging

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Represents an event when an order is created. Should be sent to Kafka topic 'order.created'."
    label "orderCreatedEvent" // A label for this contract

    // This contract defines an output message (something this service produces)
    outputMessage {
        sentTo "order.created" // The destination Kafka topic

        // Body of the message, matching the Avro schema for OrderCreatedEvent
        // Using Groovy map notation. Values can be concrete or use Contract Verifier DSL for dynamic values.
        body([
            orderId    : $(consumer(uuid()), producer(nonEmpty())), // UUID as string
            customerId : $(consumer(uuid()), producer(nonEmpty())), // UUID as string
            items      : [ // Array of LineItem
                [
                    productId: $(consumer(uuid()), producer(nonEmpty())),
                    quantity : $(consumer(positiveInt()), producer(1)),
                    price    : $(consumer(positiveDouble()), producer(10.50))
                ]
            ],
            totalAmount: $(consumer(positiveDouble()), producer(10.50)), // Example value
            currency   : $(consumer(matching("[A-Z]{3}")), producer("USD")), // ISO currency code
            createdAt  : $(consumer(anyTimestamp()), producer(System.currentTimeMillis())) // Timestamp in millis
        ])

        // Optionally, specify headers if any are consistently set (not typical for just Avro messages unless wrapped)
        // headers {
        //    header('Content-Type', 'application/avro') // Or specific Avro content type if applicable
        // }

        // Matchers for Avro:
        // For Avro, the body matching is often based on the schema.
        // The exact field value matching here provides more specific assertions.
        // Spring Cloud Contract can work with Avro schemas directly if configured.
        // For now, this explicit body structure helps.
    }
}

// Helper methods for UUID and positive numbers if needed, or use built-in Contract DSL features.
// For Avro, `uuid()` regex for string fields representing UUIDs:
// value(consumer(java.util.UUID.randomUUID().toString()), producer(regex(java.util.UUID.randomUUID().toString().replaceAll("[0-9]", "[0-9a-fA-F]"))))
// Or more simply for string UUIDs:
// orderId: $(consumer(value(java.util.UUID.randomUUID().toString())), producer(regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")))

// For this example, using simpler matchers:
// - nonEmpty() for string UUIDs
// - positiveInt() for quantity
// - positiveDouble() for amounts/prices
// - anyTimestamp() for createdAt (long)
// - matching() for currency code format
// The consumer side would use these to generate expectations, producer side uses them to generate test data.
// The producer values (e.g., producer(1)) are used to generate the message for the producer-side test.
// The consumer values (e.g., consumer(positiveInt())) are used by the consumer in their tests against stubs.
// For Avro specific records, the schema itself is the primary contract for structure and types.
// Spring Cloud Contract's generated tests will try to serialize the body map to the Avro type.
// Ensure the field names and structure in `body([...])` map correctly to the Avro schema.
// The `$(consumer(...), producer(...))` syntax allows specifying different values/matchers for consumer-side stubs vs producer-side tests.
// If same for both, can just use `$(value)`.
// Example simplified for clarity:
// orderId: $(uuid())
// customerId: $(uuid())
// items: [[productId: $(uuid()), quantity: 1, price: 10.0]]
// totalAmount: 10.0
// currency: "USD"
// createdAt: $(anyLong())

// Corrected example using more specific regex for UUIDs
// and ensuring values are appropriate for Avro types.
Contract.make {
    description "Represents an event when an order is created (AVRO)"
    label "orderCreatedEventAvro"

    outputMessage {
        sentTo "order.created"
        body([
            orderId    : $(consumer(regex("[a-f0-9]{8}-([a-f0-9]{4}-){3}[a-f0-9]{12}")), producer(UUID.randomUUID().toString())),
            customerId : $(consumer(regex("[a-f0-9]{8}-([a-f0-9]{4}-){3}[a-f0-9]{12}")), producer(UUID.randomUUID().toString())),
            items      : [
                [
                    productId: $(consumer(regex("[a-f0-9]{8}-([a-f0-9]{4}-){3}[a-f0-9]{12}")), producer(UUID.randomUUID().toString())),
                    quantity : $(consumer(value(P(1))), producer(1)), // P(1) makes it a specific value for consumer too
                    price    : $(consumer(value(P(10.50D))), producer(10.50D)) // D suffix for double
                ]
            ],
            totalAmount: $(consumer(value(P(10.50D))), producer(10.50D)),
            currency   : $(consumer(value(P("USD"))), producer("USD")),
            createdAt  : $(consumer(anyPositiveLong()), producer(System.currentTimeMillis()))
        ])
        // Indicate that the body is an Avro message of a specific type
        // This helps the verifier/stub runner use appropriate Avro serialization/deserialization
        // The exact mechanism depends on how Spring Cloud Contract is configured for Avro.
        // For messaging, often the message converter (e.g., KafkaAvroSerializer) handles this based on type.
        // We can add a metadata entry if needed by a custom setup.
        // metadata([
        //   avroSchema: "com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent"
        // ])
    }
}
