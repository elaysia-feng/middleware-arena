# Middleware Arena — AI Agent

Python FastAPI + LangGraph 实验分析服务（**骨架，功能 TODO**）。

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
cp .env.example .env   # 填入 OPENAI_API_KEY
uvicorn app.main:app --reload --port 9500
```

健康检查：http://localhost:9500/health

## TODO

- [ ] `/analyze` 接口（输入 experimentId/codeDiff/config/metrics）
- [ ] LangGraph 节点链：read_result → compare_metrics → retrieve_similar(ES) → analyze_logs → analyze_diff → judge_bottleneck → generate_report
- [ ] OpenAI 兼容 LLM 接入（见 services/llm.py）
- [ ] 输出 bottleneck / confidence / evidence[] / suggestions[]
