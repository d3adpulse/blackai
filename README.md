# BlackAI

ИИ-чат для Android. Приложение на **Kotlin** (Jetpack Compose), ИИ-движок на **Python** запускается на вашем ПК.

---

## Как это работает

```
┌──────────────────────┐         Wi-Fi / локальная сеть         ┌──────────────────────┐
│  Телефон (Android)   │  ────────  http://192.168.1.7:8000  ──→ │   ПК (Python)        │
│  Kotlin + Compose    │                                       │  FastAPI + Qwen2.5   │
│  BlackAI.apk         │  ←────────  ответ (SSE, потоком)  ───── │  llama.cpp           │
└──────────────────────┘                                       └──────────────────────┘
```

Телефон шлёт текст на ПК по локальной сети, ПК генерирует ответ моделью и возвращает его **потоком** — текст появляется в чате по мере генерации.

---

## Быстрый старт

### 1. Запустите ИИ-движок на ПК

Двойной клик по файлу:

```
server\start_server.bat
```

Либо вручную:

```bash
cd server
py -m pip install -r requirements.txt
py server.py
```

При старте сервер сам найдёт модель в папке `server\models\` и загрузит её.

> **Важно:** окно с сервером должно оставаться открытым, пока вы пользуетесь приложением.

### 2. Узнайте IP-адрес ПК

```bash
ipconfig
```

Найдите строку `IPv4-адрес` — например `192.168.1.7`.

### 3. Установите приложение

Скопируйте `BlackAI-1.0.apk` на телефон и откройте его (потребуется разрешить установку из неизвестных источников).

### 4. Подключитесь

В приложении: **шестерёнка** (настройки) → введите IP и порт `8000` → **Сохранить и проверить**.

Должен загореться зелёный индикатор **«на связи»**.

---

## Установка из репозитория

Модель ИИ **не хранится в git** (1 ГБ при лимите GitHub в 100 МБ на файл) — её нужно скачать отдельно.

```bash
git clone https://github.com/d3adpulse/blackai.git
cd blackai

# 1. Скачайте модель (1065 МБ) в папку server\models\
#    https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
#    Файл: qwen2.5-1.5b-instruct-q4_k_m.gguf

# 2. Укажите путь к SDK
copy local.properties.example local.properties
#    и пропишите свой путь в sdk.dir

# 3. Запустите сервер
cd server
py -m pip install -r requirements.txt
py -m pip install llama-cpp-python --only-binary=:all: --extra-index-url https://abetlen.github.io/llama-cpp-python/whl/cpu
run_server.bat
```

Что исключено из git и почему:

| Путь | Причина |
|------|---------|
| `server/models/` | GGUF-модель 1065 МБ — лимит GitHub 100 МБ |
| `*.apk`, `app/build/` | Собираются из исходников, в Releases |
| `app/blackai.keystore` | Ключ подписи — секрет, генерируется заново |
| `local.properties` | Абсолютный путь к SDK конкретного компьютера |
| `server/corpus*.txt` | Корпус для обучения, качается скриптом |

---

## ИИ-модель

Движок настроен на **Qwen2.5-1.5B-Instruct** (квантизация Q4_K_M, **1065 МБ**).
Модель лежит в `server\models\qwen2.5-1.5b-instruct-q4_k_m.gguf` и подхватывается автоматически.

Qwen отлично знает русский язык — отвечает осмысленно, пишет тексты, считает, объясняет.

Примеры реальных ответов движка:

| Вопрос | Ответ |
|--------|-------|
| Кто ты? | Меня зовут BlackAI, я AI-ассистент на телефоне. |
| Что такое Python? | Python — это язык программирования... (структурированный ответ со списком) |
| Посчитай 25 плюс 17 | 25 плюс 17 равно 42. |

### Как выбирается движок

1. **OpenCode / Space Bunny Free** — если запущен `opencode serve`
2. **llama.cpp** — если в `server\models\` есть `.gguf` (сейчас это Qwen)
3. **BlackAI-Mini** — собственная нейросеть (см. ниже)
4. **transformers** — если установлены `torch` + `transformers`
5. **fallback** — встроенный шаблонный режим, работает всегда

Подключить другую модель: положите `.gguf` в `server\models\` — сервер подхватит сам. Либо явно:

```bash
set BLACKAI_MODEL_PATH=C:\models\другая-модель.gguf
py server.py
```

---

## Space Bunny Free (через OpenCode)

В качестве «базы» можно использовать модель **Space Bunny Free** из OpenCode.
Она бесплатная и заметно сильнее локальной Qwen, но требует работающего OpenCode.

### Как включить

**1. Установите OpenCode** и запустите его сервер в отдельном окне:

```bash
opencode serve --port 4096
```

**2. Запустите BlackAI** — `server\start_server.bat` сам обнаружит OpenCode
и переключится на Space Bunny Free.

В окне сервера будет:

```
[Space Bunny Free] Server is up - using model opencode/space-bunny-free
[BlackAI] Движок: OpenCode -> opencode/space-bunny-free (medium)
```

### Настройки

Переменные окружения:

| Переменная | По умолчанию | Значение |
|---|---|---|
| `OPENCODE_URL` | `http://127.0.0.1:4096` | адрес сервера OpenCode |
| `OPENCODE_PROVIDER` | `opencode` | провайдер модели |
| `OPENCODE_MODEL_ID` | `space-bunny-free` | идентификатор модели |
| `OPENCODE_VARIANT` | `medium` | low, medium, high, xhigh, max |
| `BLACKAI_ENGINE` | авто | `opencode`, `llama`, `mini`, `fallback` |
| `BLACKAI_USE_OPENCODE` | — | `0` = всегда локальный Qwen |

Например, вариант `high` для более глубоких рассуждений:

```bash
set OPENCODE_VARIANT=high
py server.py
```

### Как это работает

Телефон → BlackAI-сервер (порт 8000) → OpenCode API → модель Space Bunny Free → ответ обратно.

Сервер вызывает эндпоинт `POST /api/experimental/generate`, собирая историю
диалога в текстовый промпт. Ответ приходит целиком и разбивается на куски
для плавного потока в приложении.

Если OpenCode не запущен — сервер автоматически переключается на локальный Qwen,
так что приложение не перестаёт работать.

Проверить интеграцию без OpenCode:

```bash
cd server
py test_opencode.py
```

---

## Собственная нейросеть BlackAI-Mini

В комплекте есть **собственная языковая модель, написанная с нуля на numpy** — без PyTorch, TensorFlow и внешних библиотек. Все слои, градиенты и оптимизатор Adam написаны вручную в `train_model.py`.

Архитектура — классическая нейросетевая языковая модель:

```
слова → [Embedding 96] → склейка контекста (10 слов)
      → [Dense 192 + tanh] → [Dense + softmax] → вероятности слов
```

Обучение: negative sampling + Adam (как в word2vec), ~590 тыс. параметров.

```bash
cd server
py fetch_corpus.py 4000      # скачать статьи Википедии
py prepare_corpus.py         # очистить корпус
py train_model.py            # обучить (30–60 минут)
```

**Честно о качестве:** на 8 ГБ RAM и 4 ядрах без GPU эта модель выдаёт связный русский текст в стиле корпуса, но заметно слабее Qwen и не понимает вопросы. Основной движок — Qwen; BlackAI-Mini идёт как запасной вариант и как пример полностью собственной реализации.

---

## Готовые APK

| Файл | Размер | Для чего |
|------|--------|----------|
| `BlackAI-1.0.apk` | 12 МБ | основной, ставить на телефон |
| `BlackAI-1.0-debug.apk` | 18 МБ | для отладки, не нужен |

---

## Пересборка APK

Двойной клик по `build_apk.bat`, или вручную:

```bash
gradlew.bat assembleRelease
```

Готовый файл: `app\build\outputs\apk\release\app-release.apk`

---

## Возможности приложения

- **Потоковый вывод** — ответ печатается по мере генерации, кнопка «стоп» прерывает
- **Фон приложения** — 8 градиентов: чёрный, графит, космос, неон, океан, лес, вино, закат
- **Настройки генерации** — температура (0–2), максимум токенов (64–4096)
- **Системный промпт** — меняете поведение ИИ под свои задачи
- **История диалога** — полная переписка с моделью
- **Тёмная тема** — AMOLED-чёрный, бережёт батарею

---

## Структура проекта

```
Новая папка (12)\
├── build_apk.bat              сборка APK
├── settings.gradle.kts
├── build.gradle.kts
├── gradlew.bat / gradlew      Gradle Wrapper
│
├── app\                       Android-приложение (Kotlin)
│   ├── build.gradle.kts
│   ├── blackai.keystore       ключ подписи
│   └── src\main\java\com\blackai\app\
│       ├── MainActivity.kt
│       ├── data\
│       │   ├── AiClient.kt        HTTP-клиент (OkHttp + SSE)
│       │   ├── SettingsRepository.kt
│       │   └── Models.kt
│       └── ui\
│           ├── ChatViewModel.kt
│           ├── screens\       чат, настройки, пузырьки сообщений
│           └── theme\         цвета, градиенты, тема
│
└── server\                    ИИ-движок (Python)
    ├── server.py              FastAPI: /health, /chat
    ├── engine.py              выбор движка и генерация
    ├── test_engine.py         проверка работы движка
    ├── requirements.txt
    ├── start_server.bat       запуск в один клик
    ├── models\                GGUF-модели (qwen2.5-1.5b)
    ├── train_model.py         своя нейросеть на numpy
    ├── fetch_corpus.py        загрузка корпуса (Википедия)
    └── prepare_corpus.py      очистка корпуса
```

---

## Технологии

**Приложение:** Kotlin 2.2, Jetpack Compose, Material 3, OkHttp, DataStore, корутины Flow, MVVM
**Движок:** Python 3.10+, FastAPI, Uvicorn, llama.cpp (Qwen2.5), numpy

Требования к телефону: **Android 8.0 (API 26)** и выше.
Требования к ПК: 8 ГБ RAM, любой 4-ядерный CPU (GPU не требуется).

---

## Если не подключается

1. **Телефон и ПК в одной сети** — оба должны быть в одном Wi-Fi
2. **Проверьте IP** — в настройках приложения должен быть адрес из `ipconfig`
3. **Порт 8000 свободен** — если занят, поменяйте `PORT` в `server.py` и в приложении
4. **Брандмауэр Windows** — разрешите Python через брандмауэр, иначе телефон не достучится
5. **Проверить вручную** — в браузере откройте `http://<IP>:8000/health`, должен появиться JSON