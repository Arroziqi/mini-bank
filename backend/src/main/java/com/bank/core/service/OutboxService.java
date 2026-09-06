package com.bank.core.service;

import com.bank.core.model.OutboxEvent;
import com.bank.core.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void publishEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build();

        outboxEventRepository.save(event);
        log.info("Outbox event created: type={}, aggregate={}/{}", eventType, aggregateType, aggregateId);
    }

    @Transactional(readOnly = true)
    public java.util.List<OutboxEvent> getUnpublishedEvents() {
        return outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();
    }
}
