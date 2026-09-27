-- 仅用于本机/CI 联调的明确测试线网；名称与编码均带 TEST，不代表真实公共交通数据。
INSERT INTO t_metro_cities (city_code, city_name, spell, synced_at)
VALUES ('TEST_CITY', '联调测试市', 'integration-test', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE city_name = VALUES(city_name), synced_at = VALUES(synced_at);

INSERT INTO t_metro_lines (line_id, city_code, line_code, line_name, sort_order)
VALUES ('TEST_LINE', 'TEST_CITY', 'TEST_LINE', '联调测试线', 1)
ON DUPLICATE KEY UPDATE line_name = VALUES(line_name), sort_order = VALUES(sort_order);

INSERT INTO t_metro_stations (
    station_id, city_code, line_id, station_code, station_name, longitude, latitude, sequence_no
) VALUES (
    'TEST_STATION', 'TEST_CITY', 'TEST_LINE', 'TEST_STATION', '联调测试站', NULL, NULL, 1
)
ON DUPLICATE KEY UPDATE station_name = VALUES(station_name), sequence_no = VALUES(sequence_no);
