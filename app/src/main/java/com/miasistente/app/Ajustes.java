package com.miasistente.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

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

    /** Ciudad del usuario para el clima. */
    public static String ciudad(Context c) { return p(c).getString("ciudad", "").trim(); }

    public static void ciudad(Context c, String ciudad) {
        p(c).edit().putString("ciudad", ciudad.trim()).apply();
    }

    /** Datos que el usuario pidio recordar; se le dan al modelo en cada conversacion. */
    public static synchronized JSONArray memoria(Context c) {
        try {
            return new JSONArray(p(c).getString("memoria", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static synchronized JSONObject recordar(Context c, String dato) throws JSONException {
        if (dato == null || dato.trim().isEmpty()) return new JSONObject().put("error", "No hay nada que recordar.");
        JSONArray lista = memoria(c);
        if (lista.length() >= 60) return new JSONObject().put("error", "Ya tengo demasiados datos guardados; pide borrar alguno.");
        lista.put(dato.trim());
        p(c).edit().putString("memoria", lista.toString()).apply();
        return new JSONObject().put("guardado", dato.trim());
    }

    public static synchronized JSONObject olvidar(Context c, String texto) throws JSONException {
        String buscado = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        if (buscado.isEmpty()) return new JSONObject().put("error", "Dime que dato borrar.");
        JSONArray lista = memoria(c);
        JSONArray quedan = new JSONArray();
        JSONArray borrados = new JSONArray();
        for (int i = 0; i < lista.length(); i++) {
            String d = lista.optString(i);
            if (d.toLowerCase(Locale.ROOT).contains(buscado)) borrados.put(d);
            else quedan.put(d);
        }
        p(c).edit().putString("memoria", quedan.toString()).apply();
        if (borrados.length() == 0) return new JSONObject().put("error", "No encontre ese dato.");
        return new JSONObject().put("borrados", borrados);
    }

    public static synchronized void borrarMemoria(Context c) {
        p(c).edit().putString("memoria", "[]").apply();
    }

    /** Clave opcional de Groq, solo para buscar en internet cuando el modelo principal es Gemini. */
    public static String claveBusqueda(Context c) { return p(c).getString("clave_busqueda", "").trim(); }

    public static void claveBusqueda(Context c, String clave) {
        p(c).edit().putString("clave_busqueda", clave.trim()).apply();
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
