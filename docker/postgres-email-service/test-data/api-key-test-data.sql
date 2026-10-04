--
-- PostgreSQL database dump
--


-- Dumped from database version 16.15 (Debian 16.15-1.pgdg13+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

DROP INDEX IF EXISTS api_key.flyway_schema_history_s_idx;
ALTER TABLE IF EXISTS ONLY api_key.api_key_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY api_key.api_keys DROP CONSTRAINT IF EXISTS api_keys_pkey;
ALTER TABLE IF EXISTS ONLY api_key.api_keys DROP CONSTRAINT IF EXISTS api_keys_key_hash_key;
ALTER TABLE IF EXISTS api_key.api_keys ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS api_key.api_keys_id_seq;
DROP TABLE IF EXISTS api_key.api_keys;
DROP TABLE IF EXISTS api_key.api_key_schema_history;
DROP SCHEMA IF EXISTS api_key;
--
-- Name: api_key; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA api_key;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: api_key_schema_history; Type: TABLE; Schema: api_key; Owner: -
--

CREATE TABLE api_key.api_key_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: api_keys; Type: TABLE; Schema: api_key; Owner: -
--

CREATE TABLE api_key.api_keys (
    id bigint NOT NULL,
    client_name character varying(255) NOT NULL,
    key_hash character varying(255) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    expires_at timestamp without time zone,
    revoked_at timestamp without time zone
);


--
-- Name: api_keys_id_seq; Type: SEQUENCE; Schema: api_key; Owner: -
--

CREATE SEQUENCE api_key.api_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_keys_id_seq; Type: SEQUENCE OWNED BY; Schema: api_key; Owner: -
--

ALTER SEQUENCE api_key.api_keys_id_seq OWNED BY api_key.api_keys.id;


--
-- Name: api_keys id; Type: DEFAULT; Schema: api_key; Owner: -
--

ALTER TABLE ONLY api_key.api_keys ALTER COLUMN id SET DEFAULT nextval('api_key.api_keys_id_seq'::regclass);


--
-- Data for Name: api_key_schema_history; Type: TABLE DATA; Schema: api_key; Owner: -
--

COPY api_key.api_key_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
0	\N	<< Flyway Schema Creation >>	SCHEMA	"api_key"	\N	email_service_admin	2026-10-03 19:49:26.896126	0	t
1	1	create api keys table	SQL	V1__create_api_keys_table.sql	1969127143	email_service_admin	2026-10-03 19:49:26.923462	7	t
2	2	add revoked at to api keys	SQL	V2__add_revoked_at_to_api_keys.sql	1620673178	email_service_admin	2026-10-03 19:49:26.946137	1	t
\.


--
-- Data for Name: api_keys; Type: TABLE DATA; Schema: api_key; Owner: -
--

COPY api_key.api_keys (id, client_name, key_hash, created_at, expires_at, revoked_at) FROM stdin;
1	jogo-acoes	4bb6a54721ac45965413acda0987800299242a3f237abae6584b5a81cf18b722	2026-10-03 19:49:29.138196	\N	\N
\.


--
-- Name: api_keys_id_seq; Type: SEQUENCE SET; Schema: api_key; Owner: -
--

SELECT pg_catalog.setval('api_key.api_keys_id_seq', 1, true);


--
-- Name: api_keys api_keys_key_hash_key; Type: CONSTRAINT; Schema: api_key; Owner: -
--

ALTER TABLE ONLY api_key.api_keys
    ADD CONSTRAINT api_keys_key_hash_key UNIQUE (key_hash);


--
-- Name: api_keys api_keys_pkey; Type: CONSTRAINT; Schema: api_key; Owner: -
--

ALTER TABLE ONLY api_key.api_keys
    ADD CONSTRAINT api_keys_pkey PRIMARY KEY (id);


--
-- Name: api_key_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: api_key; Owner: -
--

ALTER TABLE ONLY api_key.api_key_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: api_key; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON api_key.api_key_schema_history USING btree (success);


--
-- PostgreSQL database dump complete
--


