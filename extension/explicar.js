/*
 * La Sacerdotisa — motor de explicaciones.
 * Recibe un error de LanguageTool y devuelve el «por qué» en español claro.
 * Funciona en el navegador (window.Sacerdotisa) y en Node (module.exports) para las pruebas.
 * La misma lógica está en windows/src/Explicador.cs; las dos se prueban con compartido/casos.json.
 */
(function (global) {
  "use strict";

  var VOCALES = "aeiouáéíóúü";
  var FUERTES = "aeoáéóíú"; // í y ú con tilde se comportan como fuertes (hiato)
  var TILDES = { "á": "a", "é": "e", "í": "i", "ó": "o", "ú": "u", "ü": "u" };
  var GRUPOS = ["pr", "pl", "br", "bl", "fr", "fl", "tr", "dr", "cr", "cl", "gr", "gl", "kr", "kl", "ch", "ll", "rr"];

  function sinTildes(s) {
    return s.replace(/[áéíóúü]/g, function (c) { return TILDES[c]; });
  }

  function esVocal(p, i) {
    var c = p[i];
    if (c === "y") return i === p.length - 1 && i > 0 && VOCALES.indexOf(p[i - 1]) >= 0; // «rey», «hoy»
    if (VOCALES.indexOf(c) < 0) return false;
    if (c === "u" && i > 0 && (p[i - 1] === "q" || p[i - 1] === "g") && (p[i + 1] === "e" || p[i + 1] === "i")) return false;
    return true;
  }

  function fuerte(c) { return FUERTES.indexOf(c) >= 0; }

  /** Núcleos vocálicos de una palabra: [{ini, fin, tilde}] (fin exclusivo). */
  function nucleos(p) {
    var res = [];
    var i = 0;
    while (i < p.length) {
      if (!esVocal(p, i)) { i++; continue; }
      var ini = i;
      while (i + 1 < p.length && esVocal(p, i + 1)) {
        if (fuerte(p[i]) && fuerte(p[i + 1])) break; // hiato: dos fuertes seguidas
        i++;
      }
      var trozo = p.substring(ini, i + 1);
      res.push({ ini: ini, fin: i + 1, tilde: /[áéíóú]/.test(trozo) });
      i++;
    }
    return res;
  }

  /** Separa en sílabas (aproximado, suficiente para explicar la tilde). */
  function silabas(p) {
    var ns = nucleos(p);
    if (ns.length <= 1) return [p];
    var cortes = [];
    for (var k = 0; k < ns.length - 1; k++) {
      var a = ns[k].fin, b = ns[k + 1].ini; // consonantes entre núcleos: p[a..b)
      var n = b - a;
      var corte;
      if (n === 0) corte = a;
      else if (n === 1) corte = a;
      else if (n === 2) corte = GRUPOS.indexOf(p.substring(a, b)) >= 0 ? a : a + 1;
      else corte = GRUPOS.indexOf(p.substring(b - 2, b)) >= 0 ? b - 2 : b - 1;
      cortes.push(corte);
    }
    var out = [], desde = 0;
    cortes.forEach(function (c) { out.push(p.substring(desde, c)); desde = c; });
    out.push(p.substring(desde));
    return out;
  }

  /** Posición del acento desde el final: 1 aguda, 2 llana, 3+ esdrújula. */
  function acento(p) {
    var ns = nucleos(p);
    if (ns.length === 0) return { desdeFinal: 1, total: 0 };
    for (var k = 0; k < ns.length; k++) if (ns[k].tilde) return { desdeFinal: ns.length - k, total: ns.length };
    var termina = /[aeiouns]$/.test(p);
    var desde = ns.length === 1 ? 1 : (termina ? 2 : 1);
    return { desdeFinal: desde, total: ns.length };
  }

  function silabasMarcadas(p) {
    var ss = silabas(p);
    var ac = acento(p);
    var idx = ss.length - ac.desdeFinal;
    return ss.map(function (s, i) { return i === idx ? s.toUpperCase() : s; }).join("-");
  }

  function plantilla(t, vars) {
    return t.replace(/\{(\w+)\}/g, function (_, k) { return vars[k] != null ? vars[k] : ""; });
  }

  function categoriaDe(match, datos) {
    var rule = match.rule || {};
    var cat = (rule.category && rule.category.id) || "";
    if (datos.categorias[cat]) return cat;
    var porTipo = datos.tiposProblema[rule.issueType];
    return porTipo || "MISC";
  }

  /** Explica por qué una palabra lleva o no tilde (palabra = forma correcta). */
  function explicarTilde(original, correcta, datos) {
    var t = datos.tildes;
    var p = correcta.toLowerCase();
    var clave = sinTildes(p);
    var dia = datos.diacriticas[clave];
    var ponTilde = /[áéíóú]/.test(p) && !/[áéíóú]/.test(original.toLowerCase());
    if (dia) {
      return ponTilde ? dia.con + " Aquí corresponde esa forma. (" + dia.sin + ")"
                      : dia.sin + " Aquí corresponde esa forma. (" + dia.con + ")";
    }
    if (/\s/.test(p)) return t.general;
    var ns = nucleos(p);
    var marcadas = silabasMarcadas(p);
    if (ns.length <= 1) return t.monosilabo;
    // Hiato: í o ú con tilde junto a otra vocal
    var m = /[íú]/.exec(p);
    if (m) {
      var i = m.index;
      var vecina = (i > 0 && VOCALES.indexOf(p[i - 1]) >= 0) || (i + 1 < p.length && VOCALES.indexOf(p[i + 1]) >= 0);
      if (vecina) return plantilla(t.hiato, { vocal: p[i], silabas: marcadas });
    }
    var ac = acento(p);
    var tiene = /[áéíóú]/.test(p);
    if (ac.desdeFinal >= 3) return plantilla(t.esdrujula, { silabas: marcadas });
    if (ac.desdeFinal === 1) return plantilla(tiene ? t.aguda_con : t.aguda_sin, { silabas: marcadas });
    return plantilla(tiene ? t.llana_con : t.llana_sin, { silabas: marcadas });
  }

  function inicioDeOracion(texto, offset) {
    var i = offset - 1;
    while (i >= 0 && /[\s"'«“(¿¡]/.test(texto[i])) i--;
    return i < 0 || /[.!?…]/.test(texto[i]);
  }

  function buscarConfusion(a, b, datos) {
    a = a.toLowerCase().trim();
    b = b.toLowerCase().trim();
    for (var i = 0; i < datos.confusiones.length; i++) {
      var c = datos.confusiones[i];
      if ((c.a === a && c.b === b) || (c.a === b && c.b === a)) return c.porque;
    }
    return null;
  }

  /**
   * match: un elemento de "matches" de LanguageTool.
   * texto: el texto revisado. idioma: código ("es", "en-US"…). datos: explicaciones.json.
   * Devuelve { tipo, titulo, porque, mensaje, original, sugerencias }.
   */
  function explicar(match, texto, idioma, datos) {
    var original = texto.substr(match.offset, match.length);
    var sugerencias = (match.replacements || []).map(function (r) { return r.value; });
    var rep = sugerencias[0] || "";
    var esEspanol = !idioma || idioma.toLowerCase().indexOf("es") === 0;
    var cat = categoriaDe(match, datos);
    var res = {
      tipo: cat,
      titulo: datos.categorias[cat].titulo,
      porque: datos.categorias[cat].porque,
      mensaje: match.message || "",
      original: original,
      sugerencias: sugerencias
    };
    var issue = (match.rule && match.rule.issueType) || "";

    // 1) Solo cambia mayúscula/minúscula
    if (rep && original !== rep && original.toLowerCase() === rep.toLowerCase()) {
      var m = datos.mayusculas;
      res.tipo = "CASING";
      res.titulo = datos.categorias.CASING.titulo;
      var aMayuscula = rep[0] !== rep[0].toLowerCase() && original[0] === original[0].toLowerCase();
      if (aMayuscula) {
        var inicio = inicioDeOracion(texto, match.offset);
        res.porque = esEspanol ? (inicio ? m.es_inicio : m.es_propio) : (inicio ? m.en_inicio : m.en_propio);
      } else {
        res.porque = plantilla(esEspanol ? m.es_minuscula : m.en_minuscula, { palabra: original });
      }
      return res;
    }

    // 2) Solo cambia la tilde (español)
    if (esEspanol && rep && original !== rep && sinTildes(original.toLowerCase()) === sinTildes(rep.toLowerCase())) {
      res.tipo = "DIACRITICS";
      res.titulo = datos.categorias.DIACRITICS.titulo;
      res.porque = explicarTilde(original, rep, datos);
      return res;
    }

    // 3) Palabras que se confunden (español)
    if (esEspanol && rep) {
      var conf = buscarConfusion(original, rep, datos);
      if (conf) {
        res.tipo = "CONFUSED_WORDS";
        res.titulo = datos.categorias.CONFUSED_WORDS.titulo;
        res.porque = conf;
        return res;
      }
    }

    // 4) Palabra repetida
    var palabras = original.trim().split(/\s+/);
    if (issue === "duplication" || (palabras.length === 2 && palabras[0].toLowerCase() === palabras[1].toLowerCase())) {
      res.tipo = "REDUNDANCY";
      res.titulo = datos.categorias.REDUNDANCY.titulo;
      res.porque = plantilla(datos.repetida, { palabra: palabras[0] });
      return res;
    }

    // 5) Espacios
    if (issue === "whitespace") {
      res.tipo = "TYPOGRAPHY";
      res.titulo = datos.categorias.TYPOGRAPHY.titulo;
      res.porque = datos.espacios;
      return res;
    }

    return res; // 6) explicación de la categoría
  }

  /** Aplica las sugerencias elegidas: [{offset, length, valor}] → texto nuevo. */
  function aplicar(texto, cambios) {
    var orden = cambios.slice().sort(function (a, b) { return b.offset - a.offset; });
    orden.forEach(function (c) {
      texto = texto.substring(0, c.offset) + c.valor + texto.substring(c.offset + c.length);
    });
    return texto;
  }

  var api = { explicar: explicar, aplicar: aplicar, silabas: silabas, silabasMarcadas: silabasMarcadas, acento: acento, sinTildes: sinTildes };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else global.Sacerdotisa = api;
})(typeof self !== "undefined" ? self : this);
