-- Seed initial teams (idempotent). Requires columns: c_slug (unique, not null) and c_tla (char(3), unique, not null)
INSERT INTO public.t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES
    ('8d7d6c2c-3f4a-4a5b-9a7d-1a2b3c4d5e60', 'Arsenal', 'ARS', 'arsenal', 'ARS')
ON CONFLICT (pk_id) DO UPDATE SET
    c_name = EXCLUDED.c_name,
    c_short_name = EXCLUDED.c_short_name,
    c_slug = EXCLUDED.c_slug,
    c_tla = EXCLUDED.c_tla;

INSERT INTO public.t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES
    ('2b1c4e6a-8f7d-4c3b-9a2d-5e6f7a8b9c01', 'Chelsea', 'CHE', 'chelsea', 'CHE')
ON CONFLICT (pk_id) DO UPDATE SET
    c_name = EXCLUDED.c_name,
    c_short_name = EXCLUDED.c_short_name,
    c_slug = EXCLUDED.c_slug,
    c_tla = EXCLUDED.c_tla;

INSERT INTO public.t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES
    ('3a4b5c6d-7e8f-4a9b-8c7d-6e5f4d3c2b10', 'Liverpool', 'LIV', 'liverpool', 'LIV')
ON CONFLICT (pk_id) DO UPDATE SET
    c_name = EXCLUDED.c_name,
    c_short_name = EXCLUDED.c_short_name,
    c_slug = EXCLUDED.c_slug,
    c_tla = EXCLUDED.c_tla;

INSERT INTO public.t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES
    ('4c5d6e7f-8a9b-4c3d-9e1f-2a3b4c5d6e70', 'Manchester City', 'MCI', 'manchester-city', 'MCI')
ON CONFLICT (pk_id) DO UPDATE SET
    c_name = EXCLUDED.c_name,
    c_short_name = EXCLUDED.c_short_name,
    c_slug = EXCLUDED.c_slug,
    c_tla = EXCLUDED.c_tla;

INSERT INTO public.t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES
    ('5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a80', 'Manchester United', 'MUN', 'manchester-united', 'MUN')
ON CONFLICT (pk_id) DO UPDATE SET
    c_name = EXCLUDED.c_name,
    c_short_name = EXCLUDED.c_short_name,
    c_slug = EXCLUDED.c_slug,
    c_tla = EXCLUDED.c_tla;

INSERT INTO public.t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES
    ('6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b90', 'Tottenham Hotspur', 'TOT', 'tottenham-hotspur', 'TOT')
ON CONFLICT (pk_id) DO UPDATE SET
    c_name = EXCLUDED.c_name,
    c_short_name = EXCLUDED.c_short_name,
    c_slug = EXCLUDED.c_slug,
    c_tla = EXCLUDED.c_tla;
