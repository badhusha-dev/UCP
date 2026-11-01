--
-- PostgreSQL database dump
--

\restrict Wrk6MTbIMT2OmbQBBrKHbfPVTVrCtRGgf1YtUgWsleuUazeBJBeX2NMzTT6gahg

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
-- Name: users; Type: TABLE; Schema: public; Owner: ecommerce_admin
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    active boolean NOT NULL,
    name character varying(255)
);


ALTER TABLE public.users OWNER TO ecommerce_admin;

--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: ecommerce_admin
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.users_id_seq OWNER TO ecommerce_admin;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ecommerce_admin
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	0	<< Flyway Baseline >>	BASELINE	<< Flyway Baseline >>	\N	ecommerce_admin	2026-10-19 23:25:45.716204	0	t
2	1	init user schema	SQL	V1__init_user_schema.sql	-780576046	ecommerce_admin	2026-10-19 23:25:46.419489	110	t
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: ecommerce_admin
--

COPY public.users (id, email, password, role, active, name) FROM stdin;
1	admin	password123	ADMIN	t	Admin
2	admin@test.com	password123	ADMIN	t	Admin
3	admin@example.com	password123	ADMIN	t	Admin
4	admin@ecommerce.com	password123	ADMIN	t	Admin
\.


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ecommerce_admin
--

SELECT pg_catalog.setval('public.users_id_seq', 4, true);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: users uk_6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: ecommerce_admin
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: ecommerce_admin
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- PostgreSQL database dump complete
--

\unrestrict Wrk6MTbIMT2OmbQBBrKHbfPVTVrCtRGgf1YtUgWsleuUazeBJBeX2NMzTT6gahg

