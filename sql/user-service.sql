-- 用户历史密码表
-- auto-generated definition

create table password_history
(
    id         bigint auto_increment comment '记录ID，主键，自增'
        primary key,
    user_id    bigint       not null comment '用户ID，关联user表',
    PASSWORD   varchar(255) not null comment '历史密码（加密后）',
    salt       varchar(50)  null comment '历史密码盐值',
    created_at datetime     null comment '创建时间'
)
    comment '用户历史密码表';
-- 用户表
-- auto-generated definition
create table users
(
    id         bigint auto_increment comment '用户ID，主键，自增'
        primary key,
    username   varchar(50)  not null comment '用户名，唯一',
    PASSWORD   varchar(255) not null comment '密码（加密后）',
    nick_name  varchar(50)  null comment '昵称',
    salt       varchar(50)  null comment '密码盐值',
    phone      varchar(20)  null comment '手机号',
    email      varchar(100) null comment '邮箱',
    avatar_url varchar(255) null comment '头像URL',
    signature  varchar(255) null comment '个性签名',
    profile    varchar(500) null comment '个人简介',
    role       tinyint      null comment '角色',
    created_at datetime     null comment '创建时间',
    updated_at datetime     null comment '更新时间',
    constraint username
        unique (username)
)
    comment '用户表';
