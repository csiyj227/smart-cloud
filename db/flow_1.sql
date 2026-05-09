/*
 Smart Platform - Database Schema
 PostgreSQL
 
 Module: Flow (工作流引擎)
 File: flow_1.sql (序列 + 表结构)
 
 Generated from public.sql split
*/

-- ----------------------------
-- Sequence structure for biz_travel_apply_apply_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."biz_travel_apply_apply_id_seq";
CREATE SEQUENCE "public"."biz_travel_apply_apply_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."biz_travel_apply_apply_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for flow_approval_record_record_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."flow_approval_record_record_id_seq";
CREATE SEQUENCE "public"."flow_approval_record_record_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."flow_approval_record_record_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for flow_cc_record_cc_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."flow_cc_record_cc_id_seq";
CREATE SEQUENCE "public"."flow_cc_record_cc_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."flow_cc_record_cc_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for flow_definition_chart_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."flow_definition_chart_id_seq";
CREATE SEQUENCE "public"."flow_definition_chart_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."flow_definition_chart_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for flow_delegation_delegation_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."flow_delegation_delegation_id_seq";
CREATE SEQUENCE "public"."flow_delegation_delegation_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."flow_delegation_delegation_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for flow_form_binding_binding_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."flow_form_binding_binding_id_seq";
CREATE SEQUENCE "public"."flow_form_binding_binding_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."flow_form_binding_binding_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for flow_form_snapshot_snapshot_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."flow_form_snapshot_snapshot_id_seq";
CREATE SEQUENCE "public"."flow_form_snapshot_snapshot_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."flow_form_snapshot_snapshot_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for flow_instance_biz_biz_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."flow_instance_biz_biz_id_seq";
CREATE SEQUENCE "public"."flow_instance_biz_biz_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."flow_instance_biz_biz_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for flow_task_view_view_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."flow_task_view_view_id_seq";
CREATE SEQUENCE "public"."flow_task_view_view_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."flow_task_view_view_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_form_data_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_form_data_id_seq";
CREATE SEQUENCE "public"."sys_form_data_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_form_data_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_form_form_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_form_form_id_seq";
CREATE SEQUENCE "public"."sys_form_form_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_form_form_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Table structure for biz_travel_apply
-- ----------------------------
DROP TABLE IF EXISTS "public"."biz_travel_apply";
CREATE TABLE "public"."biz_travel_apply" (
  "apply_id" int8 NOT NULL DEFAULT nextval('biz_travel_apply_apply_id_seq'::regclass),
  "apply_no" varchar(64) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "applicant_id" int8 NOT NULL,
  "applicant_name" varchar(64) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "dept_name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "reason" varchar(500) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "departure" varchar(128) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "destination" varchar(128) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "start_time" timestamp(6) NOT NULL,
  "end_time" timestamp(6) NOT NULL,
  "transport" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "estimated_cost" numeric(10,2) NOT NULL DEFAULT 0,
  "remark" varchar(500) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "status" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'DRAFT'::character varying,
  "process_instance_id" varchar(64) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "update_time" timestamp(6) DEFAULT now(),
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar
)
;
ALTER TABLE "public"."biz_travel_apply" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."biz_travel_apply"."apply_id" IS '申请 ID';
COMMENT ON COLUMN "public"."biz_travel_apply"."apply_no" IS '申请单号';
COMMENT ON COLUMN "public"."biz_travel_apply"."applicant_id" IS '申请人 ID';
COMMENT ON COLUMN "public"."biz_travel_apply"."reason" IS '出差事由';
COMMENT ON COLUMN "public"."biz_travel_apply"."departure" IS '出发地';
COMMENT ON COLUMN "public"."biz_travel_apply"."destination" IS '目的地';
COMMENT ON COLUMN "public"."biz_travel_apply"."transport" IS '交通方式';
COMMENT ON COLUMN "public"."biz_travel_apply"."status" IS '状态：DRAFT-草稿 PENDING-审批中 APPROVED-已通过 REJECTED-已驳回';
COMMENT ON TABLE "public"."biz_travel_apply" IS '出差申请单';


-- ----------------------------
-- Table structure for flow_approval_record
-- ----------------------------
DROP TABLE IF EXISTS "public"."flow_approval_record";
CREATE TABLE "public"."flow_approval_record" (
  "record_id" int8 NOT NULL DEFAULT nextval('flow_approval_record_record_id_seq'::regclass),
  "process_instance_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "task_id" varchar(64) COLLATE "pg_catalog"."default",
  "node_key" varchar(64) COLLATE "pg_catalog"."default",
  "node_name" varchar(128) COLLATE "pg_catalog"."default",
  "action_type" varchar(16) COLLATE "pg_catalog"."default" NOT NULL,
  "actor_id" int8 NOT NULL,
  "actor_name" varchar(64) COLLATE "pg_catalog"."default",
  "target_user_id" int8,
  "target_user_name" varchar(64) COLLATE "pg_catalog"."default",
  "comment" text COLLATE "pg_catalog"."default",
  "attachments" text COLLATE "pg_catalog"."default",
  "occurred_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "del_flag" varchar(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."flow_approval_record" OWNER TO "csiyj";
COMMENT ON TABLE "public"."flow_approval_record" IS 'User-visible approval action log (append-only)';


-- ----------------------------
-- Table structure for flow_cc_record
-- ----------------------------
DROP TABLE IF EXISTS "public"."flow_cc_record";
CREATE TABLE "public"."flow_cc_record" (
  "cc_id" int8 NOT NULL DEFAULT nextval('flow_cc_record_cc_id_seq'::regclass),
  "process_instance_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "node_key" varchar(64) COLLATE "pg_catalog"."default",
  "node_name" varchar(128) COLLATE "pg_catalog"."default",
  "cc_user_id" int8 NOT NULL,
  "cc_user_name" varchar(64) COLLATE "pg_catalog"."default",
  "sent_by" int8,
  "sent_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "read_flag" varchar(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying,
  "read_at" timestamp(6),
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "del_flag" varchar(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."flow_cc_record" OWNER TO "csiyj";
COMMENT ON TABLE "public"."flow_cc_record" IS 'Carbon-copy notification entries (non-tasks)';


-- ----------------------------
-- Table structure for flow_definition
-- ----------------------------
DROP TABLE IF EXISTS "public"."flow_definition";
CREATE TABLE "public"."flow_definition" (
  "chart_id" int8 NOT NULL DEFAULT nextval('flow_definition_chart_id_seq'::regclass),
  "chart_key" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "chart_name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "chart_category" varchar(64) COLLATE "pg_catalog"."default",
  "chart_version" int4 NOT NULL DEFAULT 1,
  "publish_status" varchar(2) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying,
  "chart_dsl" text COLLATE "pg_catalog"."default",
  "bpmn_xml" text COLLATE "pg_catalog"."default",
  "deployment_id" varchar(64) COLLATE "pg_catalog"."default",
  "process_definition_id" varchar(255) COLLATE "pg_catalog"."default",
  "bound_form_id" int8,
  "description" varchar(512) COLLATE "pg_catalog"."default",
  "icon" varchar(64) COLLATE "pg_catalog"."default",
  "sort_order" int4 NOT NULL DEFAULT 0,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "del_flag" varchar(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."flow_definition" OWNER TO "csiyj";
COMMENT ON TABLE "public"."flow_definition" IS 'Process definition (template) - one row per version';


-- ----------------------------
-- Table structure for flow_delegation
-- ----------------------------
DROP TABLE IF EXISTS "public"."flow_delegation";
CREATE TABLE "public"."flow_delegation" (
  "delegation_id" int8 NOT NULL DEFAULT nextval('flow_delegation_delegation_id_seq'::regclass),
  "task_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "process_instance_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "delegation_type" varchar(16) COLLATE "pg_catalog"."default" NOT NULL,
  "from_user_id" int8 NOT NULL,
  "from_user_name" varchar(64) COLLATE "pg_catalog"."default",
  "to_user_id" int8 NOT NULL,
  "to_user_name" varchar(64) COLLATE "pg_catalog"."default",
  "reason" varchar(512) COLLATE "pg_catalog"."default",
  "occurred_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "del_flag" varchar(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."flow_delegation" OWNER TO "csiyj";
COMMENT ON TABLE "public"."flow_delegation" IS 'Audit trail of task transfers and delegations';


-- ----------------------------
-- Table structure for flow_form_binding
-- ----------------------------
DROP TABLE IF EXISTS "public"."flow_form_binding";
CREATE TABLE "public"."flow_form_binding" (
  "binding_id" int8 NOT NULL DEFAULT nextval('flow_form_binding_binding_id_seq'::regclass),
  "chart_id" int8 NOT NULL,
  "node_key" varchar(64) COLLATE "pg_catalog"."default",
  "form_id" int8 NOT NULL,
  "field_rules" text COLLATE "pg_catalog"."default",
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "del_flag" varchar(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."flow_form_binding" OWNER TO "csiyj";
COMMENT ON TABLE "public"."flow_form_binding" IS 'Per-node form field permission rules for a chart';


-- ----------------------------
-- Table structure for flow_form_snapshot
-- ----------------------------
DROP TABLE IF EXISTS "public"."flow_form_snapshot";
CREATE TABLE "public"."flow_form_snapshot" (
  "snapshot_id" int8 NOT NULL DEFAULT nextval('flow_form_snapshot_snapshot_id_seq'::regclass),
  "process_instance_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "task_id" varchar(64) COLLATE "pg_catalog"."default",
  "node_key" varchar(64) COLLATE "pg_catalog"."default",
  "form_id" int8 NOT NULL,
  "snapshot_type" varchar(2) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying,
  "payload" text COLLATE "pg_catalog"."default",
  "captured_by" int8,
  "captured_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "del_flag" varchar(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."flow_form_snapshot" OWNER TO "csiyj";
COMMENT ON TABLE "public"."flow_form_snapshot" IS 'Per-task form payload snapshot for audit replay';


-- ----------------------------
-- Table structure for flow_instance_biz
-- ----------------------------
DROP TABLE IF EXISTS "public"."flow_instance_biz";
CREATE TABLE "public"."flow_instance_biz" (
  "biz_id" int8 NOT NULL DEFAULT nextval('flow_instance_biz_biz_id_seq'::regclass),
  "process_instance_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "chart_id" int8 NOT NULL,
  "chart_key" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "chart_version" int4 NOT NULL,
  "biz_no" varchar(64) COLLATE "pg_catalog"."default",
  "title" varchar(255) COLLATE "pg_catalog"."default",
  "starter_id" int8 NOT NULL,
  "starter_name" varchar(64) COLLATE "pg_catalog"."default",
  "starter_dept_id" int8,
  "biz_status" varchar(2) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying,
  "latest_snapshot_id" int8,
  "start_time" timestamp(6),
  "end_time" timestamp(6),
  "duration_ms" int8,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "del_flag" varchar(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."flow_instance_biz" OWNER TO "csiyj";
COMMENT ON TABLE "public"."flow_instance_biz" IS 'Business view of a Flowable process instance';


-- ----------------------------
-- Table structure for flow_task_view
-- ----------------------------
DROP TABLE IF EXISTS "public"."flow_task_view";
CREATE TABLE "public"."flow_task_view" (
  "view_id" int8 NOT NULL DEFAULT nextval('flow_task_view_view_id_seq'::regclass),
  "task_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "process_instance_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "chart_key" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "chart_name" varchar(128) COLLATE "pg_catalog"."default",
  "biz_no" varchar(64) COLLATE "pg_catalog"."default",
  "title" varchar(255) COLLATE "pg_catalog"."default",
  "node_key" varchar(64) COLLATE "pg_catalog"."default",
  "node_name" varchar(128) COLLATE "pg_catalog"."default",
  "candidate_user_id" int8 NOT NULL,
  "candidate_user_name" varchar(64) COLLATE "pg_catalog"."default",
  "starter_id" int8,
  "starter_name" varchar(64) COLLATE "pg_catalog"."default",
  "view_status" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'pending'::character varying,
  "received_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "finished_at" timestamp(6),
  "form_id" int8,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "del_flag" varchar(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying
)
;
ALTER TABLE "public"."flow_task_view" OWNER TO "csiyj";
COMMENT ON TABLE "public"."flow_task_view" IS 'CQRS read model for task center listing';



-- ----------------------------
-- Table structure for sys_form
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_form";
CREATE TABLE "public"."sys_form" (
  "form_id" int8 NOT NULL DEFAULT nextval('sys_form_form_id_seq'::regclass),
  "form_name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "form_key" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "schema" text COLLATE "pg_catalog"."default",
  "layout" text COLLATE "pg_catalog"."default",
  "description" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "category" varchar(64) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "version" int4 NOT NULL DEFAULT 1,
  "table_name" varchar(128) COLLATE "pg_catalog"."default",
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now(),
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar
)
;
ALTER TABLE "public"."sys_form" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_form"."form_id" IS '主键';
COMMENT ON COLUMN "public"."sys_form"."form_name" IS '表单名称';
COMMENT ON COLUMN "public"."sys_form"."form_key" IS '表单唯一标识';
COMMENT ON COLUMN "public"."sys_form"."schema" IS '表单 Schema（JSON），前端设计器生成';
COMMENT ON COLUMN "public"."sys_form"."layout" IS '表单布局（JSON），预留扩展';
COMMENT ON COLUMN "public"."sys_form"."description" IS '表单描述';
COMMENT ON COLUMN "public"."sys_form"."category" IS '表单分类';
COMMENT ON COLUMN "public"."sys_form"."status" IS '状态：0=草稿 1=已发布';
COMMENT ON COLUMN "public"."sys_form"."version" IS '版本号';
COMMENT ON COLUMN "public"."sys_form"."table_name" IS '数据存储表名（可选，动态建表用）';
COMMENT ON COLUMN "public"."sys_form"."tenant_id" IS '租户 ID';
COMMENT ON COLUMN "public"."sys_form"."del_flag" IS '逻辑删除：0=正常 1=已删除';
COMMENT ON TABLE "public"."sys_form" IS '动态表单定义';


-- ----------------------------
-- Table structure for sys_form_data
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_form_data";
CREATE TABLE "public"."sys_form_data" (
  "id" int8 NOT NULL DEFAULT nextval('sys_form_data_id_seq'::regclass),
  "form_id" int8 NOT NULL,
  "form_key" varchar(128) COLLATE "pg_catalog"."default",
  "user_id" int8,
  "form_data" text COLLATE "pg_catalog"."default",
  "ip" varchar(64) COLLATE "pg_catalog"."default",
  "user_agent" varchar(512) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now(),
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar
)
;
ALTER TABLE "public"."sys_form_data" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_form_data"."id" IS '主键';
COMMENT ON COLUMN "public"."sys_form_data"."form_id" IS '关联表单 ID';
COMMENT ON COLUMN "public"."sys_form_data"."form_key" IS '表单标识';
COMMENT ON COLUMN "public"."sys_form_data"."user_id" IS '提交人用户 ID';
COMMENT ON COLUMN "public"."sys_form_data"."form_data" IS '表单数据（JSON）';
COMMENT ON COLUMN "public"."sys_form_data"."ip" IS '提交者 IP';
COMMENT ON COLUMN "public"."sys_form_data"."user_agent" IS '提交者 User-Agent';
COMMENT ON COLUMN "public"."sys_form_data"."status" IS '状态：0=草稿 1=已提交';
COMMENT ON COLUMN "public"."sys_form_data"."tenant_id" IS '租户 ID';
COMMENT ON COLUMN "public"."sys_form_data"."del_flag" IS '逻辑删除：0=正常 1=已删除';
COMMENT ON TABLE "public"."sys_form_data" IS '表单提交数据';


