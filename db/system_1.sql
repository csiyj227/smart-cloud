/*
 Smart Platform - Database Schema
 PostgreSQL
 
 Module: System (核心系统)
 File: system_1.sql (序列定义)
 
 Generated from public.sql split
*/

-- ----------------------------
-- Sequence structure for sys_config_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_config_id_seq";
CREATE SEQUENCE "public"."sys_config_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_config_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_dept_dept_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_dept_dept_id_seq";
CREATE SEQUENCE "public"."sys_dept_dept_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_dept_dept_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_dict_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_dict_id_seq";
CREATE SEQUENCE "public"."sys_dict_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_dict_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_dict_item_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_dict_item_id_seq";
CREATE SEQUENCE "public"."sys_dict_item_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_dict_item_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_log_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_log_id_seq";
CREATE SEQUENCE "public"."sys_log_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_log_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_login_log_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_login_log_id_seq";
CREATE SEQUENCE "public"."sys_login_log_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_login_log_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_menu_menu_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_menu_menu_id_seq";
CREATE SEQUENCE "public"."sys_menu_menu_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_menu_menu_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_notice_notice_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_notice_notice_id_seq";
CREATE SEQUENCE "public"."sys_notice_notice_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_notice_notice_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_notice_read_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_notice_read_id_seq";
CREATE SEQUENCE "public"."sys_notice_read_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_notice_read_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_post_post_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_post_post_id_seq";
CREATE SEQUENCE "public"."sys_post_post_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_post_post_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_public_param_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_public_param_id_seq";
CREATE SEQUENCE "public"."sys_public_param_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_public_param_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_role_role_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_role_role_id_seq";
CREATE SEQUENCE "public"."sys_role_role_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_role_role_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_route_conf_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_route_conf_id_seq";
CREATE SEQUENCE "public"."sys_route_conf_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_route_conf_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_tenant_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_tenant_id_seq";
CREATE SEQUENCE "public"."sys_tenant_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_tenant_id_seq" OWNER TO "csiyj";


-- ----------------------------
-- Sequence structure for sys_user_user_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sys_user_user_id_seq";
CREATE SEQUENCE "public"."sys_user_user_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;
ALTER SEQUENCE "public"."sys_user_user_id_seq" OWNER TO "csiyj";


