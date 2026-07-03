$env:LLM_PROVIDER = "deepseek"
if (-not $env:DEEPSEEK_API_KEY) {
    echo "[错误] DEEPSEEK_API_KEY 环境变量未设置"
    exit 1
}
echo "启动 DeepSeek 模式，模型: deepseek-chat"
mvn spring-boot:run
