package com.miasistente.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Datos actuales gratis y sin clave:
 * clima de Open-Meteo, tipo de cambio de Frankfurter (Banco Central Europeo) y criptomonedas de CoinGecko.
 */
public class Fuentes {

    private static String codificar(String texto) {
        try {
            return URLEncoder.encode(texto, "UTF-8");
        } catch (UnsupportedEncodingException imposible) {
            return texto;
        }
    }

    /** JSONObject rechaza NaN: si un dato no vino, simplemente no se incluye. */
    private static JSONObject num(JSONObject o, String clave, double valor) throws JSONException {
        if (!Double.isNaN(valor) && !Double.isInfinite(valor)) o.put(clave, valor);
        return o;
    }

    private static JSONObject obtener(String url) throws Cerebro.ErrorApi {
        return Cerebro.http(url, "Accept", "application/json", null);
    }

    // ------------------------------------------------------------------ clima

    private static String describir(int codigo) {
        if (codigo == 0) return "despejado";
        if (codigo == 1) return "mayormente despejado";
        if (codigo == 2) return "parcialmente nublado";
        if (codigo == 3) return "nublado";
        if (codigo == 45 || codigo == 48) return "niebla";
        if (codigo >= 51 && codigo <= 57) return "llovizna";
        if (codigo >= 61 && codigo <= 67) return "lluvia";
        if (codigo >= 71 && codigo <= 77) return "nieve";
        if (codigo >= 80 && codigo <= 82) return "chubascos";
        if (codigo == 85 || codigo == 86) return "nevadas";
        if (codigo >= 95) return "tormenta";
        return "sin descripcion";
    }

    static JSONObject clima(String ciudad, int dias) throws Cerebro.ErrorApi, JSONException {
        if (ciudad == null || ciudad.trim().isEmpty()) {
            return new JSONObject().put("error", "No se la ciudad. Pregunta al usuario de que ciudad, "
                    + "o dile que la guarde en Ajustes.");
        }
        String nombre = ciudad.split(",")[0].trim();
        JSONArray lugares = obtener("https://geocoding-api.open-meteo.com/v1/search?count=5&language=es&format=json&name="
                + codificar(nombre)).optJSONArray("results");
        if (lugares == null || lugares.length() == 0) {
            return new JSONObject().put("error", "No encontre la ciudad " + ciudad + ".");
        }
        // Si hay varias con el mismo nombre, se prefiere la de Mexico (o la que coincida con el estado dicho)
        JSONObject lugar = lugares.getJSONObject(0);
        String pista = ciudad.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lugares.length(); i++) {
            JSONObject l = lugares.getJSONObject(i);
            String estado = l.optString("admin1").toLowerCase(Locale.ROOT);
            if (!estado.isEmpty() && pista.contains(estado)) {
                lugar = l;
                break;
            }
            if ("MX".equals(l.optString("country_code")) && !"MX".equals(lugar.optString("country_code"))) lugar = l;
        }

        int n = Math.max(1, Math.min(7, dias));
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lugar.getDouble("latitude")
                + "&longitude=" + lugar.getDouble("longitude")
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=auto&forecast_days=" + n;
        JSONObject datos = obtener(url);

        JSONObject r = new JSONObject().put("lugar", lugar.optString("name") + ", " + lugar.optString("admin1")
                + ", " + lugar.optString("country"));
        JSONObject actual = datos.optJSONObject("current");
        if (actual != null) {
            JSONObject ahora = new JSONObject().put("hora_local", actual.optString("time"))
                    .put("cielo", describir(actual.optInt("weather_code")));
            num(ahora, "temperatura_c", actual.optDouble("temperature_2m"));
            num(ahora, "sensacion_c", actual.optDouble("apparent_temperature"));
            num(ahora, "humedad_pct", actual.optDouble("relative_humidity_2m"));
            num(ahora, "lluvia_mm", actual.optDouble("precipitation"));
            num(ahora, "viento_kmh", actual.optDouble("wind_speed_10m"));
            r.put("ahora", ahora);
        }
        JSONObject diario = datos.optJSONObject("daily");
        if (diario != null) {
            JSONArray fechas = diario.optJSONArray("time");
            JSONArray codigos = diario.optJSONArray("weather_code");
            JSONArray maximas = diario.optJSONArray("temperature_2m_max");
            JSONArray minimas = diario.optJSONArray("temperature_2m_min");
            JSONArray lluvia = diario.optJSONArray("precipitation_probability_max");
            JSONArray lista = new JSONArray();
            for (int i = 0; fechas != null && i < fechas.length(); i++) {
                JSONObject d = new JSONObject().put("fecha", fechas.optString(i));
                if (codigos != null) d.put("cielo", describir(codigos.optInt(i)));
                if (maximas != null) num(d, "maxima_c", maximas.optDouble(i));
                if (minimas != null) num(d, "minima_c", minimas.optDouble(i));
                if (lluvia != null && !lluvia.isNull(i)) d.put("probabilidad_lluvia_pct", lluvia.optInt(i));
                lista.put(d);
            }
            r.put("pronostico", lista);
        }
        return r.put("fuente", "Open-Meteo");
    }

    // --------------------------------------------------------- tipo de cambio

    static JSONObject tipoDeCambio(String de, String a, double cantidad) throws Cerebro.ErrorApi, JSONException {
        String origen = (de == null || de.trim().isEmpty() ? "USD" : de.trim()).toUpperCase(Locale.ROOT);
        String destino = (a == null || a.trim().isEmpty() ? "MXN" : a.trim()).toUpperCase(Locale.ROOT);
        if (origen.equals(destino)) return new JSONObject().put("error", "Las dos monedas son iguales.");
        JSONObject datos;
        try {
            datos = obtener("https://api.frankfurter.dev/v1/latest?base=" + origen + "&symbols=" + destino);
        } catch (Cerebro.ErrorApi e) {
            datos = obtener("https://api.frankfurter.app/latest?from=" + origen + "&to=" + destino);
        }
        JSONObject tasas = datos.optJSONObject("rates");
        if (tasas == null || !tasas.has(destino)) {
            return new JSONObject().put("error", "No tengo el tipo de cambio de " + origen + " a " + destino
                    + ". Usa codigos de 3 letras como USD, MXN, EUR, CAD, JPY.");
        }
        double tasa = tasas.getDouble(destino);
        double c = cantidad > 0 ? cantidad : 1;
        return new JSONObject()
                .put("de", origen).put("a", destino)
                .put("cantidad", c)
                .put("equivale_a", Math.round(c * tasa * 100.0) / 100.0)
                .put("tasa", tasa)
                .put("fecha_del_dato", datos.optString("date"))
                .put("nota", "Tipo de cambio de referencia del Banco Central Europeo de esa fecha. "
                        + "En bancos y casas de cambio suele ser un poco distinto.");
    }

    // -------------------------------------------------------------- criptos

    static JSONObject cripto(String moneda) throws Cerebro.ErrorApi, JSONException {
        String id = moneda == null ? "bitcoin" : moneda.trim().toLowerCase(Locale.ROOT);
        switch (id) {
            case "":
            case "btc":
                id = "bitcoin";
                break;
            case "eth":
            case "ether":
                id = "ethereum";
                break;
            case "sol":
                id = "solana";
                break;
            case "doge":
                id = "dogecoin";
                break;
            case "usdt":
                id = "tether";
                break;
            case "xrp":
                id = "ripple";
                break;
            default:
                break;
        }
        JSONObject datos = obtener("https://api.coingecko.com/api/v3/simple/price?vs_currencies=mxn,usd"
                + "&include_24hr_change=true&ids=" + codificar(id));
        JSONObject precio = datos.optJSONObject(id);
        if (precio == null) {
            return new JSONObject().put("error", "No encontre la criptomoneda " + moneda
                    + ". Usa su nombre en ingles, como bitcoin, ethereum o solana.");
        }
        JSONObject r = new JSONObject().put("moneda", id).put("fuente", "CoinGecko");
        num(r, "precio_mxn", precio.optDouble("mxn"));
        num(r, "precio_usd", precio.optDouble("usd"));
        double cambio = precio.optDouble("usd_24h_change");
        if (!Double.isNaN(cambio)) r.put("cambio_24h_pct", Math.round(cambio * 100.0) / 100.0);
        return r;
    }
}
