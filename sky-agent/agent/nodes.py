from langchain_core.messages import SystemMessage
from langgraph.runtime import Runtime
from llm.model import llm
from agent.state import UserAgentState
from agent.context import UserAgentContext
from langchain_core.messages import HumanMessage, RemoveMessage
from prompts.user_agent_prompt import USER_AGENT_SYSTEM_PROMPT
from tools.dish_tools import search_dishes, get_dish_detail,search_dishes_with_fallback
from tools.setmeal_tools import search_setmeals
from tools.cart_tools import get_my_cart, add_dish_to_cart, add_setmeal_to_cart,update_cart_item_number,delete_cart_item,clear_my_cart
from tools.address_tools import get_default_address,get_my_addresses,set_default_address,add_address,update_address,delete_address
from tools.memory_tools import get_memories,remember_memory,get_relevant_memories
import os

tools = [
    search_dishes,
    get_dish_detail,
    search_dishes_with_fallback,
    search_setmeals,
    get_my_cart,
    add_dish_to_cart,
    add_setmeal_to_cart,
    update_cart_item_number,
    delete_cart_item,
    clear_my_cart,
    get_default_address,
    get_my_addresses,
    set_default_address,
    add_address,
    update_address,
    delete_address,
    get_memories,
    remember_memory,
    get_relevant_memories,
]


llm_with_tools = llm.bind_tools(tools)


def agent_node(
    state: UserAgentState,
    runtime: Runtime[UserAgentContext]
):
    print("\n========== Runtime Context ==========")
    print("user_id：", runtime.context.user_id)
    print("role：", runtime.context.role)
    print("=====================================\n")

    # 获取当前保留的短期消息
    history_messages = state.get("messages", [])

    # 获取已经压缩好的历史摘要
    summary = state.get("summary", "")

    print("\n========== Conversation Summary ==========")

    if summary:
        print(summary)
    else:
        print("暂无历史摘要")

    print("==========================================\n")


    print("\n========== History Messages ==========")

    for index, message in enumerate(history_messages):
        print(f"[{index}] {type(message).__name__}")
        print("content：", message.content)

        if hasattr(message, "tool_calls"):
            print("tool_calls：", message.tool_calls)

        print("--------------------------------------")

    print("======================================\n")


    # ==================================================
    # 构造 System Prompt
    # ==================================================

    system_prompt = USER_AGENT_SYSTEM_PROMPT

    # 如果之前已经产生会话摘要，
    # 将摘要作为 Agent 的历史上下文加入 System Prompt。
    if summary:
        system_prompt += f"""
        【当前会话历史摘要】
        {summary}
        以上内容是当前会话中较早历史消息的压缩摘要，
        请将其与下面保留的最近消息共同作为当前会话上下文。

        注意：
        1. 摘要用于理解历史语境、指代关系以及之前发生过的操作；
        2. 购物车、地址等实时业务状态不能仅依赖摘要判断；
        3. 如用户询问当前真实业务状态，仍应调用对应工具获取最新数据。
        """


    # ==================================================
    # 最终发送给大模型的上下文
    #
    # System Prompt
    #       +
    # 历史摘要
    #       +
    # 最近 messages
    # ==================================================

    messages = [
        SystemMessage(content=system_prompt),
        *history_messages
    ]

    # ==================================================
    # 调用Agent大模型
    # ==================================================

    response = llm_with_tools.invoke(messages)

    print("\n========== Agent Node ==========")
    print("模型文本：", response.content)
    print("Tool Calls：", response.tool_calls)
    print("================================\n")


    return {
        "messages": [response]
    }
    
    
    # ==================== 短期记忆配置 ====================





# ==================== 短期记忆配置 ====================

# Message 数量达到该值时触发摘要
SUMMARY_MESSAGE_THRESHOLD = 30

# 所有消息正文累计字符数达到该值时触发摘要
#
# 这里先使用字符数而不是精确 Token：
# 1. 不依赖特定模型 tokenizer；
# 2. DeepSeek / OpenAI 等模型切换时不用修改；
# 3. 作为工程上的摘要触发指标已经足够稳定。
#
# 后续有需要再升级为真正 Token 统计。
SUMMARY_CHAR_THRESHOLD = 12000

# 摘要完成后保留最近几个完整用户回合
KEEP_RECENT_TURNS = 3


def find_summary_cutoff(messages):
    """
    找到短期记忆的安全切分位置。

    一个完整用户回合定义为：
    HumanMessage
        ↓
    AIMessage
        ↓
    ToolMessage / AIMessage ...
        ↓
    下一个 HumanMessage

    摘要时保留最近 KEEP_RECENT_TURNS 个完整回合，
    避免把 AI tool_call 和 ToolMessage 从中间拆开。
    """

    # 找到所有用户消息的位置
    human_indexes = [
        index
        for index, message in enumerate(messages)
        if isinstance(message, HumanMessage)
    ]

    # 连最近几个完整回合都没有积累出来，
    # 暂时不进行删除。
    if len(human_indexes) <= KEEP_RECENT_TURNS:
        return None

    # 最近 KEEP_RECENT_TURNS 个用户回合中的第一个 HumanMessage
    # 就是新的消息窗口起点。
    cutoff_index = human_indexes[-KEEP_RECENT_TURNS]

    return cutoff_index


def calculate_message_chars(messages) -> int:
    """
    粗略计算当前 messages 的上下文大小。

    Message.content 大部分情况下是字符串，
    某些模型也可能返回列表等结构，因此统一转成 str。
    """
    total_chars = 0

    for message in messages:
        content = getattr(message, "content", "")

        if content is not None:
            total_chars += len(str(content))

        # AIMessage 中的 tool_calls 同样会占用模型上下文，
        # 因此也需要计入。
        tool_calls = getattr(message, "tool_calls", None)

        if tool_calls:
            total_chars += len(str(tool_calls))

    return total_chars



# 判断是否需要压缩摘要节点
def should_summarize(state: UserAgentState) -> str:
    """
    判断当前短期记忆是否需要压缩。

    满足以下任一条件：
    1. Message 数量达到阈值；
    2. Message 内容总字符数达到阈值。

    同时还要求：
    必须有足够多的完整用户回合可以被压缩。
    """

    messages = state.get("messages", [])

    # 当前消息数量
    message_count = len(messages)

    # 当前消息总字符数
    total_chars = calculate_message_chars(messages)

    # 当前完整用户回合数量
    human_turn_count = sum(
        1
        for message in messages
        if isinstance(message, HumanMessage)
    )

    print("\n========== Memory Check ==========")
    print("当前消息数量：", message_count)
    print("当前字符数量：", total_chars)
    print("当前用户回合数：", human_turn_count)
    print("消息数量阈值：", SUMMARY_MESSAGE_THRESHOLD)
    print("字符数量阈值：", SUMMARY_CHAR_THRESHOLD)
    print("保留最近回合数：", KEEP_RECENT_TURNS)
    print("==================================\n")


    # 先判断是否达到摘要阈值
    need_summary = (
        message_count >= SUMMARY_MESSAGE_THRESHOLD
        or total_chars >= SUMMARY_CHAR_THRESHOLD
    )


    # 必须同时满足：
    #
    # 1. 上下文已经达到摘要阈值
    # 2. 当前用户回合数 > 要保留的最近回合数
    #
    # 否则没有旧回合可以安全压缩。
    if (
        need_summary
        and human_turn_count > KEEP_RECENT_TURNS
    ):
        print("短期记忆达到阈值，开始执行摘要。\n")
        return "summarize"


    print("短期记忆未达到摘要条件，直接进入 Agent。\n")
    return "agent"



# 摘要压缩节点
def summarize_node(state: UserAgentState):
    """
    将较早的历史消息压缩为滚动摘要，
    并删除已经被摘要覆盖的旧消息。
    """

    messages = state.get("messages", [])
    old_summary = state.get("summary", "")

    # 找到完整用户回合的安全切分位置
    cutoff_index = find_summary_cutoff(messages)

    # 当前还没有足够多的完整回合，
    # 本轮暂时不执行压缩。
    if cutoff_index is None:
        return {}

    # cutoff_index 之前的消息全部进行摘要
    messages_to_summarize = messages[:cutoff_index]

    # 如果找不到安全切分位置，本轮先不摘要
    if cutoff_index is None or cutoff_index == 0:
        return {}

    # 这些消息将进入摘要
    messages_to_summarize = messages[:cutoff_index]


    # --------------------------------------------------
    # 构造摘要指令
    # --------------------------------------------------

    if old_summary:

        summary_prompt = f"""
        下面是当前会话已经存在的历史摘要：

        【已有摘要】
        {old_summary}

        请结合本次新增的较早聊天记录，
        更新为一份新的滚动会话摘要。

        请重点保留：

        1. 用户当前或之前的重要需求；
        2. 菜品、套餐等候选项及其编号、名称、ID；
        3. “第一个、第二个”等可能影响后续指代的信息；
        4. 用户在当前会话中明确提出的临时要求；
        5. 已执行的重要操作及其结果；
        6. 尚未完成、等待继续处理的任务。

        可以删除：

        - 无意义的搜索过程；
        - 失败重试；
        - 重复描述；
        - 不重要的 Tool 调用细节。

        注意：
        购物车、地址等实时业务数据只能作为历史事件记录，
        后续需要获取当前真实状态时仍应调用业务工具。

        请输出简洁、清晰的会话摘要。
        """

    else:

        summary_prompt = """
        请把前面的聊天记录压缩成一份短期会话摘要。

        请重点保留：

        1. 用户当前或之前的重要需求；
        2. 菜品、套餐等候选项及其编号、名称、ID；
        3. “第一个、第二个”等可能影响后续指代的信息；
        4. 用户在当前会话中明确提出的临时要求；
        5. 已执行的重要操作及其结果；
        6. 尚未完成、等待继续处理的任务。

        可以删除：

        - 无意义的搜索过程；
        - 失败重试；
        - 重复描述；
        - 不重要的 Tool 调用细节。

        注意：
        购物车、地址等实时业务数据只能作为历史事件记录，
        后续需要获取当前真实状态时仍应调用业务工具。

        请输出简洁、清晰的会话摘要。
        """


    # --------------------------------------------------
    # 调用原始 LLM 生成摘要
    #
    # 注意这里使用 llm，
    # 不使用绑定了 tools 的 llm_with_tools。
    # --------------------------------------------------

    response = llm.invoke(
        [
            *messages_to_summarize,
            HumanMessage(content=summary_prompt)
        ]
    )

    new_summary = response.content


    # --------------------------------------------------
    # 删除已经被摘要覆盖的旧消息
    #
    # RemoveMessage 会通过 add_messages reducer
    # 从当前 LangGraph State 中移除这些消息。
    # --------------------------------------------------

    messages_to_delete = [
        RemoveMessage(id=message.id)
        for message in messages_to_summarize
    ]


    print("\n========== Conversation Summary ==========")
    print("原消息数量：", len(messages))
    print("本次摘要消息数量：", len(messages_to_summarize))
    print("摘要后保留消息数量：", len(messages) - len(messages_to_summarize))
    print("------------------------------------------")
    print(new_summary)
    print("==========================================\n")


    return {
        "summary": new_summary,
        "messages": messages_to_delete
    }