from fastapi import FastAPI
from contextlib import asynccontextmanager
from fastapi import FastAPI
import agent.graph as graph_runtime
from api.user_agent_api import router as user_agent_router



""" 
Spring Boot
→ 自动扫描 Controller

FastAPI
→ 手动 include_router
"""


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    FastAPI生命周期管理。

    startup：
        尝试初始化PostgreSQL + PostgresSaver。

    shutdown：
        正常释放PostgreSQL ConnectionPool。
    """

    # ==================================================
    # FastAPI启动
    # ==================================================

    print("\n========== Application Startup ==========")

    postgres_available = (
        graph_runtime.init_postgres_graph()
    )

    if postgres_available:

        print(
            "PostgreSQL / PostgresSaver初始化成功"
        )

    else:

        print(
            "PostgreSQL当前不可用，"
            "Agent启动后将使用fallback机制"
        )

    print(
        "=========================================\n"
    )


    # FastAPI正式开始对外提供服务
    yield


    # ==================================================
    # FastAPI关闭
    # ==================================================

    print(
        "\n========== Application Shutdown =========="
    )

    graph_runtime.close_postgres_pool()

    print(
        "FastAPI资源清理完成"
    )

    print(
        "==========================================\n"
    )


# 创建 FastAPI 应用对象
app = FastAPI(
    title="Sky Takeout Agent Service",
    description="苍穹外卖智能 Agent 服务",
    version="0.1.0",
    lifespan=lifespan,
)

# 注册用户端 Agent 路由
app.include_router(user_agent_router)