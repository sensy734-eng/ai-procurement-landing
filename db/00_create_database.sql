-- ==============================================================
-- 文件：00_create_database.sql
-- 作用：创建数据库
-- 警告：本脚本会连同数据一起删除同名库，仅在初始化与重置演示环境时执行
-- ==============================================================
SET NAMES utf8mb4;

DROP DATABASE IF EXISTS procurement_db;
CREATE DATABASE procurement_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE procurement_db;
