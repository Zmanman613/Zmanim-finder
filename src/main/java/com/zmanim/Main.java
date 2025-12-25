package com.zmanim;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.kosherjava.zmanim.ComplexZmanimCalendar;
import com.kosherjava.zmanim.util.GeoLocation;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class Main {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/zmanim", new ZmanimHandler());
        server.start();
        System.out.println("Zmanim API running on port 8080");
    }

    static class ZmanimHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) {
            try {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());

                double lat = Double.parseDouble(params.get("lat"));
                double lon = Double.parseDouble(params.get("lon"));
                double elevation = params.containsKey("elevation")
                        ? Double.parseDouble(params.get("elevation"))
                        : 0.0;
                double tzeisAngle = params.containsKey("tzeisAngle")
                        ? Double.parseDouble(params.get("tzeisAngle"))
                        : 8.5;

                String dateStr = params.get("date");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date date = sdf.parse(dateStr);

                TimeZone tz = TimeZone.getTimeZone("UTC"); // safe default

                GeoLocation location = new GeoLocation(
                        "UserLocation",
                        lat,
                        lon,
                        tz
                );
                location.setElevation(elevation);

                ComplexZmanimCalendar calendar = new ComplexZmanimCalendar(location);
                calendar.getCalendar().setTime(date);

                SimpleDateFormat out = new SimpleDateFormat("HH:mm:ss");
                out.setTimeZone(tz);

                Map<String, String> result = new HashMap<>();
                result.put("sunrise", out.format(calendar.getSunrise()));
                result.put("sunset", out.format(calendar.getSunset()));
                result.put("tzeis", out.format(calendar.getTzaisByDegrees(tzeisAngle)));
                result.put("shema_mga", out.format(calendar.getSofZmanShmaMGA()));
                result.put("shema_gra", out.format(calendar.getSofZmanShmaGRA()));
                result.put("shema_bht", out.format(calendar.getSofZmanShmaBaalHatanya()));
                result.put("shacharis_gra", out.format(calendar.getSofZmanTfilaGRA()));
                result.put("shacharis_bht", out.format(calendar.getSofZmanTfilaBaalHatanya()));

                result.put("latitude", String.valueOf(lat));
                result.put("longitude", String.valueOf(lon));
                result.put("elevation", String.valueOf(elevation));
                result.put("date", dateStr);
                result.put("tzeisAngle", String.valueOf(tzeisAngle));
                result.put("timezone", tz.getID());

                String json = toJson(result);

                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.getBytes().length);

                OutputStream os = exchange.getResponseBody();
                os.write(json.getBytes());
                os.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Map<String, String> queryToMap(String query) {
            Map<String, String> map = new HashMap<>();
            if (query == null) return map;
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    map.put(pair[0], pair[1]);
                }
            }
            return map;
        }

        private String toJson(Map<String, String> map) {
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (var e : map.entrySet()) {
                sb.append("\"").append(e.getKey()).append("\":\"")
                  .append(e.getValue()).append("\"");
                if (++i < map.size()) sb.append(",");
            }
            sb.append("}");
            return sb.toString();
        }
    }
}

