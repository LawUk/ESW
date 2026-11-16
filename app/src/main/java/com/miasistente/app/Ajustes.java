package com.miasistente.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferencias guardadas en el telefono. */
public class Ajustes {
    public static final String BASE_GROQ = "https://api.groq.com/openai/v1";
    public static final String MODELO_GROQ = "llama-3.3-70b-versatile";
    public static final String BASE_GEMINI = "https://generativelanguage.googleapis.com/v1beta/openai";
    public static final String MODELO_GEMINI = "gemini-flash-latest";

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("ajustes", Context.MODE_PRIVATE);
    }

    public static String base(Context c) {
        String b = p(c).getString("base", BASE_GROQ).trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    public static String clave(Context c) { return p(c).getString("clave", "").trim(); }

    public static String modelo(Context c) { return p(c).getString("modelo", MODELO_GROQ).trim(); }

    /** Nombre de activacion. Admite variantes separadas por coma: "luna, la luna". */
    public static String nombres(Context c) { return p(c).getString("nombre", "Luna").trim(); }

    public static String nombre(Context c) {
        String n = nombres(c).split(",")[0].trim();
        return n.isEmpty() ? "Luna" : n;
    }

    public static boolean despertar(Context c) { return p(c).getBoolean("despertar", true); }

    public static void guardar(Context c, String base, String clave, String modelo, String nombre) {
        p(c).edit().putString("base", base.trim()).putString("clave", clave.trim())
                .putString("modelo", modelo.trim()).putString("nombre", nombre.trim()).apply();
    }

    public static void despertar(Context c, boolean activo) {
        p(c).edit().putBoolean("despertar", activo).apply();
    }
}
