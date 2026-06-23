package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.application.exception.EntityNotFoundException;
import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.application.transaction.dto.PagedTransferResponse;
import com.corebank.digital_banking.application.transaction.dto.PagingMetadata;
import com.corebank.digital_banking.application.transaction.dto.TransferResponse;
import com.corebank.digital_banking.application.transaction.mapper.TransactionMapper;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import com.corebank.digital_banking.domain.transaction.Transfer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class GetStatementUseCase {

    private final TransactionRepository transactionRepository;

    public GetStatementUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PagedTransferResponse execute(String accountIdStr, String startDateStr, String endDateStr, Integer pageSize, String cursor) {
        if (accountIdStr == null || accountIdStr.trim().isEmpty()) {
            throw new InvalidArgumentException("Account ID is required", "accountId", "Account ID cannot be null or empty");
        }
        if (accountIdStr.trim().length() != 36) {
            throw new InvalidArgumentException("Invalid account ID size", "accountId", "Account ID must be exactly 36 characters");
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(accountIdStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidArgumentException("Invalid account ID format", "accountId", "Account ID must be a valid UUID");
        }

        if (startDateStr == null || startDateStr.trim().isEmpty()) {
            throw new InvalidArgumentException("Start date is required", "startDate", "Start date cannot be null or empty");
        }
        if (endDateStr == null || endDateStr.trim().isEmpty()) {
            throw new InvalidArgumentException("End date is required", "endDate", "End date cannot be null or empty");
        }

        Instant start;
        try {
            start = parseInstant(startDateStr.trim());
        } catch (Exception e) {
            throw new InvalidArgumentException("Invalid start date format", "startDate", "Start date must be a valid ISO-8601 string");
        }

        Instant end;
        try {
            end = parseInstant(endDateStr.trim());
        } catch (Exception e) {
            throw new InvalidArgumentException("Invalid end date format", "endDate", "End date must be a valid ISO-8601 string");
        }

        LocalDate startDate = LocalDate.ofInstant(start, java.time.ZoneOffset.UTC);
        LocalDate endDate = LocalDate.ofInstant(end, java.time.ZoneOffset.UTC);
        long days = ChronoUnit.DAYS.between(startDate, endDate);

        if (days < 0 || days > 90) {
            throw new InvalidArgumentException("Invalid date range", "dateRange", "The difference between start and end date must be at most 90 days");
        }

        if (!transactionRepository.accountExists(accountId)) {
            throw new EntityNotFoundException("Account with ID " + accountIdStr + " not found");
        }

        int limit = (pageSize == null) ? 20 : pageSize;
        if (limit <= 0) {
            limit = 20;
        } else if (limit > 100) {
            limit = 100;
        }

        Instant cursorCreatedAt = null;
        UUID cursorId = null;
        if (cursor != null && !cursor.trim().isEmpty()) {
            try {
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(cursor.trim());
                String decodedStr = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                String[] parts = decodedStr.split(",", 2);
                if (parts.length == 2) {
                    cursorCreatedAt = Instant.parse(parts[0]);
                    cursorId = UUID.fromString(parts[1]);
                } else {
                    throw new InvalidArgumentException("Invalid cursor format", "cursor", "Cursor must be a valid Base64 string containing Instant and UUID");
                }
            } catch (Exception e) {
                throw new InvalidArgumentException("Invalid cursor value", "cursor", "Failed to parse pagination cursor");
            }
        }

        int queryLimit = limit + 1;
        List<Transfer> transfers = transactionRepository.findTransfersByAccountAndDateRangePaged(
                accountId, start, end, cursorCreatedAt, cursorId, queryLimit
        );

        boolean hasMore = transfers.size() > limit;
        List<Transfer> pageTransfers = hasMore ? transfers.subList(0, limit) : transfers;

        String nextCursor = null;
        if (hasMore && !pageTransfers.isEmpty()) {
            Transfer lastTransfer = pageTransfers.get(pageTransfers.size() - 1);
            String cursorRaw = lastTransfer.getCreatedAt().toString() + "," + lastTransfer.getId().toString();
            nextCursor = java.util.Base64.getEncoder().encodeToString(cursorRaw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        List<TransferResponse> data = pageTransfers.stream()
                .map(TransactionMapper::toResponse)
                .toList();

        return new PagedTransferResponse(data, new PagingMetadata(nextCursor, hasMore));
    }

    private Instant parseInstant(String dateStr) {
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            try {
                return java.time.ZonedDateTime.parse(dateStr).toInstant();
            } catch (Exception e2) {
                try {
                    return java.time.OffsetDateTime.parse(dateStr).toInstant();
                } catch (Exception e3) {
                    return java.time.LocalDateTime.parse(dateStr).toInstant(java.time.ZoneOffset.UTC);
                }
            }
        }
    }
}
