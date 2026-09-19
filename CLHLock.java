import java.util.concurrent.atomic.AtomicReference;

public class CLHLock implements Lock
{
    private static class QNode
    {
        volatile boolean locked = false;
    }

    private final AtomicReference<QNode> tail =
        new AtomicReference<QNode>(new QNode());

    private final ThreadLocal<QNode> myNode =
        new ThreadLocal<QNode>()
        {
            @Override
            protected QNode initialValue()
            {
                return new QNode();
            }
        };

    private final ThreadLocal<QNode> myPred =
        new ThreadLocal<QNode>()
        {
            @Override
            protected QNode initialValue()
            {
                return null;
            }
        };

    @Override
    public void lock()
    {
        QNode node = myNode.get();
        node.locked = true;

        QNode predecessor = tail.getAndSet(node);
        myPred.set(predecessor);

        while(predecessor.locked)
        {
            // Busy wait
        }
    }

    @Override
    public void unlock()
    {
        QNode node = myNode.get();

        node.locked = false;

        // Reuse the predecessor's node.
        myNode.set(myPred.get());
    }
}