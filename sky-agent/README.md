# sky-agent

`sky-agent` 是苍穹外卖系统的 Python Agent 服务。项目基于 FastAPI 对外提供聊天接口，使用 LangGraph 编排多轮对话状态，调用兼容 OpenAI 接口的 DeepSeek 模型，并通过工具函数访问 Java Spring Boot 后端的真实业务数据。

这个项目的核心目标：

- 业务数据以 Java 后端为准，不让 Agent 编造菜单、购物车、地址、订单或记忆状态。
- 使用 PostgreSQL `PostgresSaver` 持久化多轮 Agent 工作记忆。
- PostgreSQL 临时不可用时，使用 `InMemorySaver` 和 Java/MySQL 历史消息兜底，尽量保证聊天不中断。

## 架构图

GitHub 可以直接渲染下面的 Mermaid 图：

```mermaid
flowchart LR
    Client[用户 / Java 后端] --> FastAPI[FastAPI<br/>main.py]
    FastAPI --> Router[API 路由<br/>api/user_agent_api.py]
    Router --> Graph[LangGraph Agent<br/>agent/graph.py]

    Graph --> LLM[DeepSeek 模型<br/>llm/model.py]
    Graph --> Tools[Agent 工具<br/>tools/*.py]
    Tools --> JavaAPIs[Java 内部接口<br/>真实业务数据源]

    Graph --> Postgres[(PostgreSQL<br/>PostgresSaver)]
    Graph -. fallback .-> Memory[(InMemorySaver)]
    JavaAPIs -. 历史消息兜底 .-> MySQL[(MySQL chat_message)]
    MySQL -. 恢复基础历史 .-> Memory
    Memory -. 恢复后回灌 .-> Postgres

    Env[.env / .env.example] --> Settings[config/settings.py]
    Settings --> Graph
    Settings --> LLM
    Settings --> Tools
```

## 功能特性

- 基于 FastAPI 的聊天服务，支持普通响应和流式响应。
- 基于 LangGraph 的 Agent 工作流，包含模型节点、工具节点和摘要节点。
- 使用 `ChatOpenAI` 兼容接口接入 DeepSeek 模型。
- 通过工具访问菜品、套餐、购物车、地址和长期记忆等真实业务能力。
- 支持带匹配等级的个性化菜品搜索。
- 使用 PostgreSQL 保存短期工作记忆。
- PostgreSQL 不可用时自动进入内存降级模式。
- Python 或 PostgreSQL 恢复后，可通过 Java/MySQL 历史消息进行兜底恢复。
- 使用 `.env` 进行配置外置，并提供 `.env.example` 作为公开配置模板。

## 项目结构

```text
sky-agent/
|-- main.py
|-- api/
|   `-- user_agent_api.py
|-- agent/
|   |-- graph.py
|   |-- nodes.py
|   |-- state.py
|   |-- context.py
|   `-- fallback.py
|-- tools/
|   |-- dish_tools.py
|   |-- setmeal_tools.py
|   |-- cart_tools.py
|   |-- address_tools.py
|   `-- memory_tools.py
|-- llm/
|   `-- model.py
|-- prompts/
|   `-- user_agent_prompt.py
|-- schemas/
|   |-- request.py
|   `-- response.py
|-- config/
|   |-- __init__.py
|   `-- settings.py
|-- tests/
|   `-- test_settings.py
|-- .env.example
|-- .gitignore
`-- requirements.txt
```

## 模块说明

| 模块 | 作用 |
| --- | --- |
| `main.py` | 创建 FastAPI 应用，并管理启动、关闭生命周期。 |
| `api/user_agent_api.py` | 处理聊天请求、SSE 响应、Graph 选择和降级模式调度。 |
| `agent/graph.py` | 构建 LangGraph 工作流，管理 `PostgresSaver`、fallback 和恢复逻辑。 |
| `agent/nodes.py` | 定义 Agent 节点、工具绑定和摘要逻辑。 |
| `agent/context.py` | 提供可信运行时上下文，例如 `user_id`。 |
| `agent/fallback.py` | PostgreSQL checkpoint 不可用时，从 Java/MySQL 加载基础聊天历史。 |
| `llm/model.py` | 从外置配置初始化 DeepSeek 聊天模型。 |
| `tools/` | 存放菜品、套餐、购物车、地址、长期记忆等业务工具。 |
| `config/settings.py` | 从 `.env` 读取环境相关配置。 |
| `prompts/user_agent_prompt.py` | 存放 Agent 系统提示词和工具调用规则。 |

## 配置说明

复制配置模板：

```bash
cp .env.example .env
```

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

配置示例：

```env
JAVA_BASE_URL=http://127.0.0.1:8080
JAVA_HTTP_TIMEOUT=10

POSTGRES_URI=postgresql://postgres:postgres@127.0.0.1:5433/sky_agent
POSTGRES_CONNECT_TIMEOUT=2
POSTGRES_POOL_MIN_SIZE=1
POSTGRES_POOL_MAX_SIZE=10
POSTGRES_POOL_WAIT_TIMEOUT=3
POSTGRES_POOL_RECONNECT_TIMEOUT=5

DEEPSEEK_API_KEY=your-api-key
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_TEMPERATURE=0.2
DEEPSEEK_TIMEOUT=60
DEEPSEEK_MAX_RETRIES=2
```


## 环境要求

推荐运行环境：

- Python 3.11+
- FastAPI
- Uvicorn
- LangChain / LangGraph
- DeepSeek 兼容 API Key
- PostgreSQL，用于 LangGraph checkpoint
- Java Spring Boot 后端，用于业务接口

安装 Python 依赖：

```bash
pip install -r requirements.txt
```

如果使用 Conda，请先激活对应环境：

```powershell
conda activate agent
pip install -r requirements.txt
```

## 启动服务

建议先启动依赖服务：

1. Java Spring Boot 后端，默认地址：`http://127.0.0.1:8080`
2. PostgreSQL，默认地址：`127.0.0.1:5433/sky_agent`

然后启动 Python Agent 服务：

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

接口文档地址：

```text
http://127.0.0.1:8000/docs
```

PostgreSQL 推荐启动，但不是 Python 服务启动的硬性前置条件。PostgreSQL 临时不可用时，Agent 会进入 fallback 模式，并在 PostgreSQL 恢复后尝试重新回到 `PostgresSaver`。

## 工具列表

Agent 通过工具访问真实后端数据。

| 工具文件 | 主要工具 |
| --- | --- |
| `tools/dish_tools.py` | `search_dishes`、`get_dish_detail`、`search_dishes_with_fallback` |
| `tools/setmeal_tools.py` | `search_setmeals` |
| `tools/cart_tools.py` | `get_my_cart`、`add_dish_to_cart`、`add_setmeal_to_cart`、`update_cart_item_number`、`delete_cart_item`、`clear_my_cart` |
| `tools/address_tools.py` | `get_default_address`、`get_my_addresses`、`set_default_address`、`add_address`、`update_address`、`delete_address` |
| `tools/memory_tools.py` | `get_memories`、`get_relevant_memories`、`remember_memory` |

重要设计原则：

```text
Agent 不应猜测业务数据。
菜单、购物车、地址、订单和长期记忆状态都应通过 Java 后端工具查询。
```

## 测试

编译检查：

```bash
python -m compileall .
```

运行配置测试：

```bash
python -m unittest tests.test_settings
```

运行模型连通性测试：

```bash
python test_llm.py
```

## 常见问题

### `ModuleNotFoundError: No module named 'pydantic_settings'`

请确认在运行 Uvicorn 的同一个 Python 环境中安装了项目依赖：

```bash
pip install -r requirements.txt
```

Conda 环境：

```powershell
conda activate agent
pip install -r requirements.txt
```

### 缺少 `DEEPSEEK_API_KEY`

检查 `.env` 是否存在，并确认包含：

```env
DEEPSEEK_API_KEY=your-api-key
```

### Java 后端请求失败

检查：

```env
JAVA_BASE_URL=http://127.0.0.1:8080
```

然后确认 Java Spring Boot 后端已经启动。

### PostgreSQL 不可用

项目允许 PostgreSQL 短暂不可用。Agent 会进入 fallback 模式，并在可能的情况下继续使用 `InMemorySaver`。

当 PostgreSQL 恢复后，下一轮用户请求会尝试重新初始化 `PostgresSaver` 并恢复当前 thread 的工作状态。
