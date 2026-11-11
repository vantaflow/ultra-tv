"""Petit lecteur de Kotlin : chaînes littérales (avec gabarits), parenthèses équilibrées, masque « code ».

Suffisant pour les fichiers de libellés d'Ultra TV ; pas un vrai parseur Kotlin.
"""
import re

ESC = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", '"': '"', "'": "'", "\\": "\\", "$": "$"}


def skip_string(src, i):
    """src[i] == '"' : renvoie l'index juste après la chaîne fermante (gère ${...} imbriqués)."""
    assert src[i] == '"'
    j = i + 1
    while True:
        c = src[j]
        if c == "\\":
            j += 2 if src[j + 1] != "u" else 6
        elif c == '"':
            return j + 1
        elif c == "$" and src[j + 1] == "{":
            j = skip_balanced(src, j + 1, "{", "}")
        else:
            j += 1


def skip_balanced(src, i, o, c):
    """src[i] == o : renvoie l'index juste après le fermant correspondant."""
    assert src[i] == o, (src[i - 20:i + 20], o)
    depth = 0
    j = i
    while j < len(src):
        ch = src[j]
        if ch == '"':
            j = skip_string(src, j)
            continue
        if ch == "'" and j + 2 < len(src) and (src[j + 2] == "'" or (src[j + 1] == "\\" and src[j + 3] == "'")):
            j += 3 if src[j + 2] == "'" else 4
            continue
        if ch == "/" and src[j + 1] == "/":
            j = src.index("\n", j)
            continue
        if ch == "/" and src[j + 1] == "*":
            j = src.index("*/", j) + 2
            continue
        if ch == o:
            depth += 1
        elif ch == c:
            depth -= 1
            if depth == 0:
                return j + 1
        j += 1
    raise ValueError("non équilibré")


def lex_string(src, i):
    """Lit une chaîne littérale. Renvoie (parts, fin) ; parts = [('text', s) | ('expr', e)]."""
    assert src[i] == '"' and not src.startswith('"""', i)
    parts, buf = [], []
    j = i + 1

    def flush():
        if buf:
            parts.append(("text", "".join(buf)))
            buf.clear()

    while True:
        c = src[j]
        if c == '"':
            flush()
            return parts, j + 1
        if c == "\\":
            n = src[j + 1]
            if n == "u":
                buf.append(chr(int(src[j + 2:j + 6], 16)))
                j += 6
            else:
                buf.append(ESC[n])
                j += 2
            continue
        if c == "$":
            m = re.match(r"\$([A-Za-z_]\w*)", src[j:])
            if m:
                flush()
                parts.append(("expr", m.group(1)))
                j += len(m.group(0))
                continue
            if src[j + 1] == "{":
                end = skip_balanced(src, j + 1, "{", "}")
                flush()
                parts.append(("expr", src[j + 2:end - 1].strip()))
                j = end
                continue
        buf.append(c)
        j += 1


def code_mask(src):
    """mask[i] = True si src[i] est du code (ni chaîne, ni commentaire)."""
    mask = bytearray(b"\x01" * len(src))
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '"':
            e = skip_string(src, i)
            mask[i:e] = b"\x00" * (e - i)
            i = e
        elif c == "/" and i + 1 < n and src[i + 1] == "/":
            e = src.find("\n", i)
            e = n if e < 0 else e
            mask[i:e] = b"\x00" * (e - i)
            i = e
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            e = src.index("*/", i) + 2
            mask[i:e] = b"\x00" * (e - i)
            i = e
        elif c == "'" and i + 2 < n and (src[i + 2] == "'" or (src[i + 1] == "\\" and src[i + 3] == "'")):
            e = i + (3 if src[i + 2] == "'" else 4)
            mask[i:e] = b"\x00" * (e - i)
            i = e
        else:
            i += 1
    return mask


def split_args(src, start, end):
    """Découpe src[start:end] (intérieur d'un appel) aux virgules de premier niveau. Renvoie [(a, b)]."""
    out, depth, j, a = [], 0, start, start
    while j < end:
        c = src[j]
        if c == '"':
            j = skip_string(src, j)
            continue
        if c == "/" and src[j + 1] == "/":
            j = src.index("\n", j)
            continue
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "," and depth == 0:
            out.append((a, j))
            a = j + 1
        j += 1
    if src[a:end].strip():
        out.append((a, end))
    return out


def snake(name):
    s = re.sub(r"(?<=[a-z0-9])([A-Z])", r"_\1", name)
    s = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", s)
    return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")
