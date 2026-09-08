import requests

from config.settings import settings
from langchain_core.messages import (
    BaseMessage,
    HumanMessage,
    AIMessage,
)


def load_mysql_fallback_history(
        user_id: int,
        message_id: int
) -> list[BaseMessage]:
    """
    PostgresSaver 不可用时，
    从 Java -> MySQL 加载当前会话历史消息。

    Java返回的历史已经包含当前 message_id 对应的 USER 消息，
    因此调用方不能再次追加当前 HumanMessage。
    """

    url = settings.java_url("/internal/agent/chat/history")

    response = requests.get(
        url,
        params={
            "userId": user_id,
            "messageId": message_id
        },
        timeout=settings.java_http_timeout
    )

    # HTTP状态异常，例如500、404
    response.raise_for_status()

    body = response.json()

    # 苍穹外卖统一Result返回
    if body.get("code") != 1:
        raise RuntimeError(
            f"MySQL兜底历史加载失败: {body}"
        )

    data = body.get("data") or []

    messages: list[BaseMessage] = []

    for item in data:

        role = str(item.get("role", "")).upper()
        content = item.get("content") or ""

        if role == "USER":
            messages.append(
                HumanMessage(content=content)
            )

        elif role == "ASSISTANT":
            messages.append(
                AIMessage(content=content)
            )

    return messages
