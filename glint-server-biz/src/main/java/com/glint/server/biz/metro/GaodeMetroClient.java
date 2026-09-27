package com.glint.server.biz.metro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glint.server.common.metro.MetroContracts.MetroCity;
import com.glint.server.common.metro.MetroContracts.MetroCityNetwork;
import com.glint.server.common.metro.MetroContracts.MetroLine;
import com.glint.server.common.metro.MetroContracts.MetroStation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 从高德地铁图 srhdata 拉取城市与线网；不落用户轨迹。 */
@Component
public class GaodeMetroClient {

    private static final Logger log = LoggerFactory.getLogger(GaodeMetroClient.class);

    private final GaodeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public GaodeMetroClient(GaodeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<MetroCity> fetchCities() throws Exception {
        JsonNode root = objectMapper.readTree(fetchRaw("citylist.json"));
        JsonNode list = root.path("citylist");
        List<MetroCity> cities = new ArrayList<>();
        for (JsonNode node : list) {
            cities.add(new MetroCity(node.path("adcode").asText(), node.path("cityname").asText(), node.path("spell").asText()));
        }
        return cities;
    }

    public MetroCityNetwork fetchCityNetwork(MetroCity city) throws Exception {
        JsonNode root = objectMapper.readTree(fetchRaw(city.cityCode() + "_drw_" + city.spell() + ".json"));
        List<MetroLine> lines = new ArrayList<>();
        List<MetroStation> stations = new ArrayList<>();
        int lineOrder = 0;
        for (JsonNode lineNode : root.path("l")) {
            String lineId = firstLineId(lineNode);
            if (lineId.isBlank()) continue;
            String lineName = lineNode.path("ln").asText(lineId);
            lines.add(new MetroLine(lineId, city.cityCode(), lineId, lineName, lineOrder++));
            int seq = 0;
            for (JsonNode stationNode : lineNode.path("st")) {
                String stationId = stationNode.path("sid").asText("");
                if (stationId.isBlank()) stationId = stationNode.path("si").asText("");
                if (stationId.isBlank()) continue;
                String name = stationNode.path("n").asText(stationId);
                Double[] latLng = parseLatLng(stationNode.path("sl").asText(""));
                stations.add(new MetroStation(stationId, city.cityCode(), lineId, stationId, name,
                        latLng[0], latLng[1], seq++));
            }
        }
        return new MetroCityNetwork(city, lines, stations);
    }

    protected String fetchRaw(String srhdata) throws Exception {
        String url = properties.getSubwayBaseUrl() + "?_v=w&srhdata=" + srhdata;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("高德线网请求失败 status=" + response.statusCode() + " srhdata=" + srhdata);
        }
        return response.body();
    }

    private static String firstLineId(JsonNode lineNode) {
        String ls = lineNode.path("ls").asText("");
        if (!ls.isBlank()) return ls;
        String li = lineNode.path("li").asText("");
        if (li.contains("|")) return li.substring(0, li.indexOf('|'));
        return li;
    }

    private static Double[] parseLatLng(String sl) {
        if (sl == null || !sl.contains(",")) return new Double[]{null, null};
        String[] parts = sl.split(",");
        try {
            return new Double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        } catch (NumberFormatException exception) {
            return new Double[]{null, null};
        }
    }
}
