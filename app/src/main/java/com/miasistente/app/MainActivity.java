package com.miasistente.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Pantalla principal: conversacion, boton para hablar y ajustes. */
public class MainActivity extends Activity implements AsistenteService.Pantalla {
    static volatile boolean visible;

    private TextView estado;
    private TextView registro;
    private ScrollView scroll;
    private EditText entrada;

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setFitsSystemWindows(true);
        raiz.setPadding(dp(14), dp(10), dp(14), dp(10));

        estado = new TextView(this);
        estado.setTextSize(16);
        estado.setPadding(0, dp(6), 0, dp(6));
        raiz.addView(estado);

        scroll = new ScrollView(this);
        registro = new TextView(this);
        registro.setTextSize(16);
        registro.setTextIsSelectable(true);
        scroll.addView(registro);
        raiz.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout filaTexto = new LinearLayout(this);
        filaTexto.setGravity(Gravity.CENTER_VERTICAL);
        entrada = new EditText(this);
        entrada.setHint("Escribe una pregunta");
        entrada.setSingleLine(true);
        entrada.setImeOptions(EditorInfo.IME_ACTION_SEND);
        entrada.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_SEND) {
                enviarTexto();
                return true;
            }
            return false;
        });
        filaTexto.addView(entrada, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button enviar = new Button(this);
        enviar.setText("Enviar");
        enviar.setOnClickListener(v -> enviarTexto());
        filaTexto.addView(enviar);
        raiz.addView(filaTexto);

        Button hablar = new Button(this);
        hablar.setText("Hablar");
        hablar.setTextSize(18);
        hablar.setOnClickListener(v -> mandar("HABLAR", null));
        raiz.addView(hablar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        LinearLayout filaAbajo = new LinearLayout(this);
        filaAbajo.setGravity(Gravity.CENTER_VERTICAL);
        Switch porNombre = new Switch(this);
        porNombre.setText("Activar por nombre  ");
        porNombre.setChecked(Ajustes.despertar(this));
        porNombre.setOnCheckedChangeListener((boton, activo) -> {
            Ajustes.despertar(this, activo);
            mandar("RECARGAR", null);
        });
        filaAbajo.addView(porNombre, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button ajustes = new Button(this);
        ajustes.setText("Ajustes");
        ajustes.setOnClickListener(v -> mostrarAjustes());
        filaAbajo.addView(ajustes);
        raiz.addView(filaAbajo);

        setContentView(raiz);
        pedirPermisos();
        if (Ajustes.clave(this).isEmpty()) mostrarAjustes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        AsistenteService.pantalla = this;
        registro.setText(AsistenteService.registro());
        estado.setText(AsistenteService.ultimoEstado);
        bajar();
    }

    @Override
    protected void onPause() {
        visible = false;
        AsistenteService.pantalla = null;
        super.onPause();
    }

    @Override
    public void linea(String texto) {
        runOnUiThread(() -> {
            registro.append(texto + "\n\n");
            bajar();
        });
    }

    @Override
    public void estado(String texto) {
        runOnUiThread(() -> estado.setText(texto));
    }

    private void bajar() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // -------------------------------------------------------------- permisos

    private boolean tieneMicrofono() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void pedirPermisos() {
        List<String> faltan = new ArrayList<>();
        if (!tieneMicrofono()) faltan.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            faltan.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (faltan.isEmpty()) mandar("INICIAR", null);
        else requestPermissions(faltan.toArray(new String[0]), 1);
    }

    @Override
    public void onRequestPermissionsResult(int codigo, String[] permisos, int[] resultados) {
        super.onRequestPermissionsResult(codigo, permisos, resultados);
        if (tieneMicrofono()) mandar("INICIAR", null);
        else estado.setText("Sin permiso de microfono no puedo escucharte.");
    }

    // ------------------------------------------------------------- acciones

    private void mandar(String accion, String texto) {
        if (!tieneMicrofono()) {
            Toast.makeText(this, "Primero permite el microfono", Toast.LENGTH_SHORT).show();
            pedirPermisos();
            return;
        }
        Intent i = new Intent(this, AsistenteService.class).setAction(accion);
        if (texto != null) i.putExtra("texto", texto);
        try {
            startForegroundService(i);
        } catch (RuntimeException e) {
            estado.setText("No pude iniciar el asistente: " + e.getMessage());
        }
    }

    private void enviarTexto() {
        String texto = entrada.getText().toString().trim();
        if (texto.isEmpty()) return;
        entrada.setText("");
        mandar("TEXTO", texto);
    }

    private void abrir(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Tu telefono no tiene esa pantalla de ajustes", Toast.LENGTH_SHORT).show();
        }
    }

    // --------------------------------------------------------------- ajustes

    private EditText campo(LinearLayout padre, String titulo, String valor, boolean secreto) {
        TextView etiqueta = new TextView(this);
        etiqueta.setText(titulo);
        etiqueta.setPadding(0, dp(10), 0, 0);
        padre.addView(etiqueta);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(valor);
        e.setInputType(secreto
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        padre.addView(e);
        return e;
    }

    private Button boton(LinearLayout padre, String texto, View.OnClickListener alTocar) {
        Button b = new Button(this);
        b.setText(texto);
        b.setAllCaps(false);
        b.setOnClickListener(alTocar);
        padre.addView(b);
        return b;
    }

    private void mostrarAjustes() {
        LinearLayout forma = new LinearLayout(this);
        forma.setOrientation(LinearLayout.VERTICAL);
        forma.setPadding(dp(20), dp(8), dp(20), dp(8));

        TextView ayuda = new TextView(this);
        ayuda.setText("Servicio de inteligencia artificial. Groq es gratis y no pide tarjeta: "
                + "crea tu clave en console.groq.com y pegala aqui.");
        forma.addView(ayuda);

        LinearLayout proveedores = new LinearLayout(this);
        forma.addView(proveedores);

        EditText base = campo(forma, "Direccion del servicio", Ajustes.base(this), false);
        EditText clave = campo(forma, "Clave (API key)", Ajustes.clave(this), true);
        EditText modelo = campo(forma, "Modelo", Ajustes.modelo(this), false);

        boton(proveedores, "Usar Groq", v -> {
            base.setText(Ajustes.BASE_GROQ);
            modelo.setText(Ajustes.MODELO_GROQ);
        });
        boton(proveedores, "Usar Gemini", v -> {
            base.setText(Ajustes.BASE_GEMINI);
            modelo.setText(Ajustes.MODELO_GEMINI);
        });

        boton(forma, "Elegir modelo de la lista", v -> {
            String b = base.getText().toString().trim();
            String c = clave.getText().toString().trim();
            Toast.makeText(this, "Consultando modelos...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    List<String> modelos = Cerebro.listarModelos(b, c);
                    runOnUiThread(() -> {
                        if (isFinishing() || modelos.isEmpty()) return;
                        String[] opciones = modelos.toArray(new String[0]);
                        new AlertDialog.Builder(this).setTitle("Modelos disponibles")
                                .setItems(opciones, (d, cual) -> modelo.setText(opciones[cual])).show();
                    });
                } catch (Cerebro.ErrorApi e) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "No pude consultar: revisa la clave y la conexion", Toast.LENGTH_LONG).show());
                }
            }).start();
        });

        EditText nombre = campo(forma, "Nombre de activacion (variantes separadas por coma)", Ajustes.nombres(this), false);
        TextView consejo = new TextView(this);
        consejo.setText("Elige una palabra comun en espanol. En la pantalla principal veras lo que oigo: "
                + "si tu nombre aparece escrito distinto, agrega esa forma como variante.");
        forma.addView(consejo);

        TextView permisos = new TextView(this);
        permisos.setText("Permisos opcionales");
        permisos.setPadding(0, dp(16), 0, 0);
        forma.addView(permisos);
        boton(forma, "Permiso para leer notificaciones",
                v -> abrir(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        boton(forma, "Mostrar sobre otras apps (alarmas con la app cerrada)",
                v -> abrir(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        boton(forma, "Bateria sin restricciones (para que no se apague)",
                v -> abrir(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));

        ScrollView contenedor = new ScrollView(this);
        contenedor.addView(forma);
        new AlertDialog.Builder(this)
                .setTitle("Ajustes")
                .setView(contenedor)
                .setPositiveButton("Guardar", (d, cual) -> {
                    Ajustes.guardar(this, base.getText().toString(), clave.getText().toString(),
                            modelo.getText().toString(), nombre.getText().toString());
                    mandar("RECARGAR", null);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
