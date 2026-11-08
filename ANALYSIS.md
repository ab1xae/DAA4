# Assignment 4 — analysis

## Environment and commands

- Language: Java. Compiled with `javac --release 17` (OpenJDK 25, 2025-09-16); launcher Python 3.14.
- Hardware: AMD Ryzen 7 7840HS (8 cores / 16 threads), 15 GB RAM, Windows 11 Pro.
- Commands (repository root):
  - `python tools/run.py --setup` — compile and ping.
  - `python tools/run.py --check` — 44 public cases + repeated mixed batch: all PASS.
  - `python tools/run.py --input tests/student/cases.jsonl` — own boundary cases (output in `tests/student/cases.out.jsonl`).
  - `python tests/student/stress.py 2026` — randomized oracle tests + large inputs.
  - `javac --release 17 -d out Java/source/*.java tests/student/bench/Bench.java && java -cp out Bench` — search comparison.

## Algorithm explanations

### lcs
- State: `dp[i][j]` = LCS length of prefixes `a[0..i)` and `b[0..j)`.
- Base: `dp[0][j] = dp[i][0] = 0` (empty prefix).
- Transition: if `a[i-1] == b[j-1]` then `dp[i-1][j-1] + 1`, else `max(dp[i-1][j], dp[i][j-1])`. On a mismatch at least one of the two last characters is not in an optimal common subsequence, so dropping it loses nothing.
- Order: rows by increasing `i`, columns by increasing `j`; the three dependencies are already filled.
- Reconstruction: walk back from `(n,m)`. On a match emit the character and move diagonally; otherwise move to the neighbour that keeps the value (ties go up). Each step keeps `dp` along an optimal path, so the emitted string has length `dp[n][m]` and is a subsequence of both strings.
- Complexity: O((n+1)(m+1)) time and memory (one flat `int[]`); output O(L).
- Test: `a="ab", b="ba"` → length 1; either `"a"` or `"b"` is a valid witness.

### merge
- State: `cost[l][r]` = minimum cost to merge the original interval `[l,r]` into one block; `split[l][r]` = best last split `k`.
- Base: `cost[i][i] = 0`.
- Transition: the last merge joins `[l,k]` and `[k+1,r]` and always costs `sum(l..r)` (prefix sums, `long`). So `cost[l][r] = min_k (cost[l][k] + cost[k+1][r]) + sum(l..r)`.
- Order: by increasing interval length, so shorter subintervals are ready.
- Reconstruction: iterative postorder over the split tree emits `[l,k,r]` after both children. A parent merge needs both of its blocks to exist already, so the plan is chronological. It has exactly n−1 merges.
- Complexity: O(n³) time, O(n²) memory; n ≤ 1 returns cost 0 and an empty plan in O(1).
- Test: sizes `[1e9]*4` → cost 8·10⁹ (needs 64-bit); greedily merging arbitrary smallest pairs would violate adjacency.

### tree
- State: `take[v]` = best weight in the subtree of v with v selected; `skip[v]` = best with v not selected.
- Transition: `take[v] = w[v] + Σ skip[c]`, `skip[v] = Σ max(take[c], skip[c])`. Leaves: `take = w`, `skip = 0`.
- Order: iterative BFS from root 0 (no recursion, so a 100000-vertex chain is safe); reverse BFS order is a postorder, so children are finished before their parent.
- Answer: `max(take[0], skip[0])` ≥ 0, so the empty set is allowed.
- Reconstruction: top-down in BFS order. A vertex is selected only if its parent is not selected and `take[v] > skip[v]`. When the parent is selected, the parent's `take` value used `skip[v]`, so v must be skipped. Ties choose skip. Selected vertices are unique and nonadjacent.
- Complexity: O(n+1) time and memory (array adjacency lists).
- Test: all-negative weights → weight 0, selected `[]`; a 100000 chain/star finishes without a stack overflow.

### naive
- For every start `s` in `0..n-m`, compare characters until a mismatch or a full match. The last start is `n-m`. Overlaps are found because every start is tried.
- Empty pattern → all boundaries `0..n` (n+1 starts); `m > n` → `[]`.
- Complexity: O(n·m + n + 1) time, O(1) auxiliary, O(z+1) output.
- Test: `text="abc", pattern=""` → `[0,1,2,3]`.

### kmp
- `pi[i]` = length of the longest proper prefix of `p[0..i]` that is also its suffix.
- Search keeps `q` = length of the matched prefix. On a mismatch, fall back `q = pi[q-1]` until the character matches or `q = 0`. After a full match, report `i-m+1` and set `q = pi[m-1]`. This keeps overlapping matches.
- The empty pattern and `m > n` are handled before indexing.
- Complexity: O(n+m+z+1) time (amortized: `q` grows at most n times), O(m+1) auxiliary memory.
- Test: `"aaaa"`/`"aa"` → `[0,1,2]` (overlaps after a full match).

### rk
- Hash `h(s) = Σ s[i]·31^(m-1-i) mod 101` (the leftmost character has the highest power, ASCII codes).
- Rolling update: `h = ((h − t[s]·31^(m-1)) mod 101 + 101) mod 101`, then `h = (h·31 + t[s+m]) mod 101`. The `+101` normalizes the negative remainder that Java's `%` produces.
- An equal hash is only a candidate: characters are compared before reporting, so collisions never create false matches (e.g. `ab` and `dj` both hash to 75).
- Complexity: O(n + m + c·m), where c is the number of candidate windows. With modulus 101 roughly 1/101 of the windows are random collisions, and in the worst case c = n−m+1, so the time is O(n·m) — not deterministically linear. O(1) auxiliary memory plus output.
- Test: `"abdj"`/`"ab"` → `[0]`, `"abdj"`/`"dj"` → `[2]`.

### suffix
- **Prefix doubling:** after round k, `rank[i]` orders suffixes by their first 2k characters. The next key is the pair `(rank[i], rank[i+k])`, where a suffix that ends early gets 0 (shorter < longer, with no sentinel character). Pairs plus the index are packed into one `long` and sorted with `Arrays.sort` (primitive sort). New ranks increase when the pair changes. Stop when all n ranks are distinct. O(n log² n) time, O(n) memory.
- **Kasai:** process suffixes in text order. If suffix i has LCP h with its predecessor in sa, then suffix i+1 has LCP ≥ h−1 with its predecessor. So h decreases by at most 1 per step, and the total extension is O(n).
- Longest repeat: any substring that occurs twice is a common prefix of two suffixes. The longest common prefix of any two suffixes is attained by suffixes adjacent in sa, so `max(lcp)` gives the length and `text[sa[i] .. sa[i]+lcp[i])` is the witness. Overlaps are allowed.
- Empty text → empty arrays in O(1). Suffix strings are never materialized.
- Test: `"aaaa"` → sa `[3,2,1,0]`, lcp `[0,1,2,3]`, repeated `"aaa"` (overlapping occurrences).

### aho
- Trie over the 95 printable ASCII characters (`go[state*95+c]`). Duplicate patterns end in the same terminal state, `term[i]`.
- BFS builds failure links: `fail[child] = go[fail[parent]][c]`. Missing transitions are filled from the failure state, so the automaton is complete. The failure state of u is the longest proper suffix of u's string that is a trie state.
- For each text: reset `visits` to 0, walk the automaton, and increment `visits[state]` per character. Then, in reverse BFS order (deepest first), do `visits[fail[u]] += visits[u]`. A pattern that is a suffix of the current state's string is found exactly through failure links, so after propagation `visits[term[i]]` is the number of end positions of pattern i. Overlaps and suffix patterns are counted; duplicates read the same state and get equal counts.
- Complexity: build O(L + 95·S + p); per text O(|text| + S + p). Dense matches do not cost per match.
- Edge cases: no patterns → one empty row per text; no texts → `[]`; empty text → a row of zeros.
- Test: patterns `["a","aa","a"]`, text `"aaa"` → `[3,2,3]`.

## Own tests

| Command | Input or fixture path | Expected result | Actual result / log | What it checks |
|---|---|---|---|---|
| lcs | `{"a":"","b":"abc"}` | length 0, "" | `{"length":0,"sequence":""}` | empty input |
| lcs | `{"a":"ab","b":"ba"}` | length 1, "a" or "b" | `{"length":1,"sequence":"a"}` | alternative witness, tie handling |
| merge | `[]` | 0, [] | `{"cost":0,"plan":[]}` | empty |
| merge | `[0,0,0]` | 0, 2 merges | `{"cost":0,"plan":[[1,1,2],[0,0,2]]}` | zeros, chronological plan |
| merge | `[1e9,1e9,1e9,1e9]` | 8000000000 | `{"cost":8000000000,...}` | 64-bit cost |
| tree | weights `[-1,-2,-3]` chain | 0, [] | `{"weight":0,"selected":[]}` | empty set allowed |
| tree | n=0 | 0, [] | `{"weight":0,"selected":[]}` | empty tree |
| naive | `"abc"`, `""` | [0,1,2,3] | `[0,1,2,3]` | n+1 boundaries |
| kmp | `"aaaa"`, `"aa"` | [0,1,2] | `[0,1,2]` | overlap after full match |
| rk | `"abdj"`, `"dj"` | [2] | `[2]` | collision with "ab" is verified |
| rk | `"Ab, ab!"`, `"ab"` | [4] | `[4]` | case and punctuation |
| suffix | `"aaaa"` | sa [3,2,1,0], lcp [0,1,2,3], "aaa" | same | all equal, overlap |
| suffix | `"abc"` | lcp all 0, "" | same | no repeat |
| aho | `["a","aa","a"]`, `["aaa",""]` | [[3,2,3],[0,0,0]] | same | duplicates, overlaps, empty text |
| aho | `[]`, `["x","y"]` | [[],[]] | same | empty dictionary |
| aho | `["x"]`, `[]` | [] | same | no texts |
| all | `tests/student/stress.py 2026` | random oracle agreement | `random oracle checks: OK` | 400 lcs (memoized recursion oracle), 300 merge (exhaustive merge orders + plan replay), 300 tree (subset enumeration + independence check), 500×3 search (direct slicing), 400 suffix (sorted suffix strings + direct LCP), 300 aho (direct counting) |
| scale | `stress.py` large cases | correct + fast | all PASS, each ≤ 0.2 s per process | lcs 1000×1000, merge n=200 with 1e9 sizes, tree chain/star n=100000, kmp a^200000, rk 10000×2000, suffix a^50000 and random 50000, dense aho (447 patterns × 100 texts) |

`tools/run.py --check` additionally runs all public cases twice in one process (state reset): PASS.

## Search comparison

Program: `tests/student/bench/Bench.java`, `java.util.Random(42)`. It calls `Algorithms.naive/kmp/rk` directly on the same text/pattern, so JSON parsing and JVM start are excluded. Before timing, the outputs of kmp and rk are compared with naive (`outputs_equal=true` in all rows).

- Random case: text uniform over `a–z`; pattern = a random substring of the text (so at least one match).
- Repetitive case: text `a^(n-1)b`, pattern `a^(m-1)b` (worst case for naive: almost m comparisons per start; exactly one match).
- JIT: 200 warm-up calls per algorithm/input, then 3 measured runs; each run is the mean of 50 calls. Unit: µs per call.

| Case | n | m | Seed/generator | Algorithm | Run 1 | Run 2 | Run 3 | Median | Correct outputs checked |
|---|---:|---:|---|---|---:|---:|---:|---:|---|
| random | 1000 | 10 | Random(42) | naive | 13.8 | 13.6 | 12.9 | 13.6 | yes |
| random | 1000 | 10 | Random(42) | kmp | 48.8 | 46.6 | 2.9 | 46.6 | yes |
| random | 1000 | 10 | Random(42) | rk | 56.1 | 56.3 | 37.7 | 56.1 | yes |
| random | 2000 | 100 | Random(42) | naive | 5.7 | 5.4 | 4.3 | 5.4 | yes |
| random | 2000 | 100 | Random(42) | kmp | 4.3 | 4.4 | 5.5 | 4.4 | yes |
| random | 2000 | 100 | Random(42) | rk | 16.0 | 16.1 | 16.1 | 16.1 | yes |
| random | 3000 | 300 | Random(42) | naive | 6.1 | 6.5 | 6.3 | 6.3 | yes |
| random | 3000 | 300 | Random(42) | kmp | 7.0 | 7.0 | 7.9 | 7.0 | yes |
| random | 3000 | 300 | Random(42) | rk | 24.0 | 23.9 | 23.6 | 23.9 | yes |
| repetitive | 1000 | 10 | fixed | naive | 12.9 | 11.3 | 11.6 | 11.6 | yes |
| repetitive | 1000 | 10 | fixed | kmp | 4.5 | 3.5 | 3.1 | 3.5 | yes |
| repetitive | 1000 | 10 | fixed | rk | 8.0 | 8.2 | 8.5 | 8.2 | yes |
| repetitive | 2000 | 100 | fixed | naive | 211.1 | 214.2 | 212.8 | 212.8 | yes |
| repetitive | 2000 | 100 | fixed | kmp | 6.8 | 6.8 | 7.1 | 6.8 | yes |
| repetitive | 2000 | 100 | fixed | rk | 15.9 | 17.3 | 15.8 | 15.9 | yes |
| repetitive | 3000 | 300 | fixed | naive | 1020.9 | 1020.1 | 1024.2 | 1020.9 | yes |
| repetitive | 3000 | 300 | fixed | kmp | 11.5 | 11.9 | 12.0 | 11.9 | yes |
| repetitive | 3000 | 300 | fixed | rk | 60.7 | 24.4 | 23.3 | 24.4 | yes |

Observations:
- On random text naive is as fast as KMP: a mismatch usually comes at the first or second character, so naive does about n comparisons.
- On repetitive input naive grows like n·m (≈213 µs → ≈1021 µs from 2000×100 to 3000×300), while KMP stays almost linear (6.8 → 11.9 µs).
- RK pays for the rolling update on every window. With modulus 101 about 1/101 of the windows are false candidates that need verification, so it is slower than KMP here.
- The smallest size (n=1000) is noisy: the KMP and RK runs differ by about 10× between runs, which suggests the JIT was still compiling during measurement. These numbers are measurements on one machine and do not prove asymptotic complexity.
- Modulus 101 is deliberately tiny to make collisions testable (`ab`/`dj` both hash to 75; the case is in the own tests). Production RK uses a large (often 64-bit or double) modulus, where collisions are rare, so these timings do not represent such implementations.

## Sources and AI assistance

Course materials: lectures 8–10, `docs/LECTURE_CLARIFICATIONS_EN_RU.md`, and the specification in `docs/`. Verification: public checks, randomized brute-force oracles, and large-input tests, all listed above.
