"""
Тест интеграции с OpenCode (Space Bunny Free).

Поднимает mock-сервер, повторяющий API OpenCode, и проверяет,
что engine.py корректно строит запрос и разбирает ответ.

Запуск: py test_opencode.py
"""

import json
import os
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = 4399
BASE = f"http://127.0.0.1:{PORT}"

received: list[dict] = []


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # тишина

    def _json(self, code: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/api/info":
            self._json(200, {"version": "mock"})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length)
        data = json.loads(raw.decode("utf-8"))
        received.append(data)

        if self.path == "/api/experimental/generate":
            model = data.get("model") or {}
            # повторяем модель обратно - так видно, что запрос дошёл верно
            self._json(200, {
                "data": {
                    "text": (
                        f"[{model.get('providerID')}/{model.get('id')}"
                        f" variant={model.get('variant')}] "
                        f"prompt_chars={len(data.get('prompt', ''))}"
                    )
                }
            })
        else:
            self._json(404, {"error": "not found"})


def main() -> None:
    srv = HTTPServer(("127.0.0.1", PORT), Handler)
    t = threading.Thread(target=srv.serve_forever, daemon=True)
    t.start()
    print(f"mock OpenCode на {BASE}\n")

    # настраиваем движок на mock
    os.environ["OPENCODE_URL"] = BASE
    os.environ["OPENCODE_PROVIDER"] = "opencode"
    os.environ["OPENCODE_MODEL_ID"] = "space-bunny-free"
    os.environ["OPENCODE_VARIANT"] = "medium"

    import engine

    print("opencode_is_available():", engine.opencode_is_available())
    assert engine.opencode_is_available(), "mock-сервер не обнаружен"

    engine.load_engine()
    print("выбранный движок:", engine._ENGINE_NAME)
    print("загруженная модель:", engine.engine_info()["model_path"])
    assert engine._ENGINE_NAME == "opencode", "движок не переключился на OpenCode"

    messages = [
        {"role": "system", "content": "Ты BlackAI."},
        {"role": "user", "content": "Привет!"},
        {"role": "assistant", "content": "Здравствуй!"},
        {"role": "user", "content": "Что такое Python?"},
    ]

    out = "".join(engine.generate(messages, max_tokens=100, temperature=0.7))
    print("\nответ:", out.strip())
    assert "[opencode/space-bunny-free" in out, "модель в ответе не совпала"

    sent = received[-1]
    print("\nзапрос на сервер:")
    print("  model :", json.dumps(sent["model"], ensure_ascii=False))
    assert sent["model"]["id"] == "space-bunny-free"
    assert sent["model"]["providerID"] == "opencode"
    assert sent["model"]["variant"] == "medium"

    print("  prompt (начало):")
    print("   ", sent["prompt"][:80].replace("\n", " | "))
    assert "Что такое Python?" in sent["prompt"], "история диалога не ушла"
    assert sent["prompt"].rstrip().endswith("Ассистент:"), "нет хвоста промпта"

    srv.shutdown()
    print("\nВСЕ ТЕСТЫ OPENCODE ПРОЙДЕНЫ")


if __name__ == "__main__":
    main()