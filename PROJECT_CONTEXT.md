# Instagram AI Manager — PROJECT CONTEXT

> Loyihaning asosiy texnik konteksti. Cursor va inson bir xil arxitektura bo‘yicha ishlashi shart.

**Oxirgi yangilanish:** 2026-11-03  
**Mahsulot turi:** Multi-Tenant SaaS  
**Joriy bosqich:** Auth/H2/H1 + **M0/M1 Instagram Connect & Media Sync (BE+UI)** — **DONE**  
**To‘xtatilgan:** Media Management CRUD, Instagram **publishing**, AI Agent / Video Factory  
**Keyingi (ruxsat bilan):** Meta live OAuth (App credentials); Platform Admin UI; M2 insights  

---

## 1. Mahsulot

Ko‘p mijozli Instagram AI Manager SaaS. Umumiy BE/AI, ma’lumotlar `tenant_id` bilan ajratiladi. Client yuborgan tenant ID’ga ishonilmaydi.

## 2. Stack (o‘zgartirilmasin)

- BE: Java 21, Spring Boot 4.1.2, `uzeduai.be`
- UI: Angular 22 standalone + Tailwind 4
- DB: PostgreSQL + Liquibase (**SQL** changelogs)
- Redis/Ollama: hozir tegilmagan

## 3. Security holati

| Talab | Holat |
|-------|--------|
| Spring Security + BCrypt | DONE |
| JWT access (memory, not localStorage) | DONE |
| Refresh HttpOnly cookie + DB hash + rotation/revoke | DONE |
| Refresh reuse → family revoke (REQUIRES_NEW) | DONE |
| Parallel refresh atomic claim (no fork) | DONE |
| Disabled user refresh blocked | DONE |
| Logout by refresh cookie (no access token) + CSRF | DONE |
| Prod JWT/DB secrets fail-fast; no prod bootstrap sync | DONE |
| Liquibase drop-first **not** in base config | DONE |
| CSRF on cookie refresh/logout | DONE |
| CORS allowed origins | DONE |
| Register / Login / Refresh / Logout / Me | DONE |
| Workspace activate + membership check | DONE |
| Platform ADMIN vs tenant OWNER/ADMIN/MEMBER | DONE |
| Page-level Access (admin grants pages) | DONE |
| Cross-tenant isolation tests | DONE |
| H2 TenantContext + JWT claim revalidation per request | DONE |
| Soft-clear revoked membership; hard-block tenant APIs | DONE |
| Scoped workspace/tenant loads (no blind client IDs) | DONE |
| H1 Platform Admin read-only `/api/v1/platform/**` | DONE |
| M0 AES-GCM crypto + local StoragePort | DONE |
| M1 Instagram OAuth + media sync (BE, disabled by default) | DONE |
| M1 Instagram UI (connect / accounts / media / detail) | DONE |

### Instagram (M0/M1 BE+UI)

- Default **off**: `app.instagram.enabled=false` / `INSTAGRAM_ENABLED`
- Crypto: `IG_TOKEN_ENCRYPTION_KEY` → AES-GCM (`EncryptedStringConverter` on account tokens)
- Storage: `STORAGE_ROOT` local FS (`StoragePort`) — MinIO keyin
- Page: `INSTAGRAM` (`/app/instagram`) — grant via Access; PLATFORM_ADMIN auto
- OAuth: `POST /api/v1/instagram/oauth/start`, `GET .../callback` (permitAll + state)
- Callback redirect: `INSTAGRAM_FRONTEND_SUCCESS_URL` (default `http://localhost:4200/app/instagram`)
- Accounts: `GET /api/v1/instagram/accounts`, `POST .../{id}/sync`, `DELETE .../{id}`
- Media: `GET /api/v1/instagram/media`, `GET .../{mediaId}`
- Sync: OAuth → PENDING job; scheduler + manual trigger; token refresh cron
- Graph: `RestInstagramGraphClient` when enabled, else `NoopInstagramGraphClient`
- UI: `features/instagram` + `InstagramService`; nav via page grant; i18n uz/ru/en
- Prod secrets: `JWT_SECRET` + `IG_TOKEN_ENCRYPTION_KEY` (dev crypto default rejected in prod)

### Platform Admin (H1, read-only)

- Namespace: `/api/v1/platform/**` — class `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`
- `GET /tenants` — pagination (`page`, `size` 1–100), search `q`, allowlisted sort (`createdAt|updatedAt|name|slug`)
- `GET /tenants/{id}` — details + member/workspace counts
- `GET /tenants/{id}/workspaces` — path tenantId scoped
- `GET /tenants/{id}/members` — safe fields only: `userId`, `name`, `email`, `role`
- `GET /stats` — tenant/user/workspace/membership counts
- `PlatformAdminService` **does not** use `TenantMembershipService` / `TenantContextHolder`
- `/api/v1/tenants` = membership list; `/api/v1/platform/tenants` = global inventory (ajratilgan)
- Mutatsiyalar (block/delete/subscription) — **yo‘q**

### TenantContext (H2)

- `TenantContextFilter` (JWT filter’dan keyin): claim’larni `TenantMembershipService.resolve` bilan DB’da qayta tekshiradi
- Soft-clear: membership yo‘q → autentifikatsiya saqlanadi, active tenant/workspace/role tozalanadi
- `TenantContextHolder` ThreadLocal; har request `finally` da clear (async uchun TaskDecorator kerak bo‘ladi)
- Client `tenantId` faqat membership orqali; workspace `findByIdAndTenantId`
- Platform: `/access/**`; tenant-level: `/workspaces`; user membership list: `/tenants` (H1 yo‘q)

### Profiles / secrets

- `spring.profiles.default=dev`
- **Base** `application.properties`: `spring.liquibase.drop-first=false`, no JWT default secret
- **dev**: JWT default ok, bootstrap+sync-password, cookie `Secure=false`, drop-first only via `LIQUIBASE_DROP_FIRST`
- **prod**: `JWT_SECRET` / `IG_TOKEN_ENCRYPTION_KEY` / `DB_*` / `CORS_ALLOWED_ORIGINS` required; cookie `Secure=true`; bootstrap off; `SecurityStartupValidator` rejects empty/dev JWT+crypto + drop-first
- **test**: bootstrap on, drop-first=false (shared DB saqlanadi); crypto default like dev

### Bootstrap admin (faqat local/dev/test)

- `admin@local.dev` / `0110` — `app.bootstrap.enabled=true` + `sync-password` faqat dev/test
- Production’da bootstrap ishlamaydi

### Page Access

- Katalog: `app_pages` (DASHBOARD, TENANTS, WORKSPACES, INSTAGRAM, ACCESS)
- Grant: `user_page_access`
- `PLATFORM_ADMIN` → barcha sahifalar avtomatik
- Yangi register → faqat `DASHBOARD` (default)
- Admin Access UI: `/app/access`
- API page gate: `/tenants`, `/workspaces`
- Access API: `/api/v1/access/*` → faqat `PLATFORM_ADMIN`

### Auth / Tenant / Platform API

- `POST /api/v1/auth/register|login|refresh|logout`
- `GET /api/v1/auth/me` (ichida `pages[]`)
- `POST /api/v1/auth/workspaces/{id}/activate`
- Refresh/logout: CSRF required; logout permitAll (cookie bilan)
- `GET/POST /api/v1/tenants` — membership scoped
- `GET/POST /api/v1/workspaces?tenantId=` — membership scoped
- `GET /api/v1/platform/tenants|tenants/{id}|tenants/{id}/workspaces|tenants/{id}/members|stats`
- `GET/PUT /api/v1/access/pages|users|users/{id}/pages`
- `GET /api/health`

### Liquibase SQL

| Disk fayl | logicalFilePath |
|-----------|-----------------|
| `001-create-app-meta.sql` | `001-app-meta.sql` |
| `002-create-identity-and-tenant.sql` | `002-identity-tenant.sql` |
| `003-create-app-pages-access.sql` | `003-app-pages.sql` |
| `004-create-instagram-core.sql` | (yangi changesetlar) |

- Rename: faqat fayl nomi; `logicalFilePath` + checksum saqlanadi — **003 ichiga INSTAGRAM qo‘shilmagan**
- Drop-first: **prod/base = false**; local opt-in `LIQUIBASE_DROP_FIRST=true`

### i18n (BE ↔ UI)

- Tillar: **uz** (lotin, default), **ru**, **en**
- BE xatolik: `messageKey` (+ optional `messageArgs`)
- UI `I18nService` + `t` pipe
- Envelope: `{ success, data, messageKey, messageArgs, timestamp }`

### UI

- Auth memory token; logout faqat server success’dan keyin clear
- Nav `pages` bo‘yicha; Routes: `/auth/*`, `/app/{dashboard,tenants,workspaces,instagram,access,pending}`
- `pageGuard` + Access console

## 4. Keyingi (ruxsat bilan)

1. Meta App credentials — live OAuth (`INSTAGRAM_ENABLED=true` + App ID/Secret)
2. Platform Admin UI (`/app/platform/...`)
3. M2 insights (`005-instagram-insights.sql`)
4. Media Management CRUD / publishing / AI — alohida ruxsat

## 5. Qoidalar

1. Media CRUD / Instagram **publishing** / AI — ruxsatsiz yo‘q (M1 connect/sync ruxsat berilgan)  
2. Boot/Angular downgrade yo‘q  
3. Secrets env’da; tokenlarni loglama  
4. Tenant ID faqat server-verified membership (`TenantContext`); JWT claim’lar har requestda qayta tekshiriladi  
5. DB migration — SQL Liquibase; rename faqat `logicalFilePath` bilan; drop-first prod’da yo‘q  
6. Yangi UI sahifa → `app_pages` + Access orqali ochiladi  

## 6. Progress log

Batafsil kunlik jurnal: `.cursor/md/YYYY-MM-DD.md`

| Sana | Nima |
|------|------|
| 2026-11-02 | Monorepo, health, GitHub |
| 2026-11-02 | Foundation auth/JWT/dashboard |
| 2026-11-02 | Liquibase YAML → SQL; register + isolation tests |
| 2026-11-02 | Tailwind UI: tenants/workspaces |
| 2026-11-02 | Page Access control + professional auth UI |
| 2026-11-02 | i18n uz/ru/en — BE messageKey, UI tarjima |
| 2026-11-02 | Phase-1 auth: C1 drop-first, C2 secrets, H3 refresh, H4 logout + tests |
| 2026-11-02 | Phase-2 H2: TenantContext filter, membership revalidation, isolation tests |
| 2026-11-02 | H1: read-only `/api/v1/platform/**` + PlatformAdminIntegrationTest |
| 2026-11-03 | Liquibase 001–003 rename (`logicalFilePath`); 004 Instagram core |
| 2026-11-03 | M0 crypto/storage + M1 OAuth/sync BE; tests 33/33 |
| 2026-11-03 | M1 Instagram UI + OAuth frontend redirect; UI build OK |
