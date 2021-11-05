/*
 Navicat Premium Data Transfer

 Source Server         : Canhlab
 Source Server Type    : PostgreSQL
 Source Server Version : 130002
 Source Host           : 139.59.124.197:5432
 Source Catalog        : assessment
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 130002
 File Encoding         : 65001

 Date: 05/11/2021 07:22:51
*/


-- ----------------------------
-- Table structure for share_links
-- ----------------------------
DROP TABLE IF EXISTS "public"."share_links";
CREATE TABLE "public"."share_links" (
  "id" int4 NOT NULL GENERATED ALWAYS AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
),
  "url_link" text COLLATE "pg_catalog"."default",
  "embed_link" text COLLATE "pg_catalog"."default",
  "created_at" timestamptz(0),
  "updated_at" timestamptz(0),
  "description" text COLLATE "pg_catalog"."default",
  "title" text COLLATE "pg_catalog"."default",
  "user_id" int4,
  "up_count" int4,
  "down_count" int4
)
;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS "public"."users";
CREATE TABLE "public"."users" (
  "id" int4 NOT NULL GENERATED ALWAYS AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
),
  "user_name" text COLLATE "pg_catalog"."default",
  "password" text COLLATE "pg_catalog"."default",
  "created_at" timestamptz(0),
  "updated_at" timestamptz(0)
)
;

-- ----------------------------
-- Primary Key structure for table share_links
-- ----------------------------
ALTER TABLE "public"."share_links" ADD CONSTRAINT "share_links_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Uniques structure for table users
-- ----------------------------
ALTER TABLE "public"."users" ADD CONSTRAINT "users_user_name" UNIQUE ("user_name");

-- ----------------------------
-- Primary Key structure for table users
-- ----------------------------
ALTER TABLE "public"."users" ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");
