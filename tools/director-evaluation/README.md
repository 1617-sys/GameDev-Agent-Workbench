# Director fixed comparative evaluation

Runs both frozen arms over the same versioned goals, candidate generator and experiment budget:

```powershell
powershell -ExecutionPolicy Bypass -File tools/director-evaluation/Invoke-DirectorEvaluation.ps1
```

The checked-in fixture is deterministic mock evidence for evaluator reproducibility, not real-model evidence. Add a separately labelled provider/model/version arm instead of replacing the mock samples when performing a real-model run.
