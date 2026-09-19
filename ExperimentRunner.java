import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * Task 3 - instrumented version of Runner.
 *
 * Runs ONE experiment (one lock, one thread count) and returns a Result.
 * An instance is single-use: create a new ExperimentRunner (with a NEW Auction
 * and a NEW lock) for every run.
 *
 * Bidder behaviour is identical to the Task 2 bidder (lock -> read highest bid
 * -> bid 1.0 higher -> submit -> unlock); the only additions are measurements.
 *
 * Design notes (worth knowing for the demo):
 *  - All bidder threads are created first and released together through a
 *    start latch, so thread start-up cost is NOT part of the measured time and
 *    all threads compete from the same instant.
 *  - Every counter/array is thread-local while the bidder runs and is only
 *    copied into the shared result arrays once, after the last iteration.
 *    Threads therefore never write to shared measurement data during the
 *    experiment (no extra contention, no false sharing caused by measuring).
 *  - Waiting time = System.nanoTime() just before lock() to just after lock()
 *    returns. The second nanoTime() call sits inside the critical section
 *    (about 20-30 ns); this overhead is the same for all three locks.
 */
public class ExperimentRunner
{
    /** All measurements of a single run. */
    public static class Result
    {
        public int numberOfThreads;
        public int iterations;

        public long executionTimeNanos;      // all threads released -> last bidder finished
        public long totalBids;               // sum of bids won by all bidders
        public double finalHighestBid;
        public int finalHighestBidder;
        public int[] bidsWon;                // per bidder, whole auction
        public int[] bidsWonFirstHalf;       // per bidder, first half of the auction

        public double meanWaitNanos;         // additional measurement: lock waiting time
        public long p99WaitNanos;
        public long maxWaitNanos;

        public long finishSpreadNanos;       // last finisher - first finisher

        public long expectedBids()
        {
            return (long) numberOfThreads * iterations;
        }

        /** True if the workload was completed and no bid was lost. */
        public boolean isCorrect()
        {
            if(totalBids != expectedBids())
            {
                return false;
            }
            if(finalHighestBid != (double) expectedBids())
            {
                return false;
            }
            for(int won : bidsWon)
            {
                if(won != iterations)
                {
                    return false;
                }
            }
            return true;
        }

        /** Jain's fairness index over bidsWonFirstHalf: 1.0 = perfectly even, 1/n = one bidder took everything. */
        public double jainIndexFirstHalf()
        {
            double sum = 0.0;
            double sumSquares = 0.0;
            for(int x : bidsWonFirstHalf)
            {
                sum += x;
                sumSquares += (double) x * x;
            }
            if(sumSquares == 0.0)
            {
                return 1.0;
            }
            return (sum * sum) / (bidsWonFirstHalf.length * sumSquares);
        }

        public int minFirstHalf()
        {
            return Arrays.stream(bidsWonFirstHalf).min().orElse(0);
        }

        public int maxFirstHalf()
        {
            return Arrays.stream(bidsWonFirstHalf).max().orElse(0);
        }
    }

    private final int numberOfThreads;
    private final int iterations;
    private final Auction auction;
    private final Lock lock;

    private final CountDownLatch ready;
    private final CountDownLatch go = new CountDownLatch(1);

    // Written once per bidder after its last iteration, read by main after join().
    private final int[] bidsWon;
    private final int[] bidsWonFirstHalf;
    private final long[][] waits;
    private final long[] finishTimes;

    public ExperimentRunner(int numberOfThreads, int iterations, Auction auction, Lock lock)
    {
        this.numberOfThreads = numberOfThreads;
        this.iterations = iterations;
        this.auction = auction;
        this.lock = lock;

        this.ready = new CountDownLatch(numberOfThreads);
        this.bidsWon = new int[numberOfThreads];
        this.bidsWonFirstHalf = new int[numberOfThreads];
        this.waits = new long[numberOfThreads][];
        this.finishTimes = new long[numberOfThreads];
    }

    public Result run() throws InterruptedException
    {
        Thread[] threads = new Thread[numberOfThreads];

        for(int i = 0; i < numberOfThreads; i++)
        {
            final int bidderId = i;

            threads[i] = new Thread(() -> {
                try
                {
                    bidder(bidderId);
                }
                catch(InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }, "bidder-" + i);
        }

        for(Thread thread : threads)
        {
            thread.start();
        }

        // Wait until every bidder is set up, then release them all at once.
        ready.await();
        long startTime = System.nanoTime();
        go.countDown();

        long warnAt = startTime + 30_000_000_000L;
        boolean warned = false;

        for(Thread thread : threads)
        {
            while(thread.isAlive())
            {
                thread.join(1000);

                if(!warned && System.nanoTime() > warnAt)
                {
                    System.err.println("  ...still running after 30 s. If you have fewer CPU cores than bidder "
                        + "threads, spinning locks (especially CLH/MCS) slow down enormously. "
                        + "Lower the iteration count if this is too slow.");
                    warned = true;
                }
            }
        }

        return collect(startTime);
    }

    /** Behaviour of one bidder: same logic as the Task 2 bidder, plus measurements. */
    private void bidder(int bidderId) throws InterruptedException
    {
        long[] myWaits = new long[iterations];
        int won = 0;
        int wonFirstHalf = 0;
        final double halfMark = ((double) numberOfThreads * iterations) / 2.0;

        ready.countDown();
        go.await();

        for(int i = 0; i < iterations; i++)
        {
            long requested = System.nanoTime();
            lock.lock();
            long acquired = System.nanoTime();

            try
            {
                double currentBid = auction.getHighestBid();
                double newBid = currentBid + 1.0;

                auction.placeBid(bidderId, newBid);

                // This bidder "won" the bid if it is now the highest bidder at the bid it just placed.
                if(auction.getHighestBidder() == bidderId && auction.getHighestBid() == newBid)
                {
                    won++;

                    if(newBid <= halfMark)
                    {
                        wonFirstHalf++;
                    }
                }
            }
            finally
            {
                lock.unlock();
            }

            myWaits[i] = acquired - requested;
        }

        finishTimes[bidderId] = System.nanoTime();
        waits[bidderId] = myWaits;
        bidsWon[bidderId] = won;
        bidsWonFirstHalf[bidderId] = wonFirstHalf;
    }

    private Result collect(long startTime)
    {
        Result r = new Result();
        r.numberOfThreads = numberOfThreads;
        r.iterations = iterations;

        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        for(long t : finishTimes)
        {
            first = Math.min(first, t);
            last = Math.max(last, t);
        }
        r.executionTimeNanos = last - startTime;
        r.finishSpreadNanos = last - first;

        r.finalHighestBid = auction.getHighestBid();
        r.finalHighestBidder = auction.getHighestBidder();
        r.bidsWon = bidsWon.clone();
        r.bidsWonFirstHalf = bidsWonFirstHalf.clone();

        long total = 0;
        for(int won : bidsWon)
        {
            total += won;
        }
        r.totalBids = total;

        int count = numberOfThreads * iterations;
        long[] all = new long[count];
        int pos = 0;
        double sum = 0.0;
        for(long[] w : waits)
        {
            for(long x : w)
            {
                all[pos++] = x;
                sum += x;
            }
        }
        Arrays.sort(all);
        r.meanWaitNanos = sum / count;
        r.p99WaitNanos = all[(int) Math.ceil(0.99 * count) - 1];
        r.maxWaitNanos = all[count - 1];

        return r;
    }
}
