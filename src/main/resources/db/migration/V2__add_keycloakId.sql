CREATE TABLE IF NOT EXISTS transactions (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            keycloak_user_id VARCHAR(255),
                                            sender_card_id UUID,
                                            receiver_card_id UUID,
                                            amount DECIMAL(19, 4) NOT NULL,
                                            currency VARCHAR(10) NOT NULL,
                                            status VARCHAR(50) NOT NULL,
                                            type VARCHAR(50) NOT NULL,
                                            reference_id VARCHAR(255) UNIQUE,
                                            created_at TIMESTAMP NOT NULL
);