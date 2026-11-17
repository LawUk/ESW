package com.miasistente.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Alarmas y temporizadores propios de la app. No dependen de la app de reloj del telefono,
 * que en varias marcas bloquea que otras apps creen alarmas.
 */
public class Alarmas {
    private static final String CLAVE = "alarmas";
    private static final Locale MEXICO = new Locale("es", "MX");

    private static JSONArray cargar(Context c) {
        try {
            return new JSONArray(Ajustes.p(c).getString(CLAVE, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void guardar(Context c, JSONArray lista) {
        Ajustes.p(c).edit().putString(CLAVE, lista.toString()).apply();
    }

    /** "martes 17 de noviembre, 7:30 a. m." */
    static String paraLeer(long momento) {
        return new SimpleDateFormat("EEEE d 'de' MMMM, h:mm a", MEXICO).format(new Date(momento));
    }

    public static synchronized JSONObject alarma(Context c, int hora, int minutos, String fecha, String etiqueta)
            throws JSONException {
        if (hora < 0 || hora > 23 || minutos < 0 || minutos > 59) {
            return new JSONObject().put("error", "Hora invalida. Usa hora de 0 a 23 y minutos de 0 a 59.");
        }
        Calendar cal = Calendar.getInstance();
        boolean conFecha = fecha != null && !fecha.trim().isEmpty();
        if (conFecha) {
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                f.setLenient(false);
                Date dia = f.parse(fecha.trim());
                if (dia == null) throw new ParseException(fecha, 0);
                cal.setTime(dia);
            } catch (ParseException e) {
                return new JSONObject().put("error", "No entendi la fecha. Usa el formato AAAA-MM-DD.");
            }
        }
        cal.set(Calendar.HOUR_OF_DAY, hora);
        cal.set(Calendar.MINUTE, minutos);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long ahora = System.currentTimeMillis();
        if (cal.getTimeInMillis() <= ahora) {
            if (conFecha) return new JSONObject().put("error", "Esa fecha y hora ya pasaron.");
            cal.add(Calendar.DAY_OF_MONTH, 1); // la proxima vez que el reloj marque esa hora
        }
        return crear(c, cal.getTimeInMillis(), etiqueta, "alarma");
    }

    public static synchronized JSONObject temporizador(Context c, int segundos, String etiqueta) throws JSONException {
        if (segundos < 1 || segundos > 86400) {
            return new JSONObject().put("error", "El temporizador debe durar entre 1 segundo y 24 horas.");
        }
        return crear(c, System.currentTimeMillis() + segundos * 1000L, etiqueta, "temporizador");
    }

    private static JSONObject crear(Context c, long momento, String etiqueta, String tipo) throws JSONException {
        JSONArray lista = cargar(c);
        int id = 1;
        for (int i = 0; i < lista.length(); i++) id = Math.max(id, lista.getJSONObject(i).optInt("id") + 1);
        String texto = etiqueta == null ? "" : etiqueta.trim();
        lista.put(new JSONObject().put("id", id).put("cuando", momento).put("etiqueta", texto).put("tipo", tipo));
        guardar(c, lista);
        boolean exacta = programar(c, id, momento);
        JSONObject r = new JSONObject().put("ok", true).put("id", id).put("tipo", tipo)
                .put("suena", paraLeer(momento));
        if (!texto.isEmpty()) r.put("etiqueta", texto);
        if (!exacta) r.put("nota", "Android no permitio la alarma exacta: puede sonar unos minutos tarde.");
        return r;
    }

    public static synchronized JSONObject listar(Context c) throws JSONException {
        JSONArray lista = cargar(c);
        JSONArray salida = new JSONArray();
        for (int i = 0; i < lista.length(); i++) {
            JSONObject a = lista.getJSONObject(i);
            salida.put(new JSONObject().put("id", a.optInt("id")).put("tipo", a.optString("tipo"))
                    .put("etiqueta", a.optString("etiqueta")).put("suena", paraLeer(a.optLong("cuando"))));
        }
        return new JSONObject().put("alarmas", salida).put("total", salida.length());
    }

    public static synchronized JSONObject cancelar(Context c, int id, String etiqueta, boolean todas) throws JSONException {
        JSONArray lista = cargar(c);
        JSONArray quedan = new JSONArray();
        JSONArray canceladas = new JSONArray();
        String buscado = etiqueta == null ? "" : etiqueta.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < lista.length(); i++) {
            JSONObject a = lista.getJSONObject(i);
            boolean coincide = todas
                    || (id > 0 && a.optInt("id") == id)
                    || (!buscado.isEmpty() && a.optString("etiqueta").toLowerCase(Locale.ROOT).contains(buscado));
            if (coincide) {
                desprogramar(c, a.optInt("id"));
                canceladas.put(paraLeer(a.optLong("cuando")));
            } else {
                quedan.put(a);
            }
        }
        guardar(c, quedan);
        if (canceladas.length() == 0) return new JSONObject().put("error", "No encontre esa alarma.");
        return new JSONObject().put("canceladas", canceladas);
    }

    /** La usa el receptor cuando suena: la saca de la lista y devuelve sus datos. */
    static synchronized JSONObject quitar(Context c, int id) {
        JSONArray lista = cargar(c);
        JSONArray quedan = new JSONArray();
        JSONObject encontrada = null;
        for (int i = 0; i < lista.length(); i++) {
            JSONObject a = lista.optJSONObject(i);
            if (a == null) continue;
            if (a.optInt("id") == id) encontrada = a;
            else quedan.put(a);
        }
        guardar(c, quedan);
        return encontrada;
    }

    /** Despues de reiniciar el telefono, Android olvida las alarmas: aqui se vuelven a programar. */
    static synchronized void reprogramar(Context c) {
        JSONArray lista = cargar(c);
        JSONArray quedan = new JSONArray();
        long ahora = System.currentTimeMillis();
        for (int i = 0; i < lista.length(); i++) {
            JSONObject a = lista.optJSONObject(i);
            if (a == null || a.optLong("cuando") <= ahora) continue; // ya paso mientras estaba apagado
            programar(c, a.optInt("id"), a.optLong("cuando"));
            quedan.put(a);
        }
        guardar(c, quedan);
    }

    private static PendingIntent intento(Context c, int id) {
        Intent i = new Intent(c, AlarmaReceiver.class).setAction(AlarmaReceiver.SONAR).putExtra("id", id);
        return PendingIntent.getBroadcast(c, 50000 + id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Devuelve true si quedo como alarma exacta. */
    private static boolean programar(Context c, int id, long momento) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        PendingIntent sonar = intento(c, id);
        PendingIntent mostrar = PendingIntent.getActivity(c, 49999, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(momento, mostrar), sonar);
            return true;
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, sonar);
            return false;
        }
    }

    private static void desprogramar(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(intento(c, id));
    }
}
