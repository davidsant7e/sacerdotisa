/*
 * La Sacerdotisa — panel que aparece en la página cuando pides revisar un texto.
 * Se inserta solo en ese momento. Todo el texto de la página y de LanguageTool se muestra
 * con textContent (nunca como HTML), así una página no puede inyectar nada en el panel.
 */
(function () {
  "use strict";
  if (window.__sacerdotisa) return;
  window.__sacerdotisa = true;

  const COLORES = { noche: "#1f2a5c", nocheClara: "#2b3873", durazno: "#f4a582", crema: "#fbe9df", error: "#9e3b4f" };
  let datos = null;
  let host = null;

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg && msg.tipo === "revisar") revisar();
  });

  // ---------- capturar lo seleccionado ----------

  function capturar() {
    const el = document.activeElement;
    const esCampo = el && (el.tagName === "TEXTAREA" ||
      (el.tagName === "INPUT" && /^(text|search|email|url|)$/i.test(el.type || "")));
    if (esCampo) {
      let ini = el.selectionStart, fin = el.selectionEnd;
      if (ini === fin) { ini = 0; fin = el.value.length; } // sin selección: todo el campo
      return { modo: "campo", el, ini, fin, texto: el.value.substring(ini, fin) };
    }
    const sel = window.getSelection();
    const texto = sel ? sel.toString() : "";
    if (sel && sel.rangeCount > 0) {
      const rango = sel.getRangeAt(0).cloneRange();
      const nodo = rango.commonAncestorContainer.nodeType === 1 ? rango.commonAncestorContainer : rango.commonAncestorContainer.parentElement;
      const editable = nodo && nodo.closest && nodo.closest("[contenteditable=''],[contenteditable='true'],[contenteditable='plaintext-only']");
      return { modo: editable ? "editable" : "lectura", rango, texto };
    }
    return { modo: "lectura", texto };
  }

  function rectDe(c) {
    try {
      if (c.modo === "campo") return c.el.getBoundingClientRect();
      if (c.rango) { const r = c.rango.getBoundingClientRect(); if (r.width || r.height) return r; }
    } catch (e) { /* sin posición */ }
    return null;
  }

  // ---------- flujo ----------

  async function revisar() {
    const c = capturar();
    const panel = abrirPanel(rectDe(c));
    if (!c.texto || !c.texto.trim()) {
      panel.estado("Selecciona el texto que quieres revisar (o pon el cursor en un campo de texto) y vuelve a intentarlo.");
      return;
    }
    panel.estado("Revisando…");
    try {
      if (!datos) datos = await (await fetch(chrome.runtime.getURL("datos/explicaciones.json"))).json();
      const r = await chrome.runtime.sendMessage({ tipo: "consultar", texto: c.texto });
      if (!r || r.error) { panel.estado((r && r.error) || "No se pudo consultar a LanguageTool."); return; }
      const idioma = (r.language && ((r.language.detectedLanguage && r.language.detectedLanguage.code) || r.language.code)) || "es";
      const items = (r.matches || []).map((m) => ({ m, e: Sacerdotisa.explicar(m, c.texto, idioma, datos) }));
      panel.mostrar(c, items, nombreIdioma(idioma));
    } catch (e) {
      panel.estado("No se pudo revisar: " + ((e && e.message) || e));
    }
  }

  function nombreIdioma(codigo) {
    const c = (codigo || "").toLowerCase();
    if (c.indexOf("es") === 0) return "español";
    if (c.indexOf("en") === 0) return "inglés";
    return codigo;
  }

  // ---------- panel ----------

  function crear(tag, estilo, texto) {
    const n = document.createElement(tag);
    if (estilo) n.setAttribute("style", estilo);
    if (texto != null) n.textContent = texto;
    return n;
  }

  function abrirPanel(rect) {
    if (host) host.remove();
    host = document.createElement("div");
    host.setAttribute("style", "all: initial; position: fixed; z-index: 2147483647;");
    const raiz = host.attachShadow({ mode: "open" });
    const caja = crear("div",
      `font: 14px/1.45 'Segoe UI', system-ui, sans-serif; background: ${COLORES.noche}; color: #fff;
       width: 400px; max-width: calc(100vw - 24px); max-height: 70vh; overflow: auto; border-radius: 14px;
       box-shadow: 0 12px 40px rgba(0,0,0,.35); border: 1px solid ${COLORES.durazno}; box-sizing: border-box;
       scrollbar-color: ${COLORES.durazno} ${COLORES.noche}; scrollbar-width: thin;`);
    caja.setAttribute("role", "dialog");
    caja.setAttribute("aria-label", "La Sacerdotisa: explicación de errores");
    raiz.appendChild(caja);

    const cab = crear("div", `display:flex; align-items:center; gap:8px; padding: 12px 14px 8px; position: sticky; top:0; background:${COLORES.noche};`);
    cab.appendChild(crear("span", `color:${COLORES.durazno}; font-weight:700; letter-spacing:.08em; font-size:11px; flex:1;`, "II · LA SACERDOTISA"));
    const cerrar = crear("button", `all: unset; cursor: pointer; color:${COLORES.durazno}; font-size:18px; padding: 0 6px;`, "×");
    cerrar.setAttribute("aria-label", "Cerrar");
    cerrar.addEventListener("click", cerrarPanel);
    cab.appendChild(cerrar);
    caja.appendChild(cab);
    const cuerpo = crear("div", "padding: 0 14px 12px;");
    caja.appendChild(cuerpo);

    document.documentElement.appendChild(host);
    ubicar(rect);
    document.addEventListener("keydown", alTeclear, true);

    function ubicar(r) {
      const ancho = Math.min(400, window.innerWidth - 24);
      let x = r ? Math.min(Math.max(12, r.left), window.innerWidth - ancho - 12) : window.innerWidth - ancho - 16;
      let y = r ? r.bottom + 8 : 16;
      if (y > window.innerHeight * 0.55) y = Math.max(12, (r ? r.top : 16) - Math.min(window.innerHeight * 0.7, 420) - 8);
      host.style.left = x + "px";
      host.style.top = y + "px";
    }

    return {
      estado(texto) {
        cuerpo.replaceChildren(crear("p", "margin: 6px 0;", texto));
        cuerpo.appendChild(pie());
      },
      mostrar(c, items, nombreIdioma) {
        cuerpo.replaceChildren();
        if (items.length === 0) {
          cuerpo.appendChild(crear("p", `margin: 6px 0; color:${COLORES.durazno}; font-weight:600;`, "✓ No encontré errores en el texto."));
          cuerpo.appendChild(pie(nombreIdioma));
          return;
        }
        cuerpo.appendChild(crear("p", "margin: 2px 0 10px; color:#d9cfe0; font-size:12px;",
          items.length + (items.length === 1 ? " cosa para revisar" : " cosas para revisar") + (nombreIdioma ? " · " + nombreIdioma : "")));
        const elegidos = new Map(); // índice → sugerencia elegida
        items.forEach((it, i) => cuerpo.appendChild(tarjeta(c.texto, it, (valor) => {
          if (valor == null) elegidos.delete(i); else elegidos.set(i, valor);
          actualizar();
        })));
        const acciones = crear("div", "display:flex; gap:8px; flex-wrap:wrap; margin-top:12px;");
        const aplicar = boton(c.modo === "lectura" ? "Copiar corregido" : "Aplicar cambios", true);
        const copiar = boton("Copiar corregido", false);
        acciones.appendChild(aplicar);
        if (c.modo !== "lectura") acciones.appendChild(copiar);
        cuerpo.appendChild(acciones);
        cuerpo.appendChild(pie(nombreIdioma));

        function corregido() {
          const cambios = [];
          elegidos.forEach((valor, i) => cambios.push({ offset: items[i].m.offset, length: items[i].m.length, valor }));
          return Sacerdotisa.aplicar(c.texto, cambios);
        }
        function actualizar() {
          aplicar.disabled = copiar.disabled = elegidos.size === 0;
          aplicar.style.opacity = copiar.style.opacity = elegidos.size ? "1" : ".5";
        }
        aplicar.addEventListener("click", () => {
          const nuevo = corregido();
          if (c.modo === "campo") {
            const v = c.el.value;
            c.el.value = v.substring(0, c.ini) + nuevo + v.substring(c.fin);
            c.el.dispatchEvent(new Event("input", { bubbles: true }));
            c.el.focus();
          } else if (c.modo === "editable" && c.rango) {
            const sel = window.getSelection();
            sel.removeAllRanges();
            sel.addRange(c.rango);
            document.execCommand("insertText", false, nuevo);
          } else {
            navigator.clipboard.writeText(nuevo);
          }
          cerrarPanel();
        });
        copiar.addEventListener("click", () => { navigator.clipboard.writeText(corregido()); copiar.textContent = "Copiado ✓"; });
        actualizar();
      }
    };
  }

  function tarjeta(texto, it, alElegir) {
    const { m, e } = it;
    const t = crear("div", `background:${COLORES.nocheClara}; border-radius:10px; padding:10px 12px; margin-bottom:8px;`);
    t.appendChild(crear("span", `display:inline-block; background:${COLORES.durazno}; color:${COLORES.noche}; font-size:11px;
      font-weight:700; border-radius:20px; padding:1px 8px; margin-bottom:6px;`, e.titulo));
    // fragmento con el error resaltado
    const ini = Math.max(0, m.offset - 30), fin = Math.min(texto.length, m.offset + m.length + 30);
    const frag = crear("div", "margin: 2px 0 6px; color:#fff;");
    frag.appendChild(document.createTextNode((ini > 0 ? "…" : "") + texto.substring(ini, m.offset)));
    frag.appendChild(crear("mark", `background:${COLORES.error}; color:#fff; border-radius:3px; padding:0 2px;`, texto.substr(m.offset, m.length) || "␣"));
    frag.appendChild(document.createTextNode(texto.substring(m.offset + m.length, fin) + (fin < texto.length ? "…" : "")));
    t.appendChild(frag);
    t.appendChild(crear("div", `color:${COLORES.crema}; margin-bottom:4px;`, "Por qué: " + e.porque));
    if (e.mensaje) t.appendChild(crear("div", "color:#b9b1cf; font-size:12px; margin-bottom:6px;", "LanguageTool: " + e.mensaje));
    if (e.sugerencias.length) {
      const fila = crear("div", "display:flex; gap:6px; flex-wrap:wrap;");
      let activo = null;
      e.sugerencias.slice(0, 5).forEach((s) => {
        const b = boton(s === "" ? "(quitar)" : s, false);
        b.addEventListener("click", () => {
          if (activo === b) { b.style.background = "transparent"; b.style.color = COLORES.durazno; activo = null; alElegir(null); return; }
          if (activo) { activo.style.background = "transparent"; activo.style.color = COLORES.durazno; }
          b.style.background = COLORES.durazno; b.style.color = COLORES.noche; activo = b; alElegir(s);
        });
        fila.appendChild(b);
      });
      t.appendChild(fila);
    }
    return t;
  }

  function boton(texto, principal) {
    const b = crear("button", `font: 600 13px 'Segoe UI', system-ui, sans-serif; cursor:pointer; border-radius:8px; padding:4px 10px;
      border:1px solid ${COLORES.durazno}; background:${principal ? COLORES.durazno : "transparent"}; color:${principal ? COLORES.noche : COLORES.durazno};`, texto);
    b.type = "button";
    return b;
  }

  function pie(nombreIdioma) {
    const p = crear("div", "margin-top:10px; font-size:11px; color:#9d95b8;");
    p.appendChild(document.createTextNode("Revisado con "));
    const a = crear("a", `color:${COLORES.durazno};`, "LanguageTool");
    a.href = "https://languagetool.org";
    a.target = "_blank";
    a.rel = "noopener";
    p.appendChild(a);
    p.appendChild(document.createTextNode(" · Esc para cerrar"));
    return p;
  }

  function alTeclear(ev) {
    if (ev.key === "Escape") { cerrarPanel(); ev.stopPropagation(); }
  }

  function cerrarPanel() {
    if (host) host.remove();
    host = null;
    document.removeEventListener("keydown", alTeclear, true);
  }
})();
