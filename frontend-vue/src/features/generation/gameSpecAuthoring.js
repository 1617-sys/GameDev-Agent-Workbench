export function prepareAuthorRequest(mode, idea, specText) {
  if (mode === "scratch") {
    return { request: { idea: idea.trim(), currentSpec: null }, summary: null, error: null };
  }
  if (mode !== "revise") {
    return { request: null, summary: null, error: "请选择一种 GameSpec 创作模式" };
  }
  try {
    const currentSpec = JSON.parse(specText);
    if (!currentSpec || Array.isArray(currentSpec) || typeof currentSpec !== "object") throw new Error();
    return {
      request: { idea: idea.trim(), currentSpec },
      summary: {
        title: currentSpec.title || currentSpec.metadata?.title || "未命名规格",
        archetype: currentSpec.archetype || currentSpec.metadata?.gameType || "未知玩法",
        entityCount: Array.isArray(currentSpec.entities) ? currentSpec.entities.length : 0
      },
      error: null
    };
  } catch {
    return { request: null, summary: null, error: "修改模式要求编辑器中存在合法的 JSON 对象" };
  }
}
