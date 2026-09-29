export function pageParams(state) {
  return { page: state.size === 0 ? 1 : state.page, size: state.size };
}

// Existing consumers still receive an array, while every HTTP list request uses page/size.
export function legacyListResult(response) {
  if (response?.data?.passed && !Array.isArray(response.data.data) &&
      Array.isArray(response.data.data?.items)) {
    response.data.data = response.data.data.items;
  }
  return response;
}

export function pageItems(response, state) {
  const data = response?.data?.data;
  return Array.isArray(data) ? (state ? paginateLocal(data, state) : data) : (data?.items || []);
}

export function pageTotal(response) {
  const data = response?.data?.data;
  return Array.isArray(data) ? data.length : (data?.total ?? 0);
}

export function initialPage() {
  return { page: 1, size: 0, total: 0 };
}

export function paginateLocal(items, state) {
  if (state.size === 0) return items;
  const start = (state.page - 1) * state.size;
  return items.slice(start, start + state.size);
}
