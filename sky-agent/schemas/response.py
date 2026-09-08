from pydantic import BaseModel


class UserAgentChatResponse(BaseModel):
    """
    Python Agent 返回给 Java 后端的聊天结果
    """

    thread_id: str

    content: str
    
    
    
class ChatTitleResponse(BaseModel):
    """
    会话标题生成结果。
    """

    title: str