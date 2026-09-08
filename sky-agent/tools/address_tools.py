import json
import requests
from langchain.tools import tool, ToolRuntime
from agent.context import UserAgentContext
from typing import Optional
from config.settings import settings


# 获取默认地址
@tool
def get_default_address(
    runtime: ToolRuntime[UserAgentContext]
) -> str:
    """
    查询当前登录用户的默认收货地址。

    本工具用于获取真实默认地址。

    使用场景：

    - 我的默认地址是什么
    - 送到默认地址
    - 帮我看看默认收货地址
    - 下单前获取默认地址

    user_id 不由模型生成，
    必须从 Runtime Context 获取。

    如果当前用户没有默认地址，
    应明确告诉用户尚未设置默认地址。

    禁止根据历史消息猜测地址。
    """

    user_id = runtime.context.user_id

    url = settings.java_url(
        f"/internal/agent/address-book/{user_id}/default"
    )

    try:
        response = requests.get(
            url,
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"默认地址查询失败：{result.get('msg')}"

        address = result.get("data")

        if not address:
            return "当前用户还没有设置默认收货地址。"

        return json.dumps(
            address,
            ensure_ascii=False
        )

    except requests.RequestException as e:
        return f"调用地址服务失败：{str(e)}"
    
    
# 获取所有地址
@tool
def get_my_addresses(
    runtime: ToolRuntime[UserAgentContext]
) -> str:
    """
    查询当前登录用户保存的全部收货地址。

    使用场景：

    - 我的地址有哪些
    - 查看我的全部收货地址
    - 我保存了几个地址
    - 把我的地址列出来
    - 选择一个收货地址

    user_id 不由模型生成，
    必须从 Runtime Context 获取。

    返回数据中的 id 字段是地址记录真实ID。

    后续如果用户要求：
    - 修改默认地址
    - 修改地址
    - 删除地址

    应优先使用本工具返回的真实 address id，
    禁止模型猜测地址ID。
    """

    user_id = runtime.context.user_id

    url = settings.java_url(
        f"/internal/agent/address-book/{user_id}"
    )

    try:

        response = requests.get(
            url,
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"地址查询失败：{result.get('msg')}"

        addresses = result.get("data") or []

        if not addresses:
            return "当前用户还没有保存任何收货地址。"

        return json.dumps(
            addresses,
            ensure_ascii=False
        )

    except requests.RequestException as e:

        return f"调用地址服务失败：{str(e)}"
    

# 设置默认地址
@tool
def set_default_address(
    runtime: ToolRuntime[UserAgentContext],
    address_id: int
) -> str:
    """
    将当前用户保存的某个收货地址设置为默认地址。

    使用本工具前，必须先通过 get_my_addresses
    查询当前用户真实地址列表。

    参数：

    address_id：
    - 必须来自 get_my_addresses 返回结果中的 id 字段
    - 不允许模型猜测
    - 必须是当前用户自己的地址

    例如：

    用户：
    “把西工大那个地址设成默认地址”

    应先调用：

    get_my_addresses()

    如果返回：

    {
        "id": 3,
        "consignee": "dodo",
        "detail": "西北工业大学长安校区",
        "isDefault": 0
    }

    则调用：

    set_default_address(
        address_id=3
    )

    只有本工具明确返回成功后，
    才可以告诉用户默认地址已经修改。
    """

    # 1. 从 Runtime Context 获取可信 userId
    user_id = runtime.context.user_id

    # 2. 基本参数校验
    if address_id <= 0:
        return "地址ID不合法。"

    # 3. 构造 Java internal 接口
    url = settings.java_url(
        f"/internal/agent/address-book/{user_id}/default/{address_id}"
    )

    try:

        # 4. 调用 Java 设置默认地址接口
        response = requests.put(
            url,
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        # 5. 获取 Java 返回结果
        result = response.json()

        if result.get("code") != 1:
            return f"设置默认地址失败：{result.get('msg')}"

        return "默认收货地址已成功修改。"

    except requests.RequestException as e:

        return f"调用地址服务失败：{str(e)}"
    
    

# 新增收货地址
@tool
def add_address(
    runtime: ToolRuntime[UserAgentContext],
    consignee: str,
    phone: str,
    province_name: str,
    city_name: str,
    district_name: str,
    detail: str,
    sex: Optional[str] = None,
    label: Optional[str] = None
) -> str:
    """
    为当前登录用户新增收货地址。

    只有当用户明确要求新增、保存收货地址时调用。

    参数必须来自用户明确提供的信息。

    consignee：
    收货人姓名。

    phone：
    收货人手机号。

    province_name：
    省份名称，例如“四川省”。

    city_name：
    城市名称，例如“成都市”。

    district_name：
    区县名称，例如“武侯区”。

    detail：
    详细地址，例如“西北工业大学长安校区”。

    sex：
    用户明确提供时填写。
    用户未提供时不要猜测。

    label：
    地址标签，例如“家”“公司”“学校”。
    用户未提供时可以为空。

    重要规则：

    - user_id 必须从 Runtime Context 获取
    - 不允许模型生成 user_id
    - 不允许编造用户没有提供的地址信息
    - 缺少必要信息时，应先询问用户
    - 只有工具明确返回成功后，才能告诉用户地址新增成功
    """

    user_id = runtime.context.user_id

    payload = {
        "consignee": consignee,
        "phone": phone,
        "sex": sex,
        "provinceName": province_name,
        "cityName": city_name,
        "districtName": district_name,
        "detail": detail,
        "label": label
    }

    url = settings.java_url(
        f"/internal/agent/address-book/{user_id}"
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
            return f"新增收货地址失败：{result.get('msg')}"

        return "收货地址已成功新增。"

    except requests.RequestException as e:

        return f"调用地址新增服务失败：{str(e)}"
    
    
# 修改收货地址
@tool
def update_address(
    runtime: ToolRuntime[UserAgentContext],
    address_id: int,
    consignee: str,
    phone: str,
    province_name: str,
    city_name: str,
    district_name: str,
    detail: str,
    sex: Optional[str] = None,
    label: Optional[str] = None
) -> str:
    """
    修改当前登录用户的一条收货地址。

    调用本工具前，必须先调用 get_my_addresses 获取真实的 address_id。
    不允许猜测或自行生成 address_id。

    如果用户只修改地址中的部分信息，应保留 get_my_addresses 返回的其他原有字段，
    并将完整地址信息传入本工具。

    user_id 由 Runtime Context 提供，不能由模型或用户指定。
    """

    user_id = runtime.context.user_id

    payload = {
        "consignee": consignee,
        "phone": phone,
        "sex": sex,
        "provinceName": province_name,
        "cityName": city_name,
        "districtName": district_name,
        "detail": detail,
        "label": label
    }

    url = settings.java_url(
        f"/internal/agent/address-book/{user_id}/{address_id}"
    )

    try:
        response = requests.put(
            url,
            json=payload,
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"修改收货地址失败：{result.get('msg')}"

        return "收货地址修改成功。"

    except requests.RequestException as e:
        return f"调用地址修改服务失败：{str(e)}"
    
    

# 删除收货地址
@tool
def delete_address(
    runtime: ToolRuntime[UserAgentContext],
    address_id: int
) -> str:
    """
    删除当前登录用户的一条收货地址。

    address_id 必须来自 get_my_addresses 的真实查询结果，
    不允许猜测或自行生成。
    """

    user_id = runtime.context.user_id

    url = settings.java_url(
        f"/internal/agent/address-book/{user_id}/{address_id}"
    )

    try:
        response = requests.delete(
            url,
            timeout=settings.java_http_timeout
        )

        response.raise_for_status()

        result = response.json()

        if result.get("code") != 1:
            return f"删除收货地址失败：{result.get('msg')}"

        return "收货地址删除成功。"

    except requests.RequestException as e:
        return f"调用地址删除服务失败：{str(e)}"
