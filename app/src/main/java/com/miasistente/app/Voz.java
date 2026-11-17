package com.miasistente.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte el texto de la pantalla (con formulas LaTeX y formato) en algo que suene natural en voz alta.
 * Ejemplo: $\frac{a}{b}$ se dice "a entre b"; $x^2$ se dice "x al cuadrado".
 */
public final class Voz {
    private Voz() {
    }

    private static final Pattern FORMULA = Pattern.compile(
            "\\$\\$(.+?)\\$\\$|\\\\\\[(.+?)\\\\\\]|\\\\\\((.+?)\\\\\\)|\\$([^$]+?)\\$", Pattern.DOTALL);

    private static final String[][] COMANDOS = {
            {"cdot", " por "}, {"times", " por "}, {"div", " entre "}, {"pm", " m\u00e1s menos "},
            {"leq", " menor o igual que "}, {"le", " menor o igual que "},
            {"geq", " mayor o igual que "}, {"ge", " mayor o igual que "},
            {"neq", " distinto de "}, {"ne", " distinto de "}, {"approx", " aproximadamente "},
            {"infty", " infinito "}, {"sum", " la suma de "}, {"int", " la integral de "}, {"lim", " el l\u00edmite "},
            {"to", " tiende a "}, {"rightarrow", " entonces "}, {"Rightarrow", " entonces "},
            {"pi", " pi "}, {"theta", " teta "}, {"alpha", " alfa "}, {"beta", " beta "}, {"gamma", " gama "},
            {"Delta", " delta "}, {"delta", " delta "}, {"lambda", " lambda "}, {"mu", " mu "},
            {"sigma", " sigma "}, {"Sigma", " sigma "}, {"omega", " omega "}, {"phi", " fi "}, {"rho", " ro "},
            {"sin", " seno de "}, {"cos", " coseno de "}, {"tan", " tangente de "},
            {"log", " logaritmo de "}, {"ln", " logaritmo natural de "},
            {"left", " "}, {"right", " "}, {"quad", " "}, {"qquad", " "},
    };

    /** Formula LaTeX a palabras. */
    static String formula(String f) {
        String t = f;
        t = t.replaceAll("\\\\(?:text|mathrm|mathbf|operatorname)\\{([^{}]*)\\}", " $1 ");
        for (int i = 0; i < 4; i++) {
            t = t.replaceAll("\\\\[dt]?frac\\{([^{}]*)\\}\\{([^{}]*)\\}", " $1 entre $2 ");
            t = t.replaceAll("\\\\sqrt\\[([^\\]]*)\\]\\{([^{}]*)\\}", " ra\u00edz $1 de $2 ");
            t = t.replaceAll("\\\\sqrt\\{([^{}]*)\\}", " ra\u00edz cuadrada de $1 ");
        }
        t = t.replaceAll("\\^\\{?2\\}?(?![0-9])", " al cuadrado ");
        t = t.replaceAll("\\^\\{?3\\}?(?![0-9])", " al cubo ");
        t = t.replaceAll("\\^\\{([^{}]*)\\}", " elevado a $1 ");
        t = t.replaceAll("\\^(\\w)", " elevado a $1 ");
        t = t.replaceAll("_\\{([^{}]*)\\}", " sub $1 ");
        t = t.replaceAll("_(\\w)", " sub $1 ");
        for (String[] c : COMANDOS) t = t.replaceAll("\\\\" + c[0] + "(?![a-zA-Z])", Matcher.quoteReplacement(c[1]));
        t = t.replaceAll("\\\\[a-zA-Z]+", " ");
        t = t.replace("\\,", " ").replace("\\;", " ").replace("\\!", "").replace("{", " ").replace("}", " ");
        t = t.replace("<=", " menor o igual que ").replace(">=", " mayor o igual que ")
                .replace("<", " menor que ").replace(">", " mayor que ")
                .replace("=", " igual a ").replace("+", " m\u00e1s ").replace("*", " por ").replace("/", " entre ")
                .replace("(", " ").replace(")", " ").replace("[", " ").replace("]", " ").replace("|", " ");
        t = t.replaceAll("(?<=[\\w\\s])-(?=[\\s\\w])", " menos ").replaceAll("^\\s*-", " menos ");
        return t;
    }

    /** Texto completo de una respuesta, listo para la voz. */
    public static String hablable(String texto) {
        if (texto == null) return "";
        Matcher m = FORMULA.matcher(texto);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String f = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2)
                    : m.group(3) != null ? m.group(3) : m.group(4);
            m.appendReplacement(sb, Matcher.quoteReplacement(" " + formula(f) + " "));
        }
        m.appendTail(sb);
        String t = sb.toString()
                .replaceAll("https?://\\S+", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "")
                .replaceAll("(?m)^\\s*#+\\s*", "")
                .replaceAll("[*_#`>|]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return t;
    }
}
