# GameConfig Contract

GameConfig is the data boundary between AI output and the Phaser runtime. The model may produce text, but only a validated GameConfig object can be normalized and passed to the game runtime.

Current supported game type:

```text
top_down_collect
```

Flow:

```text
raw AI output
-> extract GameConfig
-> validate required runtime fields
-> normalize optional fields and aliases
-> mount Phaser runtime
```

Validation must happen before normalization. Defaults may fill optional visual fields, but they must not hide a missing required structure from raw AI output.

## Required Fields

| Field | Type | Notes |
| --- | --- | --- |
| `version` | string | Current protocol version is `1.0`. |
| `title` | string | Display title. |
| `gameType` | string | Must be `top_down_collect`. |
| `world` | object | Must include numeric `width` and `height`. |
| `player` | object | Must include numeric `x` and `y`. |
| `obstacles` | array | Optional route-building rectangles; old configs receive runtime defaults. |
| `items` | array | Collectible objects consumed by the runtime. |
| `enemies` | array | Enemy objects consumed by the runtime. |
| `exit` | object | Must include numeric `x` and `y`. |
| `rules` | object | Runtime rule settings, including target item count. |
| `ui` | object | HUD text such as objective and controls. |

## Supported Aliases

Only aliases covered by tests should be retained:

| Alias | Normalized Field |
| --- | --- |
| `game_type` | `gameType` |
| `collectibles` | `items` |
| `game_config` wrapper | extracted GameConfig |
| `gameConfig` wrapper | extracted GameConfig |
| `data` wrapper | extracted GameConfig |
| `raw_result.game_config` wrapper | extracted GameConfig |
| `rawResult.gameConfig` wrapper | extracted GameConfig |

`winCondition` is not part of the current runtime contract. Use `rules` instead.

## Example

```json
{
  "version": "1.0",
  "title": "Pixel Dungeon Demo",
  "gameType": "top_down_collect",
  "world": {
    "width": 960,
    "height": 540,
    "backgroundColor": "#101827"
  },
  "theme": {
    "palette": {
      "floor": "#14213d",
      "wall": "#24324a",
      "player": "#5eead4",
      "item": "#facc15",
      "enemy": "#fb7185",
      "exit": "#22c55e"
    }
  },
  "player": {
    "x": 120,
    "y": 260,
    "size": 28,
    "speed": 210,
    "color": "#5eead4"
  },
  "obstacles": [
    { "id": "wall-1", "x": 350, "y": 120, "width": 150, "height": 24 },
    { "id": "wall-2", "x": 620, "y": 270, "width": 24, "height": 150 }
  ],
  "items": [
    { "id": "gem-1", "x": 260, "y": 150, "size": 18, "label": "Gem" },
    { "id": "gem-2", "x": 520, "y": 340, "size": 18, "label": "Gem" },
    { "id": "gem-3", "x": 740, "y": 180, "size": 18, "label": "Gem" }
  ],
  "enemies": [
    { "id": "enemy-1", "x": 420, "y": 220, "size": 28, "speed": 90, "range": 150, "axis": "x" },
    { "id": "enemy-2", "x": 700, "y": 380, "size": 28, "speed": 120, "range": 180, "axis": "y" }
  ],
  "exit": {
    "x": 860,
    "y": 270,
    "width": 54,
    "height": 72,
    "label": "EXIT"
  },
  "rules": {
    "targetItems": 3,
    "winCondition": "collect_all_then_exit",
    "loseCondition": "touch_enemy"
  },
  "ui": {
    "objective": "Collect every gem, then reach the exit.",
    "controls": "Move with WASD or arrow keys. Press R to restart."
  }
}
```

## Rejection Rules

The validator must reject:

- Invalid JSON.
- Missing required structures such as `world`, `player`, `items`, `enemies`, `exit`, `rules`, or `ui`.
- Unsupported `gameType`.
- Non-numeric `world.width`, `world.height`, `player.x`, `player.y`, `exit.x`, or `exit.y`.
- Non-array `items` or `enemies`.
- Empty or unrelated objects that only become playable because defaults are applied.

## Verification

```powershell
cd frontend-vue
npm run test:game-config
npm run build
```
