param(
    [string]$IdeaHistoryDir = "$env:APPDATA\JetBrains\IntelliJIdea2026.1\aia-task-history",
    [string]$MigrationDir = "$(Split-Path -Parent $PSScriptRoot)\docs\codex-migration\gamedev-sessions"
)

$ErrorActionPreference = "Stop"

function New-Base64JsonLine {
    param([hashtable]$Object)

    $json = $Object | ConvertTo-Json -Compress -Depth 32
    return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json))
}

function Get-SessionIdFromMarkdown {
    param([string]$Path)

    $firstLine = Get-Content -Encoding UTF8 -LiteralPath $Path -TotalCount 1
    return ($firstLine -replace '^# Codex Session ', '').Trim()
}

function Get-FirstUserPromptFromMarkdown {
    param([string]$Path)

    $lines = Get-Content -Encoding UTF8 -LiteralPath $Path
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -eq '### 1. User') {
            $body = New-Object System.Collections.Generic.List[string]
            for ($j = $i + 1; $j -lt $lines.Count; $j++) {
                if ($lines[$j] -like '### *') { break }
                if (-not [string]::IsNullOrWhiteSpace($lines[$j])) {
                    $body.Add($lines[$j])
                }
            }
            $prompt = (($body -join ' ') -replace '\s+', ' ').Trim()
            if ($prompt.Length -gt 90) {
                return $prompt.Substring(0, 90) + '...'
            }
            return $prompt
        }
    }
    return 'Imported Codex session'
}

if (-not (Test-Path -LiteralPath $IdeaHistoryDir)) {
    New-Item -ItemType Directory -Force -Path $IdeaHistoryDir | Out-Null
}

if (-not (Test-Path -LiteralPath $MigrationDir)) {
    throw "Migration directory not found: $MigrationDir"
}

$backupDir = Join-Path (Split-Path -Parent $IdeaHistoryDir) ("aia-task-history.backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
Copy-Item -LiteralPath $IdeaHistoryDir -Destination $backupDir -Recurse -Force

$existingSessionValues = @{}
Get-ChildItem -File -LiteralPath $IdeaHistoryDir -Filter '*.agentsession' -ErrorAction SilentlyContinue | ForEach-Object {
    $value = (Get-Content -Encoding UTF8 -LiteralPath $_.FullName -Raw).Trim()
    if ($value) { $existingSessionValues[$value] = $true }
}

$imported = 0
$skipped = 0
$agentId = 'acp.registry.codex-acp'

Get-ChildItem -File -LiteralPath $MigrationDir -Filter '*.md' | Sort-Object Name | ForEach-Object {
    $sessionId = Get-SessionIdFromMarkdown -Path $_.FullName
    if (-not $sessionId) { return }

    $sessionValue = "${agentId}:$sessionId"
    if ($existingSessionValues.ContainsKey($sessionValue)) {
        $skipped++
        return
    }

    $ideaSessionFileId = [Guid]::NewGuid().ToString()
    $relativeTranscript = "docs/codex-migration/gamedev-sessions/$($_.Name)"
    $firstPrompt = Get-FirstUserPromptFromMarkdown -Path $_.FullName
    $displayPrompt = "[Imported] $firstPrompt"
    if ($displayPrompt.Length -gt 120) {
        $displayPrompt = $displayPrompt.Substring(0, 120) + '...'
    }

    $markdownLines = @(
        'Imported from VS Code / Codex CLI history.',
        '',
        'Original Codex session id:',
        $sessionId,
        '',
        'Transcript file inside this project:',
        $relativeTranscript,
        '',
        'To continue with this history in IDEA Codex, ask it to read the transcript above and use it as GameDev Agent Workbench project context.'
    )
    $markdown = $markdownLines -join "`n"

    $events = New-Object System.Collections.Generic.List[string]
    $events.Add('AUI_EVENTS_V1')
    $events.Add((New-Base64JsonLine @{
        type = 'com.intellij.ml.llm.chat.shared.ChatSessionUserPromptEvent'
        id = @{ id = 1 }
        prompt = $displayPrompt
        attachments = @()
        agentId = @{ id = $agentId }
    }))
    $events.Add((New-Base64JsonLine @{
        type = 'com.intellij.ml.llm.chat.shared.ChatSessionMessageBlockEvent'
        id = @{ id = 2 }
        agentId = @{ id = $agentId }
        event = @{
            kind = 'com.intellij.ml.llm.aui.events.api.MarkdownBlockUpdatedEvent'
            stepId = "import-$sessionId"
            textChunk = $markdown
        }
    }))
    $events.Add((New-Base64JsonLine @{
        type = 'com.intellij.ml.llm.chat.shared.ChatSessionMessageBlockEvent'
        id = @{ id = 3 }
        agentId = @{ id = $agentId }
        event = @{
            kind = 'com.intellij.ml.llm.aui.events.api.ResultBlockUpdatedEvent'
            result = ''
            changes = @()
        }
    }))

    [IO.File]::WriteAllText((Join-Path $IdeaHistoryDir "$ideaSessionFileId.agentsession"), $sessionValue, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllLines((Join-Path $IdeaHistoryDir "$ideaSessionFileId.events"), $events, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $IdeaHistoryDir "$ideaSessionFileId.lastid"), '3', [Text.UTF8Encoding]::new($false))

    $existingSessionValues[$sessionValue] = $true
    $imported++
}

[PSCustomObject]@{
    Imported = $imported
    SkippedExisting = $skipped
    IdeaHistoryDir = $IdeaHistoryDir
    BackupDir = $backupDir
}
