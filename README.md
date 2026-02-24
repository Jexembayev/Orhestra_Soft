# Orhestra — Distributed Optimization Platform

Orhestra — система для распределённого запуска оптимизационных алгоритмов. Coordinator управляет заданиями и раздаёт задачи SPOT-воркерам.

## Архитектура

```
┌──────────────────────────────────────────────┐
│             Coordinator Server               │
│  HTTP API (Netty) → Services → H2 Database   │
│  Scheduler: TaskReaper + SpotReaper           │
└──────────┬──────────────────┬────────────────┘
           │ /internal/v1/*   │ /api/v1/*
     ┌─────┴─────┐      ┌────┴────┐
     │   SPOT    │      │ Client  │
     │  Workers  │      │ (Plugin)│
     └───────────┘      └─────────┘
```

## Быстрый старт

### Требования

- **Java** 17+
- **Maven** 3.9+

### Запуск координатора

```bash
# Из корня проекта (запускает координатор + JavaFX UI)
mvn javafx:run
```

Сервер стартует на `http://localhost:8080` (по умолчанию).

### Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `ORHESTRA_PORT` | `8080` | Порт сервера |
| `ORHESTRA_DB_URL` | `jdbc:h2:file:./data/orhestra;...` | URL базы данных |
| `ORHESTRA_AGENT_KEY` | *(нет)* | Ключ аутентификации SPOT-агентов |
| `ORHESTRA_MAX_ATTEMPTS` | `3` | Макс. кол-во попыток на задачу |

---

## Использование через curl

### 1. Проверить здоровье
```bash
curl http://localhost:8080/api/v1/health | jq .
```

### 2. Создать задание (Job)
```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jarPath": "/path/to/optimizer.jar",
    "mainClass": "com.example.OptRunner",
    "algorithms": ["PSO", "GA"],
    "iterations": {"min": 100, "max": 300, "step": 100},
    "agents": {"min": 10, "max": 30, "step": 10},
    "dimension": {"min": 2, "max": 4, "step": 2}
  }'
```
Ответ: `{"success": true, "jobId": "...", "totalTasks": 18}`

### 3. Посмотреть статус
```bash
curl http://localhost:8080/api/v1/jobs/{jobId} | jq .
```

### 4. Получить результаты
```bash
curl http://localhost:8080/api/v1/jobs/{jobId}/results | jq .
```
Ответ содержит `algorithm`, `iterations`, `agents`, `dimension`, `fopt`, `runtimeMs` для каждой задачи.

### 5. Посмотреть SPOT-ы
```bash
curl http://localhost:8080/api/v1/spots | jq .
```

---

## Использование через плагин

1. Запустить координатор (см. выше).
2. В плагине указать адрес координатора (`http://localhost:8080`).
3. Выбрать алгоритмы, настроить ranges параметров.
4. Нажать «Run» — плагин отправит `POST /api/v1/jobs`.
5. Результаты отобразятся в UI: таблица задач и график `fopt`.

---

## Запуск тестов

```bash
mvn test
```

Тесты включают:
- **Unit-тесты**: маппинг DTO, модели, валидация запросов
- **Интеграционные**: полный flow (create job → claim → complete → verify results)

---

## Подробная документация

Полный API-контракт, smoke-test скрипт и troubleshooting: [docs/COORDINATOR_V1.md](docs/COORDINATOR_V1.md)
