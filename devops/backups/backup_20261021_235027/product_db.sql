--
-- PostgreSQL database dump
--

\restrict PeHmLdXDiX4ZTvA4mNIOAGf8pxbrpjzafvANWf9CAPegu3Gju3Sj2ebIEW2mjAC

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
-- Name: product; Type: TABLE; Schema: public; Owner: ecommerce_admin
--

CREATE TABLE public.product (
    id bigint NOT NULL,
    description character varying(255),
    name character varying(255),
    price double precision,
    stock_quantity integer
);


ALTER TABLE public.product OWNER TO ecommerce_admin;

--
-- Name: product_id_seq; Type: SEQUENCE; Schema: public; Owner: ecommerce_admin
--

CREATE SEQUENCE public.product_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.product_id_seq OWNER TO ecommerce_admin;

--
-- Name: product_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ecommerce_admin
--

ALTER SEQUENCE public.product_id_seq OWNED BY public.product.id;


--
-- Name: product id; Type: DEFAULT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.product ALTER COLUMN id SET DEFAULT nextval('public.product_id_seq'::regclass);


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	0	<< Flyway Baseline >>	BASELINE	<< Flyway Baseline >>	\N	ecommerce_admin	2026-10-19 23:25:56.344054	0	t
2	1	init product schema	SQL	V1__init_product_schema.sql	1800312163	ecommerce_admin	2026-10-19 23:25:56.626223	127	t
\.


--
-- Data for Name: product; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.product (id, description, name, price, stock_quantity) FROM stdin;
3	144Hz IPS display	4K Gaming Monitor	349.99	30
2	RGB backlight with blue switches	Mechanical Keyboard	79.99	42
1	High quality audio with ANC	Wireless Noise-Canceling Headphones	99.99	31
\.


--
-- Name: product_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.product_id_seq', 3, true);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: product product_pkey; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.product
    ADD CONSTRAINT product_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: ecommerce_admin
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- PostgreSQL database dump complete
--

\unrestrict PeHmLdXDiX4ZTvA4mNIOAGf8pxbrpjzafvANWf9CAPegu3Gju3Sj2ebIEW2mjAC

