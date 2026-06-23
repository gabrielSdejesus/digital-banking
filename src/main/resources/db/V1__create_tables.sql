-- 1. ACCOUNTS TABLE (Stores the current state)
CREATE TABLE account (
    id VARCHAR(36) PRIMARY KEY, -- UUID to prevent account ID predictability
    holder_name VARCHAR(100) NOT NULL,
    balance DECIMAL(18, 4) NOT NULL, -- Strict decimal precision to prevent loss of values
    created_at TIMESTAMP NOT NULL
);

-- 2. TRANSFERS TABLE (The Financial Ledger)
CREATE TABLE transfer (
    id VARCHAR(36) PRIMARY KEY,
    source_account_id VARCHAR(36) NOT NULL,
    destination_account_id VARCHAR(36) NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    idempotency_key VARCHAR(36) UNIQUE NOT NULL, -- Unique constraint to prevent duplicate transfers
    created_at TIMESTAMP NOT NULL, 
    FOREIGN KEY (source_account_id) REFERENCES account(id),
    FOREIGN KEY (destination_account_id) REFERENCES account(id)
);

-- 3. OUTBOX TABLE (Event Mechanism / Notification Audit)
CREATE TABLE notification_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id VARCHAR(36) NOT NULL,
    payload VARCHAR(1000) NOT NULL, -- JSON containing the data required by the Notification
    status VARCHAR(20) NOT NULL, -- PENDING, PROCESSING, PROCESSED, DLQ
    retry_count INT DEFAULT 0 NOT NULL, -- Retry attempts counter
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (transfer_id) REFERENCES transfer(id)
);

-- 4. PAGINATION INDEXES (For keyset/cursor-based pagination on transfer history)
CREATE INDEX idx_transfer_source_paged ON transfer (source_account_id, created_at DESC, id DESC);
CREATE INDEX idx_transfer_dest_paged ON transfer (destination_account_id, created_at DESC, id DESC);