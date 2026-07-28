# Player evaluation harness

Run the frozen matrix against the authenticated Java API:

```powershell
$env:PLAYER_EVAL_TOKEN='<JWT>'
python tools/player-evaluation/run-player-evaluation.py --project <projectUuid> --version <prototypeVersionUuid>
```

The command writes `output/raw.json`, `output/summary.json`, and `output/report.md`. Raw results retain the PlayerRun and persisted Machine Episode identifiers. Mock episodes remain visible through `mockCount` and must not be interpreted as real-model measurements.
