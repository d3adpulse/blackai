"""
BlackAI Server - HTTP-сервер ИИ-движка для приложения BlackAI.

Эндпоинты:
    GET  /health   - проверка связи (приложение вызывает при старте)
    GET  /models   - список доступных моделей
    POST /chat     - отправка сообщений, поддерживает streaming (SSE)

Формат /chat (OpenAI-совместимый):
    {
      "model": "blackai-local",
      "messages": [{"role": "user", "content": "Привет"}],
      "temperature": 0.7,
      "max_tokens": 512,
      "stream": true
    }

Запуск:
    py server.py
"""

import json
import socket
from typing import Any, Dict

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

import engine

HOST = "0.0.0.0"   # слушаем все интерфейсы, чтобы телефон по WiFi достучался
PORT = 8000

app = FastAPI(title="BlackAI Engine", version="1.0")

# Телефон обращается напрямую - CORS для удобства отладки из браузера
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class Message(BaseModel):
    role: str = Field(default="user")
    content: str = Field(default="")


class ChatRequest(BaseModel):
    model: str = "blackai-local"
    messages: list[Message] = Field(default_factory=list)
    temperature: float = 0.7
    max_tokens: int = 512
    stream: bool = True


@app.on_event("startup")
def startup() -> None:
    engine.load_engine()
    print(f"[BlackAI] Сервер запущен: http://0.0.0.0:{PORT}")
    print(f"[BlackAI] Введите в приложении IP этого компьютера в настройках.")


@app.get("/health")
def health() -> Dict[str, Any]:
    """Проверка связи - приложение показывает зелёный индикатор при успехе."""
    info = engine.engine_info()
    return {
        "status": "ok",
        "model": "blackai-local",
        "engine": info["engine"],
        "device": socket.gethostname(),
        "model_path": info["model_path"],
        "context": info["context"],
    }


@app.get("/models")
def models() -> Dict[str, Any]:
    return {"data": [{"id": "blackai-local", "engine": engine.engine_info()["engine"]}]}


@app.post("/chat")
def chat(req: ChatRequest):
    """Основной эндпоинт. Поддерживает как SSE-поток, так и обычный JSON."""
    messages = [{"role": m.role, "content": m.content} for m in req.messages]

    if req.stream:
        def event_stream():
            yield f"data: {json.dumps({'model': req.model})}\n\n"
            try:
                for piece in engine.generate(
                    messages,
                    max_tokens=req.max_tokens,
                    temperature=req.temperature,
                    stream=True,
                ):
                    chunk = {
                        "choices": [
                            {"index": 0, "delta": {"content": piece}, "finish_reason": None}
                        ]
                    }
                    yield f"data: {json.dumps(chunk, ensure_ascii=False)}\n\n"
            except Exception as exc:  # noqa: BLE001
                err = {
                    "choices": [
                        {"index": 0, "delta": {"content": f"\n[ошибка: {exc}]"}}
                    ]
                }
                yield f"data: {json.dumps(err, ensure_ascii=False)}\n\n"

            yield f"data: {json.dumps({'choices': [{'index': 0, 'delta': {}, 'finish_reason': 'stop'}]})}\n\n"
            yield "data: [DONE]\n\n"

        return StreamingResponse(
            event_stream(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )

    text = "".join(
        engine.generate(
            messages,
            max_tokens=req.max_tokens,
            temperature=req.temperature,
            stream=False,
        )
    )
    return {
        "model": req.model,
        "choices": [{"index": 0, "message": {"role": "assistant", "content": text}}],
    }


def main() -> None:
    print("=" * 50)
    print("  BlackAI Engine v1.0")
    print("=" * 50)
    uvicorn.run(app, host=HOST, port=PORT, log_level="info")


if __name__ == "__main__":
    main()