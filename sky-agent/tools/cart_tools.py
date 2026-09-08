from typing import Optional
import requests
import json
from langchain_core.tools import tool
from langchain.tools import ToolRuntime
from agent.context import UserAgentContext
from config.settings import settings


# 查询当前登录用户的真实购物车数据。
@tool
def get_my_cart(
    runtime: ToolRuntime[UserAgentContext]
) -> str:
    """
    查询当前登录用户的真实购物车内容。

    适用场景：
    - 用户询问“我的购物车”“查看购物车”“刚才添加了什么”“我买了哪些菜”；
    - 修改购物车数量前确认当前真实购物车状态。

    user_id 从 Runtime Context 自动获取，不允许由用户或模型提供。

    返回给 Agent 的数据会进行轻量化，
    避免图片 URL 等无关字段占用短期记忆上下文。
    """

    user_id = runtime.context.user_id
    url = settings.java_url(f"/internal/agent/cart/{user_id}")

    try:
        response = requests.get(
            url,
            timeout=settings.java_http_timeout
        )
        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"购物车查询失败：{result.get('msg')}"

        cart_items = result.get("data") or []

        if not cart_items:
            return "当前用户的购物车为空。"


        # ==================================================
        # 对 Java 返回的购物车数据进行轻量化
        #
        # 保留 Agent 后续操作真正需要的字段：
        #
        # id          -> 购物车条目 ID，修改/删除时必须使用
        # dishId      -> 菜品 ID
        # setmealId   -> 套餐 ID
        # name        -> 用户自然语言指代时需要
        # dishFlavor  -> 菜品口味/忌口
        # number      -> 当前数量
        # amount      -> 单价
        #
        # image 等展示字段不需要进入 LLM 上下文。
        # ==================================================

        compact_items = []

        for item in cart_items:
            compact_items.append({
                "id": item.get("id"),
                "dishId": item.get("dishId"),
                "setmealId": item.get("setmealId"),
                "name": item.get("name"),
                "dishFlavor": item.get("dishFlavor"),
                "number": item.get("number"),
                "amount": item.get("amount"),
            })


        return json.dumps(
            compact_items,
            ensure_ascii=False,
            separators=(",", ":")
        )

    except requests.RequestException as e:
        return f"调用苍穹外卖购物车服务失败：{str(e)}"


# 将菜品加入当前登录用户的购物车。
@tool
def add_dish_to_cart(
    runtime: ToolRuntime[UserAgentContext],
    dish_id: int,
    dish_name: str,
    dish_flavor: Optional[str] = None
) -> str:
    """
    将真实菜品加入当前登录用户购物车。

    适用场景：
    - 用户明确要求加入、添加、购买某道菜。
    - 用户确认推荐结果，例如：
      “就第一个”
      “要这个”
      “帮我买这个”。

    参数：

    - dish_id:
      必须来自：
      search_dishes、
      search_dishes_with_fallback、
      get_dish_detail、
      或历史真实工具结果。

    - dish_name:
      必须和 dish_id 对应，
      使用工具返回中的真实菜品名称。

    - dish_flavor:
      菜品口味参数。

      填写规则：
      1. 如果用户当前消息明确指定口味，优先使用当前消息。
         例如：
         “不要辣”
         “做微辣”
         “不要葱”

      2. 如果用户没有当前指定口味，
         但长期记忆中存在明确饮食偏好，
         并且该菜品详情支持对应选项，
         可以根据长期偏好自动填写。

      例如：
      用户长期：
      - 喜欢中辣
      - 不吃香菜
      - 不吃葱

      菜品支持：
      - 中辣
      - 不要香菜
      - 不要葱

      则：
      dish_flavor =
      "中辣,不要香菜,不要葱"

      3. 不要根据菜名、菜系、常识猜测口味。

      例如：
      “麻辣牛蛙”
      不代表一定填写“重辣”。

    只有本工具成功返回后，
    才能告诉用户商品已经加入购物车。
    """

    user_id = runtime.context.user_id

    payload = {
        "dishId": dish_id,
        "dishName": dish_name,
        "setmealId": None,
        "dishFlavor": dish_flavor
    }

    url = settings.java_url(
        f"/internal/agent/cart/{user_id}/items"
    )

    try:
        response = requests.post(
            url,
            json=payload,
            timeout=settings.java_http_timeout
        )
        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"添加失败：{result.get('msg')}"

        return f"商品【{dish_name}】已成功加入购物车。"

    except requests.RequestException as e:
        return f"调用购物车服务失败：{str(e)}"


# 将套餐加入当前登录用户的购物车。
@tool
def add_setmeal_to_cart(
    runtime: ToolRuntime[UserAgentContext],
    setmeal_id: int,
    setmeal_name: str
) -> str:
    """
    将真实套餐加入当前用户购物车。

    使用本工具前，必须已经通过 search_setmeals
    获得真实套餐信息。

    参数：

    setmeal_id：
    - 必须来自 search_setmeals 返回结果中的 setmealId
    - 禁止根据套餐名称猜测
    - 不能使用 dishId 代替

    setmeal_name：
    - 必须来自 search_setmeals 返回结果中的 name
    - 必须与 setmeal_id 对应

    例如：

    search_setmeals 返回：

    {
        "setmealId": 3,
        "name": "商务套餐A",
        "price": 88
    }

    则调用：

    add_setmeal_to_cart(
        setmeal_id=3,
        setmeal_name="商务套餐A"
    )

    只有本工具明确返回成功后，
    才可以告诉用户套餐已经加入购物车。
    """

    # 1. 从 Runtime Context 获取可信 userId
    user_id = runtime.context.user_id

    # 2. 构造购物车请求
    payload = {
        "dishId": None,
        "setmealId": setmeal_id,
        "dishFlavor": None
    }

    # 3. 继续复用原来的购物车新增接口
    url = settings.java_url(
        f"/internal/agent/cart/{user_id}/items"
    )

    try:

        response = requests.post(
            url,
            json=payload,
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"套餐加入购物车失败：{result.get('msg')}"

        return f"套餐【{setmeal_name}】已成功加入购物车。"

    except requests.RequestException as e:

        return f"调用购物车服务失败：{str(e)}"


# 修改当前登录用户购物车中某条商品记录的数量。
@tool
def update_cart_item_number(
    runtime: ToolRuntime[UserAgentContext],
    cart_item_id: int,
    number: int
) -> str:
    """
    修改当前用户购物车中某条商品记录的数量。

    使用本工具前，必须先通过 get_my_cart 获取真实购物车数据。

    cart_item_id：
    必须来自 get_my_cart 返回结果中的 id 字段。
    禁止猜测。

    number：
    用户希望修改后的商品数量。
    必须大于0。

    例如：

    用户：
    “把清蒸鲈鱼改成3份”

    应先调用 get_my_cart。

    如果返回：

    {
        "id":43,
        "name":"清蒸鲈鱼",
        "number":1
    }

    则调用：

    update_cart_item_number(
        cart_item_id=43,
        number=3
    )

    禁止根据 dishId 代替 cart_item_id。
    """

    user_id = runtime.context.user_id

    payload = {
        "number": number
    }

    url = settings.java_url(
        f"/internal/agent/cart/{user_id}/items/{cart_item_id}"
    )

    try:

        response = requests.patch(
            url,
            json=payload,
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"修改购物车数量失败：{result.get('msg')}"

        return f"购物车商品数量已修改为 {number} 份。"

    except requests.RequestException as e:

        return f"调用购物车服务失败：{str(e)}"
    

# agent删除购物车商品
@tool
def delete_cart_item(
    runtime: ToolRuntime[UserAgentContext],
    cart_item_id: int
) -> str:
    """
    删除当前用户购物车中的一条商品记录。

    使用本工具前，必须先通过 get_my_cart 获取当前用户真实购物车数据。

    参数：

    cart_item_id：
    - 必须来自 get_my_cart 返回结果中的 id 字段
    - 代表 shopping_cart 表中的购物车记录ID
    - 不是 dishId
    - 不允许猜测

    例如：

    用户：
    “把购物车里的王老吉删掉”

    应先调用：

    get_my_cart()

    如果返回：

    {
        "id": 46,
        "dishId": 46,
        "name": "王老吉",
        "number": 3
    }

    则调用：

    delete_cart_item(
        cart_item_id=46
    )

    禁止使用：

    dishId=46

    来代替购物车记录 id。

    只有本工具明确返回删除成功后，
    才可以告诉用户商品已从购物车删除。
    """

    # 1. 从 Runtime Context 获取可信 userId
    user_id = runtime.context.user_id

    # 2. 基本参数校验
    if cart_item_id <= 0:
        return "购物车记录ID不合法。"

    # 3. 构造 Java 内部接口地址
    url = settings.java_url(
        f"/internal/agent/cart/{user_id}/items/{cart_item_id}"
    )

    try:

        # 4. 调用 Java DELETE 接口
        response = requests.delete(
            url,
            timeout=settings.java_http_timeout
        )

        # 5. 检查 HTTP 状态
        response.raise_for_status()

        # 6. Java 返回 JSON
        result = response.json()

        # 7. 判断业务是否成功
        if result.get("code") != 1:
            return f"删除购物车商品失败：{result.get('msg')}"

        return "购物车商品已成功删除。"

    except requests.RequestException as e:

        return f"调用购物车删除服务失败：{str(e)}"
    
    
# agent清空购物车
@tool
def clear_my_cart(
    runtime: ToolRuntime[UserAgentContext]
) -> str:
    """
    清空当前用户的整个购物车。

    只有当用户明确表达“清空整个购物车”的意图时才调用。

    例如：

    - 清空我的购物车
    - 把购物车里的东西全部删掉
    - 购物车里的东西我都不要了

    调用本工具前，建议先调用 get_my_cart，
    获取当前真实购物车内容。

    注意：

    本工具会删除当前用户购物车中的全部商品记录。

    不适用于删除单个商品。

    如果用户只要求删除某一个商品，
    应使用 delete_cart_item。

    user_id 不由模型生成，
    必须从 Runtime Context 获取。

    只有本工具明确返回成功后，
    才可以告诉用户购物车已经清空。
    """

    # 1. 从 Runtime Context 获取当前可信用户ID
    user_id = runtime.context.user_id

    # 2. Java内部接口
    url = settings.java_url(
        f"/internal/agent/cart/{user_id}"
    )

    try:

        # 3. 调用Java清空购物车接口
        response = requests.delete(
            url,
            timeout=settings.java_http_timeout
        )

        # 4. 检查HTTP状态
        response.raise_for_status()

        # 5. 获取Java业务返回结果
        result = response.json()

        if result.get("code") != 1:
            return f"清空购物车失败：{result.get('msg')}"

        return "购物车已成功清空。"

    except requests.RequestException as e:

        return f"调用购物车清空服务失败：{str(e)}"
