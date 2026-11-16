package com.miasistente.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Se ejecuta cuando llega la hora de un recordatorio. */
public class AvisoReceiver extends BroadcastReceiver {
    static final String CANAL = "recordatorios";

    @Override
    public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id", 0);
        String texto = intent.getStringExtra("texto");
        if (texto == null) texto = "Tienes un recordatorio";

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(
                    new NotificationChannel(CANAL, "Recordatorios", NotificationManager.IMPORTANCE_HIGH));
            PendingIntent abrir = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CANAL)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Recordatorio")
                    .setContentText(texto)
                    .setContentIntent(abrir)
                    .setAutoCancel(true)
                    .build();
            try {
                nm.notify(1000 + id, n);
            } catch (SecurityException ignorada) {
                // sin permiso de notificaciones: al menos se intenta avisar por voz
            }
        }
        AsistenteService servicio = AsistenteService.instancia;
        if (servicio != null) servicio.decir("Recordatorio: " + texto);
    }
}
