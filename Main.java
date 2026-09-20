public class Main 
{

    public static void main(String[] args) throws InterruptedException 
    {
        int numberOfThreads = 0; /*Change*/
        int iterations = 200;

        Auction auction =new Auction(AuctionUtils.generateItemName());
        Lock lock = new Lock(); /*Add your lock here*/
        Runner runner = new Runner(numberOfThreads,iterations,auction,lock);
        runner.run();
    }
}