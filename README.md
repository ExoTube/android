# ExoTube

App Android gratuita y de código abierto para descargar videos y música desde YouTube, TikTok,
Instagram, X y Facebook. Sin anuncios y sin registro.

Se usa desde el botón **Compartir** de cualquier app: eliges la calidad y el archivo queda en tu
teléfono. Incluye un reproductor con playlists.

Página de descarga: https://exotube.github.io

## Funciones

- Recibe enlaces compartidos desde otras apps y muestra las calidades disponibles.
- Descarga video (hasta 4K) o solo el audio en MP3.
- Descarga en segundo plano, con progreso en la notificación y opción de cancelar.
- Guarda los archivos en `Movies/ExoTube` y `Music/ExoTube`, visibles en la galería y en cualquier
  reproductor de música.
- Reproductor propio con controles en la notificación y en la pantalla de bloqueo.
- Playlists con foto propia, o con la carátula de una de sus canciones.
- Historial de descargas anónimo (opcional).

## Cómo está construido

| Capa | Tecnología |
|---|---|
| Interfaz | Jetpack Compose y Material 3 |
| Arquitectura | MVVM, con StateFlow y corrutinas |
| Extracción y descarga | yt-dlp mediante [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) |
| Trabajo en segundo plano | WorkManager con Foreground Service |
| Reproductor | Media3 (ExoPlayer y MediaSession) |
| Base de datos local | Room (playlists) |
| Historial en la nube | Supabase, con autenticación anónima |

El código sigue una separación por capas:

```
domain/   Modelos y contratos (Kotlin puro, sin Android: fácil de testear)
data/     Implementaciones: yt-dlp, MediaStore, Room, Supabase
download/ Worker de descarga, notificaciones y guardado en la galería
player/   Servicio de reproducción y su interfaz
library/  Pantalla de biblioteca
playlist/ Pantallas de playlists
ui/       Tema, componentes compartidos y navegación
```

## Compilarlo

Necesitas Android Studio (versión reciente) y un dispositivo o emulador con Android 8.0 o superior.

1. Clona el repositorio y ábrelo en Android Studio.
2. Sincroniza el proyecto con Gradle y ejecuta la app. **Ya funciona así**: sin configurar nada,
   descarga y reproduce; lo único que no hará es guardar el historial en la nube.

Para ejecutar los tests:

```bash
./gradlew testDebugUnitTest
```

### Historial en la nube (opcional)

Si quieres el historial, crea un proyecto en [Supabase](https://supabase.com), ejecuta el script de
`supabase/migrations/` y añade tus claves a `local.properties` (ese archivo nunca se sube a Git):

```properties
supabase.url=https://xxxx.supabase.co
supabase.key=sb_publishable_xxxx
```

Usa siempre la clave **publicable**, nunca la `service_role`. Lo que protege los datos son las
políticas de seguridad (RLS) del script, no el secreto de la clave.

### Firmar tu propia versión

Los APK publicados están firmados con una clave privada que no forma parte del repositorio. Para
generar los tuyos, crea tu clave y un archivo `keystore.properties` en la raíz:

```properties
storeFile=mi-clave.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Después, `./gradlew assembleRelease` genera un APK por arquitectura en
`app/build/outputs/apk/release/`.

## Por qué no está en Google Play

Las normas de Google Play no permiten aplicaciones que descarguen contenido de plataformas como
YouTube. Por eso se distribuye desde su propia página.

## Licencia

GPL-3.0. Esta licencia no es opcional: el motor de descarga (yt-dlp y youtubedl-android) es GPL, y
cualquier programa que lo incluya debe publicarse con la misma licencia. Es decir, puedes usar,
estudiar y modificar este código, pero si distribuyes tu versión, también tienes que publicar su
código fuente.

## Aviso

ExoTube es una herramienta. Respeta los derechos de autor y los términos de servicio de cada
plataforma: descarga solo contenido propio, de dominio público o cuyo autor permita la descarga.
Este proyecto no está afiliado a YouTube, TikTok, Instagram, X ni Facebook.
