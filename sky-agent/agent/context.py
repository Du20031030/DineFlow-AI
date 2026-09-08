from dataclasses import dataclass


@dataclass
class UserAgentContext:
    """
    Agent每次运行时使用的可信上下文
    """

    # 当前登录用户ID
    user_id: int

    # 当前用户角色
    role: str
    