"""
短期记忆流程：

                      ┌──────────────→ agent_node
                      │
START → 判断是否摘要 ──┤
                      │
                      └→ summarize_node
                              ↓
                          agent_node
                              ↓
                         模型判断
                         ├── 不需要工具 → END
                         │
                         └── 需要工具 → ToolNode
                                           ↓
                                       agent_node
                                           ↓
                                          END


短期记忆容灾结构：

正常：
    PostgreSQL
        ↓
    PostgresSaver
        ↓
    LangGraph
        ↓
    messages + summary + ToolMessage


PostgreSQL故障：
    MySQL chat_message
        ↓
    第一次恢复基础聊天历史
        ↓
    InMemorySaver
        ↓
    故障期间继续保存完整工作记忆


PostgreSQL恢复：
    新的一轮用户请求到达
        ↓
    读取InMemorySaver完整state
        ↓
    回灌PostgresSaver
        ↓
    校验成功
        ↓
    解除degraded
        ↓
    当前新请求重新使用PostgresSaver


如果Python也重启：
    InMemorySaver丢失
        ↓
    MySQL chat_message作为最终兜底


PostgreSQL连接池生命周期：

Python启动
    ↓
轻量探测PostgreSQL
    ↓
数据库正常
    ↓
创建ConnectionPool
    ↓
创建PostgresSaver


运行过程中PostgreSQL故障：
    PostgresSaver异常
        ↓
    当前请求立即放弃旧Pool
        ↓
    进入Fallback
        ↓
    后台线程清理旧Pool


PostgreSQL恢复：
    新请求到达
        ↓
    创建新的Pool
        ↓
    回灌InMemory工作记忆
        ↓
    重新使用PostgresSaver


Python正常退出：
    FastAPI lifespan shutdown
        ↓
    close_postgres_pool()
        ↓
    正常关闭当前Pool
"""

import logging
from threading import Lock, Thread

import psycopg

from langgraph.graph import (
    StateGraph,
    START,
    END,
)

from langgraph.prebuilt import (
    ToolNode,
    tools_condition,
)

from langgraph.checkpoint.postgres import PostgresSaver
from langgraph.checkpoint.memory import InMemorySaver

from psycopg_pool import ConnectionPool

from agent.state import UserAgentState
from agent.context import UserAgentContext

from agent.nodes import (
    agent_node,
    tools,
    summarize_node,
    should_summarize,
)
from config.settings import settings


# ==========================================================
# 0. 日志
# ==========================================================

logger = logging.getLogger(__name__)


# ==========================================================
# 1. 创建主Graph
# ==========================================================

builder = StateGraph(
    UserAgentState,
    context_schema=UserAgentContext,
)


# ==========================================================
# 2. 注册节点
# ==========================================================

# Agent核心节点
builder.add_node(
    "agent",
    agent_node,
)


# Tool执行节点
builder.add_node(
    "tools",
    ToolNode(tools),
)


# 短期记忆摘要节点
builder.add_node(
    "summarize",
    summarize_node,
)


# ==========================================================
# 3. START：判断是否需要摘要
# ==========================================================

builder.add_conditional_edges(
    START,
    should_summarize,
    {
        "summarize": "summarize",
        "agent": "agent",
    },
)


# 摘要完成后进入Agent
builder.add_edge(
    "summarize",
    "agent",
)


# ==========================================================
# 4. Agent → Tool / END
# ==========================================================

builder.add_conditional_edges(
    "agent",
    tools_condition,
)


# Tool执行完成后重新进入Agent
builder.add_edge(
    "tools",
    "agent",
)


# ==========================================================
# 5. Postgres记忆回灌专用Graph
# ==========================================================

def recovery_node(
        state: UserAgentState
):
    """
    Postgres记忆回灌专用节点。

    这个节点：

    - 不调用LLM
    - 不执行Tool
    - 不修改state

    目的仅仅是：

        完整UserAgentState
                ↓
        recovery graph
                ↓
        PostgresSaver生成新的checkpoint
    """

    return {}


recovery_builder = StateGraph(
    UserAgentState
)


recovery_builder.add_node(
    "recovery",
    recovery_node,
)


recovery_builder.add_edge(
    START,
    "recovery",
)


recovery_builder.add_edge(
    "recovery",
    END,
)


# ==========================================================
# 6. PostgreSQL配置
# ==========================================================

DB_URI = settings.postgres_uri


# 当前正常PostgreSQL连接池
pool = None


# 当前PostgresSaver
checkpointer = None


# 正常模式Agent Graph
user_agent_graph = None


# 专门负责：
#
# InMemorySaver
#       ↓
# PostgresSaver
#
# 工作记忆回灌
postgres_recovery_graph = None


# 防止多个并发请求同时初始化PostgreSQL
_postgres_init_lock = Lock()


# ==========================================================
# 7. degraded thread管理
# ==========================================================

# 已经进入fallback的thread。
#
# 一旦thread进入这里，
# 即使PostgreSQL恢复，
# 也不能直接读取旧Postgres checkpoint。
#
# 必须先：
#
# InMemorySaver最新状态
#       ↓
# 回灌PostgresSaver
#       ↓
# 再解除degraded状态
_degraded_threads: set[str] = set()


_degraded_threads_lock = Lock()


def mark_thread_degraded(
        thread_id: str
) -> None:
    """
    标记当前thread已经进入fallback模式。
    """

    with _degraded_threads_lock:

        _degraded_threads.add(
            thread_id
        )


    logger.warning(
        "thread进入fallback模式，thread_id=%s",
        thread_id,
    )


def is_thread_degraded(
        thread_id: str
) -> bool:
    """
    判断当前thread是否处于fallback模式。
    """

    with _degraded_threads_lock:

        return (
            thread_id
            in _degraded_threads
        )


def clear_thread_degraded(
        thread_id: str
) -> None:
    """
    记忆成功回灌PostgresSaver以后，
    解除当前thread的fallback状态。
    """

    with _degraded_threads_lock:

        # discard即使thread不存在也不会抛异常
        _degraded_threads.discard(
            thread_id
        )


    logger.info(
        "thread退出fallback模式，thread_id=%s",
        thread_id,
    )


# ==========================================================
# 8. PostgreSQL旧连接池后台清理
# ==========================================================

def close_pool_in_background(
        old_pool
) -> None:
    """
    在后台关闭已经失效的PostgreSQL连接池。

    为什么不能直接：

        old_pool.close()

    因为PostgreSQL已经异常时，
    ConnectionPool内部worker可能还在等待连接超时。

    如果当前Agent请求线程同步close，
    可能导致用户等待数秒以后才能进入fallback。


    因此采用：

        当前请求
            ↓
        立即解除旧Pool引用
            ↓
        立即进入fallback

    同时：

        后台线程
            ↓
        old_pool.close()
            ↓
        清理旧worker


    这样同时解决：

    1. 用户请求不被close阻塞
    2. 旧pool不会长期残留并反复输出：
       error connecting in 'pool-x'
    """

    if old_pool is None:
        return


    def _close():

        try:

            logger.info(
                "开始后台关闭旧PostgreSQL连接池"
            )


            old_pool.close()


            logger.info(
                "旧PostgreSQL连接池已关闭"
            )


        except Exception as e:

            logger.warning(
                "后台关闭旧PostgreSQL连接池失败：%s",
                e,
            )


    Thread(
        target=_close,
        daemon=True,
        name="postgres-pool-cleaner",
    ).start()


# ==========================================================
# 9. PostgreSQL故障期间的InMemorySaver
# ==========================================================

# PostgreSQL故障以后，
# InMemorySaver临时承担LangGraph Checkpointer职责。
#
# 它能够继续保存：
#
# - messages
# - summary
# - AIMessage tool_calls
# - ToolMessage
# - 未来UserAgentState新增字段
#
# 注意：
#
# InMemorySaver只存在于当前Python进程。
#
# Python一旦重启：
#
# InMemorySaver状态丢失
#       ↓
# 再通过MySQL chat_message恢复基础历史
fallback_checkpointer = InMemorySaver()


user_agent_fallback_graph = builder.compile(
    checkpointer=fallback_checkpointer
)


# ==========================================================
# 10. 判断fallback中是否已有当前thread
# ==========================================================

def has_fallback_checkpoint(
        thread_id: str
) -> bool:
    """
    判断当前thread是否已经在InMemorySaver中建立checkpoint。

    False：
        当前Python进程中第一次进入fallback。
        后续需要从MySQL恢复基础历史。

    True：
        之前已经恢复过。
        后续直接继续使用InMemorySaver，
        不能再次把完整MySQL历史注入Graph。
    """

    config = {
        "configurable": {
            "thread_id": thread_id
        }
    }


    try:

        checkpoint = (
            fallback_checkpointer.get_tuple(
                config
            )
        )


        return (
            checkpoint is not None
        )


    except Exception as e:

        logger.warning(
            "检查fallback checkpoint失败，"
            "thread_id=%s：%s",
            thread_id,
            e,
        )


        return False


# ==========================================================
# 11. PostgreSQL轻量健康检查
# ==========================================================

def can_connect_postgres() -> bool:
    """
    在真正创建ConnectionPool之前，
    先进行一次轻量级PostgreSQL连接测试。

    PostgreSQL已经宕机时：

        不创建ConnectionPool
        不创建pool worker
        不产生大量后台重连日志


    PostgreSQL正常时：

        探活成功
            ↓
        再创建正式ConnectionPool
    """

    try:

        with psycopg.connect(
            DB_URI,
            connect_timeout=settings.postgres_connect_timeout,
            autocommit=True,
        ):
            pass


        return True


    except Exception as e:

        logger.warning(
            "PostgreSQL当前不可用：%s",
            e,
        )


        return False


# ==========================================================
# 12. 初始化PostgreSQL + PostgresSaver + Graph
# ==========================================================

def init_postgres_graph() -> bool:
    """
    初始化：

        PostgreSQL
            ↓
        ConnectionPool
            ↓
        PostgresSaver
            ↓
        正常Agent Graph

    同时初始化：

        recovery graph


    返回：

        True
            PostgreSQL / PostgresSaver正常

        False
            PostgreSQL当前不可用，
            Agent应该继续使用fallback
    """

    global pool
    global checkpointer
    global user_agent_graph
    global postgres_recovery_graph


    # ======================================================
    # 1. 已经初始化完成，不重复创建
    # ======================================================

    if (
        pool is not None
        and checkpointer is not None
        and user_agent_graph is not None
        and postgres_recovery_graph is not None
    ):

        return True


    # ======================================================
    # 2. 防止多个请求同时创建ConnectionPool
    # ======================================================

    with _postgres_init_lock:


        # 获取锁以后再次检查
        if (
            pool is not None
            and checkpointer is not None
            and user_agent_graph is not None
            and postgres_recovery_graph is not None
        ):

            return True


        # ==================================================
        # 3. 先做PostgreSQL轻量探活
        # ==================================================

        if not can_connect_postgres():

            pool = None
            checkpointer = None
            user_agent_graph = None
            postgres_recovery_graph = None


            return False


        # ==================================================
        # 4. PostgreSQL确认正常以后才创建正式Pool
        # ==================================================

        new_pool = None


        try:

            new_pool = ConnectionPool(
                conninfo=DB_URI,
                min_size=settings.postgres_pool_min_size,
                max_size=settings.postgres_pool_max_size,
                open=False,
                kwargs={
                    "autocommit": True,
                    "connect_timeout": settings.postgres_connect_timeout,
                },
                reconnect_timeout=settings.postgres_pool_reconnect_timeout,
            )


            # 打开连接池
            new_pool.open()


            # 等待连接池真正建立数据库连接
            new_pool.wait(
                timeout=settings.postgres_pool_wait_timeout
            )


            # ==================================================
            # 5. 创建PostgresSaver
            # ==================================================

            new_checkpointer = (
                PostgresSaver(
                    new_pool
                )
            )


            # 初始化LangGraph checkpoint相关表
            #
            # setup()幂等：
            # 已存在时不会破坏原checkpoint数据
            new_checkpointer.setup()


            # ==================================================
            # 6. 编译正常Agent Graph
            # ==================================================

            new_graph = builder.compile(
                checkpointer=new_checkpointer
            )


            # ==================================================
            # 7. 编译回灌专用Graph
            # ==================================================

            new_recovery_graph = (
                recovery_builder.compile(
                    checkpointer=new_checkpointer
                )
            )


            # ==================================================
            # 8. 全部初始化成功以后才覆盖全局对象
            #
            # 防止初始化到一半失败以后留下半成品。
            # ==================================================

            pool = new_pool

            checkpointer = (
                new_checkpointer
            )

            user_agent_graph = (
                new_graph
            )

            postgres_recovery_graph = (
                new_recovery_graph
            )


            logger.info(
                "PostgresSaver初始化成功"
            )


            return True


        except Exception as e:

            logger.warning(
                "PostgresSaver初始化失败，"
                "继续使用MySQL + InMemorySaver：%s",
                e,
            )


            # ==================================================
            # 初始化过程中如果new_pool已经创建，
            # 不能在当前请求线程同步close。
            #
            # 交给后台线程慢慢清理，
            # 避免影响fallback切换速度。
            # ==================================================

            if new_pool is not None:

                close_pool_in_background(
                    new_pool
                )


            pool = None
            checkpointer = None
            user_agent_graph = None
            postgres_recovery_graph = None


            return False


# ==========================================================
# 13. 获取当前正常Postgres Graph
# ==========================================================

def get_postgres_graph():
    """
    获取当前最新的PostgresSaver Graph。

    API层建议始终：

        import agent.graph as graph_runtime

        graph_runtime.get_postgres_graph()

    不建议：

        from agent.graph import user_agent_graph

    原因：

    PostgreSQL故障恢复以后，
    user_agent_graph可能会被重新创建。

    如果外部直接导入变量，
    有可能一直持有旧对象引用。
    """

    return user_agent_graph


# ==========================================================
# 14. PostgresSaver运行状态检查
# ==========================================================

def is_checkpointer_available(
        thread_id: str
) -> bool:
    """
    每轮Agent真正执行以前，
    检查PostgresSaver是否能够正常读取checkpoint。

    注意：

    get_tuple()返回None：
        当前thread没有历史checkpoint
        这是完全正常的。

    get_tuple()抛异常：
        PostgreSQL / PostgresSaver异常
        应立即进入fallback。
    """

    global pool
    global checkpointer
    global user_agent_graph
    global postgres_recovery_graph


    # ======================================================
    # 1. PostgreSQL之前可能没有初始化成功。
    #
    # 每一轮请求都允许重新尝试初始化。
    #
    # 这样PostgreSQL恢复以后，
    # 不需要重新启动Python服务。
    # ======================================================

    if not init_postgres_graph():

        return False


    config = {
        "configurable": {
            "thread_id": thread_id
        }
    }


    try:

        # 只读，不修改checkpoint。
        #
        # 返回None也代表数据库正常。
        checkpointer.get_tuple(
            config
        )


        return True


    except Exception as e:

        logger.warning(
            "PostgresSaver运行异常，"
            "thread_id=%s，"
            "准备进入MySQL + InMemorySaver兜底模式：%s",
            thread_id,
            e,
        )


        # ==================================================
        # 2. 保存当前失效Pool引用
        # ==================================================

        old_pool = pool


        # ==================================================
        # 3. 当前Agent立即解除旧Postgres对象引用
        #
        # 不等待旧Pool关闭。
        #
        # 这样当前用户请求可以马上进入fallback。
        # ==================================================

        pool = None
        checkpointer = None
        user_agent_graph = None
        postgres_recovery_graph = None


        # ==================================================
        # 4. 后台关闭失效Pool
        # ==================================================

        close_pool_in_background(
            old_pool
        )


        return False


# ==========================================================
# 15. InMemorySaver → PostgresSaver记忆回灌
# ==========================================================

def recover_thread_to_postgres(
        thread_id: str
) -> bool:
    """
    将当前thread在InMemorySaver中的完整工作记忆
    回灌到PostgresSaver。

    回灌的是完整UserAgentState，而不是简单聊天文本：

        messages
        summary
        AIMessage tool_calls
        ToolMessage
        未来新增的state字段


    成功：

        1. Postgres得到最新state
        2. 回读并校验
        3. 解除degraded
        4. 删除InMemory临时checkpoint
        5. 返回True


    失败：

        1. 不清除degraded
        2. 不删除InMemorySaver
        3. 当前thread继续fallback
        4. 返回False
    """

    global checkpointer
    global user_agent_graph
    global postgres_recovery_graph


    # ======================================================
    # 1. 当前thread没有处于降级状态
    # ======================================================

    if not is_thread_degraded(
            thread_id
    ):

        return True


    config = {
        "configurable": {
            "thread_id": thread_id
        }
    }


    print(
        "\n========== Memory Recovery =========="
    )

    print(
        "准备回灌thread：",
        thread_id,
    )


    # ======================================================
    # 2. 读取InMemorySaver最新完整状态
    # ======================================================

    try:

        fallback_checkpoint = (
            fallback_checkpointer.get_tuple(
                config
            )
        )


        if fallback_checkpoint is None:

            logger.warning(
                "InMemorySaver不存在当前thread，"
                "无法执行记忆回灌，thread=%s",
                thread_id,
            )


            print(
                "InMemorySaver中不存在该thread。"
            )


            print(
                "=====================================\n"
            )


            return False


        fallback_snapshot = (
            user_agent_fallback_graph.get_state(
                config
            )
        )


        # ==================================================
        # 复制整个UserAgentState
        # ==================================================

        state_values = dict(
            fallback_snapshot.values
        )


        if not state_values:

            logger.warning(
                "fallback state为空，thread=%s",
                thread_id,
            )


            return False


        fallback_messages = (
            state_values.get(
                "messages",
                [],
            )
        )


        fallback_summary = (
            state_values.get(
                "summary",
                "",
            )
        )


        print(
            "InMemorySaver消息数量：",
            len(fallback_messages),
        )


        print(
            "是否存在summary：",
            bool(fallback_summary),
        )


    except Exception as e:

        logger.warning(
            "读取InMemorySaver工作记忆失败，"
            "thread=%s：%s",
            thread_id,
            e,
        )


        return False


    # ======================================================
    # 3. 判断PostgreSQL是否已经恢复
    # ======================================================

    if not init_postgres_graph():

        print(
            "PostgreSQL仍不可用，"
            "继续保持InMemorySaver模式。"
        )


        print(
            "=====================================\n"
        )


        return False


    # ======================================================
    # 4. PostgreSQL已经恢复，开始状态迁移
    # ======================================================

    try:

        if (
            checkpointer is None
            or user_agent_graph is None
            or postgres_recovery_graph is None
        ):

            raise RuntimeError(
                "Postgres恢复对象初始化不完整"
            )


        print(
            "PostgreSQL已经恢复，"
            "开始回灌完整工作记忆..."
        )


        # ==================================================
        # 5. 删除故障前Postgres中的旧checkpoint
        #
        # 如果不删除：
        #
        # InMemory messages
        #       +
        # Postgres旧messages
        #
        # 可能通过add_messages reducer再次合并，
        # 造成重复历史。
        #
        # 此时InMemorySaver仍然保存完整工作状态，
        # 所以即使后面的回灌失败，
        # 当前工作记忆也不会丢失。
        # ==================================================

        checkpointer.delete_thread(
            thread_id
        )


        # ==================================================
        # 6. 完整state写入PostgresSaver
        #
        # recovery graph不会：
        #
        # - 调用LLM
        # - 调用Tool
        # - 执行业务操作
        #
        # 只负责生成新的Postgres checkpoint。
        # ==================================================

        postgres_recovery_graph.invoke(
            state_values,
            config=config,
        )


        # ==================================================
        # 7. 回读Postgres状态进行校验
        # ==================================================

        postgres_snapshot = (
            user_agent_graph.get_state(
                config
            )
        )


        postgres_values = dict(
            postgres_snapshot.values
        )


        postgres_messages = (
            postgres_values.get(
                "messages",
                [],
            )
        )


        postgres_summary = (
            postgres_values.get(
                "summary",
                "",
            )
        )


        print(
            "回灌前InMemory消息数量：",
            len(fallback_messages),
        )


        print(
            "回灌后Postgres消息数量：",
            len(postgres_messages),
        )


        # ==================================================
        # 8. 校验messages数量
        # ==================================================

        if (
            len(postgres_messages)
            != len(fallback_messages)
        ):

            raise RuntimeError(
                "Postgres记忆回灌校验失败："
                f"InMemory消息数量={len(fallback_messages)}，"
                f"Postgres消息数量={len(postgres_messages)}"
            )


        # ==================================================
        # 9. 校验summary
        # ==================================================

        if (
            postgres_summary
            != fallback_summary
        ):

            raise RuntimeError(
                "Postgres记忆回灌校验失败："
                "summary不一致"
            )


        # ==================================================
        # 10. 回灌成功，正式退出fallback
        # ==================================================

        clear_thread_degraded(
            thread_id
        )


        # PostgreSQL已经重新成为当前thread的
        # 权威工作记忆存储。
        #
        # 清理临时InMemory checkpoint。
        fallback_checkpointer.delete_thread(
            thread_id
        )


        print(
            "记忆回灌成功，"
            "当前thread恢复PostgresSaver模式。"
        )


        print(
            "=====================================\n"
        )


        return True


    except Exception as e:

        # ==================================================
        # 回灌失败：
        #
        # 绝对不能：
        #
        # clear_thread_degraded()
        #
        # 也不能：
        #
        # fallback_checkpointer.delete_thread()
        #
        # 当前thread继续使用InMemorySaver。
        # ==================================================

        logger.warning(
            "记忆回灌PostgresSaver失败，"
            "thread=%s，"
            "继续使用InMemorySaver：%s",
            thread_id,
            e,
        )


        print(
            "记忆回灌失败，"
            "当前thread继续保持fallback。"
        )


        print(
            "=====================================\n"
        )


        return False


# ==========================================================
# 16. Python服务正常退出时关闭当前Postgres Pool
# ==========================================================

def close_postgres_pool() -> None:
    """
    Python / FastAPI服务正常退出时，
    统一关闭当前正在使用的PostgreSQL连接池。

    与运行中数据库故障不同：

    运行中故障：
        用户还在等待响应
        → 后台close
        → 不能阻塞请求

    Python正常退出：
        服务本来就要停止
        → 可以同步close
        → 等待Pool正常释放worker和连接
    """

    global pool
    global checkpointer
    global user_agent_graph
    global postgres_recovery_graph


    # ======================================================
    # 1. 保存当前Pool
    # ======================================================

    current_pool = pool


    # ======================================================
    # 2. 先解除全局引用
    # ======================================================

    pool = None
    checkpointer = None
    user_agent_graph = None
    postgres_recovery_graph = None


    # ======================================================
    # 3. 当前没有Pool，无需处理
    # ======================================================

    if current_pool is None:

        logger.info(
            "PostgreSQL连接池当前不存在，无需关闭"
        )

        return


    # ======================================================
    # 4. 正常关闭Pool
    # ======================================================

    try:

        logger.info(
            "Python服务正在退出，开始关闭PostgreSQL连接池"
        )


        current_pool.close()


        logger.info(
            "PostgreSQL连接池已正常关闭"
        )


    except Exception as e:

        logger.warning(
            "关闭PostgreSQL连接池失败：%s",
            e,
        )

