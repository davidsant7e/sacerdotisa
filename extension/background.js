/*
 * La Sacerdotisa — fondo de la extensión (Chrome, Brave y Firefox).
 * Solo actúa cuando tú lo pides (menú contextual, atajo o botón): no lee las páginas por su cuenta.
 */
"use strict";

const API = "https://api.languagetool.org/v2/check";
const MENU = "sacerdotisa-revisar";

chrome.runtime.onInstalled.addListener((detalle) => {
  if (detalle.reason === "install") {
    chrome.permissions.contains({ origins: ["https://api.languagetool.org/*"] }, (tiene) => { if (!tiene) chrome.runtime.openOptionsPage(); });
  }
  chrome.contextMenus.create({
    id: MENU,
    title: "Revisar y explicar con La Sacerdotisa",
    contexts: ["selection", "editable"]
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === MENU && tab) iniciar(tab);
});

chrome.commands.onCommand.addListener((comando, tab) => {
  if (comando === "revisar") {
    if (tab) iniciar(tab);
    else chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => tabs[0] && iniciar(tabs[0]));
  }
});

chrome.action.onClicked.addListener((tab) => iniciar(tab));

/** Inserta el panel en la pestaña (solo en ese momento, gracias a activeTab) y le pide revisar. */
async function iniciar(tab) {
  try {
    await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["explicar.js", "contenido.js"] });
    await chrome.tabs.sendMessage(tab.id, { tipo: "revisar" });
  } catch (e) {
    // Páginas internas del navegador (chrome://, about:, tienda de extensiones) no permiten extensiones.
    console.warn("La Sacerdotisa no puede actuar en esta página:", e && e.message);
  }
}

chrome.runtime.onMessage.addListener((msg, _sender, responder) => {
  if (msg && msg.tipo === "consultar") {
    consultar(msg.texto).then(responder, (e) => responder({ error: String((e && e.message) || e) }));
    return true; // respuesta asíncrona
  }
  return false;
});

async function idiomaPreferido() {
  const { idioma } = await chrome.storage.sync.get({ idioma: "auto" });
  return idioma;
}

async function consultar(texto) {
  const idioma = await idiomaPreferido();
  const cuerpo = new URLSearchParams({ text: texto, language: idioma });
  if (idioma === "auto") cuerpo.set("preferredVariants", "en-US");
  const permitido = await chrome.permissions.contains({ origins: ["https://api.languagetool.org/*"] });
  if (!permitido) {
    chrome.runtime.openOptionsPage();
    throw new Error("Falta permitir la conexión con LanguageTool: se abrió la página de opciones, pulsa «Permitir LanguageTool».");
  }
  const r = await fetch(API, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json" },
    body: cuerpo.toString()
  });
  if (r.status === 429) throw new Error("LanguageTool pide esperar un momento (límite de consultas por minuto). Intenta en unos segundos.");
  if (r.status === 413) throw new Error("El texto es demasiado largo para el servicio gratuito (máximo unos 20 KB).");
  if (!r.ok) throw new Error("LanguageTool respondió con el código " + r.status + ".");
  return await r.json();
}
