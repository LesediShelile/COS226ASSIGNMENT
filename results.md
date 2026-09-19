# Task 3 - Experimental results

## Environment and parameters

| Item | Value |
|---|---|
| Java | 1.8.0_452 (OpenJDK 64-Bit Server VM) |
| OS | Linux amd64 |
| Logical CPU cores available | 8 |
| Bidding iterations per thread (constant for all locks) | 1000 |
| Measured runs per (lock, threads) | 5 (+1 discarded warm-up) |

## Table 1 - Total execution time

Mean +- sample standard deviation over 5 runs, in milliseconds.

| Threads | TTAS (ms) | CLH (ms) | MCS (ms) |
|---|---|---|---|
| 2 | 1.39 +- 0.77 | 2.77 +- 2.11 | 2.13 +- 1.65 |
| 4 | 1.09 +- 0.35 | 2.13 +- 1.04 | 1.25 +- 0.41 |
| 8 | 2.02 +- 0.27 | 10.69 +- 15.18 | 229.96 +- 509.71 |
| 16 | 8.10 +- 4.64 | 10474.26 +- 11407.16 | 174.74 +- 377.08 |

### Table 1b - Time per bid (total time / total bids)

The total work grows with the number of threads (each thread does the same number of iterations), so this normalised value isolates the cost of contention.

| Threads | TTAS (us/bid) | CLH (us/bid) | MCS (us/bid) |
|---|---|---|---|
| 2 | 0.695 | 1.385 | 1.067 |
| 4 | 0.273 | 0.534 | 0.312 |
| 8 | 0.253 | 1.337 | 28.745 |
| 16 | 0.506 | 654.641 | 10.921 |

## Table 2 - Workload verification (total bids and final highest bid)

Every bid is exactly 1.0 above the previous highest bid, so with correct mutual exclusion the final highest bid equals the total number of bids = threads x iterations.

| Threads | Lock | Expected bids | Total bids (mean) | Final highest bid (mean) | Correct runs |
|---|---|---|---|---|---|
| 2 | TTAS | 2000 | 2000 | 2000 | 5/5 |
| 2 | CLH | 2000 | 2000 | 2000 | 5/5 |
| 2 | MCS | 2000 | 2000 | 2000 | 5/5 |
| 4 | TTAS | 4000 | 4000 | 4000 | 5/5 |
| 4 | CLH | 4000 | 4000 | 4000 | 5/5 |
| 4 | MCS | 4000 | 4000 | 4000 | 5/5 |
| 8 | TTAS | 8000 | 8000 | 8000 | 5/5 |
| 8 | CLH | 8000 | 8000 | 8000 | 5/5 |
| 8 | MCS | 8000 | 8000 | 8000 | 5/5 |
| 16 | TTAS | 16000 | 16000 | 16000 | 5/5 |
| 16 | CLH | 16000 | 16000 | 16000 | 5/5 |
| 16 | MCS | 16000 | 16000 | 16000 | 5/5 |

## Table 3 - Bids won by each bidder (whole auction)

Mean over runs. B0..B15 are the bidder ids.

| Threads | Lock | B0 | B1 | B2 | B3 | B4 | B5 | B6 | B7 | B8 | B9 | B10 | B11 | B12 | B13 | B14 | B15 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | TTAS | 1000 | 1000 | | | | | | | | | | | | | | |
| 2 | CLH | 1000 | 1000 | | | | | | | | | | | | | | |
| 2 | MCS | 1000 | 1000 | | | | | | | | | | | | | | |
| 4 | TTAS | 1000 | 1000 | 1000 | 1000 | | | | | | | | | | | | |
| 4 | CLH | 1000 | 1000 | 1000 | 1000 | | | | | | | | | | | | |
| 4 | MCS | 1000 | 1000 | 1000 | 1000 | | | | | | | | | | | | |
| 8 | TTAS | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | | | | | | | | |
| 8 | CLH | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | | | | | | | | |
| 8 | MCS | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | | | | | | | | |
| 16 | TTAS | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 |
| 16 | CLH | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 |
| 16 | MCS | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 | 1000 |

## Table 4 - Additional measurement: time spent waiting to acquire the lock

Measured per acquisition (System.nanoTime() before lock() to after lock() returns), all acquisitions of all threads pooled per run. Mean, 99th percentile and maximum are averaged over runs; "worst max" is the largest single wait seen in any run. Units: microseconds.

| Threads | Lock | Mean wait | P99 wait | Max wait (mean of runs) | Worst max (any run) |
|---|---|---|---|---|---|
| 2 | TTAS | 0.502 | 0.976 | 77.5 | 144.2 |
| 2 | CLH | 1.367 | 10.058 | 629.6 | 2245.3 |
| 2 | MCS | 1.030 | 9.984 | 338.1 | 1312.3 |
| 4 | TTAS | 0.451 | 3.426 | 216.7 | 806.9 |
| 4 | CLH | 1.179 | 6.894 | 504.6 | 915.6 |
| 4 | MCS | 0.484 | 0.797 | 90.7 | 191.9 |
| 8 | TTAS | 0.556 | 4.862 | 211.7 | 508.5 |
| 8 | CLH | 8.390 | 39.703 | 2817.7 | 7171.5 |
| 8 | MCS | 228.570 | 3002.725 | 9138.1 | 45040.6 |
| 16 | TTAS | 2.624 | 8.839 | 3976.7 | 9081.5 |
| 16 | CLH | 8056.037 | 38581.243 | 123955.0 | 302784.1 |
| 16 | MCS | 104.883 | 1655.650 | 7373.0 | 27268.6 |

## Table 5 - Fairness (distribution of access to the auction)

Over the whole auction every bidder wins exactly `iterations` bids (fixed workload), so fairness is examined over the *first half* of the auction, i.e. bids 1 .. threads x iterations / 2, while all threads are still competing. A perfectly fair lock gives every bidder about iterations/2 of them. Jain's index = (sum x)^2 / (n * sum x^2): 1.0 = perfectly even.

| Threads | Lock | Fair share | Min bidder | Max bidder | Jain index | Finish-time spread (ms) |
|---|---|---|---|---|---|---|
| 2 | TTAS | 500 | 374.8 | 625.2 | 0.9357 | 0.27 |
| 2 | CLH | 500 | 455.8 | 544.2 | 0.9882 | 0.08 |
| 2 | MCS | 500 | 446.0 | 554.0 | 0.9807 | 0.37 |
| 4 | TTAS | 500 | 106.6 | 893.8 | 0.6939 | 0.46 |
| 4 | CLH | 500 | 210.0 | 815.4 | 0.7912 | 0.59 |
| 4 | MCS | 500 | 224.0 | 885.6 | 0.7558 | 0.60 |
| 8 | TTAS | 500 | 28.4 | 1000.0 | 0.5608 | 1.72 |
| 8 | CLH | 500 | 69.2 | 1000.0 | 0.6936 | 9.21 |
| 8 | MCS | 500 | 177.6 | 952.0 | 0.7726 | 1.33 |
| 16 | TTAS | 500 | 0.0 | 1000.0 | 0.5686 | 6.99 |
| 16 | CLH | 500 | 130.8 | 1000.0 | 0.6234 | 10473.90 |
| 16 | MCS | 500 | 0.0 | 1000.0 | 0.5486 | 174.24 |

### Table 5b - Bids won per bidder in the first half of the auction

Mean over runs.

| Threads | Lock | B0 | B1 | B2 | B3 | B4 | B5 | B6 | B7 | B8 | B9 | B10 | B11 | B12 | B13 | B14 | B15 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | TTAS | 625.2 | 374.8 | | | | | | | | | | | | | | |
| 2 | CLH | 537.8 | 462.2 | | | | | | | | | | | | | | |
| 2 | MCS | 485.2 | 514.8 | | | | | | | | | | | | | | |
| 4 | TTAS | 622.2 | 759.0 | 322.2 | 296.6 | | | | | | | | | | | | |
| 4 | CLH | 797.8 | 629.0 | 363.2 | 210.0 | | | | | | | | | | | | |
| 4 | MCS | 843.6 | 615.2 | 317.2 | 224.0 | | | | | | | | | | | | |
| 8 | TTAS | 1000.0 | 997.8 | 721.6 | 728.8 | 290.6 | 98.4 | 117.0 | 45.8 | | | | | | | | |
| 8 | CLH | 1000.0 | 711.4 | 736.6 | 669.0 | 339.6 | 208.8 | 252.8 | 81.8 | | | | | | | | |
| 8 | MCS | 952.0 | 765.6 | 664.0 | 532.2 | 354.8 | 291.0 | 196.8 | 243.6 | | | | | | | | |
| 16 | TTAS | 1000.0 | 1000.0 | 815.2 | 667.8 | 859.4 | 988.0 | 979.6 | 686.0 | 326.0 | 214.0 | 195.8 | 44.2 | 20.8 | 9.0 | 0.0 | 194.2 |
| 16 | CLH | 1000.0 | 996.2 | 710.4 | 869.0 | 910.4 | 772.0 | 553.8 | 697.6 | 371.0 | 331.8 | 131.8 | 131.6 | 131.4 | 131.2 | 131.0 | 130.8 |
| 16 | MCS | 1000.0 | 1000.0 | 995.0 | 999.8 | 790.4 | 891.6 | 843.0 | 834.4 | 561.2 | 69.0 | 15.6 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 |
