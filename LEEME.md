# Asistente de voz para Android

App propia: se activa por nombre, escucha tu orden, pregunta a un modelo de IA por internet y responde en voz alta.
Guarda pendientes y recordatorios, pone alarmas y temporizadores, busca en internet y lee tus notificaciones.

## Compilar el APK con GitHub (sin instalar nada)

1. Crea una cuenta en https://github.com y un repositorio nuevo (boton "New"). Ponle un nombre, por ejemplo `asistente`, dejalo en "Public" y crealo vacio.
2. En la pagina del repositorio vacio, entra a "uploading an existing file".
3. Arrastra TODO el contenido de esta carpeta (incluida la carpeta `.github`) y pulsa "Commit changes".
4. Abre la pestana "Actions": veras "Compilar APK" trabajando. Tarda unos 5 minutos.
5. Cuando quede en verde, entra a "Releases" (columna derecha de la pagina principal del repositorio) y descarga `app-debug.apk` desde tu telefono.
6. Instalalo. Android pedira permitir "instalar apps desconocidas" para tu navegador.

Si la compilacion queda en rojo, abre el paso "Compilar", copia las lineas que digan `error:` y pasalas para corregirlas.

## Primer uso

1. Acepta los permisos de microfono y notificaciones.
2. En Ajustes pega tu clave. Groq (https://console.groq.com) es gratis y no pide tarjeta.
3. La primera vez descarga el modelo de voz que reconoce el nombre (unos 40 MB, una sola vez).
4. Di el nombre (por defecto "Luna"), espera el pitido y habla.

## Permisos opcionales (botones dentro de Ajustes)

- Leer notificaciones: para preguntar "tengo mensajes?".
- Mostrar sobre otras apps: para crear alarmas con la app cerrada.
- Bateria sin restricciones: para que Android no apague la escucha.
