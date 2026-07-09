from typing import Any

from pydantic import AliasChoices, BaseModel, ConfigDict, Field


class AgentMockRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    project_uuid: str | None = Field(
        default=None,
        validation_alias=AliasChoices("project_uuid", "projectUuid"),
        description="Project unique identifier",
    )
    title: str = Field(..., min_length=1, max_length=200, description="Task title")
    content: str = Field(..., min_length=1, max_length=5000, description="Task content")
    context: str | None = Field(default=None, max_length=50000, description="Extra context")
    user_id: int | None = Field(
        default=None,
        validation_alias=AliasChoices("user_id", "userId"),
        description="Caller user id",
    )
    system_prompt: str | None = Field(
        default=None,
        validation_alias=AliasChoices("system_prompt", "systemPrompt"),
        description="System prompt from Java",
    )
    user_prompt_template: str | None = Field(
        default=None,
        validation_alias=AliasChoices("user_prompt_template", "userPromptTemplate"),
        description="User prompt template from Java",
    )
    template_uuid: str | None = Field(
        default=None,
        validation_alias=AliasChoices("template_uuid", "templateUuid"),
        description="Prompt template UUID",
    )
    template_version: int | None = Field(
        default=None,
        validation_alias=AliasChoices("template_version", "templateVersion"),
        description="Prompt template version",
    )


class AgentMockResult(BaseModel):
    agent_type: str = Field(..., description="Agent type")
    title: str = Field(..., description="Task title")
    summary: str = Field(..., description="Result summary")
    content: str | None = Field(default=None, description="Generated content")
    key_points: list[str] = Field(default_factory=list, description="Key points")
    suggestions: list[str] = Field(default_factory=list, description="Suggestions")
    game_config: dict[str, Any] | None = Field(default=None, description="Playable game configuration")
    prompt: dict[str, Any] | str | None = Field(default=None, description="Prompt used for generation")
    template: dict[str, Any] | None = Field(default=None, description="Prompt template metadata")
    raw_result: dict[str, Any] = Field(default_factory=dict, description="Raw result")
    model: str | None = Field(default=None, description="Model name")
    time_taken_ms: int | None = Field(default=None, description="Model call duration in milliseconds")
