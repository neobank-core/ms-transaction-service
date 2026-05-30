CREATE TABLE transactions (
                              id UUID PRIMARY KEY,
                              sender_card_id UUID,
                              receiver_card_id UUID,
                              amount NUMERIC(19,2) NOT NULL,
                              currency VARCHAR(10) NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              type VARCHAR(20) NOT NULL,
                              reference_id VARCHAR(255) UNIQUE,
                              created_at TIMESTAMP NOT NULL
);