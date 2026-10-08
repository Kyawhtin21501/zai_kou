import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowDown,
  CheckCheck,
  ChevronDown,
  Download,
  Heart,
  LayoutGrid,
  Package,
  Plus,
  Sparkles,
  Ticket,
  Trash2,
  TriangleAlert,
} from 'lucide-react';
import Button from './components/common/Button';
import ConfirmDialog from './components/common/ConfirmDialog';
import EmptyState from './components/common/EmptyState';
import SearchInput from './components/common/SearchInput';
import StatCard from './components/common/StatCard';
import Tabs from './components/common/Tabs';
import Toast from './components/common/Toast';
import ItemForm from './components/inventory/ItemForm';
import InventoryTable from './components/inventory/InventoryTable';
import { categories } from './inventory';
import { request, itemPayload, references } from './api';
import LoginForm from './components/LoginForm';

const yen = (value) =>
  new Intl.NumberFormat('ja-JP', { style: 'currency', currency: 'JPY' }).format(
    value,
  );
const number = (value) => value.toLocaleString('ja-JP');
export default function App() {
  const [user, setUser] = useState(undefined);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    request('/api/auth/me', { signal: controller.signal })
      .then(setUser)
      .catch((error) => {
        if (error.name === 'AbortError') return;
        if (error.status !== 401) setError(error.message);
        setUser(null);
      });
    return () => controller.abort();
  }, []);
  if (user === undefined)
    return (
      <p className="p-10 text-center" role="status">
        接続を確認しています…
      </p>
    );
  if (!user)
    return (
      <>
        <LoginForm
          onLogin={(user) => {
            setUser(user);
            setError('');
          }}
        />
        {error && (
          <p className="form-error fixed bottom-4 left-4 right-4" role="alert">
            {error}
          </p>
        )}
      </>
    );
  return <InventoryApp user={user} onUnauthorized={() => setUser(null)} />;
}

function InventoryApp({ user, onUnauthorized }) {
  const [data, setData] = useState({
    items: [],
    total: 0,
    page: 0,
    size: 20,
    summary: {
      itemCount: 0,
      totalQuantity: 0,
      totalValue: '0',
      low: 0,
      out: 0,
      categories: {},
    },
  });
  const items = data.items;
  const [storageError, setStorageError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const mutationLock = useRef(false);
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('すべて');
  const [filter, setFilter] = useState('all');
  const [sort, setSort] = useState('updated');
  const [editor, setEditor] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [notice, setNotice] = useState(null);
  const [selected, setSelected] = useState([]);
  const listRef = useRef(null);
  const total = data.summary.totalQuantity;
  const value = BigInt(data.summary.totalValue);
  const low = data.summary.low;
  const out = data.summary.out;
  const filtered = items;
  const params = () =>
    new URLSearchParams({
      query: search,
      category: category === 'すべて' ? '' : category,
      status: filter,
      sort,
    });
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(query);
      setPage(0);
    }, 250);
    return () => clearTimeout(timer);
  }, [query]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    const queryParams = new URLSearchParams({
      query: search,
      category: category === 'すべて' ? '' : category,
      status: filter,
      sort,
      page,
      size: 20,
    });
    request(`/api/items?${queryParams}`, { signal: controller.signal })
      .then((data) => {
        setData(data);
        setStorageError('');
        setSelected([]);
      })
      .catch((error) => {
        if (error.name === 'AbortError') return;
        if (error.status === 401) onUnauthorized();
        setStorageError(error.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [search, category, filter, sort, page, reload]);
  useEffect(() => {
    const timer = setInterval(() => {
      if (
        !document.hidden &&
        !editor &&
        !deleting &&
        selected.length === 0 &&
        !mutationLock.current
      )
        setReload((value) => value + 1);
    }, 30000);
    return () => clearInterval(timer);
  }, [editor, deleting, selected.length]);
  const allSelected =
    filtered.length > 0 && filtered.every((item) => selected.includes(item.id));
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), notice.deleted ? 600000 : 7000);
    return () => clearTimeout(timer);
  }, [notice]);
  function handleError(error) {
    if (error.status === 401) onUnauthorized();
    setStorageError(error.message);
  }
  async function save(item) {
    if (mutationLock.current)
      throw new Error('処理中です。しばらくお待ちください。');
    mutationLock.current = true;
    try {
      const editing = Boolean(item.id);
      await request(editing ? `/api/items/${item.id}` : '/api/items', {
        method: editing ? 'PUT' : 'POST',
        body: itemPayload(item, editing),
      });
      setEditor(null);
      setReload((value) => value + 1);
      setNotice({
        message: editing
          ? '商品の変更を保存しました'
          : '新しい商品を登録しました',
      });
    } catch (error) {
      handleError(error);
      throw error;
    } finally {
      mutationLock.current = false;
    }
  }
  async function remove() {
    if (mutationLock.current) return;
    mutationLock.current = true;
    setBusy(true);
    try {
      const deleted = await request('/api/items/delete', {
        method: 'POST',
        body: references(deleting),
      });
      setNotice({
        message: `${deleted.length}件の商品を削除しました`,
        deleted,
      });
      setSelected([]);
      setDeleting(null);
      setReload((value) => value + 1);
    } catch (error) {
      handleError(error);
    } finally {
      mutationLock.current = false;
      setBusy(false);
    }
  }
  async function restore(deleted) {
    if (mutationLock.current) return;
    mutationLock.current = true;
    try {
      await request('/api/items/restore', {
        method: 'POST',
        body: references(deleted),
      });
      setNotice({ message: '削除を取り消しました' });
      setReload((value) => value + 1);
    } catch (error) {
      handleError(error);
    } finally {
      mutationLock.current = false;
    }
  }
  async function logout() {
    try {
      await request('/api/auth/logout', { method: 'POST' });
      onUnauthorized();
    } catch (error) {
      handleError(error);
    }
  }
  function view(nextFilter) {
    setPage(0);
    setFilter(nextFilter);
    setCategory('すべて');
    setQuery('');
    setSelected([]);
    listRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  async function exportCsv() {
    try {
      const csv = await request(`/api/items/export?${params()}`, {
        blob: true,
      });
      const url = URL.createObjectURL(csv);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'zaikoo-inventory.csv';
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setNotice({ message: '絞り込み条件の在庫をCSVに書き出しました' });
    } catch (error) {
      handleError(error);
    }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="ZaiKoo ホーム">
          <span className="brand-mark">
            <Heart size={23} fill="currentColor" />
          </span>
          <span>
            ZaiKoo<span className="brand-period">.</span>
            <small>INVENTORY STUDIO</small>
          </span>
        </a>
        <div className="workspace">
          <span className="workspace-icon">
            <Sparkles size={17} />
          </span>
          <span>
            ONCE Collection<small>My workspace</small>
          </span>
          <span className="workspace-dot" />
        </div>
        <p className="nav-label">WORKSPACE</p>
        <nav aria-label="メインナビゲーション">
          <button
            className={`nav-item ${filter !== 'attention' ? 'active' : ''}`}
            onClick={() => view('all')}
          >
            <LayoutGrid size={19} />
            在庫一覧<span>{data.summary.itemCount}</span>
          </button>
          <button
            className={`nav-item ${filter === 'attention' ? 'active' : ''}`}
            onClick={() => view('attention')}
          >
            <TriangleAlert size={19} />
            補充が必要<span className="nav-count">{low + out}</span>
          </button>
          <button
            className="nav-item"
            onClick={() => setEditor({ item: null })}
          >
            <Plus size={19} />
            商品を登録
          </button>
        </nav>
        <div className="sidebar-bottom">
          <div className="fan-note">
            <Heart size={19} />
            <p>
              A little love.
              <br />A lot of collection.
            </p>
            <span>Made for your favorite things.</span>
          </div>
          <div className="profile">
            <div className="avatar">O</div>
            <div>
              {user.username}
              <small>共有ワークスペース</small>
            </div>
          </div>
        </div>
      </aside>

      <div className="main-shell">
        <header className="topbar">
          <div className="flex items-center gap-3">
            <span className="text-muted">Workspace</span>
            <span className="text-[#d3c7cd]">/</span>
            <span className="font-semibold">在庫管理</span>
          </div>
          <div className="flex items-center gap-3">
            <span className="demo-pill">
              <span />
              共有在庫
            </span>
            <Button variant="secondary" onClick={logout}>
              ログアウト
            </Button>
          </div>
        </header>
        <main className="main-content">
          <div className="page-heading">
            <div>
              <p className="eyebrow mb-2">MY COLLECTION, MY WORLD</p>
              <h1>
                在庫ダッシュボード<span className="heading-dot">.</span>
              </h1>
              <p className="mt-2 text-sm text-muted">
                お気に入りを、いつでもベストな状態に。
              </p>
            </div>
            <Button onClick={() => setEditor({ item: null })}>
              <Plus size={18} />
              商品を登録
            </Button>
          </div>

          <section
            className="collection-banner"
            aria-label="コレクションの紹介"
          >
            <div className="banner-copy">
              <span className="banner-tag">
                <Heart size={12} fill="currentColor" /> FOR EVERY ONCE
              </span>
              <h2>
                One in a million.
                <br />
                <span>Every item matters.</span>
              </h2>
              <p>好きが集まる場所を、もっと心地よく。</p>
            </div>
            <div className="banner-type" aria-hidden="true">
              <span>THE COLLECTION EDIT</span>
              <strong>
                TWICE<span>inspired.</span>
              </strong>
              <div className="banner-bottom">
                <span>APRICOT & NEON MAGENTA</span>
                <Heart size={28} />
              </div>
            </div>
          </section>

          <section className="stats-grid" aria-label="在庫サマリー">
            {[
              {
                label: '登録商品',
                value: number(data.summary.itemCount),
                unit: '種類',
                icon: Package,
                color: 'pink',
                caption: 'Your growing collection',
              },
              {
                label: '総在庫数',
                value: number(total),
                unit: '点',
                icon: LayoutGrid,
                color: 'peach',
                caption: 'すべての商品の合計',
              },
              {
                label: '補充が必要',
                value: number(low + out),
                unit: '種類',
                icon: TriangleAlert,
                color: 'yellow',
                caption: `残りわずか ${low} · 在庫なし ${out}`,
                action: true,
              },
              {
                label: '在庫総額',
                value: yen(value),
                unit: '',
                icon: Ticket,
                color: 'purple',
                caption: '単価 × 在庫数',
              },
            ].map((stat) => (
              <StatCard
                key={stat.label}
                {...stat}
                onClick={stat.action ? () => view('attention') : undefined}
              />
            ))}
          </section>

          {storageError && (
            <p className="form-error mb-5" role="alert">
              {storageError}
            </p>
          )}
          <div className="mb-3 flex items-center justify-between gap-3 text-xs text-muted">
            <span role="status">
              {loading ? '在庫を読み込み中…' : '30秒ごとに自動更新'}
            </span>
            <Button
              variant="secondary"
              disabled={loading}
              onClick={() => setReload((value) => value + 1)}
            >
              一覧を更新
            </Button>
          </div>
          <section
            className="inventory-panel"
            ref={listRef}
            aria-labelledby="inventory-title"
          >
            <div className="inventory-heading">
              <div className="flex items-center gap-3">
                <h2 id="inventory-title">在庫一覧</h2>
                <span className="count-pill">
                  {data.summary.itemCount} items
                </span>
              </div>
              <Button
                variant="secondary"
                className="export-button"
                onClick={exportCsv}
              >
                <Download size={16} />
                CSV エクスポート
              </Button>
            </div>
            <Tabs
              label="カテゴリー"
              value={category}
              options={['すべて', ...categories].map((c) => ({
                value: c,
                label: c,
                count:
                  c === 'すべて'
                    ? data.summary.itemCount
                    : data.summary.categories[c] || 0,
              }))}
              onChange={(value) => {
                setPage(0);
                setCategory(value);
                setSelected([]);
              }}
            />
            <div className="table-toolbar">
              <SearchInput
                label="商品を検索"
                placeholder="商品名・商品コードで検索…"
                value={query}
                onChange={(value) => {
                  setQuery(value);
                  setSelected([]);
                }}
              />
              <div className="filter-controls">
                <label className="select-wrap">
                  <select
                    aria-label="在庫ステータス"
                    value={filter}
                    onChange={(event) => {
                      setPage(0);
                      setFilter(event.target.value);
                      setSelected([]);
                    }}
                  >
                    <option value="all">すべてのステータス</option>
                    <option value="ok">在庫あり</option>
                    <option value="low">残りわずか</option>
                    <option value="out">在庫なし</option>
                    <option value="attention">補充が必要</option>
                  </select>
                  <ChevronDown size={14} />
                </label>
                <label className="select-wrap">
                  <ArrowDown size={15} />
                  <select
                    aria-label="並び順"
                    value={sort}
                    onChange={(event) => {
                      setPage(0);
                      setSort(event.target.value);
                    }}
                  >
                    <option value="updated">更新日順</option>
                    <option value="quantity">在庫が少ない順</option>
                    <option value="name">商品名順</option>
                  </select>
                </label>
              </div>
            </div>
            {selected.length > 0 && (
              <div className="selection-bar">
                <span>{selected.length}件を選択中</span>
                <button
                  onClick={() =>
                    setDeleting(
                      items.filter((item) => selected.includes(item.id)),
                    )
                  }
                >
                  <Trash2 size={15} />
                  選択した商品を削除
                </button>
                <button onClick={() => setSelected([])}>選択解除</button>
              </div>
            )}
            <InventoryTable
              items={filtered}
              selected={selected}
              onToggle={(id) =>
                setSelected(
                  selected.includes(id)
                    ? selected.filter((selectedId) => selectedId !== id)
                    : [...selected, id],
                )
              }
              onToggleAll={() =>
                setSelected(
                  allSelected
                    ? selected.filter(
                        (id) => !filtered.some((item) => item.id === id),
                      )
                    : [
                        ...new Set([
                          ...selected,
                          ...filtered.map((item) => item.id),
                        ]),
                      ],
                )
              }
              onEdit={(item) => setEditor({ item })}
              onDelete={(item) => setDeleting([item])}
            />
            {!loading && !storageError && filtered.length === 0 && (
              <EmptyState
                icon={Package}
                title={
                  data.summary.itemCount === 0
                    ? 'コレクションをはじめよう'
                    : '一致する商品がありません'
                }
                description={
                  data.summary.itemCount === 0
                    ? '最初の商品を登録して、在庫を管理しましょう。'
                    : 'キーワードや絞り込み条件を変更してください。'
                }
                actionLabel={
                  data.summary.itemCount === 0 ? '商品を登録' : '条件をリセット'
                }
                onAction={() =>
                  data.summary.itemCount === 0
                    ? setEditor({ item: null })
                    : view('all')
                }
              />
            )}
            <div className="flex items-center justify-end gap-3 border-t border-line p-4">
              <Button
                variant="secondary"
                disabled={loading || data.page === 0}
                onClick={() => {
                  setPage(data.page - 1);
                  setSelected([]);
                }}
              >
                前へ
              </Button>
              <span className="text-xs">
                {data.page + 1} /{' '}
                {Math.max(1, Math.ceil(data.total / data.size))}
              </span>
              <Button
                variant="secondary"
                disabled={loading || (data.page + 1) * data.size >= data.total}
                onClick={() => {
                  setPage(data.page + 1);
                  setSelected([]);
                }}
              >
                次へ
              </Button>
            </div>
            <div className="table-footer">
              <span>
                {data.total}件中 {filtered.length}件を表示（{data.page + 1}
                ページ）
              </span>
              <span>
                <CheckCheck size={14} />
                PostgreSQL に保存
              </span>
            </div>
          </section>
          <footer className="page-footer">
            <span>
              ZaiKoo Studio <span className="mx-2">/</span> A place for what you
              love.
            </span>
            <span>TWICE-inspired fan concept · Shared inventory</span>
          </footer>
        </main>
      </div>
      {editor && (
        <ItemForm
          item={editor.item}
          items={items}
          onSave={save}
          onClose={() => setEditor(null)}
        />
      )}
      {deleting && (
        <ConfirmDialog
          eyebrow="YOUR COLLECTION"
          title="商品を削除しますか？"
          subtitle={`${deleting.length}件の商品を在庫一覧から削除します。`}
          confirmLabel="削除する"
          confirmIcon={Trash2}
          onConfirm={remove}
          pending={busy}
          onClose={() => {
            if (!busy) setDeleting(null);
          }}
        >
          {storageError && (
            <p className="form-error mb-3" role="alert">
              {storageError}
            </p>
          )}
          <div className="delete-summary">
            {deleting.map((item) => (
              <p key={item.id}>
                {item.name}
                <small>{item.sku}</small>
              </p>
            ))}
          </div>
          <p className="my-5 text-sm text-muted">
            削除後、通知の「元に戻す」から取り消せます。
          </p>
        </ConfirmDialog>
      )}
      {notice && (
        <Toast
          message={notice.message}
          actionLabel="元に戻す"
          onAction={notice.deleted ? () => restore(notice.deleted) : undefined}
          onClose={() => setNotice(null)}
        />
      )}
    </div>
  );
}
