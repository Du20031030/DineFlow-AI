# sky-take-out 苍穹外卖后端

苍穹外卖 Java 后端项目，基于 Spring Boot 多模块 Maven 工程实现。项目包含管理端、用户端、内部 Agent 接口、订单消息通知、WebSocket 来单提醒，以及 Java 后端调用 Python Agent 的 SSE 流式对话能力。

## 技术栈

- JDK：Java 17 推荐，当前 Maven 编译配置为 source/target 16
- Spring Boot：2.7.3
- Maven：多模块工程
- 持久层：MyBatis
- 数据库：MySQL + Druid
- 缓存：Redis
- 消息队列：RabbitMQ
- 实时通知：WebSocket
- 接口文档：Knife4j / Swagger
- 鉴权：JWT
- 对象存储：阿里云 OSS
- 外部 Agent：Python FastAPI SSE

## 模块结构

```text
sky-take-out
├── sky-common   # 通用常量、异常、工具类、配置属性类
├── sky-pojo     # DTO、Entity、VO 等数据模型
└── sky-server   # Spring Boot 启动模块，包含 Controller、Service、Mapper、配置类
```

核心启动类：

```text
sky-server/src/main/java/com/sky/SkyApplication.java
```

## 核心功能

- 管理端员工登录、员工管理
- 分类、菜品、套餐管理
- 店铺营业状态管理
- 用户登录、地址簿、菜品/套餐浏览
- 用户购物车、下单、支付回调、历史订单
- 管理端订单接单、拒单、取消、派送、完成
- 工作台数据、营业额、用户、订单、销量统计
- 阿里云 OSS 文件上传
- RabbitMQ 异步订单支付成功消息
- WebSocket 管理端来单提醒
- Java 调用 Python Agent，实现用户智能点餐对话
- Agent 长期记忆、会话、标题生成、SSE 流式响应

## 配置文件

主要配置文件在：

```text
sky-server/src/main/resources/application.yml
sky-server/src/main/resources/application-dev.yml
```

当前默认启用 `dev` Profile：

```yaml
spring:
  profiles:
    active: dev
```

本项目当前采用直接在 yml 中填写配置的方式。常用配置包括：

- `server.port`：Java 后端端口，默认 `8080`
- `sky.datasource.*`：MySQL 连接配置
- `sky.redis.*`：Redis 连接配置
- `sky.rabbitmq.*`：RabbitMQ 连接配置
- `sky.jwt.*`：管理端和用户端 JWT 配置
- `sky.alioss.*`：阿里云 OSS 配置
- `sky.wechat.*`：微信登录/支付相关配置
- `sky.agent.*`：Python Agent 地址、超时和线程池配置

Agent 配置示例：

```yaml
sky:
  agent:
    base-url: http://127.0.0.1:8000
    connect-timeout: 3s
    read-timeout: 120s
    executor:
      core-pool-size: 8
      max-pool-size: 32
      queue-capacity: 100
      keep-alive-seconds: 60
      await-termination-seconds: 30
```

注意：提交或共享项目时，需要自行确认 yml 中的数据库密码、Redis 密码、OSS Key、微信密钥、JWT Secret 是否允许暴露。

## 本地依赖

启动 Java 后端前，建议先准备：

- MySQL：默认 `localhost:3306`
- Redis：默认 `localhost:6379`
- RabbitMQ：默认 `localhost:5672`
- Python Agent：默认 `http://127.0.0.1:8000`

如果暂时没有启动 RabbitMQ，Spring Boot 仍可能启动，但订单支付成功后的 MQ 消费和管理端来单提醒会受到影响，日志中会看到 RabbitMQ 连接失败。

## 启动项目

在项目根目录执行：

```bash
mvn clean compile
```

打包：

```bash
mvn -pl sky-server -am package -DskipTests
```

运行：

```bash
java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar
```

也可以在 IDE 中直接运行：

```text
com.sky.SkyApplication
```

启动成功后默认访问地址：

```text
http://localhost:8080
```

## 常用接口入口

管理端接口：

```text
/admin/**
```

用户端接口：

```text
/user/**
```

Agent 内部接口：

```text
/internal/agent/**
```

用户端店铺状态查询：

```text
GET /user/shop/status
```

用户端购物车：

```text
POST   /user/shoppingCart/add
POST   /user/shoppingCart/sub
GET    /user/shoppingCart/list
DELETE /user/shoppingCart/clean
```

用户端订单：

```text
POST /user/order/submit
PUT  /user/order/payment
GET  /user/order/historyOrders
GET  /user/order/reminder/{id}
```

## Agent 智能助手链路

当前 Java 到 Python Agent 的主链路：

```text
前端用户请求
  ↓
AgentChatServiceImpl
  ↓
CompletableFuture.runAsync(...)
  ↓
agentExecutor Agent 专用线程池
  ↓
AgentClient
  ↓
Python FastAPI SSE
```

SSE 事件协议：

```text
token -> Java 实时转发给前端，并追加到 fullContent
done  -> 保存 ASSISTANT 消息，touchSession，新会话生成标题，complete
error -> 不保存 ASSISTANT，转发友好错误，complete
```

关键约束：

- USER 消息在调用 Python 前已经保存
- ASSISTANT 只在 `done` 后保存
- Python 返回 `error` 时不保存 ASSISTANT
- Java 不自动重放整个 Agent Graph
- `threadId` 和 `messageId` 会传给 Python
- Python 服务不可用时，Java 返回友好错误事件
- Agent SSE 使用独立 RestTemplate 超时配置
- Agent 异步任务使用 `agentExecutor`，线程名前缀为 `agent-executor-`

## MQ 与 WebSocket

当前 RabbitMQ 主要用于订单支付成功后的异步通知：

```text
支付成功
  ↓
OrderServiceImpl.paySuccess
  ↓
OrderMessageProducer
  ↓
RabbitMQ Exchange: sky.order.exchange
  ↓
Routing Key: order.paid
  ↓
Queue: sky.order.paid.queue
  ↓
OrderMessageListener
  ↓
WebSocketServer.sendToAllClient
  ↓
管理端收到来单提醒
```

RabbitMQ 配置类：

```text
sky-server/src/main/java/com/sky/config/RabbitMQConfiguration.java
```

消息生产者：

```text
sky-server/src/main/java/com/sky/mq/OrderMessageProducer.java
```

消息消费者：

```text
sky-server/src/main/java/com/sky/mq/OrderMessageListener.java
```

## 编译与测试

完整编译：

```bash
mvn clean compile
```

运行指定测试：

```bash
mvn -pl sky-server -am test -Dtest=AgentPropertiesTest,AgentThreadPoolConfigTest,AgentClientTest -DfailIfNoTests=false
```

如果在 PowerShell 中执行带逗号的 `-Dtest` 参数，建议加引号：

```powershell
mvn -pl sky-server -am test "-Dtest=AgentPropertiesTest,AgentThreadPoolConfigTest,AgentClientTest" "-DfailIfNoTests=false"
```

## 常见问题

### 1. 启动时报 RabbitMQ 连接失败

检查 RabbitMQ 是否已启动，默认配置为：

```text
localhost:5672
```

RabbitMQ 未启动时，订单支付成功后的异步消息和 WebSocket 来单提醒会受影响。

### 2. Agent 请求失败

检查 Python Agent 是否已启动，并确认配置：

```yaml
sky:
  agent:
    base-url: http://127.0.0.1:8000
```

SSE 是长响应，`read-timeout` 不要设置过短。

### 3. Redis 连接失败

检查 `application-dev.yml` 中的 Redis host、port、password、database 是否和本机一致。

### 4. MySQL 连接失败

检查数据库是否存在、账号密码是否正确，以及 `sky.datasource.database` 是否为实际库名。

### 5. IDE 报 `String.isBlank()` 找不到

当前项目已避免在主代码中使用 `String.isBlank()`，如果 IDE 仍报类似错误，检查 Project SDK 和 Maven JDK 是否一致。

## 开发注意事项

- 不要修改 Python Agent API 路径，Java 侧只配置 Agent base-url
- 不要在日志中打印 token、密码、Secret、AccessKeySecret 等敏感值
- 不要把普通 HTTP 超时直接套到 Agent SSE 上，Agent SSE 使用专用 RestTemplate
- 不要改动 `agentExecutor` Bean 名称，业务代码依赖该名称注入
- 数据库表、Mapper SQL、JWT 解析逻辑、BaseContext 用户上下文应谨慎修改
