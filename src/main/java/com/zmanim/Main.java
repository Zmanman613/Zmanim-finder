import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.sourceforge.zmanim.ZmanimCalendar;
import net.sourceforge.zmanim.util.GeoLocation;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/zmanim", new ZmanimHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Server started at http://localhost:8080");
    }

    static class ZmanimHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = queryToMap(query);

                double lat = Double.parseDouble(params.get("lat"));
                double lon = Double.parseDouble(params.get("lon"));
                String dateStr = params.get("date");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date date = sdf.parse(dateStr);

                GeoLocation location = new GeoLocation("UserLocation", lat, lon, 0);
                ZmanimCalendar calendar = new ZmanimCalendar(location);
                calendar.setDate(date);

                SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm:ss");

                Map<String, String> result = new HashMap<>();
                result.put("sunrise", outputFormat.format(calendar.getSunrise()));
                result.put("sunset", outputFormat.format(calendar.getSunset()));
                result.put("tzeis", outputFormat.format(calendar.getTzais())); // 8.5°
                result.put("shema_mga", outputFormat.format(calendar.getSofZmanShmaMGA()));
                result.put("shema_gra", outputFormat.format(calendar.getSofZmanShmaGRA()));
                result.put("shema_bht", outputFormat.format(calendar.getSofZmanShmaBaalHatanya()));
                result.put("shacharis_gra", outputFormat.format(calendar.getSofZmanTfilaGRA()));
                result.put("shacharis_bht", outputFormat.format(calendar.getSofZmanTfilaBaalHatanya()));

                String jsonResponse = mapToJson(result);

                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(jsonResponse.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Map<String, String> queryToMap(String query) {
            Map<String, String> result = new HashMap<>();
            if(query == null) return result;
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1) {
                    result.put(pair[0], pair[1]);
                }
            }
            return result;
        }

        private String mapToJson(Map<String, String> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            int i = 0;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                sb.append("\"").append(entry.getKey()).append("\":")
                  .append("\"").append(entry.getValue()).append("\"");
                if (i < map.size() - 1) sb.append(",");
                i++;
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
