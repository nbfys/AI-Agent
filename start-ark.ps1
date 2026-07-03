# AI 面试助手 - 火山引擎 Ark 模式
$env:LLM_PROVIDER = "ark"

if (-not $env:ARK_API_KEY) {
    Write-Host "[错误] ARK_API_KEY 环境变量未设置！" -ForegroundColor Red
    Write-Host "请在 PowerShell 执行: setx ARK_API_KEY 你的key（设完后重启终端）"
    exit 1
}

Write-Host "========================================"
Write-Host "  启动 火山引擎 Ark 模式"
Write-Host "  模型: doubao-seed-2.0-pro"
Write-Host "  API:  https://ark.cn-beijing.volces.com/api/coding/v3"
Write-Host "========================================"
mvn spring-boot:run
