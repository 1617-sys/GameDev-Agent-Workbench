export function episodePagination(page) {
  const totalPages = Math.max(1, Math.ceil(Number(page.total || 0) / Math.max(1, Number(page.size || 1))));
  return { page: page.page, totalPages, hasPrevious: page.page > 0, hasNext: page.page + 1 < totalPages };
}

export function batchPresentation(batch) {
  const empty = !Array.isArray(batch?.items) || batch.items.length === 0;
  return { empty, message: empty ? "批次已持久化，但没有 Episode 项。" : "" };
}
