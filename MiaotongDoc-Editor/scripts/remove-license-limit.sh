#!/bin/bash
# MiaotongDoc Editor - 解除并发限制脚本
# 修改编辑器服务的并发限制检查

echo "========================================="
echo "MiaotongDoc Editor - 解除并发限制"
echo "========================================="

# 编辑器服务安装目录
EDITOR_SERVER="/var/www/onlyoffice/server"
EDITOR_CONFIG="/etc/onlyoffice/documentserver"
EDITOR_WEBAPPS="/var/www/onlyoffice/web-apps"

# 关键文件路径
LICENSE_JS="${EDITOR_SERVER}/Common/sources/license.js"
LICENSE_NODE_JS="${EDITOR_SERVER}/Common/sources/licenseNode.js"
COAUTH_BASE="${EDITOR_SERVER}/DocService/sources/CoAuthoringBase.js"
EDITOR_DATA="${EDITOR_SERVER}/DocService/sources/editorDataStorage.js"
COMMON_DEFINES="${EDITOR_SERVER}/Common/sources/commondefines.js"

# 备份函数
backup_file() {
    local file=$1
    if [ -f "$file" ]; then
        cp "$file" "${file}.orig"
        echo "[✓] 已备份: $(basename $file)"
    fi
}

# ========== 第一步: 备份原始文件 ==========
echo ""
echo "[第一步] 备份原始文件..."
backup_file "$LICENSE_JS"
backup_file "$LICENSE_NODE_JS"
backup_file "$COAUTH_BASE"
backup_file "$EDITOR_DATA"
backup_file "$COMMON_DEFINES"

# ========== 第二步: 修改许可证检查 ==========
echo ""
echo "[第二步] 修改许可证检查..."

if [ -f "$LICENSE_JS" ]; then
    # 替换连接数限制 (默认 20 -> 999999)
    sed -i 's/const connectionsLimit = 20/const connectionsLimit = 999999/g' "$LICENSE_JS"
    sed -i 's/connectionsLimit:\s*20/connectionsLimit: 999999/g' "$LICENSE_JS"
    sed -i 's/let connectionsLimit = 20/let connectionsLimit = 999999/g' "$LICENSE_JS"
    sed -i 's/var connectionsLimit = 20/var connectionsLimit = 999999/g' "$LICENSE_JS"

    # 替换用户数限制
    sed -i 's/const usersCountLimit = 20/const usersCountLimit = 999999/g' "$LICENSE_JS"
    sed -i 's/usersCountLimit:\s*20/usersCountLimit: 999999/g' "$LICENSE_JS"
    sed -i 's/let usersCountLimit = 20/let usersCountLimit = 999999/g' "$LICENSE_JS"

    # 替换查看连接限制
    sed -i 's/const connectionsView = 20/const connectionsView = 999999/g' "$LICENSE_JS"
    sed -i 's/connectionsView:\s*20/connectionsView: 999999/g' "$LICENSE_JS"

    # 修改许可证类型 (community=1 -> enterprise=3)
    sed -i 's/type:\s*1/type: 3/g' "$LICENSE_JS"
    sed -i 's/licenseType:\s*1/licenseType: 3/g' "$LICENSE_JS"

    # 修改过期时间
    sed -i '/endDate.*new Date/s/new Date().*/new Date("2099-12-31")/g' "$LICENSE_JS"

    echo "[✓] 许可证检查修改完成"
else
    echo "[✗] license.js 不存在，跳过"
fi

# ========== 第三步: 修改 Node.js 许可证模块 ==========
echo ""
echo "[第三步] 修改 Node.js 许可证模块..."

if [ -f "$LICENSE_NODE_JS" ]; then
    sed -i 's/const connectionsLimit = 20/const connectionsLimit = 999999/g' "$LICENSE_NODE_JS"
    sed -i 's/connectionsLimit:\s*20/connectionsLimit: 999999/g' "$LICENSE_NODE_JS"
    sed -i 's/const usersCountLimit = 20/const usersCountLimit = 999999/g' "$LICENSE_NODE_JS"
    sed -i 's/usersCountLimit:\s*20/usersCountLimit: 999999/g' "$LICENSE_NODE_JS"
    sed -i 's/type:\s*1/type: 3/g' "$LICENSE_NODE_JS"
    sed -i 's/licenseType:\s*1/licenseType: 3/g' "$LICENSE_NODE_JS"

    echo "[✓] Node.js 许可证模块修改完成"
else
    echo "[✗] licenseNode.js 不存在，跳过"
fi

# ========== 第四步: 修改协作编辑连接检查 ==========
echo ""
echo "[第四步] 修改协作编辑连接检查..."

if [ -f "$COAUTH_BASE" ]; then
    # 禁用连接数检查
    sed -i '/connections\s*>=\s*connectionsLimit/s/if/if (false) \&\&/g' "$COAUTH_BASE"
    sed -i '/currentConnections\s*>=\s*limit/s/if/if (false) \&\&/g' "$COAUTH_BASE"

    echo "[✓] 协作编辑连接检查修改完成"
else
    echo "[✗] CoAuthoringBase.js 不存在，跳过"
fi

# ========== 第五步: 修改编辑器数据存储 ==========
echo ""
echo "[第五步] 修改编辑器数据存储..."

if [ -f "$EDITOR_DATA" ]; then
    sed -i 's/connectionsLimit\s*=\s*20/connectionsLimit = 999999/g' "$EDITOR_DATA"
    sed -i 's/maxConnections\s*=\s*20/maxConnections = 999999/g' "$EDITOR_DATA"

    echo "[✓] 编辑器数据存储修改完成"
else
    echo "[✗] editorDataStorage.js 不存在，跳过"
fi

# ========== 第六步: 创建补丁模块 ==========
echo ""
echo "[第六步] 创建补丁模块..."

PATCH_FILE="${EDITOR_SERVER}/Common/sources/miaotong-patch.js"
cat > "$PATCH_FILE" << 'PATCH_EOF'
/**
 * MiaotongDoc Editor License Patch
 * 覆盖许可证检查，移除并发限制
 */

const originalRequire = require;

function patchLicenseModule() {
    try {
        const licensePath = require.resolve('./license');
        const licenseModule = require.cache[licensePath];

        if (licenseModule && licenseModule.exports) {
            const license = licenseModule.exports;

            if (typeof license.getConnectionsLimit === 'function') {
                license.getConnectionsLimit = function() { return 999999; };
            }

            if (typeof license.getUsersCountLimit === 'function') {
                license.getUsersCountLimit = function() { return 999999; };
            }

            if (typeof license.checkLicense === 'function') {
                license.checkLicense = function() {
                    return {
                        type: 3,
                        connectionsLimit: 999999,
                        usersCountLimit: 999999,
                        endDate: new Date('2099-12-31'),
                        trial: false,
                        paid: true
                    };
                };
            }

            if (typeof license.isLicenseExpired === 'function') {
                license.isLicenseExpired = function() { return false; };
            }

            if (typeof license.isTrial === 'function') {
                license.isTrial = function() { return false; };
            }

            if (typeof license.isLicense === 'function') {
                license.isLicense = function() { return true; };
            }

            license.connectionsLimit = 999999;
            license.usersCountLimit = 999999;
            license.type = 3;
            license.paid = true;

            console.log('[MiaotongDoc] License patch applied');
        }
    } catch (e) {
        console.error('[MiaotongDoc] License patch error:', e.message);
    }
}

setTimeout(patchLicenseModule, 1000);

module.exports = {
    applied: true,
    connectionsLimit: 999999,
    usersCountLimit: 999999
};
PATCH_EOF

echo "[✓] 补丁模块创建完成"

# ========== 第七步: 修改启动脚本 ==========
echo ""
echo "[第七步] 修改启动脚本..."

# 修改 DocService 启动命令
DOCSERVICE_PKG="${EDITOR_SERVER}/DocService/package.json"
if [ -f "$DOCSERVICE_PKG" ]; then
    cp "$DOCSERVICE_PKG" "${DOCSERVICE_PKG}.orig"
    sed -i 's/"start": "node /"start": "node --require \/var\/www\/onlyoffice\/server\/Common\/sources\/miaotong-patch.js /g' "$DOCSERVICE_PKG"
    echo "[✓] DocService 启动脚本修改完成"
fi

# 修改运行脚本
RUN_SCRIPT="/app/ds/run-document-server.sh"
if [ -f "$RUN_SCRIPT" ]; then
    cp "$RUN_SCRIPT" "${RUN_SCRIPT}.orig"

    cat > /tmp/env_vars.sh << 'ENV_EOF'
# MiaotongDoc Editor - 环境变量覆盖
export LICENSE_CONNECTIONS_LIMIT=999999
export LICENSE_USERS_LIMIT=999999
export CONNECTIONS_LIMIT=999999
export MAX_CONNECTIONS=999999
export EDITOR_CONNECTIONS_LIMIT=999999
ENV_EOF

    sed -i "2r /tmp/env_vars.sh" "$RUN_SCRIPT"
    echo "[✓] 运行脚本修改完成"
fi

# ========== 第八步: 修改配置文件 ==========
echo ""
echo "[第八步] 修改配置文件..."

LOCAL_JSON="${EDITOR_CONFIG}/local.json"
if [ -f "$LOCAL_JSON" ]; then
    cp "$LOCAL_JSON" "${LOCAL_JSON}.orig"

    node -e "
const fs = require('fs');
const config = JSON.parse(fs.readFileSync('$LOCAL_JSON', 'utf8'));

if (!config.services) config.services = {};
if (!config.services.CoAuthoring) config.services.CoAuthoring = {};

config.services.CoAuthoring.license = {
    connectionsLimit: 999999,
    usersCountLimit: 999999,
    type: 3
};

config.feedback = { url: '' };
config.analytics = { enabled: false };

fs.writeFileSync('$LOCAL_JSON', JSON.stringify(config, null, 4));
console.log('[✓] 配置文件修改完成');
" 2>/dev/null || echo "[!] 配置文件修改失败"
fi

# 创建许可证文件
LICENSE_FILE="${EDITOR_CONFIG}/license.lic"
cat > "$LICENSE_FILE" << 'LIC_EOF'
{
    "type": 3,
    "licenseType": 3,
    "connectionsLimit": 999999,
    "usersCountLimit": 999999,
    "endDate": "2099-12-31T23:59:59.000Z",
    "trial": false,
    "paid": true,
    "customer": "MiaotongDoc",
    "key": "miaotongdoc-unlimited-2024"
}
LIC_EOF

echo "[✓] 许可证文件创建完成"

# ========== 第九步: 修改前端显示 ==========
echo ""
echo "[第九步] 修改前端显示..."

if [ -d "$EDITOR_WEBAPPS" ]; then
    find "$EDITOR_WEBAPPS" -name "*.js" -type f -exec sed -i \
        -e 's/license_connections/license_connections/g' \
        -e 's/license_users_limit/license_users_limit/g' \
        -e 's/license.*expired/license_active/g' \
        -e 's/license.*limit/license_unlimited/g' \
        {} \; 2>/dev/null

    find "$EDITOR_WEBAPPS" -name "*.js" -type f -exec sed -i \
        -e 's/upgrade.*enterprise/upgrade_available/g' \
        -e 's/connection.*limit.*reached/connections_available/g' \
        {} \; 2>/dev/null

    echo "[✓] 前端显示修改完成"
fi

# ========== 完成 ==========
echo ""
echo "========================================="
echo "解除并发限制完成"
echo "========================================="
echo ""
echo "已修改:"
echo "  - 许可证检查: connectionsLimit = 999999"
echo "  - 用户数限制: usersCountLimit = 999999"
echo "  - 许可证类型: 企业版 (type=3)"
echo "  - 过期时间: 2099-12-31"
echo ""
echo "请重启服务以生效"
echo "========================================="
