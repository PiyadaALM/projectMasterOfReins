using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class MasterOfReinsLauncher
{
    [STAThread]
    private static void Main()
    {
        string folder = AppDomain.CurrentDomain.BaseDirectory;
        string jar = Path.Combine(folder, "MasterOfReins.jar");

        if (!File.Exists(jar))
        {
            MessageBox.Show(
                "MasterOfReins.jar was not found next to this launcher.\n\n" +
                "Keep MasterOfReins.exe, MasterOfReins.jar, assets, and asset-packs in the same folder.",
                "Master of Reins",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
            return;
        }

        string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        string[] candidates = new string[3];
        int count = 0;
        if (!string.IsNullOrWhiteSpace(javaHome))
        {
            candidates[count++] = Path.Combine(javaHome.Trim(), "bin", "javaw.exe");
        }
        candidates[count++] = "javaw.exe";
        candidates[count++] = "java.exe";

        Exception lastError = null;
        for (int i = 0; i < count; i++)
        {
            try
            {
                var start = new ProcessStartInfo
                {
                    FileName = candidates[i],
                    Arguments = "-jar \"" + jar + "\"",
                    WorkingDirectory = folder,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };
                Process.Start(start);
                return;
            }
            catch (Exception error)
            {
                lastError = error;
            }
        }

        MessageBox.Show(
            "Java 17 or newer was not found.\n\n" +
            "Install Java 17+ and try again.\n\n" +
            (lastError == null ? "" : lastError.Message),
            "Master of Reins",
            MessageBoxButtons.OK,
            MessageBoxIcon.Error);
    }
}
