"""
Тест ИИ-движка BlackAI: проверяет /health, /chat (обычный и streaming).
Запуск: py test_engine.py
"""

import json
import urllib.request

BASE = "http://127.0.0.1:8000"


def post(path: str, payload: dict, stream: bool = False):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=data,
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    return urllib.request.urlopen(req, timeout=60)


def main() -> None:
    # 1. Health
    with urllib.request.urlopen(BASE + "/health", timeout=20) as resp:
        health = json.loads(resp.read().decode("utf-8"))
    print("[OK] /health ->", health)

    payload = {
        "model": "blackai-local",
        "messages": [
            {"role": "system", "content": "Ты BlackAI"},
            {"role": "user", "content": "Привет, кто ты?"},
        ],
        "temperature": 0.7,
        "max_tokens": 200,
    }

    # 2. Обычный ответ
    payload["stream"] = False
    with post("/chat", payload) as resp:
        result = json.loads(resp.read().decode("utf-8"))
    text = result["choices"][0]["message"]["content"]
    print("[OK] /chat (обычный) ->", text[:120])

    # Проверяем, что кириллица не побилась
    assert any("\u0400" <= ch <= "\u044f" for ch in text), "Кириллица повреждена!"
    print("[OK] Кириллица передаётся корректно (UTF-8)")

    # 3. Потоковый ответ (SSE)
    payload["stream"] = True
    chunks = []
    with post("/chat", payload) as resp:
        ctype = resp.headers.get("Content-Type", "")
        print("[INFO] Streaming Content-Type:", ctype)
        for raw in resp:
            line = raw.decode("utf-8").strip()
            if not line.startswith("data:"):
                continue
            chunk = line[5:].strip()
            if chunk == "[DONE]":
                break
            data = json.loads(chunk)
            choices = data.get("choices") or []
            if choices:
                piece = choices[0].get("delta", {}).get("content", "")
                if piece:
                    chunks.append(piece)

    streamed = "".join(chunks)
    print("[OK] /chat (streaming) ->", streamed[:120])
    assert streamed, "Пустой потоковый ответ!"
    assert len(chunks) > 1, f"Ожидался поток из нескольких чанков, получено {len(chunks)}"
    print(f"[OK] Пришло {len(chunks)} чанков - потоковая выдача работает")

    print("\nВСЕ ТЕСТЫ ПРОЙДЕНЫ")


if __name__ == "__main__":
    main()