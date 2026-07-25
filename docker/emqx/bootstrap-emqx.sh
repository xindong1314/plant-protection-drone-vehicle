#!/usr/bin/env bash
# 配置 EMQX 5 使用 HTTP 认证/授权回调后端，实现"一机一密"。
# 依赖：docker compose 已起 EMQX；后端跑在宿主机 8080（EMQX 经 host.docker.internal 回调）。
# 用法：bash docker/emqx/bootstrap-emqx.sh
set -u

API="http://127.0.0.1:18083/api/v5"
KEY="ppdvapi:ppdvapisecret123"          # docker/emqx/api_keys.txt 中的引导密钥
BACKEND="http://host.docker.internal:8080"
CURL="curl -s --noproxy 127.0.0.1,localhost -u ${KEY} -H content-type:application/json"

echo "=== 等待 EMQX API 就绪 ==="
for i in $(seq 1 30); do
  if $CURL "${API}/status" >/dev/null 2>&1; then echo "EMQX API ready"; break; fi
  sleep 2
done

echo "=== 清理旧配置（忽略不存在的报错）==="
$CURL -X DELETE "${API}/authentication/password_based:http" >/dev/null 2>&1
$CURL -X DELETE "${API}/authorization/sources/http" >/dev/null 2>&1

echo "=== 配置 HTTP 认证（一机一密）==="
$CURL -X POST "${API}/authentication" -d "{
  \"mechanism\": \"password_based\",
  \"backend\": \"http\",
  \"method\": \"post\",
  \"url\": \"${BACKEND}/api/emqx/auth\",
  \"headers\": { \"content-type\": \"application/json\" },
  \"body\": { \"username\": \"\${username}\", \"password\": \"\${password}\", \"clientid\": \"\${clientid}\" },
  \"pool_size\": 8,
  \"connect_timeout\": \"15s\",
  \"request_timeout\": \"5s\",
  \"enable\": true
}"; echo

echo "=== 配置 HTTP 授权（设备仅可访问自身主题）==="
$CURL -X POST "${API}/authorization/sources" -d "{
  \"type\": \"http\",
  \"method\": \"post\",
  \"url\": \"${BACKEND}/api/emqx/acl\",
  \"headers\": { \"content-type\": \"application/json\" },
  \"body\": { \"username\": \"\${username}\", \"topic\": \"\${topic}\", \"action\": \"\${action}\", \"clientid\": \"\${clientid}\" },
  \"connect_timeout\": \"15s\",
  \"request_timeout\": \"5s\",
  \"enable\": true
}"; echo

echo "=== 当前认证器 ==="; $CURL "${API}/authentication" | head -c 500; echo
echo "=== 完成 ==="
