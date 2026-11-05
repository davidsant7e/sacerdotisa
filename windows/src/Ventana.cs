// Ventana de resultados de La Sacerdotisa (duotono azul noche + durazno).

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace Sacerdotisa
{
    public class VentanaRevision : Form
    {
        public static readonly Color Noche = Color.FromArgb(0x1f, 0x2a, 0x5c);
        public static readonly Color NocheClara = Color.FromArgb(0x2b, 0x38, 0x73);
        public static readonly Color Durazno = Color.FromArgb(0xf4, 0xa5, 0x82);
        public static readonly Color Crema = Color.FromArgb(0xfb, 0xe9, 0xdf);
        public static readonly Color Gris = Color.FromArgb(0xb9, 0xb1, 0xcf);
        public static readonly Color Rojo = Color.FromArgb(0x9e, 0x3b, 0x4f);
        const int Ancho = 520;

        readonly FlowLayoutPanel lista;
        readonly Label estado;
        readonly Button btnReemplazar, btnCopiar;
        readonly Dictionary<int, string> elegidos = new Dictionary<int, string>();
        string texto = "";
        List<Explicacion> items = new List<Explicacion>();

        /// <summary>El usuario pidió reemplazar el texto seleccionado por el corregido.</summary>
        public event Action<string> Reemplazar;

        public VentanaRevision()
        {
            Text = "La Sacerdotisa";
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.Manual;
            TopMost = true;
            KeyPreview = true;
            BackColor = Noche;
            ForeColor = Color.White;
            Font = new Font("Segoe UI", 10f);
            AutoScaleMode = AutoScaleMode.Dpi;
            ClientSize = new Size(Ancho + 40, 200);

            var raiz = new FlowLayoutPanel
            {
                FlowDirection = FlowDirection.TopDown, WrapContents = false, Dock = DockStyle.Fill,
                Padding = new Padding(20, 14, 20, 14), BackColor = Noche, AutoScroll = false,
            };
            raiz.Controls.Add(Etiqueta("II · LA SACERDOTISA", new Font("Segoe UI", 8.5f, FontStyle.Bold), Durazno, new Padding(0, 0, 0, 6)));
            estado = Etiqueta("Revisando…", Font, Crema, new Padding(0, 0, 0, 8));
            raiz.Controls.Add(estado);

            lista = new FlowLayoutPanel
            {
                FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoScroll = true,
                Width = Ancho + 4, Height = 10, BackColor = Noche, Margin = new Padding(0),
            };
            raiz.Controls.Add(lista);

            var fila = new FlowLayoutPanel { FlowDirection = FlowDirection.LeftToRight, AutoSize = true, Margin = new Padding(0, 10, 0, 0), BackColor = Noche };
            btnReemplazar = Boton("Reemplazar en el texto", true);
            btnCopiar = Boton("Copiar corregido", false);
            var btnCerrar = Boton("Cerrar  (Esc)", false);
            btnReemplazar.Click += delegate { var h = Reemplazar; var t = Corregido(); Close(); if (h != null) h(t); };
            btnCopiar.Click += delegate { Clipboard.SetText(Corregido()); btnCopiar.Text = "Copiado ✓"; };
            btnCerrar.Click += delegate { Close(); };
            fila.Controls.Add(btnReemplazar);
            fila.Controls.Add(btnCopiar);
            fila.Controls.Add(btnCerrar);
            raiz.Controls.Add(fila);

            var pie = new LinkLabel
            {
                Text = "Revisado con LanguageTool", AutoSize = true, LinkColor = Durazno, ActiveLinkColor = Crema,
                ForeColor = Gris, Font = new Font("Segoe UI", 8.5f), Margin = new Padding(0, 8, 0, 0), BackColor = Noche,
            };
            pie.LinkArea = new LinkArea(13, 12);
            pie.LinkClicked += delegate { try { Process.Start("https://languagetool.org"); } catch (Exception) { } };
            raiz.Controls.Add(pie);
            Controls.Add(raiz);

            KeyDown += (s, e) => { if (e.KeyCode == Keys.Escape) Close(); };
            ActualizarBotones();
            btnReemplazar.Visible = btnCopiar.Visible = false;
        }

        public void Estado(string mensaje)
        {
            estado.Text = mensaje;
            Ajustar();
        }

        public void Mostrar(string textoRevisado, List<Explicacion> explicaciones, string idioma, bool puedeReemplazar)
        {
            texto = textoRevisado;
            items = explicaciones;
            lista.SuspendLayout();
            lista.Controls.Clear();
            if (items.Count == 0)
            {
                estado.Text = "✓  No encontré errores en el texto." + (idioma != null ? "  ·  " + idioma : "");
                estado.ForeColor = Durazno;
            }
            else
            {
                estado.Text = items.Count + (items.Count == 1 ? " cosa para revisar" : " cosas para revisar") + (idioma != null ? "  ·  " + idioma : "");
                for (int i = 0; i < items.Count; i++) lista.Controls.Add(Tarjeta(i));
            }
            lista.ResumeLayout();
            btnReemplazar.Visible = puedeReemplazar && items.Count > 0;
            btnCopiar.Visible = items.Count > 0;
            ActualizarBotones();
            Ajustar();
        }

        string Corregido()
        {
            return Explicador.Aplicar(texto, elegidos.Select(kv => new Cambio { Offset = items[kv.Key].Offset, Length = items[kv.Key].Length, Valor = kv.Value }));
        }

        void ActualizarBotones()
        {
            btnReemplazar.Enabled = btnCopiar.Enabled = elegidos.Count > 0;
        }

        Control Tarjeta(int indice)
        {
            var e = items[indice];
            var t = new FlowLayoutPanel
            {
                FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoSize = true, BackColor = NocheClara,
                Padding = new Padding(12, 10, 12, 10), Margin = new Padding(0, 0, 0, 8), Width = Ancho - 20,
            };
            var chip = Etiqueta(" " + e.Titulo + " ", new Font("Segoe UI", 8.5f, FontStyle.Bold), Noche, new Padding(0, 0, 0, 6));
            chip.BackColor = Durazno;
            t.Controls.Add(chip);

            // fragmento con el error resaltado
            int ini = Math.Max(0, e.Offset - 30), fin = Math.Min(texto.Length, e.Offset + e.Length + 30);
            var antes = (ini > 0 ? "…" : "") + texto.Substring(ini, e.Offset - ini);
            var error = e.Length > 0 ? texto.Substring(e.Offset, e.Length) : "␣";
            var despues = texto.Substring(e.Offset + e.Length, fin - e.Offset - e.Length) + (fin < texto.Length ? "…" : "");
            var frag = new Fragmento(Limpio(antes), Limpio(error), Limpio(despues), new Font("Segoe UI", 10.5f), Ancho - 50)
            {
                BackColor = NocheClara, ForeColor = Color.White, Margin = new Padding(0, 0, 0, 4),
            };
            t.Controls.Add(frag);

            t.Controls.Add(Etiqueta("Por qué: " + e.Porque, Font, Crema, new Padding(0, 0, 0, 4), Ancho - 50));
            if (e.Mensaje.Length > 0)
                t.Controls.Add(Etiqueta("LanguageTool: " + e.Mensaje, new Font("Segoe UI", 8.5f), Gris, new Padding(0, 0, 0, 6), Ancho - 50));

            if (e.Sugerencias.Count > 0)
            {
                var fila = new FlowLayoutPanel { FlowDirection = FlowDirection.LeftToRight, AutoSize = true, MaximumSize = new Size(Ancho - 50, 0), BackColor = NocheClara, Margin = new Padding(0) };
                Button activo = null;
                foreach (var s in e.Sugerencias.Take(5))
                {
                    var valor = s;
                    var b = Boton(valor.Length == 0 ? "(quitar)" : valor, false);
                    b.BackColor = NocheClara;
                    b.Click += delegate
                    {
                        if (activo == b) { Pintar(b, false); activo = null; elegidos.Remove(indice); }
                        else
                        {
                            if (activo != null) Pintar(activo, false);
                            Pintar(b, true); activo = b; elegidos[indice] = valor;
                        }
                        ActualizarBotones();
                    };
                    fila.Controls.Add(b);
                }
                t.Controls.Add(fila);
            }
            return t;
        }

        static string Limpio(string s) { return s.Replace("\r", " ").Replace("\n", " "); }

        static void Pintar(Button b, bool elegido)
        {
            b.BackColor = elegido ? Durazno : NocheClara;
            b.ForeColor = elegido ? Noche : Durazno;
        }

        /// <summary>Alto según el contenido (hasta el 70 % de la pantalla) y centrado donde está el mouse.</summary>
        void Ajustar()
        {
            var area = Screen.FromPoint(Cursor.Position).WorkingArea;
            int altoLista = 0;
            foreach (Control c in lista.Controls) altoLista += c.PreferredSize.Height + c.Margin.Vertical;
            int maxLista = (int)(area.Height * 0.7) - 160;
            lista.Height = Math.Max(0, Math.Min(altoLista + 2, maxLista));
            ClientSize = new Size(Ancho + 44, lista.Height + 150 + (estado.PreferredHeight > 24 ? estado.PreferredHeight - 18 : 0));
            Location = new Point(area.Left + (area.Width - Width) / 2, area.Top + Math.Max(10, (area.Height - Height) / 3));
        }

        Label Etiqueta(string t, Font f, Color c, Padding m, int ancho = Ancho)
        {
            return new Label { Text = t, Font = f, ForeColor = c, BackColor = Color.Transparent, AutoSize = true, MaximumSize = new Size(ancho, 0), Margin = m, UseMnemonic = false };
        }

        Button Boton(string t, bool principal)
        {
            var b = new Button
            {
                Text = t, AutoSize = true, FlatStyle = FlatStyle.Flat, Cursor = Cursors.Hand, UseMnemonic = false,
                BackColor = principal ? Durazno : Noche, ForeColor = principal ? Noche : Durazno,
                Padding = new Padding(8, 2, 8, 2), Margin = new Padding(0, 0, 8, 6),
            };
            b.FlatAppearance.BorderColor = Durazno;
            return b;
        }
    }

    /// <summary>Texto que se acomoda en varias líneas con el error resaltado (dibujado a mano, igual en todos los sistemas).</summary>
    public class Fragmento : Control
    {
        readonly List<KeyValuePair<string, bool>> trozos = new List<KeyValuePair<string, bool>>();
        readonly int ancho;

        public Fragmento(string antes, string error, string despues, Font fuente, int ancho)
        {
            this.ancho = ancho;
            Font = fuente;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint, true);
            Agregar(antes, false);
            Agregar(error, true);
            Agregar(despues, false);
            Size = new Size(ancho, Acomodar(null));
        }

        void Agregar(string s, bool esError)
        {
            // separa en palabras conservando los espacios, para poder cortar línea entre ellas
            int i = 0;
            while (i < s.Length)
            {
                int j = i;
                while (j < s.Length && s[j] != ' ') j++;
                while (j < s.Length && s[j] == ' ') j++;
                trozos.Add(new KeyValuePair<string, bool>(s.Substring(i, j - i), esError));
                i = j;
            }
        }

        /// <summary>Calcula (y si hay Graphics, dibuja) el texto; devuelve el alto usado.</summary>
        int Acomodar(Graphics g)
        {
            const TextFormatFlags f = TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix;
            int x = 0, y = 0, alto = TextRenderer.MeasureText("Ag", Font, Size.Empty, f).Height;
            using (var resaltado = new SolidBrush(VentanaRevision.Rojo))
            using (var negrita = new Font(Font, FontStyle.Bold))
            {
                // MeasureText ignora los espacios del final: el ancho del espacio se mide aparte.
                int espacio = TextRenderer.MeasureText("a a", Font, Size.Empty, f).Width - TextRenderer.MeasureText("aa", Font, Size.Empty, f).Width;
                foreach (var t in trozos)
                {
                    var fuente = t.Value ? negrita : Font;
                    var palabra = t.Key.TrimEnd(' ');
                    int espacios = t.Key.Length - palabra.Length;
                    int wPalabra = palabra.Length > 0 ? TextRenderer.MeasureText(palabra, fuente, Size.Empty, f).Width : 0;
                    if (x > 0 && x + wPalabra > ancho) { x = 0; y += alto + 2; }
                    if (g != null && palabra.Length > 0)
                    {
                        if (t.Value) g.FillRectangle(resaltado, x - 1, y, wPalabra + 2, alto);
                        TextRenderer.DrawText(g, palabra, fuente, new Point(x, y), ForeColor, f);
                    }
                    x += wPalabra + espacios * espacio;
                }
            }
            return y + alto + 2;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.Clear(BackColor);
            Acomodar(e.Graphics);
        }
    }
}
