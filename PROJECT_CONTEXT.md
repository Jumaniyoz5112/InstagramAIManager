# Instagram AI Manager — PROJECT CONTEXT

> Loyihaning asosiy texnik konteksti. Cursor va inson bir xil arxitektura bo‘yicha ishlashi shart.

**Oxirgi yangilanish:** 2026-11-11  
**Mahsulot turi:** Multi-Tenant SaaS  
**Joriy bosqich:** Auth/H2/H1 + M0/M1 Instagram + Publishing — **DONE**; **Content AI analysis Phase-1** — **DONE**; **Corpus Insights + Next-post Scenario Phase-2** — **DONE** (text model `qwen2.5:7b` instruct; Liquibase 008)  
**To‘xtatilgan:** Media Management CRUD (library edit/delete), Video Factory / multi-agent, Redis rewrite  
**Keyingi (ruxsat bilan):** Platform Admin UI; async publish queue; FFmpeg keyframes  

---

## 1. Mahsulot

Ko‘p mijozli Instagram AI Manager SaaS. Umumiy BE/AI, ma’lumotlar `tenant_id` bilan ajratiladi. Client yuborgan tenant ID’ga ishonilmaydi.

## 2. Stack (o‘zgartirilmasin)

- BE: Java 21, Spring Boot 4.1.2, `uzeduai.be`
- UI: Angular 22 standalone + Tailwind 4
- DB: PostgreSQL + Liquibase (**SQL** changelogs)
- Redis: hali Spring’ga ulanmagan
- Ollama: lokal vision (`qwen2.5vl:7b`) via `ai-service`

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
| Post media filter + video player + metrics/comments sync | DONE |
| Instagram Content Publishing (feed/reels/stories/carousel) | DONE |
| Content AI analysis Phase-1 (vision once + engagement 2×/day) | DONE |

### Content AI analysis (Phase-1)

- Model: **Ollama `qwen2.5vl:7b`** (vision) + **`qwen2.5:7b`** (text); **ketma-ket**, parallel yo‘q (8GB VRAM)
- `ai-service`: global Ollama lock; uvicorn `--workers 1`; `OLLAMA_NUM_PARALLEL=1`, `OLLAMA_MAX_LOADED_MODELS=1`
- BE: `AiAnalysisClient` ReentrantLock (analyze/insights/scenario); `max-concurrent=1`, `queue-batch-size=1`
- `ai-service` FastAPI (modullar: `prompts/`, `services/`, `schemas`, `config`; `main.py` faqat route): `GET /health`, `POST /analyze` (vision), `POST /insights` + `POST /scenario` (text)
- **Manual only:** UI tanlash → `POST /media/analyze` yoki `POST /media/skip-analysis` (auto-enqueue yo‘q)
- Status: `NOT_ANALYZED|QUEUED|RUNNING|ANALYZED|FAILED|SKIPPED`; kartada spinner; summary `content_analysis_results` dan (ALTER yo‘q)
- Vision **bir marta** (tanlangan post); video Phase-1 da thumbnail
- Engagement: `media_engagement_snapshots` — **08:00/20:00** `Asia/Tashkent` (like/comment/share/save); vision qayta emas
- API: analyze/skip + `GET .../analysis`, `PUT .../analysis/feedback` (user `correctedSummary`/`userNote` → result JSON), `GET .../engagement-snapshots`
- UI: kartada «Tahlilni ko‘rish» dialog — AI xulosa + tuzatish maydonlari
- **Phase-2 Insights:** engagement score `likes+3×comments+2×shares+2×saves`; corpus = topN + lowN + ANALYZED mid; **bitta post kloni emas**
- API: `GET/POST /api/v1/instagram/insights/*` (leaderboard, generate, latest, scenarios)
- UI Instagram: Media | Chuqur tahlil tab — pattern; ssenariya alohida `/app/instagram/scenario` (INSTAGRAM page gate)
- Flags: `app.ai-service.enabled` (default true; test=false), `AI_SERVICE_URL`, `AI_MODEL_NAME`, `AI_TEXT_MODEL_NAME`
- Liquibase: `007-content-analysis-engagement.sql`, `008-content-insights-scenarios.sql` (CREATE only)
- Local: Ollama + `ai-service` Python **3.11** — vision `qwen2.5vl:7b`, text **`qwen2.5:7b`** (instruct; RTX 4060 8GB; coder emas)
- Scenario **1.5.1**: BRIEF=mavzu; pack faqat fallback va **5+ rotating variant**; har so‘rovda yangi burchak; model on-topic matni saqlanadi; Video Factory — yo‘q
- BE→ai-service: `AiAnalysisClient` Jackson JSON body (`SimpleClientHttpRequestFactory`; bo‘sh body 422 oldin)

### Instagram (M0/M1 BE+UI)

- Default **off**: `app.instagram.enabled=false` / `INSTAGRAM_ENABLED` (local `.env` da live test uchun `true`)
- Crypto: `IG_TOKEN_ENCRYPTION_KEY` → AES-GCM; Hibernate `@Convert` Spring bean emas — `CryptoJpaInitializer` static encryptor wire qiladi
- Storage: `STORAGE_ROOT` local FS (`StoragePort`) — MinIO keyin
- Page: `INSTAGRAM` (`/app/instagram`) — grant via Access; PLATFORM_ADMIN auto
- OAuth Phase A: Unified Login `config_id` (`POST /oauth/start` + INSTAGRAM page gate; callback permitAll)
- Discovery: `discoverInstagramAccounts` — IG `/me` → `/me/accounts` (Page token) → businesses → client_business → debug_token → `assigned_instagram_accounts` (oxirida)
- N>1 akkaunt: in-memory pick (`?ig_pick=` → `GET/POST /oauth/pick/{id}`); FB user token `fb_exchange_token`
- Same-portfolio fallback: **dev-only** `POST /dev/seed-account` (`@Profile("dev")`) — seed asosiy yo‘l emas
- Media Graph: Facebook Login / SUAT tokenlar uchun `graph.facebook.com` + **Bearer** (query `access_token` mangle qilmasin); `graph.instagram.com` faqat Instagram Login tokenlari
- Accounts: `GET /api/v1/instagram/accounts`, `POST .../{id}/sync`, `DELETE .../{id}`
- Media: `GET /api/v1/instagram/media` (`mediaType` optional), `GET .../{mediaId}`, `GET .../{mediaId}/comments`
- Publish: `POST /api/v1/instagram/publish` (multipart: `accountId`, `publishType`, `caption?`, `files`, `idempotencyKey?`); `GET /publish`, `GET /publish/{jobId}`; public signed `GET /public-media/{assetId}?exp&sig` (permitAll)
- Publish types: `FEED_IMAGE` | `REELS` | `STORY_IMAGE` | `STORY_VIDEO` | `CAROUSEL` (JPEG / MP4|MOV; carousel 2–10)
- Publish flow: store via `StoragePort` → signed public URL → Graph container → poll status → `media_publish`; Liquibase `006-instagram-publish.sql`
- **Local publish:** `INSTAGRAM_PUBLIC_MEDIA_BASE_URL` = HTTPS tunnel (Meta localhost’ni o‘qimaydi); token scope `instagram_business_content_publish` (yoki FB Login `instagram_content_publish`)
- **Remote UI test:** UI tunnel → `:4200` (proxy `/api`); `CORS_ALLOWED_ORIGINS` + `INSTAGRAM_FRONTEND_SUCCESS_URL` = UI origin; BE tunnel alohida (OAuth/public-media)
- Sync enrich: Graph `like_count`/`comments_count`; insights `shares`/`saved` → DB; comments upsert (`instagram_media_comments`); insights/comments xato → null/skip, sync davom etadi
- Liquibase: `005-…metrics-comments`, `006-…publish` (`instagram_publish_jobs` + `instagram_publish_assets`)
- Scopes default: `…_basic,…_manage_insights,…_manage_comments,instagram_business_content_publish`
- Sync: enqueue → scheduler; live verified: `@mashhura01_2009` ACTIVE, media synced
- UI: filter ALL|IMAGE|VIDEO|CAROUSEL; HTML5 video; metrics; comments; **Post qo‘shish** panel; i18n uz/ru/en
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
| `004-create-instagram-core.sql` | Instagram core + media metrics columns (CREATE) |
| `005-instagram-media-metrics-comments.sql` | `instagram_media_comments` CREATE only |
| `006-instagram-publish.sql` | publish jobs/assets |
| `007-content-analysis-engagement.sql` | analyses + results + engagement snapshots |

- **Test:** schema o‘zgarishi → CREATE / asosiy CREATE faylga qo‘shish; **`ALTER` yo‘q** — DB o‘chirib qayta yaratish
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

1. Platform Admin UI (`/app/platform/...`)
2. Async publish queue
3. Video FFmpeg keyframes (thumbnail o‘rniga)
4. Media Management CRUD / Video Factory — alohida ruxsat

## 5. Qoidalar

1. Media CRUD / Video Factory / multi-agent — ruxsatsiz yo‘q (Phase-1 content analysis ruxsat berilgan)  
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
| 2026-11-10 | Live Meta connect/sync fix (crypto init, Bearer Graph, dev seed) |
| 2026-11-10 | Media filter/video + metrics/comments Liquibase 005 + sync/API/UI |
| 2026-11-11 | Content AI Phase-1: Ollama vision + Liquibase 007 + engagement 2×/day |
| 2026-11-11 | Meta OAuth Phase A discovery/pick; Insights+Scenario Phase-2 (008, text model) |
