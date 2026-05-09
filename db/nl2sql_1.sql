

CREATE EXTENSION IF NOT EXISTS vector;

DROP SEQUENCE IF EXISTS "public"."nl2sql_dataset_column_id_seq";
CREATE SEQUENCE "public"."nl2sql_dataset_column_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_dataset_column_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for nl2sql_dataset_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."nl2sql_dataset_id_seq";
CREATE SEQUENCE "public"."nl2sql_dataset_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_dataset_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for nl2sql_dataset_relation_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."nl2sql_dataset_relation_id_seq";
CREATE SEQUENCE "public"."nl2sql_dataset_relation_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_dataset_relation_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for nl2sql_dataset_sample_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."nl2sql_dataset_sample_id_seq";
CREATE SEQUENCE "public"."nl2sql_dataset_sample_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_dataset_sample_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for nl2sql_dataset_segment_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."nl2sql_dataset_segment_id_seq";
CREATE SEQUENCE "public"."nl2sql_dataset_segment_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_dataset_segment_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for nl2sql_dataset_table_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."nl2sql_dataset_table_id_seq";
CREATE SEQUENCE "public"."nl2sql_dataset_table_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_dataset_table_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for nl2sql_datasource_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."nl2sql_datasource_id_seq";
CREATE SEQUENCE "public"."nl2sql_datasource_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_datasource_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for nl2sql_knowledge_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."nl2sql_knowledge_id_seq";
CREATE SEQUENCE "public"."nl2sql_knowledge_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_knowledge_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for nl2sql_message_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."nl2sql_message_id_seq";
CREATE SEQUENCE "public"."nl2sql_message_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_message_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for nl2sql_session_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."nl2sql_session_id_seq";
CREATE SEQUENCE "public"."nl2sql_session_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."nl2sql_session_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Table structure for nl2sql_dataset
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_dataset";
CREATE TABLE "public"."nl2sql_dataset" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_dataset_id_seq'::regclass),
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "datasource_id" int8 NOT NULL,
  "description" varchar(500) COLLATE "pg_catalog"."default",
  "learn_status" int2 DEFAULT 0,
  "learn_time" timestamp(6),
  "status" int2 DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "tenant_id" int8,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_dataset" OWNER TO "csiyj";
COMMENT ON TABLE "public"."nl2sql_dataset" IS 'NL2SQL 数据集（语义层）';


-- ----------------------------
-- Table structure for nl2sql_dataset_column
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_dataset_column";
CREATE TABLE "public"."nl2sql_dataset_column" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_dataset_column_id_seq'::regclass),
  "dataset_id" int8 NOT NULL,
  "table_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "column_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "column_type" varchar(50) COLLATE "pg_catalog"."default",
  "column_comment" varchar(500) COLLATE "pg_catalog"."default",
  "user_remark" varchar(500) COLLATE "pg_catalog"."default",
  "sample_values" varchar(1000) COLLATE "pg_catalog"."default",
  "is_dimension" bool DEFAULT false,
  "is_measure" bool DEFAULT false,
  "is_primary_key" bool DEFAULT false,
  "sort_order" int4 DEFAULT 0,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_dataset_column" OWNER TO "csiyj";
COMMENT ON TABLE "public"."nl2sql_dataset_column" IS '数据集字段元数据';


-- ----------------------------
-- Table structure for nl2sql_dataset_relation
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_dataset_relation";
CREATE TABLE "public"."nl2sql_dataset_relation" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_dataset_relation_id_seq'::regclass),
  "dataset_id" int8 NOT NULL,
  "source_table" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "source_column" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "target_table" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "target_column" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "relation_type" varchar(20) COLLATE "pg_catalog"."default" DEFAULT 'LEFT JOIN'::character varying,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_dataset_relation" OWNER TO "csiyj";
COMMENT ON TABLE "public"."nl2sql_dataset_relation" IS '数据集表关系';


-- ----------------------------
-- Table structure for nl2sql_dataset_sample
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_dataset_sample";
CREATE TABLE "public"."nl2sql_dataset_sample" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_dataset_sample_id_seq'::regclass),
  "dataset_id" int8 NOT NULL,
  "question" text COLLATE "pg_catalog"."default" NOT NULL,
  "sql_text" text COLLATE "pg_catalog"."default" NOT NULL,
  "explanation" text COLLATE "pg_catalog"."default",
  "source" varchar(20) COLLATE "pg_catalog"."default" DEFAULT 'auto'::character varying,
  "is_verified" bool DEFAULT false,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_dataset_sample" OWNER TO "csiyj";
COMMENT ON TABLE "public"."nl2sql_dataset_sample" IS 'NL2SQL Few-Shot 样本库';


-- ----------------------------
-- Table structure for nl2sql_dataset_segment
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_dataset_segment";
CREATE TABLE "public"."nl2sql_dataset_segment" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_dataset_segment_id_seq'::regclass),
  "dataset_id" int8 NOT NULL,
  "segment_type" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
  "ref_id" int8,
  "ref_table" varchar(200) COLLATE "pg_catalog"."default",
  "ref_label" varchar(300) COLLATE "pg_catalog"."default",
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "token_count" int4 DEFAULT 0,
  "embedding" "public"."vector",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_dataset_segment" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."nl2sql_dataset_segment"."embedding" IS 'Vector(1024) 默认对齐 DashScope text-embedding-v3，余弦距离检索';
COMMENT ON TABLE "public"."nl2sql_dataset_segment" IS 'NL2SQL 数据集向量索引段（RAG schema retrieval）';


-- ----------------------------
-- Table structure for nl2sql_dataset_table
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_dataset_table";
CREATE TABLE "public"."nl2sql_dataset_table" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_dataset_table_id_seq'::regclass),
  "dataset_id" int8 NOT NULL,
  "table_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "table_comment" varchar(500) COLLATE "pg_catalog"."default",
  "table_alias" varchar(100) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_dataset_table" OWNER TO "csiyj";
COMMENT ON TABLE "public"."nl2sql_dataset_table" IS '数据集包含的表';


-- ----------------------------
-- Table structure for nl2sql_datasource
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_datasource";
CREATE TABLE "public"."nl2sql_datasource" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_datasource_id_seq'::regclass),
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "type" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "host" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "port" int4 NOT NULL,
  "database_name" varchar(100) COLLATE "pg_catalog"."default",
  "schema_name" varchar(100) COLLATE "pg_catalog"."default",
  "username" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "password" varchar(500) COLLATE "pg_catalog"."default" NOT NULL,
  "extra_params" text COLLATE "pg_catalog"."default",
  "status" int2 DEFAULT 1,
  "description" varchar(500) COLLATE "pg_catalog"."default",
  "last_test_time" timestamp(6),
  "last_test_status" int2,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "tenant_id" int8,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_datasource" OWNER TO "csiyj";
COMMENT ON TABLE "public"."nl2sql_datasource" IS 'NL2SQL 外部数据源连接配置';


-- ----------------------------
-- Table structure for nl2sql_knowledge
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_knowledge";
CREATE TABLE "public"."nl2sql_knowledge" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_knowledge_id_seq'::regclass),
  "dataset_id" int8 NOT NULL,
  "type" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "title" varchar(200) COLLATE "pg_catalog"."default",
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "status" int2 DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "tenant_id" int8,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_knowledge" OWNER TO "csiyj";
COMMENT ON TABLE "public"."nl2sql_knowledge" IS 'NL2SQL 业务知识库';


-- ----------------------------
-- Table structure for nl2sql_message
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_message";
CREATE TABLE "public"."nl2sql_message" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_message_id_seq'::regclass),
  "session_id" int8 NOT NULL,
  "role" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
  "content" text COLLATE "pg_catalog"."default",
  "generated_sql" text COLLATE "pg_catalog"."default",
  "sql_status" int2,
  "query_result" text COLLATE "pg_catalog"."default",
  "result_count" int4,
  "execution_time" int4,
  "chart_config" text COLLATE "pg_catalog"."default",
  "chart_type" varchar(20) COLLATE "pg_catalog"."default",
  "dimensions" text COLLATE "pg_catalog"."default",
  "measures" text COLLATE "pg_catalog"."default",
  "data_insight" text COLLATE "pg_catalog"."default",
  "error_message" text COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_message" OWNER TO "csiyj";
COMMENT ON TABLE "public"."nl2sql_message" IS 'NL2SQL 对话消息';


-- ----------------------------
-- Table structure for nl2sql_session
-- ----------------------------
DROP TABLE IF EXISTS "public"."nl2sql_session";
CREATE TABLE "public"."nl2sql_session" (
  "id" int8 NOT NULL DEFAULT nextval('nl2sql_session_id_seq'::regclass),
  "title" varchar(200) COLLATE "pg_catalog"."default",
  "dataset_id" int8 NOT NULL,
  "model_id" int8,
  "user_id" int8,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "tenant_id" int8,
  "del_flag" varchar(2) COLLATE "pg_catalog"."default" DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."nl2sql_session" OWNER TO "csiyj";
COMMENT ON TABLE "public"."nl2sql_session" IS 'NL2SQL 对话会话';


