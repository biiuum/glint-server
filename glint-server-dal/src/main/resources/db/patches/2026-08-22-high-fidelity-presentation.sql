-- 高保真页面所需的用户偏好、留言互动与成就首次解锁记录。
-- 仅保存显式选择和聚合状态，不保存原始定位或传感器数据。

CREATE TABLE IF NOT EXISTS t_board_post_likes (
    post_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_t_board_post_likes_post FOREIGN KEY (post_id) REFERENCES t_board_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_t_board_post_likes_user FOREIGN KEY (user_id) REFERENCES t_users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS t_board_comments (
    id CHAR(36) PRIMARY KEY,
    post_id CHAR(36) NOT NULL,
    author_id CHAR(36) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_board_comments_post_created (post_id, created_at),
    CONSTRAINT fk_t_board_comments_post FOREIGN KEY (post_id) REFERENCES t_board_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_t_board_comments_user FOREIGN KEY (author_id) REFERENCES t_users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS t_commute_preferences (
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
);

CREATE TABLE IF NOT EXISTS t_user_achievements (
    user_id CHAR(36) NOT NULL,
    achievement_code VARCHAR(64) NOT NULL,
    earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, achievement_code),
    CONSTRAINT fk_t_user_achievements_user FOREIGN KEY (user_id) REFERENCES t_users (id) ON DELETE CASCADE
);
