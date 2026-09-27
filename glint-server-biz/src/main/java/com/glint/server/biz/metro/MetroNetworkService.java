package com.glint.server.biz.metro;

import com.glint.server.common.metro.MetroContracts.MetroCity;
import com.glint.server.common.metro.MetroContracts.MetroCityNetwork;
import com.glint.server.common.metro.MetroContracts.MetroLine;
import com.glint.server.common.metro.MetroContracts.MetroStation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class MetroNetworkService {

    private static final Logger log = LoggerFactory.getLogger(MetroNetworkService.class);

    private final GaodeMetroClient gaodeMetroClient;
    private final JdbcTemplate jdbcTemplate;

    public MetroNetworkService(GaodeMetroClient gaodeMetroClient, JdbcTemplate jdbcTemplate) {
        this.gaodeMetroClient = gaodeMetroClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MetroCity> listCities() {
        return jdbcTemplate.query("""
                SELECT city_code, city_name, spell FROM t_metro_cities ORDER BY city_name
                """, (row, ignored) -> new MetroCity(row.getString(1), row.getString(2), row.getString(3)));
    }

    public List<MetroLine> listLines(String cityCode) {
        return jdbcTemplate.query("""
                SELECT line_id, city_code, line_code, line_name, sort_order
                FROM t_metro_lines WHERE city_code = ? ORDER BY sort_order, line_name
                """, (row, ignored) -> new MetroLine(row.getString(1), row.getString(2), row.getString(3),
                        row.getString(4), row.getInt(5)), cityCode);
    }

    public List<MetroStation> listStations(String lineId) {
        return jdbcTemplate.query("""
                SELECT station_id, city_code, line_id, station_code, station_name, longitude, latitude, sequence_no
                FROM t_metro_stations WHERE line_id = ? ORDER BY sequence_no
                """, (row, ignored) -> new MetroStation(row.getString(1), row.getString(2), row.getString(3),
                        row.getString(4), row.getString(5),
                        toDouble(row.getObject(6)), toDouble(row.getObject(7)), row.getInt(8)), lineId);
    }

    public List<MetroStation> listStationsByCity(String cityCode) {
        return jdbcTemplate.query("""
                SELECT station_id, city_code, line_id, station_code, station_name, longitude, latitude, sequence_no
                FROM t_metro_stations WHERE city_code = ? ORDER BY line_id, sequence_no
                """, (row, ignored) -> new MetroStation(row.getString(1), row.getString(2), row.getString(3),
                        row.getString(4), row.getString(5),
                        toDouble(row.getObject(6)), toDouble(row.getObject(7)), row.getInt(8)), cityCode);
    }

    public long cityCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_metro_cities", Long.class);
        return count == null ? 0 : count;
    }

    public boolean cityExists(String cityCode) {
        if (cityCode == null || cityCode.isBlank()) return false;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_metro_cities WHERE city_code = ?", Integer.class, cityCode);
        return count != null && count > 0;
    }

    public boolean lineExistsInCity(String cityCode, String lineId) {
        if (cityCode == null || cityCode.isBlank() || lineId == null || lineId.isBlank()) return false;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_metro_lines WHERE city_code = ? AND line_id = ?",
                Integer.class, cityCode, lineId);
        return count != null && count > 0;
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double d) return d;
        if (value instanceof Number number) return number.doubleValue();
        return Double.valueOf(value.toString());
    }

    /** 全量同步；任一城市失败时跳过该城并保留库内既有快照。 */
    public int syncAll() {
        List<MetroCity> cities;
        try {
            cities = gaodeMetroClient.fetchCities();
        } catch (Exception exception) {
            log.warn("metro_city_list_sync_failed message={}", exception.getMessage());
            return 0;
        }
        int synced = 0;
        for (MetroCity city : cities) {
            try {
                syncCity(city);
                synced++;
            } catch (Exception exception) {
                log.warn("metro_city_sync_failed cityCode={}, message={}", city.cityCode(), exception.getMessage());
            }
        }
        log.info("metro_network_sync_finished requested={}, synced={}", cities.size(), synced);
        return synced;
    }

    @Transactional
    public void syncCity(MetroCity city) throws Exception {
        MetroCityNetwork network = gaodeMetroClient.fetchCityNetwork(city);
        jdbcTemplate.update("""
                INSERT INTO t_metro_cities (city_code, city_name, spell, synced_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE city_name = VALUES(city_name), spell = VALUES(spell), synced_at = VALUES(synced_at)
                """, city.cityCode(), city.cityName(), city.spell(), Timestamp.from(Instant.now()));

        jdbcTemplate.update("DELETE FROM t_metro_stations WHERE city_code = ?", city.cityCode());
        jdbcTemplate.update("DELETE FROM t_metro_lines WHERE city_code = ?", city.cityCode());

        for (MetroLine line : network.lines()) {
            jdbcTemplate.update("""
                    INSERT INTO t_metro_lines (line_id, city_code, line_code, line_name, sort_order)
                    VALUES (?, ?, ?, ?, ?)
                    """, line.lineId(), line.cityCode(), line.lineCode(), line.lineName(), line.sortOrder());
        }
        for (MetroStation station : network.stations()) {
            jdbcTemplate.update("""
                    INSERT INTO t_metro_stations
                    (station_id, city_code, line_id, station_code, station_name, longitude, latitude, sequence_no)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, station.stationId(), station.cityCode(), station.lineId(), station.stationCode(),
                    station.stationName(), station.longitude(), station.latitude(), station.sequenceNo());
        }
        log.info("metro_city_synced cityCode={}, lines={}, stations={}", city.cityCode(),
                network.lines().size(), network.stations().size());
    }
}
