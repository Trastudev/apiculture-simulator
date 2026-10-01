"use strict";

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function contactLine() {
  const email = (process.env.PRIVACY_CONTACT_EMAIL || "").trim();
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return "Para ejercer tus derechos, escribe al correo de contacto publicado en la ficha de Google Play de ApiSim.";
  }
  const safe = escapeHtml(email);
  return `Para ejercer tus derechos, escribe a <a href="mailto:${safe}">${safe}</a>.`;
}

function page(title, body) {
  return `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(title)}</title>
  <style>
    body { font-family: Georgia, serif; margin: 0; background: #f6f1e4; color: #2b2416; }
    main { max-width: 42rem; margin: 0 auto; padding: 1.5rem 1.25rem 3rem; }
    h1 { font-size: 1.7rem; line-height: 1.2; }
    h2 { font-size: 1.15rem; margin-top: 1.6rem; }
    a { color: #8a4b12; }
    p, li { line-height: 1.5; }
    .card { background: #fffdf8; border-radius: 16px; padding: 1rem 1.1rem; }
  </style>
</head>
<body>
  <main>
    <p><a href="/privacy">Privacidad</a> · <a href="/delete-account">Eliminar cuenta</a></p>
    ${body}
  </main>
</body>
</html>`;
}

function privacyHtml() {
  return page("Política de privacidad de ApiSim", `
    <h1>Política de privacidad de ApiSim</h1>
    <p>Última actualización: 29 de septiembre de 2026.</p>
    <p>ApiSim es un simulador de apicultura. Esta página explica qué datos trata el juego, para qué y cómo puedes borrarlos. El responsable es quien publica la aplicación en Google Play.</p>
    <p>${contactLine()}</p>

    <h2>Cuenta</h2>
    <p>La cuenta se crea al entrar con Google. Recibimos el identificador de la cuenta de Google, el correo, el nombre visible y un token de sesión. Sirven para reconocerte y guardar tu partida. No pedimos una contraseña propia.</p>

    <h2>Datos de juego</h2>
    <p>En el servidor del juego guardamos el perfil (nombre de apicultor y marca de miel), la economía, las colmenas, los terrenos, los contratos, los viajes, las comandas, el correo entre jugadores, el idioma elegido y la posición en el ranking. Esos datos son necesarios para que la partida continúe en este teléfono y en otros.</p>
    <p>El teléfono guarda una copia local de la misma partida. No usamos la ubicación GPS del dispositivo: el mapa muestra colmenares y rutas del juego.</p>

    <h2>Publicidad</h2>
    <p>Google AdMob puede tratar el identificador de publicidad del teléfono, la dirección IP y la interacción con los anuncios. Los anuncios no se piden como publicidad infantil: la app no forma parte del programa de Familias de Google Play. El contenido de los anuncios se limita a la categoría general (G). Puedes limitar la publicidad personalizada en los ajustes de Google del teléfono.</p>

    <h2>Mapas</h2>
    <p>Google Maps dibuja el mapa. La clave de la API viaja con la aplicación para pedir los mosaicos del mapa. Las coordenadas que enviamos son las del juego (colmenares, destinos y rutas), no la posición real del teléfono.</p>

    <h2>Con quién se comparten</h2>
    <ul>
      <li>Google, para iniciar sesión, mostrar mapas y servir anuncios.</li>
      <li>El servidor de ApiSim, para guardar la partida.</li>
    </ul>
    <p>No vendemos tus datos.</p>

    <h2>Cuánto tiempo se conservan</h2>
    <p>Los datos de la cuenta y de la partida se conservan mientras la cuenta exista. Al eliminarla se borran del servidor y, si lo haces desde la app, también del teléfono.</p>

    <h2>Tus derechos</h2>
    <p>Puedes consultar esta información y pedir la eliminación de la cuenta y de los datos asociados. Dentro de la app: Ajustes del panel → Eliminar cuenta y datos. Sin la app: <a href="/delete-account">esta página</a>, entrando con la misma cuenta de Google. La eliminación es inmediata y no se puede deshacer.</p>

    <h2>Audiencia</h2>
    <p>ApiSim es un juego para el público general de 13 años en adelante. No está dirigido a niños menores de 13 años y no está en el programa de Familias de Google Play. No recogemos a sabiendas datos de menores de 13 años.</p>

    <h2>Seguridad</h2>
    <p>En la versión publicada, el tráfico con el servidor del juego va cifrado por HTTPS.</p>
  `);
}

function deleteHtml(clientId) {
  const safeId = escapeHtml(clientId);
  const button = safeId
    ? `<div id="g_id_onload" data-client_id="${safeId}" data-callback="onGoogle" data-auto_prompt="false"></div>
       <div class="g_id_signin" data-type="standard" data-theme="outline" data-text="signin_with" data-shape="pill"></div>
       <script src="https://accounts.google.com/gsi/client" async defer></script>`
    : `<p>Esta página aún no tiene configurado el inicio de sesión de Google.</p>`;
  return page("Eliminar cuenta de ApiSim", `
    <h1>Eliminar cuenta y datos</h1>
    <div class="card">
      <p>Entra con la misma cuenta de Google que usas en ApiSim. Al confirmar, borramos la cuenta y la partida en el servidor: perfil, economía, colmenas, terrenos, contratos, viajes, correo y ranking.</p>
      <p>Si todavía tienes la app instalada, también puedes hacerlo en Ajustes del panel. Esa opción borra además la copia guardada en el teléfono.</p>
      ${button}
      <p id="status" role="status"></p>
    </div>
    <script>
      function onGoogle(response) {
        var status = document.getElementById("status");
        status.textContent = "Eliminando…";
        fetch("/account/delete", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ credential: response && response.credential })
        }).then(function (res) {
          if (res.ok) {
            status.textContent = "Cuenta y datos del servidor eliminados.";
            return;
          }
          status.textContent = "No se pudo eliminar la cuenta. Vuelve a entrar con Google.";
        }).catch(function () {
          status.textContent = "No hay conexión con el servidor.";
        });
      }
    </script>
  `);
}

module.exports = { privacyHtml, deleteHtml, escapeHtml };
