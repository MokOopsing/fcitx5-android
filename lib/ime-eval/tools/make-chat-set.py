#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-2.1-or-later
# SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
"""
Makes data/pinyin-chat.tsv from LCCC-base's validation split (MIT; its README asks for research
use, so the set only measures and helps choose parameters; nothing the app ships is trained on LCCC):

    python3 tools/make-chat-set.py lccc_base_valid.jsonl.gz <dict_sc.txt> <lm_sc.arpa> > data/pinyin-chat.tsv

and data/pinyin-web.tsv the same way from the chat-like pages of a crawl's WET files the engine
data never took, held out (`ime-dict-tool cc --from` past engine-data.sh's FROM + FILES, `clean`,
then `text`), one run in WEB_KEEP_ONE_IN:

    python3 tools/make-chat-set.py --web <crawl and files> held-out.jsonl.gz <dict_sc.txt> <lm_sc.arpa> > data/pinyin-web.tsv

with libime's dict_sc.txt (app/build/engine-sources/dict) and lm_sc.arpa (download.fcitx-im.org/data/lm_sc.arpa-20260629.tar.zst) as the dictionary and model.
Each turn is cut at punctuation; a run of 4 to 12 Han chars becomes a sample when its text hashes
into the kept 1/25, the run's earlier part of the same turn being its context. The run is split
into the dictionary's words the way the model finds likeliest (the sum of unigram scores), and
its input is their readings: a word with more than one reading takes the one of chat.py's READINGS
(得 dei after a word of MUST, 了 and 着 liao and zhao after a verb and 不, and the few of AHEAD by
the word after them), and a run with a word these do not settle is left out rather than guessed.
"""
import gzip
import hashlib
import json
import re
import sys

from chat import CONTEXT, KEEP_ONE_IN, PUNCTUATION, RUN, loaders

HEADER = """\
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: Copyright (c) 2020 lemon234071 (LCCC, https://github.com/thu-coai/CDial-GPT)
#
# Chat lines as people type them: runs of 4 to 12 Han chars from LCCC-base's validation split
# (Wang et al., 2020: Weibo and other chat, MIT; its README asks for research use, so the set only
# measures and helps choose parameters; nothing the app ships is trained on it). Picked and given
# readings for this project by
# tools/make-chat-set.py; the text is kept as its writers typed it, slips and all, so a first
# choice is sometimes judged wrong for being better written. Measures what a change does to chat
# next to pinyin.tsv.
# One sample per line: input<TAB>expected<TAB>chat[<TAB>context], the context being what came
# before the run in the same turn."""
WEB_HEADER = """\
# SPDX-License-Identifier: LGPL-2.1-or-later
# SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
#
# Text of recent pages as people write it: runs of 4 to 12 Han chars from the chat-like pages of
# CommonCrawl {}, WET files the engine data never took. Picked and given readings by
# tools/make-chat-set.py --web, kept as written. Measures what a change does to text of the
# crawls' time beside the older chat of pinyin-chat.tsv.
# One sample per line: input<TAB>expected<TAB>web[<TAB>context], the context being what came
# before the run in the same line."""
WEB_KEEP_ONE_IN = 40


def main(chat, dictionary, arpa, web=None):
    _, read = loaders(dictionary, arpa)
    print(WEB_HEADER.format(web) if web else HEADER)
    keep, tag = (WEB_KEEP_ONE_IN, 'web') if web else (KEEP_ONE_IN, 'chat')
    seen = set()
    with gzip.open(chat, 'rt', encoding='utf-8') as f:
        for line in f:
            for turn in json.loads(line):
                before = ''
                # LCCC puts a space between chars; any other blank, a tab say, must not reach a line
                for part in PUNCTUATION.split(re.sub(r'\s+', '', turn)):
                    if RUN.match(part) and part not in seen and \
                            int(hashlib.md5(part.encode()).hexdigest(), 16) % keep == 0:
                        seen.add(part)
                        typed = read(part)
                        if typed:
                            sample = [typed, part, tag]
                            if before:
                                sample.append(before[-CONTEXT:])
                            print('\t'.join(sample))
                    before += part


if __name__ == '__main__':
    args = sys.argv[1:]
    web = args[1] if len(args) > 1 and args[0] == '--web' else None
    files = args if web is None else args[2:]
    if len(files) != 3:
        sys.exit(f'usage: {sys.argv[0]} [--web <crawl and files>] <chat.jsonl.gz> <dict_sc.txt> <lm_sc.arpa>')
    main(*files, web=web)
