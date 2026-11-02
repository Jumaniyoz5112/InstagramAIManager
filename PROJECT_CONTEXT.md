# Instagram AI Manager — PROJECT CONTEXT

> Loyihaning asosiy texnik konteksti. Cursor va inson bir xil arxitektura bo‘yicha ishlashi shart.

**Oxirgi yangilanish:** 2026-11-02  
**Mahsulot turi:** Multi-Tenant SaaS  
**Joriy bosqich:** Foundation — Security + Auth + Workspace (implementatsiya bajarildi)  
**To‘xtatilgan:** Media Management, Instagram publishing, AI Agent  

---

## 1. Mahsulot

Ko‘p mijozli Instagram AI Manager SaaS. Umumiy BE/AI, ma’lumotlar `tenant_id` bilan ajratiladi. Client yuborgan tenant ID’ga ishonilmaydi.

## 2. Stack (o‘zgartirilmasin)

- BE: Java 21, Spring Boot 4.1.2, `uzeduai.be`
- UI: Angular 22 standalone
- DB: PostgreSQL + Liquibase
- Redis/Ollama: hozir tegilmagan

## 3. Joriy progress

| Bosqich | Holat |
|---------|--------|
| F1 BE package + Liquibase identity/tenant/workspace | **DONE** |
| F2 JWT + refresh cookie + CSRF + Auth APIs | **DONE** |
| F3 Angular core/layout/features auth+dashboard | **DONE** |
| F4 Login → activate workspace → dashboard | **DONE** (API test) |
| Media Management | to‘xtatilgan |
| Instagram OAuth impl | to‘xtatilgan |

### Bootstrap admin

- Email: `admin@local.dev` (env: `BOOTSTRAP_ADMIN_EMAIL`)
- Password: `ChangeMe0110!` (env: `BOOTSTRAP_ADMIN_PASSWORD`)
- Demo tenant `demo` + Demo Workspace

### Auth contract

- `POST /api/v1/auth/login` — public, CSRF ignore
- `POST /api/v1/auth/refresh` — cookie + CSRF
- `POST /api/v1/auth/logout` — auth + CSRF, revoke
- `GET /api/v1/auth/me` — Bearer
- `POST /api/v1/auth/workspaces/{id}/activate` — Bearer, membership check
- `GET/POST /api/v1/tenants`, `GET/POST /api/v1/workspaces`
- `GET /api/health` — public (saqlangan)

### Token siyosati

- Access JWT — Angular memory (localStorage yo‘q)
- Refresh — HttpOnly cookie `iam_refresh`, DB’da SHA-256 hash, rotation
- CSRF — `XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header
- Tenant/workspace — JWT claim (activate dan keyin)

### Response format

```json
{ "success": true, "data": {}, "message": null, "timestamp": "..." }
```

## 4. Keyingi ruxsat bilan

1. Tenant isolation integration test (cross-tenant 403)
2. Register / onboarding UI
3. Media Management (tenant storage namespace)
4. Instagram package (encrypt tokens) — impl keyin

## 5. Qoidalar

1. Media/Instagram/AI — ruxsatsiz yo‘q  
2. Boot/Angular downgrade yo‘q  
3. Secrets env’da  
4. Har biznes modul: controller/service/repository/entity/dto  
5. Bo‘sh media/content/ai package’lar hali yaratilmagan (keraksiz bo‘sh class yo‘q)

## 6. Progress log

| Sana | Nima |
|------|------|
| 2026-11-02 | Monorepo, health bog‘lash, GitHub |
| 2026-11-02 | Media reja → to‘xtatildi |
| 2026-11-02 | SaaS foundation: security, JWT, Angular auth/dashboard |
