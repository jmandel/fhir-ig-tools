#!/usr/bin/env python3
"""Differential fuzz input generator (not an acceptance suite): random documents assembled from
lines of the feature-suite texts plus random markdown punctuation, with random configs.
usage: fuzz.py <seed> <count> <out.jsonl>"""
import json, os, random, sys
ROOT = os.path.dirname(os.path.abspath(__file__))
seed, count, out = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
rnd = random.Random(seed)
texts = [json.loads(l)['text'] for l in open(os.path.join(ROOT, 'cases.jsonl'), encoding='utf-8')]
if seed % 4 == 0:
    # corpus mode: windows of real IG markdown
    corpus = [json.loads(l)['text'] for l in open(os.path.join(ROOT, '..', 'corpus', 'md-cases.jsonl'), encoding='utf-8')]
    texts = []
    for t in corpus:
        for _ in range(3):
            a = rnd.randrange(max(1, len(t)))
            a = t.rfind('\n', 0, a) + 1
            texts.append(t[a:a + rnd.randint(200, 6000)])
lines = [l for t in texts for l in t.split('\n')]
TOK = ['*', '**', '_', '__', '`', '``', '[', ']', '(', ')', '<', '>', '"', "'", '|', '\\', '{:', '}', '#', '-', '~~',
       '$$', '&', ';', ':', '^', '!', '\n', '\n\n', '  ', '    ', '\t', '<div>', '</div>', '<span>', '</span>',
       '{: .c}', '[^1]', '[^1]: x', '*[A]: b', '<!--', '-->', '```', '~~~', '1. ', '* ', '> ', ': ', '---', '===',
       'word', 'é', '日本', '<div markdown="1">', '{::options parse_block_html="true" /}', '{::comment}', '{:/}',
       '\\\n', 'http://x.y', '<http://a.b>', '&amp;', '&nbsp;', '--', '...', '<<', '>>']
CFGS = [{}, {"toc_levels": "1..3", "smart_quotes": ["apos", "apos", "quot", "quot"],
             "typographic_symbols": {"laquo": "<<", "raquo": ">>"}},
        {"auto_ids": False}, {"hard_wrap": True}, {"parse_block_html": True}, {"gfm_quirks": []},
        {"input": "kramdown"}, {"entity_output": "numeric"}, {"auto_id_prefix": "x-", "footnote_nr": 3}]
with open(out, 'w', encoding='utf-8') as f:
    for i in range(count):
        parts = []
        for _ in range(rnd.randint(2, 14)):
            r = rnd.random()
            if r < 0.6:
                parts.append(rnd.choice(lines) + '\n')
            elif r < 0.9:
                parts.append(''.join(rnd.choice(TOK) for _ in range(rnd.randint(1, 6))))
            else:
                parts.append('\n')
        text = ''.join(parts)
        if seed % 2 == 0:
            # mutation mode: mutate a whole suite text
            text = rnd.choice(texts)
            for _ in range(rnd.randint(1, 8)):
                if not text:
                    text = rnd.choice(TOK)
                    continue
                k = rnd.randrange(len(text))
                r = rnd.random()
                if r < 0.35:
                    text = text[:k] + rnd.choice(TOK) + text[k:]
                elif r < 0.55:
                    text = text[:k] + text[k + rnd.randint(1, 10):]
                elif r < 0.75:
                    ls = text.split('\n'); j = rnd.randrange(len(ls)); ls.insert(j, ls[j]); text = '\n'.join(ls)
                elif r < 0.9:
                    ls = text.split('\n'); j = rnd.randrange(len(ls)); ls[j] = rnd.choice(['', ' ', '  ', '    ', '\t', '> ', '* ', '1. ', ': ']) + ls[j]; text = '\n'.join(ls)
                else:
                    text = text + '\n' + rnd.choice(texts)
        f.write(json.dumps({"id": "fuzz%d-%d" % (seed, i), "type": "kramdown", "text": text,
                            "config": rnd.choice(CFGS)}, ensure_ascii=False) + '\n')
