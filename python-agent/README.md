# python-agent

FastAPI mock Agent service for GameDev Agent Workbench.

## Run

1. Create and activate a virtual environment.
2. Install dependencies:

```bash
pip install -r requirements.txt
```

3. Start the service:

```bash
uvicorn app.main:app --reload
```

4. Open:

```text
http://127.0.0.1:8000/docs
```

## Endpoints

- `GET /health`
- `POST /agent/requirement-breakdown`
- `POST /agent/api-design`
- `POST /agent/bug-analysis`
- `POST /agent/prompt-generate`
