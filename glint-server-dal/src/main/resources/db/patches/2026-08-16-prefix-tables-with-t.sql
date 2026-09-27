-- 已有库升级：把无前缀表改为 t_ 前缀，数据保留。
-- 新库请只执行 glint_schema.sql，不要执行本文件。
-- 可重复执行：表已是 t_ 前缀时本脚本为空操作。

USE glint;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE() AND table_name = 'users') > 0,
  'RENAME TABLE
     users TO t_users,
     profiles TO t_profiles,
     anonymous_sessions TO t_anonymous_sessions,
     commute_station_summaries TO t_commute_station_summaries,
     match_events TO t_match_events,
     match_unlock_states TO t_match_unlock_states,
     chat_sessions TO t_chat_sessions,
     chat_messages TO t_chat_messages,
     message_reports TO t_message_reports,
     board_posts TO t_board_posts,
     commute_reports TO t_commute_reports',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE() AND table_name = 'metro_cities') > 0
  AND (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema = DATABASE() AND table_name = 't_metro_cities') = 0,
  'RENAME TABLE
     metro_cities TO t_metro_cities,
     metro_lines TO t_metro_lines,
     metro_stations TO t_metro_stations',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
