--
-- PostgreSQL database dump
--

\restrict h8jgdXldEBBbudTPOgjdlCL91PoV14ih6QJw44VvFcXVGv2IJs77142kKadX6Z8

-- Dumped from database version 15.18
-- Dumped by pg_dump version 15.18

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

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: dead_letter_events; Type: TABLE; Schema: public; Owner: ecommerce_admin
--

CREATE TABLE public.dead_letter_events (
    id bigint NOT NULL,
    topic character varying(255) NOT NULL,
    partition_id integer,
    offset_number bigint,
    payload text NOT NULL,
    exception_message text,
    status character varying(50) DEFAULT 'FAILED'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.dead_letter_events OWNER TO ecommerce_admin;

--
-- Name: dead_letter_events_id_seq; Type: SEQUENCE; Schema: public; Owner: ecommerce_admin
--

CREATE SEQUENCE public.dead_letter_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.dead_letter_events_id_seq OWNER TO ecommerce_admin;

--
-- Name: dead_letter_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ecommerce_admin
--

ALTER SEQUENCE public.dead_letter_events_id_seq OWNED BY public.dead_letter_events.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: ecommerce_admin
--

CREATE TABLE public.flyway_schema_history (
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


ALTER TABLE public.flyway_schema_history OWNER TO ecommerce_admin;

--
-- Name: notifications; Type: TABLE; Schema: public; Owner: ecommerce_admin
--

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    is_read boolean NOT NULL,
    message character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    user_id bigint NOT NULL,
    idempotency_key character varying(255)
);


ALTER TABLE public.notifications OWNER TO ecommerce_admin;

--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: ecommerce_admin
--

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.notifications_id_seq OWNER TO ecommerce_admin;

--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ecommerce_admin
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;


--
-- Name: dead_letter_events id; Type: DEFAULT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.dead_letter_events ALTER COLUMN id SET DEFAULT nextval('public.dead_letter_events_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);


--
-- Data for Name: dead_letter_events; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.dead_letter_events (id, topic, partition_id, offset_number, payload, exception_message, status, created_at) FROM stdin;
1	order-events-dlt	0	0	{"corruptedPayload":"missing-orderId-poison-pill"}	Listener failed; Kafka message processing failed, triggering retry: Invalid payload structure: missing orderId	REPLAYED	2026-10-19 23:35:55.06713
2	order-events-dlt	0	1	{"corruptedPayload":"missing-orderId-poison-pill"}	Listener failed; Kafka message processing failed, triggering retry: Invalid payload structure: missing orderId	FAILED	2026-10-19 23:36:06.034109
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	0	<< Flyway Baseline >>	BASELINE	<< Flyway Baseline >>	\N	ecommerce_admin	2026-10-19 23:25:53.943484	0	t
2	1	init notification schema	SQL	V1__init_notification_schema.sql	1895853327	ecommerce_admin	2026-10-19 23:25:54.447392	61	t
3	2	add idempotency and dlq	SQL	V2__add_idempotency_and_dlq.sql	-1649477064	ecommerce_admin	2026-10-19 23:25:54.624315	122	t
\.


--
-- Data for Name: notifications; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.notifications (id, created_at, is_read, message, type, user_id, idempotency_key) FROM stdin;
1	2026-10-19 23:35:00.269775	f	Your order #901 has been placed successfully. Total: $150.00	ORDER_CREATED	1	ORDER-901-ORDER_CREATED
\.


--
-- Name: dead_letter_events_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.dead_letter_events_id_seq', 2, true);


--
-- Name: notifications_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.notifications_id_seq', 1, true);


--
-- Name: dead_letter_events dead_letter_events_pkey; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.dead_letter_events
    ADD CONSTRAINT dead_letter_events_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: ecommerce_admin
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_dead_letter_status; Type: INDEX; Schema: public; Owner: ecommerce_admin
--

CREATE INDEX idx_dead_letter_status ON public.dead_letter_events USING btree (status);


--
-- Name: idx_notifications_idempotency_key; Type: INDEX; Schema: public; Owner: ecommerce_admin
--

CREATE UNIQUE INDEX idx_notifications_idempotency_key ON public.notifications USING btree (idempotency_key);


--
-- Name: idx_notifications_user_id; Type: INDEX; Schema: public; Owner: ecommerce_admin
--

CREATE INDEX idx_notifications_user_id ON public.notifications USING btree (user_id);


--
-- PostgreSQL database dump complete
--

\unrestrict h8jgdXldEBBbudTPOgjdlCL91PoV14ih6QJw44VvFcXVGv2IJs77142kKadX6Z8

