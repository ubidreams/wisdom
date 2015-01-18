package org.wisdom.pools;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.wisdom.api.concurrent.ExecutionContextService;
import org.wisdom.api.concurrent.ManagedExecutorService;
import org.wisdom.api.configuration.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * Implementation of the {@link org.wisdom.api.concurrent.ManagedExecutorService}.
 */
public class ManagedExecutorServiceImpl extends AbstractManagedExecutorService
        implements ManagedExecutorService {

    public ManagedExecutorServiceImpl(String name, Configuration configuration, List<ExecutionContextService> ecs) {
        this(
                name,
                configuration.get("threadType", ThreadType.class, ThreadType.POOLED),
                configuration.getDuration("hungTime", TimeUnit.MILLISECONDS, 60000),
                configuration.getIntegerWithDefault("coreSize", 5),
                configuration.getIntegerWithDefault("maxSize", 25),
                configuration.getDuration("keepAlive", TimeUnit.MILLISECONDS, 5000),
                configuration.getIntegerWithDefault("workQueueCapacity",
                        Integer.MAX_VALUE),
                configuration.getIntegerWithDefault("priority", Thread.NORM_PRIORITY),
                ecs);
    }

    public ManagedExecutorServiceImpl(
            String name,
            ThreadType tu,
            long hungTime,
            int coreSize,
            int maxSize,
            long keepAlive,
            int workQueueCapacity,
            int priority,
            List<ExecutionContextService> ecs) {

        super(name, hungTime, ecs);
        ThreadFactoryBuilder builder = new ThreadFactoryBuilder()
                .setDaemon(tu == ThreadType.DAEMON)
                .setNameFormat(name + "-%s")
                .setPriority(priority)
                .setUncaughtExceptionHandler(
                        new Thread.UncaughtExceptionHandler() {
                            @Override
                            public void uncaughtException(Thread t, Throwable e) {
                                logger.error("Uncaught exception in thread '{}'",
                                        t.getName(), e);
                            }
                        });

        BlockingQueue<Runnable> queue = createWorkQueue(workQueueCapacity);
        setInternalPool(new ThreadPoolExecutor(coreSize, maxSize, keepAlive,
                TimeUnit.MILLISECONDS, queue, builder.build()));
    }

    protected BlockingQueue<Runnable> createWorkQueue(int workQueueCapacity) {
        if (workQueueCapacity < 0) {
            throw new IllegalArgumentException();
        }
        BlockingQueue<Runnable> queue;
        if (workQueueCapacity == Integer.MAX_VALUE) {
            queue = new LinkedBlockingQueue<>();
        } else if (workQueueCapacity == 0) {
            queue = new SynchronousQueue<>();
        } else {
            queue = new ArrayBlockingQueue<>(workQueueCapacity);
        }
        return queue;
    }

    protected <V> Task<V> getNewTaskFor(Runnable task, V result) {
        return new Task<>(executor, task, result, createExecutionContext(),
                hungTime);
    }


    protected <V> Task<V> getNewTaskFor(Callable<V> callable) {
        return new Task(executor, callable, createExecutionContext(), hungTime);
    }


    /**
     * Set the context services. For testing purpose only.
     *
     * @param services the context services
     */
    public void setExecutionContextService(ExecutionContextService... services) {
        ecs = new ArrayList<>();
        Collections.addAll(ecs, services);
    }

}
