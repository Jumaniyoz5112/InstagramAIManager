# Instagram AI Manager — PROJECT CONTEXT

> Loyihaning asosiy texnik konteksti. Cursor va inson bir xil arxitektura bo‘yicha ishlashi shart.

**Oxirgi yangilanish:** 2026-11-02  
**Mahsulot turi:** Multi-Tenant SaaS  
**Joriy bosqich:** Auth Phase-1 + H2 TenantContext + H1 Platform Admin (read-only) — **DONE**  
**To‘xtatilgan:** Media Management, Instagram publishing, AI Agent  
**Keyingi (ruxsat bilan):** Platform Admin UI; Media (ruxsat bilan)  

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
- **prod**: `JWT_SECRET` / `DB_*` / `CORS_ALLOWED_ORIGINS` required; cookie `Secure=true`; bootstrap off; `SecurityStartupValidator` rejects empty/dev secret + drop-first
- **test**: bootstrap on, drop-first=false (shared DB saqlanadi)

### Bootstrap admin (faqat local/dev/test)

- `admin@local.dev` / `0110` — `app.bootstrap.enabled=true` + `sync-password` faqat dev/test
- Production’da bootstrap ishlamaydi

### Page Access

- Katalog: `app_pages` (DASHBOARD, TENANTS, WORKSPACES, ACCESS)
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

- `001-app-meta.sql`
- `002-identity-tenant.sql`
- `003-app-pages.sql`
- Drop-first: **prod/base = false**; local opt-in `LIQUIBASE_DROP_FIRST=true`

### i18n (BE ↔ UI)

- Tillar: **uz** (lotin, default), **ru**, **en**
- BE xatolik: `messageKey` (+ optional `messageArgs`)
- UI `I18nService` + `t` pipe
- Envelope: `{ success, data, messageKey, messageArgs, timestamp }`

### UI

- Auth memory token; logout faqat server success’dan keyin clear
- Nav `pages` bo‘yicha; Routes: `/auth/*`, `/app/{dashboard,tenants,workspaces,access,pending}`
- `pageGuard` + Access console

## 4. Keyingi (ruxsat bilan)

1. Platform Admin UI (`/app/platform/...`)
2. Media Management
3. Instagram OAuth
4. AI modules

## 5. Qoidalar

1. Media/Instagram/AI — ruxsatsiz yo‘q  
2. Boot/Angular downgrade yo‘q  
3. Secrets env’da; tokenlarni loglama  
4. Tenant ID faqat server-verified membership (`TenantContext`); JWT claim’lar har requestda qayta tekshiriladi  
5. DB migration — SQL Liquibase; migration tarixini buzma / drop-first prod’da yo‘q  
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
