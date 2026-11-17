package com.miasistente.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Al encender el telefono o actualizar la app, vuelve a programar alarmas y recordatorios. */
public class ArranqueReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String accion = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(accion) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(accion)) {
            Alarmas.reprogramar(c);
            Pendientes.reprogramar(c);
        }
    }
}
