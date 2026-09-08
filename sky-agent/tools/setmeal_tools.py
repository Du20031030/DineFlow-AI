import json
from typing import Optional
import requests
from langchain.tools import tool
from config.settings import settings


# agent查询套餐
@tool
def search_setmeals(
    keyword: Optional[str] = None,
    category_id: Optional[int] = None,
    min_price: Optional[float] = None,
    max_price: Optional[float] = None
) -> str:
    """
    查询苍穹外卖当前真实在售套餐。

    本工具返回真实套餐信息，包括：

    - setmealId：套餐真实ID
    - name：套餐名称
    - price：套餐价格
    - dishes：套餐中包含的真实菜品列表

    dishes 中每个菜品包含：

    - dishId：菜品真实ID
    - name：菜品名称
    - price：菜品价格
    - copies：套餐中该菜品的份数


    ====================
    使用场景
    ====================

    当用户询问：

    - 有什么套餐
    - 有哪些套餐
    - 有没有商务套餐
    - 有没有儿童套餐
    - 某个套餐里面有什么
    - 哪个套餐包含某种菜品

    可以调用本工具。


    当用户要求：

    “把某个套餐加入购物车”

    但是当前还没有真实 setmealId 时，

    必须先调用本工具查询真实套餐信息。


    ====================
    参数规则
    ====================

    keyword：

    提取用户关于套餐的核心关键词。

    例如：

    “有什么商务套餐”

    keyword="商务"


    “有没有儿童套餐”

    keyword="儿童"


    category_id：

    只有用户明确提供，
    或者已经从真实业务数据中获得时填写。

    禁止猜测。


    min_price：

    只有用户明确提出最低价格时填写。


    max_price：

    只有用户明确提出最高价格时填写。


    ====================
    返回数据规则
    ====================

    例如工具可能返回：

    [
        {
            "setmealId": 3,
            "name": "商务套餐A",
            "price": 88,
            "dishes": [
                {
                    "dishId": 61,
                    "name": "剁椒鱼头",
                    "price": 66,
                    "copies": 1
                },
                {
                    "dishId": 56,
                    "name": "清炒西兰花",
                    "price": 18,
                    "copies": 1
                }
            ]
        }
    ]


    如果用户询问套餐内容：

    必须根据 dishes 中的真实数据回答。

    禁止自行增加套餐中不存在的菜品。

    禁止根据常识推测套餐内容。


    ====================
    数据可信规则
    ====================

    setmealId：

    只能来自本工具真实返回结果。

    禁止模型根据套餐名称猜测 setmealId。


    dishes：

    表示该套餐真实关联的菜品。

    不允许模型自行修改：

    - 菜品名称
    - 菜品数量
    - 菜品价格


    本工具只负责查询套餐。

    不会执行：

    - 加入购物车
    - 修改购物车
    - 下单

    如果用户原始请求是将套餐加入购物车，
    查询成功后应继续调用对应套餐添加工具。
    """

    payload = {
        "keyword": keyword,
        "categoryId": category_id,
        "minPrice": min_price,
        "maxPrice": max_price
    }

    url = settings.java_url("/internal/agent/setmeals/search")

    try:

        response = requests.post(
            url,
            json=payload,
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"套餐查询失败：{result.get('msg')}"

        setmeals = result.get("data") or []

        if not setmeals:
            return "没有查询到符合当前条件的在售套餐。"

        return json.dumps(
            setmeals,
            ensure_ascii=False
        )

    except requests.RequestException as e:

        return f"调用苍穹外卖套餐查询服务失败：{str(e)}"
    

