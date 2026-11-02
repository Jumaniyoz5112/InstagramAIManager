# Instagram AI Manager — PROJECT CONTEXT

> Loyihaning asosiy texnik konteksti. Cursor va inson bir xil arxitektura bo‘yicha ishlashi shart.

**Oxirgi yangilanish:** 2026-11-02  
**Mahsulot turi:** Multi-Tenant SaaS  
**Joriy bosqich:** Auth Phase-1 security hardening (C1/C2/H3/H4) — **DONE**  
**To‘xtatilgan:** Media Management, Instagram publishing, AI Agent  
**Keyingi (ruxsat bilan):** H1 Platform Admin tenant list, H2 TenantContext  

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

### Auth API

- `POST /api/v1/auth/register|login|refresh|logout`
- `GET /api/v1/auth/me` (ichida `pages[]`)
- `POST /api/v1/auth/workspaces/{id}/activate`
- Refresh/logout: CSRF required; logout permitAll (cookie bilan)
- `GET/POST /api/v1/tenants`
- `GET/POST /api/v1/workspaces?tenantId=`
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

1. H1 — PLATFORM_ADMIN barcha tenantlarni ko‘ra olsin
2. H2 — TenantContext / isolation hardening
3. Media Management
4. Instagram OAuth
5. AI modules

## 5. Qoidalar

1. Media/Instagram/AI — ruxsatsiz yo‘q  
2. Boot/Angular downgrade yo‘q  
3. Secrets env’da; tokenlarni loglama  
4. Tenant ID faqat server membership/JWT claim  
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
