// La Sacerdotisa — motor de explicaciones (C# 5). Es la misma lógica que extension/explicar.js;
// las dos se prueban con compartido/casos.json para que expliquen igual.

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Text.RegularExpressions;

namespace Sacerdotisa
{
    // ---------- respuesta de LanguageTool (/v2/check) ----------

    [DataContract] public class RespuestaLT
    {
        [DataMember(Name = "language")] public IdiomaLT Idioma;
        [DataMember(Name = "matches")] public List<MatchLT> Errores = new List<MatchLT>();
    }

    [DataContract] public class IdiomaLT
    {
        [DataMember(Name = "name")] public string Nombre;
        [DataMember(Name = "code")] public string Codigo;
        [DataMember(Name = "detectedLanguage")] public IdiomaLT Detectado;
    }

    [DataContract] public class MatchLT
    {
        [DataMember(Name = "message")] public string Mensaje;
        [DataMember(Name = "offset")] public int Offset;
        [DataMember(Name = "length")] public int Length;
        [DataMember(Name = "replacements")] public List<ReemplazoLT> Reemplazos = new List<ReemplazoLT>();
        [DataMember(Name = "rule")] public ReglaLT Regla;
    }

    [DataContract] public class ReemplazoLT { [DataMember(Name = "value")] public string Valor; }

    [DataContract] public class ReglaLT
    {
        [DataMember(Name = "id")] public string Id;
        [DataMember(Name = "issueType")] public string TipoProblema;
        [DataMember(Name = "category")] public CategoriaLT Categoria;
    }

    [DataContract] public class CategoriaLT
    {
        [DataMember(Name = "id")] public string Id;
        [DataMember(Name = "name")] public string Nombre;
    }

    // ---------- datos de las explicaciones (extension/datos/explicaciones.json) ----------

    [DataContract] public class Datos
    {
        [DataMember(Name = "categorias")] public Dictionary<string, TextoCategoria> Categorias;
        [DataMember(Name = "tiposProblema")] public Dictionary<string, string> TiposProblema;
        [DataMember(Name = "diacriticas")] public Dictionary<string, ParDiacritico> Diacriticas;
        [DataMember(Name = "confusiones")] public List<Confusion> Confusiones;
        [DataMember(Name = "tildes")] public Dictionary<string, string> Tildes;
        [DataMember(Name = "mayusculas")] public Dictionary<string, string> Mayusculas;
        [DataMember(Name = "repetida")] public string Repetida;
        [DataMember(Name = "espacios")] public string Espacios;

        public static Datos Cargar(string ruta) { return Json.Leer<Datos>(File.ReadAllText(ruta, Encoding.UTF8)); }
    }

    [DataContract] public class TextoCategoria
    {
        [DataMember(Name = "titulo")] public string Titulo;
        [DataMember(Name = "porque")] public string Porque;
    }

    [DataContract] public class ParDiacritico
    {
        [DataMember(Name = "sin")] public string Sin;
        [DataMember(Name = "con")] public string Con;
    }

    [DataContract] public class Confusion
    {
        [DataMember(Name = "a")] public string A;
        [DataMember(Name = "b")] public string B;
        [DataMember(Name = "porque")] public string Porque;
    }

    public class Explicacion
    {
        public string Tipo, Titulo, Porque, Mensaje, Original;
        public List<string> Sugerencias = new List<string>();
        public int Offset, Length;
    }

    public class Cambio { public int Offset, Length; public string Valor; }

    // ---------- motor ----------

    public static class Explicador
    {
        const string Vocales = "aeiouáéíóúü";
        const string Fuertes = "aeoáéóíú";
        static readonly string[] Grupos = { "pr", "pl", "br", "bl", "fr", "fl", "tr", "dr", "cr", "cl", "gr", "gl", "kr", "kl", "ch", "ll", "rr" };

        public static string SinTildes(string s)
        {
            var sb = new StringBuilder(s.Length);
            foreach (var c in s)
            {
                switch (c)
                {
                    case 'á': sb.Append('a'); break;
                    case 'é': sb.Append('e'); break;
                    case 'í': sb.Append('i'); break;
                    case 'ó': sb.Append('o'); break;
                    case 'ú': case 'ü': sb.Append('u'); break;
                    default: sb.Append(c); break;
                }
            }
            return sb.ToString();
        }

        static bool EsVocal(string p, int i)
        {
            char c = p[i];
            if (c == 'y') return i == p.Length - 1 && i > 0 && Vocales.IndexOf(p[i - 1]) >= 0;
            if (Vocales.IndexOf(c) < 0) return false;
            if (c == 'u' && i > 0 && (p[i - 1] == 'q' || p[i - 1] == 'g') && i + 1 < p.Length && (p[i + 1] == 'e' || p[i + 1] == 'i')) return false;
            return true;
        }

        static bool Fuerte(char c) { return Fuertes.IndexOf(c) >= 0; }
        static bool ConTilde(string s) { return Regex.IsMatch(s, "[áéíóú]"); }

        class Nucleo { public int Ini, Fin; public bool Tilde; }

        static List<Nucleo> Nucleos(string p)
        {
            var res = new List<Nucleo>();
            int i = 0;
            while (i < p.Length)
            {
                if (!EsVocal(p, i)) { i++; continue; }
                int ini = i;
                while (i + 1 < p.Length && EsVocal(p, i + 1))
                {
                    if (Fuerte(p[i]) && Fuerte(p[i + 1])) break;
                    i++;
                }
                res.Add(new Nucleo { Ini = ini, Fin = i + 1, Tilde = ConTilde(p.Substring(ini, i + 1 - ini)) });
                i++;
            }
            return res;
        }

        public static List<string> Silabas(string p)
        {
            var ns = Nucleos(p);
            if (ns.Count <= 1) return new List<string> { p };
            var cortes = new List<int>();
            for (int k = 0; k < ns.Count - 1; k++)
            {
                int a = ns[k].Fin, b = ns[k + 1].Ini, n = b - a, corte;
                if (n <= 1) corte = a;
                else if (n == 2) corte = Grupos.Contains(p.Substring(a, 2)) ? a : a + 1;
                else corte = Grupos.Contains(p.Substring(b - 2, 2)) ? b - 2 : b - 1;
                cortes.Add(corte);
            }
            var outp = new List<string>();
            int desde = 0;
            foreach (var c in cortes) { outp.Add(p.Substring(desde, c - desde)); desde = c; }
            outp.Add(p.Substring(desde));
            return outp;
        }

        /// <summary>Posición del acento desde el final: 1 aguda, 2 llana, 3+ esdrújula.</summary>
        public static int AcentoDesdeFinal(string p)
        {
            var ns = Nucleos(p);
            if (ns.Count == 0) return 1;
            for (int k = 0; k < ns.Count; k++) if (ns[k].Tilde) return ns.Count - k;
            if (ns.Count == 1) return 1;
            return Regex.IsMatch(p, "[aeiouns]$") ? 2 : 1;
        }

        public static string SilabasMarcadas(string p)
        {
            var ss = Silabas(p);
            int idx = ss.Count - AcentoDesdeFinal(p);
            return string.Join("-", ss.Select((s, i) => i == idx ? s.ToUpperInvariant() : s).ToArray());
        }

        static string Plantilla(string t, string clave, string valor)
        {
            return t.Replace("{" + clave + "}", valor);
        }

        static string CategoriaDe(MatchLT m, Datos d)
        {
            var cat = (m.Regla != null && m.Regla.Categoria != null) ? m.Regla.Categoria.Id : null;
            if (cat != null && d.Categorias.ContainsKey(cat)) return cat;
            string porTipo;
            var issue = m.Regla != null ? m.Regla.TipoProblema : null;
            if (issue != null && d.TiposProblema.TryGetValue(issue, out porTipo)) return porTipo;
            return "MISC";
        }

        static string ExplicarTilde(string original, string correcta, Datos d)
        {
            var t = d.Tildes;
            var p = correcta.ToLowerInvariant();
            var clave = SinTildes(p);
            bool ponTilde = ConTilde(p) && !ConTilde(original.ToLowerInvariant());
            ParDiacritico dia;
            if (d.Diacriticas.TryGetValue(clave, out dia))
                return ponTilde ? dia.Con + " Aquí corresponde esa forma. (" + dia.Sin + ")"
                                : dia.Sin + " Aquí corresponde esa forma. (" + dia.Con + ")";
            if (Regex.IsMatch(p, @"\s")) return t["general"];
            var ns = Nucleos(p);
            var marcadas = SilabasMarcadas(p);
            if (ns.Count <= 1) return t["monosilabo"];
            var m = Regex.Match(p, "[íú]");
            if (m.Success)
            {
                int i = m.Index;
                bool vecina = (i > 0 && Vocales.IndexOf(p[i - 1]) >= 0) || (i + 1 < p.Length && Vocales.IndexOf(p[i + 1]) >= 0);
                if (vecina) return Plantilla(Plantilla(t["hiato"], "vocal", p[i].ToString()), "silabas", marcadas);
            }
            int ac = AcentoDesdeFinal(p);
            bool tiene = ConTilde(p);
            if (ac >= 3) return Plantilla(t["esdrujula"], "silabas", marcadas);
            if (ac == 1) return Plantilla(tiene ? t["aguda_con"] : t["aguda_sin"], "silabas", marcadas);
            return Plantilla(tiene ? t["llana_con"] : t["llana_sin"], "silabas", marcadas);
        }

        static bool InicioDeOracion(string texto, int offset)
        {
            int i = offset - 1;
            while (i >= 0 && Regex.IsMatch(texto[i].ToString(), "[\\s\"'«“(¿¡]")) i--;
            return i < 0 || ".!?…".IndexOf(texto[i]) >= 0;
        }

        static string BuscarConfusion(string a, string b, Datos d)
        {
            a = a.ToLowerInvariant().Trim();
            b = b.ToLowerInvariant().Trim();
            foreach (var c in d.Confusiones)
                if ((c.A == a && c.B == b) || (c.A == b && c.B == a)) return c.Porque;
            return null;
        }

        static string Fragmento(string s, int ini, int largo)
        {
            if (ini < 0 || ini > s.Length) return "";
            return s.Substring(ini, Math.Min(largo, s.Length - ini));
        }

        public static Explicacion Explicar(MatchLT m, string texto, string idioma, Datos d)
        {
            var original = Fragmento(texto, m.Offset, m.Length);
            var sugerencias = (m.Reemplazos ?? new List<ReemplazoLT>()).Select(r => r.Valor ?? "").ToList();
            var rep = sugerencias.Count > 0 ? sugerencias[0] : "";
            bool esEspanol = string.IsNullOrEmpty(idioma) || idioma.ToLowerInvariant().StartsWith("es");
            var cat = CategoriaDe(m, d);
            var res = new Explicacion
            {
                Tipo = cat, Titulo = d.Categorias[cat].Titulo, Porque = d.Categorias[cat].Porque,
                Mensaje = m.Mensaje ?? "", Original = original, Sugerencias = sugerencias,
                Offset = m.Offset, Length = m.Length,
            };
            var issue = m.Regla != null ? (m.Regla.TipoProblema ?? "") : "";

            // 1) solo cambia mayúscula/minúscula
            if (rep.Length > 0 && original.Length > 0 && original != rep && original.ToLowerInvariant() == rep.ToLowerInvariant())
            {
                var may = d.Mayusculas;
                res.Tipo = "CASING";
                res.Titulo = d.Categorias["CASING"].Titulo;
                bool aMayuscula = char.IsUpper(rep[0]) && !char.IsUpper(original[0]);
                if (aMayuscula)
                {
                    bool inicio = InicioDeOracion(texto, m.Offset);
                    res.Porque = esEspanol ? (inicio ? may["es_inicio"] : may["es_propio"]) : (inicio ? may["en_inicio"] : may["en_propio"]);
                }
                else res.Porque = Plantilla(esEspanol ? may["es_minuscula"] : may["en_minuscula"], "palabra", original);
                return res;
            }

            // 2) solo cambia la tilde (español)
            if (esEspanol && rep.Length > 0 && original != rep && SinTildes(original.ToLowerInvariant()) == SinTildes(rep.ToLowerInvariant()))
            {
                res.Tipo = "DIACRITICS";
                res.Titulo = d.Categorias["DIACRITICS"].Titulo;
                res.Porque = ExplicarTilde(original, rep, d);
                return res;
            }

            // 3) palabras que se confunden (español)
            if (esEspanol && rep.Length > 0)
            {
                var conf = BuscarConfusion(original, rep, d);
                if (conf != null)
                {
                    res.Tipo = "CONFUSED_WORDS";
                    res.Titulo = d.Categorias["CONFUSED_WORDS"].Titulo;
                    res.Porque = conf;
                    return res;
                }
            }

            // 4) palabra repetida
            var palabras = Regex.Split(original.Trim(), @"\s+");
            if (issue == "duplication" || (palabras.Length == 2 && palabras[0].ToLowerInvariant() == palabras[1].ToLowerInvariant()))
            {
                res.Tipo = "REDUNDANCY";
                res.Titulo = d.Categorias["REDUNDANCY"].Titulo;
                res.Porque = Plantilla(d.Repetida, "palabra", palabras[0]);
                return res;
            }

            // 5) espacios
            if (issue == "whitespace")
            {
                res.Tipo = "TYPOGRAPHY";
                res.Titulo = d.Categorias["TYPOGRAPHY"].Titulo;
                res.Porque = d.Espacios;
                return res;
            }
            return res;
        }

        public static string Aplicar(string texto, IEnumerable<Cambio> cambios)
        {
            foreach (var c in cambios.OrderByDescending(x => x.Offset))
                texto = texto.Substring(0, c.Offset) + c.Valor + texto.Substring(c.Offset + c.Length);
            return texto;
        }
    }

    public static class Json
    {
        static readonly DataContractJsonSerializerSettings Ajustes =
            new DataContractJsonSerializerSettings { UseSimpleDictionaryFormat = true };

        public static T Leer<T>(string texto)
        {
            if (texto.Length > 0 && texto[0] == '﻿') texto = texto.Substring(1);
            var ser = new DataContractJsonSerializer(typeof(T), Ajustes);
            using (var ms = new MemoryStream(Encoding.UTF8.GetBytes(texto))) return (T)ser.ReadObject(ms);
        }

        public static string Escribir<T>(T obj)
        {
            var ser = new DataContractJsonSerializer(typeof(T), Ajustes);
            using (var ms = new MemoryStream()) { ser.WriteObject(ms, obj); return Encoding.UTF8.GetString(ms.ToArray()); }
        }
    }
}
