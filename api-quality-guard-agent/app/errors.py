"""Agent 可预期失败（模型调用失败、输出校验失败、轮数耗尽等）。"""


class AgentError(Exception):
    def __init__(self, message: str):
        super().__init__(message)
