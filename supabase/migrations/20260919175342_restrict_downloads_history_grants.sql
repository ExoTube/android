-- Supabase concede por defecto TODOS los privilegios de las tablas nuevas a anon y authenticated.
-- Un GRANT solo suma permisos, así que primero se revocan todos y luego se concede lo mínimo.
-- (TRUNCATE ignora RLS: por eso importa quitarlo aunque la API REST no lo exponga.)
revoke all on public.downloads_history from anon, authenticated;
grant select, insert, delete on public.downloads_history to authenticated;
