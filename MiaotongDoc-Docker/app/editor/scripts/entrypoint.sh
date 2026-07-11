#!/bin/bash
# MiaotongDoc Editor 自定义入口脚本
# 在 run-document-server.sh 生成 local.json 后注入 aiSettings

CONFIG_FILE="/etc/onlyoffice/documentserver/local.json"

# 从 ai-config.json 读取模型配置（前端配置，/data/config 是只读挂载）
fetch_models() {
    local ai_config_file="/data/config/ai-config.json"

    if [ ! -f "$ai_config_file" ]; then
        echo "[MiaotongDoc] WARNING: $ai_config_file not found" >&2
        echo '[]'
        return 1
    fi

    local llm_url
    local llm_key

    llm_url=$(python3 -c "import json; print(json.load(open('$ai_config_file')).get('targetUrl',''))" 2>/dev/null)
    llm_key=$(python3 -c "import json; print(json.load(open('$ai_config_file')).get('apiKey',''))" 2>/dev/null)

    if [ -z "$llm_url" ]; then
        echo "[MiaotongDoc] WARNING: LLM URL not configured" >&2
        echo '[]'
        return 1
    fi

    # 清理 llm_url（去除尾部多余斜杠，但保留 /v1）
    llm_url=$(echo "$llm_url" | sed 's|/*$||')
    # 如果有重复的 /v1//v1，去重为 /v1
    llm_url=$(echo "$llm_url" | sed 's|(/v1)+|/v1|g')

    echo "[MiaotongDoc] Fetching models from $llm_url" >&2

    # 容错处理：如果 URL 已带 /v1，不再添加；否则添加
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

    local response
    response=$(curl -sf --max-time 10 -H "$auth_header" "$models_url" 2>/dev/null)

    if [ $? -ne 0 ] || [ -z "$response" ]; then
        echo "[MiaotongDoc] WARNING: Failed to fetch models from LLM API" >&2
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
                    models: ($provider_models + [
                        {"id":"gpt-4o","name":"gpt-4o","endpoints":[1],"options":{}},
                        {"id":"gpt-4o-mini","name":"gpt-4o-mini","endpoints":[1],"options":{}},
                        {"id":"gpt-3.5-turbo","name":"gpt-3.5-turbo","endpoints":[1],"options":{}},
                        {"id":"o1","name":"o1","endpoints":[1],"options":{}},
                        {"id":"o1-mini","name":"o1-mini","endpoints":[1],"options":{}},
                        {"id":"deepseek-r1","name":"deepseek-r1","endpoints":[1],"options":{}},
                        {"id":"deepseek-v3","name":"deepseek-v3","endpoints":[1],"options":{}},
                        {"id":"deepseek-v4-flash","name":"deepseek-v4-flash","endpoints":[1],"options":{}},
                        {"id":"qwen2.5-72b","name":"qwen2.5-72b","endpoints":[1],"options":{}},
                        {"id":"qwen3-coder","name":"qwen3-coder","endpoints":[1],"options":{}},
                        {"id":"Qwen36-35B-A3B","name":"Qwen36-35B-A3B","endpoints":[1],"options":{}},
                        {"id":"claude-3-5-sonnet","name":"claude-3-5-sonnet","endpoints":[1],"options":{}},
                        {"id":"MiniMax-M3","name":"MiniMax-M3","endpoints":[1],"options":{}},
                        {"id":"MiniMax-M2.7","name":"MiniMax-M2.7","endpoints":[1],"options":{}},
                        {"id":"MiniMax-M2.5","name":"MiniMax-M2.5","endpoints":[1],"options":{}},
                        {"id":"MiniMax-M2.7-highspeed","name":"MiniMax-M2.7-highspeed","endpoints":[1],"options":{}},
                        {"id":"MiniMax-M2.5-highspeed","name":"MiniMax-M2.5-highspeed","endpoints":[1],"options":{}},
                        {"id":"sensenova-6.7-flash-lite","name":"sensenova-6.7-flash-lite","endpoints":[1],"options":{}},
                        {"id":"glm-5.2","name":"glm-5.2","endpoints":[1],"options":{}},
                        {"id":"sensenova-u1-fast","name":"sensenova-u1-fast","endpoints":[1],"options":{}}
                    ])
                }
            },
            models: ($global_models + [
                {id: "gpt-4o", name: "gpt-4o", provider: "OpenAI", capabilities: 511},
                {id: "gpt-4o-mini", name: "gpt-4o-mini", provider: "OpenAI", capabilities: 511},
                {id: "gpt-3.5-turbo", name: "gpt-3.5-turbo", provider: "OpenAI", capabilities: 511},
                {id: "o1", name: "o1", provider: "OpenAI", capabilities: 511},
                {id: "o1-mini", name: "o1-mini", provider: "OpenAI", capabilities: 511},
                {id: "deepseek-r1", name: "deepseek-r1", provider: "OpenAI", capabilities: 511},
                {id: "deepseek-v3", name: "deepseek-v3", provider: "OpenAI", capabilities: 511},
                {id: "deepseek-v4-flash", name: "deepseek-v4-flash", provider: "OpenAI", capabilities: 511},
                {id: "qwen2.5-72b", name: "qwen2.5-72b", provider: "OpenAI", capabilities: 511},
                {id: "qwen3-coder", name: "qwen3-coder", provider: "OpenAI", capabilities: 511},
                {id: "Qwen36-35B-A3B", name: "Qwen36-35B-A3B", provider: "OpenAI", capabilities: 511},
                {id: "claude-3-5-sonnet", name: "claude-3-5-sonnet", provider: "OpenAI", capabilities: 511},
                {id: "MiniMax-M3", name: "MiniMax-M3", provider: "OpenAI", capabilities: 511},
                {id: "MiniMax-M2.7", name: "MiniMax-M2.7", provider: "OpenAI", capabilities: 511},
                {id: "MiniMax-M2.5", name: "MiniMax-M2.5", provider: "OpenAI", capabilities: 511},
                {id: "MiniMax-M2.7-highspeed", name: "MiniMax-M2.7-highspeed", provider: "OpenAI", capabilities: 511},
                {id: "MiniMax-M2.5-highspeed", name: "MiniMax-M2.5-highspeed", provider: "OpenAI", capabilities: 511},
                {id: "sensenova-6.7-flash-lite", name: "sensenova-6.7-flash-lite", provider: "OpenAI", capabilities: 511},
                {id: "glm-5.2", name: "glm-5.2", provider: "OpenAI", capabilities: 511},
                {id: "sensenova-u1-fast", name: "sensenova-u1-fast", provider: "OpenAI", capabilities: 511}
            ])
        }' | jq -c '.')

    jq --arg llm_url "$provider_url" \
       --arg llm_key "$llm_key" \
       --arg default_model "$default_model" \
       --argjson provider_models "$provider_models" \
       --argjson global_models "$global_models" \
       --argjson ai_plugin_settings "$ai_plugin_settings" \
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
                    url: $llm_url,
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

    # 从 /data/config/ai-config.json 读取 LLM 配置（前端配置）
    local llm_url=""
    local llm_key=""
    local ai_config_file="/data/config/ai-config.json"

    if [ -f "$ai_config_file" ]; then
        llm_url=$(python3 -c "import json; print(json.load(open('$ai_config_file')).get('targetUrl',''))" 2>/dev/null)
        llm_key=$(python3 -c "import json; print(json.load(open('$ai_config_file')).get('apiKey',''))" 2>/dev/null)
        echo "[MiaotongDoc] AI config loaded from $ai_config_file"
    fi

    if [ -z "$llm_url" ]; then
        echo "[MiaotongDoc] WARNING: LLM URL not configured"
        return 0
    fi

    # 容错处理：清理 URL（去除尾部斜杠，去重 /v1）
    llm_url=$(echo "$llm_url" | sed 's|/*$||' | sed 's|(/v1)+|/v1|g')

    # 从 LLM API 获取可用模型列表
    local available_models
    available_models=$(fetch_models)
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
    global_models=$(echo "$available_models" | jq '[.[] | {id: .id, name: .id, provider: "OpenAI", capabilities: 511}]')

    echo "[MiaotongDoc] Found $model_count models, default=$default_model"

    # 等待 Document Server 完成初始配置
    sleep 10

    # 重试注入（Document Server 可能覆盖配置）
    local max_attempts=6
    local attempt=0
    while [ $attempt -lt $max_attempts ]; do
        do_inject "$llm_url" "$llm_key" "$available_models" "$default_model" "$provider_models" "$global_models"
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
