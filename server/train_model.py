"""
BlackAI-Mini: собственная нейросетевая языковая модель.

Реализована с нуля на numpy, без PyTorch/TensorFlow.

Архитектура (классическая нейросетевая языковая модель,
по идее Bengio et al. 2003 / word2vec):

    слова -> [Embedding] -> склеиваем контекст
          -> [Dense + tanh] -> [Dense + softmax] -> распределение по словарю

Все слои, градиенты и оптимизатор Adam написаны вручную.

Запуск обучения:
    py train_model.py
"""

import json
import os
import time

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS_FILE = os.path.join(HERE, "corpus.txt")
MODEL_DIR = os.path.join(HERE, "model_mini")

# ---------------- Параметры модели ----------------
CONTEXT = 10       # сколько слов смотрим назад
EMBED_DIM = 96     # размерность эмбеддинга
HIDDEN = 192       # скрытый слой
MIN_COUNT = 4      # слова реже этого не берём в словарь
NEG_SAMPLES = 5    # примеров negative sampling на один положительный
BATCH = 64
EPOCHS = 12
LEARNING_RATE = 0.002
TRAIN_LIMIT = 400_000  # сколько токенов берём из корпуса

np.random.seed(42)
rng = np.random.default_rng(42)


# ================= Загрузка корпуса =================

def load_tokens() -> list[str]:
    if not os.path.isfile(CORPUS_FILE):
        raise SystemExit(
            "Нет корпуса! Сначала выполните:\n"
            "  py prepare_corpus.py"
        )
    tokens: list[str] = []
    with open(CORPUS_FILE, encoding="utf-8") as f:
        for line in f:
            tokens.extend(line.split())
            if len(tokens) >= TRAIN_LIMIT:
                break
    return tokens


def build_vocab(tokens: list[str]) -> tuple[list[str], dict[str, int]]:
    counts: dict[str, int] = {}
    for t in tokens:
        counts[t] = counts.get(t, 0) + 1

    words = sorted(
        [w for w, c in counts.items() if c >= MIN_COUNT],
        key=lambda w: -counts[w],
    )
    print(f"  уникальных слов всего: {len(counts)}")
    print(f"  в словаре (частота >= {MIN_COUNT}): {len(words)}")

    vocab = ["<unk>"] + words
    index = {w: i for i, w in enumerate(vocab)}
    return vocab, index


# ================= Модель =================

class BlackAINeuralLM:
    """
    Параметры:
        W_emb  (vocab, embed)  - входные эмбеддинги
        W1     (embed*ctx, hid) - скрытый слой
        b1     (hid,)
        W2     (hid, vocab)     - выходной слой (предсказание слова)
        b2     (vocab,)
    """

    def __init__(self, vocab_size: int):
        v, e, h = vocab_size, EMBED_DIM, HIDDEN
        # Xavier/He инициализация
        self.W_emb = rng.normal(0, 0.05, size=(v, e)).astype(np.float32)
        self.W1 = rng.normal(0, np.sqrt(2.0 / (e * CONTEXT)), size=(e * CONTEXT, h)).astype(np.float32)
        self.b1 = np.zeros(h, dtype=np.float32)
        self.W2 = rng.normal(0, 0.05, size=(h, v)).astype(np.float32)
        self.b2 = np.zeros(v, dtype=np.float32)

        self.params = [self.W_emb, self.W1, self.b1, self.W2, self.b2]

        # Состояние Adam
        self.m = [np.zeros_like(p) for p in self.params]
        self.v = [np.zeros_like(p) for p in self.params]
        self.step_count = 0

    # ---- прямое распространение ----
    def forward(self, context_idx: np.ndarray, targets: np.ndarray | None = None):
        """
        context_idx: (B, CONTEXT) - индексы слов контекста
        targets:     (B,) или (B, K) - правильные ответы (+ негативные)
        Возвращает логиты и кэш для backward.
        """
        B = context_idx.shape[0]

        # 1. Эмбеддинги -> (B, CONTEXT, EMBED)
        emb = self.W_emb[context_idx]              # (B, C, E)
        # 2. Склеиваем контекст в один вектор
        x = emb.reshape(B, CONTEXT * EMBED_DIM)    # (B, C*E)

        # 3. Скрытый слой + tanh
        z = x @ self.W1 + self.b1                  # (B, H)
        h_act = np.tanh(z)                         # (B, H)

        # 4. Выходной слой
        logits = h_act @ self.W2 + self.b2         # (B, V)

        cache = (context_idx, emb, x, z, h_act, logits, targets)
        return logits, cache

    # ---- обратное распространение ----
    def backward(self, cache, grad_logits: np.ndarray):
        """Считает градиенты параметров по grad_logits."""
        context_idx, emb, x, z, h_act, logits, targets = cache
        B = context_idx.shape[0]

        gW2 = h_act.T @ grad_logits                 # (H, V)
        gb2 = grad_logits.sum(axis=0)               # (V,)

        g_h = grad_logits @ self.W2.T               # (B, H)

        # tanh': 1 - tanh^2
        g_z = g_h * (1.0 - h_act * h_act)           # (B, H)

        gW1 = x.T @ g_z                             # (C*E, H)
        gb1 = g_z.sum(axis=0)                       # (H,)

        g_x = g_z @ self.W1.T                       # (B, C*E)
        g_emb = g_x.reshape(B, CONTEXT, EMBED_DIM)  # (B, C, E)

        # Градиент по таблице эмбеддингов: суммируем по всем позициям контекста
        gW_emb = np.zeros_like(self.W_emb)
        np.add.at(gW_emb, context_idx.ravel(), g_emb.reshape(-1, EMBED_DIM))

        grads = [gW_emb, gW1, gb1, gW2, gb2]
        return grads

    # ---- шаг оптимизатора Adam ----
    def adam_step(self, grads: list[np.ndarray], lr: float, clip: float = 5.0):
        # Обрезка градиентов, чтобы не взорваться
        total = np.sqrt(sum(float((g * g).sum()) for g in grads))
        if total > clip:
            scale = clip / (total + 1e-6)
            grads = [g * scale for g in grads]

        self.step_count += 1
        b1, b2, eps = 0.9, 0.999, 1e-8

        for i, (p, g) in enumerate(zip(self.params, grads)):
            self.m[i] = b1 * self.m[i] + (1 - b1) * g
            self.v[i] = b2 * self.v[i] + (1 - b2) * (g * g)

            m_hat = self.m[i] / (1 - b1 ** self.step_count)
            v_hat = self.v[i] / (1 - b2 ** self.step_count)

            p -= lr * m_hat / (np.sqrt(v_hat) + eps)

    # ---- сохранение / загрузка ----
    def save(self, path: str, vocab: list[str]):
        np.savez_compressed(
            path,
            W_emb=self.W_emb, W1=self.W1, b1=self.b1,
            W2=self.W2, b2=self.b2,
        )
        meta = {
            "vocab": vocab,
            "context": CONTEXT,
            "embed_dim": EMBED_DIM,
            "hidden": HIDDEN,
        }
        with open(path.replace(".npz", ".json"), "w", encoding="utf-8") as f:
            json.dump(meta, f, ensure_ascii=False)

    @classmethod
    def load(cls, path: str):
        data = np.load(path)
        with open(path.replace(".npz", ".json"), encoding="utf-8") as f:
            meta = json.load(f)

        vocab = meta["vocab"]
        m = cls.__new__(cls)
        m.W_emb = data["W_emb"]; m.W1 = data["W1"]; m.b1 = data["b1"]
        m.W2 = data["W2"]; m.b2 = data["b2"]
        m.params = [m.W_emb, m.W1, m.b1, m.W2, m.b2]
        m.vocab = vocab
        m.vocab_index = {w: i for i, w in enumerate(vocab)}
        return m

    # ---- генерация текста ----
    def generate(self, seed_words: list[str], max_len: int = 60,
                 temperature: float = 0.9, rng_gen=None) -> str:
        g = rng_gen or rng
        idx = [self.vocab_index.get(w, 0) for w in seed_words]
        # добиваем контекст последним словом
        while len(idx) < CONTEXT:
            idx.insert(0, idx[0] if idx else 0)

        out: list[str] = []
        for _ in range(max_len):
            ctx = np.array([idx[-CONTEXT:]], dtype=np.int64)
            logits, _ = self.forward(ctx)

            probs = logits[0].astype(np.float64)
            probs[0] = 0.0  # не предсказываем <unk>
            probs /= temperature
            probs = np.exp(probs - probs.max())
            probs /= probs.sum()

            nxt = int(g.choice(len(probs), p=probs))
            word = self.vocab[nxt] if nxt < len(self.vocab) else "<unk>"
            if word == "<unk>":
                continue
            out.append(word)
            idx.append(nxt)

        return " ".join(out)


# ================= Negative sampling =================

def make_noise_distribution(counts: dict[str, int], vocab: list[str],
                            power: float = 0.75) -> np.ndarray:
    """
    Частотное распределение для negative sampling (Word2vec, ^0.75).
    Редкие слова получают относительно больше шансов выбираться шумом.
    """
    vocab_counts = np.ones(len(vocab), dtype=np.float64)
    index = {w: i for i, w in enumerate(vocab)}
    for w, c in counts.items():
        i = index.get(w)
        if i is not None:
            vocab_counts[i] = c

    vocab_counts[0] = 0.0  # <unk> не бывает шумом
    noise = vocab_counts ** power
    noise /= noise.sum()
    return noise


def negative_sampling_batch(targets: np.ndarray, noise: np.ndarray,
                            negs: int) -> np.ndarray:
    """
    targets: (B,) -> (B, 1 + negs)
    Первый элемент - правильный ответ, остальные - случайные слова.
    """
    B = targets.shape[0]
    negatives = rng.choice(
        len(noise), size=(B, negs), p=noise
    ).astype(np.int64)
    return np.concatenate([targets.reshape(-1, 1), negatives], axis=1)


# ================= Обучение =================

def train() -> None:
    print("=" * 55)
    print("  BlackAI-Mini - обучение собственной нейросети")
    print("=" * 55)

    print("\n[1/5] Загружаю корпус...")
    tokens = load_tokens()
    print(f"  токенов: {len(tokens)}")

    print("\n[2/5] Строю словарь...")
    vocab, index = build_vocab(tokens)

    print("\n[3/5] Кодирую корпус...")
    encoded = np.array([index.get(t, 0) for t in tokens], dtype=np.int64)
    print(f"  закодировано: {len(encoded)}")

    counts: dict[str, int] = {}
    for t in tokens:
        counts[t] = counts.get(t, 0) + 1
    noise = make_noise_distribution(counts, vocab)

    model = BlackAINeuralLM(len(vocab))
    print(f"  параметров: {sum(p.size for p in model.params):,}")

    print(f"\n[4/5] Обучение: {EPOCHS} эпох, батч {BATCH}, "
          f"контекст {CONTEXT}, negatives {NEG_SAMPLES}\n")

    n = len(encoded) - CONTEXT - 1
    batches_per_epoch = n // BATCH
    best_loss = float("inf")
    t_start = time.time()

    for epoch in range(1, EPOCHS + 1):
        # перемешиваем начало, чтобы не всегда учить на одном куске
        order = rng.permutation(n)[: n - (n % BATCH)]
        epoch_loss = 0.0
        n_batches = 0

        for b in range(batches_per_epoch):
            sl = order[b * BATCH:(b + 1) * BATCH]
            if sl[-1] + CONTEXT + 1 >= len(encoded):
                continue

            ctx_idx = np.stack(
                [encoded[i:i + CONTEXT] for i in sl]
            ).astype(np.int64)
            tgt = encoded[sl + CONTEXT + 1].astype(np.int64)

            # положительный + негативные примеры
            all_targets = negative_sampling_batch(tgt, noise, NEG_SAMPLES)

            B = ctx_idx.shape[0]
            # повторяем контекст для каждого негативного примера
            ctx_rep = np.repeat(ctx_idx, 1 + NEG_SAMPLES, axis=0)  # (B*(1+K), C)
            tgt_flat = all_targets.reshape(-1)                      # (B*(1+K),)

            logits, cache = model.forward(ctx_rep)

            # softmax + кросс-энтропия по каждой строке (1 положительный, K шумовых)
            shifted = logits - logits.max(axis=1, keepdims=True)
            exp = np.exp(shifted)
            probs = exp / exp.sum(axis=1, keepdims=True)

            # вероятность положительного класса (индекс 0 в каждой группе)
            pos_probs = probs[np.arange(B * (1 + NEG_SAMPLES)), 0]
            loss = -np.mean(np.log(pos_probs + 1e-9))
            epoch_loss += loss
            n_batches += 1

            # dL/dlogits для softmax: (probs - onehot)
            grad = probs.copy()
            grad[np.arange(B * (1 + NEG_SAMPLES)), 0] -= 1.0
            grad /= (B * (1 + NEG_SAMPLES))

            grads = model.backward(cache, grad)
            # lr постепенно уменьшаем
            lr = LEARNING_RATE * (1.0 - 0.9 * epoch / EPOCHS)
            model.adam_step(grads, lr)

        avg_loss = epoch_loss / max(n_batches, 1)
        elapsed = time.time() - t_start

        flag = ""
        if avg_loss < best_loss:
            best_loss = avg_loss
            flag = "  <- лучший"
            os.makedirs(MODEL_DIR, exist_ok=True)
            model.save(os.path.join(MODEL_DIR, "blackai_mini.npz"), vocab)

        print(f"  эпоха {epoch:2d}/{EPOCHS}  loss={avg_loss:.4f}  "
              f"{elapsed/60:.1f} мин{flag}")

        # пробная генерация - видно, как модель учится говорить
        try:
            sample = model.generate(["и", "он"], max_len=18, temperature=0.9)
            print(f"      пример: {sample}")
        except Exception:  # noqa: BLE001
            pass

    print(f"\n[5/5] Готово. Лучший loss: {best_loss:.4f}")
    print(f"Модель сохранена: {os.path.join(MODEL_DIR, 'blackai_mini.npz')}")

    # Финальная демонстрация
    print("\n--- Как отвечает обученная модель ---")
    model.vocab = vocab
    model.vocab_index = index
    for start in (["а", "что"], ["это"], ["но"]):
        print(f"  {start} -> {model.generate(start, max_len=20)}")


if __name__ == "__main__":
    train()