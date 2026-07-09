from typing import Any

from pydantic import BaseModel, Field


class ApiResponse(BaseModel):
    code: int = Field(default=0, description="业务状态码，0 表示成功")
    message: str = Field(default="success", description="响应提示信息")
    data: Any | None = Field(default=None, description="响应数据")
    trace_id: str | None = Field(default=None, description="链路追踪 ID")
