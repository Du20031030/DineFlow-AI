import json
from typing import Any
import requests
from langchain.tools import tool, ToolRuntime
from agent.context import UserAgentContext
from config.settings import settings


# 获取记忆
@tool
def get_memories(
    runtime: ToolRuntime[UserAgentContext],
    namespace: str
) -> str:
    """
    查询当前登录用户在指定 namespace 下的长期记忆。
    当需要根据用户长期偏好、目标或约束进行回答时使用。

    例如：
    - 推荐菜品前查询 food.taste
    - 查询用户长期口味偏好
    - 查询用户已经明确保存的长期偏好

    namespace 示例：
    - food.taste
    - food.category
    - food.ingredient
    - food.budget

    user_id 由系统运行时上下文自动提供，
    不允许由模型自行指定。
    
    注意：
    不要在执行 remember_memory 前调用本工具检查是否已经存在。
    新增、修改或删除长期记忆时，
    直接调用 remember_memory。
    只有当用户询问已有长期记忆内容，
    或者回答问题需要读取用户历史偏好时，
    才调用 get_memories。
    """

    # 1. 从可信 Runtime Context 获取当前用户ID
    user_id = runtime.context.user_id

    # 2. Java Memory 查询接口
    url = settings.java_url(f"/internal/agent/memory/{user_id}")

    try:

        # 3. 调用Java Memory服务
        response = requests.get(
            url,
            params={
                "namespace": namespace
            },
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        # 4. 获取Java统一返回结果
        result = response.json()

        # 5. Java业务调用失败
        if result.get("code") != 1:
            return f"查询长期记忆失败：{result.get('msg')}"

        memories = result.get("data") or []

        # 6. 当前namespace没有长期记忆
        if not memories:
            return f"当前用户在 {namespace} 下没有已保存的长期记忆。"

        # 7. 返回标准JSON，方便LLM读取
        return format_memories(memories)

    except requests.RequestException as e:
        return f"调用长期记忆服务失败：{str(e)}"

    except ValueError as e:
        return f"解析长期记忆服务响应失败：{str(e)}"
    

## 查询当前任务场景下的相关长期记忆
@tool
def get_relevant_memories(
    runtime: ToolRuntime[UserAgentContext],
    scene: str
) -> str:
    """
    根据当前任务场景自动召回相关长期记忆。

    不需要模型指定namespace。

    使用场景：

    dish_recommendation:
    菜品推荐、套餐推荐、口味推荐

    order_assist:
    下单辅助


    示例：

    用户：
    根据我的口味推荐几个菜

    调用：

    get_relevant_memories(
        scene="dish_recommendation"
    )

    系统会自动召回：
    - 辣度偏好
    - 忌口食材
    - 喜欢类别
    - 预算偏好


    user_id由Runtime Context自动提供。
    """

    user_id = runtime.context.user_id


    url = settings.java_url(
        f"/internal/agent/memory/{user_id}/scene"
    )


    try:

        response = requests.get(
            url,
            params={
                "scene": scene
            },
            timeout=settings.java_http_timeout
        )


        response.raise_for_status()


        result = response.json()


        if result.get("code") != 1:

            return (
                f"查询场景Memory失败："
                f"{result.get('msg')}"
            )


        memories = result.get("data") or []


        if not memories:

            return (
                "当前场景没有可用的长期记忆。"
            )


        return format_memories(
            memories
        )


    except requests.RequestException as e:

        return (
            f"调用Memory服务失败：{str(e)}"
        )
    
    
# 保存记忆
@tool
def remember_memory(
    runtime: ToolRuntime[UserAgentContext],
    namespace: str,
    memory_key: str,
    value: Any,
    operation: str
) -> str:
    """
    保存、更新或移除当前登录用户明确表达的长期记忆。

    只有当用户表达的是稳定、跨会话仍然有意义的信息时才调用。
    临时需求、当前业务状态、模型自行推断出的偏好不得写入长期记忆。

    当前支持的Memory Schema：

    1. 长期辣度偏好
       namespace = food.taste
       memory_key = spicy_level
       value只能是：
       - 不辣
       - 微辣
       - 中辣
       - 重辣

       operation必须使用：
       - SET

       示例：
       “我以后喜欢中辣”
       ->
       namespace = food.taste
       memory_key = spicy_level
       value = 中辣
       operation = SET


    2. 长期不喜欢的食材
       namespace = food.ingredient
       memory_key = disliked

       operation：
       - ADD：新增一种长期不喜欢的食材
       - REMOVE：用户明确表示不再讨厌某种食材

       示例：
       “我平时不喜欢香菜”
       ->
       namespace = food.ingredient
       memory_key = disliked
       value = 香菜
       operation = ADD

       “我也不喜欢葱”
       ->
       namespace = food.ingredient
       memory_key = disliked
       value = 葱
       operation = ADD

       “我现在不讨厌香菜了”
       ->
       namespace = food.ingredient
       memory_key = disliked
       value = 香菜
       operation = REMOVE


    操作规则：

    SET：
    用于单值长期记忆。
    新值会替换原来的当前值。

    ADD：
    用于多值长期记忆。
    增加一个独立的原子记忆。

    REMOVE：
    用于多值长期记忆。
    删除指定的原子记忆。


    不允许保存：
    - “今天想吃微辣”
    - “这一顿不要香菜”
    - 当前购物车
    - 当前订单状态
    - 默认地址
    - 当前菜品价格
    - 根据购买行为自行推断出的偏好
    
    调用规则：

    以下情况直接调用 remember_memory：

    1. 用户明确新增长期偏好

    例如：
    “我不喜欢香菜”
    “记住我喜欢微辣”

    无需先查询已有Memory。


    2. 用户明确修改已有偏好

    例如：
    “我以前喜欢微辣，现在喜欢中辣”

    直接使用 SET 更新。


    3. 用户明确撤销已有偏好

    例如：
    “我现在不讨厌香菜了”

    直接使用 REMOVE。


    不要为了确认Memory是否存在而提前调用get_memories。
    Memory数据库会自行处理：
    - ADD重复
    - REMOVE不存在
    - SET覆盖

    user_id由系统Runtime Context自动提供，
    不允许模型自行指定。
    """

    user_id = runtime.context.user_id

    # 获取当前threadId，仅作为Memory来源追踪
    thread_id = None

    try:
        configurable = runtime.config.get("configurable", {})
        thread_id = configurable.get("thread_id")
    except Exception:
        pass

    source_ref = (
        f"thread:{thread_id}"
        if thread_id
        else None
    )

    # 将operation统一成大写
    operation = operation.upper()

    # 生成简短的来源说明
    if isinstance(value, str):
        value_text = value
    else:
        value_text = json.dumps(
            value,
            ensure_ascii=False
        )

    evidence_summary = (
        f"用户明确设置长期记忆："
        f"{namespace}.{memory_key} "
        f"{operation} {value_text}"
    )

    url = settings.java_url(
        f"/internal/agent/memory/{user_id}"
    )

    payload = {
        "namespace": namespace,
        "memoryKey": memory_key,
        "value": value,
        "operation": operation,
        "sourceRef": source_ref,
        "evidenceSummary": evidence_summary,
        "expiresAt": None
    }

    try:
        response = requests.post(
            url,
            json=payload,
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        result = response.json()
        
        if result.get("code") != 1:
            return f"长期记忆操作失败：{result.get('msg')}"

        memory_result = result.get("data")

        if memory_result == "REMOVED":
            return (
                f"长期记忆移除成功："
                f"{namespace}.{memory_key} - {value_text}"
            )

        if memory_result == "NOT_FOUND":
            return (
                f"没有找到需要移除的长期记忆："
                f"{namespace}.{memory_key} = {value_text}"
            )

        if memory_result == "SAVED":
            return (
                f"长期记忆保存成功："
                f"{namespace}.{memory_key} = {value_text}"
            )

        return f"长期记忆操作完成，结果：{memory_result}"

    except requests.RequestException as e:
        return (
            f"调用长期记忆服务失败："
            f"{str(e)}"
        )

    except ValueError as e:
        return (
            f"解析长期记忆服务响应失败："
            f"{str(e)}"
        )


# 记忆格式输出定义
def format_memories(memories):

    """
    将数据库Memory转换成LLM容易理解的语义描述。

    输入:
    [
        {
            "namespace": "food.ingredient",
            "memoryKey": "disliked",
            "value": "香菜"
        }
    ]

    输出:
    用户长期不喜欢的食材：
    - 香菜
    """

    grouped = {}


    for memory in memories:

        namespace = memory.get("namespace")

        memory_key = memory.get("memoryKey")

        value = memory.get("value")


        group_key = (
            namespace,
            memory_key
        )


        if group_key not in grouped:
            grouped[group_key] = []


        grouped[group_key].append(
            str(value)
        )


    result = []


    for (namespace, memory_key), values in grouped.items():


        # 忌口
        if (
            namespace == "food.ingredient"
            and memory_key == "disliked"
        ):

            result.append(
                "用户长期不喜欢的食材："
                + "、".join(values)
            )


        # 辣度偏好
        elif (
            namespace == "food.taste"
            and memory_key == "spicy_level"
        ):

            result.append(
                "用户长期辣度偏好："
                + "、".join(values)
            )


        # 其他未知Memory
        else:

            result.append(
                f"{namespace}.{memory_key}："
                + "、".join(values)
            )


    if not result:

        return "当前没有可用的长期记忆。"


    return "\n".join(result)
