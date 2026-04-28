CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (name) VALUES ('Alice');
INSERT INTO users (name) VALUES ('Bob');
