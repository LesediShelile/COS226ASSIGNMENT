import java.util.concurrent.atomic.AtomicBoolean;

public class TTASLock implements Lock
{
    private final AtomicBoolean state = new AtomicBoolean(false);

    @Override
    public void lock()
    {
        while(true)
        {
            // Test
            while(state.get())
            {
                // Busy wait
            }

            // Test-and-set
            if(state.compareAndSet(false, true))
            {
                return;
            }
        }
    }

    @Override
    public void unlock()
    {
        state.set(false);
    }
}