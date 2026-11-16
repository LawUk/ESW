package com.miasistente.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Pendientes y recordatorios, guardados solo en el telefono. */
public class Pendientes {

    private static JSONArray cargar(Context c) {
        try {
            return new JSONArray(Ajustes.p(c).getString("pendientes", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void guardar(Context c, JSONArray lista) {
        Ajustes.p(c).edit().putString("pendientes", lista.toString()).apply();
    }

    static Date fecha(String texto) {
        if (texto == null || texto.trim().isEmpty()) return null;
        String t = texto.trim().replace('T', ' ');
        String[] formatos = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"};
        for (String f : formatos) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(f, Locale.US);
                sdf.setLenient(false);
                return sdf.parse(t.length() > f.length() ? t.substring(0, f.length()) : t);
            } catch (ParseException ignorada) {
                // probar el siguiente formato
            }
        }
        return null;
    }

    public static synchronized JSONObject agregar(Context c, String texto, String fechaHora) throws JSONException {
        Date cuando = fecha(fechaHora);
        if (fechaHora != null && !fechaHora.trim().isEmpty() && cuando == null) {
            return new JSONObject().put("error", "No entendi la fecha. Usa el formato AAAA-MM-DD HH:MM.");
        }
        JSONArray lista = cargar(c);
        int id = 1;
        for (int i = 0; i < lista.length(); i++) id = Math.max(id, lista.getJSONObject(i).optInt("id") + 1);
        JSONObject nuevo = new JSONObject().put("id", id).put("texto", texto).put("hecho", false);
        if (cuando != null) {
            nuevo.put("cuando", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(cuando));
            programar(c, id, texto, cuando.getTime());
        }
        lista.put(nuevo);
        guardar(c, lista);
        return new JSONObject().put("guardado", nuevo);
    }

    public static synchronized JSONObject listar(Context c, boolean incluirHechos) throws JSONException {
        JSONArray lista = cargar(c);
        JSONArray visibles = new JSONArray();
        for (int i = 0; i < lista.length(); i++) {
            JSONObject p = lista.getJSONObject(i);
            if (incluirHechos || !p.optBoolean("hecho")) visibles.put(p);
        }
        return new JSONObject().put("pendientes", visibles).put("total", visibles.length());
    }

    public static synchronized JSONObject completar(Context c, int id, String texto) throws JSONException {
        JSONArray lista = cargar(c);
        JSONArray marcados = new JSONArray();
        String buscado = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < lista.length(); i++) {
            JSONObject p = lista.getJSONObject(i);
            if (p.optBoolean("hecho")) continue;
            boolean porId = id > 0 && p.optInt("id") == id;
            boolean porTexto = !buscado.isEmpty() && p.optString("texto").toLowerCase(Locale.ROOT).contains(buscado);
            if (porId || porTexto) {
                p.put("hecho", true);
                marcados.put(p.optString("texto"));
                cancelar(c, p.optInt("id"));
            }
        }
        guardar(c, lista);
        if (marcados.length() == 0) return new JSONObject().put("error", "No encontre ese pendiente.");
        return new JSONObject().put("completados", marcados);
    }

    private static PendingIntent aviso(Context c, int id, String texto) {
        Intent i = new Intent(c, AvisoReceiver.class).putExtra("id", id).putExtra("texto", texto);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void programar(Context c, int id, String texto, long momento) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = aviso(c, id, texto);
        boolean exacta = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exacta) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pi);
        }
    }

    private static void cancelar(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(aviso(c, id, ""));
    }
}
