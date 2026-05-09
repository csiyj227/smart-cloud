/*
 Smart Platform - Database Schema
 PostgreSQL
 
 Module: Gen (代码生成器)
 File: gen_1.sql (序列 + 表结构)
 
 Generated from public.sql split
*/

-- ----------------------------
-- Sequence structure for gen_table_column_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."gen_table_column_id_seq";
CREATE SEQUENCE "public"."gen_table_column_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."gen_table_column_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for gen_table_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."gen_table_id_seq";
CREATE SEQUENCE "public"."gen_table_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."gen_table_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for gen_template_group_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."gen_template_group_id_seq";
CREATE SEQUENCE "public"."gen_template_group_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."gen_template_group_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for gen_template_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."gen_template_id_seq";
CREATE SEQUENCE "public"."gen_template_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."gen_template_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Table structure for gen_table
-- ----------------------------
DROP TABLE IF EXISTS "public"."gen_table";
CREATE TABLE "public"."gen_table" (
  "id" int8 NOT NULL DEFAULT nextval('gen_table_id_seq'::regclass),
  "table_name" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "table_comment" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "sub_table_name" varchar(256) COLLATE "pg_catalog"."default",
  "sub_table_fk" varchar(128) COLLATE "pg_catalog"."default",
  "class_name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "tpl_category" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "package_name" varchar(256) COLLATE "pg_catalog"."default" DEFAULT 'com.smart'::character varying,
  "module_name" varchar(128) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "business_name" varchar(128) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "function_name" varchar(256) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "function_author" varchar(64) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "gen_type" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "gen_path" varchar(256) COLLATE "pg_catalog"."default" DEFAULT '/'::character varying,
  "options" jsonb,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now(),
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar
)
;
ALTER TABLE "public"."gen_table" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."gen_table"."tpl_category" IS 'Template category: 0=CRUD, 1=tree, 2=sub-table';
COMMENT ON COLUMN "public"."gen_table"."gen_type" IS 'Generate type: 0=download, 1=write to path';
COMMENT ON COLUMN "public"."gen_table"."del_flag" IS 'Logical delete flag: 0=normal, 1=deleted';
COMMENT ON TABLE "public"."gen_table" IS 'Code generation table metadata';


-- ----------------------------
-- Table structure for gen_table_column
-- ----------------------------
DROP TABLE IF EXISTS "public"."gen_table_column";
CREATE TABLE "public"."gen_table_column" (
  "id" int8 NOT NULL DEFAULT nextval('gen_table_column_id_seq'::regclass),
  "table_id" int8 NOT NULL,
  "column_name" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "column_comment" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "column_type" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "java_type" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "java_field" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "is_pk" bool DEFAULT false,
  "is_increment" bool DEFAULT false,
  "is_required" bool DEFAULT false,
  "is_insert" bool DEFAULT false,
  "is_edit" bool DEFAULT false,
  "is_list" bool DEFAULT false,
  "is_query" bool DEFAULT false,
  "query_type" varchar(32) COLLATE "pg_catalog"."default" DEFAULT 'EQ'::character varying,
  "html_type" varchar(32) COLLATE "pg_catalog"."default" DEFAULT 'input'::character varying,
  "dict_type" varchar(256) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "sort_order" int4 NOT NULL DEFAULT 0
)
;
ALTER TABLE "public"."gen_table_column" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."gen_table_column"."query_type" IS 'Query type: EQ=equals, LIKE, BETWEEN, GT, LT, etc.';
COMMENT ON COLUMN "public"."gen_table_column"."html_type" IS 'HTML widget type: input, select, radio, checkbox, textarea, date, datetime';
COMMENT ON TABLE "public"."gen_table_column" IS 'Code generation column metadata';


-- ----------------------------
-- Table structure for gen_template
-- ----------------------------
DROP TABLE IF EXISTS "public"."gen_template";
CREATE TABLE "public"."gen_template" (
  "id" int8 NOT NULL DEFAULT nextval('gen_template_id_seq'::regclass),
  "group_id" int8 NOT NULL,
  "template_name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "template_code" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "template_content" text COLLATE "pg_catalog"."default" NOT NULL,
  "file_path" varchar(512) COLLATE "pg_catalog"."default" NOT NULL,
  "file_extension" varchar(32) COLLATE "pg_catalog"."default" DEFAULT '.java'::character varying,
  "sort_order" int4 NOT NULL DEFAULT 0,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."gen_template" OWNER TO "csiyj";
COMMENT ON TABLE "public"."gen_template" IS 'Code generation Velocity templates (DB-stored)';


-- ----------------------------
-- Table structure for gen_template_group
-- ----------------------------
DROP TABLE IF EXISTS "public"."gen_template_group";
CREATE TABLE "public"."gen_template_group" (
  "id" int8 NOT NULL DEFAULT nextval('gen_template_group_id_seq'::regclass),
  "group_name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "group_code" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."gen_template_group" OWNER TO "csiyj";
COMMENT ON TABLE "public"."gen_template_group" IS 'Code generation template groups';


