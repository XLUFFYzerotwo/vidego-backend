CREATE DATABASE IF NOT EXISTS vidego DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE vidego;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL UNIQUE,
  `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt encrypted',
  `email` VARCHAR(100) DEFAULT NULL,
  `nickname` VARCHAR(50) DEFAULT NULL,
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT 'MinIO object key',
  `bio` VARCHAR(200) DEFAULT NULL,
  `follower_count` INT DEFAULT 0,
  `following_count` INT DEFAULT 0,
  `video_count` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '1:normal 0:disabled',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_username` (`username`),
  INDEX `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user table';

-- 视频表
CREATE TABLE IF NOT EXISTS `video` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `description` TEXT,
  `video_key` VARCHAR(255) NOT NULL COMMENT 'MinIO object key',
  `cover_key` VARCHAR(255) DEFAULT NULL COMMENT 'MinIO object key',
  `duration` INT DEFAULT 0 COMMENT 'duration in seconds',
  `size` BIGINT DEFAULT 0 COMMENT 'file size in bytes',
  `status` TINYINT DEFAULT 0 COMMENT '0:processing 1:published -1:failed',
  `view_count` INT DEFAULT 0,
  `like_count` INT DEFAULT 0,
  `comment_count` INT DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `hot_score` INT DEFAULT 0,
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status_created` (`status`, `created_at`) COMMENT 'homepage feed/search pagination',
  FULLTEXT INDEX `ft_title_desc` (`title`, `description`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='video table';

-- 评论表
CREATE TABLE IF NOT EXISTS `comment` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `video_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `parent_id` BIGINT DEFAULT NULL COMMENT 'NULL means root comment',
  `content` VARCHAR(500) NOT NULL,
  `like_count` INT DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_video_parent_created` (video_id, parent_id, created_at),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='comment table';

-- 点赞记录表 (通用：视频 + 评论)
CREATE TABLE IF NOT EXISTS `like_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `target_type` VARCHAR(20) NOT NULL COMMENT 'video / comment',
  `target_id` BIGINT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE INDEX `idx_user_target` (`user_id`, `target_type`, `target_id`),
  INDEX `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='like record';

-- 标签表
CREATE TABLE IF NOT EXISTS `tag` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `name` VARCHAR(30) NOT NULL UNIQUE,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tag table';

-- 视频-标签关联表
CREATE TABLE IF NOT EXISTS `video_tag` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `video_id` BIGINT NOT NULL,
  `tag_id` BIGINT NOT NULL,
  UNIQUE INDEX `idx_video_tag` (`video_id`, `tag_id`),
  INDEX `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='video-tag relation';

-- 关注表
CREATE TABLE IF NOT EXISTS `follow` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `follower_id` BIGINT NOT NULL COMMENT 'who follows',
  `following_id` BIGINT NOT NULL COMMENT 'who is followed',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE INDEX `idx_follower_following` (`follower_id`, `following_id`),
  INDEX `idx_following_id` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='follow relation';

-- 收藏表
CREATE TABLE IF NOT EXISTS `favorite` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `video_id` BIGINT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE INDEX `idx_user_video` (`user_id`, `video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='favorite relation';

-- 弹幕表
CREATE TABLE danmaku (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         video_id BIGINT NOT NULL,
                         user_id BIGINT NOT NULL,
                         content VARCHAR(100) NOT NULL COMMENT '弹幕内容，最长100字',
                         time FLOAT NOT NULL COMMENT '视频时间点（秒），支持小数',
                         color VARCHAR(20) DEFAULT '#FFFFFF' COMMENT '弹幕颜色',
                         type TINYINT DEFAULT 0 COMMENT '0:滚动 1:顶部 2:底部',
                         created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                         status TINYINT DEFAULT 1 COMMENT '1:显示 0:待审核 -1:屏蔽',
                         INDEX idx_video_time (video_id, time),
                         INDEX idx_user_id (user_id),
                         INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='弹幕表';
