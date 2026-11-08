# Assignment 4 — analysis

## Environment and commands

Java (`javac --release 17`, OpenJDK 25), Python 3.14 launcher; AMD Ryzen 7 7840HS, 15 GB RAM, Windows 11.

```sh
python tools/run.py --setup
python tools/run.py --check                                   # 44 cases + state reset: PASS
python tools/run.py --input tests/student/cases.jsonl         # output: tests/student/cases.out.jsonl
python tests/student/stress.py 2026                           # random oracles + large inputs
javac --release 17 -d out Java/source/*.java tests/student/bench/Bench.java && java -cp out Bench
```

## Algorithms

**lcs.** `dp[i][j]` = LCS of `a[0..i)`, `b[0..j)`; `dp[0][*]=dp[*][0]=0`. Match: `dp[i-1][j-1]+1`; mismatch: `max(dp[i-1][j], dp[i][j-1])`, since one of the last characters is unused in some optimum. Rows filled in increasing order. Reconstruction walks back from `(n,m)`, emitting matches and following an equal-valued neighbour (ties go up). O((n+1)(m+1)) time and memory.

**merge.** `cost[l][r]` = min cost to merge `[l,r]`, `cost[i][i]=0`. The last merge always costs `sum(l..r)`, so `cost[l][r] = min_k(cost[l][k]+cost[k+1][r]) + sum(l..r)`, by increasing length, with 64-bit prefix sums. The plan is an iterative postorder of the `split` tree: children before the parent, n−1 merges. O(n³) time, O(n²) memory.

**tree.** `take[v] = w[v] + Σ skip[c]`, `skip[v] = Σ max(take[c], skip[c])`. Iterative BFS from 0; reverse BFS order gives a postorder, so a deep chain cannot overflow the stack. Answer `max(take[0], skip[0]) ≥ 0`, so the empty set is allowed. Reconstruction is top-down: v is taken only if its parent is not taken and `take[v] > skip[v]`. O(n+1) time and memory.

**naive.** Try every start `0..n−m` and compare characters; overlaps are included. Empty pattern → `0..n`; `m>n` → `[]`. O(nm+n+1) time, O(1) auxiliary.

**kmp.** `pi[i]` = longest proper border of `p[0..i]`. On a mismatch `q = pi[q−1]`; after a full match report `i−m+1` and set `q = pi[m−1]`, which keeps overlaps. O(n+m+z+1) time, O(m) auxiliary.

**rk.** `h = Σ s[i]·31^(m−1−i) mod 101`. Roll: `h = ((h − t[s]·31^(m−1)) % 101 + 101) % 101`, then `h = (h·31 + t[s+m]) % 101`. Equal hashes are verified character by character, so `ab`/`dj` (both hash to 75) produce no false match. O(n+m+c·m) time, where c is the number of candidates; the worst case is O(nm), not deterministically linear.

**suffix.** Prefix doubling: sort pairs `(rank[i], rank[i+k] or 0)` packed into `long` with `Arrays.sort`, and stop when all ranks are distinct. O(n log² n). No sentinel and no suffix strings are built. Kasai: going to the next suffix in text order, the LCP drops by at most 1, so the total work is O(n). The longest repeat is the prefix of length `max(lcp)` at the adjacent pair where it occurs, because the best common prefix of any two suffixes appears between neighbours in sa.

**aho.** Trie over 95 ASCII characters; duplicate patterns share a terminal state. BFS builds failure links and a complete `go` table. Per text: reset counters, add a visit per character, then propagate `visits[fail[u]] += visits[u]` deepest first. This counts suffix patterns and overlaps without per-match work. Build O(L+95S+p); per text O(|text|+S+p).

## Own tests

| Command | Input | Expected = actual | Checks |
|---|---|---|---|
| lcs | `""`,`"abc"` / `"ab"`,`"ba"` | 0,"" / 1,"a" | empty; alternative witness |
| merge | `[]` / `[0,0,0]` / `[1e9]*4` | 0 / 0 / 8000000000 | empty; zeros; 64-bit |
| tree | `[-1,-2,-3]` chain / n=0 | 0, [] | empty set allowed |
| naive, kmp | `"abc"`,`""` / `"aaaa"`,`"aa"` | [0,1,2,3] / [0,1,2] | n+1 boundaries; overlaps |
| rk | `"abdj"`,`"dj"` / `"Ab, ab!"`,`"ab"` | [2] / [4] | collision verified; case |
| suffix | `"aaaa"` / `"abc"` | sa [3,2,1,0], lcp [0,1,2,3], "aaa" / "" | all equal; no repeat |
| aho | `["a","aa","a"]`,`["aaa",""]` / `[]` / no texts | [[3,2,3],[0,0,0]] / [[],[]] / [] | duplicates, empty cases |
| all | `stress.py 2026` | OK | ~3000 random cases vs brute-force oracles |
| scale | `stress.py` | PASS, ≤0.2 s each | lcs 1000², tree chain n=1e5, suffix 50000, dense aho |

## Search comparison

`Bench.java`, `Random(42)`. Direct method calls with the same inputs; outputs checked equal to naive before timing. 200 warm-up calls, then 3 runs of 50 calls each; µs per call. Random: `a–z` text, pattern taken from the text. Repetitive: `a^(n−1)b` / `a^(m−1)b`.

| Case | n | m | naive (runs → median) | kmp | rk |
|---|---:|---:|---|---|---|
| random | 1000 | 10 | 13.8, 13.6, 12.9 → 13.6 | 48.8, 46.6, 2.9 → 46.6 | 56.1, 56.3, 37.7 → 56.1 |
| random | 2000 | 100 | 5.7, 5.4, 4.3 → 5.4 | 4.3, 4.4, 5.5 → 4.4 | 16.0, 16.1, 16.1 → 16.1 |
| random | 3000 | 300 | 6.1, 6.5, 6.3 → 6.3 | 7.0, 7.0, 7.9 → 7.0 | 24.0, 23.9, 23.6 → 23.9 |
| repetitive | 1000 | 10 | 12.9, 11.3, 11.6 → 11.6 | 4.5, 3.5, 3.1 → 3.5 | 8.0, 8.2, 8.5 → 8.2 |
| repetitive | 2000 | 100 | 211.1, 214.2, 212.8 → 212.8 | 6.8, 6.8, 7.1 → 6.8 | 15.9, 17.3, 15.8 → 15.9 |
| repetitive | 3000 | 300 | 1020.9, 1020.1, 1024.2 → 1020.9 | 11.5, 11.9, 12.0 → 11.9 | 60.7, 24.4, 23.3 → 24.4 |

On random text naive ≈ KMP, because mismatches come early. On repetitive input naive grows like n·m while KMP stays near linear. RK pays for the rolling update, plus false candidates at modulus 101. The n=1000 rows are noisy, probably because the JIT was still compiling. Timings do not prove asymptotics. Modulus 101 is chosen to make collisions testable; production RK uses large moduli, so these numbers do not represent it.

## Sources and AI assistance

Course lectures 8–10, `docs/LECTURE_CLARIFICATIONS_EN_RU.md`, specification in `docs/`. Verified with public checks, brute-force oracles and large-input tests.
