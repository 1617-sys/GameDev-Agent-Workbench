param(
    [string]$WorkspaceXml = "$env:APPDATA\JetBrains\IntelliJIdea2026.1\workspace\3DMASwn84BQLNNPhrni0vbRHvwg.xml",
    [string]$HistoryDir = "$env:APPDATA\JetBrains\IntelliJIdea2026.1\aia-task-history",
    [string]$MigrationDir = "$(Split-Path -Parent $PSScriptRoot)\docs\codex-migration\gamedev-sessions"
)

$ErrorActionPreference = "Stop"

function Escape-Xml {
    param([string]$Text)
    return [System.Security.SecurityElement]::Escape($Text)
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
    return 'Imported Codex history'
}

if (-not (Test-Path -LiteralPath $WorkspaceXml)) {
    throw "Workspace XML not found: $WorkspaceXml"
}
if (-not (Test-Path -LiteralPath $MigrationDir)) {
    throw "Migration directory not found: $MigrationDir"
}

$backup = "$WorkspaceXml.backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Copy-Item -LiteralPath $WorkspaceXml -Destination $backup -Force

$xml = [System.IO.File]::ReadAllText($WorkspaceXml, [Text.Encoding]::UTF8)

function Add-MapEntry {
    param(
        [string]$XmlText,
        [string]$ComponentName,
        [string]$ChatId,
        [string]$Entry
    )

    $componentMarker = "<component name=""$ComponentName"">"
    $componentStart = $XmlText.IndexOf($componentMarker)
    if ($componentStart -lt 0) {
        throw "Component not found: $ComponentName"
    }

    $componentEnd = $XmlText.IndexOf("</component>", $componentStart)
    if ($componentEnd -lt 0) {
        throw "Component end not found: $ComponentName"
    }

    $componentText = $XmlText.Substring($componentStart, $componentEnd - $componentStart)
    if ($componentText.Contains("key=""$ChatId""")) {
        return [PSCustomObject]@{ Text = $XmlText; Added = $false }
    }

    $mapEnd = $XmlText.IndexOf("      </map>", $componentStart)
    if ($mapEnd -lt 0 -or $mapEnd -gt $componentEnd) {
        throw "Map end not found in component: $ComponentName"
    }

    return [PSCustomObject]@{
        Text = $XmlText.Insert($mapEnd, $Entry)
        Added = $true
    }
}

$sessionFiles = Get-ChildItem -File -LiteralPath $HistoryDir -Filter '*.agentsession'
$imports = @()
Get-ChildItem -File -LiteralPath $MigrationDir -Filter '*.md' | Sort-Object Name | ForEach-Object {
    $id = ((Get-Content -Encoding UTF8 -LiteralPath $_.FullName -TotalCount 1) -replace '^# Codex Session ', '').Trim()
    if (-not $id) { return }

    $agentsession = $sessionFiles | Where-Object {
        (Get-Content -Encoding UTF8 -LiteralPath $_.FullName -Raw).Trim() -eq "acp.registry.codex-acp:$id"
    } | Select-Object -First 1
    if (-not $agentsession) { return }

    $chatId = [IO.Path]::GetFileNameWithoutExtension($agentsession.Name)
    $rel = "docs/codex-migration/gamedev-sessions/$($_.Name)"
    $prompt = Get-FirstUserPromptFromMarkdown -Path $_.FullName
    $imports += [PSCustomObject]@{
        SessionId = $id
        ChatId = $chatId
        Transcript = $rel
        Prompt = $prompt
    }
}

$insertedMaps = 0
$insertedChats = 0

foreach ($item in $imports) {
    $entry = "        <entry key=""$($item.ChatId)"" value=""false"" />`r`n"

    $result = Add-MapEntry -XmlText $xml -ComponentName "AgentBraveModeToggleService" -ChatId $item.ChatId -Entry $entry
    $xml = $result.Text
    if ($result.Added) {
        $insertedMaps++
    }

    $result = Add-MapEntry -XmlText $xml -ComponentName "JunieThinkMoreToggleService" -ChatId $item.ChatId -Entry $entry
    $xml = $result.Text
    if ($result.Added) {
        $insertedMaps++
    }
}

$serialized = New-Object System.Collections.Generic.List[string]
foreach ($item in $imports) {
    if ($xml -match [regex]::Escape("<option name=""uid"" value=""$($item.ChatId)"" />")) {
        continue
    }

    $title = "[Imported] " + $item.Prompt
    if ($title.Length -gt 70) { $title = $title.Substring(0, 70) + "..." }
    $message = "Please read $($item.Transcript) and use it as imported history context for the current GameDev Agent Workbench project."
    $now = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    $msgId = [Guid]::NewGuid().ToString()
    $assistantId = [Guid]::NewGuid().ToString()

    $serialized.Add(@"
        <SerializedChat>
          <option name="chatContext">
            <SerializedChatContext />
          </option>
          <option name="chatModelId" value="agent_acp.registry.codex-acp" />
          <option name="messages">
            <list>
              <SerializedChatMessage>
                <option name="markupLanguageID" value="ChatInput" />
                <option name="uid" value="UUID(uuid=$msgId)" />
                <option name="displayContent" value="$(Escape-Xml $message)" />
                <option name="internalContent" value="$(Escape-Xml $message)" />
              </SerializedChatMessage>
              <SerializedChatMessage>
                <option name="author" value="Assistant" />
                <option name="uid" value="$assistantId" />
              </SerializedChatMessage>
            </list>
          </option>
          <option name="modifiedAt" value="$now" />
          <option name="statisticInformation">
            <ChatStatisticInformation>
              <option name="sourceActionType" value="NEW_CHAT" />
              <option name="timestamp" value="$now" />
            </ChatStatisticInformation>
          </option>
          <option name="title">
            <SerializedChatTitle>
              <option name="custom" value="true" />
              <option name="text" value="$(Escape-Xml $title)" />
            </SerializedChatTitle>
          </option>
          <option name="uid" value="$($item.ChatId)" />
        </SerializedChat>
"@)
    $insertedChats++
}

if ($serialized.Count -gt 0) {
    $block = ($serialized -join "`r`n")
    $fileEditorIndex = $xml.IndexOf('  <component name="FileEditorManager">')
    if ($fileEditorIndex -lt 0) {
        throw "FileEditorManager component not found"
    }

    $chatListEnd = $xml.LastIndexOf('      </list>', $fileEditorIndex)
    if ($chatListEnd -lt 0) {
        throw "ChatSessionStateTemp list end not found"
    }

    $xml = $xml.Insert($chatListEnd, "`r`n" + $block + "`r`n")
}

[System.IO.File]::WriteAllText($WorkspaceXml, $xml, [Text.UTF8Encoding]::new($false))

[PSCustomObject]@{
    ImportedCandidates = $imports.Count
    InsertedMapEntries = $insertedMaps
    InsertedSerializedChats = $insertedChats
    WorkspaceXml = $WorkspaceXml
    Backup = $backup
}
