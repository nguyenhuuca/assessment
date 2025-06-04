CREATE TABLE user_email_request (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    email VARCHAR(255) NOT NULL,
                                    token VARCHAR(255) NOT NULL UNIQUE,
                                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                    expires_at TIMESTAMP NOT NULL,
                                    created_at TIMESTAMP NOT NULL DEFAULT now(),
                                    used_at TIMESTAMP,
                                    invited_by_user_id int4,
                                    user_id int4,
                                    purpose VARCHAR(50) NOT NULL DEFAULT 'JOIN_SYSTEM',
                                    metadata Text
);