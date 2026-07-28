from collections import Counter
from typing import Any


def summarize_episode_results(results: list[dict[str, Any]], optimal_steps: int) -> dict[str, Any]:
    """Build deterministic aggregate metrics without mixing machine and human samples."""
    total = len(results)
    completed = sum(result.get("outcome") == "WON" for result in results)
    action_count = sum(result.get("acceptedActionCount", 0) for result in results)
    invalid_count = sum(result.get("invalidActionCount", 0) for result in results)
    durations = [result["timing"]["wallDurationMs"] for result in results if result.get("timing", {}).get("wallDurationMs") is not None]
    failures = Counter((result.get("error") or {}).get("code") or result.get("terminationReason") or "NONE" for result in results if result.get("outcome") != "WON")
    return {
        "sampleSource": "MACHINE",
        "sampleSize": total,
        "completionRatePermille": completed * 1000 // total if total else 0,
        "meanWallDurationMs": sum(durations) // len(durations) if durations else None,
        "invalidActionRatePermille": invalid_count * 1000 // (action_count + invalid_count) if action_count + invalid_count else 0,
        "pathEfficiencyPermille": min(1000, completed * optimal_steps * 1000 // action_count) if action_count else 0,
        "failureReasons": dict(sorted(failures.items())),
    }
