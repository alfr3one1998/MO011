-- Reviewed schema proposal; apply to the chosen Supabase project before cutover.
-- No customer data, owner identity, or credentials belong in this file.
BEGIN;
CREATE TABLE public.mandoub_workspace (
 id integer PRIMARY KEY CHECK (id=1), owner text NOT NULL, name text NOT NULL
);
CREATE TABLE public.mandoub_drivers (
 id text PRIMARY KEY, name text NOT NULL, email text NOT NULL UNIQUE CHECK(email=lower(email)), phone text NOT NULL
);
CREATE TABLE public.mandoub_orders (
 id text PRIMARY KEY, customer text NOT NULL, phone text NOT NULL,
 address text NOT NULL, district text NOT NULL,
 amount integer NOT NULL CHECK(amount BETWEEN 0 AND 100000000),
 fee integer NOT NULL CHECK(fee BETWEEN 0 AND 100000000),
 payment text NOT NULL CHECK(payment IN ('cash','paid')),
 "driverId" text REFERENCES public.mandoub_drivers(id) ON DELETE RESTRICT,
 status text NOT NULL CHECK(status IN ('new','received','onway','delivered')),
 notes text NOT NULL DEFAULT '', created timestamptz NOT NULL, delivered timestamptz,
 settled integer NOT NULL DEFAULT 0 CHECK(settled IN (0,1)),
 CHECK(settled=0 OR status='delivered')
);
CREATE INDEX mandoub_orders_driver_id ON public.mandoub_orders("driverId",id);
CREATE INDEX mandoub_orders_pending_settlement ON public.mandoub_orders("driverId") WHERE status='delivered' AND settled=0;
ALTER TABLE public.mandoub_workspace ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mandoub_drivers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mandoub_orders ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.mandoub_workspace,public.mandoub_drivers,public.mandoub_orders FROM PUBLIC,anon,authenticated;
GRANT SELECT,INSERT,UPDATE ON public.mandoub_workspace,public.mandoub_drivers,public.mandoub_orders TO service_role;
-- Deny direct browser access. Sites authenticates users, and the server enforces
-- owner/driver authorization before using its secret key. No permissive policies.
COMMIT;
