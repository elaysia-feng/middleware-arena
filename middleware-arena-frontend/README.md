# Middleware Arena Frontend

基于 Vue 3 + TypeScript + Element Plus 构建的中间件竞技场前端。

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

## TODO

- [ ] 登录态校验（双 token 接入，路由守卫中实现）
- [ ] LoginView 对接 auth.ts 的 login 请求
- [ ] User store 双 token 刷新流程
- [ ] 请求拦截器注入 AccessToken
- [ ] 响应拦截器 401 自动 refresh token 重放
- [ ] MainLayout 菜单项完善
- [ ] HomeView 首页内容
- [ ] Monaco Editor 编辑器组件引入
