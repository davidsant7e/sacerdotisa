"use strict";
const sel = document.getElementById("idioma");
const ok = document.getElementById("guardado");
chrome.storage.sync.get({ idioma: "auto" }, (v) => { sel.value = v.idioma; });
sel.addEventListener("change", () => {
  chrome.storage.sync.set({ idioma: sel.value }, () => {
    ok.textContent = " Guardado ✓";
    setTimeout(() => { ok.textContent = ""; }, 1500);
  });
});

// Firefox no concede solo los permisos de sitio: se piden con un clic aquí.
const ORIGEN = { origins: ["https://api.languagetool.org/*"] };
const aviso = document.getElementById("permiso");
chrome.permissions.contains(ORIGEN, (tiene) => { aviso.hidden = tiene; });
document.getElementById("pedir").addEventListener("click", () => {
  chrome.permissions.request(ORIGEN, (ok) => { aviso.hidden = ok; });
});
