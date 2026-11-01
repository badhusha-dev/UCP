--
-- PostgreSQL database dump
--

\restrict T0uG6cCawurKcdSpKCTVCFgTmyIjviS9B9BYW6oB1pbDSdKkRYlICokjRPeBJzt

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
\.


--
-- Data for Name: orders; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.orders (id, created_at, status, total_amount, user_id) FROM stdin;
1	2026-10-19 22:13:59.602287	PLACED	199.98	1
\.


--
-- Name: order_items_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.order_items_id_seq', 1, true);


--
-- Name: orders_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.orders_id_seq', 1, true);


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

\unrestrict T0uG6cCawurKcdSpKCTVCFgTmyIjviS9B9BYW6oB1pbDSdKkRYlICokjRPeBJzt

