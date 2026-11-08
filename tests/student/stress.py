"""Usage: python tests/student/stress.py [seed]"""
import itertools, json, random, subprocess, sys, tempfile, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def build():
    out = Path(tempfile.mkdtemp())
    src = sorted(str(p) for p in (ROOT / 'Java' / 'source').glob('*.java'))
    subprocess.run(['javac', '--release', '17', '-nowarn', '-encoding', 'UTF-8', '-d', str(out), *src],
                   check=True, stderr=subprocess.DEVNULL)
    return ['java', '-Xmx512m', '-cp', str(out), 'Main']


def run(cmd, reqs):
    data = ''.join(json.dumps(r) + '\n' for r in reqs)
    t0 = time.perf_counter()
    p = subprocess.run(cmd, input=data, capture_output=True, encoding='utf-8', timeout=20)
    dt = time.perf_counter() - t0
    lines = p.stdout.splitlines()
    assert len(lines) == len(reqs), (len(lines), len(reqs), p.stderr[-500:])
    return [json.loads(x) for x in lines], dt


def is_subseq(s, t):
    it = iter(t)
    return all(c in it for c in s)


def lcs_len(a, b):
    from functools import lru_cache
    @lru_cache(None)
    def f(i, j):
        if i == len(a) or j == len(b):
            return 0
        if a[i] == b[j]:
            return 1 + f(i + 1, j + 1)
        return max(f(i + 1, j), f(i, j + 1))
    return f(0, 0)


def merge_best(s):
    best = None
    def go(blocks, cost):
        nonlocal best
        if len(blocks) == 1:
            best = cost if best is None else min(best, cost)
            return
        for i in range(len(blocks) - 1):
            go(blocks[:i] + [blocks[i] + blocks[i + 1]] + blocks[i + 2:], cost + blocks[i] + blocks[i + 1])
    go(list(s), 0)
    return best or 0


def check_plan(sizes, plan, cost):
    n = len(sizes)
    if n <= 1:
        return plan == [] and cost == 0
    blocks = {(i, i) for i in range(n)}
    total = 0
    for l, k, r in plan:
        if (l, k) not in blocks or (k + 1, r) not in blocks:
            return False
        blocks -= {(l, k), (k + 1, r)}
        blocks.add((l, r))
        total += sum(sizes[l:r + 1])
    return blocks == {(0, n - 1)} and total == cost and len(plan) == n - 1


def rand_tree(n):
    return [[random.randrange(i), i] for i in range(1, n)]


def tree_best(n, w, edges):
    best = 0
    adj = [set() for _ in range(n)]
    for u, v in edges:
        adj[u].add(v); adj[v].add(u)
    for mask in range(1 << n):
        if all(not (mask >> u & 1 and mask >> v & 1) for u, v in edges):
            best = max(best, sum(w[i] for i in range(n) if mask >> i & 1))
    return best


def occ(t, p):
    if not p:
        return list(range(len(t) + 1))
    return [i for i in range(len(t) - len(p) + 1) if t[i:i + len(p)] == p]


def rs(alpha, lo, hi):
    return ''.join(random.choice(alpha) for _ in range(random.randint(lo, hi)))


def main():
    seed = int(sys.argv[1]) if len(sys.argv) > 1 else 2026
    random.seed(seed)
    cmd = build()
    fails = 0

    def report(name, ok, info):
        nonlocal fails
        if not ok:
            fails += 1
            if fails < 10:
                print('FAIL', name, info)

  
    reqs = [{'task': 'lcs', 'a': rs('abc', 0, 9), 'b': rs('abc', 0, 9)} for _ in range(400)]
    res, _ = run(cmd, reqs)
    for q, r in zip(reqs, res):
        L = lcs_len(q['a'], q['b'])
        report('lcs', r['length'] == L and len(r['sequence']) == L and is_subseq(r['sequence'], q['a'])
               and is_subseq(r['sequence'], q['b']), (q, r))
  
    reqs = [{'task': 'merge', 'sizes': [random.choice([0, 1, 2, 5, 10**9]) for _ in range(random.randint(0, 7))]}
            for _ in range(300)]
    res, _ = run(cmd, reqs)
    for q, r in zip(reqs, res):
        report('merge', r['cost'] == merge_best(q['sizes']) and check_plan(q['sizes'], r['plan'], r['cost']), (q, r))
  
    reqs = []
    for _ in range(300):
        n = random.randint(0, 12)
        reqs.append({'task': 'tree', 'n': n, 'weights': [random.randint(-5, 5) for _ in range(n)], 'edges': rand_tree(n)})
    res, _ = run(cmd, reqs)
    for q, r in zip(reqs, res):
        sel = r['selected']
        ok = len(set(sel)) == len(sel) and all(0 <= v < q['n'] for v in sel)
        s = set(sel)
        ok = ok and not any(u in s and v in s for u, v in q['edges'])
        ok = ok and sum(q['weights'][v] for v in sel) == r['weight'] == tree_best(q['n'], q['weights'], q['edges'])
        report('tree', ok, (q, r))
  
    reqs = []
    for _ in range(500):
        alpha = random.choice(['a', 'ab', 'abc', 'aB .!', 'abdj'])
        t, p = rs(alpha, 0, 30), rs(alpha, 0, 5)
        for task in ('naive', 'kmp', 'rk'):
            reqs.append({'task': task, 'text': t, 'pattern': p})
    res, _ = run(cmd, reqs)
    for q, r in zip(reqs, res):
        report(q['task'], r['positions'] == occ(q['text'], q['pattern']), (q, r))
  
    reqs = [{'task': 'suffix', 'text': rs(random.choice(['a', 'ab', 'abc', 'xyz ab']), 0, 25)} for _ in range(400)]
    res, _ = run(cmd, reqs)
    for q, r in zip(reqs, res):
        t = q['text']
        sa = sorted(range(len(t)), key=lambda i: t[i:])
        lcp = [0] * len(t)
        for i in range(1, len(t)):
            a, b = t[sa[i - 1]:], t[sa[i]:]
            k = 0
            while k < min(len(a), len(b)) and a[k] == b[k]:
                k += 1
            lcp[i] = k
        best = max(lcp, default=0)
        rep = r['repeated']
        ok = r['sa'] == sa and r['lcp'] == lcp and len(rep) == best
        ok = ok and (rep == '' or len(occ(t, rep)) >= 2)
        report('suffix', ok, (q, r))
  
    reqs = []
    for _ in range(300):
        alpha = random.choice(['a', 'ab', 'abc'])
        pats = [rs(alpha, 1, 4) for _ in range(random.randint(0, 6))]
        texts = [rs(alpha, 0, 20) for _ in range(random.randint(0, 4))]
        reqs.append({'task': 'aho', 'patterns': pats, 'texts': texts})
    res, _ = run(cmd, reqs)
    for q, r in zip(reqs, res):
        exp = [[len(occ(t, p)) for p in q['patterns']] for t in q['texts']]
        report('aho', r['counts'] == exp, (q, r))

    print(f'random oracle checks: {"OK" if fails == 0 else str(fails) + " FAILURES"} (seed {seed})')

  
    big = [
        ('lcs 1000x1000', {'task': 'lcs', 'a': rs('ab', 1000, 1000), 'b': rs('ab', 1000, 1000)}),
        ('merge n=200 max sizes', {'task': 'merge', 'sizes': [10**9] * 200}),
        ('tree chain n=100000', {'task': 'tree', 'n': 100000, 'weights': [10**9] * 100000,
                                 'edges': [[i, i + 1] for i in range(99999)]}),
        ('tree star n=100000', {'task': 'tree', 'n': 100000, 'weights': [-1] + [10**9] * 99999,
                                'edges': [[0, i] for i in range(1, 100000)]}),
        ('kmp a^200000 / a^1000', {'task': 'kmp', 'text': 'a' * 200000, 'pattern': 'a' * 1000}),
        ('rk 10000x2000 repetitive', {'task': 'rk', 'text': 'a' * 10000, 'pattern': 'a' * 2000}),
        ('suffix a^50000', {'task': 'suffix', 'text': 'a' * 50000}),
        ('suffix random 50000', {'task': 'suffix', 'text': rs('ab', 50000, 50000)}),
        ('aho dense a..a^447', {'task': 'aho', 'patterns': ['a' * k for k in range(1, 448)],
                                'texts': ['a' * 2000] * 100}),
    ]
    for name, q in big:
        (r,), dt = run(cmd, [q])
        ok = 'error' not in r
        if q['task'] == 'tree' and name.startswith('tree chain'):
            ok = ok and r['weight'] == 50000 * 10**9
        if name.startswith('tree star'):
            ok = ok and r['weight'] == 99999 * 10**9
        if q['task'] == 'merge':
            ok = ok and check_plan(q['sizes'], r['plan'], r['cost'])
        if q['task'] == 'kmp':
            ok = ok and r['positions'] == list(range(200000 - 1000 + 1))
        if q['task'] == 'rk':
            ok = ok and r['positions'] == list(range(10000 - 2000 + 1))
        if name == 'suffix a^50000':
            ok = ok and r['sa'] == list(range(49999, -1, -1)) and len(r['repeated']) == 49999
        if q['task'] == 'aho':
            ok = ok and r['counts'][0] == [2000 - k + 1 for k in range(1, 448)]
        report(name, ok, str(r)[:200])
        print(f'{"PASS" if ok else "FAIL"} {name}: {dt:.2f}s (process incl. JVM start)')
    sys.exit(1 if fails else 0)


if __name__ == '__main__':
    main()
