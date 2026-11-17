package com.miasistente.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;

import org.json.JSONObject;

/** Hace sonar las alarmas y temporizadores con el sonido de alarma del telefono, hasta que se detienen. */
public class AlarmaReceiver extends BroadcastReceiver {
    static final String SONAR = "com.miasistente.app.SONAR";
    static final String DETENER = "com.miasistente.app.DETENER";
    private static final String CANAL = "alarmas_sonando";

    @Override
    public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id", 0);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (DETENER.equals(intent.getAction())) {
            nm.cancel(3000 + id);
            return;
        }

        JSONObject datos = Alarmas.quitar(c, id);
        String etiqueta = datos == null ? "" : datos.optString("etiqueta", "");
        boolean esTemporizador = datos != null && "temporizador".equals(datos.optString("tipo"));
        String titulo = esTemporizador ? "Temporizador terminado" : "Alarma";
        String texto = !etiqueta.isEmpty() ? etiqueta : (esTemporizador ? "Se acabo el tiempo" : "Es hora");

        NotificationChannel canal = new NotificationChannel(CANAL, "Alarmas y temporizadores",
                NotificationManager.IMPORTANCE_HIGH);
        Uri sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sonido == null) sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        canal.setSound(sonido, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        canal.enableVibration(true);
        canal.setVibrationPattern(new long[]{0, 800, 400, 800});
        nm.createNotificationChannel(canal);

        int banderas = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent detener = PendingIntent.getBroadcast(c, 60000 + id,
                new Intent(c, AlarmaReceiver.class).setAction(DETENER).putExtra("id", id), banderas);
        PendingIntent abrir = PendingIntent.getActivity(c, 61000 + id, new Intent(c, MainActivity.class), banderas);

        Notification n = new Notification.Builder(c, CANAL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(abrir)
                .setDeleteIntent(detener)
                .setAutoCancel(true)
                .setFullScreenIntent(abrir, true)
                .addAction(new Notification.Action.Builder(null, "Detener", detener).build())
                .build();
        n.flags |= Notification.FLAG_INSISTENT; // repite el sonido hasta que se toque o se deslice

        try {
            nm.notify(3000 + id, n);
        } catch (SecurityException ignorada) {
            // sin permiso de notificaciones no puede sonar; queda al menos el aviso por voz
        }
        AsistenteService servicio = AsistenteService.instancia;
        if (servicio != null) servicio.decir(titulo + (etiqueta.isEmpty() ? "" : ": " + etiqueta));
    }
}
