package com.miasistente.app;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Da acceso a las notificaciones del telefono (el usuario debe activarlo en Ajustes). */
public class Notis extends NotificationListenerService {
    static volatile Notis instancia;

    @Override
    public void onListenerConnected() {
        instancia = this;
    }

    @Override
    public void onListenerDisconnected() {
        instancia = null;
    }

    static JSONObject leer() throws JSONException {
        Notis yo = instancia;
        if (yo == null) {
            return new JSONObject().put("error",
                    "La lectura de notificaciones no esta disponible en esta version de la app.");
        }
        StatusBarNotification[] activas;
        try {
            activas = yo.getActiveNotifications();
        } catch (RuntimeException e) {
            return new JSONObject().put("error", "Android no entrego las notificaciones: " + e.getMessage());
        }
        JSONArray lista = new JSONArray();
        PackageManager pm = yo.getPackageManager();
        if (activas != null) {
            for (StatusBarNotification s : activas) {
                Notification n = s.getNotification();
                if (n == null || s.isOngoing() || yo.getPackageName().equals(s.getPackageName())) continue;
                if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                Bundle extras = n.extras;
                CharSequence titulo = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence texto = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
                if (titulo == null && texto == null) continue;
                String app = s.getPackageName();
                try {
                    app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(s.getPackageName(), 0)));
                } catch (PackageManager.NameNotFoundException ignorada) {
                    // se queda el nombre del paquete
                }
                lista.put(new JSONObject()
                        .put("app", app)
                        .put("titulo", recortar(titulo, 80))
                        .put("contenido", recortar(texto, 200)));
                if (lista.length() >= 25) break;
            }
        }
        return new JSONObject().put("notificaciones", lista).put("total", lista.length());
    }

    private static String recortar(CharSequence t, int max) {
        if (t == null) return "";
        String s = t.toString();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
