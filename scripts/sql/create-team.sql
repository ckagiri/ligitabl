-- Recreate t_team table with desired schema
DROP TABLE IF EXISTS public.t_team CASCADE;

CREATE TABLE public.t_team (
    pk_id uuid PRIMARY KEY,
    c_name varchar(255) NOT NULL,
    c_short_name varchar(100) NOT NULL
);
