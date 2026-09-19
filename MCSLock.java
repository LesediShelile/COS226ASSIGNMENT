import java.util.concurrent.atomic.AtomicReference;

public class MCSLock implements Lock
{
    private static class QNode
    {
        volatile boolean locked = false;
        volatile QNode next = null;
    }

    private final AtomicReference<QNode> tail =
        new AtomicReference<QNode>(null);

    private final ThreadLocal<QNode> myNode =
        new ThreadLocal<QNode>()
        {
            @Override
            protected QNode initialValue()
            {
                return new QNode();
            }
        };

    @Override
    public void lock()
    {
        QNode node = myNode.get();

        node.locked = true;
        node.next = null;

        QNode predecessor = tail.getAndSet(node);

        if(predecessor != null)
        {
            predecessor.next = node;

            while(node.locked)
            {
                // Busy wait
            }
        }
    }

    @Override
    public void unlock()
    {
        QNode node = myNode.get();

        if(node.next == null)
        {
            if(tail.compareAndSet(node, null))
            {
                return;
            }

            // A successor is joining the queue.
            while(node.next == null)
            {
                // Busy wait
            }
        }

        // Pass ownership to the successor.
        node.next.locked = false;
        node.next = null;
    }
}