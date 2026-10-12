-- Massa de dados do caderno de testes (spec 05-040), gerada por gerar-massa.sh.
-- data-geracao: 2026-10-12
-- deslocamento-relogio-dias: -30
-- versao-flyway: 9
--
-- PostgreSQL database dump
--

\restrict eQY7gLTEXFLXrFpmcm5OrvJ0UZXmonFqTdCLgjNJYEEbv0Agvl10grEqnJC4S2i

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

--
-- Name: jogo_acoes; Type: SCHEMA; Schema: -; Owner: jogo_acoes_admin
--

CREATE SCHEMA jogo_acoes;


ALTER SCHEMA jogo_acoes OWNER TO jogo_acoes_admin;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_user; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.app_user (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    registered boolean DEFAULT false NOT NULL
);


ALTER TABLE jogo_acoes.app_user OWNER TO jogo_acoes_admin;

--
-- Name: app_user_id_seq; Type: SEQUENCE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE SEQUENCE jogo_acoes.app_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE jogo_acoes.app_user_id_seq OWNER TO jogo_acoes_admin;

--
-- Name: app_user_id_seq; Type: SEQUENCE OWNED BY; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER SEQUENCE jogo_acoes.app_user_id_seq OWNED BY jogo_acoes.app_user.id;


--
-- Name: competition; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.competition (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(20) NOT NULL,
    start_date date NOT NULL,
    duration_days integer NOT NULL,
    recurring boolean DEFAULT false NOT NULL,
    buy_fee numeric(5,2) NOT NULL,
    sell_fee numeric(5,2) NOT NULL,
    status character varying(20) NOT NULL,
    creator_id bigint NOT NULL
);


ALTER TABLE jogo_acoes.competition OWNER TO jogo_acoes_admin;

--
-- Name: competition_id_seq; Type: SEQUENCE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE SEQUENCE jogo_acoes.competition_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE jogo_acoes.competition_id_seq OWNER TO jogo_acoes_admin;

--
-- Name: competition_id_seq; Type: SEQUENCE OWNED BY; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER SEQUENCE jogo_acoes.competition_id_seq OWNED BY jogo_acoes.competition.id;


--
-- Name: jogo_acoes_schema_history; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.jogo_acoes_schema_history (
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


ALTER TABLE jogo_acoes.jogo_acoes_schema_history OWNER TO jogo_acoes_admin;

--
-- Name: link_record; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.link_record (
    id bigint NOT NULL,
    token character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    email_sent_at timestamp without time zone,
    expires_at timestamp without time zone NOT NULL,
    used_at timestamp without time zone,
    invalidated_at timestamp without time zone,
    user_id bigint,
    service_key character varying(64) NOT NULL,
    extra_json text
);


ALTER TABLE jogo_acoes.link_record OWNER TO jogo_acoes_admin;

--
-- Name: log; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.log (
    id bigint NOT NULL,
    related_object_id bigint NOT NULL,
    user_id bigint,
    created_at timestamp without time zone NOT NULL,
    log_type character varying(50) NOT NULL,
    message text NOT NULL
);


ALTER TABLE jogo_acoes.log OWNER TO jogo_acoes_admin;

--
-- Name: log_id_seq; Type: SEQUENCE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE SEQUENCE jogo_acoes.log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE jogo_acoes.log_id_seq OWNER TO jogo_acoes_admin;

--
-- Name: log_id_seq; Type: SEQUENCE OWNED BY; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER SEQUENCE jogo_acoes.log_id_seq OWNED BY jogo_acoes.log.id;


--
-- Name: login_link_id_seq; Type: SEQUENCE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE SEQUENCE jogo_acoes.login_link_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE jogo_acoes.login_link_id_seq OWNER TO jogo_acoes_admin;

--
-- Name: login_link_id_seq; Type: SEQUENCE OWNED BY; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER SEQUENCE jogo_acoes.login_link_id_seq OWNED BY jogo_acoes.link_record.id;


--
-- Name: login_session; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.login_session (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    link_record_id bigint NOT NULL,
    device_id character varying(255) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    ended_at timestamp without time zone,
    http_session_id character varying(255) NOT NULL
);


ALTER TABLE jogo_acoes.login_session OWNER TO jogo_acoes_admin;

--
-- Name: login_session_id_seq; Type: SEQUENCE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE SEQUENCE jogo_acoes.login_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE jogo_acoes.login_session_id_seq OWNER TO jogo_acoes_admin;

--
-- Name: login_session_id_seq; Type: SEQUENCE OWNED BY; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER SEQUENCE jogo_acoes.login_session_id_seq OWNED BY jogo_acoes.login_session.id;


--
-- Name: participation; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.participation (
    id bigint NOT NULL,
    competition_id bigint NOT NULL,
    user_id bigint,
    email character varying(255) NOT NULL,
    status character varying(20) NOT NULL,
    request_type character varying(20) NOT NULL,
    first_email_sent_date date,
    joined_at date
);


ALTER TABLE jogo_acoes.participation OWNER TO jogo_acoes_admin;

--
-- Name: participation_id_seq; Type: SEQUENCE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE SEQUENCE jogo_acoes.participation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE jogo_acoes.participation_id_seq OWNER TO jogo_acoes_admin;

--
-- Name: participation_id_seq; Type: SEQUENCE OWNED BY; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER SEQUENCE jogo_acoes.participation_id_seq OWNED BY jogo_acoes.participation.id;


--
-- Name: role; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.role (
    id bigint NOT NULL,
    name character varying(50) NOT NULL
);


ALTER TABLE jogo_acoes.role OWNER TO jogo_acoes_admin;

--
-- Name: role_id_seq; Type: SEQUENCE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE SEQUENCE jogo_acoes.role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE jogo_acoes.role_id_seq OWNER TO jogo_acoes_admin;

--
-- Name: role_id_seq; Type: SEQUENCE OWNED BY; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER SEQUENCE jogo_acoes.role_id_seq OWNED BY jogo_acoes.role.id;


--
-- Name: sent_email; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.sent_email (
    id bigint NOT NULL,
    user_id bigint,
    email character varying(255) NOT NULL,
    link character varying(2048) NOT NULL,
    template character varying(50) NOT NULL,
    sent_at timestamp without time zone NOT NULL,
    email_service_id uuid
);


ALTER TABLE jogo_acoes.sent_email OWNER TO jogo_acoes_admin;

--
-- Name: sent_email_id_seq; Type: SEQUENCE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE SEQUENCE jogo_acoes.sent_email_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE jogo_acoes.sent_email_id_seq OWNER TO jogo_acoes_admin;

--
-- Name: sent_email_id_seq; Type: SEQUENCE OWNED BY; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER SEQUENCE jogo_acoes.sent_email_id_seq OWNED BY jogo_acoes.sent_email.id;


--
-- Name: spring_session; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.spring_session (
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100)
);


ALTER TABLE jogo_acoes.spring_session OWNER TO jogo_acoes_admin;

--
-- Name: spring_session_attributes; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.spring_session_attributes (
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL
);


ALTER TABLE jogo_acoes.spring_session_attributes OWNER TO jogo_acoes_admin;

--
-- Name: user_role; Type: TABLE; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE TABLE jogo_acoes.user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    assigned_at timestamp without time zone NOT NULL
);


ALTER TABLE jogo_acoes.user_role OWNER TO jogo_acoes_admin;

--
-- Name: app_user id; Type: DEFAULT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.app_user ALTER COLUMN id SET DEFAULT nextval('jogo_acoes.app_user_id_seq'::regclass);


--
-- Name: competition id; Type: DEFAULT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.competition ALTER COLUMN id SET DEFAULT nextval('jogo_acoes.competition_id_seq'::regclass);


--
-- Name: link_record id; Type: DEFAULT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.link_record ALTER COLUMN id SET DEFAULT nextval('jogo_acoes.login_link_id_seq'::regclass);


--
-- Name: log id; Type: DEFAULT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.log ALTER COLUMN id SET DEFAULT nextval('jogo_acoes.log_id_seq'::regclass);


--
-- Name: login_session id; Type: DEFAULT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.login_session ALTER COLUMN id SET DEFAULT nextval('jogo_acoes.login_session_id_seq'::regclass);


--
-- Name: participation id; Type: DEFAULT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.participation ALTER COLUMN id SET DEFAULT nextval('jogo_acoes.participation_id_seq'::regclass);


--
-- Name: role id; Type: DEFAULT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.role ALTER COLUMN id SET DEFAULT nextval('jogo_acoes.role_id_seq'::regclass);


--
-- Name: sent_email id; Type: DEFAULT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.sent_email ALTER COLUMN id SET DEFAULT nextval('jogo_acoes.sent_email_id_seq'::regclass);


--
-- Data for Name: app_user; Type: TABLE DATA; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

COPY jogo_acoes.app_user (id, name, email, registered) FROM stdin;
1	Administrator	success+admin@simulator.amazonses.com	t
2	Seed C2 Jogador 1	success+seed-c2-jogador-1@simulator.amazonses.com	t
3	Seed C2 Jogador 2	success+seed-c2-jogador-2@simulator.amazonses.com	t
4	Seed C2 Jogador 3	success+seed-c2-jogador-3@simulator.amazonses.com	t
5	Seed C2 Jogador 4	success+seed-c2-jogador-4@simulator.amazonses.com	t
6	Seed C2 Jogador 5	success+seed-c2-jogador-5@simulator.amazonses.com	t
7	Seed C3 Jogador 1	success+seed-c3-jogador-1@simulator.amazonses.com	t
8	Seed C3 Jogador 2	success+seed-c3-jogador-2@simulator.amazonses.com	t
9	Seed C6 Jogador 1	success+seed-c6-jogador-1@simulator.amazonses.com	t
10	Seed C6 Jogador 2	success+seed-c6-jogador-2@simulator.amazonses.com	t
11	Seed C9 Jogador 1	success+seed-c9-jogador-1@simulator.amazonses.com	t
12	Seed C9 Jogador 2	success+seed-c9-jogador-2@simulator.amazonses.com	t
13	Seed C9 Jogador 3	success+seed-c9-jogador-3@simulator.amazonses.com	t
14	Seed C10 Jogador 1	success+seed-c10-jogador-1@simulator.amazonses.com	t
15	Seed C10 Jogador 2	success+seed-c10-jogador-2@simulator.amazonses.com	t
16	Seed C10 Jogador 3	success+seed-c10-jogador-3@simulator.amazonses.com	t
17	Seed C11 Jogador 1	success+seed-c11-jogador-1@simulator.amazonses.com	t
18	Seed C11 Jogador 2	success+seed-c11-jogador-2@simulator.amazonses.com	t
19	Seed Multi M1	success+seed-multi-m1@simulator.amazonses.com	t
20	Seed Multi M2	success+seed-multi-m2@simulator.amazonses.com	t
21	Seed Multi M3	success+seed-multi-m3@simulator.amazonses.com	t
22	Seed Multi M4	success+seed-multi-m4@simulator.amazonses.com	t
\.


--
-- Data for Name: competition; Type: TABLE DATA; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

COPY jogo_acoes.competition (id, name, type, start_date, duration_days, recurring, buy_fee, sell_fee, status, creator_id) FROM stdin;
1	[seed] C1 Copa publica	PUBLIC	2026-09-13	30	f	0.50	0.50	OPEN	1
2	[seed] C2 Copa publica com jogadores	PUBLIC	2026-09-13	30	f	0.50	0.50	OPEN	1
3	[seed] C3 Copa publica recorrente	PUBLIC	2026-09-13	30	t	0.50	0.50	OPEN	1
4	[seed] C4 Copa privada aguardando convites	PRIVATE	2026-09-13	30	f	0.50	0.50	AWAITING_INVITES	1
5	[seed] C5 Copa privada com convites enviados	PRIVATE	2026-09-13	30	f	0.50	0.50	OPEN	1
6	[seed] C6 Copa privada parcialmente confirmada	PRIVATE	2026-09-13	30	f	0.50	0.50	OPEN	1
7	[seed] C7 Copa publica de taxa zero e duracao curta	PUBLIC	2026-09-13	1	f	0.00	0.00	OPEN	1
8	[seed] C8 Copa privada com jogadores ja registrados	PRIVATE	2026-09-13	30	f	0.50	0.50	OPEN	1
9	[seed] C9 Copa publica em andamento	PUBLIC	2026-09-13	60	f	0.50	0.50	OPEN	1
10	[seed] C10 Copa publica terminada por data	PUBLIC	2026-09-13	10	f	0.50	0.50	OPEN	1
11	[seed] C11 Copa privada em andamento	PRIVATE	2026-09-13	60	f	0.50	0.50	OPEN	1
\.


--
-- Data for Name: jogo_acoes_schema_history; Type: TABLE DATA; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

COPY jogo_acoes.jogo_acoes_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init schema	SQL	V1__init_schema.sql	-1490224053	jogo_acoes_admin	2026-10-12 21:51:29.751574	83	t
2	2	add log table	SQL	V2__add_log_table.sql	436197341	jogo_acoes_admin	2026-10-12 21:51:30.96721	55	t
3	3	add spring session schema	SQL	V3__add_spring_session_schema.sql	-973511306	jogo_acoes_admin	2026-10-12 21:51:31.269134	36	t
4	4	add sent email table	SQL	V4__add_sent_email_table.sql	450938011	jogo_acoes_admin	2026-10-12 21:51:31.549282	11	t
5	5	seed roles	SQL	V5__seed_roles.sql	598362273	jogo_acoes_admin	2026-10-12 21:51:31.758505	3	t
6	6	decouple login link into link record	SQL	V6__decouple_login_link_into_link_record.sql	-198044550	jogo_acoes_admin	2026-10-12 21:51:31.925223	13	t
7	7	add http session id to login session	SQL	V7__add_http_session_id_to_login_session.sql	612977308	jogo_acoes_admin	2026-10-12 21:51:32.00917	11	t
8	8	add email service id to sent email	SQL	V8__add_email_service_id_to_sent_email.sql	-1603473693	jogo_acoes_admin	2026-10-12 21:51:32.129973	144	t
9	9	index participation user and competition creator	SQL	V9__index_participation_user_and_competition_creator.sql	810151202	jogo_acoes_admin	2026-10-12 21:51:32.381055	183	t
\.


--
-- Data for Name: link_record; Type: TABLE DATA; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

COPY jogo_acoes.link_record (id, token, email, email_sent_at, expires_at, used_at, invalidated_at, user_id, service_key, extra_json) FROM stdin;
1	3c7a9046-be67-427f-88fa-dcbcdd6ae94e	success+admin@simulator.amazonses.com	2026-09-12 21:53:15.530014	2026-09-19 21:53:15.53003	\N	2026-09-12 21:54:26.057005	1	login	{}
2	63820045-6cc4-4a9b-9042-e7a9ab7a5653	success+admin@simulator.amazonses.com	2026-09-12 21:54:26.393421	2026-09-19 21:54:26.393446	\N	2026-09-12 21:55:05.805234	1	login	{}
3	7d2a9344-5fd0-4ca2-85ed-62fcbde4f712	success+admin@simulator.amazonses.com	2026-09-12 21:55:06.03365	2026-09-19 21:55:06.033667	2026-09-12 21:55:07.050541	\N	1	login	{}
4	7ce0ffd8-649f-4198-9620-e1af5e6c2a01	success+seed-c2-jogador-1@simulator.amazonses.com	2026-09-12 21:55:10.984894	2026-09-19 21:55:10.984903	2026-09-12 21:55:12.0865	\N	\N	competition-entry	{"participationId":"1"}
5	79c7edfb-e6b6-449e-a6f3-87e87b7b77d7	success+seed-c2-jogador-2@simulator.amazonses.com	2026-09-12 21:55:13.737556	2026-09-19 21:55:13.737566	2026-09-12 21:55:15.47298	\N	\N	competition-entry	{"participationId":"2"}
6	8a4680bd-9d55-4296-ac16-9b609b9daf21	success+seed-c2-jogador-3@simulator.amazonses.com	2026-09-12 21:55:16.547733	2026-09-19 21:55:16.54774	2026-09-12 21:55:17.230499	\N	\N	competition-entry	{"participationId":"3"}
7	6599ba0d-4ff8-4363-b6f2-75930c834f88	success+seed-c2-jogador-4@simulator.amazonses.com	2026-09-12 21:55:17.992234	2026-09-19 21:55:17.992238	2026-09-12 21:55:19.590772	\N	\N	competition-entry	{"participationId":"4"}
8	58a53ffa-11fe-42d1-8ee2-b160f1c5d6a7	success+seed-c2-jogador-5@simulator.amazonses.com	2026-09-12 21:55:20.255432	2026-09-19 21:55:20.255438	2026-09-12 21:55:21.665172	\N	\N	competition-entry	{"participationId":"5"}
9	4825d927-c83d-4d2d-9291-98d0a977e809	success+seed-c3-jogador-1@simulator.amazonses.com	2026-09-12 21:55:23.467995	2026-09-19 21:55:23.468	2026-09-12 21:55:24.699874	\N	\N	competition-entry	{"participationId":"6"}
10	44bc8a27-563f-415e-8873-945ad48887db	success+seed-c3-jogador-2@simulator.amazonses.com	2026-09-12 21:55:25.270359	2026-09-19 21:55:25.270362	2026-09-12 21:55:27.771697	\N	\N	competition-entry	{"participationId":"7"}
11	1b61cb2f-9c88-45dc-97c6-10666ef1b94f	success+seed-c5-jogador-1@simulator.amazonses.com	2026-09-12 21:55:30.471108	2026-09-19 21:55:30.471114	\N	\N	\N	competition-entry	{"participationId":"11"}
12	57d45307-ab34-4a5a-a474-5b42083a29c7	success+seed-c5-jogador-2@simulator.amazonses.com	2026-09-12 21:55:31.617045	2026-09-19 21:55:31.61705	\N	\N	\N	competition-entry	{"participationId":"12"}
13	875a4259-3881-4c85-84bf-eea684eb372d	success+seed-c5-jogador-3@simulator.amazonses.com	2026-09-12 21:55:31.849555	2026-09-19 21:55:31.849559	\N	\N	\N	competition-entry	{"participationId":"13"}
16	ad743681-1ce5-4146-b8a8-1281026d064d	success+seed-c6-jogador-3@simulator.amazonses.com	2026-09-12 21:55:33.538105	2026-09-19 21:55:33.538109	\N	\N	\N	competition-entry	{"participationId":"16"}
14	1773c24d-4618-4e83-b703-ac907758a34b	success+seed-c6-jogador-1@simulator.amazonses.com	2026-09-12 21:55:33.10676	2026-09-19 21:55:33.106764	2026-09-12 21:55:34.891693	\N	\N	competition-entry	{"participationId":"14"}
15	7d948126-3829-4cc7-a66d-9abc3462cc64	success+seed-c6-jogador-2@simulator.amazonses.com	2026-09-12 21:55:33.318132	2026-09-19 21:55:33.318136	2026-09-12 21:55:36.231653	\N	\N	competition-entry	{"participationId":"15"}
17	69b2ddd9-9c86-42db-a13f-956631aec313	success+seed-c2-jogador-1@simulator.amazonses.com	2026-09-12 21:55:38.158396	2026-09-19 21:55:38.158401	\N	\N	2	competition-entry	{"participationId":"17"}
18	ebebfbf7-8358-40ca-8575-c77e9e65fba1	success+seed-c2-jogador-2@simulator.amazonses.com	2026-09-12 21:55:38.597811	2026-09-19 21:55:38.597815	\N	\N	3	competition-entry	{"participationId":"18"}
19	1f8603bb-acc6-4e53-a5c8-74ca8a7a626a	success+seed-c9-jogador-1@simulator.amazonses.com	2026-09-12 21:55:39.355634	2026-09-19 21:55:39.355637	2026-09-12 21:55:40.744567	\N	\N	competition-entry	{"participationId":"19"}
20	4c977f93-c518-44e8-acf7-3e1cac6ed082	success+seed-c9-jogador-2@simulator.amazonses.com	2026-09-12 21:55:41.177767	2026-09-19 21:55:41.177772	2026-09-12 21:55:42.009135	\N	\N	competition-entry	{"participationId":"20"}
21	a7e14b4a-ebdf-4ab6-bcd5-ecea0aecd3ca	success+seed-c9-jogador-3@simulator.amazonses.com	2026-09-12 21:55:42.485242	2026-09-19 21:55:42.485247	2026-09-12 21:55:43.618507	\N	\N	competition-entry	{"participationId":"21"}
22	3c250313-5674-4349-a2d3-926466f45418	success+seed-c10-jogador-1@simulator.amazonses.com	2026-09-12 21:55:44.153349	2026-09-19 21:55:44.153355	2026-09-12 21:55:45.025587	\N	\N	competition-entry	{"participationId":"22"}
23	6c5979be-4885-429d-91fd-d8c2a4b78b59	success+seed-c10-jogador-2@simulator.amazonses.com	2026-09-12 21:55:45.449421	2026-09-19 21:55:45.449425	2026-09-12 21:55:46.217134	\N	\N	competition-entry	{"participationId":"23"}
24	f4d3c65a-fc55-4eec-9435-0360e8dd9af8	success+seed-c10-jogador-3@simulator.amazonses.com	2026-09-12 21:55:47.593217	2026-09-19 21:55:47.593222	2026-09-12 21:55:48.121388	\N	\N	competition-entry	{"participationId":"24"}
27	6a370010-5507-44de-8082-f2c82daf7061	success+seed-c11-jogador-3@simulator.amazonses.com	2026-09-12 21:55:49.425367	2026-09-19 21:55:49.425371	\N	\N	\N	competition-entry	{"participationId":"27"}
25	1b4e7558-afe7-4f33-b478-0156555b8b6b	success+seed-c11-jogador-1@simulator.amazonses.com	2026-09-12 21:55:48.953195	2026-09-19 21:55:48.9532	2026-09-12 21:55:51.128396	\N	\N	competition-entry	{"participationId":"25"}
26	7b8939eb-af71-4daa-bf66-dc254d969337	success+seed-c11-jogador-2@simulator.amazonses.com	2026-09-12 21:55:49.193743	2026-09-19 21:55:49.19375	2026-09-12 21:55:51.566464	\N	\N	competition-entry	{"participationId":"26"}
28	757017bb-b32e-40ca-b136-eafc9ffc8df9	success+seed-multi-m1@simulator.amazonses.com	2026-09-12 21:55:51.986931	2026-09-19 21:55:51.986935	2026-09-12 21:55:52.622093	\N	\N	competition-entry	{"participationId":"28"}
29	a0086695-d82d-470c-9a4e-19783d29c72c	success+seed-multi-m2@simulator.amazonses.com	2026-09-12 21:55:53.496591	2026-09-19 21:55:53.496599	2026-09-12 21:55:54.725088	\N	\N	competition-entry	{"participationId":"29"}
30	c186fc44-8eab-405f-be9b-6e8ed3fd6042	success+seed-multi-m2@simulator.amazonses.com	2026-09-12 21:55:55.434027	2026-09-19 21:55:55.434033	2026-09-12 21:55:56.091295	\N	20	login	{}
31	93e4981c-1f3e-440c-b006-912da74497dd	success+seed-multi-m3@simulator.amazonses.com	2026-09-12 21:55:59.663116	2026-09-19 21:55:59.663121	2026-09-12 21:56:00.77236	\N	\N	competition-entry	{"participationId":"31"}
32	a9892aaa-0d2b-4102-a5af-596eef12cfed	success+seed-multi-m3@simulator.amazonses.com	2026-09-12 21:56:01.221267	2026-09-19 21:56:01.221274	2026-09-12 21:56:01.645237	\N	21	login	{}
33	cbf8b21b-0013-43e6-9f48-3146a3d827a8	success+seed-multi-m3@simulator.amazonses.com	2026-09-12 21:56:04.795693	2026-09-19 21:56:04.800509	\N	2026-09-12 21:56:06.157126	21	competition-entry	{"participationId":"33"}
34	349ac99e-1bdc-44b4-8b60-ea40052f75eb	success+seed-multi-m3@simulator.amazonses.com	2026-09-12 21:56:06.254644	2026-09-19 21:56:06.254655	2026-09-12 21:56:06.8403	\N	21	login	{}
35	e9d6d827-7746-41ae-8cad-f9bd6d3b21ea	success+seed-multi-m4@simulator.amazonses.com	2026-09-12 21:56:08.137598	2026-09-19 21:56:08.137602	2026-09-12 21:56:09.421106	\N	\N	competition-entry	{"participationId":"34"}
36	16141e69-b402-455f-a4f2-a2c40d57a0a9	success+seed-multi-m4@simulator.amazonses.com	2026-09-12 21:56:10.050479	2026-09-19 21:56:10.050485	2026-09-12 21:56:10.937971	\N	22	login	{}
37	012bf39f-321d-4fe8-9b7b-ef149996d4ef	success+seed-multi-m4@simulator.amazonses.com	2026-09-12 21:56:12.633888	2026-09-19 21:56:12.633892	\N	2026-09-12 21:56:13.089239	22	competition-entry	{"participationId":"36"}
38	4644d8b2-f227-4ecb-9c55-e67c3402d3a4	success+seed-multi-m4@simulator.amazonses.com	2026-09-12 21:56:13.099147	2026-09-19 21:56:13.099152	2026-09-12 21:56:13.943174	\N	22	login	{}
39	aaec92f1-372a-4420-9c6e-58a9d3e396a0	success+seed-multi-m4@simulator.amazonses.com	2026-09-12 21:56:15.309069	2026-09-19 21:56:15.309075	\N	\N	22	competition-entry	{"participationId":"37"}
\.


--
-- Data for Name: log; Type: TABLE DATA; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

COPY jogo_acoes.log (id, related_object_id, user_id, created_at, log_type, message) FROM stdin;
1	1	1	2026-09-12 21:53:15.685269	LOGIN_LINK_ISSUED	Login link issued to success+admin@simulator.amazonses.com
2	2	1	2026-09-12 21:54:26.445019	LOGIN_LINK_ISSUED	Login link issued to success+admin@simulator.amazonses.com
3	3	1	2026-09-12 21:55:06.113315	LOGIN_LINK_ISSUED	Login link issued to success+admin@simulator.amazonses.com
4	1	1	2026-09-12 21:55:09.267478	COMPETITION_CREATED	Competition "[seed] C1 Copa publica" created
5	2	1	2026-09-12 21:55:10.281163	COMPETITION_CREATED	Competition "[seed] C2 Copa publica com jogadores" created
6	4	\N	2026-09-12 21:55:11.043155	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c2-jogador-1@simulator.amazonses.com
7	1	\N	2026-09-12 21:55:11.169226	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
8	1	2	2026-09-12 21:55:12.042767	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
9	5	\N	2026-09-12 21:55:13.740734	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c2-jogador-2@simulator.amazonses.com
10	2	\N	2026-09-12 21:55:14.136848	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
11	2	3	2026-09-12 21:55:15.405919	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
12	6	\N	2026-09-12 21:55:16.559048	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c2-jogador-3@simulator.amazonses.com
13	3	\N	2026-09-12 21:55:16.677479	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
14	3	4	2026-09-12 21:55:17.198677	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
15	7	\N	2026-09-12 21:55:18.033607	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c2-jogador-4@simulator.amazonses.com
16	4	\N	2026-09-12 21:55:18.432637	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
17	4	5	2026-09-12 21:55:19.578129	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
18	8	\N	2026-09-12 21:55:20.281366	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c2-jogador-5@simulator.amazonses.com
19	5	\N	2026-09-12 21:55:20.407045	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
20	5	6	2026-09-12 21:55:21.601179	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
21	3	1	2026-09-12 21:55:22.515098	COMPETITION_CREATED	Competition "[seed] C3 Copa publica recorrente" created
22	9	\N	2026-09-12 21:55:23.501506	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c3-jogador-1@simulator.amazonses.com
23	6	\N	2026-09-12 21:55:23.901662	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
24	6	7	2026-09-12 21:55:24.59421	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
25	10	\N	2026-09-12 21:55:25.27227	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c3-jogador-2@simulator.amazonses.com
26	7	\N	2026-09-12 21:55:25.587366	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
27	7	8	2026-09-12 21:55:27.750952	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
28	4	1	2026-09-12 21:55:28.269582	COMPETITION_CREATED	Competition "[seed] C4 Copa privada aguardando convites" created
29	8	1	2026-09-12 21:55:28.321477	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c4-jogador-1@simulator.amazonses.com created with status EMAIL_NOT_SENT
30	9	1	2026-09-12 21:55:28.433261	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c4-jogador-2@simulator.amazonses.com created with status EMAIL_NOT_SENT
31	10	1	2026-09-12 21:55:28.492688	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c4-jogador-3@simulator.amazonses.com created with status EMAIL_NOT_SENT
32	5	1	2026-09-12 21:55:29.759893	COMPETITION_CREATED	Competition "[seed] C5 Copa privada com convites enviados" created
33	11	1	2026-09-12 21:55:29.870346	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c5-jogador-1@simulator.amazonses.com created with status EMAIL_NOT_SENT
34	12	1	2026-09-12 21:55:29.94659	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c5-jogador-2@simulator.amazonses.com created with status EMAIL_NOT_SENT
35	13	1	2026-09-12 21:55:30.045306	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c5-jogador-3@simulator.amazonses.com created with status EMAIL_NOT_SENT
36	11	1	2026-09-12 21:55:30.751074	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c5-jogador-1@simulator.amazonses.com
37	11	1	2026-09-12 21:55:31.539206	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
38	12	1	2026-09-12 21:55:31.623076	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c5-jogador-2@simulator.amazonses.com
39	12	1	2026-09-12 21:55:31.846069	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
40	13	1	2026-09-12 21:55:31.854847	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c5-jogador-3@simulator.amazonses.com
41	13	1	2026-09-12 21:55:32.110014	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
42	6	1	2026-09-12 21:55:32.36602	COMPETITION_CREATED	Competition "[seed] C6 Copa privada parcialmente confirmada" created
43	14	1	2026-09-12 21:55:32.443164	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c6-jogador-1@simulator.amazonses.com created with status EMAIL_NOT_SENT
44	15	1	2026-09-12 21:55:32.596957	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c6-jogador-2@simulator.amazonses.com created with status EMAIL_NOT_SENT
45	16	1	2026-09-12 21:55:32.63011	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c6-jogador-3@simulator.amazonses.com created with status EMAIL_NOT_SENT
46	14	1	2026-09-12 21:55:33.109718	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c6-jogador-1@simulator.amazonses.com
47	14	1	2026-09-12 21:55:33.311559	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
48	15	1	2026-09-12 21:55:33.325837	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c6-jogador-2@simulator.amazonses.com
49	15	1	2026-09-12 21:55:33.497259	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
50	16	1	2026-09-12 21:55:33.539877	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c6-jogador-3@simulator.amazonses.com
51	16	1	2026-09-12 21:55:33.59812	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
52	14	9	2026-09-12 21:55:34.88861	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
53	15	10	2026-09-12 21:55:36.173257	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
54	7	1	2026-09-12 21:55:36.853884	COMPETITION_CREATED	Competition "[seed] C7 Copa publica de taxa zero e duracao curta" created
55	8	1	2026-09-12 21:55:37.605323	COMPETITION_CREATED	Competition "[seed] C8 Copa privada com jogadores ja registrados" created
56	17	1	2026-09-12 21:55:37.889821	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c2-jogador-1@simulator.amazonses.com created with status EMAIL_NOT_SENT
57	18	1	2026-09-12 21:55:37.901128	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c2-jogador-2@simulator.amazonses.com created with status EMAIL_NOT_SENT
58	17	1	2026-09-12 21:55:38.359137	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c2-jogador-1@simulator.amazonses.com
59	17	1	2026-09-12 21:55:38.593116	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
60	18	1	2026-09-12 21:55:38.72236	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c2-jogador-2@simulator.amazonses.com
61	18	1	2026-09-12 21:55:38.879977	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
62	9	1	2026-09-12 21:55:39.206126	COMPETITION_CREATED	Competition "[seed] C9 Copa publica em andamento" created
63	19	\N	2026-09-12 21:55:39.360131	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c9-jogador-1@simulator.amazonses.com
64	19	\N	2026-09-12 21:55:39.429918	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
65	19	11	2026-09-12 21:55:40.493635	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
66	20	\N	2026-09-12 21:55:41.233122	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c9-jogador-2@simulator.amazonses.com
67	20	\N	2026-09-12 21:55:41.354128	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
68	20	12	2026-09-12 21:55:42.006446	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
69	21	\N	2026-09-12 21:55:42.550775	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c9-jogador-3@simulator.amazonses.com
70	21	\N	2026-09-12 21:55:42.613876	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
71	21	13	2026-09-12 21:55:43.614956	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
72	10	1	2026-09-12 21:55:44.002291	COMPETITION_CREATED	Competition "[seed] C10 Copa publica terminada por data" created
73	22	\N	2026-09-12 21:55:44.161353	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c10-jogador-1@simulator.amazonses.com
74	22	\N	2026-09-12 21:55:44.440361	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
75	22	14	2026-09-12 21:55:45.018155	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
76	23	\N	2026-09-12 21:55:45.451184	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c10-jogador-2@simulator.amazonses.com
77	23	\N	2026-09-12 21:55:45.655638	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
78	23	15	2026-09-12 21:55:46.201721	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
79	24	\N	2026-09-12 21:55:47.59519	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-c10-jogador-3@simulator.amazonses.com
80	24	\N	2026-09-12 21:55:47.725488	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
81	24	16	2026-09-12 21:55:48.113795	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
82	11	1	2026-09-12 21:55:48.535095	COMPETITION_CREATED	Competition "[seed] C11 Copa privada em andamento" created
83	25	1	2026-09-12 21:55:48.582063	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c11-jogador-1@simulator.amazonses.com created with status EMAIL_NOT_SENT
84	26	1	2026-09-12 21:55:48.589926	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c11-jogador-2@simulator.amazonses.com created with status EMAIL_NOT_SENT
85	27	1	2026-09-12 21:55:48.78795	PARTICIPATION_STATUS_CHANGED	Participation for success+seed-c11-jogador-3@simulator.amazonses.com created with status EMAIL_NOT_SENT
86	25	1	2026-09-12 21:55:48.961461	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c11-jogador-1@simulator.amazonses.com
87	25	1	2026-09-12 21:55:49.109271	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
88	26	1	2026-09-12 21:55:49.269566	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c11-jogador-2@simulator.amazonses.com
89	26	1	2026-09-12 21:55:49.406238	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
90	27	1	2026-09-12 21:55:49.427083	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-c11-jogador-3@simulator.amazonses.com
91	27	1	2026-09-12 21:55:49.516113	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
92	25	17	2026-09-12 21:55:51.125801	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
93	26	18	2026-09-12 21:55:51.546401	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
94	28	\N	2026-09-12 21:55:51.992795	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-multi-m1@simulator.amazonses.com
95	28	\N	2026-09-12 21:55:52.066068	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
96	28	19	2026-09-12 21:55:52.617882	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
97	29	\N	2026-09-12 21:55:53.50993	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-multi-m2@simulator.amazonses.com
98	29	\N	2026-09-12 21:55:53.666871	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
99	29	20	2026-09-12 21:55:54.473601	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
100	30	20	2026-09-12 21:55:55.448877	LOGIN_LINK_ISSUED	Login link issued to success+seed-multi-m2@simulator.amazonses.com
101	30	20	2026-09-12 21:55:59.301071	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION
102	31	\N	2026-09-12 21:55:59.704329	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-multi-m3@simulator.amazonses.com
103	31	\N	2026-09-12 21:56:00.046336	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
104	31	21	2026-09-12 21:56:00.768652	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
105	32	21	2026-09-12 21:56:01.31742	LOGIN_LINK_ISSUED	Login link issued to success+seed-multi-m3@simulator.amazonses.com
106	32	21	2026-09-12 21:56:02.532788	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION
107	33	1	2026-09-12 21:56:04.804153	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-multi-m3@simulator.amazonses.com
108	33	1	2026-09-12 21:56:05.269116	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
109	34	21	2026-09-12 21:56:06.264989	LOGIN_LINK_ISSUED	Login link issued to success+seed-multi-m3@simulator.amazonses.com
110	33	21	2026-09-12 21:56:07.585969	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION
111	35	\N	2026-09-12 21:56:08.168641	LOGIN_LINK_ISSUED	Entry request login link issued to success+seed-multi-m4@simulator.amazonses.com
112	34	\N	2026-09-12 21:56:08.334082	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
113	34	22	2026-09-12 21:56:09.401597	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION (registration completed)
114	36	22	2026-09-12 21:56:10.072888	LOGIN_LINK_ISSUED	Login link issued to success+seed-multi-m4@simulator.amazonses.com
115	35	22	2026-09-12 21:56:12.042215	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION
116	37	1	2026-09-12 21:56:12.679778	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-multi-m4@simulator.amazonses.com
117	36	1	2026-09-12 21:56:12.825107	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
118	38	22	2026-09-12 21:56:13.205514	LOGIN_LINK_ISSUED	Login link issued to success+seed-multi-m4@simulator.amazonses.com
119	36	22	2026-09-12 21:56:14.661586	PARTICIPATION_STATUS_CHANGED	Participation status changed to IN_COMPETITION
120	39	1	2026-09-12 21:56:15.313322	LOGIN_LINK_ISSUED	Invite login link issued to success+seed-multi-m4@simulator.amazonses.com
121	37	1	2026-09-12 21:56:15.519367	PARTICIPATION_STATUS_CHANGED	Participation status changed to EMAIL_SENT
\.


--
-- Data for Name: participation; Type: TABLE DATA; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

COPY jogo_acoes.participation (id, competition_id, user_id, email, status, request_type, first_email_sent_date, joined_at) FROM stdin;
24	10	16	success+seed-c10-jogador-3@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
1	2	2	success+seed-c2-jogador-1@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
2	2	3	success+seed-c2-jogador-2@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
3	2	4	success+seed-c2-jogador-3@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
4	2	5	success+seed-c2-jogador-4@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
5	2	6	success+seed-c2-jogador-5@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
6	3	7	success+seed-c3-jogador-1@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
27	11	\N	success+seed-c11-jogador-3@simulator.amazonses.com	EMAIL_SENT	INVITE	2026-09-12	\N
7	3	8	success+seed-c3-jogador-2@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
8	4	\N	success+seed-c4-jogador-1@simulator.amazonses.com	EMAIL_NOT_SENT	INVITE	\N	\N
9	4	\N	success+seed-c4-jogador-2@simulator.amazonses.com	EMAIL_NOT_SENT	INVITE	\N	\N
10	4	\N	success+seed-c4-jogador-3@simulator.amazonses.com	EMAIL_NOT_SENT	INVITE	\N	\N
11	5	\N	success+seed-c5-jogador-1@simulator.amazonses.com	EMAIL_SENT	INVITE	2026-09-12	\N
12	5	\N	success+seed-c5-jogador-2@simulator.amazonses.com	EMAIL_SENT	INVITE	2026-09-12	\N
13	5	\N	success+seed-c5-jogador-3@simulator.amazonses.com	EMAIL_SENT	INVITE	2026-09-12	\N
25	11	17	success+seed-c11-jogador-1@simulator.amazonses.com	IN_COMPETITION	INVITE	2026-09-12	2026-09-12
26	11	18	success+seed-c11-jogador-2@simulator.amazonses.com	IN_COMPETITION	INVITE	2026-09-12	2026-09-12
16	6	\N	success+seed-c6-jogador-3@simulator.amazonses.com	EMAIL_SENT	INVITE	2026-09-12	\N
14	6	9	success+seed-c6-jogador-1@simulator.amazonses.com	IN_COMPETITION	INVITE	2026-09-12	2026-09-12
15	6	10	success+seed-c6-jogador-2@simulator.amazonses.com	IN_COMPETITION	INVITE	2026-09-12	2026-09-12
17	8	2	success+seed-c2-jogador-1@simulator.amazonses.com	EMAIL_SENT	INVITE	2026-09-12	\N
18	8	3	success+seed-c2-jogador-2@simulator.amazonses.com	EMAIL_SENT	INVITE	2026-09-12	\N
19	9	11	success+seed-c9-jogador-1@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
20	9	12	success+seed-c9-jogador-2@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
28	9	19	success+seed-multi-m1@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
21	9	13	success+seed-c9-jogador-3@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
22	10	14	success+seed-c10-jogador-1@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
23	10	15	success+seed-c10-jogador-2@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
29	9	20	success+seed-multi-m2@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
30	10	20	success+seed-multi-m2@simulator.amazonses.com	IN_COMPETITION	REQUEST	\N	2026-09-12
31	9	21	success+seed-multi-m3@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
32	10	21	success+seed-multi-m3@simulator.amazonses.com	IN_COMPETITION	REQUEST	\N	2026-09-12
33	11	21	success+seed-multi-m3@simulator.amazonses.com	IN_COMPETITION	INVITE	2026-09-12	2026-09-12
34	9	22	success+seed-multi-m4@simulator.amazonses.com	IN_COMPETITION	REQUEST	2026-09-12	2026-09-12
35	10	22	success+seed-multi-m4@simulator.amazonses.com	IN_COMPETITION	REQUEST	\N	2026-09-12
36	11	22	success+seed-multi-m4@simulator.amazonses.com	IN_COMPETITION	INVITE	2026-09-12	2026-09-12
37	5	22	success+seed-multi-m4@simulator.amazonses.com	EMAIL_SENT	INVITE	2026-09-12	\N
\.


--
-- Data for Name: role; Type: TABLE DATA; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

COPY jogo_acoes.role (id, name) FROM stdin;
1	ADMINISTRATOR
2	PLAYER
\.


--
-- Data for Name: sent_email; Type: TABLE DATA; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

COPY jogo_acoes.sent_email (id, user_id, email, link, template, sent_at, email_service_id) FROM stdin;
1	1	success+admin@simulator.amazonses.com	/login-links/3c7a9046-be67-427f-88fa-dcbcdd6ae94e	LOGIN_LINK	2026-09-12 21:53:17.714708	43fb1a54-7116-462d-9b45-8e963df00bf6
2	1	success+admin@simulator.amazonses.com	/login-links/63820045-6cc4-4a9b-9042-e7a9ab7a5653	LOGIN_LINK	2026-09-12 21:54:26.601433	06d2b048-6b1b-413e-a927-39b38ff2537a
3	1	success+admin@simulator.amazonses.com	/login-links/7d2a9344-5fd0-4ca2-85ed-62fcbde4f712	LOGIN_LINK	2026-09-12 21:55:06.207118	ed40db38-82d5-47b0-a13c-9fb2c47b98cf
4	\N	success+seed-c2-jogador-1@simulator.amazonses.com	/login-links/7ce0ffd8-649f-4198-9620-e1af5e6c2a01	REGISTRATION_LINK	2026-09-12 21:55:11.113749	bd5e0874-35fb-4718-a1cf-8d545df44f26
5	\N	success+seed-c2-jogador-2@simulator.amazonses.com	/login-links/79c7edfb-e6b6-449e-a6f3-87e87b7b77d7	REGISTRATION_LINK	2026-09-12 21:55:14.069725	e6d896b4-e139-46b7-b3c7-3eab264c30c7
6	\N	success+seed-c2-jogador-3@simulator.amazonses.com	/login-links/8a4680bd-9d55-4296-ac16-9b609b9daf21	REGISTRATION_LINK	2026-09-12 21:55:16.622757	eefe7271-2189-4f17-9e2e-fdf5ef7bcaa4
7	\N	success+seed-c2-jogador-4@simulator.amazonses.com	/login-links/6599ba0d-4ff8-4363-b6f2-75930c834f88	REGISTRATION_LINK	2026-09-12 21:55:18.32601	5d823ab1-d691-49ea-aa97-4b5edbd7aa64
8	\N	success+seed-c2-jogador-5@simulator.amazonses.com	/login-links/58a53ffa-11fe-42d1-8ee2-b160f1c5d6a7	REGISTRATION_LINK	2026-09-12 21:55:20.378132	dc546823-169d-40c4-b9b6-71e14862d46d
9	\N	success+seed-c3-jogador-1@simulator.amazonses.com	/login-links/4825d927-c83d-4d2d-9291-98d0a977e809	REGISTRATION_LINK	2026-09-12 21:55:23.829679	a3ce5272-945f-4eaa-9e35-1164440450fc
10	\N	success+seed-c3-jogador-2@simulator.amazonses.com	/login-links/44bc8a27-563f-415e-8873-945ad48887db	REGISTRATION_LINK	2026-09-12 21:55:25.453649	db10dd4c-8543-4bf4-80c5-9effca0df6ce
11	\N	success+seed-c5-jogador-1@simulator.amazonses.com	/login-links/1b61cb2f-9c88-45dc-97c6-10666ef1b94f	INVITE	2026-09-12 21:55:31.31868	aba5fa51-a0d2-4604-92da-26fbccc6bb4b
12	\N	success+seed-c5-jogador-2@simulator.amazonses.com	/login-links/57d45307-ab34-4a5a-a474-5b42083a29c7	INVITE	2026-09-12 21:55:31.831743	04df878a-a444-40a5-9136-c154e91536ec
13	\N	success+seed-c5-jogador-3@simulator.amazonses.com	/login-links/875a4259-3881-4c85-84bf-eea684eb372d	INVITE	2026-09-12 21:55:31.997419	23563bc3-2d88-4949-9499-0592a5be4845
14	\N	success+seed-c6-jogador-1@simulator.amazonses.com	/login-links/1773c24d-4618-4e83-b703-ac907758a34b	INVITE	2026-09-12 21:55:33.219053	0cdcedd9-3a56-4a11-acde-588dbffe43d4
15	\N	success+seed-c6-jogador-2@simulator.amazonses.com	/login-links/7d948126-3829-4cc7-a66d-9abc3462cc64	INVITE	2026-09-12 21:55:33.433485	cdae3340-8064-49b8-b60a-8ccc86bc3913
16	\N	success+seed-c6-jogador-3@simulator.amazonses.com	/login-links/ad743681-1ce5-4146-b8a8-1281026d064d	INVITE	2026-09-12 21:55:33.589562	8034cc13-bbb0-4ba8-a66e-b952aba2cb37
17	2	success+seed-c2-jogador-1@simulator.amazonses.com	/login-links/69b2ddd9-9c86-42db-a13f-956631aec313	LOGIN_LINK	2026-09-12 21:55:38.537991	cef5b46b-37fa-4fa5-8145-0a4bd909d396
18	3	success+seed-c2-jogador-2@simulator.amazonses.com	/login-links/ebebfbf7-8358-40ca-8575-c77e9e65fba1	LOGIN_LINK	2026-09-12 21:55:38.876954	d5a580a4-dad5-4c97-bb34-99edb72dad23
19	\N	success+seed-c9-jogador-1@simulator.amazonses.com	/login-links/1f8603bb-acc6-4e53-a5c8-74ca8a7a626a	REGISTRATION_LINK	2026-09-12 21:55:39.427577	3e978c45-4cbf-4929-9579-dc153aa6a7e2
20	\N	success+seed-c9-jogador-2@simulator.amazonses.com	/login-links/4c977f93-c518-44e8-acf7-3e1cac6ed082	REGISTRATION_LINK	2026-09-12 21:55:41.345402	117670ca-d1be-4a74-b2a5-37f746cfd999
21	\N	success+seed-c9-jogador-3@simulator.amazonses.com	/login-links/a7e14b4a-ebdf-4ab6-bcd5-ecea0aecd3ca	REGISTRATION_LINK	2026-09-12 21:55:42.611149	5cdb6d01-60db-4779-9f6c-f5ee1440870d
22	\N	success+seed-c10-jogador-1@simulator.amazonses.com	/login-links/3c250313-5674-4349-a2d3-926466f45418	REGISTRATION_LINK	2026-09-12 21:55:44.437777	0d8a28e7-8ca0-4512-8613-fc44156f7b01
23	\N	success+seed-c10-jogador-2@simulator.amazonses.com	/login-links/6c5979be-4885-429d-91fd-d8c2a4b78b59	REGISTRATION_LINK	2026-09-12 21:55:45.635344	75bd7693-306b-4c01-aa79-ca63c0a49df1
24	\N	success+seed-c10-jogador-3@simulator.amazonses.com	/login-links/f4d3c65a-fc55-4eec-9435-0360e8dd9af8	REGISTRATION_LINK	2026-09-12 21:55:47.72156	1524f88d-d3b0-44d8-8fe8-7dee37de0121
25	\N	success+seed-c11-jogador-1@simulator.amazonses.com	/login-links/1b4e7558-afe7-4f33-b478-0156555b8b6b	INVITE	2026-09-12 21:55:49.106312	6bccb9aa-7e9d-4e3e-9ea6-535db0f78528
26	\N	success+seed-c11-jogador-2@simulator.amazonses.com	/login-links/7b8939eb-af71-4daa-bf66-dc254d969337	INVITE	2026-09-12 21:55:49.393777	73d977ac-3e9d-4aa1-96dd-2c11be86d623
27	\N	success+seed-c11-jogador-3@simulator.amazonses.com	/login-links/6a370010-5507-44de-8082-f2c82daf7061	INVITE	2026-09-12 21:55:49.513376	22dc1881-cfb1-49a6-a625-9a027eb8bc0e
28	\N	success+seed-multi-m1@simulator.amazonses.com	/login-links/757017bb-b32e-40ca-b136-eafc9ffc8df9	REGISTRATION_LINK	2026-09-12 21:55:52.053761	cb2a9a72-ad85-4584-a744-16e259f5dd95
29	\N	success+seed-multi-m2@simulator.amazonses.com	/login-links/a0086695-d82d-470c-9a4e-19783d29c72c	REGISTRATION_LINK	2026-09-12 21:55:53.652136	8f3e1a0c-b11e-4c2d-99e2-ebdd0dfd3832
30	20	success+seed-multi-m2@simulator.amazonses.com	/login-links/c186fc44-8eab-405f-be9b-6e8ed3fd6042	LOGIN_LINK	2026-09-12 21:55:55.641772	759eaec5-a284-4882-a318-89dfc1c118ae
31	\N	success+seed-multi-m3@simulator.amazonses.com	/login-links/93e4981c-1f3e-440c-b006-912da74497dd	REGISTRATION_LINK	2026-09-12 21:55:59.871166	23581a56-6e44-4882-99ed-b60d663766da
32	21	success+seed-multi-m3@simulator.amazonses.com	/login-links/a9892aaa-0d2b-4102-a5af-596eef12cfed	LOGIN_LINK	2026-09-12 21:56:01.452578	842bfc4b-42fd-42ec-a5c6-9d751e2a0d97
33	21	success+seed-multi-m3@simulator.amazonses.com	/login-links/cbf8b21b-0013-43e6-9f48-3146a3d827a8	LOGIN_LINK	2026-09-12 21:56:05.254874	8ce2f654-7be6-4f9b-b025-c80a3d2f2839
34	21	success+seed-multi-m3@simulator.amazonses.com	/login-links/349ac99e-1bdc-44b4-8b60-ea40052f75eb	LOGIN_LINK	2026-09-12 21:56:06.3977	8e483c5e-5de9-4d76-9789-076b3446d4df
35	\N	success+seed-multi-m4@simulator.amazonses.com	/login-links/e9d6d827-7746-41ae-8cad-f9bd6d3b21ea	REGISTRATION_LINK	2026-09-12 21:56:08.261846	b8dbf0ec-89c6-4305-8943-89f9e1752023
36	22	success+seed-multi-m4@simulator.amazonses.com	/login-links/16141e69-b402-455f-a4f2-a2c40d57a0a9	LOGIN_LINK	2026-09-12 21:56:10.412899	07ce75a6-f963-462e-92cc-180ece4078e0
37	22	success+seed-multi-m4@simulator.amazonses.com	/login-links/012bf39f-321d-4fe8-9b7b-ef149996d4ef	LOGIN_LINK	2026-09-12 21:56:12.802147	935558c8-895f-4361-92d9-473c93f8c6ed
38	22	success+seed-multi-m4@simulator.amazonses.com	/login-links/4644d8b2-f227-4ecb-9c55-e67c3402d3a4	LOGIN_LINK	2026-09-12 21:56:13.318739	171eb423-4206-44d5-8796-5ac60551f66a
39	22	success+seed-multi-m4@simulator.amazonses.com	/login-links/aaec92f1-372a-4420-9c6e-58a9d3e396a0	LOGIN_LINK	2026-09-12 21:56:15.37182	c46948b9-f3d5-4ab8-a67d-8f12138f6adb
\.


--
-- Data for Name: user_role; Type: TABLE DATA; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

COPY jogo_acoes.user_role (user_id, role_id, assigned_at) FROM stdin;
1	1	2026-09-12 21:52:30.872389
2	2	2026-09-12 21:55:12.033569
3	2	2026-09-12 21:55:15.316891
4	2	2026-09-12 21:55:17.161256
5	2	2026-09-12 21:55:19.57045
6	2	2026-09-12 21:55:21.353107
7	2	2026-09-12 21:55:24.437277
8	2	2026-09-12 21:55:27.690805
9	2	2026-09-12 21:55:34.873353
10	2	2026-09-12 21:55:36.158827
11	2	2026-09-12 21:55:40.270625
12	2	2026-09-12 21:55:41.981054
13	2	2026-09-12 21:55:43.468753
14	2	2026-09-12 21:55:44.988945
15	2	2026-09-12 21:55:46.195306
16	2	2026-09-12 21:55:48.076934
17	2	2026-09-12 21:55:51.086864
18	2	2026-09-12 21:55:51.524858
19	2	2026-09-12 21:55:52.610497
20	2	2026-09-12 21:55:54.42207
21	2	2026-09-12 21:56:00.66157
22	2	2026-09-12 21:56:09.397002
\.


--
-- Name: app_user_id_seq; Type: SEQUENCE SET; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

SELECT pg_catalog.setval('jogo_acoes.app_user_id_seq', 22, true);


--
-- Name: competition_id_seq; Type: SEQUENCE SET; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

SELECT pg_catalog.setval('jogo_acoes.competition_id_seq', 11, true);


--
-- Name: log_id_seq; Type: SEQUENCE SET; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

SELECT pg_catalog.setval('jogo_acoes.log_id_seq', 121, true);


--
-- Name: login_link_id_seq; Type: SEQUENCE SET; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

SELECT pg_catalog.setval('jogo_acoes.login_link_id_seq', 39, true);


--
-- Name: login_session_id_seq; Type: SEQUENCE SET; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

SELECT pg_catalog.setval('jogo_acoes.login_session_id_seq', 27, true);


--
-- Name: participation_id_seq; Type: SEQUENCE SET; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

SELECT pg_catalog.setval('jogo_acoes.participation_id_seq', 37, true);


--
-- Name: role_id_seq; Type: SEQUENCE SET; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

SELECT pg_catalog.setval('jogo_acoes.role_id_seq', 2, true);


--
-- Name: sent_email_id_seq; Type: SEQUENCE SET; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

SELECT pg_catalog.setval('jogo_acoes.sent_email_id_seq', 39, true);


--
-- Name: app_user app_user_email_key; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.app_user
    ADD CONSTRAINT app_user_email_key UNIQUE (email);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: competition competition_pkey; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.competition
    ADD CONSTRAINT competition_pkey PRIMARY KEY (id);


--
-- Name: jogo_acoes_schema_history jogo_acoes_schema_history_pk; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.jogo_acoes_schema_history
    ADD CONSTRAINT jogo_acoes_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: log log_pkey; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.log
    ADD CONSTRAINT log_pkey PRIMARY KEY (id);


--
-- Name: link_record login_link_pkey; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.link_record
    ADD CONSTRAINT login_link_pkey PRIMARY KEY (id);


--
-- Name: link_record login_link_token_key; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.link_record
    ADD CONSTRAINT login_link_token_key UNIQUE (token);


--
-- Name: login_session login_session_login_link_id_key; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.login_session
    ADD CONSTRAINT login_session_login_link_id_key UNIQUE (link_record_id);


--
-- Name: login_session login_session_pkey; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.login_session
    ADD CONSTRAINT login_session_pkey PRIMARY KEY (id);


--
-- Name: participation participation_pkey; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.participation
    ADD CONSTRAINT participation_pkey PRIMARY KEY (id);


--
-- Name: role role_name_key; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.role
    ADD CONSTRAINT role_name_key UNIQUE (name);


--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);


--
-- Name: sent_email sent_email_pkey; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.sent_email
    ADD CONSTRAINT sent_email_pkey PRIMARY KEY (id);


--
-- Name: spring_session_attributes spring_session_attributes_pk; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name);


--
-- Name: spring_session spring_session_pk; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.spring_session
    ADD CONSTRAINT spring_session_pk PRIMARY KEY (primary_id);


--
-- Name: user_role user_role_pkey; Type: CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.user_role
    ADD CONSTRAINT user_role_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: competition_creator_id_idx; Type: INDEX; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE INDEX competition_creator_id_idx ON jogo_acoes.competition USING btree (creator_id);


--
-- Name: jogo_acoes_schema_history_s_idx; Type: INDEX; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE INDEX jogo_acoes_schema_history_s_idx ON jogo_acoes.jogo_acoes_schema_history USING btree (success);


--
-- Name: participation_user_id_idx; Type: INDEX; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE INDEX participation_user_id_idx ON jogo_acoes.participation USING btree (user_id);


--
-- Name: spring_session_ix1; Type: INDEX; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE UNIQUE INDEX spring_session_ix1 ON jogo_acoes.spring_session USING btree (session_id);


--
-- Name: spring_session_ix2; Type: INDEX; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE INDEX spring_session_ix2 ON jogo_acoes.spring_session USING btree (expiry_time);


--
-- Name: spring_session_ix3; Type: INDEX; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

CREATE INDEX spring_session_ix3 ON jogo_acoes.spring_session USING btree (principal_name);


--
-- Name: competition competition_creator_id_fkey; Type: FK CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.competition
    ADD CONSTRAINT competition_creator_id_fkey FOREIGN KEY (creator_id) REFERENCES jogo_acoes.app_user(id);


--
-- Name: log log_user_id_fkey; Type: FK CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.log
    ADD CONSTRAINT log_user_id_fkey FOREIGN KEY (user_id) REFERENCES jogo_acoes.app_user(id);


--
-- Name: login_session login_session_link_record_id_fkey; Type: FK CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.login_session
    ADD CONSTRAINT login_session_link_record_id_fkey FOREIGN KEY (link_record_id) REFERENCES jogo_acoes.link_record(id);


--
-- Name: participation participation_competition_id_fkey; Type: FK CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.participation
    ADD CONSTRAINT participation_competition_id_fkey FOREIGN KEY (competition_id) REFERENCES jogo_acoes.competition(id);


--
-- Name: participation participation_user_id_fkey; Type: FK CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.participation
    ADD CONSTRAINT participation_user_id_fkey FOREIGN KEY (user_id) REFERENCES jogo_acoes.app_user(id);


--
-- Name: sent_email sent_email_user_id_fkey; Type: FK CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.sent_email
    ADD CONSTRAINT sent_email_user_id_fkey FOREIGN KEY (user_id) REFERENCES jogo_acoes.app_user(id);


--
-- Name: spring_session_attributes spring_session_attributes_fk; Type: FK CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES jogo_acoes.spring_session(primary_id) ON DELETE CASCADE;


--
-- Name: user_role user_role_role_id_fkey; Type: FK CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.user_role
    ADD CONSTRAINT user_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES jogo_acoes.role(id);


--
-- Name: user_role user_role_user_id_fkey; Type: FK CONSTRAINT; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER TABLE ONLY jogo_acoes.user_role
    ADD CONSTRAINT user_role_user_id_fkey FOREIGN KEY (user_id) REFERENCES jogo_acoes.app_user(id);


--
-- Name: SCHEMA jogo_acoes; Type: ACL; Schema: -; Owner: jogo_acoes_admin
--

GRANT USAGE ON SCHEMA jogo_acoes TO jogo_acoes_app;


--
-- Name: TABLE app_user; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.app_user TO jogo_acoes_app;


--
-- Name: SEQUENCE app_user_id_seq; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,USAGE ON SEQUENCE jogo_acoes.app_user_id_seq TO jogo_acoes_app;


--
-- Name: TABLE competition; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.competition TO jogo_acoes_app;


--
-- Name: SEQUENCE competition_id_seq; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,USAGE ON SEQUENCE jogo_acoes.competition_id_seq TO jogo_acoes_app;


--
-- Name: TABLE jogo_acoes_schema_history; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.jogo_acoes_schema_history TO jogo_acoes_app;


--
-- Name: TABLE link_record; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.link_record TO jogo_acoes_app;


--
-- Name: TABLE log; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT ON TABLE jogo_acoes.log TO jogo_acoes_app;


--
-- Name: SEQUENCE log_id_seq; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,USAGE ON SEQUENCE jogo_acoes.log_id_seq TO jogo_acoes_app;


--
-- Name: SEQUENCE login_link_id_seq; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,USAGE ON SEQUENCE jogo_acoes.login_link_id_seq TO jogo_acoes_app;


--
-- Name: TABLE login_session; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.login_session TO jogo_acoes_app;


--
-- Name: SEQUENCE login_session_id_seq; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,USAGE ON SEQUENCE jogo_acoes.login_session_id_seq TO jogo_acoes_app;


--
-- Name: TABLE participation; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.participation TO jogo_acoes_app;


--
-- Name: SEQUENCE participation_id_seq; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,USAGE ON SEQUENCE jogo_acoes.participation_id_seq TO jogo_acoes_app;


--
-- Name: TABLE role; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.role TO jogo_acoes_app;


--
-- Name: SEQUENCE role_id_seq; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,USAGE ON SEQUENCE jogo_acoes.role_id_seq TO jogo_acoes_app;


--
-- Name: TABLE sent_email; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT ON TABLE jogo_acoes.sent_email TO jogo_acoes_app;


--
-- Name: SEQUENCE sent_email_id_seq; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,USAGE ON SEQUENCE jogo_acoes.sent_email_id_seq TO jogo_acoes_app;


--
-- Name: TABLE spring_session; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.spring_session TO jogo_acoes_app;


--
-- Name: TABLE spring_session_attributes; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.spring_session_attributes TO jogo_acoes_app;


--
-- Name: TABLE user_role; Type: ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE jogo_acoes.user_role TO jogo_acoes_app;


--
-- Name: DEFAULT PRIVILEGES FOR SEQUENCES; Type: DEFAULT ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER DEFAULT PRIVILEGES FOR ROLE jogo_acoes_admin IN SCHEMA jogo_acoes GRANT SELECT,USAGE ON SEQUENCES TO jogo_acoes_app;


--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: jogo_acoes; Owner: jogo_acoes_admin
--

ALTER DEFAULT PRIVILEGES FOR ROLE jogo_acoes_admin IN SCHEMA jogo_acoes GRANT SELECT,INSERT,DELETE,UPDATE ON TABLES TO jogo_acoes_app;


--
-- PostgreSQL database dump complete
--

\unrestrict eQY7gLTEXFLXrFpmcm5OrvJ0UZXmonFqTdCLgjNJYEEbv0Agvl10grEqnJC4S2i

