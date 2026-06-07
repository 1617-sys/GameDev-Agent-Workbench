# Minimal Playable Game Demo

## Goal

This document records the simplest playable game demo path for GameDev Agent Workbench.

The goal is not to generate a complete game project yet. The goal is to make the existing AI workflow produce a playable demo link that can be shown in a video or interview.

## Current Chain

```text
User submits game idea
-> Java DemoStreamService starts SSE
-> Java calls three Agent steps
-> Python Agent calls LLM
-> Java saves Agent artifacts
-> Java GameBuildClient returns demoUrl
-> Frontend opens a playable static game page
```

## Why This Version Is Minimal

This version does not create a new game project folder, compile source code, or generate assets.

Instead, it uses a fixed browser game template:

```text
frontend-vue/public/demo/mock-game.html
frontend-vue/public/demo/mock-game.css
frontend-vue/public/demo/mock-game.js
```

The backend returns this page as `demoUrl`.

Example:

```text
http://localhost:5173/demo/mock-game.html?projectUuid=xxx&title=xxx
```

The page reads `projectUuid` and `title` from the URL, then starts a small playable canvas game.

## Gameplay

The demo game is intentionally simple:

- Move the player with WASD or arrow keys.
- Collect 3 gems.
- Reach the exit portal.
- The page shows a win message after clearing the goal.

## Backend Change

`GameBuildClient` is still a mock client, but it now returns a real playable URL.

It does three things:

1. Receives `GameBuildRequest`.
2. Builds a URL pointing to the frontend demo page.
3. Returns `GameBuildResponse` with `status`, `demoUrl`, `buildId`, and `timeTakenMs`.

## Why This Is Useful

This creates the first complete demo loop:

```text
AI generation result -> playable game preview
```

For a resume or interview, this is stronger than only returning text from an LLM.

## How To Test

Start Java backend:

```bash
cd backend-java
mvn spring-boot:run
```

Start Python Agent:

```bash
cd F:\coe\python\python-agent
.\.venv\Scripts\activate
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

Start Vue3 frontend:

```bash
cd frontend-vue
npm install
npm run dev
```

Then call:

```text
POST /api/demo/game/stream
```

When the SSE workflow completes, open the returned `demoUrl`.

## Next Upgrade

The next version should replace the fixed template with `GameSpec`.

Recommended chain:

```text
Agent output
-> GameSpec JSON
-> Phaser or Canvas template reads GameSpec
-> Different user ideas produce different playable parameters
```

This keeps the project stable while making the generated game feel more dynamic.
