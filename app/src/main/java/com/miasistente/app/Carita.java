package com.miasistente.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** La cara animada del asistente. Cambia de expresion y color segun lo que este haciendo. */
public class Carita extends View {
    private final Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF caja = new RectF();
    private int modo = AsistenteService.APAGADO;
    private float nivel;
    private float nivelSuave;

    public Carita(Context contexto) {
        super(contexto);
    }

    public void modo(int nuevo) {
        modo = nuevo;
        if (nuevo != AsistenteService.ESCUCHA) nivel = 0f;
        invalidate();
    }

    /** Volumen de la voz mientras escucha, de 0 a 1. */
    public void nivel(float valor) {
        nivel = Math.max(0f, Math.min(1f, valor));
    }

    private int color() {
        switch (modo) {
            case AsistenteService.ESCUCHA:
                return 0xFF34D399;
            case AsistenteService.PIENSA:
                return 0xFFFBBF24;
            case AsistenteService.MUDO:
            case AsistenteService.APAGADO:
                return 0xFF64748B;
            case AsistenteService.CARGA:
                return 0xFFA78BFA;
            default:
                return 0xFF38BDF8;
        }
    }

    private float apertura(float t) {
        switch (modo) {
            case AsistenteService.MUDO:
            case AsistenteService.APAGADO:
                return 0f;
            case AsistenteService.CARGA:
                return 0.45f;
            case AsistenteService.ESCUCHA:
                return 1.12f;
            default:
                return (t % 4.2f) < 0.13f ? 0.08f : 1f; // parpadeo
        }
    }

    @Override
    protected void onDraw(Canvas lienzo) {
        super.onDraw(lienzo);
        float ancho = getWidth();
        float alto = getHeight();
        if (ancho <= 0 || alto <= 0) return;
        float lado = Math.min(ancho, alto);
        float cx = ancho / 2f;
        float cy = alto / 2f;
        float t = SystemClock.uptimeMillis() / 1000f;
        int color = color();
        nivelSuave += (nivel - nivelSuave) * 0.25f;

        // Halo que respira; al escuchar sigue el volumen de la voz
        float pulso;
        if (modo == AsistenteService.ESCUCHA) pulso = 0.35f + 0.65f * nivelSuave;
        else if (modo == AsistenteService.MUDO || modo == AsistenteService.APAGADO) pulso = 0.15f;
        else pulso = (float) (0.5 + 0.5 * Math.sin(t * (modo == AsistenteService.HABLA ? 5.0 : 1.8)));
        pincel.setStyle(Paint.Style.FILL);
        pincel.setColor(color);
        pincel.setAlpha((int) (26 + 40 * pulso));
        lienzo.drawCircle(cx, cy, lado * (0.40f + 0.07f * pulso), pincel);
        pincel.setAlpha(255);
        pincel.setColor(0xFF141C2E);
        lienzo.drawCircle(cx, cy, lado * 0.37f, pincel);

        // Ojos
        float ojoAncho = lado * 0.085f;
        float ojoAlto = lado * 0.17f * apertura(t);
        float separacion = lado * 0.13f;
        float ojoY = cy - lado * 0.05f;
        float dx = 0f;
        if (modo == AsistenteService.PIENSA) {
            dx = (float) Math.sin(t * 2.4) * lado * 0.035f;
            ojoY -= lado * 0.03f;
        }
        pincel.setColor(color);
        float minimo = lado * 0.014f;
        for (int lado2 = -1; lado2 <= 1; lado2 += 2) {
            float ex = cx + lado2 * separacion + dx;
            float mitad = Math.max(ojoAlto, minimo) / 2f;
            caja.set(ex - ojoAncho / 2f, ojoY - mitad, ex + ojoAncho / 2f, ojoY + mitad);
            float radio = Math.min(ojoAncho / 2f, mitad);
            lienzo.drawRoundRect(caja, radio, radio, pincel);
        }

        // Boca
        float bocaY = cy + lado * 0.15f;
        float bocaAncho = lado * 0.13f;
        if (modo == AsistenteService.HABLA) {
            float abre = (float) (0.3 + 0.7 * Math.abs(Math.sin(t * 9.0))) * lado * 0.075f;
            caja.set(cx - bocaAncho / 2f, bocaY - abre / 2f, cx + bocaAncho / 2f, bocaY + abre / 2f);
            lienzo.drawRoundRect(caja, bocaAncho / 3f, bocaAncho / 3f, pincel);
        } else if (modo == AsistenteService.PIENSA || modo == AsistenteService.CARGA) {
            for (int i = -1; i <= 1; i++) {
                float salto = (float) Math.max(0.0, Math.sin(t * 5.0 - i * 0.9)) * lado * 0.02f;
                lienzo.drawCircle(cx + i * lado * 0.05f, bocaY - salto, lado * 0.013f, pincel);
            }
        } else {
            pincel.setStyle(Paint.Style.STROKE);
            pincel.setStrokeWidth(lado * 0.02f);
            pincel.setStrokeCap(Paint.Cap.ROUND);
            if (modo == AsistenteService.ESCUCHA) {
                lienzo.drawCircle(cx, bocaY, lado * (0.02f + 0.015f * nivelSuave), pincel);
            } else if (modo == AsistenteService.MUDO || modo == AsistenteService.APAGADO) {
                lienzo.drawLine(cx - bocaAncho / 2.4f, bocaY, cx + bocaAncho / 2.4f, bocaY, pincel);
            } else {
                caja.set(cx - bocaAncho / 2f, bocaY - bocaAncho * 0.55f, cx + bocaAncho / 2f, bocaY + bocaAncho * 0.2f);
                lienzo.drawArc(caja, 25f, 130f, false, pincel);
            }
            pincel.setStyle(Paint.Style.FILL);
        }

        if (isShown()) postInvalidateOnAnimation();
    }

    @Override
    protected void onVisibilityChanged(View vista, int visibilidad) {
        super.onVisibilityChanged(vista, visibilidad);
        if (visibilidad == VISIBLE) invalidate();
    }
}
