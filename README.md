# InstagramAIManager

Instagram kontentini boshqarish uchun monorepo: Spring Boot backend, Angular frontend va FastAPI AI service.

## Tuzilma

```text
InstagramAIManager/
├── BE/                 # Spring Boot (API, JPA, Security, Liquibase)
├── UI/                 # Angular frontend
├── ai-service/         # Python FastAPI
├── infrastructure/     # docker-compose (Postgres + Redis)
└── storage/            # yuklangan media
```

## Talablar

- JDK 21
- Node.js 20+
- PostgreSQL 16 (yoki Docker)
- Python 3.11+ (AI service uchun)

## Database

Local PostgreSQL:

- DB: `InstagramAIManager`
- User: `postgres`
- Password: `0110`
- Port: `5432`

Docker orqali:

```bash
cd infrastructure
docker compose up -d
```

## Backend (Spring Boot)

```bash
cd BE
./gradlew bootRun
```

API:

- `GET http://localhost:8080/api/health`
- `GET http://localhost:8080/api/ping`

CORS: `http://localhost:4200` ruxsat etilgan.

## Frontend (Angular)

```bash
cd UI
npm install
npm start
```

Dev server: `http://localhost:4200`

`proxy.conf.json` orqali `/api` so‘rovlari `http://localhost:8080` ga yuboriladi.

## AI service

```bash
cd ai-service
python -m venv .venv
# Windows
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## Ishga tushirish tartibi

1. Postgres (local yoki docker)
2. `BE` → `./gradlew bootRun`
3. `UI` → `npm start`
4. Brauzerda `http://localhost:4200` — backend holati ko‘rinadi
