// Punto de entrada de Sacerdotisa.exe (lo compila Sacerdotisa.ps1 con el csc.exe que trae Windows).
//   Sacerdotisa.exe                         → queda junto al reloj, atajo Ctrl+Alt+Win+E
//   Sacerdotisa.exe --prueba resp.json t.txt → muestra la ventana con una respuesta guardada (sin internet)

using System;
using System.IO;
using System.Text;
using System.Windows.Forms;

namespace Sacerdotisa
{
    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            try
            {
                if (args.Length == 3 && args[0] == "--prueba")
                {
                    Application.EnableVisualStyles();
                    App.CargarDatos(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "explicaciones.json"));
                    var texto = File.ReadAllText(args[2], Encoding.UTF8);
                    var r = Json.Leer<RespuestaLT>(File.ReadAllText(args[1], Encoding.UTF8));
                    var v = new VentanaRevision();
                    v.Shown += delegate { App.MostrarResultado(v, texto, r, true); };
                    Application.Run(v);
                    return;
                }
                string datos = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Sacerdotisa");
                App.Run(datos);
            }
            catch (Exception e)
            {
                MessageBox.Show("La Sacerdotisa se cerró por un error:\n\n" + e, "La Sacerdotisa", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
