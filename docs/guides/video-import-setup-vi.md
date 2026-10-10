# Hướng dẫn setup Video Import (Google Drive OAuth)

Hướng dẫn bật tính năng **Admin → Import Video** ([ADR-0019](../adr/0019-admin-video-import.md)) trên server production: tạo OAuth client, lấy refresh token, cấu hình server và test.

> **Làm 1 lần.** Thời gian khoảng 15 phút.

---

## 1. Vì sao cần OAuth khi đã có `googlekey.json`?

`googlekey.json` là key của **Service Account**. Service Account **đọc** được folder Drive đã share (cronjob sync đang dùng), nhưng **không upload** được:

```
list folder    : 200                       ✅ đọc được
canAddChildren : true                      ✅ có quyền ghi
upload         : 403 storageQuotaExceeded  ❌
```

Trên Google Drive, file thuộc về **người tạo** và tính vào dung lượng của người đó. Từ 2024 Service Account có **0 byte** storage quota, nên mọi upload đều bị từ chối. Share folder chỉ cấp *quyền*, không cấp *dung lượng*.

→ Upload dùng **OAuth refresh token** của chính tài khoản Gmail sở hữu folder (file thuộc về bạn, dùng 15 GB của bạn).

| Credential | Dùng cho | Ở đâu |
|------------|----------|-------|
| Service Account (`googlekey.json`) | Cronjob **đọc** Drive (`syncDriveVideos`) | `GOOGLE_CREDENTIAL_PATH` (giữ nguyên) |
| OAuth refresh token | Video Import **upload** lên Drive | `GOOGLE_OAUTH_*` trong `.env` |

Các cách khác (Shared Drive, domain-wide delegation) đều cần Google Workspace trả phí.

---

## 2. Tạo OAuth client trên Google Cloud Console

Mở <https://console.cloud.google.com> → kiểm tra project ở góc trên bên trái là project của Service Account (**canh-test**).

Menu → **Google Auth Platform** (hoặc **APIs & Services → OAuth consent screen**, sẽ tự chuyển sang Google Auth Platform).

### 2.1 Get started (nếu project chưa cấu hình)

| Bước | Giá trị |
|------|---------|
| App information → App name | `Funny App Uploader` |
| App information → User support email | email của bạn |
| Audience | **External** |
| Contact information | email của bạn |
| Finish | Tick đồng ý → **Continue** → **Create** |

### 2.2 Audience → Publish app ⚠️ quan trọng

- Publishing status đang **Testing** → bấm **Publish app** → **Confirm**.
- Status phải là **In production**.
- Google có thể nhắc verification → **bỏ qua**. App chưa verify vẫn dùng được cho chính tài khoản của bạn.

> ❗ Nếu để **Testing**, refresh token **hết hạn sau 7 ngày** → import lỗi `DRIVE_NOT_CONFIGURED` / `invalid_grant`.

### 2.3 Data Access

Bỏ qua. Script sẽ xin scope `https://www.googleapis.com/auth/drive` lúc consent.

> Không dùng scope `drive.file`: scope này không cho app thêm file vào folder mà app không tự tạo.

### 2.4 Clients → Create client

| Field | Giá trị |
|-------|---------|
| Application type | **Desktop app** |
| Name | `funny-uploader` |

Bấm **Create** → **Download JSON** → lưu thành `client_secret.json`.

> Console mới **chỉ cho tải client secret lúc tạo**. Lỡ đóng thì tạo client mới.

### 2.5 Kiểm tra Google Drive API

**APIs & Services → Enabled APIs & services** → phải có **Google Drive API**. Chưa có → **+ Enable APIs and services** → tìm "Google Drive API" → **Enable**.

---

## 3. Lấy refresh token (trên máy cá nhân)

Yêu cầu: Python 3 (chỉ dùng standard library), trình duyệt, port `8765` trống.

```bash
cd D:\DO\assessment
python scripts/google/get-drive-refresh-token.py D:\path\to\client_secret.json
```

1. Trình duyệt mở trang đăng nhập Google → chọn **tài khoản Gmail sở hữu folder Drive** (folder mà cronjob đang sync).
2. Màn hình **"Google hasn't verified this app"** → **Advanced** → **Go to Funny App Uploader (unsafe)**.
3. Màn hình xin quyền Drive → **Continue** / **Allow**.
4. Tab hiện `OK - you can close this tab.`; terminal hiện:
   ```
   Saved to drive-oauth.env (not printed). Copy into the server .env, then delete this file.
   ```

File `drive-oauth.env` gồm 3 dòng:

```
GOOGLE_OAUTH_CLIENT_ID=...
GOOGLE_OAUTH_CLIENT_SECRET=...
GOOGLE_OAUTH_REFRESH_TOKEN=...
```

> 🔒 Đây là secret: **không** commit (đã có trong `.gitignore`), **không** dán vào chat/issue. Sau khi chép lên server thì xoá `drive-oauth.env` và `client_secret.json` trên máy.

### Lỗi thường gặp

| Lỗi | Cách xử lý |
|-----|-----------|
| `client_secret.json must be an OAuth client (Desktop app)` | Tải nhầm key Service Account → tải JSON của OAuth client (bước 2.4) |
| `No refresh_token returned` | Vào <https://myaccount.google.com/permissions> → xoá quyền "Funny App Uploader" → chạy lại script |
| `Consent failed: access_denied` | Bấm Cancel ở màn hình consent, hoặc app còn Testing mà tài khoản không nằm trong Test users → Publish app (2.2) |
| `redirect_uri_mismatch` | Client không phải **Desktop app** → tạo lại đúng loại |
| `Address already in use` | Port 8765 đang bị dùng → tắt chương trình đó rồi chạy lại |

---

## 4. Cấu hình server

SSH vào server production.

### 4.1 Biến môi trường

Thêm vào `/opt/funnyapp/.env`:

```bash
VIDEO_IMPORT_ENABLED=true
GOOGLE_OAUTH_CLIENT_ID=...
GOOGLE_OAUTH_CLIENT_SECRET=...
GOOGLE_OAUTH_REFRESH_TOKEN=...
```

Bảo vệ file:

```bash
chmod 600 /opt/funnyapp/.env
```

Các biến tuỳ chọn (đã có default):

| Biến | Default | Ý nghĩa |
|------|---------|---------|
| `VIDEO_IMPORT_WORK_DIR` | `/opt/data/imports` | Thư mục tạm khi tải |
| `VIDEO_IMPORT_MAX_FILE_SIZE` | `1G` | Giới hạn dung lượng mỗi video |
| `VIDEO_IMPORT_MAX_CONCURRENT` | `1` | Số job chạy song song |
| `VIDEO_IMPORT_DOWNLOAD_TIMEOUT` | `30m` | Timeout tải 1 video |
| `VIDEO_IMPORT_BULK_MAX` | `20` | Số URL tối đa mỗi lần submit |
| `VIDEO_IMPORT_COOKIES_PATH` | (trống) | File cookies cho video Facebook cần đăng nhập |
| `YT_DLP_PATH` | `yt-dlp` | Đường dẫn binary yt-dlp |

### 4.2 Thư mục làm việc

```bash
sudo mkdir -p /opt/data/imports
sudo chown <user-chạy-app>:<group> /opt/data/imports
```

Cần ≥ 2 GB trống (job tự từ chối nếu thiếu).

### 4.3 yt-dlp và ffmpeg

```bash
yt-dlp --version
ffmpeg -version | head -1
```

Chưa có thì cài:

```bash
sudo apt update && sudo apt install -y ffmpeg
sudo curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp
sudo chmod a+rx /usr/local/bin/yt-dlp
```

Tự update hằng ngày (YouTube/Facebook đổi liên tục):

```bash
echo '0 3 * * * root /usr/local/bin/yt-dlp -U >/dev/null 2>&1' | sudo tee /etc/cron.d/yt-dlp-update
```

### 4.4 (Tuỳ chọn) Cookies cho Facebook

Chỉ cần khi video Facebook yêu cầu đăng nhập (lỗi `LOGIN_REQUIRED`). Export cookies từ trình duyệt (định dạng Netscape) rồi:

```bash
sudo cp cookies.txt /opt/funnyapp/fb-cookies.txt
sudo chown <user-chạy-app> /opt/funnyapp/fb-cookies.txt
sudo chmod 600 /opt/funnyapp/fb-cookies.txt
# .env
VIDEO_IMPORT_COOKIES_PATH=/opt/funnyapp/fb-cookies.txt
```

> Cookies chứa phiên đăng nhập Facebook của bạn, coi như mật khẩu.

### 4.5 Restart

```bash
cd /opt/funnyapp && ./stopServer.sh && ./startServer.sh
```

---

## 5. Test

1. Đăng nhập tài khoản admin → **Admin → Import Video**.
2. Dán 1 link YouTube → **Lấy tiêu đề** (kiểm tra yt-dlp chạy được) → **Tải ngay**.
3. Bảng job: `Đang tải về` → `Đang upload lên Drive` → `DONE / Chờ đồng bộ (≤15 phút)`.
4. Mở folder Google Drive → thấy file `{tiêu đề}.mp4`, owner là tài khoản của bạn.
5. Trong ≤ 15 phút (lượt cronjob tiếp theo) → trạng thái **Đã lên app**, video xuất hiện trên feed với đúng tiêu đề.
6. Test thêm: bulk 3 link (1 Reel Facebook, 1 YouTube Short, 1 link sai), hẹn giờ +5 phút, huỷ khi đang tải, thử lại job lỗi.

### Mã lỗi

| Mã | Ý nghĩa | Xử lý |
|----|---------|-------|
| `IMPORT_DISABLED` | `VIDEO_IMPORT_ENABLED` chưa bật | Bước 4.1 + restart |
| `DRIVE_NOT_CONFIGURED` | Thiếu `GOOGLE_OAUTH_*` | Bước 3–4.1 |
| `DRIVE_AUTH_FAILED` | Refresh token sai / bị thu hồi / hết hạn (`invalid_grant`) | Kiểm tra app đã **In production** (2.2) → làm lại bước 3 |
| `DRIVE_UPLOAD_FAILED` | Upload lên Drive lỗi (mạng, quota Drive đầy, folder bị xoá) | Kiểm tra dung lượng Drive → **Thử lại** |
| `DISK_LOW` | Thư mục làm việc còn < 2 GB | Dọn ổ đĩa server |
| `NOT_INSTALLED` | Không tìm thấy yt-dlp | Bước 4.3 hoặc set `YT_DLP_PATH` |
| `LOGIN_REQUIRED` | Video riêng tư / cần đăng nhập | Bước 4.4 |
| `EXTRACTOR_ERROR` | yt-dlp không đọc được trang (thường do site đổi) | `sudo yt-dlp -U` → **Thử lại** |
| `TOO_LARGE` | Vượt `VIDEO_IMPORT_MAX_FILE_SIZE` | Tăng giới hạn hoặc bỏ qua |
| `TIMEOUT` | Tải quá 30 phút / metadata quá 60 s | Thử lại lúc mạng ổn |
| `INTERRUPTED` | App restart giữa chừng 3 lần | **Thử lại** |
| `DUPLICATE_ACTIVE` | URL này đang có job chạy | Chờ job cũ xong |

`DRIVE_AUTH_FAILED` thường do refresh token bị thu hồi/hết hạn: app còn Testing, đổi mật khẩu Google, hoặc xoá quyền → làm lại bước 2.2 và 3.

---

## 6. Thu hồi / đổi token

- Thu hồi: <https://myaccount.google.com/permissions> → "Funny App Uploader" → **Remove access**.
- Đổi token: chạy lại bước 3 → cập nhật `.env` → restart.

> ⚖️ Chỉ import nội dung bạn có quyền sử dụng. Tải video từ YouTube/Facebook có thể vi phạm Terms of Service và bản quyền.
