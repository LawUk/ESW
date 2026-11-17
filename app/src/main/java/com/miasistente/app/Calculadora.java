package com.miasistente.app;

import java.util.Locale;

/**
 * Calculadora para que el modelo no haga cuentas de memoria (los modelos pequenos se equivocan).
 * Acepta + - * / ^ %, parentesis, pi, e y funciones: raiz/sqrt, sen/sin, cos, tan, asen, acos, atan
 * (en grados), log (base 10), ln, abs, exp, redondear.
 */
public final class Calculadora {
    private final String s;
    private int pos;

    private Calculadora(String texto) {
        s = texto;
    }

    public static double evaluar(String expresion) {
        if (expresion == null || expresion.trim().isEmpty()) throw new IllegalArgumentException("La expresion esta vacia");
        String t = expresion.toLowerCase(Locale.ROOT)
                .replace("×", "*").replace("÷", "/").replace("·", "*").replace("−", "-")
                .replace("**", "^").replace(",", ".").replace("π", "pi").replace("√", "raiz")
                .replaceAll("\\s+", "");
        Calculadora c = new Calculadora(t);
        double r = c.suma();
        if (c.pos < c.s.length()) throw new IllegalArgumentException("No entiendo esto: " + c.s.substring(c.pos));
        if (Double.isNaN(r) || Double.isInfinite(r)) throw new IllegalArgumentException("El resultado no es un numero valido");
        return r;
    }

    /** Formato legible: enteros sin decimales y hasta 10 cifras significativas. */
    public static String formato(double x) {
        if (Math.abs(x - Math.rint(x)) < 1e-9 && Math.abs(x) < 1e15) return String.valueOf((long) Math.rint(x));
        String t = String.format(Locale.US, "%.10g", x);
        if (t.contains("e")) return t;
        if (t.contains(".")) t = t.replaceAll("0+$", "").replaceAll("\\.$", "");
        return t;
    }

    private char actual() {
        return pos < s.length() ? s.charAt(pos) : '\0';
    }

    private double suma() {
        double v = producto();
        while (true) {
            char c = actual();
            if (c == '+') {
                pos++;
                v += producto();
            } else if (c == '-') {
                pos++;
                v -= producto();
            } else {
                return v;
            }
        }
    }

    private double producto() {
        double v = signo();
        while (true) {
            char c = actual();
            if (c == '*') {
                pos++;
                v *= signo();
            } else if (c == '/') {
                pos++;
                double d = signo();
                if (d == 0) throw new IllegalArgumentException("No se puede dividir entre cero");
                v /= d;
            } else if (c == '%') {
                pos++;
                v %= signo();
            } else if (c == '(' || Character.isLetter(c)) {
                v *= signo(); // multiplicacion implicita: 2pi, 3(4+1)
            } else {
                return v;
            }
        }
    }

    private double signo() {
        char c = actual();
        if (c == '-') {
            pos++;
            return -signo();
        }
        if (c == '+') {
            pos++;
            return signo();
        }
        return potencia();
    }

    private double potencia() {
        double base = primario();
        if (actual() == '^') {
            pos++;
            return Math.pow(base, signo()); // asociativa a la derecha: 2^3^2 = 2^9
        }
        return base;
    }

    private double primario() {
        char c = actual();
        if (c == '(') {
            pos++;
            double v = suma();
            if (actual() != ')') throw new IllegalArgumentException("Falta cerrar un parentesis");
            pos++;
            return v;
        }
        if (Character.isDigit(c) || c == '.') {
            int inicio = pos;
            while (Character.isDigit(actual()) || actual() == '.') pos++;
            if (actual() == 'e' && pos + 1 < s.length()
                    && (Character.isDigit(s.charAt(pos + 1)) || s.charAt(pos + 1) == '-' || s.charAt(pos + 1) == '+')) {
                pos += 2;
                while (Character.isDigit(actual())) pos++;
            }
            try {
                return Double.parseDouble(s.substring(inicio, pos));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Numero mal escrito: " + s.substring(inicio, pos));
            }
        }
        if (Character.isLetter(c)) {
            int inicio = pos;
            while (Character.isLetter(actual())) pos++;
            String nombre = s.substring(inicio, pos);
            if (nombre.equals("pi")) return Math.PI;
            if (nombre.equals("e")) return Math.E;
            double x = actual() == '(' ? primario() : potencia();
            switch (nombre) {
                case "raiz":
                case "sqrt":
                    if (x < 0) throw new IllegalArgumentException("No hay raiz cuadrada real de un numero negativo");
                    return Math.sqrt(x);
                case "sen":
                case "sin":
                    return Math.sin(Math.toRadians(x));
                case "cos":
                    return Math.cos(Math.toRadians(x));
                case "tan":
                    return Math.tan(Math.toRadians(x));
                case "asen":
                case "asin":
                    return Math.toDegrees(Math.asin(x));
                case "acos":
                    return Math.toDegrees(Math.acos(x));
                case "atan":
                    return Math.toDegrees(Math.atan(x));
                case "log":
                    return Math.log10(x);
                case "ln":
                    return Math.log(x);
                case "abs":
                    return Math.abs(x);
                case "exp":
                    return Math.exp(x);
                case "redondear":
                case "round":
                    return Math.rint(x);
                default:
                    throw new IllegalArgumentException("No conozco la funcion " + nombre);
            }
        }
        throw new IllegalArgumentException(c == '\0' ? "La expresion esta incompleta" : "No entiendo el simbolo " + c);
    }
}
