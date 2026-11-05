// Pruebas del motor de explicaciones (Node): node tests/explicar.test.js
const fs = require("fs");
const path = require("path");
const S = require("../extension/explicar.js");
const raiz = path.join(__dirname, "..");
const datos = JSON.parse(fs.readFileSync(path.join(raiz, "extension/datos/explicaciones.json"), "utf8"));
const casos = JSON.parse(fs.readFileSync(path.join(raiz, "compartido/casos.json"), "utf8"));
let ok = 0, fallos = 0;
const check = (c, n) => { if (c) ok++; else { fallos++; console.log("FALLA: " + n); } };

for (const c of casos.silabas) {
  const r = S.silabasMarcadas(c.palabra);
  check(r === c.marcadas, `sílabas ${c.palabra}: ${r} (esperado ${c.marcadas})`);
}
for (const c of casos.explicar) {
  const match = { offset: c.offset, length: c.length, message: "msg", replacements: c.reemplazo ? [{ value: c.reemplazo }] : [],
                  rule: { id: "X", issueType: c.issueType, category: { id: c.categoria } } };
  const r = S.explicar(match, c.texto, c.idioma, datos);
  check(r.tipo === c.tipo, `${c.nombre}: tipo ${r.tipo} (esperado ${c.tipo})`);
  for (const t of c.contiene) check(r.porque.includes(t), `${c.nombre}: «${t}» no está en: ${r.porque}`);
  check(r.original === c.texto.substr(c.offset, c.length), `${c.nombre}: original`);
}
for (const c of casos.aplicar) check(S.aplicar(c.texto, c.cambios) === c.resultado, "aplicar: " + S.aplicar(c.texto, c.cambios));
console.log(`ok=${ok} fallos=${fallos}`);
process.exit(fallos ? 1 : 0);
