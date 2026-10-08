import React from 'react';
import {
  Disc3,
  Heart,
  Package,
  Pencil,
  Shirt,
  Sparkles,
  Trash2,
} from 'lucide-react';
import { categories, stockStatus } from '../../inventory';
import IconButton from '../common/IconButton';

const yen = (value) =>
  new Intl.NumberFormat('ja-JP', { style: 'currency', currency: 'JPY' }).format(
    value,
  );
const number = (value) => value.toLocaleString('ja-JP');
const statusLabels = { ok: '在庫あり', low: '残りわずか', out: '在庫なし' };
const categoryIcons = {
  アルバム: Disc3,
  フォトカード: Heart,
  ライブグッズ: Sparkles,
  アパレル: Shirt,
  その他: Package,
};
export default function InventoryTable({
  items,
  selected,
  onToggle,
  onToggleAll,
  onEdit,
  onDelete,
}) {
  const allSelected =
    items.length > 0 && items.every((item) => selected.includes(item.id));
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th className="check-cell">
              <input
                type="checkbox"
                aria-label="表示中の商品をすべて選択"
                checked={allSelected}
                onChange={onToggleAll}
              />
            </th>
            <th>商品情報</th>
            <th>カテゴリー</th>
            <th className="text-right">在庫数</th>
            <th>ステータス</th>
            <th className="text-right">単価</th>
            <th>保管場所</th>
            <th className="text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const Icon = categoryIcons[item.category];
            const status = stockStatus(item);
            return (
              <tr
                key={item.id}
                className={selected.includes(item.id) ? 'row-selected' : ''}
              >
                <td className="check-cell">
                  <input
                    type="checkbox"
                    aria-label={`${item.name}を選択`}
                    checked={selected.includes(item.id)}
                    onChange={() => onToggle(item.id)}
                  />
                </td>
                <td>
                  <div className="product-cell">
                    <div
                      className={`product-icon category-${categories.indexOf(item.category)}`}
                    >
                      <Icon size={25} strokeWidth={1.4} />
                    </div>
                    <div>
                      <span className="product-sku">{item.sku}</span>
                      <span className="product-name">{item.name}</span>
                      <span className="product-detail">
                        {item.detail || '—'}
                      </span>
                    </div>
                  </div>
                </td>
                <td>
                  <span className="category-label">{item.category}</span>
                </td>
                <td
                  className={`quantity-cell ${status !== 'ok' ? 'quantity-alert' : ''}`}
                >
                  {number(item.quantity)}
                  <span>点</span>
                </td>
                <td>
                  <span className={`status-badge ${status}`}>
                    <span />
                    {statusLabels[status]}
                  </span>
                </td>
                <td className="price-cell">{yen(item.price)}</td>
                <td>
                  <span className="location-label">{item.location || '—'}</span>
                </td>
                <td>
                  <div className="row-actions">
                    <IconButton
                      label={`${item.name}を編集`}
                      onClick={() => onEdit(item)}
                    >
                      <Pencil size={16} />
                    </IconButton>
                    <IconButton
                      className="delete-button"
                      label={`${item.name}を削除`}
                      onClick={() => onDelete(item)}
                    >
                      <Trash2 size={16} />
                    </IconButton>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
