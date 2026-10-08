import React, { useState } from 'react';
import { Heart, LogIn } from 'lucide-react';
import Button from './common/Button';
import FormField from './common/FormField';
import { request } from '../api';

export default function LoginForm({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await request('/api/auth/login', {
        method: 'POST',
        form: true,
        body: { username, password },
      });
      setPassword('');
      onLogin(await request('/api/auth/me'));
    } catch (error) {
      setError(error.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#fbe6e2] p-6">
      <section className="w-full max-w-md rounded-2xl border border-line bg-white p-8 shadow-sm">
        <div className="mb-7 flex items-center gap-3 text-3xl font-bold">
          <Heart className="text-[#c6327e]" fill="currentColor" />
          ZaiKoo.
        </div>
        <p className="eyebrow mb-2">YOUR SHARED COLLECTION</p>
        <h1 className="mb-2 text-xl font-bold">在庫管理にログイン</h1>
        <p className="mb-7 text-sm text-muted">
          チームの在庫を、ひとつの場所で。
        </p>
        <form onSubmit={submit} className="space-y-5">
          <fieldset disabled={busy} className="space-y-5">
            <FormField label="ユーザー名" required>
              <input
                autoFocus
                autoComplete="username"
                required
                maxLength={64}
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </FormField>
            <FormField label="パスワード" required>
              <input
                type="password"
                autoComplete="current-password"
                required
                maxLength={72}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </FormField>
          </fieldset>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <Button className="w-full" type="submit" disabled={busy}>
            <LogIn size={17} />
            {busy ? 'ログイン中…' : 'ログイン'}
          </Button>
        </form>
      </section>
    </main>
  );
}
