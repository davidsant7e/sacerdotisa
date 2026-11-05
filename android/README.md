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

Puntuación (los «¿ ¡» de apertura, comas) y estilo vienen **apagados**: en el chat casi nadie abre «¿» y molestaría. Se prenden en la app.

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

## Código

| Ruta | Qué es |
|---|---|
| `app/src/main/java/…/motor/` | El motor, en Kotlin puro (sin Android): `Explicador` (el mismo de `explicar.js` y `Explicador.cs`), `ReglasLocales`, `LanguageTool`, `Revisor`, `Json`. |
| `…/servicio/SacerdotisaService.kt` | Servicio de accesibilidad: lee el campo, revisa y corrige (`ACTION_SET_TEXT`). |
| `…/servicio/Burbuja.kt` | La burbuja y el panel (ventana de accesibilidad: no pide «Mostrar sobre otras apps»). |
| `…/ui/MainActivity.kt` | Activar, ajustes y caja de prueba. |
| `…/ui/ExplicarActivity.kt` | Menú «Explicar» al seleccionar texto. |
| `app/src/test/…` | Pruebas: los casos de `compartido/casos.json` y las reglas locales (con textos de chat que **no** deben dar avisos). |

Sin librerías: solo Android y Kotlin (AGP 9.4, compileSdk 37, mínimo Android 8).
