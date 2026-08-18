-- 2 库 × 4 表 = 8 个点赞事实物理分片。
CREATE DATABASE IF NOT EXISTS ma_community_ds0 DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS ma_community_ds1 DEFAULT CHARACTER SET utf8mb4;
USE ma_community_ds0;
CREATE TABLE IF NOT EXISTS post_like_0 LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS post_like_1 LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS post_like_2 LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS post_like_3 LIKE ma_community.post_like;
USE ma_community_ds1;
CREATE TABLE IF NOT EXISTS post_like_0 LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS post_like_1 LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS post_like_2 LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS post_like_3 LIKE ma_community.post_like;
