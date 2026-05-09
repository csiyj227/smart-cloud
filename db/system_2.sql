/*
 Smart Platform - Database Schema
 PostgreSQL
 
 Module: System (核心系统)
 File: system_2.sql (表结构)
 
 Generated from public.sql split
*/

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_config";
CREATE TABLE "public"."sys_config" (
  "id" int8 NOT NULL DEFAULT nextval('sys_config_id_seq'::regclass),
  "param_key" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "param_value" text COLLATE "pg_catalog"."default",
  "param_type" char(1) COLLATE "pg_catalog"."default" DEFAULT '0'::bpchar,
  "description" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "system_flag" bool DEFAULT false,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_config" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_config"."param_type" IS '参数类型：0=字符串，1=数字，2=JSON';
COMMENT ON COLUMN "public"."sys_config"."system_flag" IS '是否系统内置（不可删除）';
COMMENT ON TABLE "public"."sys_config" IS '系统参数配置表';


-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_dept";
CREATE TABLE "public"."sys_dept" (
  "dept_id" int8 NOT NULL DEFAULT nextval('sys_dept_dept_id_seq'::regclass),
  "dept_name" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "parent_id" int8 NOT NULL DEFAULT 0,
  "ancestors" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "sort_order" int4 NOT NULL DEFAULT 0,
  "leader" varchar(64) COLLATE "pg_catalog"."default",
  "phone" varchar(20) COLLATE "pg_catalog"."default",
  "email" varchar(64) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_dept" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_dept"."ancestors" IS '祖级列表，如 0,1,2';
COMMENT ON COLUMN "public"."sys_dept"."tenant_id" IS '租户ID，用于隔离';
COMMENT ON TABLE "public"."sys_dept" IS '部门表';


-- ----------------------------
-- Table structure for sys_dict
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_dict";
CREATE TABLE "public"."sys_dict" (
  "id" int8 NOT NULL DEFAULT nextval('sys_dict_id_seq'::regclass),
  "type_code" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "type_name" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "system_flag" bool DEFAULT false,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_dict" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_dict"."system_flag" IS '是否系统内置（不可删除）';
COMMENT ON TABLE "public"."sys_dict" IS '字典类型表';


-- ----------------------------
-- Table structure for sys_dict_item
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_dict_item";
CREATE TABLE "public"."sys_dict_item" (
  "id" int8 NOT NULL DEFAULT nextval('sys_dict_item_id_seq'::regclass),
  "dict_id" int8 NOT NULL,
  "item_label" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "item_value" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "sort_order" int4 NOT NULL DEFAULT 0,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_dict_item" OWNER TO "csiyj";
COMMENT ON TABLE "public"."sys_dict_item" IS '字典数据表';


-- ----------------------------
-- Table structure for sys_log
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_log";
CREATE TABLE "public"."sys_log" (
  "id" int8 NOT NULL DEFAULT nextval('sys_log_id_seq'::regclass),
  "log_type" varchar(8) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying,
  "title" varchar(255) COLLATE "pg_catalog"."default",
  "service_id" varchar(64) COLLATE "pg_catalog"."default",
  "remote_addr" varchar(64) COLLATE "pg_catalog"."default",
  "user_agent" varchar(512) COLLATE "pg_catalog"."default",
  "request_uri" varchar(512) COLLATE "pg_catalog"."default",
  "http_method" varchar(16) COLLATE "pg_catalog"."default",
  "class_name" varchar(255) COLLATE "pg_catalog"."default",
  "method_name" varchar(128) COLLATE "pg_catalog"."default",
  "params" text COLLATE "pg_catalog"."default",
  "execution_time" int8,
  "exception" text COLLATE "pg_catalog"."default",
  "trace_id" varchar(64) COLLATE "pg_catalog"."default",
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
)
;
ALTER TABLE "public"."sys_log" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_log"."id" IS '主键';
COMMENT ON COLUMN "public"."sys_log"."log_type" IS '日志类型：0=正常 1=异常';
COMMENT ON COLUMN "public"."sys_log"."title" IS '日志标题';
COMMENT ON COLUMN "public"."sys_log"."service_id" IS '服务 ID / 应用名';
COMMENT ON COLUMN "public"."sys_log"."remote_addr" IS '请求 IP';
COMMENT ON COLUMN "public"."sys_log"."user_agent" IS '客户端 UA';
COMMENT ON COLUMN "public"."sys_log"."request_uri" IS '请求 URI';
COMMENT ON COLUMN "public"."sys_log"."http_method" IS '请求方法';
COMMENT ON COLUMN "public"."sys_log"."class_name" IS '处理类名';
COMMENT ON COLUMN "public"."sys_log"."method_name" IS '处理方法名';
COMMENT ON COLUMN "public"."sys_log"."params" IS '请求参数';
COMMENT ON COLUMN "public"."sys_log"."execution_time" IS '执行耗时(ms)';
COMMENT ON COLUMN "public"."sys_log"."exception" IS '异常信息';
COMMENT ON COLUMN "public"."sys_log"."trace_id" IS '链路追踪ID';
COMMENT ON COLUMN "public"."sys_log"."tenant_id" IS '租户 ID';
COMMENT ON COLUMN "public"."sys_log"."create_by" IS '操作人';
COMMENT ON COLUMN "public"."sys_log"."create_time" IS '创建时间';
COMMENT ON TABLE "public"."sys_log" IS '系统操作日志';


-- ----------------------------
-- Table structure for sys_login_log
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_login_log";
CREATE TABLE "public"."sys_login_log" (
  "id" int8 NOT NULL DEFAULT nextval('sys_login_log_id_seq'::regclass),
  "user_id" int8,
  "username" varchar(64) COLLATE "pg_catalog"."default",
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "login_type" varchar(8) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying,
  "status" varchar(8) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::character varying,
  "ip" varchar(64) COLLATE "pg_catalog"."default",
  "location" varchar(255) COLLATE "pg_catalog"."default",
  "user_agent" varchar(512) COLLATE "pg_catalog"."default",
  "msg" varchar(1024) COLLATE "pg_catalog"."default",
  "access_token" varchar(64) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar
)
;
ALTER TABLE "public"."sys_login_log" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_login_log"."id" IS '主键';
COMMENT ON COLUMN "public"."sys_login_log"."user_id" IS '用户 ID（失败时可能为空）';
COMMENT ON COLUMN "public"."sys_login_log"."username" IS '登录用户名';
COMMENT ON COLUMN "public"."sys_login_log"."tenant_id" IS '租户 ID';
COMMENT ON COLUMN "public"."sys_login_log"."login_type" IS '类型：0=登录 1=登出 2=注册 3=密码错误 4=账号锁定';
COMMENT ON COLUMN "public"."sys_login_log"."status" IS '状态：0=成功 1=失败';
COMMENT ON COLUMN "public"."sys_login_log"."ip" IS '客户端 IP';
COMMENT ON COLUMN "public"."sys_login_log"."location" IS 'IP 归属地（可选）';
COMMENT ON COLUMN "public"."sys_login_log"."user_agent" IS '客户端 UA';
COMMENT ON COLUMN "public"."sys_login_log"."msg" IS '提示信息 / 错误原因';
COMMENT ON COLUMN "public"."sys_login_log"."access_token" IS 'access token 标识（截断或哈希）';
COMMENT ON TABLE "public"."sys_login_log" IS '登录日志';


-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_menu";
CREATE TABLE "public"."sys_menu" (
  "menu_id" int8 NOT NULL DEFAULT nextval('sys_menu_menu_id_seq'::regclass),
  "menu_name" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "permission" varchar(256) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "path" varchar(256) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "component" varchar(256) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "parent_id" int8 NOT NULL DEFAULT 0,
  "icon" varchar(256) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "sort_order" int4 NOT NULL DEFAULT 0,
  "menu_type" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "keep_alive" bool DEFAULT false,
  "visible" bool DEFAULT true,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now(),
  "tenant_id" int8
)
;
ALTER TABLE "public"."sys_menu" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_menu"."permission" IS '权限标识，如 sys_user_add';
COMMENT ON COLUMN "public"."sys_menu"."menu_type" IS '菜单类型：0=目录，1=菜单，2=按钮';
COMMENT ON TABLE "public"."sys_menu" IS '菜单权限表';


-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_notice";
CREATE TABLE "public"."sys_notice" (
  "notice_id" int8 NOT NULL DEFAULT nextval('sys_notice_notice_id_seq'::regclass),
  "notice_title" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "notice_type" char(1) COLLATE "pg_catalog"."default" DEFAULT '0'::bpchar,
  "notice_content" text COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now(),
  "publisher" varchar(255) COLLATE "pg_catalog"."default",
  "priority" varchar(255) COLLATE "pg_catalog"."default",
  "publish_time" timestamp(6),
  "expire_time" timestamp(6)
)
;
ALTER TABLE "public"."sys_notice" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_notice"."notice_type" IS '通知类型：0=通知，1=公告';
COMMENT ON TABLE "public"."sys_notice" IS '系统通知公告表';


-- ----------------------------
-- Table structure for sys_notice_read
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_notice_read";
CREATE TABLE "public"."sys_notice_read" (
  "id" int8 NOT NULL DEFAULT nextval('sys_notice_read_id_seq'::regclass),
  "notice_id" int8 NOT NULL,
  "user_id" int8 NOT NULL,
  "read_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_notice_read" OWNER TO "csiyj";
COMMENT ON TABLE "public"."sys_notice_read" IS '通知公告阅读记录表';


-- ----------------------------
-- Table structure for sys_oauth_client_details
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_oauth_client_details";
CREATE TABLE "public"."sys_oauth_client_details" (
  "client_id" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "resource_ids" varchar(256) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "client_secret" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "scope" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "authorized_grant_types" varchar(512) COLLATE "pg_catalog"."default" NOT NULL,
  "web_server_redirect_uri" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "authorities" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "access_token_validity" int4 DEFAULT 43200,
  "refresh_token_validity" int4 DEFAULT 2592000,
  "additional_information" varchar(4096) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "auto_approve" varchar(256) COLLATE "pg_catalog"."default" DEFAULT 'true'::character varying,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now(),
  "del_flag" varchar(255) COLLATE "pg_catalog"."default"
)
;
ALTER TABLE "public"."sys_oauth_client_details" OWNER TO "csiyj";
COMMENT ON TABLE "public"."sys_oauth_client_details" IS 'OAuth2客户端详情表';


-- ----------------------------
-- Table structure for sys_post
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_post";
CREATE TABLE "public"."sys_post" (
  "post_id" int8 NOT NULL DEFAULT nextval('sys_post_post_id_seq'::regclass),
  "post_code" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "post_name" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "sort_order" int4 NOT NULL DEFAULT 0,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_post" OWNER TO "csiyj";
COMMENT ON TABLE "public"."sys_post" IS '岗位表';


-- ----------------------------
-- Table structure for sys_public_param
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_public_param";
CREATE TABLE "public"."sys_public_param" (
  "id" int8 NOT NULL DEFAULT nextval('sys_public_param_id_seq'::regclass),
  "param_name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "param_key" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "param_value" text COLLATE "pg_catalog"."default",
  "param_type" char(1) COLLATE "pg_catalog"."default" DEFAULT '0'::bpchar,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_public_param" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_public_param"."param_type" IS '参数类型：0=字符串，1=数字，2=JSON';
COMMENT ON TABLE "public"."sys_public_param" IS '公共参数表';


-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_role";
CREATE TABLE "public"."sys_role" (
  "role_id" int8 NOT NULL DEFAULT nextval('sys_role_role_id_seq'::regclass),
  "role_name" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "role_code" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "role_desc" varchar(256) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "ds_type" int4 NOT NULL DEFAULT 0,
  "ds_scope" varchar(256) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_role" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_role"."ds_type" IS '数据权限类型：0=全部，1=自定义，2=本部门，3=本部门及以下，4=仅本人';
COMMENT ON COLUMN "public"."sys_role"."ds_scope" IS '数据权限范围：自定义部门ID列表，ds_type=1时使用';
COMMENT ON TABLE "public"."sys_role" IS '角色表';


-- ----------------------------
-- Table structure for sys_role_dept
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_role_dept";
CREATE TABLE "public"."sys_role_dept" (
  "role_id" int8 NOT NULL,
  "dept_id" int8 NOT NULL,
  "tenant_id" int8 NOT NULL DEFAULT 1
)
;
ALTER TABLE "public"."sys_role_dept" OWNER TO "csiyj";
COMMENT ON TABLE "public"."sys_role_dept" IS '角色部门关联表（自定义数据权限）';


-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_role_menu";
CREATE TABLE "public"."sys_role_menu" (
  "role_id" int8 NOT NULL,
  "menu_id" int8 NOT NULL,
  "tenant_id" int8 NOT NULL DEFAULT 1
)
;
ALTER TABLE "public"."sys_role_menu" OWNER TO "csiyj";
COMMENT ON TABLE "public"."sys_role_menu" IS '角色菜单关联表';


-- ----------------------------
-- Table structure for sys_route_conf
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_route_conf";
CREATE TABLE "public"."sys_route_conf" (
  "id" int8 NOT NULL DEFAULT nextval('sys_route_conf_id_seq'::regclass),
  "route_name" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "route_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "predicates" text COLLATE "pg_catalog"."default" NOT NULL,
  "filters" text COLLATE "pg_catalog"."default",
  "uri" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "sort_order" int4 NOT NULL DEFAULT 0,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_route_conf" OWNER TO "csiyj";
COMMENT ON TABLE "public"."sys_route_conf" IS '网关动态路由配置表';


-- ----------------------------
-- Table structure for sys_tenant
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_tenant";
CREATE TABLE "public"."sys_tenant" (
  "id" int8 NOT NULL DEFAULT nextval('sys_tenant_id_seq'::regclass),
  "tenant_name" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "tenant_code" varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
  "start_time" timestamp(6),
  "end_time" timestamp(6),
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now()
)
;
ALTER TABLE "public"."sys_tenant" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_tenant"."status" IS '状态：0=正常，1=停用';
COMMENT ON COLUMN "public"."sys_tenant"."del_flag" IS '删除标志：0=正常，1=删除';
COMMENT ON TABLE "public"."sys_tenant" IS '租户管理表';


-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_user";
CREATE TABLE "public"."sys_user" (
  "user_id" int8 NOT NULL DEFAULT nextval('sys_user_user_id_seq'::regclass),
  "username" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "password" varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
  "real_name" varchar(64) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "phone" varchar(20) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "avatar" varchar(512) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "email" varchar(128) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "dept_id" int8 DEFAULT 0,
  "post_id" int8 DEFAULT 0,
  "user_type" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '1'::bpchar,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "lock_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "tenant_id" int8 NOT NULL DEFAULT 1,
  "del_flag" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '0'::bpchar,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT now(),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6) DEFAULT now(),
  "password_update_time" timestamp(6),
  "password_expire_days" int8
)
;
ALTER TABLE "public"."sys_user" OWNER TO "csiyj";
COMMENT ON COLUMN "public"."sys_user"."user_type" IS '用户类型：0=超级管理员，1=普通用户';
COMMENT ON COLUMN "public"."sys_user"."lock_flag" IS '锁定标志：0=正常，9=锁定';
COMMENT ON TABLE "public"."sys_user" IS '用户表';


-- ----------------------------
-- Table structure for sys_user_post
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_user_post";
CREATE TABLE "public"."sys_user_post" (
  "user_id" int8 NOT NULL,
  "post_id" int8 NOT NULL
)
;
ALTER TABLE "public"."sys_user_post" OWNER TO "csiyj";
COMMENT ON TABLE "public"."sys_user_post" IS '用户岗位关联表';


-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_user_role";
CREATE TABLE "public"."sys_user_role" (
  "user_id" int8 NOT NULL,
  "role_id" int8 NOT NULL,
  "tenant_id" int8 NOT NULL DEFAULT 1
)
;
ALTER TABLE "public"."sys_user_role" OWNER TO "csiyj";
COMMENT ON TABLE "public"."sys_user_role" IS '用户角色关联表';


