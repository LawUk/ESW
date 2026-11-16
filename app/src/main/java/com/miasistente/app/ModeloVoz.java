package com.miasistente.app;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Descarga una sola vez el modelo de voz en espanol que reconoce el nombre de activacion (unos 40 MB). */
public class ModeloVoz {
    static final String URL_MODELO = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip";

    public interface Progreso {
        void aviso(String texto);
    }

    static File preparar(Context c, Progreso progreso) throws IOException {
        File listo = new File(c.getFilesDir(), "modelo-es");
        if (new File(listo, "am").isDirectory()) return listo;

        File temporal = new File(c.getFilesDir(), "modelo-es-tmp");
        borrar(temporal);
        borrar(listo);
        if (!temporal.mkdirs()) throw new IOException("No pude crear la carpeta del modelo");
        String raiz = temporal.getCanonicalPath() + File.separator;

        HttpURLConnection con = (HttpURLConnection) new URL(URL_MODELO).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(60000);
        try {
            if (con.getResponseCode() != 200) throw new IOException("Descarga rechazada: " + con.getResponseCode());
            long leido = 0;
            long siguienteAviso = 0;
            byte[] bufer = new byte[65536];
            try (InputStream in = new BufferedInputStream(con.getInputStream());
                 ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    String nombre = e.getName();
                    int corte = nombre.indexOf('/');
                    if (corte < 0 || corte == nombre.length() - 1) continue; // la carpeta principal
                    File destino = new File(temporal, nombre.substring(corte + 1));
                    if (!destino.getCanonicalPath().startsWith(raiz)) throw new IOException("Archivo sospechoso en el zip");
                    if (e.isDirectory()) {
                        destino.mkdirs();
                        continue;
                    }
                    File carpeta = destino.getParentFile();
                    if (carpeta != null) carpeta.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(destino)) {
                        int n;
                        while ((n = zip.read(bufer)) > 0) {
                            out.write(bufer, 0, n);
                            leido += n;
                            if (leido >= siguienteAviso) {
                                progreso.aviso("Descargando el modelo de voz: " + (leido / 1048576) + " MB");
                                siguienteAviso = leido + 8L * 1048576;
                            }
                        }
                    }
                }
            }
        } finally {
            con.disconnect();
        }
        if (!new File(temporal, "am").isDirectory()) throw new IOException("El modelo descargado esta incompleto");
        if (!temporal.renameTo(listo)) throw new IOException("No pude guardar el modelo");
        return listo;
    }

    private static void borrar(File f) {
        if (!f.exists()) return;
        File[] hijos = f.listFiles();
        if (hijos != null) for (File h : hijos) borrar(h);
        f.delete();
    }
}
