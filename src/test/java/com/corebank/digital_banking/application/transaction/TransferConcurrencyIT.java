package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.application.transaction.dto.TransferRequest;
import com.corebank.digital_banking.domain.account.Account;
import com.corebank.digital_banking.domain.account.AccountRepository;
import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@DisplayName("Transfer High Concurrency Integration Tests")
class TransferConcurrencyIT {

    @Autowired
    private TransferUseCase transferUseCase;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Should process concurrent transfers correctly without double spending under heavy race conditions")
    void shouldHandleConcurrentTransfersCorrectlyWithoutDoubleSpending() throws InterruptedException {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();

        // High balance so we don't exceed the R$ 5,000 daily limit in multiple transfers (6 * 150 = 900, which is below 5000)
        Account sourceAccount = new Account(sourceId, "Source Holder", new BigDecimal("1000.0000"), Instant.now());
        Account destinationAccount = new Account(destinationId, "Destination Holder", new BigDecimal("0.0000"), Instant.now());

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        int totalThreads = 10;
        BigDecimal transferAmount = new BigDecimal("150.0000");

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < totalThreads; i++) {
            tasks.add(() -> {
                startLatch.await(); // wait for start signal
                try {
                    // Send unique idempotency key for each request
                    String idempotencyKey = UUID.randomUUID().toString();
                    transferUseCase.execute(new TransferRequest(sourceId.toString(), destinationId.toString(), transferAmount), idempotencyKey);
                    successCounter.incrementAndGet();
                } catch (BusinessRuleException e) {
                    failureCounter.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
                return null;
            });
        }

        // Submit tasks
        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        // Release start signal to trigger concurrent execution
        startLatch.countDown();

        // Wait for all threads to finish
        endLatch.await();
        executor.shutdown();

        // We expect exactly 6 transfers to succeed (6 * 150 = 900)
        // 4 transfers should fail because 1000 - 900 = 100 remaining balance, not enough for $150
        assertEquals(6, successCounter.get());
        assertEquals(4, failureCounter.get());
    }

    @Test
    @DisplayName("Should throw QueryTimeoutException when lock cannot be acquired within timeout window")
    void shouldTimeoutWhenLockIsHeld() throws Exception {
        UUID accountId = UUID.randomUUID();
        // Save account with sufficient balance
        accountRepository.save(new Account(accountId, "Locker Account", new BigDecimal("2000.0000"), Instant.now()));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);

        Future<Void> lockHolderFuture = executor.submit(() -> {
            transactionTemplate.execute(status -> {
                transactionRepository.findBalanceByIdWithLock(accountId);
                lockAcquiredLatch.countDown();
                try {
                    // Hold lock for 3 seconds (exceeding database LOCK_TIMEOUT of 2000ms)
                    releaseLockLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
            return null;
        });

        // Wait until lock holder has acquired the lock
        lockAcquiredLatch.await();

        // Now, try to execute a transfer using the locked account. This should wait and timeout.
        org.junit.jupiter.api.function.Executable attempt = () -> {
            transferUseCase.execute(
                new TransferRequest(accountId.toString(), UUID.randomUUID().toString(), new BigDecimal("100.00")),
                UUID.randomUUID().toString()
            );
        };

        // Assert that a query timeout exception is thrown due to database lock congestion
        assertThrows(QueryTimeoutException.class, attempt);

        // Cleanup and shutdown
        releaseLockLatch.countDown();
        lockHolderFuture.get();
        executor.shutdown();
    }
}
