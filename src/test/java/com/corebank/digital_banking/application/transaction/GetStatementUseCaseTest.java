package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.application.exception.EntityNotFoundException;
import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.application.transaction.dto.PagedTransferResponse;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import com.corebank.digital_banking.domain.transaction.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Get Statement Use Case Unit Tests")
class GetStatementUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private GetStatementUseCase getStatementUseCase;

    @Test
    @DisplayName("Should successfully retrieve statements when date range is within 90 days")
    void shouldRetrieveStatementsWithin90DaysRange() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant start = now.minus(45, ChronoUnit.DAYS);

        Transfer transfer = new Transfer(UUID.randomUUID(), accountId, UUID.randomUUID(), new BigDecimal("150.00"), "idemp-key-123", now);

        when(transactionRepository.accountExists(accountId)).thenReturn(true);
        when(transactionRepository.findTransfersByAccountAndDateRangePaged(accountId, start, now, null, null, 21))
                .thenReturn(List.of(transfer));

        PagedTransferResponse results = getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), null, null);

        assertFalse(results.data().isEmpty());
        assertEquals(1, results.data().size());
        assertEquals(transfer.getId().toString(), results.data().get(0).transferId());
        assertFalse(results.paging().hasMore());
        assertNull(results.paging().nextCursor());
    }

    @Test
    @DisplayName("Should successfully handle pagination and return nextCursor when hasMore is true")
    void shouldReturnNextCursorWhenHasMore() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant start = now.minus(5, ChronoUnit.DAYS);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        Transfer t1 = new Transfer(id1, accountId, UUID.randomUUID(), new BigDecimal("10.00"), "key1", now.minus(1, ChronoUnit.HOURS));
        Transfer t2 = new Transfer(id2, accountId, UUID.randomUUID(), new BigDecimal("20.00"), "key2", now.minus(2, ChronoUnit.HOURS));
        Transfer t3 = new Transfer(id3, accountId, UUID.randomUUID(), new BigDecimal("30.00"), "key3", now.minus(3, ChronoUnit.HOURS));

        when(transactionRepository.accountExists(accountId)).thenReturn(true);
        when(transactionRepository.findTransfersByAccountAndDateRangePaged(accountId, start, now, null, null, 3))
                .thenReturn(List.of(t1, t2, t3));

        PagedTransferResponse results = getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), 2, null);

        assertEquals(2, results.data().size());
        assertTrue(results.paging().hasMore());
        assertNotNull(results.paging().nextCursor());

        String decoded = new String(java.util.Base64.getDecoder().decode(results.paging().nextCursor()));
        String expectedCursor = t2.getCreatedAt().toString() + "," + t2.getId().toString();
        assertEquals(expectedCursor, decoded);
    }

    @Test
    @DisplayName("Should successfully parse and pass cursor coordinates to repository")
    void shouldParseAndPassCursorToRepository() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant start = now.minus(5, ChronoUnit.DAYS);

        Instant cursorTime = now.minus(2, ChronoUnit.HOURS);
        UUID cursorId = UUID.randomUUID();
        String cursorRaw = cursorTime.toString() + "," + cursorId.toString();
        String cursorBase64 = java.util.Base64.getEncoder().encodeToString(cursorRaw.getBytes());

        Transfer t3 = new Transfer(UUID.randomUUID(), accountId, UUID.randomUUID(), new BigDecimal("30.00"), "key3", now.minus(3, ChronoUnit.HOURS));

        when(transactionRepository.accountExists(accountId)).thenReturn(true);
        when(transactionRepository.findTransfersByAccountAndDateRangePaged(accountId, start, now, cursorTime, cursorId, 21))
                .thenReturn(List.of(t3));

        PagedTransferResponse results = getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), null, cursorBase64);

        assertEquals(1, results.data().size());
        assertFalse(results.paging().hasMore());
        assertNull(results.paging().nextCursor());
    }

    @Test
    @DisplayName("Should clamp invalid page size limits correctly")
    void shouldClampPageSizeLimits() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant start = now.minus(5, ChronoUnit.DAYS);

        when(transactionRepository.accountExists(accountId)).thenReturn(true);

        when(transactionRepository.findTransfersByAccountAndDateRangePaged(accountId, start, now, null, null, 21))
                .thenReturn(List.of());
        getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), -5, null);

        when(transactionRepository.findTransfersByAccountAndDateRangePaged(accountId, start, now, null, null, 101))
                .thenReturn(List.of());
        getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), 250, null);
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when cursor is malformed")
    void shouldThrowExceptionWhenCursorIsMalformed() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant start = now.minus(5, ChronoUnit.DAYS);

        when(transactionRepository.accountExists(accountId)).thenReturn(true);

        assertThrows(InvalidArgumentException.class, () -> {
            getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), null, "not-base-64-!!!");
        });

        String badCursor1 = java.util.Base64.getEncoder().encodeToString("just-a-string".getBytes());
        assertThrows(InvalidArgumentException.class, () -> {
            getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), null, badCursor1);
        });

        String badCursor2 = java.util.Base64.getEncoder().encodeToString("not-an-instant,not-a-uuid".getBytes());
        assertThrows(InvalidArgumentException.class, () -> {
            getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), null, badCursor2);
        });
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when date range exceeds 90 days threshold")
    void shouldThrowInvalidArgumentExceptionWhenRangeExceeds90Days() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant start = now.minus(91, ChronoUnit.DAYS);

        assertThrows(InvalidArgumentException.class, () -> {
            getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), null, null);
        });
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when statement account does not exist")
    void shouldThrowEntityNotFoundExceptionWhenAccountDoesNotExist() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant start = now.minus(10, ChronoUnit.DAYS);

        when(transactionRepository.accountExists(accountId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> {
            getStatementUseCase.execute(accountId.toString(), start.toString(), now.toString(), null, null);
        });
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when start date format is malformed")
    void shouldThrowInvalidArgumentExceptionWhenStartDateFormatIsMalformed() {
        UUID accountId = UUID.randomUUID();
        assertThrows(InvalidArgumentException.class, () -> {
            getStatementUseCase.execute(accountId.toString(), "invalid-date-format", "2026-06-15T00:00:00Z", null, null);
        });
    }
}
