-- 增量补丁：高德地铁线网 + 通勤摘要/匹配扩展字段（可重复执行）

CREATE TABLE IF NOT EXISTS t_metro_cities (
    city_code VARCHAR(16) PRIMARY KEY COMMENT '高德地铁 adcode，例如 3100',
    city_name VARCHAR(64) NOT NULL COMMENT '城市显示名',
    spell VARCHAR(64) NOT NULL COMMENT '线路图文件用拼音，例如 shanghai',
    synced_at TIMESTAMP NULL COMMENT '最近一次成功同步时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '行更新时间'
) COMMENT='有地铁的城市列表';

CREATE TABLE IF NOT EXISTS t_metro_lines (
    line_id VARCHAR(64) PRIMARY KEY COMMENT '高德线路 ID',
    city_code VARCHAR(16) NOT NULL COMMENT '所属城市 adcode',
    line_code VARCHAR(64) NOT NULL COMMENT '匹配用线路代码（通常同 line_id）',
    line_name VARCHAR(128) NOT NULL COMMENT '线路显示名，例如 2号线',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
    INDEX idx_metro_lines_city (city_code),
    CONSTRAINT fk_t_metro_lines_city FOREIGN KEY (city_code) REFERENCES t_metro_cities (city_code)
) COMMENT='城市地铁线路';

CREATE TABLE IF NOT EXISTS t_metro_stations (
    station_id VARCHAR(64) PRIMARY KEY COMMENT '高德站点 ID',
    city_code VARCHAR(16) NOT NULL COMMENT '所属城市',
    line_id VARCHAR(64) NOT NULL COMMENT '所属线路',
    station_code VARCHAR(64) NOT NULL COMMENT '站点代码（通常同 station_id）',
    station_name VARCHAR(128) NOT NULL COMMENT '站点显示名',
    longitude DECIMAL(10, 6) NULL COMMENT '站点参考经度（公共 POI）',
    latitude DECIMAL(10, 6) NULL COMMENT '站点参考纬度（公共 POI）',
    sequence_no INT NOT NULL DEFAULT 0 COMMENT '线路内顺序',
    INDEX idx_metro_stations_line (line_id, sequence_no),
    INDEX idx_metro_stations_city (city_code),
    CONSTRAINT fk_t_metro_stations_line FOREIGN KEY (line_id) REFERENCES t_metro_lines (line_id),
    CONSTRAINT fk_t_metro_stations_city FOREIGN KEY (city_code) REFERENCES t_metro_cities (city_code)
) COMMENT='线路站点及参考坐标';

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_commute_station_summaries' AND COLUMN_NAME = 'city_code'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_commute_station_summaries ADD COLUMN city_code VARCHAR(16) NULL COMMENT ''城市 adcode'' AFTER user_id, ADD COLUMN line_id VARCHAR(64) NULL COMMENT ''线路 ID'' AFTER city_code, ADD INDEX idx_station_summary_line (city_code, line_id, occurred_at)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_match_events' AND COLUMN_NAME = 'city_code'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_match_events ADD COLUMN city_code VARCHAR(16) NULL COMMENT ''城市 adcode'' AFTER second_user_id, ADD COLUMN line_id VARCHAR(64) NULL COMMENT ''线路 ID'' AFTER city_code',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
