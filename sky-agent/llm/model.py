from langchain_ollama import ChatOllama
from langchain_openai import ChatOpenAI

from config.settings import settings

# 创建苍穹外卖 Agent 使用的大语言模型
# 本地ollama
# llm = ChatOllama(
#     model="qwen2.5:latest",
#     base_url="http://127.0.0.1:11434",
#     temperature=0.2
# )


llm = ChatOpenAI(
    model=settings.deepseek_model,
    api_key=settings.deepseek_api_key,
    base_url=settings.deepseek_base_url,
    temperature=settings.deepseek_temperature,
    timeout=settings.deepseek_timeout,
    max_retries=settings.deepseek_max_retries
)
