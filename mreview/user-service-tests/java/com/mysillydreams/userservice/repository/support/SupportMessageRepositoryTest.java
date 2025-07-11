package com.mysillydreams.userservice.repository.support;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.support.*;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
public class SupportMessageRepositoryTest {

    @Autowired
    private SupportMessageRepository supportMessageRepository;
    @Autowired
    private SupportTicketRepository supportTicketRepository;
    @Autowired
    private UserRepository userRepository; // For customerId in ticket

    private UserEntity testCustomer;
    private SupportTicket testTicket1;
    private SupportTicket testTicket2;

    @BeforeEach
    void setUp() {
        supportMessageRepository.deleteAll();
        supportTicketRepository.deleteAll();
        userRepository.deleteAll();

        testCustomer = new UserEntity();
        testCustomer.setReferenceId("msg-cust-" + UUID.randomUUID());
        testCustomer.setEmail(testCustomer.getReferenceId() + "@example.com");
        testCustomer = userRepository.saveAndFlush(testCustomer);

        testTicket1 = new SupportTicket();
        testTicket1.setCustomerId(testCustomer.getId());
        testTicket1.setSubject("Ticket 1 for messages");
        testTicket1.setDescription("Desc 1");
        testTicket1 = supportTicketRepository.saveAndFlush(testTicket1);

        testTicket2 = new SupportTicket(); // Another ticket for filtering tests
        testTicket2.setCustomerId(testCustomer.getId());
        testTicket2.setSubject("Ticket 2 for messages");
        testTicket2.setDescription("Desc 2");
        testTicket2 = supportTicketRepository.saveAndFlush(testTicket2);
    }

    @AfterEach
    void tearDown() {
        supportMessageRepository.deleteAll();
        supportTicketRepository.deleteAll();
        userRepository.deleteAll();
    }


    private SupportMessage createAndSaveMessage(SupportTicket ticket, SenderType senderType, UUID senderId, String messageContent, long sleepMillis) throws InterruptedException {
        if (sleepMillis > 0) Thread.sleep(sleepMillis);
        SupportMessage message = new SupportMessage();
        message.setTicket(ticket);
        message.setSenderType(senderType);
        message.setSenderId(senderId);
        message.setMessage(messageContent);
        // attachments can be null or JSON string
        return supportMessageRepository.saveAndFlush(message);
    }

    @Test
    void saveAndFindById_shouldPersistAndRetrieveMessage() throws InterruptedException {
        SupportMessage msg = createAndSaveMessage(testTicket1, SenderType.CUSTOMER, testCustomer.getId(), "Hello Support!",0);

        SupportMessage foundMsg = supportMessageRepository.findById(msg.getId()).orElse(null);

        assertThat(foundMsg).isNotNull();
        assertThat(foundMsg.getTicket().getId()).isEqualTo(testTicket1.getId());
        assertThat(foundMsg.getSenderType()).isEqualTo(SenderType.CUSTOMER);
        assertThat(foundMsg.getSenderId()).isEqualTo(testCustomer.getId());
        assertThat(foundMsg.getMessage()).isEqualTo("Hello Support!");
        assertThat(foundMsg.getTimestamp()).isNotNull();
    }

    @Test
    void findByTicket_returnsSortedMessagesForTicket() throws InterruptedException {
        SupportMessage msg1 = createAndSaveMessage(testTicket1, SenderType.CUSTOMER, testCustomer.getId(), "First message", 0);
        SupportMessage msg2 = createAndSaveMessage(testTicket1, SenderType.SUPPORT_USER, UUID.randomUUID(), "Support reply", 10);
        SupportMessage msg3 = createAndSaveMessage(testTicket1, SenderType.CUSTOMER, testCustomer.getId(), "Thanks!", 10);

        // Message for another ticket
        createAndSaveMessage(testTicket2, SenderType.CUSTOMER, testCustomer.getId(), "Message for ticket 2", 0);

        List<SupportMessage> ticket1MessagesAsc = supportMessageRepository.findByTicket(testTicket1, Sort.by(Sort.Direction.ASC, "timestamp"));
        assertThat(ticket1MessagesAsc).hasSize(3);
        assertThat(ticket1MessagesAsc).extracting(SupportMessage::getId).containsExactly(msg1.getId(), msg2.getId(), msg3.getId());

        List<SupportMessage> ticket1MessagesDesc = supportMessageRepository.findByTicket(testTicket1, Sort.by(Sort.Direction.DESC, "timestamp"));
        assertThat(ticket1MessagesDesc).hasSize(3);
        assertThat(ticket1MessagesDesc).extracting(SupportMessage::getId).containsExactly(msg3.getId(), msg2.getId(), msg1.getId());
    }

    @Test
    void findByTicketId_returnsSortedMessagesForTicketId() throws InterruptedException {
        SupportMessage msg1 = createAndSaveMessage(testTicket1, SenderType.CUSTOMER, testCustomer.getId(), "Message A", 0);
        createAndSaveMessage(testTicket1, SenderType.SUPPORT_USER, UUID.randomUUID(), "Message B", 10);

        List<SupportMessage> messages = supportMessageRepository.findByTicketId(testTicket1.getId(), Sort.by("timestamp"));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getId()).isEqualTo(msg1.getId());
        assertThat(messages.get(0).getMessage()).isEqualTo("Message A");
    }
}
