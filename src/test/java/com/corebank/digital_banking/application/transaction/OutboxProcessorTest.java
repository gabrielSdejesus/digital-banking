package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.domain.transaction.OutboxEvent;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox Processor Unit Tests")
class OutboxProcessorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    @Test
    @DisplayName("Should skip processing when no pending outbox events are found")
    void shouldDoNothingWhenNoEvents() {
        when(transactionRepository.findPendingOutboxEvents()).thenReturn(Collections.emptyList());

        outboxProcessor.processPendingEvents();

        verify(transactionRepository, times(1)).findPendingOutboxEvents();
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should successfully process pending outbox events and mark them as PROCESSED")
    void shouldProcessPendingEventsSuccessfully() {
        OutboxEvent event1 = new OutboxEvent(1L, "tx-1", "{\"msg\":\"Payload 1\"}", "PENDING", 0, Instant.now());
        OutboxEvent event2 = new OutboxEvent(2L, "tx-2", "{\"msg\":\"Payload 2\"}", "PENDING", 0, Instant.now());

        when(transactionRepository.findPendingOutboxEvents()).thenReturn(List.of(event1, event2));
        when(transactionRepository.claimOutboxEvent(1L)).thenReturn(true);
        when(transactionRepository.claimOutboxEvent(2L)).thenReturn(true);

        outboxProcessor.processPendingEvents();

        verify(transactionRepository, times(1)).findPendingOutboxEvents();
        verify(transactionRepository, times(1)).claimOutboxEvent(1L);
        verify(transactionRepository, times(1)).claimOutboxEvent(2L);
        verify(transactionRepository, times(1)).updateOutboxStatus(1L, "PROCESSED");
        verify(transactionRepository, times(1)).updateOutboxStatus(2L, "PROCESSED");
    }

    @Test
    @DisplayName("Should skip event processing when claim lock acquisition fails")
    void shouldSkipEventWhenClaimFails() {
        OutboxEvent event = new OutboxEvent(1L, "tx-1", "{\"msg\":\"Payload 1\"}", "PENDING", 0, Instant.now());

        when(transactionRepository.findPendingOutboxEvents()).thenReturn(List.of(event));
        // Another instance claimed this event
        when(transactionRepository.claimOutboxEvent(1L)).thenReturn(false);

        outboxProcessor.processPendingEvents();

        verify(transactionRepository, times(1)).findPendingOutboxEvents();
        verify(transactionRepository, times(1)).claimOutboxEvent(1L);
        verify(transactionRepository, never()).updateOutboxStatus(anyLong(), anyString());
        verify(transactionRepository, never()).handleOutboxFailure(anyLong());
    }

    @Test
    @DisplayName("Should invoke handleOutboxFailure when event processing fails")
    void shouldMarkEventAsFailedOnException() {
        OutboxEvent event = new OutboxEvent(1L, "tx-1", "{\"msg\":\"Payload 1\"}", "PENDING", 0, Instant.now());

        when(transactionRepository.findPendingOutboxEvents()).thenReturn(List.of(event));
        when(transactionRepository.claimOutboxEvent(1L)).thenReturn(true);
        doThrow(new RuntimeException("Notification service timeout")).when(transactionRepository).updateOutboxStatus(1L, "PROCESSED");

        outboxProcessor.processPendingEvents();

        verify(transactionRepository, times(1)).findPendingOutboxEvents();
        verify(transactionRepository, times(1)).claimOutboxEvent(1L);
        verify(transactionRepository, times(1)).updateOutboxStatus(1L, "PROCESSED");
        verify(transactionRepository, times(1)).handleOutboxFailure(1L);
    }
}
