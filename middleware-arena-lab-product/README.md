# middleware-arena-lab-product

商品实验辅助宿主（注册名 `lab-product-service`，端口 `9009`），为订单实验提供商品快照与单价，不是平台的商品管理模块。

- `GET /product/{productId}`：订单实验通过 Feign 查询商品与单价。
- `sql/init.sql`：初始化实验商品数据。

只在需要商品查询的下单实验中启动。模板与版本管理由 experiment-service 统一负责。
