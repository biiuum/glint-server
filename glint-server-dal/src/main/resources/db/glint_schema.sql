-- 巧了：MySQL 8 一次性初始化脚本。所有业务表使用 t_ 前缀。
-- 用法：按 README 手动执行本文件；脚本会创建并使用 glint 数据库。服务启动不会自动执行 SQL。
-- 隐私总则：严禁保存精确 GPS、基站/Wi-Fi/BLE 标识或运动传感器原始数据。
drop database glint;
-- 应用数据库：与 application-dev.properties 中默认库名 glint 对应。
CREATE DATABASE IF NOT EXISTS glint
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 后续全部表创建在 glint 数据库中。
USE glint;

CREATE TABLE t_schema_versions (
    version VARCHAR(64) PRIMARY KEY COMMENT '已手动应用的数据库变更版本',
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '应用时间'
) COMMENT='数据库手动变更版本记录';

-- 用户私密账号表：联系方式和密码哈希只由本人账号接口与认证服务使用。
CREATE TABLE t_users (
    id CHAR(36) PRIMARY KEY COMMENT '内部用户 UUID；通勤业务使用匿名关联标识',
    email VARCHAR(254) NULL COMMENT '登录邮箱；仅本人和认证服务可见',
    phone VARCHAR(32) NULL COMMENT '登录手机号；仅本人和认证服务可见',
    password_hash VARCHAR(100) NULL COMMENT 'BCrypt 密码哈希；绝不保存或返回密码明文',
    account_status VARCHAR(32) NOT NULL DEFAULT 'ANONYMOUS' COMMENT 'ANONYMOUS、ACTIVE 或 DISABLED',
    last_login_at TIMESTAMP NULL COMMENT '最近一次凭证登录成功时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '用户创建时间',
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone)
) COMMENT='私密账号与匿名通勤身份的主表';

-- 可控公开资料：展示必须服从匹配解锁等级，永不包含账号联系方式。
CREATE TABLE t_profiles (
    user_id CHAR(36) PRIMARY KEY COMMENT '资料所属内部用户 UUID',
    avatar_key VARCHAR(128) NOT NULL COMMENT '客户端虚拟头像资源标识',
    nickname VARCHAR(64) NULL COMMENT '公开昵称；匹配展示遵循解锁规则',
    region_code VARCHAR(32) NULL COMMENT '公开地区代码；不得存储精确位置',
    region_name VARCHAR(64) NULL COMMENT '公开地区名称，例如上海市',
    interest_tags JSON NOT NULL COMMENT '兴趣标签 JSON 数组',
    commute_status VARCHAR(64) NULL COMMENT '当前通勤状态，例如 idle/searching',
    profile_visibility VARCHAR(32) NOT NULL DEFAULT 'MATCH_ONLY' COMMENT 'MATCH_ONLY 或 PRIVATE',
    is_virtual BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为明确标识的虚拟路人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    CONSTRAINT fk_t_profiles_user FOREIGN KEY (user_id) REFERENCES t_users (id)
) COMMENT='受控公开资料';

-- 不透明匿名 Bearer 会话：数据库只保留 token 摘要。
CREATE TABLE t_anonymous_sessions (
    id CHAR(36) PRIMARY KEY COMMENT '会话 UUID',
    user_id CHAR(36) NOT NULL COMMENT '会话关联用户 UUID',
    token_digest CHAR(64) NOT NULL COMMENT 'SHA-256 token 摘要；禁止保存 token 明文',
    expires_at TIMESTAMP NOT NULL COMMENT '会话自然失效时间',
    revoked_at TIMESTAMP NULL COMMENT '主动注销时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间',
    UNIQUE KEY uk_anonymous_session_token_digest (token_digest),
    INDEX idx_anonymous_session_expiry (expires_at),
    CONSTRAINT fk_t_anonymous_sessions_user FOREIGN KEY (user_id) REFERENCES t_users (id)
) COMMENT='匿名会话';

-- 站点级通勤摘要：仅保存派生结果，24 小时后清理。
CREATE TABLE t_commute_station_summaries (
    id CHAR(36) PRIMARY KEY COMMENT '摘要 UUID',
    user_id CHAR(36) NOT NULL COMMENT '上报摘要的匿名用户',
    city_code VARCHAR(16) NULL COMMENT '城市 adcode',
    line_id VARCHAR(64) NULL COMMENT '线路 ID',
    station_code VARCHAR(64) NOT NULL COMMENT '站点代码，不是经纬度',
    station_name VARCHAR(128) NOT NULL COMMENT '展示用站点名称',
    occurred_at TIMESTAMP NOT NULL COMMENT '站点通勤事件时间',
    status_summary VARCHAR(256) NOT NULL DEFAULT 'searching' COMMENT '本地派生通勤状态摘要',
    direction_confidence DECIMAL(3,2) NOT NULL DEFAULT 0.00 COMMENT '本地计算方向相似度，范围 0.00–1.00',
    expires_at TIMESTAMP NOT NULL COMMENT '过期删除时间',
    INDEX idx_station_summary_expiry (expires_at),
    INDEX idx_station_summary_match (station_code, occurred_at, direction_confidence),
    INDEX idx_station_summary_line (city_code, line_id, occurred_at),
    CONSTRAINT fk_t_commute_station_summaries_user FOREIGN KEY (user_id) REFERENCES t_users (id)
) COMMENT='无原始定位的站点级通勤摘要';

-- 有效擦肩事件：同城同线（或同站兜底）判断通过后创建，24 小时后清理。
CREATE TABLE t_match_events (
    id CHAR(36) PRIMARY KEY COMMENT '匹配事件 UUID',
    first_user_id CHAR(36) NOT NULL COMMENT '排序后较小的匿名用户 UUID',
    second_user_id CHAR(36) NOT NULL COMMENT '排序后较大的匿名用户 UUID',
    city_code VARCHAR(16) NULL COMMENT '城市 adcode',
    line_id VARCHAR(64) NULL COMMENT '线路 ID',
    station_code VARCHAR(64) NOT NULL COMMENT '擦肩发生站点代码',
    occurred_at TIMESTAMP NOT NULL COMMENT '擦肩事件时间',
    expires_at TIMESTAMP NOT NULL COMMENT '过期删除时间',
    pair_key VARCHAR(128) NOT NULL COMMENT '两位用户组成的稳定配对键',
    INDEX idx_match_event_expiry (expires_at),
    UNIQUE KEY uk_match_event_pair_window (pair_key, station_code, occurred_at)
) COMMENT='有效擦肩匹配事件';

-- 渐进式解锁状态：真实信息交换只保存双方同意状态，不保存真实联系方式。
CREATE TABLE t_match_unlock_states (
    id CHAR(36) PRIMARY KEY COMMENT '解锁状态 UUID',
    match_event_id CHAR(36) NOT NULL COMMENT '对应匹配事件',
    unlock_level TINYINT NOT NULL DEFAULT 1 COMMENT '1匿名资料、2互动、3聊天、4双方确认',
    first_user_interested BOOLEAN NOT NULL DEFAULT FALSE COMMENT '第一位用户是否感兴趣',
    second_user_interested BOOLEAN NOT NULL DEFAULT FALSE COMMENT '第二位用户是否感兴趣',
    first_user_unlock_consent BOOLEAN NOT NULL DEFAULT FALSE COMMENT '第一位用户是否同意聊天解锁',
    second_user_unlock_consent BOOLEAN NOT NULL DEFAULT FALSE COMMENT '第二位用户是否同意聊天解锁',
    first_user_real_info_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '第一位用户是否确认真实信息交换',
    second_user_real_info_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '第二位用户是否确认真实信息交换',
    real_info_exchange_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已达成双方确认状态',
    CONSTRAINT fk_t_match_unlock_states_match_event FOREIGN KEY (match_event_id) REFERENCES t_match_events (id)
) COMMENT='匹配解锁状态';

-- 限时聊天会话：匹配事件清理后关联置空，会话按自身窗口继续保留。
CREATE TABLE t_chat_sessions (
    id CHAR(36) PRIMARY KEY COMMENT '聊天会话 UUID',
    match_event_id CHAR(36) NULL COMMENT '来源匹配事件；事件清理后置空',
    ride_ends_at TIMESTAMP NULL COMMENT '预计同行结束时间',
    reply_window_ends_at TIMESTAMP NULL COMMENT '下车后 24 小时回复截止时间',
    status VARCHAR(32) NOT NULL COMMENT 'RIDING、BOTTLE 或 EXPIRED',
    UNIQUE KEY uk_chat_sessions_match_event (match_event_id),
    CONSTRAINT fk_t_chat_sessions_match_event FOREIGN KEY (match_event_id) REFERENCES t_match_events (id) ON DELETE SET NULL
) COMMENT='限时聊天会话';

-- 聊天消息：只保存 AES-256-GCM 密文，7 天后清理。
CREATE TABLE t_chat_messages (
    id CHAR(36) PRIMARY KEY COMMENT '消息 UUID',
    session_id CHAR(36) NOT NULL COMMENT '所属聊天会话',
    sender_id CHAR(36) NOT NULL COMMENT '发送者匿名用户 UUID',
    ciphertext TEXT NOT NULL COMMENT 'AES-256-GCM 密文；禁止保存明文',
    nonce VARCHAR(64) NOT NULL COMMENT '每条消息独立随机 nonce',
    key_version VARCHAR(32) NOT NULL COMMENT '加密密钥版本',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    expires_at TIMESTAMP NOT NULL COMMENT '消息和关联举报材料的删除时间',
    INDEX idx_chat_message_expiry (expires_at),
    CONSTRAINT fk_t_chat_messages_session FOREIGN KEY (session_id) REFERENCES t_chat_sessions (id)
) COMMENT='加密聊天消息';

-- 消息举报：只关联加密消息、受控证据引用与原因，7 天后随消息清理。
CREATE TABLE t_message_reports (
    id CHAR(36) PRIMARY KEY COMMENT '举报 UUID',
    message_id CHAR(36) NOT NULL COMMENT '被举报消息 UUID',
    reporter_id CHAR(36) NOT NULL COMMENT '举报人匿名用户 UUID',
    reason VARCHAR(128) NOT NULL COMMENT '举报原因枚举或文本',
    evidence_ref VARCHAR(255) NULL COMMENT '受控证据文件引用；不存消息明文',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING、RESOLVED 等处理状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报提交时间',
    expires_at TIMESTAMP NOT NULL COMMENT '举报材料删除时间',
    INDEX idx_message_report_expiry (expires_at),
    CONSTRAINT fk_t_message_reports_message FOREIGN KEY (message_id) REFERENCES t_chat_messages (id)
) COMMENT='消息举报';

-- 车厢匿名留言板：作者仅服务端内部保留，不对客户端返回。
CREATE TABLE t_board_posts (
    id CHAR(36) PRIMARY KEY COMMENT '留言 UUID',
    author_id CHAR(36) NOT NULL COMMENT '匿名作者 UUID，仅服务端内部使用',
    line_code VARCHAR(64) NOT NULL COMMENT '地铁或公交线路代码',
    content TEXT NOT NULL COMMENT '纯文字匿名留言',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '留言发布时间',
    INDEX idx_board_post_line_created (line_code, created_at)
) COMMENT='匿名线路留言板';

-- 留言点赞：一位用户对一条留言至多一个点赞。
CREATE TABLE t_board_post_likes (
    post_id CHAR(36) NOT NULL COMMENT '留言 UUID',
    user_id CHAR(36) NOT NULL COMMENT '点赞用户 UUID，仅服务端使用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_t_board_post_likes_post FOREIGN KEY (post_id) REFERENCES t_board_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_t_board_post_likes_user FOREIGN KEY (user_id) REFERENCES t_users (id) ON DELETE CASCADE
) COMMENT='匿名留言点赞关系';

-- 留言评论：对外只返回匿名别名和头像资源 Key。
CREATE TABLE t_board_comments (
    id CHAR(36) PRIMARY KEY COMMENT '评论 UUID',
    post_id CHAR(36) NOT NULL COMMENT '所属留言 UUID',
    author_id CHAR(36) NOT NULL COMMENT '匿名作者 UUID，仅服务端使用',
    content VARCHAR(1000) NOT NULL COMMENT '纯文字评论',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_board_comments_post_created (post_id, created_at),
    CONSTRAINT fk_t_board_comments_post FOREIGN KEY (post_id) REFERENCES t_board_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_t_board_comments_user FOREIGN KEY (author_id) REFERENCES t_users (id) ON DELETE CASCADE
) COMMENT='匿名留言评论';

-- 每日通勤报告：每个用户每天最多一条。
CREATE TABLE t_commute_reports (
    id CHAR(36) PRIMARY KEY COMMENT '报告 UUID',
    user_id CHAR(36) NOT NULL COMMENT '报告所属匿名用户',
    report_date DATE NOT NULL COMMENT '报告对应日期',
    encounter_count INT NOT NULL DEFAULT 0 COMMENT '当日有效擦肩次数',
    same_route_count INT NOT NULL DEFAULT 0 COMMENT '当日同路线匹配次数',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报告生成时间',
    UNIQUE KEY uk_commute_report_user_date (user_id, report_date),
    CONSTRAINT fk_t_commute_reports_user FOREIGN KEY (user_id) REFERENCES t_users (id)
) COMMENT='每日通勤报告';

-- 可同步的通勤偏好；不保存原始定位。
CREATE TABLE t_commute_preferences (
    user_id CHAR(36) PRIMARY KEY,
    start_time CHAR(5) NOT NULL DEFAULT '07:30',
    end_time CHAR(5) NOT NULL DEFAULT '09:30',
    preferred_line_ids JSON NOT NULL,
    auto_match BOOLEAN NOT NULL DEFAULT TRUE,
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    stealth BOOLEAN NOT NULL DEFAULT FALSE,
    favorite_station_code VARCHAR(64) NULL,
    favorite_station_name VARCHAR(128) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_commute_preferences_user FOREIGN KEY (user_id) REFERENCES t_users (id) ON DELETE CASCADE
) COMMENT='用户显式通勤偏好，不含原始坐标';

-- 成就首次解锁记录；进度从业务事实聚合。
CREATE TABLE t_user_achievements (
    user_id CHAR(36) NOT NULL,
    achievement_code VARCHAR(64) NOT NULL,
    earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, achievement_code),
    CONSTRAINT fk_t_user_achievements_user FOREIGN KEY (user_id) REFERENCES t_users (id) ON DELETE CASCADE
) COMMENT='用户成就首次解锁时间';

-- 高德地铁城市（公共线网，非用户轨迹）。
CREATE TABLE t_metro_cities (
    city_code VARCHAR(16) PRIMARY KEY COMMENT '高德地铁 adcode，例如 3100',
    city_name VARCHAR(64) NOT NULL COMMENT '城市显示名',
    spell VARCHAR(64) NOT NULL COMMENT '线路图文件用拼音，例如 shanghai',
    synced_at TIMESTAMP NULL COMMENT '最近一次成功同步时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '行更新时间'
) COMMENT='有地铁的城市列表';

CREATE TABLE t_metro_lines (
    line_id VARCHAR(64) PRIMARY KEY COMMENT '高德线路 ID',
    city_code VARCHAR(16) NOT NULL COMMENT '所属城市 adcode',
    line_code VARCHAR(64) NOT NULL COMMENT '匹配用线路代码（通常同 line_id）',
    line_name VARCHAR(128) NOT NULL COMMENT '线路显示名，例如 2号线',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
    INDEX idx_metro_lines_city (city_code),
    CONSTRAINT fk_t_metro_lines_city FOREIGN KEY (city_code) REFERENCES t_metro_cities (city_code)
) COMMENT='城市地铁线路';

CREATE TABLE t_metro_stations (
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

INSERT INTO t_schema_versions (version) VALUES ('2026-08-22-release-readiness');
