export class ApiError extends Error {
  constructor(message, status, fields = {}) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}

export async function request(
  path,
  { method = 'GET', body, signal, form = false, blob = false } = {},
) {
  const headers = {
    Accept: blob ? 'text/csv, application/json' : 'application/json',
  };
  if (method !== 'GET') {
    const csrf = await request('/api/auth/csrf');
    headers[csrf.headerName] = csrf.token;
  }
  if (body !== undefined)
    headers['Content-Type'] = form
      ? 'application/x-www-form-urlencoded'
      : 'application/json';
  const timeout = AbortSignal.timeout(20000);
  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
      body:
        body === undefined
          ? undefined
          : form
            ? new URLSearchParams(body)
            : JSON.stringify(body),
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    if (timeout.aborted) {
      throw new ApiError(
        '通信がタイムアウトしました。一覧を更新して処理結果を確認してください。',
        0,
      );
    }
    throw new ApiError(
      'サーバーに接続できません。接続を確認して再試行してください。',
      0,
    );
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new ApiError(
      error.message || '処理できませんでした。時間をおいて再試行してください。',
      response.status,
      error.fields,
    );
  }
  if (response.status === 204) return null;
  return blob ? response.blob() : response.json();
}

export function itemPayload(item, editing) {
  const { sku, name, detail, category, quantity, minimum, price, location } =
    item;
  return {
    sku,
    name,
    detail,
    category,
    quantity,
    minimum,
    price,
    location,
    ...(editing ? { version: item.version } : {}),
  };
}
export const references = (items) => ({
  items: items.map(({ id, version }) => ({ id, version })),
});
