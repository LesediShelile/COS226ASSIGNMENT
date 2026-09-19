public class Task1LockTest
{
    private static int counter = 0;
    private static final int THREADS = 8;
    private static final int ITERATIONS = 1000;

    private static void testLock(Lock lock, String name)
        throws InterruptedException
    {
        counter = 0;
        Thread[] threads = new Thread[THREADS];

        for(int i = 0; i < THREADS; i++)
        {
            threads[i] = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    for(int j = 0; j < ITERATIONS; j++)
                    {
                        lock.lock();

                        try
                        {
                            counter++;
                        }
                        finally
                        {
                            lock.unlock();
                        }
                    }
                }
            });
        }

        for(Thread thread : threads)
        {
            thread.start();
        }

        for(Thread thread : threads)
        {
            thread.join();
        }

        int expected = THREADS * ITERATIONS;

        System.out.println(name);
        System.out.println("Expected counter: " + expected);
        System.out.println("Actual counter:   " + counter);
        System.out.println("Mutual exclusion: " +
            (counter == expected ? "PASSED" : "FAILED"));
        System.out.println();
    }

    public static void main(String[] args) throws InterruptedException
    {
        testLock(new TTASLock(), "TTAS");
        testLock(new CLHLock(), "CLH");
        testLock(new MCSLock(), "MCS");
    }
}
