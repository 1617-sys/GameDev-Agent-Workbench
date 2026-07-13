from typing import Any

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, model_validator

class RetrievedChunk(BaseModel):
    chunk_uuid: str
    document_uuid: str
    rank: int = Field(ge=1)
    score: float
    text: str = Field(min_length=1, max_length=12000)
    document_version: str | None = None

class RagContext(BaseModel):
    rag_enabled: bool = False
    retrieved_chunks: list[RetrievedChunk] = Field(default_factory=list)
    retrieval_version: str | None = None
    budget_chars: int = Field(default=8000, ge=1, le=50000)
    @model_validator(mode="after")
    def disabled_has_no_chunks(self):
        if not self.rag_enabled and self.retrieved_chunks: raise ValueError("retrieved_chunks must be empty when rag_enabled is false")
        return self


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
    rag: RagContext | None = None
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
