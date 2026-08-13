# Middleware Arena — AI Agent

Python FastAPI + LangGraph 实验分析服务。

## 目录

```
app/
├── main.py               # FastAPI 入口 + /health（TODO: 挂 /analyze）
├── state.py              # LangGraph AnalysisState 定义
├── nodes/                # LangGraph 节点（TODO: 7 步分析链）
└── services/llm.py       # OpenAI 兼容 LLM 接入（TODO）
```

## 启动

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
cp .env.example .env   # 填入 DeepSeek OPENAI_API_KEY，或使用系统环境变量
uvicorn app.main:app --reload --port 9500
```

健康检查：http://localhost:9500/health

## 资源建议接口

`POST /resource/advice` 接收规则预算、同类实验历史指标和运行参数：

```json
{
  "experiment_type": "REDIS",
  "run_params": {"vus": 20, "duration": "30s"},
  "rule_budget": {"cpus": 2.0, "memory_mb": 2048},
  "max_budget": {"cpus": 3.0, "memory_mb": 4096},
  "history": [
    {"cpu_cores": 1.6, "memory_mb": 1800},
    {"cpu_cores": 1.8, "memory_mb": 1900}
  ]
}
```

服务按 `max(规则预算, 历史 P95 × 安全系数, LLM 建议)` 计算，最后受 `max_budget` 限制。
未配置或无法访问 LLM 时自动退回规则与历史计算，并通过 `llm_used=false` 明确标识。

当前默认使用 DeepSeek OpenAI 兼容接口：`https://api.deepseek.com`，模型为
`deepseek-v4-flash`。代码仍使用通用的 `OPENAI_API_BASE / OPENAI_API_KEY / OPENAI_MODEL`
变量名，便于 `ChatOpenAI` 直接读取。

## TODO

- [ ] `/analyze` 接口（输入 experimentId/codeDiff/config/metrics）
- [ ] LangGraph 节点链：read_result → compare_metrics → retrieve_similar(ES) → analyze_logs → analyze_diff → judge_bottleneck → generate_report
- [ ] OpenAI 兼容 LLM 接入（见 services/llm.py）
- [ ] 输出 bottleneck / confidence / evidence[] / suggestions[]
