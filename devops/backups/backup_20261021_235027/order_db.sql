--
-- PostgreSQL database dump
--

\restrict aT8jurMDaG57w15Gqm4na80E0ow7oOQMKU2zmirxY4NGQunz5xttqAjW8Nmi5Wc

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
-- Name: order_items; Type: TABLE; Schema: public; Owner: ecommerce_admin
--

CREATE TABLE public.order_items (
    id bigint NOT NULL,
    price numeric(38,2),
    product_id bigint,
    product_name character varying(255),
    quantity integer,
    order_id bigint
);


ALTER TABLE public.order_items OWNER TO ecommerce_admin;

--
-- Name: order_items_id_seq; Type: SEQUENCE; Schema: public; Owner: ecommerce_admin
--

CREATE SEQUENCE public.order_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.order_items_id_seq OWNER TO ecommerce_admin;

--
-- Name: order_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ecommerce_admin
--

ALTER SEQUENCE public.order_items_id_seq OWNED BY public.order_items.id;


--
-- Name: orders; Type: TABLE; Schema: public; Owner: ecommerce_admin
--

CREATE TABLE public.orders (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    status character varying(255),
    total_amount numeric(38,2),
    user_id bigint
);


ALTER TABLE public.orders OWNER TO ecommerce_admin;

--
-- Name: orders_id_seq; Type: SEQUENCE; Schema: public; Owner: ecommerce_admin
--

CREATE SEQUENCE public.orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.orders_id_seq OWNER TO ecommerce_admin;

--
-- Name: orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ecommerce_admin
--

ALTER SEQUENCE public.orders_id_seq OWNED BY public.orders.id;


--
-- Name: order_items id; Type: DEFAULT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.order_items ALTER COLUMN id SET DEFAULT nextval('public.order_items_id_seq'::regclass);


--
-- Name: orders id; Type: DEFAULT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.orders ALTER COLUMN id SET DEFAULT nextval('public.orders_id_seq'::regclass);


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	0	<< Flyway Baseline >>	BASELINE	<< Flyway Baseline >>	\N	ecommerce_admin	2026-10-19 23:25:47.8125	0	t
2	1	init order schema	SQL	V1__init_order_schema.sql	881883630	ecommerce_admin	2026-10-19 23:25:48.340602	86	t
\.


--
-- Data for Name: order_items; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.order_items (id, price, product_id, product_name, quantity, order_id) FROM stdin;
1	99.99	1	Wireless Noise-Canceling Headphones	2	1
2	99.99	1	Wireless Noise-Canceling Headphones	1	2
3	99.99	1	Wireless Noise-Canceling Headphones	1	3
4	79.99	2	Mechanical Keyboard	1	4
5	79.99	2	Mechanical Keyboard	1	5
6	79.99	2	Mechanical Keyboard	1	6
7	79.99	2	Mechanical Keyboard	1	7
8	79.99	2	Mechanical Keyboard	1	8
9	79.99	2	Mechanical Keyboard	1	9
10	79.99	2	Mechanical Keyboard	1	10
11	99.99	1	Wireless Headphones	1	11
12	79.99	2	Mechanical Keyboard	1	12
13	99.99	1	Wireless Noise-Canceling Headphones	1	13
14	99.99	1	Wireless Noise-Canceling Headphones	1	14
15	99.99	1	Wireless Headphones	1	15
16	99.99	1	Wireless Headphones	1	16
17	99.99	1	Wireless Headphones	1	17
18	99.99	1	Wireless Headphones	1	18
19	99.99	1	Wireless Headphones	1	19
20	99.99	1	Wireless Headphones	1	20
21	99.99	1	Wireless Headphones	1	21
22	99.99	1	Wireless Headphones	1	22
23	99.99	1	Wireless Headphones	1	23
24	99.99	1	Wireless Headphones	1	24
25	99.99	1	Wireless Headphones	1	25
26	99.99	1	Wireless Headphones	1	26
27	99.99	1	Wireless Headphones	1	27
28	99.99	1	Wireless Headphones	1	28
29	99.99	1	Wireless Headphones	1	29
30	99.99	1	Wireless Headphones	1	30
31	99.99	1	Wireless Headphones	1	31
32	99.99	1	Wireless Headphones	1	32
33	99.99	1	Wireless Headphones	1	33
34	99.99	1	Wireless Headphones	1	34
35	99.99	1	Wireless Noise-Canceling Headphones	1	35
36	99.99	1	Wireless Headphones	1	36
37	99.99	1	Wireless Headphones	1	37
38	99.99	1	Wireless Headphones	1	38
39	99.99	1	Wireless Headphones	1	39
40	99.99	1	Wireless Headphones	1	40
41	99.99	1	Wireless Headphones	1	41
42	99.99	1	Wireless Headphones	1	42
43	99.99	1	Wireless Headphones	1	43
44	99.99	1	Wireless Headphones	1	44
45	99.99	1	Wireless Headphones	1	45
46	99.99	1	Wireless Headphones	1	46
47	99.99	1	Wireless Headphones	1	47
48	99.99	1	Wireless Headphones	1	48
49	99.99	1	Wireless Headphones	1	49
50	99.99	1	Wireless Headphones	1	50
51	99.99	1	Wireless Headphones	1	51
52	99.99	1	Wireless Headphones	1	52
53	99.99	1	Wireless Headphones	1	53
54	99.99	1	Wireless Headphones	1	54
55	99.99	1	Wireless Headphones	1	55
56	99.99	1	Wireless Noise-Canceling Headphones	1	56
57	99.99	1	Wireless Headphones	1	57
58	99.99	1	Wireless Headphones	1	58
59	99.99	1	Wireless Headphones	1	59
60	99.99	1	Wireless Headphones	1	60
61	99.99	1	Wireless Headphones	1	61
62	99.99	1	Wireless Headphones	1	62
63	99.99	1	Wireless Headphones	1	63
64	99.99	1	Wireless Headphones	1	64
65	99.99	1	Wireless Headphones	1	65
66	99.99	1	Wireless Headphones	1	66
67	99.99	1	Wireless Headphones	1	67
68	99.99	1	Wireless Headphones	1	68
69	99.99	1	Wireless Headphones	1	69
70	99.99	1	Wireless Headphones	1	70
71	99.99	1	Wireless Headphones	1	71
72	99.99	1	Wireless Headphones	1	72
73	99.99	1	Wireless Headphones	1	73
74	99.99	1	Wireless Headphones	1	74
75	99.99	1	Wireless Headphones	1	75
76	99.99	1	Wireless Headphones	1	76
\.


--
-- Data for Name: orders; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.orders (id, created_at, status, total_amount, user_id) FROM stdin;
1	2026-10-19 22:13:59.602287	PLACED	199.98	1
2	2026-10-20 14:34:54.311394	PLACED	99.99	6
3	2026-10-20 14:39:03.165736	CONFIRMED	99.99	6
4	2026-10-20 14:41:37.96814	CONFIRMED	79.99	6
5	2026-10-20 14:48:22.888193	CONFIRMED	79.99	6
6	2026-10-20 14:51:41.610623	CONFIRMED	79.99	6
7	2026-10-21 06:36:19.52428	PLACED	79.99	6
8	2026-10-21 06:41:16.334629	PLACED	79.99	6
9	2026-10-21 06:41:36.276645	CONFIRMED	79.99	6
10	2026-10-21 15:45:35.496176	CONFIRMED	79.99	6
11	2026-10-21 17:29:44.366789	PLACED	99.99	4
12	2026-10-21 17:35:46.447049	PLACED	79.99	4
13	2026-10-21 17:38:22.373581	PLACED	99.99	4
14	2026-10-21 17:39:13.411586	PLACED	99.99	4
15	2026-10-21 17:39:17.750405	PLACED	99.99	4
16	2026-10-21 17:39:17.862318	PLACED	99.99	4
17	2026-10-21 17:39:17.984696	PLACED	99.99	4
18	2026-10-21 17:39:18.113963	PLACED	99.99	4
19	2026-10-21 17:39:18.234351	PLACED	99.99	4
20	2026-10-21 17:39:18.374752	PLACED	99.99	4
21	2026-10-21 17:39:18.497827	PLACED	99.99	4
22	2026-10-21 17:39:18.605899	PLACED	99.99	4
23	2026-10-21 17:39:18.705904	PLACED	99.99	4
24	2026-10-21 17:39:18.810073	PLACED	99.99	4
25	2026-10-21 17:39:18.918012	PLACED	99.99	4
26	2026-10-21 17:39:19.030064	PLACED	99.99	4
27	2026-10-21 17:39:19.139046	PLACED	99.99	4
28	2026-10-21 17:39:19.246223	PLACED	99.99	4
29	2026-10-21 17:39:19.358346	PLACED	99.99	4
30	2026-10-21 17:39:19.469938	PLACED	99.99	4
31	2026-10-21 17:39:19.573824	PLACED	99.99	4
32	2026-10-21 17:39:19.677849	PLACED	99.99	4
33	2026-10-21 17:39:19.785623	PLACED	99.99	4
34	2026-10-21 17:39:19.897944	PLACED	99.99	4
35	2026-10-21 17:40:16.368907	PLACED	99.99	4
36	2026-10-21 17:40:21.932432	PLACED	99.99	4
37	2026-10-21 17:40:22.068317	PLACED	99.99	4
38	2026-10-21 17:40:22.174955	PLACED	99.99	4
39	2026-10-21 17:40:22.308126	PLACED	99.99	4
40	2026-10-21 17:40:22.423608	PLACED	99.99	4
41	2026-10-21 17:40:22.551566	PLACED	99.99	4
42	2026-10-21 17:40:22.65923	PLACED	99.99	4
43	2026-10-21 17:40:22.779347	PLACED	99.99	4
44	2026-10-21 17:40:22.88688	PLACED	99.99	4
45	2026-10-21 17:40:22.991126	PLACED	99.99	4
46	2026-10-21 17:40:23.099413	PLACED	99.99	4
47	2026-10-21 17:40:23.203527	PLACED	99.99	4
48	2026-10-21 17:40:23.31498	PLACED	99.99	4
49	2026-10-21 17:40:23.411459	PLACED	99.99	4
50	2026-10-21 17:40:23.507065	PLACED	99.99	4
51	2026-10-21 17:40:23.629122	PLACED	99.99	4
52	2026-10-21 17:40:23.771949	PLACED	99.99	4
53	2026-10-21 17:40:23.915459	PLACED	99.99	4
54	2026-10-21 17:40:24.031512	PLACED	99.99	4
55	2026-10-21 17:40:24.147123	PLACED	99.99	4
56	2026-10-21 17:42:23.958311	PLACED	99.99	4
57	2026-10-21 17:42:29.423063	PLACED	99.99	4
58	2026-10-21 17:42:29.526329	PLACED	99.99	4
59	2026-10-21 17:42:29.638474	PLACED	99.99	4
60	2026-10-21 17:42:29.750188	PLACED	99.99	4
61	2026-10-21 17:42:29.854094	PLACED	99.99	4
62	2026-10-21 17:42:29.966196	PLACED	99.99	4
63	2026-10-21 17:42:30.074419	PLACED	99.99	4
64	2026-10-21 17:42:30.18237	PLACED	99.99	4
65	2026-10-21 17:42:30.302652	PLACED	99.99	4
66	2026-10-21 17:42:30.435351	PLACED	99.99	4
67	2026-10-21 17:42:30.562441	PLACED	99.99	4
68	2026-10-21 17:42:30.678616	PLACED	99.99	4
69	2026-10-21 17:42:30.798058	PLACED	99.99	4
70	2026-10-21 17:42:30.902073	PLACED	99.99	4
71	2026-10-21 17:42:30.9979	PLACED	99.99	4
72	2026-10-21 17:42:31.09041	PLACED	99.99	4
73	2026-10-21 17:42:31.194141	PLACED	99.99	4
74	2026-10-21 17:42:31.298088	PLACED	99.99	4
75	2026-10-21 17:42:31.411009	PLACED	99.99	4
76	2026-10-21 17:42:31.555178	PLACED	99.99	4
\.


--
-- Name: order_items_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.order_items_id_seq', 76, true);


--
-- Name: orders_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.orders_id_seq', 76, true);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: ecommerce_admin
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_order_items_order_id; Type: INDEX; Schema: public; Owner: ecommerce_admin
--

CREATE INDEX idx_order_items_order_id ON public.order_items USING btree (order_id);


--
-- Name: idx_orders_user_id; Type: INDEX; Schema: public; Owner: ecommerce_admin
--

CREATE INDEX idx_orders_user_id ON public.orders USING btree (user_id);


--
-- Name: order_items fkbioxgbv59vetrxe0ejfubep1w; Type: FK CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- PostgreSQL database dump complete
--

\unrestrict aT8jurMDaG57w15Gqm4na80E0ow7oOQMKU2zmirxY4NGQunz5xttqAjW8Nmi5Wc

