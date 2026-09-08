from typing import Optional
import json
import requests
from langchain_core.tools import tool
from config.settings import settings


# 查询在售菜品，返回真实业务数据。
@tool
def search_dishes(
    keyword: Optional[str] = None,
    category_id: Optional[int] = None,
    min_price: Optional[float] = None,
    max_price: Optional[float] = None,
    spicy_level: Optional[str] = None,
    disliked_ingredients: Optional[list[str]] = None,
    limit: int = 10
) -> str:
    """
    查询苍穹外卖当前真实在售菜品。

    适用场景：
    - 用户查询有哪些菜。
    - 用户询问有没有某个菜。
    - 用户查询某类菜品。
    - 用户查询某个价格范围内的菜品。
    - 用户要求添加某个菜，但当前上下文还没有该菜真实 dishId。
    - 已经明确查询条件时，执行一次真实菜品查询。

    参数：
    - keyword:
      从用户需求中提取的核心关键词，
      例如“鱼”“酸菜鱼”“王老吉”。

    - category_id:
      已知真实分类 ID 时填写，
      禁止猜测 category_id。

    - min_price:
      用户明确提出最低价格时填写。

    - max_price:
      用户明确提出最高价格时填写。

    - spicy_level:
      用户明确表达或从长期记忆中查询到的辣度偏好，
      例如“中辣”。
      不得自行推断。

    - disliked_ingredients:
      用户明确表达或从长期记忆中查询到的不喜欢食材，
      例如 ["香菜", "葱"]。

      该条件用于查询支持对应
      “不要香菜”“不要葱”
      等忌口选项的菜品。

      这并不代表菜品天然不包含这些食材。

    - limit:
      最大返回菜品数量，
      默认 10，
      最大 20。

    返回：
    - 通常包含 dishId、name、price。
    - 调用本工具只表示进行了菜品查询，
      不代表菜品已经加入购物车。
    """

    try:
        dishes = _search_dishes_once(
            keyword=keyword,
            category_id=category_id,
            min_price=min_price,
            max_price=max_price,
            spicy_level=spicy_level,
            disliked_ingredients=disliked_ingredients,
            limit=limit
        )

        if not dishes:
            return "没有查询到符合当前条件的在售菜品。"

        return json.dumps(
            dishes,
            ensure_ascii=False
        )

    except requests.RequestException as e:
        return (
            f"调用苍穹外卖菜品服务失败："
            f"{str(e)}"
        )

    except RuntimeError as e:
        return str(e)



# 根据菜品 ID 查询菜品详情。
@tool
def get_dish_detail(
    dish_id: int,
    include_image: bool = False
) -> str:
    """
    根据真实 dishId 查询某一道在售菜品的详细信息。

    适用场景：
    - 用户询问菜品介绍、原料、价格、口味、忌口等具体信息；
    - 用户明确询问菜品图片时，将 include_image 设置为 True。

    参数：
    - dish_id:
      必须来自 search_dishes 或历史真实工具结果，禁止猜测。

    - include_image:
      是否返回菜品图片 URL。
      默认 False，避免较长图片 URL 无意义进入短期上下文。
    """

    url = settings.java_url(f"/internal/agent/dishes/{dish_id}")

    try:
        response = requests.get(
            url,
            timeout=settings.java_http_timeout
        )
        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"菜品详情查询失败：{result.get('msg')}"

        dish = result.get("data")

        if not dish:
            return "没有查询到该菜品，可能菜品不存在或已经停售。"


        # ==================================================
        # 只保留 Agent 推理真正需要的详情字段
        # ==================================================

        compact_dish = {
            "id": dish.get("id") or dish_id,
            "name": dish.get("name"),
            "price": dish.get("price"),
            "description": dish.get("description"),
            "flavors": dish.get("flavors") or []
        }


        # 图片属于展示型信息。
        # 只有用户明确询问图片时才返回，
        # 避免长 URL 长期进入 LangGraph messages。
        if include_image:
            compact_dish["image"] = dish.get("image")


        return json.dumps(
            compact_dish,
            ensure_ascii=False,
            separators=(",", ":")
        )

    except requests.RequestException as e:
        return f"调用苍穹外卖菜品详情服务失败：{str(e)}"


# 自动逐级放宽查询
@tool
def search_dishes_with_fallback(
    keyword: Optional[str] = None,
    category_id: Optional[int] = None,
    min_price: Optional[float] = None,
    max_price: Optional[float] = None,
    spicy_level: Optional[str] = None,
    disliked_ingredients: Optional[list[str]] = None,
    limit: int = 10
) -> str:
    """
    根据用户偏好搜索菜品，并在没有完全匹配结果时自动逐级放宽条件。

    主要用于：
    - 根据用户长期口味推荐菜品
    - 根据忌口、辣度进行个性化推荐

    匹配等级：

    FULL_MATCH：
    当前业务条件、辣度偏好、忌口条件均满足。

    TASTE_RELAXED：
    放宽辣度偏好后找到结果，
    但仍保留用户忌口条件。

    BASIC_CANDIDATE：
    辣度和忌口条件都无法满足，
    最终只根据关键词、分类、价格等业务条件找到真实候选。

    NO_RESULT：
    即使逐步放宽个性化条件后，
    当前业务查询条件下仍没有真实在售菜品。
    """

    try:
        # 记录本次搜索实际放宽过的条件
        relaxed_conditions = []

        # -----------------------------
        # 第一层：完全匹配
        # -----------------------------
        dishes = _search_dishes_once(
            keyword=keyword,
            category_id=category_id,
            min_price=min_price,
            max_price=max_price,
            spicy_level=spicy_level,
            disliked_ingredients=disliked_ingredients,
            limit=limit
        )

        if dishes:
            return json.dumps(
                {
                    "matchLevel": "FULL_MATCH",
                    "relaxedConditions": [],
                    "dishes": _compact_dish_candidates(dishes)
                },
                ensure_ascii=False,
                separators=(",", ":")
            )

        # -----------------------------
        # 第二层：放宽辣度
        # 但继续保留忌口
        # -----------------------------
        if spicy_level:
            relaxed_conditions.append("spicy_level")

            dishes = _search_dishes_once(
                keyword=keyword,
                category_id=category_id,
                min_price=min_price,
                max_price=max_price,
                spicy_level=None,
                disliked_ingredients=disliked_ingredients,
                limit=limit
            )

            if dishes:
                return json.dumps(
                    {
                        "matchLevel": "TASTE_RELAXED",
                        "relaxedConditions": relaxed_conditions.copy(),
                        "dishes": _compact_dish_candidates(dishes)
                    },
                    ensure_ascii=False,
                    separators=(",", ":")
                )

        # -----------------------------
        # 第三层：放宽忌口
        # 只保留当前业务查询条件
        # -----------------------------
        if disliked_ingredients:
            relaxed_conditions.append("disliked_ingredients")

        dishes = _search_dishes_once(
            keyword=keyword,
            category_id=category_id,
            min_price=min_price,
            max_price=max_price,
            spicy_level=None,
            disliked_ingredients=None,
            limit=limit
        )

        if dishes:
            return json.dumps(
                {
                    "matchLevel": "BASIC_CANDIDATE",
                    "relaxedConditions": relaxed_conditions.copy(),
                    "dishes": _compact_dish_candidates(dishes)
                },
                ensure_ascii=False,
                separators=(",", ":")
            )

        # -----------------------------
        # 最终仍然没有结果
        # -----------------------------
        return json.dumps(
        {
            "matchLevel": "NO_RESULT",
            "relaxedConditions": relaxed_conditions,
            "dishes": []
        },
        ensure_ascii=False,
        separators=(",", ":")
    )
    except requests.RequestException as e:
        return (
            f"调用苍穹外卖菜品服务失败："
            f"{str(e)}"
        )

    except RuntimeError as e:
        return str(e)



def _compact_dish_candidates(dishes: list) -> list:
    """
    精简菜品搜索结果。

    搜索阶段只保留推荐、指代和后续查询详情所需的信息，
    避免图片、描述等展示字段进入 Agent 短期上下文。
    """

    return [
        {
            "dishId": dish.get("dishId") or dish.get("id"),
            "name": dish.get("name"),
            "price": dish.get("price"),
        }
        for dish in dishes
    ]

# 一次调用API查询,将search_dish和search_dish_with_fallback的查询逻辑封装在一起,避免重复代码
def _search_dishes_once(
    keyword: Optional[str] = None,
    category_id: Optional[int] = None,
    min_price: Optional[float] = None,
    max_price: Optional[float] = None,
    spicy_level: Optional[str] = None,
    disliked_ingredients: Optional[list[str]] = None,
    limit: int = 10
) -> list[dict]:

    limit = max(1, min(limit, 20))

    payload = {
        "keyword": keyword,
        "categoryId": category_id,
        "minPrice": min_price,
        "maxPrice": max_price,
        "spicyLevel": spicy_level,
        "dislikedIngredients": disliked_ingredients,
        "limit": limit
    }

    url = settings.java_url("/internal/agent/dishes/search")

    response = requests.post(
        url,
        json=payload,
        timeout=settings.java_http_timeout
    )

    response.raise_for_status()

    result = response.json()

    if result.get("code") != 1:
        raise RuntimeError(
            f"菜品查询失败：{result.get('msg')}"
        )

    return result.get("data") or []
