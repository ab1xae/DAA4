import java.util.*;

public final class Bench {
    static String randomText(Random r, int n, String alpha) {
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) b.append(alpha.charAt(r.nextInt(alpha.length())));
        return b.toString();
    }

    public static void main(String[] args) {
        long seed = 42;
        int[][] sizes = {{1000, 10}, {2000, 100}, {3000, 300}};
        int reps = 50, warm = 200;
        System.out.println("case|n|m|algorithm|run1_us|run2_us|run3_us|median_us|z|outputs_equal");
        for (String kind : new String[]{"random", "repetitive"}) {
            Random r = new Random(seed);
            for (int[] nm : sizes) {
                int n = nm[0], m = nm[1];
                String t, p;
                if (kind.equals("random")) {
                    t = randomText(r, n, "abcdefghijklmnopqrstuvwxyz");
                    int s = r.nextInt(n - m + 1);
                    p = t.substring(s, s + m);
                } else {
                    t = "a".repeat(n);
                    p = "a".repeat(m - 1) + "b";
                    if (m > 1) t = "a".repeat(n - 1) + "b";
                }
                Map<String,Object> in = new HashMap<>();
                in.put("text", t); in.put("pattern", p);
                int[] ref = (int[]) Algorithms.naive(in).get("positions");
                boolean eq = Arrays.equals(ref, (int[]) Algorithms.kmp(in).get("positions"))
                        && Arrays.equals(ref, (int[]) Algorithms.rk(in).get("positions"));
                for (String alg : new String[]{"naive", "kmp", "rk"}) {
                    for (int i = 0; i < warm; i++) call(alg, in);
                    double[] runs = new double[3];
                    for (int k = 0; k < 3; k++) {
                        long t0 = System.nanoTime();
                        for (int i = 0; i < reps; i++) call(alg, in);
                        runs[k] = (System.nanoTime() - t0) / 1000.0 / reps;
                    }
                    double[] s = runs.clone();
                    Arrays.sort(s);
                    System.out.printf(Locale.ROOT, "%s|%d|%d|%s|%.1f|%.1f|%.1f|%.1f|%d|%b%n",
                            kind, n, m, alg, runs[0], runs[1], runs[2], s[1], ref.length, eq);
                }
            }
        }
    }

    static Object call(String alg, Map<String,Object> in) {
        switch (alg) {
            case "naive": return Algorithms.naive(in);
            case "kmp": return Algorithms.kmp(in);
            default: return Algorithms.rk(in);
        }
    }
}
