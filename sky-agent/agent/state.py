"""
TypedDict它不是普通对象模型，而更像是在告诉 Python：
这个字典应该有哪些字段，以及每个字段是什么类型。

Annotated 在“字段类型”之外，再额外告诉 LangGraph这个字段应该如何处理
list[BaseMessage]
      ↓
这个字段存消息列表

add_messages
      ↓
新消息来了以后，用这个规则进行合并
"""

from typing import Annotated, TypedDict

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


class UserAgentState(TypedDict, total=False):
    """
    用户 Agent 的运行状态。

    该 State 会由 LangGraph Checkpointer 持久化到 PostgreSQL。
    同一个 thread_id 再次运行时，会自动恢复这里的数据。
    """

    # 当前会话的消息状态。
    #
    # add_messages 是 LangGraph 提供的 reducer，
    # 新一轮输入不会覆盖旧消息，而是追加到现有 messages 中。
    #
    # 其中可能包含：
    # HumanMessage
    # AIMessage
    # ToolMessage
    messages: Annotated[
        list[BaseMessage],
        add_messages
    ]


    # 较早聊天历史的滚动摘要。
    #
    # 当 messages 过长时：
    # 旧消息 -> LLM生成摘要 -> 保存到 summary
    #
    # 后续只保留：
    # summary + 最近一段 messages
    #
    # 从而避免上下文无限增长。
    summary: str