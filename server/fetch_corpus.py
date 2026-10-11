"""
Загрузка русского корпуса для обучения модели BlackAI.

Берёт случайные статьи русской Википедии через открытый API
и сохраняет их обычным текстом. Википедия - свободная лицензия CC BY-SA.

Запуск:
    py fetch_corpus.py 800
Результат:
    wiki_raw.json  - сырые статьи
"""

import json
import os
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_FILE = os.path.join(HERE, "wiki_raw.json")

API = "https://ru.wikipedia.org/w/api.php"
# Wikimedia требует описательный User-Agent с контактом,
# иначе возвращает 429 (Too Many Requests)
UA = (
    "BlackAI-corpus-builder/1.0 "
    "(https://github.com/local/blackai; local-user@example.com) "
    "python-urllib/3.10"
)
DELAY = 1.5          # секунд между запросами
RETRIES = 6


def fetch_batch(count: int = 10) -> list[dict]:
    params = {
        "action": "query",
        "generator": "random",
        "grnnamespace": "0",      # только статьи, без служебных страниц
        "grnlimit": str(count),
        "prop": "extracts",
        "explaintext": "1",       # чистый текст без разметки
        "exlimit": "max",
        "format": "json",
        "formatversion": "2",
    }
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=90) as r:
        data = json.loads(r.read().decode("utf-8"))
    return data.get("query", {}).get("pages", []) or []


def fetch_with_retry(count: int) -> list[dict]:
    """Запрос с экспоненциальной паузой при 429/5xx."""
    for attempt in range(RETRIES):
        try:
            return fetch_batch(count)
        except urllib.error.HTTPError as exc:
            if exc.code in (429, 503, 502):
                wait = min(60, 5 * (2 ** attempt))
                print(f"    rate-limit ({exc.code}), жду {wait} с")
                time.sleep(wait)
                continue
            raise
        except Exception:  # noqa: BLE001
            time.sleep(3)
    return []


def main() -> None:
    target_articles = int(sys.argv[1]) if len(sys.argv) > 1 else 800

    # Продолжаем предыдущую загрузку, если файл уже есть
    collected: list[dict] = []
    seen: set[str] = set()
    if os.path.isfile(OUT_FILE):
        with open(OUT_FILE, encoding="utf-8") as f:
            collected = json.load(f)
        seen = {a["title"] for a in collected}
        print(f"Продолжаю: уже скачано {len(collected)} статей")

    total_chars = sum(len(a["text"]) for a in collected)
    batches = 0

    print(f"Качаю до {target_articles} статей русской Википедии...")

    while len(collected) < target_articles:
        batches += 1
        pages = fetch_with_retry(10)
        if not pages:
            if batches > 200:
                print("Слишком много неудачных попыток, останавливаюсь.")
                break
            time.sleep(5)
            continue

        for p in pages:
            title = p.get("title", "")
            text = p.get("extract", "")
            if not text or len(text) < 800:
                continue
            if title in seen:
                continue
            seen.add(title)
            collected.append({"title": title, "text": text})
            total_chars += len(text)

        # промежуточное сохранение - чтобы не потерять прогресс
        with open(OUT_FILE, "w", encoding="utf-8") as f:
            json.dump(collected, f, ensure_ascii=False)

        if batches % 10 == 0:
            print(f"  {len(collected)}/{target_articles} статей, "
                  f"{total_chars/1024/1024:.2f} МБ")

        time.sleep(DELAY)  # вежливо к API

    with open(OUT_FILE, "w", encoding="utf-8") as f:
        json.dump(collected, f, ensure_ascii=False)

    size_mb = os.path.getsize(OUT_FILE) / 1024 / 1024
    print(f"\nГотово: {len(collected)} статей, {total_chars/1024/1024:.2f} МБ текста")
    print(f"Файл: {OUT_FILE} ({size_mb:.1f} МБ)")


if __name__ == "__main__":
    main()