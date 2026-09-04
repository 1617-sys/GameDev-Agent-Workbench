const states = Object.freeze({
  loading: { title: "正在加载", message: "正在从服务端读取最新状态，请稍候。", busy: true },
  empty: { title: "暂无数据", message: "当前范围暂无可展示记录。", busy: false },
  forbidden: { title: "权限不足", message: "后端权限未向当前账号授予此能力。", busy: false },
  "network-error": { title: "网络请求失败", message: "无法连接服务，请检查网络后重试。", busy: false },
  partial: { title: "部分数据缺失", message: "服务端返回了部分结果，缺失内容不会被推测或伪造。", busy: false }
});

export function asyncStateCopy(kind) {
  return states[kind] || states["network-error"];
}

export function asyncStateKind({ loading = false, error = "", empty = false } = {}) {
  if (loading) return "loading";
  if (error) return /权限|403|forbidden/i.test(error) ? "forbidden" : "network-error";
  if (empty) return "empty";
  return "partial";
}

export function dangerConfirmation(expected, input) {
  return {
    disabled: !expected || input !== expected,
    message: "此操作可能影响正在使用的版本且不可撤销。请核对对象后输入确认词。"
  };
}
