package com.fortress.chat.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer — publishes MessageSentEvent to the fortress.messages topic.
 * Called by MessageService after a message is persisted to PostgreSQL.
 *
 * Design note: the message is persisted FIRST, then published to Kafka.
 * If Kafka publish fails, the message is still safely in PostgreSQL.
 * This is "at-most-once" delivery to Kafka, which is acceptable for
 * delivery marking (a client reconnecting can always fetch from DB).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageProducer {

    private static final String TOPIC = "fortress.messages";

    private final KafkaTemplate<String, MessageSentEvent> kafkaTemplate;

    public void publish(MessageSentEvent event) {
        log.debug("Publishing MessageSentEvent to Kafka: messageId={}, chatroomId={}",
                event.getMessageId(), event.getChatroomId());

        kafkaTemplate.send(TOPIC, event.getChatroomId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to Kafka: {}", ex.getMessage());
                    } else {
                        log.debug("Message published to Kafka: topic={}, partition={}, offset={}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
