from __future__ import annotations

import re
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


STRICT = ConfigDict(extra="forbid", strict=True)
ID_PATTERN = re.compile(r"^[a-z][a-z0-9-]{0,31}$")
COLOR_PATTERN = re.compile(r"^#[0-9A-Fa-f]{6}$")
CONTROL_PATTERN = re.compile(r"[\x00-\x1f\x7f]")
TELEMETRY_EVENTS = (
    "SESSION_STARTED",
    "ITEM_COLLECTED",
    "PLAYER_HIT",
    "GAME_WON",
    "GAME_LOST",
    "SESSION_RESTARTED",
    "SESSION_ENDED",
)

Identifier = Annotated[str, Field(pattern=r"^[a-z][a-z0-9-]{0,31}$")]
Coordinate = Annotated[int | float, Field(ge=0)]
Color = Annotated[str, Field(pattern=r"^#[0-9A-Fa-f]{6}$")]


def _safe_text(value: str, limit: int) -> str:
    trimmed = value.strip()
    if not trimmed or len(trimmed) > limit or CONTROL_PATTERN.search(trimmed) or "<" in trimmed or ">" in trimmed:
        raise ValueError(f"must be safe text with 1-{limit} code points")
    return trimmed


class Point(BaseModel):
    model_config = STRICT
    x: Coordinate
    y: Coordinate


class Metadata(BaseModel):
    model_config = STRICT
    schemaVersion: Literal["2.0"]
    gameType: Literal["arcade_collect"]
    title: str
    seed: Annotated[int, Field(ge=0, le=2_147_483_647)]

    @field_validator("title")
    @classmethod
    def title_is_safe(cls, value: str) -> str:
        return _safe_text(value, 80)


class Viewport(BaseModel):
    model_config = STRICT
    width: Annotated[int, Field(ge=640, le=1280)]
    height: Annotated[int, Field(ge=360, le=720)]
    scaleMode: Literal["fit"]

    @model_validator(mode="after")
    def uses_landscape_ratio(self) -> "Viewport":
        if abs(self.width / self.height - 16 / 9) / (16 / 9) > 0.01:
            raise ValueError("viewport must use a 16:9 ratio within 1%")
        return self


OBSTACLE_KEYS = {"obstacle.stone", "obstacle.metal", "obstacle.wood"}
PLAYER_KEYS = {"player.blue", "player.green"}
COLLECTIBLE_KEYS = {"collectible.gem", "collectible.artifact", "collectible.core"}
ENEMY_KEYS = {"enemy.guard", "enemy.drone"}
EXIT_KEYS = {"exit.portal", "exit.door"}
SOUND_KEYS = {"sfx.collect", "sfx.hit", "sfx.win", "sfx.lose", "sfx.silent"}


class Obstacle(BaseModel):
    model_config = STRICT
    id: Identifier
    x: Coordinate
    y: Coordinate
    width: Annotated[int, Field(ge=24, le=320)]
    height: Annotated[int, Field(ge=24, le=320)]
    spriteKey: str

    @field_validator("spriteKey")
    @classmethod
    def resource_is_allowed(cls, value: str) -> str:
        if value not in OBSTACLE_KEYS:
            raise ValueError("obstacle resource key is not allowed")
        return value


class World(BaseModel):
    model_config = STRICT
    width: Annotated[int, Field(ge=640, le=1280)]
    height: Annotated[int, Field(ge=360, le=720)]
    spawn: Point
    obstacles: Annotated[list[Obstacle], Field(max_length=16)]


class Player(BaseModel):
    model_config = STRICT
    speed: Annotated[int, Field(ge=80, le=400)]
    size: Annotated[int, Field(ge=24, le=64)]
    maxHealth: Annotated[int, Field(ge=1, le=5)]
    hitInvulnerabilityMs: Annotated[int, Field(ge=0, le=3000)]
    spriteKey: str

    @field_validator("spriteKey")
    @classmethod
    def resource_is_allowed(cls, value: str) -> str:
        if value not in PLAYER_KEYS:
            raise ValueError("player resource key is not allowed")
        return value


class Collectible(BaseModel):
    model_config = STRICT
    id: Identifier
    x: Coordinate
    y: Coordinate
    size: Annotated[int, Field(ge=12, le=48)]
    score: Annotated[int, Field(ge=1, le=1000)]
    label: str
    spriteKey: str

    @field_validator("label")
    @classmethod
    def label_is_safe(cls, value: str) -> str:
        return _safe_text(value, 80)

    @field_validator("spriteKey")
    @classmethod
    def resource_is_allowed(cls, value: str) -> str:
        if value not in COLLECTIBLE_KEYS:
            raise ValueError("collectible resource key is not allowed")
        return value


class Enemy(BaseModel):
    model_config = STRICT
    id: Identifier
    x: Coordinate
    y: Coordinate
    size: Annotated[int, Field(ge=24, le=64)]
    speed: Annotated[int, Field(ge=20, le=240)]
    spriteKey: str

    @field_validator("spriteKey")
    @classmethod
    def resource_is_allowed(cls, value: str) -> str:
        if value not in ENEMY_KEYS:
            raise ValueError("enemy resource key is not allowed")
        return value


class Exit(BaseModel):
    model_config = STRICT
    x: Coordinate
    y: Coordinate
    width: Annotated[int, Field(ge=32, le=160)]
    height: Annotated[int, Field(ge=32, le=160)]
    label: str
    spriteKey: str

    @field_validator("label")
    @classmethod
    def label_is_safe(cls, value: str) -> str:
        return _safe_text(value, 80)

    @field_validator("spriteKey")
    @classmethod
    def resource_is_allowed(cls, value: str) -> str:
        if value not in EXIT_KEYS:
            raise ValueError("exit resource key is not allowed")
        return value


class Entities(BaseModel):
    model_config = STRICT
    collectibles: Annotated[list[Collectible], Field(min_length=1, max_length=20)]
    enemies: Annotated[list[Enemy], Field(max_length=12)]
    exit: Exit


class EnemyPatrol(BaseModel):
    model_config = STRICT
    enemyId: Identifier
    axis: Literal["x", "y"]
    distance: Annotated[int, Field(ge=32, le=480)]


class Contact(BaseModel):
    model_config = STRICT
    damage: Annotated[int, Field(ge=1, le=5)]


class Behaviors(BaseModel):
    model_config = STRICT
    enemyPatrols: list[EnemyPatrol]
    contact: Contact


class Objectives(BaseModel):
    model_config = STRICT
    targetCollectibles: Annotated[int, Field(ge=1, le=20)]
    winCondition: Literal["collect_target_then_exit"]
    loseConditions: list[Literal["health_depleted", "time_expired"]]

    @field_validator("loseConditions")
    @classmethod
    def conditions_are_unique_and_nonempty(cls, value: list[str]) -> list[str]:
        if not value or len(value) != len(set(value)):
            raise ValueError("loseConditions must be non-empty and unique")
        order = {"health_depleted": 0, "time_expired": 1}
        return sorted(value, key=order.__getitem__)


class Balance(BaseModel):
    model_config = STRICT
    timeLimitSeconds: Annotated[int, Field(ge=30, le=600)]
    winBonus: Annotated[int, Field(ge=0, le=10_000)]
    difficulty: Literal["easy", "normal", "hard"]


class Palette(BaseModel):
    model_config = STRICT
    floor: Color
    wall: Color
    player: Color
    item: Color
    enemy: Color
    exit: Color

    @field_validator("floor", "wall", "player", "item", "enemy", "exit")
    @classmethod
    def normalize_color(cls, value: str) -> str:
        return value.upper()


class Audio(BaseModel):
    model_config = STRICT
    collect: str
    hit: str
    win: str
    lose: str

    @field_validator("collect", "hit", "win", "lose")
    @classmethod
    def resource_is_allowed(cls, value: str) -> str:
        if value not in SOUND_KEYS:
            raise ValueError("sound resource key is not allowed")
        return value


class Ui(BaseModel):
    model_config = STRICT
    objective: str
    controls: str

    @field_validator("objective", "controls")
    @classmethod
    def text_is_safe(cls, value: str) -> str:
        return _safe_text(value, 160)


class Presentation(BaseModel):
    model_config = STRICT
    palette: Palette
    audio: Audio
    ui: Ui


class Telemetry(BaseModel):
    model_config = STRICT
    events: list[str]

    @field_validator("events")
    @classmethod
    def exact_allow_list(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)) or set(value) != set(TELEMETRY_EVENTS):
            raise ValueError("telemetry.events must contain the seven allowed events exactly once")
        return list(TELEMETRY_EVENTS)


class GameConfigV2(BaseModel):
    model_config = STRICT
    metadata: Metadata
    viewport: Viewport
    world: World
    player: Player
    entities: Entities
    behaviors: Behaviors
    objectives: Objectives
    balance: Balance
    presentation: Presentation
    telemetry: Telemetry

    @model_validator(mode="after")
    def cross_field_rules(self) -> "GameConfigV2":
        if (self.world.width, self.world.height) != (self.viewport.width, self.viewport.height):
            raise ValueError("world dimensions must equal viewport dimensions")
        if self.objectives.targetCollectibles > len(self.entities.collectibles):
            raise ValueError("targetCollectibles exceeds collectible count")
        self._unique_ids(self.world.obstacles, "obstacle")
        self._unique_ids(self.entities.collectibles, "collectible")
        self._unique_ids(self.entities.enemies, "enemy")
        enemy_ids = {enemy.id for enemy in self.entities.enemies}
        patrol_ids = [patrol.enemyId for patrol in self.behaviors.enemyPatrols]
        if len(patrol_ids) != len(set(patrol_ids)) or set(patrol_ids) != enemy_ids:
            raise ValueError("every enemy must have exactly one patrol")

        width, height = self.world.width, self.world.height
        self._circle_in_world(self.world.spawn.x, self.world.spawn.y, self.player.size / 2, width, height, "spawn")
        for obstacle in self.world.obstacles:
            self._rect_in_world(obstacle.x, obstacle.y, obstacle.width, obstacle.height, width, height, obstacle.id)
        for item in self.entities.collectibles:
            self._circle_in_world(item.x, item.y, item.size / 2, width, height, item.id)
        enemy_by_id = {enemy.id: enemy for enemy in self.entities.enemies}
        for enemy in self.entities.enemies:
            self._circle_in_world(enemy.x, enemy.y, enemy.size / 2, width, height, enemy.id)
        self._rect_in_world(
            self.entities.exit.x,
            self.entities.exit.y,
            self.entities.exit.width,
            self.entities.exit.height,
            width,
            height,
            "exit",
        )
        for patrol in self.behaviors.enemyPatrols:
            enemy = enemy_by_id[patrol.enemyId]
            radius = enemy.size / 2
            low = (enemy.x if patrol.axis == "x" else enemy.y) - patrol.distance - radius
            high = (enemy.x if patrol.axis == "x" else enemy.y) + patrol.distance + radius
            limit = width if patrol.axis == "x" else height
            if low < 0 or high > limit:
                raise ValueError(f"patrol for {enemy.id} leaves world bounds")
        for obstacle in self.world.obstacles:
            if self._circle_rect_overlap(self.world.spawn.x, self.world.spawn.y, self.player.size / 2, obstacle):
                raise ValueError(f"spawn overlaps obstacle {obstacle.id}")
            for item in self.entities.collectibles:
                if self._circle_rect_overlap(item.x, item.y, item.size / 2, obstacle):
                    raise ValueError(f"collectible {item.id} overlaps obstacle {obstacle.id}")
            for enemy in self.entities.enemies:
                if self._circle_rect_overlap(enemy.x, enemy.y, enemy.size / 2, obstacle):
                    raise ValueError(f"enemy {enemy.id} overlaps obstacle {obstacle.id}")
            if self._rect_overlap(obstacle.x, obstacle.y, obstacle.width, obstacle.height,
                                  self.entities.exit.x, self.entities.exit.y,
                                  self.entities.exit.width, self.entities.exit.height):
                raise ValueError(f"exit overlaps obstacle {obstacle.id}")
        return self

    @staticmethod
    def _unique_ids(entries: list, label: str) -> None:
        ids = [entry.id for entry in entries]
        if len(ids) != len(set(ids)):
            raise ValueError(f"duplicate {label} id")

    @staticmethod
    def _circle_in_world(x: float, y: float, radius: float, width: int, height: int, label: str) -> None:
        if x - radius < 0 or x + radius > width or y - radius < 0 or y + radius > height:
            raise ValueError(f"{label} leaves world bounds")

    @staticmethod
    def _rect_in_world(x: float, y: float, rect_width: float, rect_height: float,
                       width: int, height: int, label: str) -> None:
        if x - rect_width / 2 < 0 or x + rect_width / 2 > width or y - rect_height / 2 < 0 or y + rect_height / 2 > height:
            raise ValueError(f"{label} leaves world bounds")

    @staticmethod
    def _circle_rect_overlap(x: float, y: float, radius: float, rect: Obstacle) -> bool:
        closest_x = max(rect.x - rect.width / 2, min(x, rect.x + rect.width / 2))
        closest_y = max(rect.y - rect.height / 2, min(y, rect.y + rect.height / 2))
        return (x - closest_x) ** 2 + (y - closest_y) ** 2 < radius ** 2

    @staticmethod
    def _rect_overlap(ax: float, ay: float, aw: float, ah: float,
                      bx: float, by: float, bw: float, bh: float) -> bool:
        return abs(ax - bx) * 2 < aw + bw and abs(ay - by) * 2 < ah + bh


def validate_game_config_v2(value: object) -> dict:
    return GameConfigV2.model_validate(value).model_dump(mode="json")
