package com.miasistente.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servicio que mantiene vivo al asistente:
 * espera el nombre (Vosk, sin internet) -> escucha la orden (voz de Android) -> pregunta al modelo -> responde en voz alta.
 */
public class AsistenteService extends Service {

    public interface Pantalla {
        void linea(int quien, String texto);

        void estado(String texto, int modo);

        void nivel(float nivel);
    }

    // Lo que esta haciendo el asistente (la cara cambia con esto)
    public static final int APAGADO = 0, ESPERA = 1, ESCUCHA = 2, PIENSA = 3, HABLA = 4, MUDO = 5, CARGA = 6;
    // Quien dice cada linea de la conversacion
    public static final int YO = 0, ASISTENTE = 1, SISTEMA = 2;

    static final String CANAL = "asistente";
    static volatile AsistenteService instancia;
    static volatile Pantalla pantalla;
    static volatile String ultimoEstado = "Apagado";
    static volatile int ultimoModo = APAGADO;
    private static final ArrayList<String[]> LINEAS = new ArrayList<>();

    private final Handler principal = new Handler(Looper.getMainLooper());
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final List<JSONObject> historial = new ArrayList<>();

    private TextToSpeech tts;
    private boolean ttsListo;
    private SpeechRecognizer stt;
    private boolean sttActivo;
    private Model modelo;
    private SpeechService vosk;
    private boolean cargandoModelo;
    private boolean ocupado;
    private boolean seguirEscuchando;

    /** Copia de la conversacion: cada elemento es {quien, texto}. */
    static synchronized List<String[]> lineas() {
        return new ArrayList<>(LINEAS);
    }

    private static synchronized void borrarLineas() {
        LINEAS.clear();
    }

    private static synchronized void anotar(int quien, String texto) {
        LINEAS.add(new String[]{String.valueOf(quien), texto});
        while (LINEAS.size() > 200) LINEAS.remove(0);
    }

    private void linea(int quien, String texto) {
        anotar(quien, texto);
        Pantalla p = pantalla;
        if (p != null) p.linea(quien, texto);
    }

    private void linea(String texto) {
        linea(SISTEMA, texto);
    }

    private void estado(String texto, int modo) {
        ultimoEstado = texto;
        ultimoModo = modo;
        Pantalla p = pantalla;
        if (p != null) p.estado(texto, modo);
    }

    /** Vuelve al reposo: esperar el nombre, o quedarse en silencio si el microfono esta apagado. */
    private void reposo() {
        if (Ajustes.despertar(this)) iniciarDespertar();
        else estado("Silenciado. Toca el microfono para hablar.", MUDO);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------ ciclo de vida

    @Override
    public void onCreate() {
        super.onCreate();
        instancia = this;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CANAL, "Asistente activo", NotificationManager.IMPORTANCE_LOW));
        }
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("es", "MX"));
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                    }

                    @Override
                    public void onDone(String id) {
                        if ("respuesta".equals(id)) principal.post(AsistenteService.this::alTerminarDeHablar);
                    }

                    @Override
                    public void onError(String id) {
                        if ("respuesta".equals(id)) principal.post(AsistenteService.this::alTerminarDeHablar);
                    }
                });
                ttsListo = true;
            }
        });
    }

    private Notification notificacion() {
        int banderas = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent abrir = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), banderas);
        PendingIntent hablar = PendingIntent.getForegroundService(this, 1,
                new Intent(this, AsistenteService.class).setAction("HABLAR"), banderas);
        PendingIntent apagar = PendingIntent.getService(this, 2,
                new Intent(this, AsistenteService.class).setAction("PARAR"), banderas);
        PendingIntent mudo = PendingIntent.getService(this, 3,
                new Intent(this, AsistenteService.class).setAction("MUDO"), banderas);
        boolean oyendo = Ajustes.despertar(this);
        String texto = oyendo
                ? "Di \"" + Ajustes.nombre(this) + "\" o toca Hablar"
                : "Silenciado: no te escucho. Toca Hablar para darme una orden";
        return new Notification.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(Ajustes.nombre(this) + " esta activo")
                .setContentText(texto)
                .setContentIntent(abrir)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Hablar", hablar).build())
                .addAction(new Notification.Action.Builder(null, oyendo ? "Silenciar" : "Volver a escuchar", mudo).build())
                .addAction(new Notification.Action.Builder(null, "Apagar", apagar).build())
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String accion = intent == null || intent.getAction() == null ? "INICIAR" : intent.getAction();
        if ("MUDO".equals(accion)) Ajustes.despertar(this, !Ajustes.despertar(this));
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(1, notificacion(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(1, notificacion());
            }
        } catch (RuntimeException e) {
            linea("Android no me dejo quedarme activo: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
        switch (accion) {
            case "PARAR":
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            case "HABLAR":
                if (!ocupado) {
                    historial.clear();
                    escucharOrden();
                }
                break;
            case "TEXTO":
                String texto = intent.getStringExtra("texto");
                if (texto != null && !texto.trim().isEmpty()) {
                    if (ocupado) linea("Espera un momento, sigo con lo anterior.");
                    else procesar(texto.trim(), false);
                }
                break;
            case "BORRAR":
                historial.clear();
                borrarLineas();
                break;
            case "MUDO":
            case "RECARGAR":
                detenerVosk();
                if (!ocupado) reposo();
                break;
            default:
                if (!ocupado) reposo();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instancia = null;
        detenerVosk();
        soltarStt();
        if (tts != null) tts.shutdown();
        hilo.shutdownNow();
        estado("Apagado", APAGADO);
        super.onDestroy();
    }

    // ------------------------------------------------- activacion por nombre

    private void iniciarDespertar() {
        if (!Ajustes.despertar(this) || ocupado || vosk != null) return;
        if (modelo == null) {
            if (cargandoModelo) return;
            cargandoModelo = true;
            estado("Preparando la activacion por nombre...", CARGA);
            new Thread(() -> {
                try {
                    File carpeta = ModeloVoz.preparar(this, aviso -> estado(aviso, CARGA));
                    Model cargado = new Model(carpeta.getAbsolutePath());
                    principal.post(() -> {
                        modelo = cargado;
                        cargandoModelo = false;
                        iniciarDespertar();
                    });
                } catch (Exception e) {
                    principal.post(() -> {
                        cargandoModelo = false;
                        estado("Sin activacion por nombre. Toca el microfono para hablar.", MUDO);
                        linea("No pude preparar la activacion por nombre: " + e.getMessage());
                    });
                }
            }).start();
            return;
        }
        try {
            vosk = new SpeechService(new Recognizer(modelo, 16000.0f), 16000.0f);
            vosk.startListening(new org.vosk.android.RecognitionListener() {
                @Override
                public void onPartialResult(String hipotesis) {
                    revisar(hipotesis, "partial");
                }

                @Override
                public void onResult(String hipotesis) {
                    revisar(hipotesis, "text");
                }

                @Override
                public void onFinalResult(String hipotesis) {
                }

                @Override
                public void onError(Exception e) {
                    detenerVosk();
                    estado("El microfono fallo. Toca el microfono para hablar.", MUDO);
                }

                @Override
                public void onTimeout() {
                }
            });
            estado("Di \"" + Ajustes.nombre(this) + "\"", ESPERA);
        } catch (Exception e) {
            vosk = null;
            estado("No pude abrir el microfono: " + e.getMessage(), MUDO);
        }
    }

    private void revisar(String json, String campo) {
        if (vosk == null || ocupado) return;
        String oido;
        try {
            oido = new JSONObject(json).optString(campo, "");
        } catch (JSONException e) {
            return;
        }
        if (oido.isEmpty()) return;
        estado("Di \"" + Ajustes.nombre(this) + "\"  (oigo: " + oido + ")", ESPERA);
        if (contieneNombre(oido)) {
            detenerVosk();
            pitido();
            historial.clear();
            escucharOrden();
        }
    }

    private static String normalizar(String s) {
        String sinAcentos = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return " " + sinAcentos.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim() + " ";
    }

    private boolean contieneNombre(String oido) {
        String texto = normalizar(oido);
        for (String variante : Ajustes.nombres(this).split(",")) {
            String v = normalizar(variante);
            if (v.trim().isEmpty()) continue;
            if (texto.contains(v)) return true;
        }
        return false;
    }

    private boolean soloNombre(String dicho) {
        String texto = normalizar(dicho).trim();
        for (String variante : Ajustes.nombres(this).split(",")) {
            String v = normalizar(variante).trim();
            if (!v.isEmpty() && (texto.equals(v) || texto.equals("oye " + v) || texto.equals("hola " + v))) return true;
        }
        return false;
    }

    private void detenerVosk() {
        if (vosk != null) {
            try {
                vosk.stop();
                vosk.shutdown();
            } catch (RuntimeException ignorada) {
                // ya estaba detenido
            }
            vosk = null;
        }
    }

    private void pitido() {
        try {
            ToneGenerator tono = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
            tono.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
            principal.postDelayed(tono::release, 400);
        } catch (RuntimeException ignorada) {
            // sin pitido no pasa nada
        }
    }

    // ---------------------------------------------------- escuchar la orden

    private void escucharOrden() {
        ocupado = true;
        detenerVosk();
        principal.postDelayed(() -> {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                linea("Este telefono no tiene reconocimiento de voz. Instala la app de Google o escribe tus preguntas.");
                terminar();
                return;
            }
            soltarStt();
            stt = SpeechRecognizer.createSpeechRecognizer(this);
            sttActivo = true;
            stt.setRecognitionListener(new android.speech.RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    estado("Te escucho...", ESCUCHA);
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    Pantalla p = pantalla;
                    if (p != null) p.nivel((rmsdB + 2f) / 12f);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    estado("Pensando...", PIENSA);
                }

                @Override
                public void onError(int error) {
                    if (!sttActivo) return;
                    sttActivo = false;
                    soltarStt();
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        linea("Falta el permiso de microfono.");
                    } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        linea("El reconocimiento de voz necesita internet y no hubo conexion.");
                    }
                    terminar();
                }

                @Override
                public void onResults(Bundle results) {
                    if (!sttActivo) return;
                    sttActivo = false;
                    soltarStt();
                    ArrayList<String> frases = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String dicho = frases == null || frases.isEmpty() ? "" : frases.get(0).trim();
                    if (dicho.isEmpty()) terminar();
                    else if (soloNombre(dicho)) escucharOrden();
                    else procesar(dicho, true);
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
            Intent orden = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName())
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
            try {
                stt.startListening(orden);
            } catch (RuntimeException e) {
                sttActivo = false;
                linea("No pude escuchar: " + e.getMessage());
                terminar();
            }
        }, 350);
    }

    private void soltarStt() {
        if (stt != null) {
            try {
                stt.destroy();
            } catch (RuntimeException ignorada) {
                // ya estaba liberado
            }
            stt = null;
        }
    }

    // --------------------------------------------------- pensar y responder

    private void procesar(String texto, boolean porVoz) {
        ocupado = true;
        linea(YO, texto);
        String simple = normalizar(texto).trim();
        if (porVoz && (simple.equals("adios") || simple.equals("hasta luego") || simple.equals("eso es todo")
                || simple.equals("nada mas") || simple.equals("gracias"))) {
            hablar("Hasta luego.", false);
            return;
        }
        estado("Pensando...", PIENSA);
        hilo.execute(() -> {
            String respuesta = Cerebro.responder(this, historial, texto,
                    (herramienta, resultado) -> linea(SISTEMA, aviso(herramienta, resultado)));
            principal.post(() -> {
                linea(ASISTENTE, respuesta);
                hablar(respuesta, porVoz);
            });
        });
    }

    private static String paraVoz(String texto) {
        return Voz.hablable(texto);
    }

    /** Linea corta que se ve en la conversacion cuando el asistente usa una herramienta. */
    private static String aviso(String herramienta, JSONObject resultado) {
        String accion;
        switch (herramienta) {
            case "agregar_pendiente": accion = "Guardando recordatorio"; break;
            case "listar_pendientes": accion = "Revisando pendientes"; break;
            case "completar_pendiente": accion = "Marcando pendiente"; break;
            case "poner_alarma": accion = "Poniendo alarma"; break;
            case "poner_temporizador": accion = "Iniciando temporizador"; break;
            case "listar_alarmas": accion = "Revisando alarmas"; break;
            case "cancelar_alarma": accion = "Cancelando alarma"; break;
            case "calcular": accion = "Calculando"; break;
            case "clima": accion = "Consultando el clima"; break;
            case "tipo_de_cambio": accion = "Consultando el tipo de cambio"; break;
            case "precio_cripto": accion = "Consultando criptomonedas"; break;
            case "noticias": accion = "Leyendo noticias"; break;
            case "buscar_en_internet": accion = "Buscando en internet"; break;
            case "recordar_dato": accion = "Guardando en memoria"; break;
            case "olvidar_dato": accion = "Borrando de memoria"; break;
            default: accion = herramienta;
        }
        if (resultado != null && resultado.has("error")) {
            return "\u26A0 " + accion + ": " + resultado.optString("error");
        }
        return "\u2713 " + accion;
    }

    private void hablar(String texto, boolean seguir) {
        seguirEscuchando = seguir;
        String limpio = paraVoz(texto);
        if ("Hasta luego.".equals(texto)) linea(ASISTENTE, texto);
        if (!ttsListo || limpio.isEmpty()) {
            alTerminarDeHablar();
            return;
        }
        estado("Hablando...", HABLA);
        Bundle parametros = new Bundle();
        parametros.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        if (tts.speak(limpio, TextToSpeech.QUEUE_FLUSH, parametros, "respuesta") != TextToSpeech.SUCCESS) {
            alTerminarDeHablar();
        }
    }

    /** Lo usan los recordatorios: habla sin cambiar el estado de la conversacion. */
    void decir(String texto) {
        principal.post(() -> {
            linea(texto);
            if (ttsListo) tts.speak(paraVoz(texto), TextToSpeech.QUEUE_ADD, null, "aviso");
        });
    }

    private void alTerminarDeHablar() {
        if (seguirEscuchando) {
            seguirEscuchando = false;
            escucharOrden(); // por si hay otra pregunta; si hay silencio, vuelve a esperar el nombre
        } else {
            terminar();
        }
    }

    private void terminar() {
        ocupado = false;
        reposo();
    }
}
