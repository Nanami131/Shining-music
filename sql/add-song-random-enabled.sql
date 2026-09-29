USE `shining-music`;

ALTER TABLE songs
    ADD COLUMN random_enabled TINYINT NOT NULL DEFAULT 1
        COMMENT '是否启用随机推荐：1启用，0禁用'
        AFTER status;
