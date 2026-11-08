# II·A · La Sacerdotisa para Android

El corrector que explica, en el celular. Mientras escribes en **WhatsApp, Notas, Messenger, Gmail o cualquier app**, aparece una burbuja si algo está mal:

```
┌──────────────────────────┐
│ Si la Vida fuera facil   │
└──────────────────────────┘
          ╭──────────────╮
          │ II · 2 avisos │   ← tócala
          ╰──────────────╯
MAYÚSCULAS   «Vida» → «vida»
Va en minúscula: «Vida» es una palabra común en medio de la oración…        [Corregir]
TILDES       «facil» → «fácil»
Es una palabra llana o grave (FÁ-cil) y no termina en vocal, «n» ni «s», por eso lleva tilde.   [Corregir]
```

- **Corregir** cambia la palabra ahí mismo, en la app donde estás escribiendo. **Corregir todo** cambia todas.
- **No revisar en …** apaga la burbuja en esa app.
- También: selecciona un texto → en el menú, **Explicar**.

## Qué revisa

| Sin internet (reglas del celular) | Con LanguageTool (si lo activas) |
|---|---|
| Mayúscula al empezar y «la Vida» en medio de la oración, tildes que siempre van (también, después, fácil, día, canción…), preguntas sin tilde (¿como…? → ¿cómo…?), «vió/dió/fué», palabras repetidas, espacios, «haber si» → «a ver si» | Todo lo anterior y mucho más: ortografía, gramática, tu/tú, el/él, se/sé, que/qué… |

Las explicaciones son las mismas de la extensión y de la app de Windows (`extension/datos/explicaciones.json`).

Puntuación de LanguageTool (los «¿ ¡» de apertura y otras) y estilo vienen **apagados**: en el chat casi nadie abre «¿» y molestaría. Se prenden en la app.

### Comas: mirando la frase completa

Además de las palabras, La Sacerdotisa revisa **dónde va y dónde no va una coma** según la estructura de la frase
(`ReglasComas.kt`, sin internet, encendido por defecto):

| Caso | Ejemplo | Corrección |
|---|---|---|
| Sobra entre sujeto y verbo | «El perro de mi vecino, es bravo» | «…vecino es bravo» |
| Sobra antes de la «y» final de una lista | «pan, leche, y huevos» | «pan, leche y huevos» |
| Sobra antes de «que» | «Me dijo, que vendría» | «Me dijo que vendría» |
| Falta antes de «pero» / «aunque» | «Quería ir pero llovió» | «Quería ir, pero llovió» |
| Falta después de un conector | «Sin embargo no llegó» | «Sin embargo, no llegó» |
| Conector en medio, entre comas | «Lo intenté sin embargo no pude» | «Lo intenté, sin embargo, no pude» |
| Falta con el nombre de a quien le hablas | «Hola Juan» | «Hola, Juan» |

Son reglas conservadoras: respetan incisos («Mi hermano, que vive en Cali, llega hoy»), complementos de tiempo al
inicio («Esta semana, tengo…»), vocativos («Juan, es tarde») y frases cortas («pobre pero honrado»).

### Revisar el texto completo (Gemini)

Lo que depende del sentido (una coma que cambia el significado, concordancia, tiempos verbales, una frase que no se
entiende) lo revisa **Gemini** cuando lo pides: en la burbuja toca **«Revisar texto completo»** (también en el menú
«Explicar» y en la caja de prueba). Gemini lee todo el texto y devuelve cada error con el fragmento exacto, la corrección
y el porqué; las tarjetas dicen «· Gemini». Si corriges uno, los demás siguen en su sitio.

- Necesita la clave de Gemini (la misma de La Justicia y El Sol), que se pega en la app y se guarda cifrada con el Android Keystore. Modelo por defecto: `gemini-3.5-flash` (se puede cambiar).
- Con clave, la burbuja también aparece como «II · revisar texto» en textos de 6 palabras o más aunque no haya otros avisos.
- Nunca se llama sola mientras escribes: solo al tocar el botón.

## Instalar (Android Studio)

1. **File → Open** → `C:\Git\sacerdotisa\android`. Espera a que termine *Gradle sync*.
2. Conecta el Motorola y pulsa **Run ▶**.
3. En la app: **Abrir Accesibilidad** → *Apps instaladas* → **La Sacerdotisa** → activar. Android avisa que la app puede ver lo que escribes: es lo que necesita para revisarlo.
   - Si el interruptor sale gris («ajuste restringido»): **Ajustes → Apps → La Sacerdotisa → ⋮ → Permitir ajustes restringidos**, y vuelve a activarlo.
4. Escribe en la caja de prueba de la app, o ve a WhatsApp y escribe *Si la Vida fuera facil*.

Desde la terminal (Git Bash, con el SDK de Android Studio):

```bash
cd /c/Git/sacerdotisa/android
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testDebugUnitTest     # pruebas del motor
./gradlew installDebug          # instala en el celular conectado
```

## Privacidad

- Lee **solo el campo donde estás escribiendo**, y solo mientras escribes. **Nunca lee campos de contraseña.** No guarda el texto.
- Con **LanguageTool** activado, el texto de ese campo se envía a `languagetool.org` para revisarlo (como en la extensión y en Windows). Se puede apagar: entonces nada sale del celular.
- Para no abusar del servicio gratuito: consulta cuando dejas de escribir (1,5 s), máximo 10 veces por minuto, y espera si LanguageTool pide calma.
- Con **Revisar texto completo**, el texto se envía a Google (Gemini) solo en ese momento.

## Código

| Ruta | Qué es |
|---|---|
| `app/src/main/java/…/motor/` | El motor, en Kotlin puro (sin Android): `Explicador` (el mismo de `explicar.js` y `Explicador.cs`), `ReglasLocales` (palabras), `ReglasComas` (frase completa), `LanguageTool`, `Gemini`, `Revisor`, `Json`. |
| `…/servicio/SacerdotisaService.kt` | Servicio de accesibilidad: lee el campo, revisa y corrige (`ACTION_SET_TEXT`). |
| `…/servicio/Burbuja.kt` | La burbuja y el panel (ventana de accesibilidad: no pide «Mostrar sobre otras apps»). |
| `…/ui/MainActivity.kt` | Activar, ajustes y caja de prueba. |
| `…/ui/ExplicarActivity.kt` | Menú «Explicar» al seleccionar texto. |
| `app/src/test/…` | 29 pruebas: los casos de `compartido/casos.json`, las reglas locales y de comas (con textos que **no** deben dar avisos) y la lectura de respuestas de Gemini. |

Sin librerías: solo Android y Kotlin (AGP 9.4, compileSdk 37, mínimo Android 8).
