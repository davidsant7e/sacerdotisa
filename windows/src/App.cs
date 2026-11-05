// La Sacerdotisa para Windows: atajo global, captura del texto seleccionado, consulta a LanguageTool.

using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Runtime.InteropServices;
using System.Runtime.Serialization;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace Sacerdotisa
{
    [DataContract] public class Ajustes
    {
        /// <summary>"auto", "es" o "en-US".</summary>
        [DataMember(Name = "idioma")] public string Idioma = "auto";
    }

    public static class App
    {
        // ---------- API de Windows ----------
        [DllImport("user32.dll")] static extern bool RegisterHotKey(IntPtr h, int id, int mods, int vk);
        [DllImport("user32.dll")] static extern bool UnregisterHotKey(IntPtr h, int id);
        [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
        [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
        [DllImport("user32.dll")] static extern short GetAsyncKeyState(int vk);
        [DllImport("user32.dll")] static extern uint GetClipboardSequenceNumber();
        [DllImport("user32.dll")] static extern uint SendInput(uint n, INPUT[] inputs, int size);

        [StructLayout(LayoutKind.Sequential)] struct INPUT { public int type; public KEYBDINPUT ki; public long relleno; }
        [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }

        const int MOD_ALT = 1, MOD_CONTROL = 2, MOD_WIN = 8, MOD_NOREPEAT = 0x4000;
        const int VK_CONTROL = 0x11, VK_MENU = 0x12, VK_SHIFT = 0x10, VK_LWIN = 0x5B, VK_RWIN = 0x5C;
        const uint KEYEVENTF_KEYUP = 2;
        const string Api = "https://api.languagetool.org/v2/check";
        public const string TextoAtajo = "Ctrl+Alt+Win+E";

        static NotifyIcon icono;
        static Datos datos;
        static Ajustes ajustes;
        static string rutaAjustes;
        static VentanaRevision abierta;
        static Icon iconoApp;

        class Receptor : NativeWindow
        {
            public Action AlAtajo;
            public Receptor() { CreateHandle(new CreateParams()); }
            protected override void WndProc(ref Message m)
            {
                if (m.Msg == 0x0312 && AlAtajo != null) { AlAtajo(); return; }
                base.WndProc(ref m);
            }
        }

        public static void Run(string carpetaDatos)
        {
            bool primera;
            using (var unico = new Mutex(true, "Sacerdotisa_dseu1", out primera))
            {
                if (!primera) { MessageBox.Show("La Sacerdotisa ya está abierta (ícono junto al reloj).", "La Sacerdotisa"); return; }
                Application.EnableVisualStyles();
                ServicePointManager.SecurityProtocol |= (SecurityProtocolType)3072; // TLS 1.2

                datos = Datos.Cargar(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "explicaciones.json"));
                rutaAjustes = Path.Combine(carpetaDatos, "ajustes.json");
                ajustes = CargarAjustes();
                iconoApp = CargarIcono();

                var receptor = new Receptor { AlAtajo = delegate { RevisarSeleccion(); } };
                bool atajo = RegisterHotKey(receptor.Handle, 1, MOD_CONTROL | MOD_ALT | MOD_WIN | MOD_NOREPEAT, (int)Keys.E);

                using (icono = new NotifyIcon())
                {
                    icono.Icon = iconoApp;
                    icono.Text = "La Sacerdotisa · " + TextoAtajo;
                    icono.ContextMenuStrip = CrearMenu();
                    icono.DoubleClick += delegate { RevisarPortapapeles(); };
                    icono.Visible = true;
                    Avisar(atajo ? "Selecciona un texto en cualquier programa y pulsa " + TextoAtajo + "."
                                 : "Otra app ya usa " + TextoAtajo + ". Usa el menú del ícono para revisar el portapapeles.",
                           atajo ? ToolTipIcon.Info : ToolTipIcon.Warning);
                    try { Application.Run(); }
                    finally
                    {
                        UnregisterHotKey(receptor.Handle, 1);
                        receptor.DestroyHandle();
                        icono.Visible = false;
                    }
                }
            }
        }

        // ---------- captura ----------

        /// <summary>Copia lo seleccionado en la ventana activa (Ctrl+C simulado) y deja el portapapeles como estaba.</summary>
        static void RevisarSeleccion()
        {
            var destino = GetForegroundWindow();
            EsperarTeclasSueltas();
            string previo = null;
            try { if (Clipboard.ContainsText()) previo = Clipboard.GetText(); } catch (Exception) { }
            uint antes = GetClipboardSequenceNumber();
            Teclas((ushort)VK_CONTROL, (ushort)'C');
            string texto = null;
            for (int i = 0; i < 20 && GetClipboardSequenceNumber() == antes; i++) { Thread.Sleep(30); Application.DoEvents(); }
            if (GetClipboardSequenceNumber() != antes)
            {
                try { if (Clipboard.ContainsText()) texto = Clipboard.GetText(); } catch (Exception) { }
                Restaurar(previo);
            }
            if (string.IsNullOrWhiteSpace(texto))
            {
                Avisar("No encontré texto seleccionado. Selecciónalo y vuelve a pulsar " + TextoAtajo + ".", ToolTipIcon.Info);
                return;
            }
            Revisar(texto, destino);
        }

        static void RevisarPortapapeles()
        {
            string texto = null;
            try { if (Clipboard.ContainsText()) texto = Clipboard.GetText(); } catch (Exception) { }
            if (string.IsNullOrWhiteSpace(texto)) { Avisar("El portapapeles no tiene texto. Copia algo (Ctrl+C) y vuelve a intentarlo.", ToolTipIcon.Info); return; }
            Revisar(texto, IntPtr.Zero);
        }

        static void EsperarTeclasSueltas()
        {
            int[] teclas = { VK_CONTROL, VK_MENU, VK_SHIFT, VK_LWIN, VK_RWIN, (int)Keys.E };
            for (int i = 0; i < 60; i++)
            {
                if (!teclas.Any(k => (GetAsyncKeyState(k) & 0x8000) != 0)) return;
                Thread.Sleep(25);
            }
        }

        static void Teclas(ushort modificador, ushort tecla)
        {
            var e = new[]
            {
                Tecla(modificador, false), Tecla(tecla, false), Tecla(tecla, true), Tecla(modificador, true),
            };
            SendInput((uint)e.Length, e, Marshal.SizeOf(typeof(INPUT)));
        }

        static INPUT Tecla(ushort vk, bool soltar)
        {
            return new INPUT { type = 1, ki = new KEYBDINPUT { wVk = vk, dwFlags = soltar ? KEYEVENTF_KEYUP : 0 } };
        }

        static void Restaurar(string previo)
        {
            try { if (previo != null) Clipboard.SetText(previo); else Clipboard.Clear(); } catch (Exception) { }
        }

        // ---------- revisar ----------

        public static void Revisar(string texto, IntPtr destino)
        {
            if (abierta != null) abierta.Close();
            var v = new VentanaRevision();
            if (iconoApp != null) v.Icon = iconoApp;
            abierta = v;
            v.FormClosed += delegate { if (abierta == v) abierta = null; };
            v.Reemplazar += corregido => Pegar(corregido, destino);
            v.Show();
            v.Activate();
            v.Estado("Revisando " + texto.Length + " caracteres…");

            var hilo = new Thread(() =>
            {
                RespuestaLT r = null;
                string error = null;
                try { r = Consultar(texto); }
                catch (WebException we)
                {
                    var http = we.Response as HttpWebResponse;
                    if (http != null && (int)http.StatusCode == 429) error = "LanguageTool pide esperar un momento (límite de consultas por minuto). Intenta en unos segundos.";
                    else if (http != null && (int)http.StatusCode == 413) error = "El texto es demasiado largo para el servicio gratuito (máximo unos 20 KB).";
                    else error = "No pude conectar con LanguageTool: " + we.Message;
                }
                catch (Exception e) { error = "No se pudo revisar: " + e.Message; }
                try
                {
                    v.BeginInvoke((Action)(() =>
                    {
                        if (v.IsDisposed) return;
                        if (error != null) { v.Estado(error); return; }
                        MostrarResultado(v, texto, r, destino != IntPtr.Zero);
                    }));
                }
                catch (Exception) { /* la ventana se cerró */ }
            }) { IsBackground = true };
            hilo.Start();
        }

        public static void MostrarResultado(VentanaRevision v, string texto, RespuestaLT r, bool puedeReemplazar)
        {
            var codigo = r.Idioma != null ? ((r.Idioma.Detectado != null ? r.Idioma.Detectado.Codigo : null) ?? r.Idioma.Codigo) : "es";
            var items = (r.Errores ?? new List<MatchLT>()).Select(m => Explicador.Explicar(m, texto, codigo, datos)).ToList();
            v.Mostrar(texto, items, NombreIdioma(codigo), puedeReemplazar);
        }

        public static void CargarDatos(string ruta) { datos = Datos.Cargar(ruta); }

        static string NombreIdioma(string codigo)
        {
            var c = (codigo ?? "").ToLowerInvariant();
            if (c.StartsWith("es")) return "español";
            if (c.StartsWith("en")) return "inglés";
            return codigo;
        }

        static RespuestaLT Consultar(string texto)
        {
            var campos = "text=" + Uri.EscapeDataString(texto) + "&language=" + Uri.EscapeDataString(ajustes.Idioma);
            if (ajustes.Idioma == "auto") campos += "&preferredVariants=en-US";
            var cuerpo = Encoding.UTF8.GetBytes(campos);
            var req = (HttpWebRequest)WebRequest.Create(Api);
            req.Method = "POST";
            req.ContentType = "application/x-www-form-urlencoded";
            req.Accept = "application/json";
            req.UserAgent = "LaSacerdotisa/0.1 (Windows)";
            req.Timeout = 20000;
            req.ContentLength = cuerpo.Length;
            using (var s = req.GetRequestStream()) s.Write(cuerpo, 0, cuerpo.Length);
            using (var resp = (HttpWebResponse)req.GetResponse())
            using (var lector = new StreamReader(resp.GetResponseStream(), Encoding.UTF8))
                return Json.Leer<RespuestaLT>(lector.ReadToEnd());
        }

        /// <summary>Vuelve a la ventana original y pega el texto corregido sobre la selección.</summary>
        static void Pegar(string corregido, IntPtr destino)
        {
            if (destino == IntPtr.Zero) { Clipboard.SetText(corregido); Avisar("Texto corregido copiado al portapapeles.", ToolTipIcon.Info); return; }
            string previo = null;
            try { if (Clipboard.ContainsText()) previo = Clipboard.GetText(); } catch (Exception) { }
            Clipboard.SetText(corregido);
            SetForegroundWindow(destino);
            Thread.Sleep(150);
            Teclas((ushort)VK_CONTROL, (ushort)'V');
            var t = new System.Windows.Forms.Timer { Interval = 700 };
            t.Tick += delegate { t.Stop(); t.Dispose(); Restaurar(previo); };
            t.Start();
        }

        // ---------- menú, ajustes, ícono ----------

        static ContextMenuStrip CrearMenu()
        {
            var menu = new ContextMenuStrip();
            menu.Items.Add("Revisar el portapapeles", null, delegate { RevisarPortapapeles(); });
            menu.Items.Add(new ToolStripMenuItem("Revisar lo seleccionado: " + TextoAtajo) { Enabled = false });
            menu.Items.Add(new ToolStripSeparator());
            var idioma = new ToolStripMenuItem("Idioma");
            foreach (var par in new[] { new[] { "auto", "Detectar (español o inglés)" }, new[] { "es", "Español" }, new[] { "en-US", "Inglés" } })
            {
                var codigo = par[0];
                var item = new ToolStripMenuItem(par[1]);
                item.Click += delegate { ajustes.Idioma = codigo; GuardarAjustes(); };
                idioma.DropDownItems.Add(item);
            }
            idioma.DropDownOpening += delegate
            {
                for (int i = 0; i < idioma.DropDownItems.Count; i++)
                    ((ToolStripMenuItem)idioma.DropDownItems[i]).Checked = new[] { "auto", "es", "en-US" }[i] == ajustes.Idioma;
            };
            menu.Items.Add(idioma);
            menu.Items.Add("Acerca de LanguageTool", null, delegate { try { System.Diagnostics.Process.Start("https://languagetool.org"); } catch (Exception) { } });
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("Salir", null, delegate { Application.Exit(); });
            return menu;
        }

        static Ajustes CargarAjustes()
        {
            try { if (File.Exists(rutaAjustes)) return Json.Leer<Ajustes>(File.ReadAllText(rutaAjustes, Encoding.UTF8)) ?? new Ajustes(); }
            catch (Exception) { }
            return new Ajustes();
        }

        static void GuardarAjustes()
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(rutaAjustes));
                File.WriteAllText(rutaAjustes, Json.Escribir(ajustes), new UTF8Encoding(false));
            }
            catch (Exception e) { Avisar("No se pudieron guardar los ajustes: " + e.Message, ToolTipIcon.Error); }
        }

        public static Icon CargarIcono()
        {
            try
            {
                var ruta = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "sacerdotisa.ico");
                if (File.Exists(ruta)) return new Icon(ruta, SystemInformation.SmallIconSize);
            }
            catch (Exception) { }
            return SystemIcons.Information;
        }

        static void Avisar(string texto, ToolTipIcon tipo)
        {
            if (icono != null) icono.ShowBalloonTip(5000, "La Sacerdotisa", texto, tipo);
        }
    }
}
