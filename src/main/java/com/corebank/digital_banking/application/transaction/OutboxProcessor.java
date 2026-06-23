package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.domain.transaction.OutboxEvent;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private final TransactionRepository transactionRepository;

    public OutboxProcessor(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Scheduled(fixedDelay = 5000)
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = transactionRepository.findPendingOutboxEvents();
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending outbox event(s) to process.", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            if (!transactionRepository.claimOutboxEvent(event.getId())) {
                log.info("Outbox event {} already claimed or processed by another instance. Skipping.", event.getId());
                continue;
            }

            try {
                log.info("[NOTIFICATION WORKER] Evento do outbox {} processado com sucesso. Payload: {}", 
                        event.getId(), event.getPayload());
                
                transactionRepository.updateOutboxStatus(event.getId(), "PROCESSED");
            } catch (Exception e) {
                log.error("Failed to process outbox event {}: {}", event.getId(), e.getMessage(), e);
                try {
                    transactionRepository.handleOutboxFailure(event.getId());
                } catch (Exception dbEx) {
                    log.error("Failed to handle outbox failure for event {}: {}", event.getId(), dbEx.getMessage(), dbEx);
                }
            }
        }
    }
}
