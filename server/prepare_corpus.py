"""
Подготовка русского корпуса для обучения собственной модели BlackAI.

Берёт книги с Project Gutenberg, вырезает служебные заголовки,
оставляет только кириллический текст и превращает его в поток токенов.

Запуск:
    py prepare_corpus.py
Результат:
    corpus.txt  - по одной строке на предложение, слова разделены пробелом
"""

import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RAW_DIR = os.path.join(HERE, "corpus_raw")
WIKI_FILE = os.path.join(HERE, "wiki_raw.json")
OUT_FILE = os.path.join(HERE, "corpus.txt")

# Служебные маркеры Project Gutenberg
START_RE = re.compile(r"\*\*\*\s*START OF (THE|THIS) PROJECT GUTENBERG.*?\*\*\*", re.I | re.S)
END_RE = re.compile(r"\*\*\*\s*END OF (THE|THIS) PROJECT GUTENBERG.*?\*\*\*", re.I | re.S)

WORD_RE = re.compile(r"[А-Яа-яЁё]+(?:['\-][А-Яа-яЁё]+)*")
LATIN_RE = re.compile(r"[A-Za-z]")
CYRILLIC_RE = re.compile(r"[А-Яа-яЁё]")


def clean_book(path: str) -> str:
    with open(path, "rb") as f:
        raw = f.read()
    text = raw.decode("utf-8", errors="ignore")

    # Вырезаем заголовок/подвал Gutenberg
    m = START_RE.search(text)
    if m:
        text = text[m.end():]
    m = END_RE.search(text)
    if m:
        text = text[: m.start()]

    return text


def is_russian(line: str) -> bool:
    """Оставляем строки, где кириллицы заметно больше латиницы."""
    if not CYRILLIC_RE.search(line):
        return False
    cyr = len(CYRILLIC_RE.findall(line))
    lat = len(LATIN_RE.findall(line))
    return cyr >= lat * 2 and cyr >= 10


def main() -> None:
    chunks: list[str] = []

    # 1) Статьи Википедии из wiki_raw.json
    if os.path.isfile(WIKI_FILE):
        with open(WIKI_FILE, encoding="utf-8") as f:
            articles = json.load(f)
        for art in articles:
            text = art.get("text", "")
            # убираем служебные разметки и оглавления
            text = re.sub(r"==+[^=]+==+", " ", text)
            text = re.sub(r"\[\d+\]|\[источник[^\]]*\]|\[примечание[^\]]*\]", " ", text)
            chunks.append(text)
        print(f"  wiki_raw.json: {len(articles)} статей")

    # 2) Книги Gutenberg, если есть (русские)
    files = sorted(glob.glob(os.path.join(RAW_DIR, "*.txt")))
    for path in files:
        chunks.append(clean_book(path))
        print(f"  {os.path.basename(path)}: прочитан")

    if not chunks:
        print("Нет ни Википедии, ни книг. Сначала выполните:")
        print("  py fetch_corpus.py 1500")
        sys.exit(1)

    sentences: list[str] = []
    total_words = 0

    for text in chunks:
        for line in text.splitlines():
            if not is_russian(line):
                continue
            for part in re.split(r"(?<=[.!?…])\s+", line):
                words = WORD_RE.findall(part.lower())
                if len(words) < 3:
                    continue
                sentences.append(" ".join(words))
                total_words += len(words)

    with open(OUT_FILE, "w", encoding="utf-8") as f:
        for s in sentences:
            f.write(s + "\n")

    size_mb = os.path.getsize(OUT_FILE) / 1024 / 1024
    print(f"\nГотово: {len(sentences)} предложений, {total_words} слов, {size_mb:.1f} МБ")
    print(f"Файл: {OUT_FILE}")


if __name__ == "__main__":
    main()