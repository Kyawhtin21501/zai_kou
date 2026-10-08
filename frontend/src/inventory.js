export const categories = [
  'アルバム',
  'フォトカード',
  'ライブグッズ',
  'アパレル',
  'その他',
];
export const stockStatus = (item) =>
  item.quantity === 0 ? 'out' : item.quantity <= item.minimum ? 'low' : 'ok';
export function validateItem(draft, items, editingId) {
  if (!draft.name.trim() || !draft.sku.trim())
    return '商品名と商品コードを入力してください。';
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$/.test(draft.sku.trim()))
    return '商品コードは半角英数字・ハイフン・アンダースコアで入力してください。';
  if (
    items.some(
      (item) =>
        item.id !== editingId &&
        item.sku.toLowerCase() === draft.sku.trim().toLowerCase(),
    )
  )
    return 'この商品コードはすでに登録されています。';
  if (!categories.includes(draft.category))
    return 'カテゴリーを選択してください。';
  for (const key of ['quantity', 'minimum', 'price']) {
    if (
      String(draft[key]).trim() === '' ||
      !Number.isSafeInteger(Number(draft[key])) ||
      Number(draft[key]) < 0 ||
      Number(draft[key]) > 99999999
    )
      return '在庫数・補充ライン・単価には、0〜99,999,999 の整数を入力してください。';
  }
  return '';
}
