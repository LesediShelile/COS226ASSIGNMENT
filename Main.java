import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Task 3 - experiment driver.
 *
 * Usage:   java Main [iterationsPerThread] [runsPerConfiguration] [threadCounts]
 * Example: java Main                    (10000 iterations, 5 runs, threads 2,4,8,16)
 *          java Main 50000 7 2,4,8,16,32
 *
 * For every thread count and every lock (TTAS, CLH, MCS):
 *   - one discarded warm-up run (lets the JIT compile the lock code),
 *   - then `runs` measured runs, each with a fresh lock and a fresh Auction.
 * The order of the three locks is rotated from run to run, so no lock is
 * always measured first (or always right after a heavy neighbour).
 *
 * Output: tables on the console, plus results.md (tables) and results.csv (raw data).
 */
public class Main
{
    private static final String[] LOCK_NAMES = {"TTAS", "CLH", "MCS"};

    private static Lock createLock(int lockIndex)
    {
        switch(lockIndex)
        {
            case 0:
                return new TTASLock();
            case 1:
                return new CLHLock();
            default:
                return new MCSLock();
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException
    {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 10000;
        int runs = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        int[] threadCounts = {2, 4, 8, 16};

        if(args.length > 2)
        {
            String[] parts = args[2].split(",");
            threadCounts = new int[parts.length];
            for(int i = 0; i < parts.length; i++)
            {
                threadCounts[i] = Integer.parseInt(parts[i].trim());
            }
        }

        int cores = Runtime.getRuntime().availableProcessors();

        System.out.println("COS 226 Assignment 1 - Task 3: TTAS vs CLH vs MCS");
        System.out.println("Cores available: " + cores + ", iterations/thread: " + iterations
            + ", measured runs/config: " + runs);

        for(int t : threadCounts)
        {
            if(t > cores)
            {
                System.out.println("WARNING: " + t + " threads > " + cores + " cores. The lock spins on a core, so "
                    + "threads that are not scheduled cannot pass on / take the lock. Expect much slower "
                    + "(especially queue-lock) results for this configuration.");
            }
        }
        System.out.println();

        @SuppressWarnings({"unchecked", "rawtypes"})
        List<ExperimentRunner.Result>[][] data = new List[LOCK_NAMES.length][threadCounts.length];
        for(int l = 0; l < LOCK_NAMES.length; l++)
        {
            for(int t = 0; t < threadCounts.length; t++)
            {
                data[l][t] = new ArrayList<ExperimentRunner.Result>();
            }
        }

        for(int t = 0; t < threadCounts.length; t++)
        {
            int threads = threadCounts[t];

            // Warm-up (discarded).
            for(int l = 0; l < LOCK_NAMES.length; l++)
            {
                runOnce(l, threads, iterations);
            }

            for(int run = 0; run < runs; run++)
            {
                for(int k = 0; k < LOCK_NAMES.length; k++)
                {
                    int l = (run + k) % LOCK_NAMES.length;   // rotate the lock order every run

                    ExperimentRunner.Result r = runOnce(l, threads, iterations);
                    data[l][t].add(r);

                    System.out.println(String.format(Locale.ROOT,
                        "%-4s threads=%-2d run %d/%d  time=%9.2f ms  bids=%d  final=%.0f  %s",
                        LOCK_NAMES[l], threads, run + 1, runs, r.executionTimeNanos / 1e6,
                        r.totalBids, r.finalHighestBid, r.isCorrect() ? "OK" : "*** WRONG ***"));
                }
            }
            System.out.println();
        }

        String report = buildReport(data, threadCounts, iterations, runs, cores);
        System.out.println(report);

        try(PrintWriter out = new PrintWriter("results.md", "UTF-8"))
        {
            out.print(report);
        }
        try(PrintWriter out = new PrintWriter("results.csv", "UTF-8"))
        {
            out.print(buildCsv(data, threadCounts));
        }

        System.out.println("Wrote results.md and results.csv");
    }

    private static ExperimentRunner.Result runOnce(int lockIndex, int threads, int iterations)
        throws InterruptedException
    {
        Auction auction = new Auction(AuctionUtils.generateItemName());
        Lock lock = createLock(lockIndex);
        return new ExperimentRunner(threads, iterations, auction, lock).run();
    }

    // ------------------------------------------------------------------ reporting

    private static String f(double value, int decimals)
    {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static double mean(double[] v)
    {
        double s = 0.0;
        for(double x : v)
        {
            s += x;
        }
        return v.length == 0 ? 0.0 : s / v.length;
    }

    /** Sample standard deviation. */
    private static double sd(double[] v)
    {
        if(v.length < 2)
        {
            return 0.0;
        }
        double m = mean(v);
        double s = 0.0;
        for(double x : v)
        {
            s += (x - m) * (x - m);
        }
        return Math.sqrt(s / (v.length - 1));
    }

    private static double max(double[] v)
    {
        double m = Double.NEGATIVE_INFINITY;
        for(double x : v)
        {
            m = Math.max(m, x);
        }
        return m;
    }

    private static double[] extract(List<ExperimentRunner.Result> rs, java.util.function.ToDoubleFunction<ExperimentRunner.Result> fn)
    {
        return rs.stream().mapToDouble(fn).toArray();
    }

    private static String buildReport(List<ExperimentRunner.Result>[][] data, int[] threadCounts,
                                      int iterations, int runs, int cores)
    {
        StringBuilder sb = new StringBuilder();
        int nl = LOCK_NAMES.length;
        int maxThreads = 0;
        for(int t : threadCounts)
        {
            maxThreads = Math.max(maxThreads, t);
        }

        sb.append("# Task 3 - Experimental results\n\n");
        sb.append("## Environment and parameters\n\n");
        sb.append("| Item | Value |\n|---|---|\n");
        sb.append("| Java | ").append(System.getProperty("java.version")).append(" (")
          .append(System.getProperty("java.vm.name")).append(") |\n");
        sb.append("| OS | ").append(System.getProperty("os.name")).append(" ")
          .append(System.getProperty("os.arch")).append(" |\n");
        sb.append("| Logical CPU cores available | ").append(cores).append(" |\n");
        sb.append("| Bidding iterations per thread (constant for all locks) | ").append(iterations).append(" |\n");
        sb.append("| Measured runs per (lock, threads) | ").append(runs).append(" (+1 discarded warm-up) |\n\n");

        // ---- Table 1: execution time
        sb.append("## Table 1 - Total execution time\n\n");
        sb.append("Mean +- sample standard deviation over ").append(runs).append(" runs, in milliseconds.\n\n");
        sb.append("| Threads |");
        for(String n : LOCK_NAMES)
        {
            sb.append(" ").append(n).append(" (ms) |");
        }
        sb.append("\n|---|");
        for(int l = 0; l < nl; l++)
        {
            sb.append("---|");
        }
        sb.append("\n");
        for(int t = 0; t < threadCounts.length; t++)
        {
            sb.append("| ").append(threadCounts[t]).append(" |");
            for(int l = 0; l < nl; l++)
            {
                double[] ms = extract(data[l][t], r -> r.executionTimeNanos / 1e6);
                sb.append(" ").append(f(mean(ms), 2)).append(" +- ").append(f(sd(ms), 2)).append(" |");
            }
            sb.append("\n");
        }

        sb.append("\n### Table 1b - Time per bid (total time / total bids)\n\n");
        sb.append("The total work grows with the number of threads (each thread does the same number of "
            + "iterations), so this normalised value isolates the cost of contention.\n\n");
        sb.append("| Threads |");
        for(String n : LOCK_NAMES)
        {
            sb.append(" ").append(n).append(" (us/bid) |");
        }
        sb.append("\n|---|");
        for(int l = 0; l < nl; l++)
        {
            sb.append("---|");
        }
        sb.append("\n");
        for(int t = 0; t < threadCounts.length; t++)
        {
            sb.append("| ").append(threadCounts[t]).append(" |");
            for(int l = 0; l < nl; l++)
            {
                double[] us = extract(data[l][t], r -> r.executionTimeNanos / 1e3 / r.expectedBids());
                sb.append(" ").append(f(mean(us), 3)).append(" |");
            }
            sb.append("\n");
        }

        // ---- Table 2: workload verification
        sb.append("\n## Table 2 - Workload verification (total bids and final highest bid)\n\n");
        sb.append("Every bid is exactly 1.0 above the previous highest bid, so with correct mutual exclusion "
            + "the final highest bid equals the total number of bids = threads x iterations.\n\n");
        sb.append("| Threads | Lock | Expected bids | Total bids (mean) | Final highest bid (mean) | Correct runs |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for(int t = 0; t < threadCounts.length; t++)
        {
            for(int l = 0; l < nl; l++)
            {
                List<ExperimentRunner.Result> rs = data[l][t];
                long ok = rs.stream().filter(ExperimentRunner.Result::isCorrect).count();
                sb.append("| ").append(threadCounts[t]).append(" | ").append(LOCK_NAMES[l]).append(" | ")
                  .append((long) threadCounts[t] * iterations).append(" | ")
                  .append(f(mean(extract(rs, r -> r.totalBids)), 0)).append(" | ")
                  .append(f(mean(extract(rs, r -> r.finalHighestBid)), 0)).append(" | ")
                  .append(ok).append("/").append(rs.size()).append(" |\n");
            }
        }

        // ---- Table 3: bids won by each bidder
        sb.append("\n## Table 3 - Bids won by each bidder (whole auction)\n\n");
        sb.append("Mean over runs. B0..B").append(maxThreads - 1).append(" are the bidder ids.\n\n");
        appendPerBidderTable(sb, data, threadCounts, maxThreads, false);

        // ---- Table 4: additional measurement (lock waiting time)
        sb.append("\n## Table 4 - Additional measurement: time spent waiting to acquire the lock\n\n");
        sb.append("Measured per acquisition (System.nanoTime() before lock() to after lock() returns), all "
            + "acquisitions of all threads pooled per run. Mean, 99th percentile and maximum are averaged "
            + "over runs; \"worst max\" is the largest single wait seen in any run. Units: microseconds.\n\n");
        sb.append("| Threads | Lock | Mean wait | P99 wait | Max wait (mean of runs) | Worst max (any run) |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for(int t = 0; t < threadCounts.length; t++)
        {
            for(int l = 0; l < nl; l++)
            {
                List<ExperimentRunner.Result> rs = data[l][t];
                double[] maxes = extract(rs, r -> r.maxWaitNanos / 1e3);
                sb.append("| ").append(threadCounts[t]).append(" | ").append(LOCK_NAMES[l]).append(" | ")
                  .append(f(mean(extract(rs, r -> r.meanWaitNanos / 1e3)), 3)).append(" | ")
                  .append(f(mean(extract(rs, r -> r.p99WaitNanos / 1e3)), 3)).append(" | ")
                  .append(f(mean(maxes), 1)).append(" | ")
                  .append(f(max(maxes), 1)).append(" |\n");
            }
        }

        // ---- Table 5: fairness
        sb.append("\n## Table 5 - Fairness (distribution of access to the auction)\n\n");
        sb.append("Over the whole auction every bidder wins exactly `iterations` bids (fixed workload), so "
            + "fairness is examined over the *first half* of the auction, i.e. bids 1 .. threads x iterations / 2, "
            + "while all threads are still competing. A perfectly fair lock gives every bidder "
            + "about iterations/2 of them. Jain's index = (sum x)^2 / (n * sum x^2): 1.0 = perfectly even.\n\n");
        sb.append("| Threads | Lock | Fair share | Min bidder | Max bidder | Jain index | Finish-time spread (ms) |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for(int t = 0; t < threadCounts.length; t++)
        {
            for(int l = 0; l < nl; l++)
            {
                List<ExperimentRunner.Result> rs = data[l][t];
                sb.append("| ").append(threadCounts[t]).append(" | ").append(LOCK_NAMES[l]).append(" | ")
                  .append(f(iterations / 2.0, 0)).append(" | ")
                  .append(f(mean(extract(rs, r -> r.minFirstHalf())), 1)).append(" | ")
                  .append(f(mean(extract(rs, r -> r.maxFirstHalf())), 1)).append(" | ")
                  .append(f(mean(extract(rs, r -> r.jainIndexFirstHalf())), 4)).append(" | ")
                  .append(f(mean(extract(rs, r -> r.finishSpreadNanos / 1e6)), 2)).append(" |\n");
            }
        }

        sb.append("\n### Table 5b - Bids won per bidder in the first half of the auction\n\n");
        sb.append("Mean over runs.\n\n");
        appendPerBidderTable(sb, data, threadCounts, maxThreads, true);

        return sb.toString();
    }

    private static void appendPerBidderTable(StringBuilder sb, List<ExperimentRunner.Result>[][] data,
                                             int[] threadCounts, int maxThreads, boolean firstHalf)
    {
        sb.append("| Threads | Lock |");
        for(int b = 0; b < maxThreads; b++)
        {
            sb.append(" B").append(b).append(" |");
        }
        sb.append("\n|---|---|");
        for(int b = 0; b < maxThreads; b++)
        {
            sb.append("---|");
        }
        sb.append("\n");

        for(int t = 0; t < threadCounts.length; t++)
        {
            for(int l = 0; l < LOCK_NAMES.length; l++)
            {
                List<ExperimentRunner.Result> rs = data[l][t];
                sb.append("| ").append(threadCounts[t]).append(" | ").append(LOCK_NAMES[l]).append(" |");
                for(int b = 0; b < maxThreads; b++)
                {
                    if(b >= threadCounts[t])
                    {
                        sb.append(" |");
                        continue;
                    }
                    final int bidder = b;
                    double m = mean(extract(rs, r -> firstHalf(r, bidder, firstHalf)));
                    sb.append(" ").append(f(m, firstHalf ? 1 : 0)).append(" |");
                }
                sb.append("\n");
            }
        }
    }

    private static double firstHalf(ExperimentRunner.Result r, int bidder, boolean firstHalf)
    {
        return firstHalf ? r.bidsWonFirstHalf[bidder] : r.bidsWon[bidder];
    }

    private static String buildCsv(List<ExperimentRunner.Result>[][] data, int[] threadCounts)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("lock,threads,run,iterations,exec_ns,total_bids,final_highest_bid,final_highest_bidder,"
            + "mean_wait_ns,p99_wait_ns,max_wait_ns,finish_spread_ns,jain_first_half,"
            + "bids_won_per_bidder,bids_won_first_half_per_bidder\n");
        for(int l = 0; l < LOCK_NAMES.length; l++)
        {
            for(int t = 0; t < threadCounts.length; t++)
            {
                List<ExperimentRunner.Result> rs = data[l][t];
                for(int i = 0; i < rs.size(); i++)
                {
                    ExperimentRunner.Result r = rs.get(i);
                    sb.append(LOCK_NAMES[l]).append(',').append(threadCounts[t]).append(',').append(i + 1)
                      .append(',').append(r.iterations).append(',').append(r.executionTimeNanos)
                      .append(',').append(r.totalBids).append(',').append(f(r.finalHighestBid, 0))
                      .append(',').append(r.finalHighestBidder).append(',').append(f(r.meanWaitNanos, 1))
                      .append(',').append(r.p99WaitNanos).append(',').append(r.maxWaitNanos)
                      .append(',').append(r.finishSpreadNanos).append(',').append(f(r.jainIndexFirstHalf(), 4))
                      .append(',').append(join(r.bidsWon)).append(',').append(join(r.bidsWonFirstHalf))
                      .append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static String join(int[] v)
    {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < v.length; i++)
        {
            if(i > 0)
            {
                sb.append(';');
            }
            sb.append(v[i]);
        }
        return sb.toString();
    }
}
