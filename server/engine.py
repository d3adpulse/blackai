"""
BlackAI Engine - локальный ИИ-движок для приложения BlackAI.

Запускается на вашем ПК, телефон подключается по локальной сети.
Поддерживает:
  1) OpenCode        (модель Space Bunny Free через opencode serve)
  2) llama-cpp-python  (GGUF-модель, быстро, без внешних сервисов)
  3) BlackAI-Mini    (собственная нейросеть на numpy)
  4) HuggingFace     (transformers)
  5) Fallback        (встроенный простой движок, работает всегда)

Запуск:
    py server.py
    или
    py -m uvicorn server:app --host 0.0.0.0 --port 8000
"""

import json
import os
import platform
import time
import threading
import urllib.error
import urllib.request
from typing import Any, Dict, Generator, List, Optional

MODEL_NAME = os.environ.get("BLACKAI_MODEL", "blackai-local")
MODEL_PATH = os.environ.get("BLACKAI_MODEL_PATH", "")  # путь к .gguf, если задать
CTX_SIZE = int(os.environ.get("BLACKAI_CTX", "4096"))
N_THREADS = int(os.environ.get("BLACKAI_THREADS", "0")) or None

# --- OpenCode / Space Bunny Free ---
# Требуется установленный OpenCode с запущенным сервером:
#     opencode serve --hostname 127.0.0.1 --port 4096
OPENCODE_URL = os.environ.get("OPENCODE_URL", "http://127.0.0.1:4096")
OPENCODE_MODEL_ID = os.environ.get("OPENCODE_MODEL_ID", "space-bunny-free")
OPENCODE_PROVIDER = os.environ.get("OPENCODE_PROVIDER", "opencode")
OPENCODE_VARIANT = os.environ.get("OPENCODE_VARIANT", "")  # low|medium|high|xhigh|max
# BLACKAI_ENGINE=opencode|llama|mini|fallback - принудительный выбор
FORCE_ENGINE = os.environ.get("BLACKAI_ENGINE", "").lower()

_HERE = os.path.dirname(os.path.abspath(__file__))
_MODELS_DIR = os.path.join(_HERE, "models")


def _auto_find_model() -> str:
    """
    Если путь не задан через переменную окружения - ищем .gguf в папке models/.
    Так модель подхватывается автоматически, без настройки.
    """
    if MODEL_PATH and os.path.isfile(MODEL_PATH):
        return MODEL_PATH
    if os.path.isdir(_MODELS_DIR):
        candidates = [
            os.path.join(_MODELS_DIR, f)
            for f in os.listdir(_MODELS_DIR)
            if f.lower().endswith(".gguf")
        ]
        # при нескольких моделях берём наименьшую по весу - быстрее на CPU
        if candidates:
            candidates.sort(key=lambda p: os.path.getsize(p))
            return candidates[0]
    return ""

_ENGINE_NAME = "fallback"
_LOCK = threading.Lock()
_LLM = None
_TRANSFORMERS_PIPE = None
_NEURAL = None
_LOADED_MODEL = ""   # реальное имя загруженной модели (для /health)


# ---------------------------------------------------------------------------
# Загрузка движка
# ---------------------------------------------------------------------------

def load_engine() -> None:
    """Пытается поднять реальную модель; при неудаче остаётся fallback."""
    global _ENGINE_NAME, _LLM, _TRANSFORMERS_PIPE, _LOADED_MODEL

    # 0) OpenCode / Space Bunny Free - если доступен локальный сервер
    if FORCE_ENGINE in ("", "opencode"):
        if opencode_is_available():
            _ENGINE_NAME = "opencode"
            _LOADED_MODEL = (
                f"{OPENCODE_PROVIDER}/{OPENCODE_MODEL_ID}"
                + (f" ({OPENCODE_VARIANT})" if OPENCODE_VARIANT else "")
            )
            print(f"[BlackAI] Движок: OpenCode -> {_LOADED_MODEL}")
            return
        if FORCE_ENGINE == "opencode":
            print(f"[BlackAI] OpenCode недоступен на {OPENCODE_URL}.")
            print("           Запустите: opencode serve --port 4096")

    model_file = _auto_find_model()

    # 1) llama-cpp
    if model_file:
        try:
            from llama_cpp import Llama

            _LLM = Llama(
                model_path=model_file,
                n_ctx=CTX_SIZE,
                n_threads=N_THREADS or max(1, (os.cpu_count() or 4) - 1),
                verbose=False,
            )
            _ENGINE_NAME = "llama.cpp"
            _LOADED_MODEL = os.path.basename(model_file)
            print(f"[BlackAI] Загружена GGUF-модель: {_LOADED_MODEL}")
            return
        except ImportError:
            print("[BlackAI] llama-cpp-python не установлен. Выполните:")
            print("           py -m pip install llama-cpp-python")
        except Exception as exc:  # noqa: BLE001
            print(f"[BlackAI] Не удалось загрузить модель: {exc}")

    # 2) Собственная нейросеть BlackAI-Mini (обучена на русском корпусе)
    if load_neural_model():
        return

    # 3) transformers
    try:
        import torch  # noqa: F401
        from transformers import pipeline

        name = MODEL_PATH or "sshleifer/tiny-gpt2"
        _TRANSFORMERS_PIPE = pipeline("text-generation", model=name)
        _ENGINE_NAME = "transformers"
        print(f"[BlackAI] Загружена модель transformers: {name}")
        return
    except Exception as exc:  # noqa: BLE001
        print(f"[BlackAI] transformers недоступен: {exc}")

    _ENGINE_NAME = "fallback"
    print("[BlackAI] Работаю в режиме fallback (без внешних моделей).")
    print("[BlackAI] Чтобы подключить настоящую модель, укажите BLACKAI_MODEL_PATH.")


def opencode_is_available() -> bool:
    """Проверяет, отвечает ли локальный сервер OpenCode."""
    try:
        req = urllib.request.Request(
            OPENCODE_URL.rstrip("/") + "/api/info",
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=3) as r:
            return r.status == 200
    except Exception:  # noqa: BLE001
        return False


def _build_prompt(messages: List[Dict[str, str]]) -> str:
    """
    Эндпоинт generate принимает одну строку, поэтому собираем
    из истории диалога связный текстовый промпт.
    """
    parts: List[str] = []
    for m in messages:
        role = m.get("role", "user")
        content = m.get("content", "")
        if not content:
            continue
        if role == "system":
            parts.append(content)
        elif role == "assistant":
            parts.append(f"Ассистент: {content}")
        else:
            parts.append(f"Пользователь: {content}")
    parts.append("Ассистент:")
    return "\n\n".join(parts)


def _opencode_generate(messages: List[Dict[str, str]]) -> str:
    """Один запрос к OpenCode, возвращает готовый текст ответа."""
    body: Dict[str, Any] = {"prompt": _build_prompt(messages)}
    model_ref: Dict[str, str] = {
        "id": OPENCODE_MODEL_ID,
        "providerID": OPENCODE_PROVIDER,
    }
    if OPENCODE_VARIANT:
        model_ref["variant"] = OPENCODE_VARIANT
    body["model"] = model_ref

    req = urllib.request.Request(
        OPENCODE_URL.rstrip("/") + "/api/experimental/generate",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=180) as r:
        data = json.loads(r.read().decode("utf-8"))
    return (data.get("data") or {}).get("text", "")


def load_neural_model() -> bool:
    global _ENGINE_NAME, _NEURAL, _LOADED_MODEL

    path = os.path.join(_HERE, "model_mini", "blackai_mini.npz")
    if not os.path.isfile(path):
        return False

    try:
        from train_model import BlackAINeuralLM

        _NEURAL = BlackAINeuralLM.load(path)
        _ENGINE_NAME = "blackai-mini"
        _LOADED_MODEL = "blackai-mini (собственная нейросеть)"
        print(f"[BlackAI] Загружена BlackAI-Mini "
              f"({len(_NEURAL.vocab)} слов в словаре).")
        return True
    except Exception as exc:  # noqa: BLE001
        print(f"[BlackAI] Не удалось загрузить BlackAI-Mini: {exc}")
        return False


# ---------------------------------------------------------------------------
# Генерация ответа
# ---------------------------------------------------------------------------

def _format_messages(messages: List[Dict[str, str]]) -> str:
    """Собирает единый текстовый промпт из истории диалога."""
    parts: List[str] = []
    for msg in messages:
        role = msg.get("role", "user")
        content = msg.get("content", "")
        if role == "system":
            parts.append(f"[Инструкции] {content}")
        elif role == "assistant":
            parts.append(f"BlackAI: {content}")
        else:
            parts.append(f"Пользователь: {content}")
    parts.append("BlackAI:")
    return "\n".join(parts)


def _seed_from_messages(messages: List[Dict[str, str]]) -> List[str]:
    """
    Превращает сообщения в стартовые слова для генерации.
    Neural-модель не понимает ролей, поэтому берём последние
    слова реплики пользователя - они задают тему ответа.
    """
    import re

    for msg in reversed(messages):
        if msg.get("role") == "user":
            words = re.findall(r"[А-Яа-яЁё]+", msg.get("content", "").lower())
            if words:
                return words[-10:]
    return ["это"]


def generate(
    messages: List[Dict[str, str]],
    max_tokens: int = 512,
    temperature: float = 0.7,
    stream: bool = False,
) -> Generator[str, None, None]:
    """Отдаёт генератор кусочков текста ответа."""
    global _ENGINE_NAME

    with _LOCK:
        if _ENGINE_NAME == "opencode":
            try:
                text = _opencode_generate(messages)
                # эндпоинт отдаёт текст целиком - режем на куски для потока
                for piece in text.split(" "):
                    yield piece + " "
                    time.sleep(0.01)
                return
            except Exception as exc:  # noqa: BLE001
                print(f"[BlackAI] Ошибка OpenCode: {exc}")
                yield from _fallback_reply(messages)
                return

        if _ENGINE_NAME == "blackai-mini" and _NEURAL is not None:
            try:
                seed = _seed_from_messages(messages)
                text = _NEURAL.generate(
                    seed,
                    max_len=min(max_tokens, 80),
                    temperature=max(temperature, 0.5),
                )
                for piece in text.split(" "):
                    yield piece + " "
                    time.sleep(0.01)
                return
            except Exception as exc:  # noqa: BLE001
                print(f"[BlackAI] Ошибка BlackAI-Mini: {exc}")

        if _ENGINE_NAME == "llama.cpp" and _LLM is not None:
            try:
                # create_chat_completion сам применяет chat-шаблон модели
                # (для Qwen/Llama instruct-моделей это обязательно)
                chat = [
                    {
                        "role": m.get("role", "user"),
                        "content": m.get("content", ""),
                    }
                    for m in messages
                    if m.get("content")
                ]

                result = _LLM.create_chat_completion(
                    messages=chat,
                    max_tokens=max_tokens,
                    temperature=temperature,
                    top_p=0.9,
                    repeat_penalty=1.05,
                    stream=True,
                )

                for chunk in result:
                    choices = chunk.get("choices") or [{}]
                    piece = choices[0].get("delta", {}).get("content")
                    if piece:
                        yield piece
                return
            except Exception as exc:  # noqa: BLE001
                print(f"[BlackAI] Ошибка генерации: {exc}")

        if _ENGINE_NAME == "transformers" and _TRANSFORMERS_PIPE is not None:
            prompt = _format_messages(messages)
            try:
                result = _TRANSFORMERS_PIPE(
                    prompt,
                    max_new_tokens=min(max_tokens, 256),
                    temperature=max(temperature, 0.01),
                    do_sample=temperature > 0,
                    return_full_text=False,
                )
                text = result[0]["generated_text"]
                # эмулируем потоковую выдачу по словам
                for word in text.split(" "):
                    yield word + " "
                return
            except Exception as exc:  # noqa: BLE001
                print(f"[BlackAI] Ошибка transformers: {exc}")

        # ---- Fallback-движок ----
        yield from _fallback_reply(messages)


def _fallback_reply(messages: List[Dict[str, str]]) -> Generator[str, None, None]:
    """
    Простой встроенный движок. Отвечает осмысленно на типовые запросы,
    чтобы приложение работало даже без установки тяжёлых моделей.
    """
    last_user = ""
    for msg in reversed(messages):
        if msg.get("role") == "user":
            last_user = msg.get("content", "").strip()
            break

    low = last_user.lower()

    if any(k in low for k in ("привет", "здравствуй", "хай", "hi", "hello")):
        reply = "Привет! Я BlackAI. ИИ-движок запущен на твоём компьютере."
    elif any(k in low for k in ("кто ты", "ты кто", "что ты умеешь")):
        reply = (
            "Я BlackAI - локальный ИИ-ассистент. Сейчас работаю в упрощённом "
            "режиме fallback. Чтобы получить полноценные ответы, установи "
            "реальную модель (см. README)."
        )
    elif any(k in low for k in ("пока", "до свидания", "бывай")):
        reply = "До связи! Хорошего дня."
    elif "?" in last_user:
        reply = (
            "Это хороший вопрос. Сейчас я работаю в упрощённом режиме и отвечаю "
            "по шаблону, потому что нейросеть ещё не подключена. "
            "Подключи GGUF-модель - и я дам полноценный развёрнутый ответ."
        )
    elif any(k in low for k in ("спасибо", "благодар")):
        reply = "Пожалуйста! Рад помочь."
    elif any(k in low for k in ("как дела", "как ты")):
        reply = "У меня всё отлично, спасибо, что спросили! Я готов помогать."
    else:
        reply = (
            f"Принял: «{last_user}». Сейчас я работаю в упрощённом режиме без "
            "нейросети, поэтому отвечаю по шаблону. Подключи GGUF-модель, "
            "и я смогу отвечать полноценно."
        )

    # имитация набора текста
    for piece in reply.split(" "):
        yield piece + " "
        time.sleep(0.02)


def engine_info() -> Dict[str, Any]:
    return {
        "engine": _ENGINE_NAME,
        "model_path": _LOADED_MODEL or MODEL_PATH or MODEL_NAME,
        "context": CTX_SIZE,
    }