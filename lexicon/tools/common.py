"""What the lexicon tools share: the engine's syllables, a list's rows, and latin_counts.py's header."""
import re
from pathlib import Path

SYLLABLES_KT = (Path(__file__).resolve().parents[2]
                / 'lib/ime-core/src/main/kotlin/org/fcitx/fcitx5/android/engine/pinyin/Syllables.kt')
# latin_counts.py's first line, the FineWeb shards counted: latin.words credits them (ODC-By)
SHARDS = '# shards:'


def syllables():
    """The engine's syllables, as Syllables.kt's SPELLINGS lists them: a reading of any other cannot be packed."""
    source = SYLLABLES_KT.read_text(encoding='utf-8')
    listed = re.search(r'SPELLINGS =((?:\s*"[^"]*"\s*\+)*\s*"[^"]*")', source)
    if not listed:
        raise SystemExit(f'{SYLLABLES_KT}: no SPELLINGS = "..." to read the syllables from')
    return set(''.join(re.findall(r'"([^"]*)"', listed.group(1))).split())


def rows(path):
    """A tab-separated list's lines, each split at its tabs; `#` comments and blank lines skipped."""
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.strip() and not line.startswith('#'):
                yield line.rstrip('\n').split('\t')
