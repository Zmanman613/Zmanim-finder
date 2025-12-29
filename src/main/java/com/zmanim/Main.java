package com.zmanim;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import com.kosherjava.zmanim.ComplexZmanimCalendar;
import com.kosherjava.zmanim.util.GeoLocation;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.TefilaRules;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/zmanim", new ZmanimHandler());
        server.createContext("/halachik", new HalachikHandler());
        server.start();
        System.out.println("Zmanim API running on port 8080");
    }

    // ===================== /zmanim =====================
    static class ZmanimHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                Map<String, String> p = queryToMap(exchange.getRequestURI().getQuery());
                require(p, "lat", "lon", "date");

                double lat = Double.parseDouble(p.get("lat"));
                double lon = Double.parseDouble(p.get("lon"));
                double elevation = p.containsKey("elevation") ? Double.parseDouble(p.get("elevation")) : 0;

                TimeZone tz = TimeZone.getTimeZone("UTC");
                Date date = new SimpleDateFormat("yyyy-MM-dd").parse(p.get("date"));

                GeoLocation loc = new GeoLocation("User", lat, lon, tz);
                loc.setElevation(elevation);

                ComplexZmanimCalendar cal = new ComplexZmanimCalendar(loc);
                cal.getCalendar().setTime(date);

                SimpleDateFormat out = new SimpleDateFormat("HH:mm:ss");
                out.setTimeZone(tz);

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("sunrise", out.format(cal.getSunrise()));
                r.put("sunset", out.format(cal.getSunset()));
                r.put("tzeis", out.format(cal.getTzais()));

                respond(exchange, r);
            } catch (Exception e) {
                error(exchange, e.getMessage());
            }
        }
    }

    // ===================== /halachik =====================
    static class HalachikHandler implements HttpHandler {

        static final String[] REQUIRED_BOOLEANS = {
                "tachanunRecitedEndOfTishrei",
                "tachanunRecitedWeekAfterShavuos",
                "tachanunRecited13SivanOutOfIsrael",
                "tachanunRecitedPesachSheni",
                "tachanunRecited15IyarOutOfIsrael",
                "tachanunRecitedMinchaErevLagBaomer",
                "tachanunRecitedShivasYemeiHamiluim",
                "tachanunRecitedWeekOfHod",
                "tachanunRecitedWeekOfPurim",
                "tachanunRecitedFridays",
                "tachanunRecitedSundays"
        };

        @Override
        public void handle(HttpExchange exchange) {
            try {
                Map<String, String> p = queryToMap(exchange.getRequestURI().getQuery());
                require(p, "lat", "lon", "date", "time");
                for (String b : REQUIRED_BOOLEANS) require(p, b);

                double lat = Double.parseDouble(p.get("lat"));
                double lon = Double.parseDouble(p.get("lon"));
                double elevation = p.containsKey("elevation") ? Double.parseDouble(p.get("elevation")) : 0;

                TimeZone tz = TimeZone.getTimeZone("UTC");
                SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                dt.setTimeZone(tz);

                Date userDateTime = dt.parse(p.get("date") + " " + p.get("time"));

                GeoLocation loc = new GeoLocation("User", lat, lon, tz);
                loc.setElevation(elevation);

                ComplexZmanimCalendar zmanim = new ComplexZmanimCalendar(loc);
                zmanim.getCalendar().setTime(userDateTime);

                Date sunset = zmanim.getSunset();

                JewishCalendar jc = new JewishCalendar(zmanim.getCalendar());
                boolean afterSunset = !userDateTime.before(sunset);
                if (afterSunset) jc.forward(Calendar.DATE, 1);

                TefilaRules rules = new TefilaRules();

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("dayOfWeek", jc.getDayOfWeek());
                r.put("isRoshChodesh", jc.isRoshChodesh());
                r.put("isAssurBemelacha", jc.isAssurBemelacha());
                r.put("isTaanis", jc.isTaanis());
                r.put("isTachanunRecitedShacharis", rules.isTachanunRecitedShacharis(jc));
                r.put("isTachanunRecitedMincha", rules.isTachanunRecitedMincha(jc));
                r.put("isHallelRecited", rules.isHallelRecited(jc));
                r.put("isYaalehVeyavoRecited", rules.isYaalehVeyavoRecited(jc));
                r.put("isMizmorLesodaRecited", rules.isMizmorLesodaRecited(jc));
                r.put("halachicDayAdvancedAfterSunset", afterSunset);

                respond(exchange, r);
            } catch (Exception e) {
                error(exchange, e.getMessage());
            }
        }
    }

    // ===================== helpers =====================
    static void require(Map<String, String> p, String... keys) {
        for (String k : keys)
            if (!p.containsKey(k))
                throw new RuntimeException(k + " parameter is required");
    }

    static void respond(HttpExchange ex, Map<String, Object> map) throws Exception {
        StringBuilder json = new StringBuilder("{");
        int i = 0;
        for (var e : map.entrySet()) {
            json.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) json.append(v);
            else json.append("\"").append(v).append("\"");
            if (++i < map.size()) json.append(",");
        }
        json.append("}");

        byte[] b = json.toString().getBytes();
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    static void error(HttpExchange ex, String msg) {
        try {
            byte[] b = ("{\"error\":\"" + msg + "\"}").getBytes();
            ex.sendResponseHeaders(400, b.length);
            ex.getResponseBody().write(b);
            ex.getResponseBody().close();
        } catch (Exception ignored) {}
    }

    static Map<String, String> queryToMap(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null) return m;
        for (String s : q.split("&")) {
            String[] p = s.split("=");
            if (p.length == 2) m.put(p[0], p[1]);
        }
        return m;
    }
}
