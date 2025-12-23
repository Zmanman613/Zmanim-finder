package com.zmanim;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import com.kosherjava.zmanim.ZmanimCalendar;
import com.kosherjava.zmanim.ComplexZmanimCalendar;
import com.kosherjava.zmanim.util.GeoLocation;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class Main {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/sunrise", e -> handleBasicZman(e, "sunrise"));
        server.createContext("/sunset",  e -> handleBasicZman(e, "sunset"));
        server.createContext("/tzeis",    e -> handleBasicZman(e, "tzeis"));

        server.createContext("/shema-mga",      e -> handleComplexZman(e, "shema-mga"));
        server.createContext("/shema-gra",      e -> handleComplexZman(e, "shema-gra"));
        server.createContext("/shema-bht",      e -> handleComplexZman(e, "shema-bht"));
        server.createContext("/shacharis-gra",  e -> handleComplexZman(e, "shacharis-gra"));
        server.createContext("/shacharis-bht",  e -> handleComplexZman(e, "shacharis-bht"));

        server.setExecutor(null);
        server.start();

        System.out.println("Zmanim server running on port 8080");
    }

    // ---------- BASIC ZMANIM ----------

    private static void handleBasicZman(HttpExchange exchange, String type) {
        try {
            ZmanimCalendar zmanim = buildBasicCalendar(exchange);
            SimpleDateFormat fmt = utcFormatter();

            String time;

            switch (type) {
                case "sunrise":
                    time = fmt.format(zmanim.getSunrise());
                    break;
                case "sunset":
                    time = fmt.format(zmanim.getSunset());
                    break;
                case "tzeis":
                    time = fmt.format(zmanim.getTzais());
                    break;
                default:
                    throw new RuntimeException("Invalid zman");
            }

            send(exchange, type, time);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- COMPLEX ZMANIM ----------

    private static void handleComplexZman(HttpExchange exchange, String type) {
        try {
            ComplexZmanimCalendar czc = buildComplexCalendar(exchange);
            SimpleDateFormat fmt = utcFormatter();

            String time;

            switch (type) {
                case "shema-mga":
                    time = fmt.format(czc.getSofZmanShmaMGA());
                    break;
                case "shema-gra":
                    time = fmt.format(czc.getSofZmanShmaGRA());
                    break;
                case "shema-bht":
                    time = fmt.format(czc.getSofZmanShmaBaalHatanya());
                    break;
                case "shacharis-gra":
                    time = fmt.format(czc.getSofZmanTfilaGRA());
                    break;
                case "shacharis-bht":
                    time = fmt.format(czc.getSofZmanTfilaBaalHatanya());
                    break;
                default:
                    throw new RuntimeException("Invalid zman");
            }

            send(exchange, type, time);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- HELPERS ----------

    private static ZmanimCalendar buildBasicCalendar(HttpExchange exchange) {
        GeoLocation loc = parseLocation(exchange);
        ZmanimCalendar zc = new ZmanimCalendar(loc);
        zc.setCalendar(parseDate(exchange));
        return zc;
    }

    private static ComplexZmanimCalendar buildComplexCalendar(HttpExchange exchange) {
        GeoLocation loc = parseLocation(exchange);
        ComplexZmanimCalendar czc = new ComplexZmanimCalendar(loc);
        czc.setCalendar(parseDate(exchange));
        return czc;
    }

    private static GeoLocation parseLocation(HttpExchange exchange) {
        String q = exchange.getRequestURI().getQuery();
        double lat = 0, lon = 0;

        for (String p : q.split("&")) {
            String[] kv = p.split("=");
            if (kv[0].equals("lat")) lat = Double.parseDouble(kv[1]);
            if (kv[0].equals("lon")) lon = Double.parseDouble(kv[1]);
        }

        return new GeoLocation("location", lat, lon, 0, TimeZone.getTimeZone("UTC"));
    }

    private static Calendar parseDate(HttpExchange exchange) {
        String q = exchange.getRequestURI().getQuery();
        String date = null;

        for (String p : q.split("&")) {
            String[] kv = p.split("=");
            if (kv[0].equals("date")) date = kv[1];
        }

        String[] d = date.split("-");
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]));
        return cal;
    }

    private static SimpleDateFormat utcFormatter() {
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt;
    }

    private static void send(HttpExchange exchange, String zman, String time) throws Exception {
        String response = "{ \"zman\": \"" + zman + "\", \"time\": \"" + time + " UTC\" }";
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
