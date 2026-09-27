package com.glint.server.common.metro;

import java.util.List;

public final class MetroContracts {

    private MetroContracts() {
    }

    public record MetroCity(String cityCode, String cityName, String spell) {
    }

    public record MetroLine(String lineId, String cityCode, String lineCode, String lineName, int sortOrder,
                            String colorHex) {
        public MetroLine(String lineId, String cityCode, String lineCode, String lineName, int sortOrder) {
            this(lineId, cityCode, lineCode, lineName, sortOrder, lineColor(lineName));
        }
    }

    public record MetroStation(String stationId, String cityCode, String lineId, String stationCode,
                               String stationName, Double longitude, Double latitude, int sequenceNo) {
    }

    public record MetroCityNetwork(MetroCity city, List<MetroLine> lines, List<MetroStation> stations) {
    }

    /** 给外部线网补充稳定展示色；颜色不参与匹配。 */
    public static String lineColor(String lineName) {
        if (lineName == null) return "#45B7D1";
        if (lineName.contains("1号")) return "#FF5C75";
        if (lineName.contains("2号")) return "#93C83E";
        if (lineName.contains("3号")) return "#FFBC32";
        if (lineName.contains("4号")) return "#7353D9";
        if (lineName.contains("5号")) return "#45B7D1";
        if (lineName.contains("6号")) return "#4ECDC4";
        if (lineName.contains("7号")) return "#FF9F43";
        if (lineName.contains("8号")) return "#6C8CD5";
        if (lineName.contains("10号")) return "#C7CEEA";
        return "#45B7D1";
    }
}
