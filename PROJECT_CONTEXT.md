# Instagram AI Manager — PROJECT CONTEXT

> Loyihaning asosiy texnik konteksti. Cursor va inson bir xil arxitektura bo‘yicha ishlashi shart.

**Oxirgi yangilanish:** 2026-11-02  
**Mahsulot turi:** Multi-Tenant SaaS  
**Joriy bosqich:** Backend Security + Authentication — **DONE**  
**To‘xtatilgan:** Media Management, Instagram publishing, AI Agent  

---

## 1. Mahsulot

Ko‘p mijozli Instagram AI Manager SaaS. Umumiy BE/AI, ma’lumotlar `tenant_id` bilan ajratiladi. Client yuborgan tenant ID’ga ishonilmaydi.

## 2. Stack (o‘zgartirilmasin)

- BE: Java 21, Spring Boot 4.1.2, `uzeduai.be`
- UI: Angular 22 standalone
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
| JWT secret via `JWT_SECRET` / `app.security.jwt.secret` | DONE |
| Register / Login / Refresh / Logout / Me | DONE |
| Workspace activate + membership check | DONE |
| Platform ADMIN vs tenant OWNER/ADMIN/MEMBER | DONE |
| Cross-tenant isolation tests | DONE |
| ApiResponse unified format | DONE |

### Bootstrap admin

- `admin@local.dev` / `0110`

### Auth API

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/workspaces/{id}/activate`
- `GET/POST /api/v1/tenants`
- `GET/POST /api/v1/workspaces?tenantId=`
- `GET /api/health`

### Liquibase SQL

- `001-app-meta.sql`
- `002-identity-tenant.sql` (refresh_tokens ichida tenant/workspace context)
- Dev: `spring.liquibase.drop-first=true` — har start’da baza tozalanadi

### UI

- `/auth/login`, `/auth/register`
- Auth guard, interceptors (Bearer + single-flight refresh + XSRF)
- Dashboard (protected)

## 4. Keyingi (ruxsat bilan)

1. Onboarding UX (workspace selector UI)
2. Media Management (tenant storage namespace)
3. Instagram OAuth (encrypt tokens)
4. AI modules

## 5. Qoidalar

1. Media/Instagram/AI — ruxsatsiz yo‘q  
2. Boot/Angular downgrade yo‘q  
3. Secrets env’da; tokenlarni loglama  
4. Tenant ID faqat server membership/JWT claim  
5. DB migration — SQL Liquibase  

## 6. Progress log

Batafsil kunlik jurnal: `.cursor/md/YYYY-MM-DD.md`

| Sana | Nima |
|------|------|
| 2026-11-02 | Monorepo, health, GitHub |
| 2026-11-02 | Foundation auth/JWT/dashboard |
| 2026-11-02 | Liquibase YAML → SQL; drop-first; register + isolation tests |
| 2026-11-02 | `.cursor/md/` kunlik jurnal ochildi |
