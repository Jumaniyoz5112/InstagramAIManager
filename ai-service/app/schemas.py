from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    mediaId: str | None = None
    mediaType: str = "IMAGE"
    caption: str | None = None
    imageUrls: list[str] = Field(default_factory=list)


class InsightsPostInput(BaseModel):
    mediaId: str | None = None
    mediaType: str | None = None
    caption: str | None = None
    likeCount: int | None = None
    commentsCount: int | None = None
    sharesCount: int | None = None
    savesCount: int | None = None
    engagementScore: float | None = None
    bucket: str = "mid"  # top | mid | low
    analysis: dict[str, Any] | None = None


class InsightsRequest(BaseModel):
    accountId: str | None = None
    stats: dict[str, Any] = Field(default_factory=dict)
    posts: list[InsightsPostInput] = Field(default_factory=list)


class ScenarioRequest(BaseModel):
    brief: str | None = None
    insights: dict[str, Any]
    preferredFormat: str | None = None
