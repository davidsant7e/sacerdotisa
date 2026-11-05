// Pruebas del motor en C#, con los mismos casos que la extensión (compartido/casos.json).
// Uso: ExplicadorTests.exe <raíz del repositorio>

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.Serialization;
using System.Text;
using Sacerdotisa;

[DataContract] class Casos
{
    [DataMember(Name = "silabas")] public List<CasoSilaba> Silabas;
    [DataMember(Name = "explicar")] public List<CasoExplicar> Explicar;
    [DataMember(Name = "aplicar")] public List<CasoAplicar> Aplicar;
}
[DataContract] class CasoSilaba { [DataMember(Name = "palabra")] public string Palabra; [DataMember(Name = "marcadas")] public string Marcadas; }
[DataContract] class CasoExplicar
{
    [DataMember(Name = "nombre")] public string Nombre;
    [DataMember(Name = "texto")] public string Texto;
    [DataMember(Name = "offset")] public int Offset;
    [DataMember(Name = "length")] public int Length;
    [DataMember(Name = "reemplazo")] public string Reemplazo;
    [DataMember(Name = "categoria")] public string Categoria;
    [DataMember(Name = "issueType")] public string IssueType;
    [DataMember(Name = "idioma")] public string Idioma;
    [DataMember(Name = "tipo")] public string Tipo;
    [DataMember(Name = "contiene")] public List<string> Contiene;
}
[DataContract] class CasoAplicar
{
    [DataMember(Name = "texto")] public string Texto;
    [DataMember(Name = "cambios")] public List<CambioJson> Cambios;
    [DataMember(Name = "resultado")] public string Resultado;
}
[DataContract] class CambioJson { [DataMember(Name = "offset")] public int Offset; [DataMember(Name = "length")] public int Length; [DataMember(Name = "valor")] public string Valor; }

static class ExplicadorTests
{
    static int ok, fallos;
    static void Check(bool c, string n) { if (c) ok++; else { fallos++; Console.WriteLine("FALLA: " + n); } }

    static int Main(string[] args)
    {
        var raiz = args.Length > 0 ? args[0] : "..";
        var datos = Datos.Cargar(Path.Combine(raiz, "extension", "datos", "explicaciones.json"));
        var casos = Json.Leer<Casos>(File.ReadAllText(Path.Combine(raiz, "compartido", "casos.json"), Encoding.UTF8));

        foreach (var c in casos.Silabas)
        {
            var r = Explicador.SilabasMarcadas(c.Palabra);
            Check(r == c.Marcadas, "sílabas " + c.Palabra + ": " + r + " (esperado " + c.Marcadas + ")");
        }
        foreach (var c in casos.Explicar)
        {
            var m = new MatchLT
            {
                Offset = c.Offset, Length = c.Length, Mensaje = "msg",
                Reemplazos = string.IsNullOrEmpty(c.Reemplazo) ? new List<ReemplazoLT>() : new List<ReemplazoLT> { new ReemplazoLT { Valor = c.Reemplazo } },
                Regla = new ReglaLT { Id = "X", TipoProblema = c.IssueType, Categoria = new CategoriaLT { Id = c.Categoria } },
            };
            var e = Explicador.Explicar(m, c.Texto, c.Idioma, datos);
            Check(e.Tipo == c.Tipo, c.Nombre + ": tipo " + e.Tipo + " (esperado " + c.Tipo + ")");
            foreach (var t in c.Contiene) Check(e.Porque.Contains(t), c.Nombre + ": «" + t + "» no está en: " + e.Porque);
        }
        foreach (var c in casos.Aplicar)
        {
            var r = Explicador.Aplicar(c.Texto, c.Cambios.Select(x => new Cambio { Offset = x.Offset, Length = x.Length, Valor = x.Valor }));
            Check(r == c.Resultado, "aplicar: " + r);
        }

        // respuesta real de LanguageTool (formato /v2/check)
        var json = "{\"software\":{\"name\":\"LanguageTool\"},\"language\":{\"name\":\"Spanish\",\"code\":\"es\",\"detectedLanguage\":{\"name\":\"Spanish\",\"code\":\"es\"}}," +
                   "\"matches\":[{\"message\":\"No va mayúscula.\",\"offset\":6,\"length\":4,\"replacements\":[{\"value\":\"vida\"}]," +
                   "\"context\":{\"text\":\"Si la Vida\",\"offset\":6,\"length\":4},\"rule\":{\"id\":\"X\",\"issueType\":\"typographical\",\"category\":{\"id\":\"CASING\",\"name\":\"Mayúsculas\"}}}]}";
        var resp = Json.Leer<RespuestaLT>(json);
        Check(resp.Errores.Count == 1 && resp.Errores[0].Reemplazos[0].Valor == "vida", "lee la respuesta de LanguageTool");
        Check(resp.Idioma.Detectado.Codigo == "es", "idioma detectado");

        Console.WriteLine("ok=" + ok + " fallos=" + fallos);
        return fallos == 0 ? 0 : 1;
    }
}
