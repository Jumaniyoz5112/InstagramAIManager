# Instagram AI Manager — PROJECT CONTEXT

> Loyihaning asosiy texnik konteksti. Cursor va inson bir xil arxitektura bo‘yicha ishlashi shart.

**Oxirgi yangilanish:** 2026-11-02  
**Mahsulot turi:** Multi-Tenant SaaS  
**Joriy bosqich:** Auth + Access + i18n (uz/ru/en) — **DONE**  
**To‘xtatilgan:** Media Management, Instagram publishing, AI Agent  

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
| CSRF on cookie refresh | DONE |
| CORS allowed origins | DONE |
| Register / Login / Refresh / Logout / Me | DONE |
| Workspace activate + membership check | DONE |
| Platform ADMIN vs tenant OWNER/ADMIN/MEMBER | DONE |
| Page-level Access (admin grants pages) | DONE |
| Cross-tenant isolation tests | DONE |

### Bootstrap admin (faqat config / log — UI’da ko‘rsatilmaydi)

- `admin@local.dev` / `0110` (`app.bootstrap.*`)

### Page Access

- Katalog: `app_pages` (DASHBOARD, TENANTS, WORKSPACES, ACCESS)
- Grant: `user_page_access`
- `PLATFORM_ADMIN` → barcha sahifalar avtomatik
- Yangi register → faqat `DASHBOARD` (default)
- Admin Access UI: `/app/access` — userlarga sahifa ochish/yopish
- API ham page gate: `/tenants`, `/workspaces` → tegishli page kerak
- Access API: `/api/v1/access/*` → faqat `PLATFORM_ADMIN`

### Auth API

- `POST /api/v1/auth/register|login|refresh|logout`
- `GET /api/v1/auth/me` (ichida `pages[]`)
- `POST /api/v1/auth/workspaces/{id}/activate`
- `GET/POST /api/v1/tenants`
- `GET/POST /api/v1/workspaces?tenantId=`
- `GET/PUT /api/v1/access/pages|users|users/{id}/pages`
- `GET /api/health`

### Liquibase SQL

- `001-app-meta.sql`
- `002-identity-tenant.sql`
- `003-app-pages.sql`
- Dev: `spring.liquibase.drop-first=true`

### i18n (BE ↔ UI)

- Tillar: **uz** (lotin, default), **ru**, **en**
- BE xatolik: `messageKey` (+ optional `messageArgs`), lokal matn yo‘q
- UI `I18nService` + `t` pipe — keyni tarjima qiladi
- Lang switcher: auth + main layout
- Envelope: `{ success, data, messageKey, messageArgs, timestamp }`

### UI

- Professional auth (parol hint yo‘q, schema dump yo‘q)
- Nav faqat ruxsat berilgan `pages` bo‘yicha
- Routes: `/auth/*`, `/app/{dashboard,tenants,workspaces,access,pending}`
- `pageGuard` + Access console

## 4. Keyingi (ruxsat bilan)

1. Media Management (tenant storage namespace)
2. Instagram OAuth (encrypt tokens)
3. AI modules

## 5. Qoidalar

1. Media/Instagram/AI — ruxsatsiz yo‘q  
2. Boot/Angular downgrade yo‘q  
3. Secrets env’da; tokenlarni loglama  
4. Tenant ID faqat server membership/JWT claim  
5. DB migration — SQL Liquibase  
6. Yangi UI sahifa → `app_pages` + Access orqali ochiladi  

## 6. Progress log

Batafsil kunlik jurnal: `.cursor/md/YYYY-MM-DD.md`

| Sana | Nima |
|------|------|
| 2026-11-02 | Monorepo, health, GitHub |
| 2026-11-02 | Foundation auth/JWT/dashboard |
| 2026-11-02 | Liquibase YAML → SQL; drop-first; register + isolation tests |
| 2026-11-02 | Tailwind UI: tenants/workspaces |
| 2026-11-02 | Page Access control (admin grants) + professional auth UI |
| 2026-11-02 | i18n uz/ru/en — BE messageKey, UI tarjima |
