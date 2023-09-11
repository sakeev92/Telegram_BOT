-- liquibase formatted sql
-- changeset agurenko:1
CREATE TABLE notification_task (
                                   id BIGSERIAL PRIMARY KEY,
                                   chat_id BIGINT NOT NULL,
                                   notification_text VARCHAR(255) NOT NULL,
                                   notification_date TIMESTAMP NOT NULL
);