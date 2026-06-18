#!/bin/bash
# MiaotongDoc Editor 自定义入口脚本
# 在 run-document-server.sh 生成 local.json 后注入 aiSettings

CONFIG_FILE="/etc/onlyoffice/documentserver/local.json"

# 查询 LLM API 可用模型，返回 jq 可用的 JSON 数组
fetch_models() {
    local llm_url="$1"
    local llm_key="$2"
    # 如果 URL 已经以 /v1 结尾，直接拼接 /models；否则拼接 /v1/models
    local models_url
    if [[ "$llm_url" == */v1 ]]; then
        models_url="${llm_url}/models"
    else
        models_url="${llm_url}/v1/models"
    fi

    local auth_header=""
    if [ -n "$llm_key" ]; then
        auth_header="Authorization: Bearer ${llm_key}"
    fi

    echo "[MiaotongDoc] Querying LLM models: $models_url" >&2

    local response
    response=$(curl -sf --max-time 10 -H "$auth_header" "$models_url" 2>/dev/null)

    if [ $? -ne 0 ] || [ -z "$response" ]; then
        echo "[MiaotongDoc] WARNING: Failed to query LLM models" >&2
        echo '[]'
        return 1
    fi

    # 过滤掉 embedding 和 reranker 模型（不支持对话）
    echo "$response" | jq '[.data[] | select(.id | test("embed|rerank"; "i") | not)]'
}

# 执行注入（jq 修改 local.json）
do_inject() {
    local llm_url="$1"
    local llm_key="$2"
    local available_models="$3"
    local default_model="$4"
    local provider_models="$5"
    local global_models="$6"

    # provider URL 需要 /v1 后缀（AI 插件拼接 /chat/completions）
    local provider_url="${llm_url}"
    if [[ "$provider_url" != */v1 ]]; then
        provider_url="${provider_url}/v1"
    fi

    # 构建 aiPluginSettings JSON 字符串（给 AI 插件读取）
    # proxy 使用后端代理地址，浏览器通过 /api/ai/proxy 转发到 LLM
    local ai_plugin_settings
    ai_plugin_settings=$(jq -n \
        --arg llm_url "$provider_url" \
        --arg llm_key "$llm_key" \
        --arg default_model "$default_model" \
        --argjson provider_models "$provider_models" \
        --argjson global_models "$global_models" \
        '{
            proxy: "/api/ai/proxy",
            version: 4,
            timeout: "5m",
            actions: {
                Chat: {model: $default_model},
                Summarization: {model: $default_model},
                Translation: {model: $default_model},
                TextAnalyze: {model: $default_model}
            },
            providers: {
                OpenAI: {
                    name: "OpenAI",
                    url: $llm_url,
                    key: $llm_key,
                    models: $provider_models
                }
            },
            models: $global_models
        }' | jq -c '.')

    jq --arg llm_url "$provider_url" \
       --arg llm_key "$llm_key" \
       --arg default_model "$default_model" \
       --argjson provider_models "$provider_models" \
       --argjson global_models "$global_models" \
       --arg ai_plugin_settings "$ai_plugin_settings" \
        '.services.CoAuthoring.server.appName = "MiaotongDoc/1.0" |
         .services.CoAuthoring.plugins = {
            autostart: ["asc.{9DC93CDB-B576-4F0C-B55E-FCC9C48DD007}"],
            aiPluginSettings: $ai_plugin_settings
         } |
         .aiSettings = {
            proxy: "/api/ai/proxy",
            version: 4,
            timeout: "5m",
            actions: {
                Chat: {model: $default_model},
                Summarization: {model: $default_model},
                Translation: {model: $default_model},
                TextAnalyze: {model: $default_model}
            },
            providers: {
                OpenAI: {
                    name: "OpenAI",
                    url: $provider_url,
                    key: $llm_key,
                    models: $provider_models
                }
            },
            models: $global_models
         }' "$CONFIG_FILE" > "${CONFIG_FILE}.tmp" && mv "${CONFIG_FILE}.tmp" "$CONFIG_FILE"
}

# 后台注入 aiSettings（等待 run-document-server.sh 生成配置文件后注入）
inject_ai_settings() {
    local max_wait=60
    local count=0
    while [ ! -f "$CONFIG_FILE" ] && [ $count -lt $max_wait ]; do
        sleep 1
        count=$((count + 1))
    done

    if [ ! -f "$CONFIG_FILE" ]; then
        echo "[MiaotongDoc] ERROR: $CONFIG_FILE not found after ${max_wait}s"
        return 1
    fi

    # 从配置文件或环境变量读取 LLM 配置
    local LLM_URL=""
    local LLM_KEY=""
    local AI_CONFIG_FILE="/data/config/ai-config.json"

    if [ -f "$AI_CONFIG_FILE" ]; then
        LLM_URL=$(python3 -c "import json; print(json.load(open('$AI_CONFIG_FILE')).get('targetUrl',''))" 2>/dev/null)
        LLM_KEY=$(python3 -c "import json; print(json.load(open('$AI_CONFIG_FILE')).get('apiKey',''))" 2>/dev/null)
        echo "[MiaotongDoc] AI config loaded from $AI_CONFIG_FILE"
    fi

    if [ -z "$LLM_URL" ]; then
        LLM_URL="${LLM_API_URL:-http://192.24.129.1:31000}"
    fi
    if [ -z "$LLM_KEY" ]; then
        LLM_KEY="${LLM_API_KEY:-}"
    fi

    # 查询可用模型
    local available_models
    available_models=$(fetch_models "$LLM_URL" "$LLM_KEY")
    local model_count
    model_count=$(echo "$available_models" | jq 'length')

    if [ "$model_count" -eq 0 ]; then
        echo "[MiaotongDoc] No models available, skipping"
        return 0
    fi

    local default_model
    default_model=$(echo "$available_models" | jq -r '.[0].id')
    local provider_models
    provider_models=$(echo "$available_models" | jq '[.[] | {id: .id, name: .id, endpoints: [1], options: {}}]')
    local global_models
    global_models=$(echo "$available_models" | jq '[.[] | {id: .id, name: .id, provider: "OpenAI", capabilities: 1}]')

    echo "[MiaotongDoc] Found $model_count models, default=$default_model"

    # 等待 Document Server 完成初始配置
    sleep 10

    # 重试注入（Document Server 可能覆盖配置）
    local max_attempts=6
    local attempt=0
    while [ $attempt -lt $max_attempts ]; do
        do_inject "$LLM_URL" "$LLM_KEY" "$available_models" "$default_model" "$provider_models" "$global_models"
        echo "[MiaotongDoc] Config injected (attempt $((attempt+1)))"

        sleep 3

        # 验证注入是否生效
        local ok
        ok=$(python3 -c "
import json
try:
    d=json.load(open('$CONFIG_FILE'))
    ais=d.get('aiSettings','')
    if isinstance(ais,dict) and ais.get('proxy','') != '':
        print('ok')
    else:
        print('fail')
except:
    print('fail')
" 2>/dev/null)

        if [ "$ok" = "ok" ]; then
            echo "[MiaotongDoc] Injection confirmed"
            return 0
        fi

        echo "[MiaotongDoc] Injection not confirmed, retrying..."
        sleep 2
        attempt=$((attempt + 1))
    done

    echo "[MiaotongDoc] WARNING: Could not confirm injection"
    return 0
}

# 替换 About 对话框中的版本标识
replace_version_label() {
    EDITOR_HOME="/var/www/onlyoffice"
    for f in $(find ${EDITOR_HOME}/documentserver/web-apps -name "code.js" 2>/dev/null); do
        if grep -q 'onlyoffice/[0-9]' "$f" 2>/dev/null; then
            sed -i 's|onlyoffice/[0-9]*\.[0-9]*\.[0-9]*\.[0-9]*|miaotongdoc/1.0|g' "$f" 2>/dev/null
            echo "[MiaotongDoc] Replaced version label in: $f"
        fi
    done
}

# 替换版本标识（前台执行）
replace_version_label

# 后台注入 aiSettings（等待 local.json 生成后执行）
inject_ai_settings &

# 使用 exec 替换当前进程为原始命令（run-document-server.sh）
exec "$@"
