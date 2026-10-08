import React, { useState } from 'react';
import { Check } from 'lucide-react';
import { categories, validateItem } from '../../inventory';
import Button from '../common/Button';
import FormField from '../common/FormField';
import Modal from '../common/Modal';

const blankItem = {
  name: '',
  sku: '',
  detail: '',
  category: 'アルバム',
  quantity: 0,
  minimum: 5,
  price: 0,
  location: '',
};

export default function ItemForm({ item, items, onSave, onClose }) {
  const [draft, setDraft] = useState(item || blankItem);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const close = () => {
    if (!saving) onClose();
  };
  const field = (name) => ({
    value: draft[name],
    onChange: (event) => setDraft({ ...draft, [name]: event.target.value }),
  });
  async function submit(event) {
    event.preventDefault();
    if (saving) return;
    const message = validateItem(draft, items, item?.id);
    if (message) return setError(message);
    setSaving(true);
    setError('');
    try {
      await onSave({
        ...draft,
        name: draft.name.trim(),
        sku: draft.sku.trim(),
        detail: draft.detail.trim(),
        location: draft.location.trim(),
        quantity: Number(draft.quantity),
        minimum: Number(draft.minimum),
        price: Number(draft.price),
        id: item?.id,
      });
    } catch (error) {
      setError(error.message);
    } finally {
      setSaving(false);
    }
  }
  return (
    <Modal
      eyebrow="YOUR COLLECTION"
      title={item ? '商品を編集' : '新しい商品を登録'}
      subtitle="大切なコレクションを、ひとつずつ。"
      onClose={close}
    >
      <form onSubmit={submit} className="space-y-5">
        <fieldset disabled={saving} className="space-y-5">
          <FormField label="商品名" required>
            <input
              autoFocus
              required
              maxLength={100}
              placeholder="例：With YOU-th"
              {...field('name')}
            />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="商品コード" required>
              <input
                required
                maxLength={40}
                placeholder="TW-AL-007"
                {...field('sku')}
              />
            </FormField>
            <FormField label="カテゴリー">
              <select {...field('category')}>
                {categories.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </FormField>
          </div>
          <FormField label="バージョン・商品メモ">
            <input
              maxLength={180}
              placeholder="例：Forever ver. / 初回限定"
              {...field('detail')}
            />
          </FormField>
          <div className="grid grid-cols-2 gap-4">
            <FormField label="在庫数">
              <input
                required
                type="number"
                min="0"
                max="99999999"
                step="1"
                {...field('quantity')}
              />
            </FormField>
            <FormField label="補充ライン">
              <input
                required
                type="number"
                min="0"
                max="99999999"
                step="1"
                {...field('minimum')}
              />
            </FormField>
          </div>
          <p className="-mt-3 text-xs text-muted">
            在庫数が補充ライン以下になると「残りわずか」と表示します。
          </p>
          <div className="grid grid-cols-2 gap-4">
            <FormField label="単価（円）">
              <input
                required
                type="number"
                min="0"
                max="99999999"
                step="1"
                {...field('price')}
              />
            </FormField>
            <FormField label="保管場所">
              <input
                maxLength={40}
                placeholder="例：A-01"
                {...field('location')}
              />
            </FormField>
          </div>
        </fieldset>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-3 border-t border-line pt-5">
          <Button variant="secondary" onClick={close} disabled={saving}>
            キャンセル
          </Button>
          <Button type="submit" disabled={saving}>
            <Check size={17} />
            {saving ? '保存中…' : item ? '変更を保存' : '商品を登録'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
