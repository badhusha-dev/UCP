--
-- PostgreSQL database dump
--

\restrict xiMjdGmjLcqdJe2zio4oC9b1YhLgM5AGaYedws54Ke6NQAdkbm9u3mQHyrrFgLH

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
2	2026-10-20 14:35:14.174643	f	Your order #2 has been placed successfully. Total: $99.99	ORDER_CREATED	6	ORDER-2-ORDER_CREATED
3	2026-10-20 14:39:05.458896	f	Your order #3 has been placed successfully. Total: $99.99	ORDER_CREATED	6	ORDER-3-ORDER_CREATED
4	2026-10-20 14:39:56.091982	f	The status of your order #3 has been updated to CONFIRMED	ORDER_STATUS_UPDATED	6	ORDER-3-ORDER_STATUS_UPDATED
5	2026-10-20 14:41:38.027255	f	Your order #4 has been placed successfully. Total: $79.99	ORDER_CREATED	6	ORDER-4-ORDER_CREATED
6	2026-10-20 14:41:38.144127	f	The status of your order #4 has been updated to CONFIRMED	ORDER_STATUS_UPDATED	6	ORDER-4-ORDER_STATUS_UPDATED
7	2026-10-20 14:48:23.259354	f	Your order #5 has been placed successfully. Total: $79.99	ORDER_CREATED	6	ORDER-5-ORDER_CREATED
8	2026-10-20 14:48:23.320193	f	The status of your order #5 has been updated to CONFIRMED	ORDER_STATUS_UPDATED	6	ORDER-5-ORDER_STATUS_UPDATED
9	2026-10-20 14:51:41.811484	f	Your order #6 has been placed successfully. Total: $79.99	ORDER_CREATED	6	ORDER-6-ORDER_CREATED
10	2026-10-20 14:51:41.872155	f	The status of your order #6 has been updated to CONFIRMED	ORDER_STATUS_UPDATED	6	ORDER-6-ORDER_STATUS_UPDATED
11	2026-10-21 06:36:23.703887	f	Your order #7 has been placed successfully. Total: $79.99	ORDER_CREATED	6	ORDER-7-ORDER_CREATED
12	2026-10-21 06:41:16.415613	f	Your order #8 has been placed successfully. Total: $79.99	ORDER_CREATED	6	ORDER-8-ORDER_CREATED
13	2026-10-21 06:41:36.308027	f	Your order #9 has been placed successfully. Total: $79.99	ORDER_CREATED	6	ORDER-9-ORDER_CREATED
14	2026-10-21 06:41:36.44538	f	The status of your order #9 has been updated to CONFIRMED	ORDER_STATUS_UPDATED	6	ORDER-9-ORDER_STATUS_UPDATED
15	2026-10-21 15:45:38.244707	f	Your order #10 has been placed successfully. Total: $79.99	ORDER_CREATED	6	ORDER-10-ORDER_CREATED
16	2026-10-21 15:45:38.424763	f	The status of your order #10 has been updated to CONFIRMED	ORDER_STATUS_UPDATED	6	ORDER-10-ORDER_STATUS_UPDATED
17	2026-10-21 17:29:44.591251	f	Your order #11 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-11-ORDER_CREATED
18	2026-10-21 17:35:48.592813	f	Your order #12 has been placed successfully. Total: $79.99	ORDER_CREATED	4	ORDER-12-ORDER_CREATED
19	2026-10-21 17:38:22.418313	f	Your order #13 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-13-ORDER_CREATED
20	2026-10-21 17:39:13.451968	f	Your order #14 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-14-ORDER_CREATED
21	2026-10-21 17:39:17.775054	f	Your order #15 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-15-ORDER_CREATED
22	2026-10-21 17:39:17.898844	f	Your order #16 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-16-ORDER_CREATED
23	2026-10-21 17:39:18.024677	f	Your order #17 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-17-ORDER_CREATED
24	2026-10-21 17:39:18.154321	f	Your order #18 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-18-ORDER_CREATED
25	2026-10-21 17:39:18.277741	f	Your order #19 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-19-ORDER_CREATED
26	2026-10-21 17:39:18.418925	f	Your order #20 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-20-ORDER_CREATED
27	2026-10-21 17:39:18.526001	f	Your order #21 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-21-ORDER_CREATED
28	2026-10-21 17:39:18.633769	f	Your order #22 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-22-ORDER_CREATED
29	2026-10-21 17:39:18.730968	f	Your order #23 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-23-ORDER_CREATED
30	2026-10-21 17:39:18.842069	f	Your order #24 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-24-ORDER_CREATED
31	2026-10-21 17:39:18.95331	f	Your order #25 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-25-ORDER_CREATED
32	2026-10-21 17:39:19.061629	f	Your order #26 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-26-ORDER_CREATED
33	2026-10-21 17:39:19.171208	f	Your order #27 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-27-ORDER_CREATED
34	2026-10-21 17:39:19.272682	f	Your order #28 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-28-ORDER_CREATED
35	2026-10-21 17:39:19.392925	f	Your order #29 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-29-ORDER_CREATED
36	2026-10-21 17:39:19.496498	f	Your order #30 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-30-ORDER_CREATED
37	2026-10-21 17:39:19.600258	f	Your order #31 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-31-ORDER_CREATED
38	2026-10-21 17:39:19.710866	f	Your order #32 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-32-ORDER_CREATED
39	2026-10-21 17:39:19.814179	f	Your order #33 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-33-ORDER_CREATED
40	2026-10-21 17:39:19.925908	f	Your order #34 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-34-ORDER_CREATED
41	2026-10-21 17:40:16.406986	f	Your order #35 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-35-ORDER_CREATED
42	2026-10-21 17:40:21.969655	f	Your order #36 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-36-ORDER_CREATED
43	2026-10-21 17:40:22.09599	f	Your order #37 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-37-ORDER_CREATED
44	2026-10-21 17:40:22.208649	f	Your order #38 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-38-ORDER_CREATED
45	2026-10-21 17:40:22.344706	f	Your order #39 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-39-ORDER_CREATED
46	2026-10-21 17:40:22.457059	f	Your order #40 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-40-ORDER_CREATED
47	2026-10-21 17:40:22.588931	f	Your order #41 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-41-ORDER_CREATED
48	2026-10-21 17:40:22.686326	f	Your order #42 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-42-ORDER_CREATED
49	2026-10-21 17:40:22.815523	f	Your order #43 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-43-ORDER_CREATED
50	2026-10-21 17:40:22.910672	f	Your order #44 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-44-ORDER_CREATED
51	2026-10-21 17:40:23.021821	f	Your order #45 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-45-ORDER_CREATED
52	2026-10-21 17:40:23.126783	f	Your order #46 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-46-ORDER_CREATED
53	2026-10-21 17:40:23.234838	f	Your order #47 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-47-ORDER_CREATED
54	2026-10-21 17:40:23.337584	f	Your order #48 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-48-ORDER_CREATED
55	2026-10-21 17:40:23.433052	f	Your order #49 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-49-ORDER_CREATED
56	2026-10-21 17:40:23.530635	f	Your order #50 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-50-ORDER_CREATED
57	2026-10-21 17:40:23.691246	f	Your order #51 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-51-ORDER_CREATED
58	2026-10-21 17:40:23.828401	f	Your order #52 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-52-ORDER_CREATED
59	2026-10-21 17:40:23.948891	f	Your order #53 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-53-ORDER_CREATED
60	2026-10-21 17:40:24.064938	f	Your order #54 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-54-ORDER_CREATED
61	2026-10-21 17:40:24.182261	f	Your order #55 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-55-ORDER_CREATED
62	2026-10-21 17:42:24.009122	f	Your order #56 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-56-ORDER_CREATED
63	2026-10-21 17:42:29.449894	f	Your order #57 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-57-ORDER_CREATED
64	2026-10-21 17:42:29.557182	f	Your order #58 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-58-ORDER_CREATED
65	2026-10-21 17:42:29.674004	f	Your order #59 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-59-ORDER_CREATED
66	2026-10-21 17:42:29.779354	f	Your order #60 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-60-ORDER_CREATED
67	2026-10-21 17:42:29.885967	f	Your order #61 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-61-ORDER_CREATED
68	2026-10-21 17:42:29.995814	f	Your order #62 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-62-ORDER_CREATED
69	2026-10-21 17:42:30.108043	f	Your order #63 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-63-ORDER_CREATED
70	2026-10-21 17:42:30.225048	f	Your order #64 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-64-ORDER_CREATED
71	2026-10-21 17:42:30.339964	f	Your order #65 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-65-ORDER_CREATED
72	2026-10-21 17:42:30.483112	f	Your order #66 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-66-ORDER_CREATED
73	2026-10-21 17:42:30.596114	f	Your order #67 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-67-ORDER_CREATED
74	2026-10-21 17:42:30.723557	f	Your order #68 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-68-ORDER_CREATED
75	2026-10-21 17:42:30.829776	f	Your order #69 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-69-ORDER_CREATED
76	2026-10-21 17:42:30.92716	f	Your order #70 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-70-ORDER_CREATED
77	2026-10-21 17:42:31.02119	f	Your order #71 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-71-ORDER_CREATED
78	2026-10-21 17:42:31.117929	f	Your order #72 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-72-ORDER_CREATED
79	2026-10-21 17:42:31.221252	f	Your order #73 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-73-ORDER_CREATED
80	2026-10-21 17:42:31.326204	f	Your order #74 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-74-ORDER_CREATED
81	2026-10-21 17:42:31.467947	f	Your order #75 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-75-ORDER_CREATED
82	2026-10-21 17:42:31.611695	f	Your order #76 has been placed successfully. Total: $99.99	ORDER_CREATED	4	ORDER-76-ORDER_CREATED
\.


--
-- Name: dead_letter_events_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.dead_letter_events_id_seq', 2, true);


--
-- Name: notifications_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.notifications_id_seq', 82, true);


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

\unrestrict xiMjdGmjLcqdJe2zio4oC9b1YhLgM5AGaYedws54Ke6NQAdkbm9u3mQHyrrFgLH

