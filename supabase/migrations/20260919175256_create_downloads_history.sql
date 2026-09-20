-- ExoTube · historial anónimo de descargas
-- Ejecútalo en Supabase → SQL Editor, o con la CLI: `supabase db push`.
-- Requisito: Authentication → Sign In / Providers → "Allow anonymous sign-ins" activado.

create table if not exists public.downloads_history (
    id              bigint generated always as identity primary key,

    -- Dueño de la fila. Postgres lo rellena con el usuario (anónimo) que hace la petición;
    -- la app nunca lo envía, así que no puede escribir en el historial de otro.
    user_id         uuid not null default auth.uid()
                    references auth.users (id) on delete cascade,

    original_url    text not null check (char_length(original_url) between 1 and 2048),
    platform        text not null
                    check (platform in ('youtube', 'tiktok', 'instagram', 'x', 'facebook', 'unknown')),
    title           text check (char_length(title) <= 500),
    format          text not null check (char_length(format) between 1 and 32), -- "1080p", "MP3"…
    media_type      text not null check (media_type in ('video', 'audio')),
    file_size_bytes bigint not null check (file_size_bytes >= 0),
    created_at      timestamptz not null default now()
);

comment on table public.downloads_history is
    'Descargas completadas en ExoTube, por usuario anónimo (Supabase Anonymous Sign-Ins).';

-- Consulta típica: "mis descargas, de la más reciente a la más antigua".
create index if not exists downloads_history_user_created_idx
    on public.downloads_history (user_id, created_at desc);

-- Row Level Security: con RLS activado y sin políticas, nadie puede leer ni escribir,
-- ni siquiera con la clave pública que va dentro del APK. Las políticas abren solo lo necesario.
alter table public.downloads_history enable row level security;

-- (select auth.uid()) en vez de auth.uid(): Postgres lo evalúa una vez por consulta, no por fila.
drop policy if exists "Leer mi historial" on public.downloads_history;
create policy "Leer mi historial"
    on public.downloads_history for select
    to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists "Añadir a mi historial" on public.downloads_history;
create policy "Añadir a mi historial"
    on public.downloads_history for insert
    to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists "Borrar mi historial" on public.downloads_history;
create policy "Borrar mi historial"
    on public.downloads_history for delete
    to authenticated
    using ((select auth.uid()) = user_id);

-- Permisos de tabla (se suman a RLS). Los usuarios anónimos de Supabase usan el rol
-- "authenticated"; "anon" (peticiones sin sesión) no tiene acceso. Sin UPDATE: el historial no se edita.
revoke all on public.downloads_history from anon;
grant select, insert, delete on public.downloads_history to authenticated;
