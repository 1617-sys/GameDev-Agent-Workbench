$ErrorActionPreference = "Stop"

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host ""
    Write-Host "Created .env from .env.example."
    Write-Host "Please edit .env first, especially MYSQL_ROOT_PASSWORD, DB_PASSWORD, JWT_SECRET, LLM_API_KEY, RABBITMQ_USERNAME and RABBITMQ_PASSWORD."
    Write-Host "Then run this script again:"
    Write-Host "  .\start-docker.ps1"
    exit 1
}

Write-Host "Starting GameDev Agent Workbench with Docker Compose..."
docker compose up --build
