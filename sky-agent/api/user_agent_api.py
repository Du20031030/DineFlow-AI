import json
import traceback

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from langchain_core.messages import (
    HumanMessage,
    AIMessageChunk,
)

from schemas.request import (
    UserAgentChatRequest,
    ChatTitleRequest,
)

from schemas.response import (
    UserAgentChatResponse,
    ChatTitleResponse,
)

from agent.context import UserAgentContext
from agent.fallback import load_mysql_fallback_history

# 注意：
# 不再直接：
#
# from agent.graph import user_agent_graph, checkpointer
#
# 因为PostgreSQL故障 / 恢复以后，
# graph.py中的这些对象可能被重新赋值。
#
# 直接导入模块，每次使用最新对象。
import agent.graph as graph_runtime
from llm.model import llm


router = APIRouter(
    prefix="/agent/user",
    tags=["User Agent"]
)


"""
正常聊天
    ↓
PostgresSaver


PostgreSQL宕机
    ↓
检测失败
    ↓
mark_thread_degraded
    ↓
第一次从MySQL恢复
    ↓
InMemorySaver
    ↓
连续保存完整工作记忆


PostgreSQL重新启动
    ↓
不会立即切换
    ↓
等下一条USER请求
    ↓
recover_thread_to_postgres()
    ↓
读取InMemorySaver完整state
    ↓
删除Postgres旧checkpoint
    ↓
完整state写回Postgres
    ↓
回读校验
    ↓
成功
    ↓
clear_thread_degraded
    ↓
删除InMemory临时checkpoint
    ↓
当前新USER消息
    ↓
PostgresSaver继续 
"""

# ==========================================================
# 1. 根据当前状态选择 Graph
# ==========================================================

def prepare_agent_execution(request: UserAgentChatRequest):
    """
    根据当前thread状态决定本轮使用：

    1. PostgresSaver Graph
    2. InMemorySaver fallback Graph

    返回：

        active_graph
        input_state
        use_fallback
    """

    thread_id = request.thread_id

    use_fallback = False


    # ======================================================
    # A. 当前thread之前已经进入过fallback
    # ======================================================

    if graph_runtime.is_thread_degraded(
            thread_id
    ):

        print(
            f"\nthread已经处于fallback模式，"
            f"thread_id={thread_id}"
        )


        # ==================================================
        # PostgreSQL故障以后，
        # 这个thread一直由InMemorySaver维护。
        #
        # 每个新的用户请求进来时，
        # 先尝试将上一轮结束时的完整工作记忆
        # 回灌到PostgresSaver。
        #
        # 注意：
        # 当前request.message此时尚未进入Graph，
        # 所以不会造成当前USER消息重复。
        # ==================================================

        recovered = (
            graph_runtime.recover_thread_to_postgres(
                thread_id
            )
        )


        if recovered:

            print(
                "工作记忆回灌成功，"
                "当前新请求重新使用PostgresSaver。"
            )

            use_fallback = False


        else:

            print(
                "PostgreSQL仍不可用或记忆回灌失败，"
                "当前请求继续使用InMemorySaver。"
            )

            use_fallback = True


    # ======================================================
    # B. 当前thread还没有进入过fallback
    # ======================================================

    else:

        print(
            f"\n准备检查PostgresSaver，"
            f"thread_id={thread_id}"
        )

        postgres_available = (
            graph_runtime.is_checkpointer_available(
                thread_id
            )
        )


        print(
            "PostgresSaver当前是否可用：",
            postgres_available
        )


        # PostgreSQL当前不可用
        if not postgres_available:

            graph_runtime.mark_thread_degraded(
                thread_id
            )

            use_fallback = True


    # ======================================================
    # 2. fallback模式
    # ======================================================

    if use_fallback:

        print("\n========== Fallback Start ==========")
        print("thread_id：", request.thread_id)
        print("user_id：", request.user_id)
        print("message_id：", request.message_id)
        print("====================================\n")


        # --------------------------------------------------
        # 检查当前thread是否已经在InMemorySaver中存在
        # --------------------------------------------------

        print(
            "准备检查 InMemorySaver checkpoint..."
        )

        has_checkpoint = (
            graph_runtime.has_fallback_checkpoint(
                request.thread_id
            )
        )


        print(
            "InMemorySaver是否已有checkpoint：",
            has_checkpoint
        )


        # ==================================================
        # 2.1 已经存在fallback checkpoint
        # ==================================================

        if has_checkpoint:

            print(
                "当前thread已有fallback工作记忆，"
                "本轮只追加当前USER消息。"
            )


            input_state = {
                "messages": [
                    HumanMessage(
                        content=request.message
                    )
                ]
            }


        # ==================================================
        # 2.2 第一次进入fallback
        # ==================================================

        else:

            print(
                "当前thread第一次进入fallback，"
                "准备从MySQL恢复历史。"
            )


            if request.message_id is None:

                raise RuntimeError(
                    "进入fallback模式时缺少message_id，"
                    "无法从MySQL准确恢复聊天历史"
                )


            print(
                "准备调用Java历史接口，"
                f"user_id={request.user_id}，"
                f"message_id={request.message_id}"
            )


            # ----------------------------------------------
            # Java
            #   ↓
            # MySQL chat_message
            #   ↓
            # USER -> HumanMessage
            # ASSISTANT -> AIMessage
            # ----------------------------------------------

            history_messages = (
                load_mysql_fallback_history(
                    user_id=request.user_id,
                    message_id=request.message_id
                )
            )


            print(
                "MySQL历史恢复完成，消息数量：",
                len(history_messages)
            )


            if not history_messages:

                raise RuntimeError(
                    "MySQL兜底历史为空，"
                    "无法恢复当前会话上下文"
                )


            # ----------------------------------------------
            # 打印恢复结果
            # ----------------------------------------------

            print(
                "\n========== MySQL Fallback History =========="
            )

            for index, message in enumerate(
                    history_messages
            ):

                print(
                    f"[{index}] "
                    f"{type(message).__name__}"
                )

                print(
                    "content：",
                    message.content
                )

                print(
                    "--------------------------------------"
                )


            print(
                "============================================\n"
            )


            # MySQL返回结果已经包含
            # 当前message_id对应的USER消息，
            # 所以这里不能再额外追加HumanMessage。
            input_state = {
                "messages": history_messages
            }


        active_graph = (
            graph_runtime.user_agent_fallback_graph
        )


        print(
            "Fallback输入状态准备完成，"
            "即将执行 user_agent_fallback_graph。"
        )


        return (
            active_graph,
            input_state,
            True
        )


    # ======================================================
    # 3. 正常PostgresSaver模式
    # ======================================================

    active_graph = (
        graph_runtime.get_postgres_graph()
    )


    # 理论上：
    #
    # is_checkpointer_available() == True
    #
    # 这里就不应该是None。
    #
    # 这是防御性处理。
    if active_graph is None:

        print(
            "PostgresSaver检查通过，"
            "但user_agent_graph为空，"
            "临时进入fallback。"
        )


        graph_runtime.mark_thread_degraded(
            request.thread_id
        )


        # --------------------------------------------------
        # 如果fallback中已经存在当前thread
        # --------------------------------------------------

        if graph_runtime.has_fallback_checkpoint(
                request.thread_id
        ):

            input_state = {
                "messages": [
                    HumanMessage(
                        content=request.message
                    )
                ]
            }


        # --------------------------------------------------
        # fallback中也不存在
        # 从MySQL恢复
        # --------------------------------------------------

        else:

            if request.message_id is None:

                raise RuntimeError(
                    "进入fallback模式时缺少message_id"
                )


            history_messages = (
                load_mysql_fallback_history(
                    user_id=request.user_id,
                    message_id=request.message_id
                )
            )


            if not history_messages:

                raise RuntimeError(
                    "MySQL兜底历史为空"
                )


            input_state = {
                "messages": history_messages
            }


        active_graph = (
            graph_runtime.user_agent_fallback_graph
        )


        return (
            active_graph,
            input_state,
            True
        )


    # ======================================================
    # 4. 正常Postgres模式
    # ======================================================

    input_state = {
        "messages": [
            HumanMessage(
                content=request.message
            )
        ]
    }


    return (
        active_graph,
        input_state,
        False
    )


# ==========================================================
# 2. 普通聊天接口
# ==========================================================

@router.post( "/chat",response_model=UserAgentChatResponse)
def chat(request: UserAgentChatRequest):

    config = {
        "configurable": {
            "thread_id": request.thread_id
        }
    }


    context = UserAgentContext(
        user_id=request.user_id,
        role=request.role,
    )

    # 与SSE接口使用完全相同的
    # Postgres / fallback选择逻辑。
    active_graph, input_state, use_fallback = (
        prepare_agent_execution(
            request
        )
    )


    print(
        "\n========== Normal Chat Graph =========="
    )

    print(
        "当前Graph模式：",
        "FALLBACK"
        if use_fallback
        else "POSTGRES"
    )

    print(
        "thread_id：",
        request.thread_id
    )

    print(
        "=======================================\n"
    )


    result = active_graph.invoke(
        input_state,
        config=config,
        context=context
    )


    final_message = (
        result["messages"][-1]
    )


    return UserAgentChatResponse(
        thread_id=request.thread_id,
        content=final_message.content
    )


# ==========================================================
# 3. SSE流式聊天接口
# ==========================================================
@router.post("/chat/stream")
def chat_stream(request: UserAgentChatRequest):
    """
    SSE流式聊天接口。

    正常：

        PostgresSaver
            ↓
        LangGraph

    PostgreSQL故障：

        第一次：
            MySQL历史
                ↓
            InMemorySaver

        后续：
            InMemorySaver继续保存工作记忆

    当前thread一旦进入degraded状态，
    即使PostgreSQL恢复，
    暂时也不会立即切回PostgresSaver。

    后续会增加：
        InMemorySaver
            ↓
        PostgresSaver
        状态回灌机制。
    """


    config = {
        "configurable": {
            "thread_id": request.thread_id
        }
    }


    context = UserAgentContext(
        user_id=request.user_id,
        role=request.role
    )


    def event_generator():

        try:

            # ==================================================
            # 1. 选择本轮Graph
            # ==================================================

            active_graph, input_state, use_fallback = (
                prepare_agent_execution(
                    request
                )
            )


            # ==================================================
            # 2. 开始执行Graph
            # ==================================================

            print(
                "\n========== Graph Stream Start =========="
            )

            print(
                "当前Graph模式：",
                "FALLBACK"
                if use_fallback
                else "POSTGRES"
            )

            print(
                "thread_id：",
                request.thread_id
            )

            print(
                "========================================\n"
            )


            # messages模式可以获得
            # 模型生成过程中的AIMessageChunk。
            for message, metadata in active_graph.stream(
                    input_state,
                    config=config,
                    context=context,
                    stream_mode="messages"
            ):

                # ==================================================
                # 只把真正用户可见的Agent文本发送给前端
                # ==================================================

                # summarize节点、tools节点等不输出
                if (
                    metadata.get("langgraph_node")
                    != "agent"
                ):
                    continue


                # 只接受模型流式chunk
                if not isinstance(
                        message,
                        AIMessageChunk
                ):
                    continue


                content = message.content


                # tool_call阶段通常content为空
                if not content:
                    continue


                if not isinstance(
                        content,
                        str
                ):
                    continue


                event = {
                    "type": "token",
                    "content": content
                }


                yield (
                    "data: "
                    + json.dumps(
                        event,
                        ensure_ascii=False
                    )
                    + "\n\n"
                )


            # ==================================================
            # 3. 当前Agent整轮运行完成
            # ==================================================

            print(
                "\n========== Graph Stream Done =========="
            )

            print(
                "thread_id：",
                request.thread_id
            )

            print(
                "当前Graph模式：",
                "FALLBACK"
                if use_fallback
                else "POSTGRES"
            )

            print(
                "=======================================\n"
            )


            yield (
                "data: "
                + json.dumps(
                    {
                        "type": "done"
                    },
                    ensure_ascii=False
                )
                + "\n\n"
            )


        except Exception as e:

            # ==================================================
            # 最终兜底
            #
            # 到这里说明：
            #
            # 1. PostgresSaver可能已经不可用
            # 2. fallback也可能失败
            # 3. 或LLM / Tool / Java内部接口发生异常
            #
            # 注意：
            # 这里绝对不能重新执行整个Graph。
            #
            # 因为Tool可能已经产生业务副作用，例如：
            # - 加入购物车
            # - 修改购物车
            # - 清空购物车
            #
            # 如果重新执行Graph，可能导致重复操作。
            # ==================================================

            logger.exception(
                "Agent执行最终失败，thread_id=%s",
                request.thread_id
            )


            print(
                "\n========== Agent Final Error =========="
            )

            print(
                "thread_id：",
                request.thread_id
            )

            print(
                "真实异常：",
                repr(e)
            )

            print(
                "=======================================\n"
            )


            # ==================================================
            # 前端只接收友好、稳定的错误信息。
            #
            # 不把数据库、SSL、API等内部异常直接暴露给用户。
            # ==================================================

            error_data = {
                "type": "error",
                "code": "AGENT_SERVICE_UNAVAILABLE",
                "message": "智能助手暂时不可用，请稍后再试"
            }


            yield (
                f"data: "
                f"{json.dumps(error_data, ensure_ascii=False)}"
                f"\n\n"
            )


            # 发生异常以后直接结束SSE。
            #
            # 不再发送done，
            # 防止Java误认为本轮Agent正常完成。
            return


    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={

            # SSE禁止缓存
            "Cache-Control": "no-cache",

            # 防止Nginx缓存SSE响应
            "X-Accel-Buffering": "no",

            # 保持连接
            "Connection": "keep-alive"
        }
    )


# ==========================================================
# 4. 新会话标题生成
# ==========================================================

@router.post( "/title",response_model=ChatTitleResponse)
def generate_chat_title(request: ChatTitleRequest):
    """
    根据新会话第一轮：

        USER
        +
        ASSISTANT

    自动生成简短会话标题。

    该功能不属于LangGraph短期记忆：

    - 不使用thread_id
    - 不读取checkpoint
    - 不写入checkpoint
    """


    prompt = f"""
    请根据下面第一轮聊天内容生成一个简洁的中文会话标题。

    【用户】
    {request.user_message}

    【助手】
    {request.assistant_message}

    要求：
    1. 只输出标题，不要解释；
    2. 标题控制在 4～10 个汉字；
    3. 不要使用引号；
    4. 不要使用句号、冒号等结尾标点；
    5. 准确概括用户这次会话的主要需求；
    6. 不要使用“新聊天”“用户咨询”“聊天记录”等空泛标题。

    示例：

    用户：根据我的口味推荐几个菜
    标题：个性化菜品推荐

    用户：帮我看看现在购物车有什么
    标题：购物车查询

    用户：有什么套餐推荐
    标题：套餐推荐
    """


    response = llm.invoke(
        prompt
    )


    title = (
        str(response.content)
        .strip()
    )


    # 防止模型返回引号 / 换行
    title = (
        title
        .replace('"', '')
        .replace("'", '')
        .replace("“", '')
        .replace("”", '')
        .replace("\n", '')
        .strip()
    )


    # 长度保护
    if len(title) > 10:

        title = title[:10]


    # 极端情况下模型没有产生标题
    if not title:

        title = "新聊天"


    return ChatTitleResponse(
        title=title
    )


# ==========================================================
# 5. 删除短期记忆
# ==========================================================

@router.delete("/thread/{thread_id}")
def delete_thread_memory(thread_id: str):
    """
    删除当前thread的短期记忆。

    同时尝试删除：

    1. PostgresSaver checkpoint
    2. InMemorySaver fallback checkpoint
    """

    errors = []


    # ======================================================
    # 1. 删除PostgresSaver
    # ======================================================

    current_checkpointer = (
        graph_runtime.checkpointer
    )


    if current_checkpointer is not None:

        try:

            current_checkpointer.delete_thread(
                thread_id
            )

        except Exception as e:

            errors.append(
                f"PostgresSaver删除失败：{str(e)}"
            )


    # ======================================================
    # 2. 删除fallback InMemorySaver
    # ======================================================

    try:

        graph_runtime.fallback_checkpointer.delete_thread(
            thread_id
        )

    except Exception as e:

        errors.append(
            f"InMemorySaver删除失败：{str(e)}"
        )


    # ======================================================
    # 3. 返回结果
    # ======================================================

    if errors:

        return {
            "code": 0,
            "msg": "；".join(errors)
        }


    return {
        "code": 1,
        "msg": "短期记忆删除成功"
    }