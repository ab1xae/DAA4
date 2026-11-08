import java.util.*;
/** Implement these algorithms. docs/guide/CONTRACTS.md defines each field and limit. */
public final class Algorithms {

    private static String str(Map<String,Object> input, String key) {
        Object v = input.get(key);
        return v == null ? "" : (String) v;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String,Object> input, String key) {
        Object v = input.get(key);
        return v == null ? new ArrayList<>() : (List<Object>) v;
    }

    private static Map<String,Object> result() { return new LinkedHashMap<>(); }

    /* ------------------------------------------------------------------ */
    /* Part 1: LCS — prefix DP, dp[i][j] = LCS length of a[0..i) and b[0..j) */
    /* ------------------------------------------------------------------ */
    static Map<String,Object> lcs(Map<String,Object> input) {
        String a = str(input, "a"), b = str(input, "b");
        int n = a.length(), m = b.length();
        Map<String,Object> out = result();
        if (n == 0 || m == 0) { out.put("length", 0); out.put("sequence", ""); return out; }
        int w = m + 1;
        int[] dp = new int[(n + 1) * w];
        for (int i = 1; i <= n; i++) {
            char ca = a.charAt(i - 1);
            int row = i * w, prev = (i - 1) * w;
            for (int j = 1; j <= m; j++) {
                if (ca == b.charAt(j - 1)) dp[row + j] = dp[prev + j - 1] + 1;
                else dp[row + j] = Math.max(dp[prev + j], dp[row + j - 1]);
            }
        }
        // Reconstruct backwards from (n, m).
        char[] seq = new char[dp[n * w + m]];
        int k = seq.length, i = n, j = m;
        while (i > 0 && j > 0) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) { seq[--k] = a.charAt(i - 1); i--; j--; }
            else if (dp[(i - 1) * w + j] >= dp[i * w + j - 1]) i--;
            else j--;
        }
        out.put("length", seq.length);
        out.put("sequence", new String(seq));
        return out;
    }

    /* ------------------------------------------------------------------ */
    /* Part 2: ordered adjacent-block merging — interval DP                */
    /* cost[l][r] = min over k of cost[l][k] + cost[k+1][r] + sum(l..r)    */
    /* ------------------------------------------------------------------ */
    static Map<String,Object> merge(Map<String,Object> input) {
        List<Object> raw = list(input, "sizes");
        int n = raw.size();
        Map<String,Object> out = result();
        List<long[]> plan = new ArrayList<>();
        if (n <= 1) { out.put("cost", 0L); out.put("plan", plan); return out; }
        long[] pre = new long[n + 1];
        for (int i = 0; i < n; i++) pre[i + 1] = pre[i] + ((Number) raw.get(i)).longValue();
        long[][] cost = new long[n][n];
        int[][] split = new int[n][n];
        for (int len = 2; len <= n; len++) {
            for (int l = 0; l + len - 1 < n; l++) {
                int r = l + len - 1;
                long best = Long.MAX_VALUE; int bk = l;
                for (int k = l; k < r; k++) {
                    long c = cost[l][k] + cost[k + 1][r];
                    if (c < best) { best = c; bk = k; }
                }
                cost[l][r] = best + (pre[r + 1] - pre[l]);
                split[l][r] = bk;
            }
        }
        // Postorder (children before parent) without recursion.
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1, 0});
        while (!stack.isEmpty()) {
            int[] f = stack.peek();
            int l = f[0], r = f[1];
            if (l == r) { stack.pop(); continue; }
            int k = split[l][r];
            if (f[2] == 0) { f[2] = 1; stack.push(new int[]{l, k, 0}); }
            else if (f[2] == 1) { f[2] = 2; stack.push(new int[]{k + 1, r, 0}); }
            else { stack.pop(); plan.add(new long[]{l, k, r}); }
        }
        out.put("cost", cost[0][n - 1]);
        out.put("plan", plan);
        return out;
    }

    /* ------------------------------------------------------------------ */
    /* Part 3: maximum-weight independent set on a tree — take/skip DP     */
    /* ------------------------------------------------------------------ */
    static Map<String,Object> tree(Map<String,Object> input) {
        List<Object> ws = list(input, "weights");
        List<Object> es = list(input, "edges");
        int n = ws.size();
        Map<String,Object> out = result();
        if (n == 0) { out.put("weight", 0L); out.put("selected", new int[0]); return out; }
        long[] w = new long[n];
        for (int i = 0; i < n; i++) w[i] = ((Number) ws.get(i)).longValue();
        // Adjacency as linked lists in arrays.
        int[] head = new int[n], next = new int[2 * es.size()], to = new int[2 * es.size()];
        Arrays.fill(head, -1);
        int ec = 0;
        for (Object eo : es) {
            List<?> e = (List<?>) eo;
            int u = ((Number) e.get(0)).intValue(), v = ((Number) e.get(1)).intValue();
            to[ec] = v; next[ec] = head[u]; head[u] = ec++;
            to[ec] = u; next[ec] = head[v]; head[v] = ec++;
        }
        // BFS order from root 0; reverse BFS order is a valid postorder.
        int[] order = new int[n], parent = new int[n];
        boolean[] seen = new boolean[n];
        int qh = 0, qt = 0;
        order[qt++] = 0; seen[0] = true; parent[0] = -1;
        while (qh < qt) {
            int v = order[qh++];
            for (int e = head[v]; e != -1; e = next[e]) {
                int u = to[e];
                if (!seen[u]) { seen[u] = true; parent[u] = v; order[qt++] = u; }
            }
        }
        long[] take = new long[n], skip = new long[n];
        for (int idx = qt - 1; idx >= 0; idx--) {
            int v = order[idx];
            take[v] += w[v];
            int p = parent[v];
            if (p >= 0) {
                take[p] += skip[v];
                skip[p] += Math.max(take[v], skip[v]);
            }
        }
        // Top-down reconstruction: a vertex may be taken only if its parent is not.
        boolean[] chosen = new boolean[n];
        int cnt = 0;
        for (int idx = 0; idx < qt; idx++) {
            int v = order[idx];
            int p = parent[v];
            chosen[v] = (p < 0 || !chosen[p]) && take[v] > skip[v];
            if (chosen[v]) cnt++;
        }
        int[] sel = new int[cnt];
        for (int v = 0, k = 0; v < n; v++) if (chosen[v]) sel[k++] = v;
        out.put("weight", Math.max(take[0], skip[0]));
        out.put("selected", sel);
        return out;
    }

    /* ------------------------------------------------------------------ */
    /* Part 4: exact single-pattern search                                 */
    /* ------------------------------------------------------------------ */
    private static Map<String,Object> positions(int[] buf, int z) {
        Map<String,Object> out = result();
        out.put("positions", Arrays.copyOf(buf, z));
        return out;
    }

    private static Map<String,Object> allBoundaries(int n) {
        int[] p = new int[n + 1];
        for (int i = 0; i <= n; i++) p[i] = i;
        Map<String,Object> out = result();
        out.put("positions", p);
        return out;
    }

    static Map<String,Object> naive(Map<String,Object> input) {
        String t = str(input, "text"), p = str(input, "pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return allBoundaries(n);
        if (m > n) return positions(new int[0], 0);
        int[] buf = new int[n - m + 1];
        int z = 0;
        for (int s = 0; s + m <= n; s++) {
            int j = 0;
            while (j < m && t.charAt(s + j) == p.charAt(j)) j++;
            if (j == m) buf[z++] = s;
        }
        return positions(buf, z);
    }

    /** pi[i] = length of the longest proper border of p[0..i]. */
    static int[] prefixFunction(String p) {
        int m = p.length();
        int[] pi = new int[m];
        for (int i = 1; i < m; i++) {
            int k = pi[i - 1];
            while (k > 0 && p.charAt(i) != p.charAt(k)) k = pi[k - 1];
            if (p.charAt(i) == p.charAt(k)) k++;
            pi[i] = k;
        }
        return pi;
    }

    static Map<String,Object> kmp(Map<String,Object> input) {
        String t = str(input, "text"), p = str(input, "pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return allBoundaries(n);
        if (m > n) return positions(new int[0], 0);
        int[] pi = prefixFunction(p);
        int[] buf = new int[n - m + 1];
        int z = 0, q = 0;
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            while (q > 0 && c != p.charAt(q)) q = pi[q - 1];
            if (c == p.charAt(q)) q++;
            if (q == m) { buf[z++] = i - m + 1; q = pi[q - 1]; }
        }
        return positions(buf, z);
    }

    static final int RK_BASE = 31, RK_MOD = 101;

    static Map<String,Object> rk(Map<String,Object> input) {
        String t = str(input, "text"), p = str(input, "pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return allBoundaries(n);
        if (m > n) return positions(new int[0], 0);
        long high = 1; // BASE^(m-1) mod MOD, weight of the leftmost character
        for (int i = 1; i < m; i++) high = high * RK_BASE % RK_MOD;
        long hp = 0, ht = 0;
        for (int i = 0; i < m; i++) {
            hp = (hp * RK_BASE + p.charAt(i)) % RK_MOD;
            ht = (ht * RK_BASE + t.charAt(i)) % RK_MOD;
        }
        int[] buf = new int[n - m + 1];
        int z = 0;
        for (int s = 0; ; s++) {
            if (ht == hp) { // candidate only: verify characters
                int j = 0;
                while (j < m && t.charAt(s + j) == p.charAt(j)) j++;
                if (j == m) buf[z++] = s;
            }
            if (s + m >= n) break;
            ht = (ht - t.charAt(s) * high) % RK_MOD;
            if (ht < 0) ht += RK_MOD; // normalize negative remainder
            ht = (ht * RK_BASE + t.charAt(s + m)) % RK_MOD;
        }
        return positions(buf, z);
    }

    /* ------------------------------------------------------------------ */
    /* Part 5: suffix array (prefix doubling) + Kasai LCP                  */
    /* ------------------------------------------------------------------ */
    static int[] suffixArray(String t) {
        int n = t.length();
        int[] sa = new int[n], rank = new int[n], tmp = new int[n];
        for (int i = 0; i < n; i++) rank[i] = t.charAt(i); // 32..126
        long[] keys = new long[n];
        for (int k = 1; ; k <<= 1) {
            // key = (rank[i]+1, rank[i+k]+1 or 0 if past end), packed with index i.
            for (int i = 0; i < n; i++) {
                long r1 = rank[i] + 1L, r2 = i + k < n ? rank[i + k] + 1L : 0L;
                keys[i] = (r1 << 40) | (r2 << 20) | i;
            }
            Arrays.sort(keys);
            for (int i = 0; i < n; i++) sa[i] = (int) (keys[i] & 0xFFFFF);
            tmp[sa[0]] = 0;
            for (int i = 1; i < n; i++)
                tmp[sa[i]] = tmp[sa[i - 1]] + ((keys[i] >>> 20) != (keys[i - 1] >>> 20) ? 1 : 0);
            System.arraycopy(tmp, 0, rank, 0, n);
            if (rank[sa[n - 1]] == n - 1) break; // all ranks distinct
            if (k >= n) break;
        }
        return sa;
    }

    /** Kasai: lcp[i] = LCP(suffix sa[i-1], suffix sa[i]), lcp[0] = 0. */
    static int[] kasai(String t, int[] sa) {
        int n = t.length();
        int[] rank = new int[n], lcp = new int[n];
        for (int i = 0; i < n; i++) rank[sa[i]] = i;
        int h = 0;
        for (int i = 0; i < n; i++) {
            int r = rank[i];
            if (r == 0) { h = 0; continue; }
            int j = sa[r - 1];
            while (i + h < n && j + h < n && t.charAt(i + h) == t.charAt(j + h)) h++;
            lcp[r] = h;
            if (h > 0) h--;
        }
        return lcp;
    }

    static Map<String,Object> suffix(Map<String,Object> input) {
        String t = str(input, "text");
        int n = t.length();
        Map<String,Object> out = result();
        if (n == 0) { out.put("sa", new int[0]); out.put("lcp", new int[0]); out.put("repeated", ""); return out; }
        int[] sa = suffixArray(t);
        int[] lcp = kasai(t, sa);
        int best = 0, at = 0;
        for (int i = 1; i < n; i++) if (lcp[i] > best) { best = lcp[i]; at = sa[i]; }
        out.put("sa", sa);
        out.put("lcp", lcp);
        out.put("repeated", t.substring(at, at + best));
        return out;
    }

    /* ------------------------------------------------------------------ */
    /* Part 6: Aho–Corasick with reverse-failure visit propagation         */
    /* ------------------------------------------------------------------ */
    static final int SIGMA = 95, LOW = 32;

    private static int code(char c) {
        int x = c - LOW;
        return (x >= 0 && x < SIGMA) ? x : -1;
    }

    static Map<String,Object> aho(Map<String,Object> input) {
        List<Object> pats = list(input, "patterns");
        List<Object> texts = list(input, "texts");
        int p = pats.size(), q = texts.size();
        Map<String,Object> out = result();
        long[][] counts = new long[q][p];
        if (p == 0 || q == 0) { out.put("counts", counts); return out; }

        int total = 1;
        for (Object o : pats) total += ((String) o).length();
        int[] go = new int[total * SIGMA];
        Arrays.fill(go, -1);
        int states = 1;
        int[] term = new int[p];
        for (int i = 0; i < p; i++) {
            String s = (String) pats.get(i);
            int v = 0;
            for (int j = 0; j < s.length(); j++) {
                int c = code(s.charAt(j));
                if (go[v * SIGMA + c] == -1) go[v * SIGMA + c] = states++;
                v = go[v * SIGMA + c];
            }
            term[i] = v; // duplicates share one terminal state
        }
        // BFS: failure links and complete goto automaton.
        int[] fail = new int[states], bfs = new int[states];
        int qh = 0, qt = 0;
        for (int c = 0; c < SIGMA; c++) {
            int u = go[c];
            if (u == -1) go[c] = 0;
            else { fail[u] = 0; bfs[qt++] = u; }
        }
        while (qh < qt) {
            int v = bfs[qh++];
            int fv = fail[v];
            for (int c = 0; c < SIGMA; c++) {
                int u = go[v * SIGMA + c];
                if (u == -1) go[v * SIGMA + c] = go[fv * SIGMA + c];
                else { fail[u] = go[fv * SIGMA + c]; bfs[qt++] = u; }
            }
        }
        long[] visits = new long[states];
        for (int ti = 0; ti < q; ti++) {
            String t = (String) texts.get(ti);
            Arrays.fill(visits, 0L);
            int v = 0;
            for (int i = 0; i < t.length(); i++) {
                int c = code(t.charAt(i));
                v = c < 0 ? 0 : go[v * SIGMA + c];
                visits[v]++;
            }
            // Deepest states first: push visit counts to failure (suffix) states.
            for (int i = qt - 1; i >= 0; i--) {
                int u = bfs[i];
                visits[fail[u]] += visits[u];
            }
            for (int i = 0; i < p; i++) counts[ti][i] = visits[term[i]];
        }
        out.put("counts", counts);
        return out;
    }
}
