CREATE TABLE transaction_ledger (
                                    id UUID PRIMARY KEY,
                                    transaction_id UUID NOT NULL REFERENCES transactions(id),
                                    entry_type VARCHAR(10) NOT NULL,
                                    account_id UUID NOT NULL,
                                    amount NUMERIC(19,4) NOT NULL,
                                    balance_after NUMERIC(19,4),
                                    created_at TIMESTAMP NOT NULL
);