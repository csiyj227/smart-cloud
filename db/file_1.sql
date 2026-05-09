/*
 Smart Platform - Database Schema
 PostgreSQL
 
 Module: File (文件管理)
 File: file_1.sql (序列 + 表结构)
 
 Generated from public.sql split
*/

-- ----------------------------
-- Sequence structure for sys_file_chunk_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_file_chunk_id_seq";
CREATE SEQUENCE "public"."sys_file_chunk_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_file_chunk_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_file_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_file_id_seq";
CREATE SEQUENCE "public"."sys_file_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_file_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Table structure for sys_file
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_file";
CREATE TABLE "public"."sys_file" (
  "id" int8 NOT NULL DEFAULT nextval('sys_file_id_seq'::regclass),
  "original_name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "stored_name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "file_path" varchar(512) COLLATE "pg_catalog"."default" NOT NULL,
  "file_url" varchar(512) COLLATE "pg_catalog"."default",
  "file_size" int8 NOT NULL DEFAULT 0,
  "content_type" varchar(128) COLLATE "pg_catalog"."default",
  "file_ext" varchar(32) COLLATE "pg_catalog"."default",
  "md5" varchar(64) COLLATE "pg_catalog"."default",
  "storage_type" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'local'::character varying,
  "bucket_name" varchar(128) COLLATE "pg_catalog"."default",
  "version" int4 NOT NULL DEFAULT 1,
  "parent_id" int8,
  "is_latest" bool NOT NULL DEFAULT true,
  "in_recycle" bool NOT NULL DEFAULT false,
  "recycle_time" timestamp(6),
  "ref_count" int4 NOT NULL DEFAULT 1,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now(),
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar
)
;
ALTER TABLE "public"."sys_file" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_file"."original_name" IS '上传时的原始文件名';
COMMENT ON COLUMN "public"."sys_file"."stored_name" IS '实际存储文件名（uuid+ext）';
COMMENT ON COLUMN "public"."sys_file"."file_path" IS '完整存储路径或对象key';
COMMENT ON COLUMN "public"."sys_file"."md5" IS '文件 MD5（秒传依据）';
COMMENT ON COLUMN "public"."sys_file"."storage_type" IS '存储类型：local / minio';
COMMENT ON COLUMN "public"."sys_file"."version" IS '版本号，同名上传自增';
COMMENT ON COLUMN "public"."sys_file"."parent_id" IS '版本组根 id（null 表示自身就是根）';
COMMENT ON COLUMN "public"."sys_file"."is_latest" IS '是否当前最新版本';
COMMENT ON COLUMN "public"."sys_file"."in_recycle" IS '是否在回收站';
COMMENT ON COLUMN "public"."sys_file"."recycle_time" IS '进入回收站时间，30 天后由定时任务清理';
COMMENT ON COLUMN "public"."sys_file"."ref_count" IS '引用计数（秒传场景共享一份物理文件时 +1）';
COMMENT ON TABLE "public"."sys_file" IS '文件元数据表';


-- ----------------------------
-- Table structure for sys_file_chunk
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_file_chunk";
CREATE TABLE "public"."sys_file_chunk" (
  "id" int8 NOT NULL DEFAULT nextval('sys_file_chunk_id_seq'::regclass),
  "upload_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "original_name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "total_size" int8 NOT NULL,
  "chunk_size" int4 NOT NULL,
  "total_chunks" int4 NOT NULL,
  "uploaded_chunks" varchar(2048) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "file_md5" varchar(64) COLLATE "pg_catalog"."default",
  "status" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'uploading'::character varying,
  "merged_file_id" int8,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now(),
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar
)
;
ALTER TABLE "public"."sys_file_chunk" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_file_chunk"."upload_id" IS '客户端生成的上传任务唯一标识';
COMMENT ON COLUMN "public"."sys_file_chunk"."uploaded_chunks" IS '已上传分片序号，逗号分隔';
COMMENT ON COLUMN "public"."sys_file_chunk"."status" IS 'uploading / merged / failed';
COMMENT ON COLUMN "public"."sys_file_chunk"."merged_file_id" IS '合并完成后的 sys_file.id';
COMMENT ON TABLE "public"."sys_file_chunk" IS '分片上传任务表';


