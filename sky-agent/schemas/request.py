from pydantic import BaseModel
from typing import Optional
from pydantic import BaseModel


class UserAgentChatRequest(BaseModel):
    user_id: int
    role: str
    message: str
    thread_id: str

    # 当前USER消息在MySQL chat_message中的主键
    # PostgresSaver异常时用于准确恢复当前会话历史
    message_id: int | None = None


class ChatTitleRequest(BaseModel):
    """
    根据首轮对话生成会话标题。
    """

    user_message: str

    assistant_message: str