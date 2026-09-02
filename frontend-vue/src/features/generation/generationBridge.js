export function bridgePresentation(result) {
  if (!result) return { disabled: true, label: "正在检查 Player 兼容性…", reasons: [], versionUuid: null };
  if (!result.compatible) {
    return {
      disabled: true,
      label: "与现有 Player 契约不兼容",
      versionUuid: null,
      reasons: (result.reasons || []).map(reason =>
        [reason.code, reason.path, reason.message].filter(Boolean).join(" · "))
    };
  }
  return {
    disabled: false,
    label: result.prototypeVersionUuid ? "查看原型版本" : "创建原型版本",
    versionUuid: result.prototypeVersionUuid || null,
    reasons: []
  };
}
