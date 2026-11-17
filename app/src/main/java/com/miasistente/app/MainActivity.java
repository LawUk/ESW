package com.miasistente.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Pantalla principal: la cara del asistente, la conversacion en burbujas y los controles. */
public class MainActivity extends Activity implements AsistenteService.Pantalla {
    static volatile boolean visible;

    private static final int FONDO = 0xFF0B0F1A;
    private static final int PANEL = 0xFF141C2E;
    private static final int ACENTO = 0xFF38BDF8;
    private static final int TEXTO = 0xFFE5E7EB;
    private static final int TENUE = 0xFF94A3B8;
    private static final int BURBUJA_MIA = 0xFF2563EB;
    private static final int ROJO = 0xFFEF4444;

    private Carita carita;
    private TextView estado;
    private TextView botonMudo;
    private WebView chat;
    private boolean chatListo;
    private final List<String> pendientesChat = new ArrayList<>();
    private EditText entrada;

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable fondo(int color, int radioDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radioDp));
        return g;
    }

    private TextView pastilla(String texto) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(15);
        t.setTextColor(TEXTO);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        t.setBackground(fondo(PANEL, 24));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        getWindow().setStatusBarColor(FONDO);
        getWindow().setNavigationBarColor(FONDO);

        boolean horizontal = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        raiz.setBackgroundColor(FONDO);
        raiz.setFitsSystemWindows(true);
        raiz.setPadding(dp(16), dp(12), dp(16), dp(12));

        // ---- Lado de la cara: expresion, estado y controles
        LinearLayout panelCara = new LinearLayout(this);
        panelCara.setOrientation(LinearLayout.VERTICAL);
        panelCara.setGravity(Gravity.CENTER_HORIZONTAL);

        carita = new Carita(this);
        panelCara.addView(carita, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        estado = new TextView(this);
        estado.setTextSize(17);
        estado.setTextColor(TEXTO);
        estado.setGravity(Gravity.CENTER);
        estado.setPadding(dp(8), dp(4), dp(8), dp(12));
        panelCara.addView(estado);

        LinearLayout controles = new LinearLayout(this);
        controles.setGravity(Gravity.CENTER);

        botonMudo = pastilla("");
        botonMudo.setOnClickListener(v -> {
            Ajustes.despertar(this, !Ajustes.despertar(this));
            pintarMudo();
            mandar("RECARGAR", null);
        });
        controles.addView(botonMudo);

        TextView microfono = new TextView(this);
        microfono.setText("🎤");
        microfono.setTextSize(30);
        microfono.setGravity(Gravity.CENTER);
        GradientDrawable circulo = new GradientDrawable();
        circulo.setShape(GradientDrawable.OVAL);
        circulo.setColor(ACENTO);
        microfono.setBackground(circulo);
        microfono.setClickable(true);
        microfono.setFocusable(true);
        microfono.setContentDescription("Hablar");
        microfono.setOnClickListener(v -> mandar("HABLAR", null));
        LinearLayout.LayoutParams medidaMic = new LinearLayout.LayoutParams(dp(84), dp(84));
        medidaMic.leftMargin = dp(18);
        medidaMic.rightMargin = dp(18);
        controles.addView(microfono, medidaMic);

        TextView ajustes = pastilla("⚙ Ajustes");
        ajustes.setOnClickListener(v -> mostrarAjustes());
        controles.addView(ajustes);
        panelCara.addView(controles);

        // ---- Lado de la conversacion
        LinearLayout panelChat = new LinearLayout(this);
        panelChat.setOrientation(LinearLayout.VERTICAL);
        if (horizontal) panelChat.setPadding(dp(16), 0, 0, 0);
        else panelChat.setPadding(0, dp(12), 0, 0);

        chat = new WebView(this);
        chat.setBackgroundColor(FONDO);
        WebSettings ajustesWeb = chat.getSettings();
        ajustesWeb.setJavaScriptEnabled(true); // solo para dibujar el chat y las formulas
        ajustesWeb.setAllowFileAccess(false);
        ajustesWeb.setAllowContentAccess(false);
        chat.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView vista, String url) {
                chatListo = true;
                for (String js : pendientesChat) chat.evaluateJavascript(js, null);
                pendientesChat.clear();
            }
        });
        chat.loadUrl("file:///android_asset/chat.html");
        panelChat.addView(chat, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout filaTexto = new LinearLayout(this);
        filaTexto.setGravity(Gravity.CENTER_VERTICAL);
        entrada = new EditText(this);
        entrada.setHint("Escribe una pregunta");
        entrada.setHintTextColor(TENUE);
        entrada.setTextColor(TEXTO);
        entrada.setSingleLine(true);
        entrada.setBackground(fondo(PANEL, 24));
        entrada.setPadding(dp(18), dp(12), dp(18), dp(12));
        entrada.setImeOptions(EditorInfo.IME_ACTION_SEND);
        entrada.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_SEND) {
                enviarTexto();
                return true;
            }
            return false;
        });
        filaTexto.addView(entrada, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView enviar = pastilla("Enviar");
        enviar.setBackground(fondo(BURBUJA_MIA, 24));
        enviar.setOnClickListener(v -> enviarTexto());
        LinearLayout.LayoutParams medidaEnviar = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        medidaEnviar.leftMargin = dp(8);
        filaTexto.addView(enviar, medidaEnviar);
        panelChat.addView(filaTexto);

        if (horizontal) {
            raiz.addView(panelCara, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            raiz.addView(panelChat, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f));
        } else {
            raiz.addView(panelCara, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            raiz.addView(panelChat, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        setContentView(raiz);
        pintarMudo();
        pedirPermisos();
        if (Ajustes.clave(this).isEmpty()) mostrarAjustes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        AsistenteService.pantalla = this;
        js("limpiar()");
        for (String[] l : AsistenteService.lineas()) burbuja(Integer.parseInt(l[0]), l[1]);
        estado.setText(AsistenteService.ultimoEstado);
        carita.modo(AsistenteService.ultimoModo);
        pintarMudo();
    }

    @Override
    protected void onPause() {
        visible = false;
        AsistenteService.pantalla = null;
        super.onPause();
    }

    // ------------------------------------------------ avisos del servicio

    @Override
    public void linea(int quien, String texto) {
        runOnUiThread(() -> burbuja(quien, texto));
    }

    @Override
    public void estado(String texto, int modo) {
        runOnUiThread(() -> {
            estado.setText(texto);
            carita.modo(modo);
            pintarMudo();
        });
    }

    @Override
    public void nivel(float nivel) {
        carita.nivel(nivel);
    }

    /** Ejecuta JavaScript en el chat; si la pagina aun carga, lo guarda para despues. */
    private void js(String codigo) {
        if (chatListo) chat.evaluateJavascript(codigo, null);
        else pendientesChat.add(codigo);
    }

    private void burbuja(int quien, String texto) {
        js("agregar(" + quien + "," + JSONObject.quote(texto) + ")");
    }

    private void pintarMudo() {
        boolean oyendo = Ajustes.despertar(this);
        botonMudo.setText(oyendo ? "🔊 Escuchando" : "🔇 Silenciado");
        botonMudo.setBackground(fondo(oyendo ? PANEL : ROJO, 24));
        botonMudo.setContentDescription(oyendo ? "Silenciar el microfono" : "Volver a escuchar");
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

        EditText claveBusqueda = campo(forma,
                "Clave de Groq para buscar en internet (opcional, si usas Gemini)", Ajustes.claveBusqueda(this), true);
        TextView notaBusqueda = new TextView(this);
        notaBusqueda.setText("La busqueda de Google no viene en el nivel gratuito de Gemini. Con una clave gratis de "
                + "Groq busco en internet; sin ella uso Google News para lo actual y Wikipedia para conceptos.");
        forma.addView(notaBusqueda);

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

        boton(forma, "Probar conexion", v -> {
            String b = base.getText().toString().trim();
            String c = clave.getText().toString().trim();
            String m = modelo.getText().toString().trim();
            Toast.makeText(this, "Probando... puede tardar hasta 45 segundos", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                String resultado = Cerebro.probar(b, c, m);
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        new AlertDialog.Builder(this).setTitle("Prueba de conexion")
                                .setMessage(resultado).setPositiveButton("OK", null).show();
                    }
                });
            }).start();
        });

        EditText nombre = campo(forma, "Nombre de activacion (variantes separadas por coma)", Ajustes.nombres(this), false);
        TextView consejo = new TextView(this);
        consejo.setText("Elige una palabra comun en espanol. Bajo la cara veras lo que oigo: "
                + "si tu nombre aparece escrito distinto, agrega esa forma como variante.");
        forma.addView(consejo);

        EditText ciudad = campo(forma, "Tu ciudad (para el clima)", Ajustes.ciudad(this), false);

        TextView datos = new TextView(this);
        datos.setText("Memoria: " + Ajustes.memoria(this).length() + " datos guardados sobre ti");
        datos.setPadding(0, dp(16), 0, 0);
        forma.addView(datos);
        boton(forma, "Borrar los datos que recuerdo", v -> new AlertDialog.Builder(this)
                .setMessage("Se borraran todos los datos que me pediste recordar.")
                .setPositiveButton("Borrar", (d, cual) -> {
                    Ajustes.borrarMemoria(this);
                    datos.setText("Memoria: 0 datos guardados sobre ti");
                })
                .setNegativeButton("Cancelar", null).show());
        boton(forma, "Borrar la conversacion", v -> {
            mandar("BORRAR", null);
            js("limpiar()");
            Toast.makeText(this, "Conversacion borrada", Toast.LENGTH_SHORT).show();
        });

        TextView permisos = new TextView(this);
        permisos.setText("Permisos opcionales");
        permisos.setPadding(0, dp(16), 0, 0);
        forma.addView(permisos);
        boton(forma, "Alarmas exactas (si las alarmas suenan tarde)", v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                abrir(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            } else {
                Toast.makeText(this, "Tu Android no necesita este permiso", Toast.LENGTH_SHORT).show();
            }
        });
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
                    Ajustes.claveBusqueda(this, claveBusqueda.getText().toString());
                    Ajustes.ciudad(this, ciudad.getText().toString());
                    mandar("RECARGAR", null);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
