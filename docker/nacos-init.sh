#!/bin/bash
# Nacos 配置初始化脚本
# 从 nacos-config/ 目录读取 YAML 文件，通过 Nacos Open API 推送配置
# 用法: ./nacos-init.sh [NACOS_ADDR]
#   NACOS_ADDR 默认为 http://127.0.0.1:8848

NACOS_ADDR="${1:-http://127.0.0.1:8848}"
NACOS_USER="${NACOS_USERNAME:-nacos}"
NACOS_PASS="${NACOS_PASSWORD:-nacos}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_DIR="${SCRIPT_DIR}/nacos-config"

echo "=== Smart Nacos 配置初始化 ==="
echo "Nacos 地址: ${NACOS_ADDR}"
echo "配置目录:   ${CONFIG_DIR}"
echo ""

# 检查配置目录是否存在
if [ ! -d "$CONFIG_DIR" ]; then
  echo "❌ 配置目录不存在: ${CONFIG_DIR}"
  exit 1
fi

# 等待 Nacos 就绪
echo "[1/3] 等待 Nacos 就绪..."
MAX_RETRIES=30
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf "${NACOS_ADDR}/nacos/v1/console/health/readiness" > /dev/null 2>&1; then
    echo "  ✅ Nacos 已就绪"
    break
  fi
  if [ "$i" -eq "$MAX_RETRIES" ]; then
    echo "  ❌ 等待 Nacos 超时 (${MAX_RETRIES}s)，请检查 Nacos 是否正常运行"
    exit 1
  fi
  sleep 1
done

# 从文件发布配置的函数
publish_config_file() {
  local file_path=$1
  local data_id=$(basename "$file_path")

  local result=$(curl -s -X POST "${NACOS_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "username=${NACOS_USER}" \
    --data-urlencode "password=${NACOS_PASS}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@${file_path}")

  if [ "$result" = "true" ]; then
    echo "  ✅ ${data_id}"
  else
    echo "  ❌ ${data_id} 发布失败: ${result}"
    return 1
  fi
}

# 发布所有配置文件
echo "[2/3] 发布配置文件..."
FAIL_COUNT=0
SUCCESS_COUNT=0

# 优先发布公共配置
if [ -f "${CONFIG_DIR}/application-common.yml" ]; then
  publish_config_file "${CONFIG_DIR}/application-common.yml" || ((FAIL_COUNT++))
  ((SUCCESS_COUNT++))
fi

# 发布其他服务配置
for config_file in "${CONFIG_DIR}"/*.yml; do
  data_id=$(basename "$config_file")
  if [ "$data_id" = "application-common.yml" ]; then
    continue
  fi
  publish_config_file "$config_file" && ((SUCCESS_COUNT++)) || ((FAIL_COUNT++))
done

# 验证
echo ""
echo "[3/3] 验证配置..."
CONFIG_COUNT=$(curl -s "${NACOS_ADDR}/nacos/v1/cs/configs?search=accurate&dataId=&group=&pageNo=1&pageSize=100&username=${NACOS_USER}&password=${NACOS_PASS}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalCount', 0))" 2>/dev/null)

echo ""
echo "=== 初始化完成 ==="
echo "成功: ${SUCCESS_COUNT}, 失败: ${FAIL_COUNT}"
echo "Nacos 中共有 ${CONFIG_COUNT} 条配置"
echo "Nacos 控制台: ${NACOS_ADDR}/nacos (${NACOS_USER}/${NACOS_PASS})"
