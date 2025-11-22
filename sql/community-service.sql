-- 帖子表
create table posts
(
    id              bigint auto_increment comment '帖子ID，主键，自增'
        primary key,
    user_id         bigint        not null comment '发帖用户ID，关联users表',
    title           varchar(255)  not null comment '帖子标题',
    content         text          not null comment '帖子正文',
    STATUS          tinyint       not null default 0 comment '帖子状态（正常、删除、审核中等）',
    comment_count   int           not null default 0 comment '评论总数（包含楼和楼中楼）',
    last_comment_at datetime      null comment '最后评论时间',
    created_at      datetime      null comment '创建时间',
    updated_at      datetime      null comment '更新时间',
    index idx_user_created (user_id, created_at)
)
    comment '帖子表';

create table post_comments
(
    id               bigint auto_increment comment '评论ID，主键，自增'
        primary key,
    post_id          bigint       not null comment '关联帖子ID，关联posts表',
    root_id          bigint       null comment '所属楼ID，一级评论时等于自身ID，楼中楼为所属楼的ID',
    parent_id        bigint       null comment '父评论ID，一级评论为空，楼中楼为被回复的评论ID',
    comment_type     tinyint      not null comment '评论类型：1-楼(一级)，2-楼中楼',
    user_id          bigint       not null comment '评论用户ID，关联users表',
    reply_to_user_id bigint       null comment '被回复用户ID，方便展示@谁',
    content          text         not null comment '评论内容',
    floor_no         int          null comment '楼层号，仅一级评论使用，从1开始递增',
    reply_count      int          not null default 0 comment '回复数量，主要用于一级评论',
    STATUS           tinyint      not null default 0 comment '评论状态（正常、删除、审核中等）',
    created_at       datetime     null comment '创建时间',
    updated_at       datetime     null comment '更新时间',
    index idx_post_type_created (post_id, comment_type, created_at),
    index idx_root_created       (root_id, created_at),
    index idx_parent             (parent_id),
    index idx_user_created       (user_id, created_at)
)
    comment '帖子评论表';

