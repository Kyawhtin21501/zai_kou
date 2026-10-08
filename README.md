# ZaiKoo

React + Tailwind CSS、Spring Boot 4.1 / Java 21、PostgreSQL 18 の共有在庫管理アプリです。
在庫は PostgreSQL に保存され、同じサーバーにログインした端末で共有されます。

## 初回起動・既存環境の更新

Docker Desktop を起動し、ルートで実行します。数GB程度の空き容量と、初回の依存関係取得のためのインターネット接続が必要です。

```sh
python3 scripts/setup-local.py
docker compose up --build -d --wait
```

`setup-local.py` は `.env` がなければ作成し、未設定の管理者パスワードとDBアプリ用パスワードをランダム生成します。値は端末に表示せず、読み書き権限を所有者だけに設定します。DBを起動し、制限付き `zaikou_app` ロールを作成します。既存の在庫やボリュームは削除しません。

- 画面: http://localhost:5173
- ユーザー名: `.env` の `APP_ADMIN_USERNAME`（初期値 `admin`）
- パスワード: `.env` の `APP_ADMIN_PASSWORD` をエディターで確認
- ヘルスチェック: http://localhost:8080/actuator/health
- DB: `localhost:5433`（管理用。ブラウザから直接接続しません）

`.env` は Git に含めないでください。パスワードを変更した場合はセットアップスクリプトと `docker compose up -d --force-recreate backend` を再実行します。Spring Boot の再起動でログインセッションは失効します。

初期DBは空です。以前のブラウザ保存のデモデータは自動では移行せず、localStorage 内の既存値も削除しません。

## LAN からアクセス

同じLANの端末で `http://このMacのIP:5173` を開きます。フロントエンドだけがLANに公開され、`/api` は内部のバックエンドへプロキシされます。バックエンド8080とDB5433は `127.0.0.1` に限定しています。Wi-Fi の端末間通信制限とファイアウォールの受信許可を確認してください。

**現在のComposeは開発用HTTPです。実データや認証情報を扱う運用では、HTTPS対応のリバースプロキシでTLSを終端し、`SESSION_COOKIE_SECURE=true` に設定してください。** インターネットへVite開発サーバーを公開しないでください。本番では `npm run build` の静的ファイルを配信します。DBのバックアップと個人別アカウント管理も運用設計に含めてください。

## 在庫機能

- 商品登録・編集、検索、カテゴリー／在庫ステータスの絞り込み、並べ替え
- サーバー側ページ分割（標準20件、最大100件）と全在庫のサマリー
- 30秒ごとの自動更新（編集中・削除確認中・選択中は停止）と手動更新
- 単体／一括削除（最大100件、トランザクションで全件成功または全件取り消し）
- 論理削除と復元。UIの通知から取り消せます。APIの復元期限は削除後10分、削除したアカウントのみです。
- CSVエクスポート（絞り込み後の全件、最大10,000件、数式として解釈される文字列を無害化）

別端末で更新済みの商品を古い状態で保存すると `409 Conflict` になり、上書きされません。フォームを閉じて「一覧を更新」し、最新版を開き直してください。サーバーエラー時は成功扱いにせず、入力内容をフォームに残します。

## API

認証にはサーバー側セッションを使用します。トークンやパスワードをlocalStorageに保存しません。

| Method | パス                 | 内容                                                       |
| ------ | -------------------- | ---------------------------------------------------------- |
| GET    | `/api/auth/csrf`     | CSRFトークンとヘッダー名を取得                             |
| POST   | `/api/auth/login`    | `application/x-www-form-urlencoded` の username / password |
| GET    | `/api/auth/me`       | ログイン中のユーザー                                       |
| POST   | `/api/auth/logout`   | ログアウト                                                 |
| GET    | `/api/items`         | 一覧、件数、全在庫サマリー                                 |
| POST   | `/api/items`         | 登録                                                       |
| PUT    | `/api/items/{id}`    | 更新（version必須）                                        |
| POST   | `/api/items/delete`  | 論理削除                                                   |
| POST   | `/api/items/restore` | 復元                                                       |
| GET    | `/api/items/export`  | CSV                                                        |

一覧・CSVのパラメーター: `query`（最大100文字）、`category`、`status`（all / ok / low / out / attention）、`sort`（updated / quantity / name）。一覧のみ `page`（0始まり）、`size`（1〜100）。

登録・更新のJSON:

```json
{
  "sku": "TW-AL-001",
  "name": "With YOU-th",
  "detail": "Forever ver.",
  "category": "アルバム",
  "quantity": 10,
  "minimum": 5,
  "price": 3200,
  "location": "A-01"
}
```

更新時には取得した `version` を加えます。ID・更新日時・次のversionはサーバーが決定します。削除／復元のJSONは `{"items":[{"id":"UUID","version":0}]}` です。復元には削除APIが返した新しいversionを使用します。

書き込み時は先に `/api/auth/csrf` を取得し、その `headerName` に `token` を設定します。ログイン・ログアウトにも必要です。認証後はトークンが更新されるため取得し直します。フロントの `api.js` がこの処理を担当します。

エラーは `{ "message": "説明", "fields": {} }` 形式です。主なHTTPステータスは400（入力不正）、401（未ログイン）、403（権限／CSRF）、404（存在しない商品）、409（重複／更新競合／復元不可）、413（サイズ超過）、429（ログイン試行制限）。

## 検証・安全対策

- Bean Validation：必須、文字数、許可カテゴリー、0〜99,999,999の整数。小数・未知のJSON項目を拒否
- 商品コード：半角英数字・`_`・`-`、最大40文字、大文字に正規化。DBの部分ユニークインデックスでも重複を拒否
- SQLはバインド変数を使用し、ソート／フィルターの構造は許可リストから選択
- DBのCHECK制約・トランザクション・versionによる楽観ロック
- Spring Security：全在庫APIにADMIN権限、CSRF有効、HttpOnly／SameSite=Strictセッション、ログイン時のセッションID更新
- BCryptハッシュでパスワード照合。固定の初期パスワードなし。初期構成は環境変数で指定した管理者1アカウント
- ログイン試行は接続元IPごとに20回/分。Vite経由では複数端末が同じ接続元として集計されます。単一インスタンス用の制限です。
- リクエストボディは16KiB、CSV出力は10,000件まで
- スタックトレース・SQLエラー詳細を利用者に返さず、ヘルスチェック以外のActuatorを公開しない
- アプリ用DBロールは必要な表のSELECT／INSERT／UPDATEと監査ログINSERTのみ。Flywayは別の管理用接続を使用
- 操作ユーザー・対象ID・操作・version・時刻を `inventory_audit` に記録

テスト（Dockerが必要、既存DBではなく使い捨てPostgreSQLで実行）:

```sh
./gradlew test bootJar
cd frontend
npm ci
npm run build
```

Dockerが利用できないMacで、Homebrew PostgreSQL 18 がある場合:

```sh
sh scripts/test-native-postgres.sh
```

このスクリプトは一時ディレクトリに専用DBを作成し、終了時にそのDBだけを削除します。通常のPostgreSQLには接続しません。テストは認証・権限・CSRF・入力検証・重複・同時更新・一括処理の原子性・復元期限・CSV・ログイン試行制限・大容量リクエストを確認します。これらは安全性の検証範囲であり、すべての脆弱性が存在しないことを保証するものではありません。

## コード構成

- `src/main/java/jp/ac/ccmc/zaikou/inventory/`: DTO、Controller、Service、SQLによる永続化
- `src/main/java/jp/ac/ccmc/zaikou/security/`: 認証・権限・CSRF・リクエスト制限
- `src/main/java/jp/ac/ccmc/zaikou/web/`: 共通エラー処理
- `src/main/resources/db/migration/`: FlywayのDBマイグレーション（適用済みファイルは編集せず、V2以降を追加）
- `frontend/src/api.js`: 通信・CSRF・エラー処理
- `frontend/src/App.jsx`: 認証状態、一覧の取得、画面構成
- `frontend/src/components/common/`: 共通 UI
- `frontend/src/components/inventory/`: 商品フォーム・在庫表
- `scripts/setup-local.py`: 認証情報とDBユーザーの初期設定

Reactのソース変更は自動反映されます。Java変更後は `docker compose up -d --build backend` を実行します。

```sh
docker compose logs -f
docker compose down
```

通常の `down` ではDBデータは消えません。**全DBデータを削除する場合のみ** `docker compose down -v` を使用します。

参照: [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)、[PostgreSQL Testcontainers](https://java.testcontainers.org/modules/databases/postgres/)。
# zai_kou
