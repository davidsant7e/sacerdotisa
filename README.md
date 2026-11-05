# La Sacerdotisa — corrector que explica

<p align="center"><img src="docs/carta.svg" alt="II · La Sacerdotisa" width="180"></p>

Selecciona un texto y te dice **qué está mal y por qué**:

- **Mayúsculas** — *«Vida» va en minúscula: es una palabra común en medio de la oración.*
- **Tildes**, con la regla de verdad — *«canción» es aguda (can-CIÓN) y termina en «n», por eso lleva tilde.* También la tilde diacrítica (tú/tu, él/el, más/mas…) y los hiatos (dí-a).
- **Palabras que se confunden** — haber / a ver, hay / ahí / ay, echo / hecho, vaya / valla…
- **Gramática, puntuación, palabras repetidas, espacios**, con lo que explique cada caso.

Funciona en **español e inglés**. Las correcciones las detecta [LanguageTool](https://languagetool.org); La Sacerdotisa agrega el «por qué» en español claro.

Hay dos formas de usarla, con el mismo motor y las mismas explicaciones:

| | Dónde funciona | Cómo se usa |
|---|---|---|
| **Extensión** (`extension/`) | Firefox, Brave y Chrome: cualquier página web | Selecciona y pulsa **Ctrl+Shift+U**, o clic derecho → *Revisar y explicar con La Sacerdotisa* |
| **App de Windows** (`windows/`) | **En todos los programas**: Word, Bloc de notas, WhatsApp, Google Docs… | Selecciona y pulsa **Ctrl+Alt+Win+E** |
| **App de Android** (`android/`) | **En todas las apps del celular**: WhatsApp, Notas, Messenger… | **Automática**: mientras escribes sale una burbuja; tócala para ver el porqué. O selecciona → **Explicar**. [Cómo instalarla](android/README.md) |

En el panel eliges las sugerencias que quieras y pulsas **Aplicar cambios** (extensión) o **Reemplazar en el texto** (Windows): el texto se corrige ahí mismo.

## Instalar la extensión

**Brave o Chrome**
1. Abre `brave://extensions` (o `chrome://extensions`).
2. Activa **Modo de desarrollador** (arriba a la derecha).
3. **Cargar descomprimida** → elige la carpeta `extension`.

**Firefox**
1. Abre `about:debugging#/runtime/this-firefox`.
2. **Cargar complemento temporal…** → elige `extension/manifest.json`.
3. Se abre la página de opciones: pulsa **Permitir LanguageTool** (Firefox pide ese permiso aparte).

En Firefox, un complemento temporal se quita al cerrar el navegador. Para dejarlo fijo hay que firmarlo en addons.mozilla.org (gratis, puede ser sin publicarlo).

El atajo se cambia en `brave://extensions/shortcuts`, `chrome://extensions/shortcuts` o en Firefox en *Complementos → ⚙ → Gestionar atajos*.

## Instalar la app de Windows

En la carpeta `windows`:
1. Doble clic en **`Probar.bat`**: compila (con el compilador que ya trae Windows) y la abre.
2. Selecciona un texto en cualquier programa y pulsa **Ctrl+Alt+Win+E**.
3. Si todo va bien, **`Instalar.bat`** para que arranque con Windows. **`Desinstalar.bat`** la quita.

En el ícono junto al reloj: *Revisar el portapapeles* (también con doble clic), *Idioma* (detectar, español, inglés) y *Salir*.

## Privacidad

En la extensión y en Windows, el texto se envía a LanguageTool **solo cuando lo pides** (atajo, menú o botón); nunca se lee la página por su cuenta. En **Android** la burbuja revisa mientras escribes: lee solo el campo donde escribes (nunca contraseñas), y lo envía a LanguageTool solo si lo dejas activado en la app; sin LanguageTool usa reglas del celular y nada sale del teléfono. El servicio gratuito admite unas 20 revisiones por minuto y hasta unos 20 KB por texto, y pide no enviarle consultas automáticas.

## Código

| Ruta | Qué es |
|---|---|
| `extension/datos/explicaciones.json` | **Todas las explicaciones** (las usan la extensión y la app de Windows). Edítalo para cambiar o agregar textos. |
| `extension/explicar.js` | Motor de explicaciones (JavaScript). |
| `extension/contenido.js`, `background.js` | Panel en la página y consulta a LanguageTool. |
| `windows/src/Explicador.cs` | El mismo motor en C#. |
| `windows/src/App.cs`, `Ventana.cs` | Atajo, captura del texto seleccionado, ventana. |
| `android/app/src/main/java/…/motor/Explicador.kt` | El mismo motor en Kotlin (Android). |
| `compartido/casos.json` | Casos de prueba que deben dar lo mismo en los tres motores. |

Pruebas: `node tests/explicar.test.js` (extensión), `windows/tests/ExplicadorTests.cs` (Windows) y `./gradlew testDebugUnitTest` en `android/`.

## Estado

Primera versión (5 nov 2026). La extensión se probó cargada en Chromium y pasa la revisión `web-ext lint` de Firefox; la app de Windows compila como C# 5 y su ventana se probó con una respuesta guardada. Las pruebas reales contra LanguageTool, Firefox y el atajo en Windows quedan para el PC.

Ilustraciones propias en estilo duotono · azul noche `#1f2a5c` y durazno `#f4a582`.
