CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    registered BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    role_id BIGINT NOT NULL REFERENCES role (id),
    assigned_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE competition (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    duration_days INTEGER NOT NULL,
    recurring BOOLEAN NOT NULL DEFAULT FALSE,
    buy_fee NUMERIC(5, 2) NOT NULL,
    sell_fee NUMERIC(5, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    creator_id BIGINT NOT NULL REFERENCES app_user (id)
);

CREATE TABLE participation (
    id BIGSERIAL PRIMARY KEY,
    competition_id BIGINT NOT NULL REFERENCES competition (id),
    user_id BIGINT REFERENCES app_user (id),
    email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    first_email_sent_date DATE,
    joined_at DATE
);

CREATE TABLE login_link (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    email_sent_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    invalidated_at TIMESTAMP,
    user_id BIGINT REFERENCES app_user (id),
    participation_id BIGINT REFERENCES participation (id)
);

CREATE TABLE login_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    login_link_id BIGINT NOT NULL UNIQUE REFERENCES login_link (id),
    device_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP
);
