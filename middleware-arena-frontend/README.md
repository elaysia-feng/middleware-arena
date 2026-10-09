# Middleware Arena Frontend

基于 Vue 3 + TypeScript + Element Plus 构建的中间件学习实验工作台，聚焦实验模板、代码编辑、压测任务和 AI 分析。当前不提供社区功能。

## 启动方式

```bash
npm install
npm run dev
```

## 构建

```bash
npm run build
```

## 技术栈

- Vite 5
- Vue 3.4
- TypeScript 5.5
- Element Plus 2.7
- Pinia 2.1
- Vue Router 4.3
- Axios 1.7
- Monaco Editor 0.50

## 功能

- 登录注册、双 Token 刷新与账号管理。
- 内置实验场景、Monaco 源码编辑、版本历史与 Diff。
- 压测任务创建、取消、重试、状态和阶段监控。
- Agent 资源建议、性能分析与实验完成通知。

开发服务器将 `/api/**` 代理到 `http://localhost:8000`。服务启动和实验依赖见仓库根目录的 [README](../README.md)。
