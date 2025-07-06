package com.mysillydreams.payment.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mysillydreams.payment.domain.OutboxEvent;
import com.mysillydreams.payment.domain.PaymentTransaction;
import com.mysillydreams.payment.dto.PaymentRequestedEvent;
import com.mysillydreams.payment.dto.PaymentSucceededEvent;
import com.mysillydreams.payment.repository.OutboxRepository;
import com.mysillydreams.payment.repository.PaymentRepository;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("testcontainers") // Ensure application-test.yml testcontainers profile is active
class PaymentIntegrationTest {

    private static final Network network = Network.newNetwork();
    private static final ObjectMapper objectMapper = new ObjectMapper();


    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.3-alpine"))
            .withNetwork(network).withNetworkAliases("postgres-db")
            .withDatabaseName("testdb").withUsername("testuser").withPassword("testpass");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withNetwork(network).withNetworkAliases("kafka-broker");

    @Container
    static final GenericContainer<?> schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.6.0"))
            .withNetwork(network).withNetworkAliases("schema-registry").withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka-broker:9092")
            .dependsOn(kafka);

    @RegisterExtension
    static WireMockExtension wireMockServer = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort().network(network).port(9999)) // Use fixed port inside docker network if needed by app
            .build();
    // Note: If payment service needs to call wiremock via "http://wiremockserver:port",
    // ensure wiremockServer is aliased on the docker network if Spring app runs in a container.
    // For this test, Spring app runs on host, so localhost:dynamicPort is fine.
    // If app itself was containerized for test, network aliasing for WireMock would be critical.


    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        String schemaRegistryUrl = "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
        registry.add("spring.kafka.consumer.properties.schema.registry.url", () -> schemaRegistryUrl);
        registry.add("spring.kafka.producer.properties.schema.registry.url", () -> schemaRegistryUrl);

        // Override Razorpay client to point to WireMock
        // This requires RazorpayClient to be configurable or re-beans for test.
        // For simplicity, let's assume RazorpayConfig in the app could pick up a base URL if modified.
        // Or, more practically, the RazorpayClient bean itself is mocked/replaced in a test configuration.
        // The current RazorpayConfig doesn't allow changing base URL.
        // So, direct interaction with Razorpay sandbox or heavy mocking in service test is more likely.
        // For this integration test, we are testing our service, assuming RazorpayClient works as per its contract.
        // So we will MOCK the RazorpayClient's behavior using WireMock for its HTTP calls.
        // The Razorpay SDK makes HTTP calls. We need to ensure it calls WireMock.
        // This is tricky without modifying SDK or using system properties for proxy, or if SDK allows URL override.
        // For now, this test will focus on Kafka-DB-Outbox flow, and Razorpay interaction is "as if" successful via stubs.
        // A true end-to-end with Razorpay SDK hitting WireMock requires deeper SDK configuration/mocking,
        // such as replacing the RazorpayClient bean in a @TestConfiguration with one that can be pointed to WireMock,
        // or using system properties if the SDK's HTTP client respects them for proxies.
        // The current setup assumes that if these WireMock stubs are hit, the service behaves correctly.
        // If RazorpayClient cannot be redirected, these stubs might not be used, and actual Razorpay sandbox would be hit.
        registry.add("payment.razorpay.api-base-url", () -> wireMockServer.baseUrl() + "/v1"); // Hypothetical if SDK supported it

        // Actual Razorpay test keys should be used if not mocking the SDK's HTTP calls effectively.
        // These would be loaded from application-test.yml (testcontainers profile) or environment.
        // registry.add("payment.razorpay.key-id", () -> "rzp_test_yourkeyid_from_env_or_file");
        // registry.add("payment.razorpay.key-secret", () -> "rzp_test_yoursecret_from_env_or_file");
    }


    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Value("${kafka.topics.paymentRequested}") private String paymentRequestedTopic;
    @Value("${kafka.topics.paymentSucceeded}") private String paymentSucceededTopic;
    // @Value("${kafka.topics.paymentFailed}") private String paymentFailedTopic;

    private KafkaProducer<String, SpecificRecord> producer;
    private KafkaConsumer<String, SpecificRecord> consumer;

    @BeforeEach
    void setUpKafka() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        producerProps.put("schema.registry.url", "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        producer = new KafkaProducer<>(producerProps);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("payment-itest-group-" + UUID.randomUUID(), "true", kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        consumerProps.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(consumerProps);

        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void shouldProcessPaymentAndPublishSuccessEvent() throws JsonProcessingException {
        // --- Arrange ---
        String orderId = UUID.randomUUID().toString();
        double amount = 150.75;
        String currency = "INR";
        PaymentRequestedEvent requestEvent = PaymentRequestedEvent.newBuilder()
                .setOrderId(orderId).setAmount(amount).setCurrency(currency).build();

        // Mock Razorpay Order API call
        String rzpOrderId = "order_mock_" + UUID.randomUUID().toString().substring(0,14);
        String rzpPaymentId = "pay_mock_" + UUID.randomUUID().toString().substring(0,14);

        JSONObject orderResponse = new JSONObject()
                .put("id", rzpOrderId)
                .put("entity", "order")
                .put("amount", (long)(amount * 100))
                .put("amount_paid", 0)
                .put("amount_due", (long)(amount * 100))
                .put("currency", currency)
                .put("receipt", orderId)
                .put("status", "created") // or "attempted"
                .put("attempts", 0)
                .put("created_at", System.currentTimeMillis()/1000);

        // This mocks the razorpayClient.Orders.create() call
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/orders"))
                // .withHeader("Content-Type", containing("application/json"))
                // .withHeader("Authorization", matching("Basic .*")) // Basic auth with key:secret
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(orderResponse.toString())));


        // Mock Razorpay Fetch Payments for Order call (simulating auto-capture or finding existing payment)
        JSONObject paymentDetails = new JSONObject()
                .put("id", rzpPaymentId)
                .put("entity", "payment")
                .put("amount", (long)(amount * 100))
                .put("currency", currency)
                .put("status", "captured") // Important: payment is captured
                .put("order_id", rzpOrderId)
                .put("method", "card")
                .put("captured", true)
                .put("created_at", System.currentTimeMillis()/1000);

        // This mocks razorpayClient.Orders.fetchPayments(rzpOrderId)
        // The SDK will call /v1/orders/{rzpOrderId}/payments
         wireMockServer.stubFor(get(urlPathEqualTo("/v1/orders/" + rzpOrderId + "/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(Map.of("entity", "collection", "count", 1, "items", List.of(paymentDetails.toMap()))))));
        // If explicit capture was needed (status: "authorized" then capture call)
        // wireMockServer.stubFor(post(urlPathEqualTo("/v1/payments/" + rzpPaymentId + "/capture")) ... )


        // --- Act ---
        producer.send(new ProducerRecord<>(paymentRequestedTopic, orderId, requestEvent));
        producer.flush();

        // --- Assert ---
        // 1. PaymentTransaction created and status becomes SUCCEEDED
        await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Optional<PaymentTransaction> txOpt = paymentRepository.findByOrderId(UUID.fromString(orderId));
            assertThat(txOpt).isPresent();
            PaymentTransaction tx = txOpt.get();
            assertThat(tx.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(tx.getRazorpayOrderId()).isEqualTo(rzpOrderId);
            assertThat(tx.getRazorpayPaymentId()).isEqualTo(rzpPaymentId);
        });

        // 2. OutboxEvent created for payment success
        UUID transactionId = paymentRepository.findByOrderId(UUID.fromString(orderId)).get().getId();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEvent> outboxEvents = outboxRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            OutboxEvent outbox = outboxEvents.get(0);
            assertThat(outbox.getAggregateId()).isEqualTo(transactionId.toString());
            assertThat(outbox.getEventType()).isEqualTo(paymentSucceededTopic);
            assertThat(outbox.getPayload().get("orderId")).isEqualTo(orderId);
            assertThat(outbox.getPayload().get("paymentId")).isEqualTo(rzpPaymentId);
        });

        // 3. PaymentSucceededEvent consumed from Kafka
        consumer.subscribe(Collections.singletonList(paymentSucceededTopic));
        ConsumerRecords<String, SpecificRecord> records = consumer.poll(Duration.ofSeconds(15));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        boolean found = false;
        for(ConsumerRecord<String, SpecificRecord> record : records) {
            if (record.key().equals(transactionId.toString()) && record.value() instanceof PaymentSucceededEvent) {
                PaymentSucceededEvent succeededEvent = (PaymentSucceededEvent) record.value();
                assertThat(succeededEvent.getOrderId()).isEqualTo(orderId);
                assertThat(succeededEvent.getPaymentId()).isEqualTo(rzpPaymentId);
                found = true;
                break;
            }
        }
        assertThat(found).isTrue().withFailMessage("PaymentSucceededEvent not found for order " + orderId);

        // 4. Outbox event marked processed
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxEvent outbox = outboxRepository.findAll().get(0); // Assuming only one for this test
            assertThat(outbox.isProcessed()).isTrue();
        });
        consumer.unsubscribe();
    }

    // TODO: Test for payment failure scenario (e.g., Razorpay API returns error)
    // TODO: Test for webhook signature verification and event handling (RazorpayWebhookController)

    // --- Vendor Payout Flow Integration Tests ---
    // These tests would be similar in structure to shouldProcessPaymentAndPublishSuccessEvent
    // but would focus on the vendor payout part.

    /*
    @Test
    void shouldInitiateVendorPayout_afterSuccessfulPayment() {
        // 1. Arrange:
        //    - Setup initial PaymentTransaction (or let shouldProcessPaymentAndPublishSuccessEvent create it).
        //    - Mock Razorpay Payouts.create API via WireMock to return a pending payout response.
        //    - Ensure a Vendor ID is available (e.g. from PaymentRequestedEvent or lookup).
        //    - (PaymentService.determineVendorIdForOrder needs to be robust or mocked for this).

        // 2. Act:
        //    - Produce PaymentRequestedEvent (as in existing test).
        //    - This will trigger PaymentServiceImpl, which then calls VendorPayoutService.initiatePayout.

        // 3. Assert:
        //    - Verify PayoutTransaction is created in INIT status.
        //    - Verify VendorPayoutInitiatedEvent is published to outbox and then to Kafka.
        //    - Verify (after @Async Razorpay call) PayoutTransaction moves to PENDING and has razorpay_payout_id.
        //    - (If Razorpay Payouts.create fails, verify FAILED status and VendorPayoutFailedEvent).
    }
    */

    /*
    @Test
    void shouldProcessPayoutWebhook_andPublishSucceededEvent() {
        // 1. Arrange:
        //    - Create a PayoutTransaction in PENDING status with a known razorpay_payout_id.
        //    - Prepare a valid Razorpay webhook payload for "payout.processed" for this razorpay_payout_id.
        //    - Calculate the expected signature for this payload.

        // 2. Act:
        //    - Send a POST request to /webhook/razorpay with the payload and signature.
        //      (Requires bringing up WebEnvironment for MockMvc or using TestRestTemplate).

        // 3. Assert:
        //    - Verify PayoutTransaction status updates to SUCCESS.
        //    - Verify VendorPayoutSucceededEvent is published to outbox and then to Kafka.
        //    - Consume and validate the VendorPayoutSucceededEvent.
    }
    */
    // Similarly, add tests for payout.failed webhook.
}
