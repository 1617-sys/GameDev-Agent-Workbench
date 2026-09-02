const exposedFields = ["templateUuid", "name", "agentType", "systemPrompt", "userPromptTemplate", "version", "status", "updatedAt"];

export function safePromptTemplate(source = {}) {
  return Object.fromEntries(exposedFields.map(field => [field, source[field] ?? null]));
}

export function promptDiff(before = {}, after = {}) {
  return exposedFields
    .filter(field => before[field] !== after[field])
    .map(field => ({ field, before: before[field] ?? null, after: after[field] ?? null }));
}

export function promptUpdatePayload(template = {}) {
  return {
    name: String(template.name || "").trim(),
    agentType: String(template.agentType || "").trim(),
    systemPrompt: String(template.systemPrompt || ""),
    userPromptTemplate: String(template.userPromptTemplate || ""),
    version: Number(template.version) + 1,
    status: String(template.status || "").trim()
  };
}
