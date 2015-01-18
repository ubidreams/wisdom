package org.wisdom.pools;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wisdom.api.concurrent.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Common methods used in the different
 * {@link org.wisdom.api.concurrent.ManagedExecutorService} implementations.
 */
public abstract class AbstractManagedExecutorService implements ManagedExecutorService {

    protected final String name;
    protected final long hungTime;

    protected ListeningExecutorService executor;
    protected ThreadPoolExecutor internalPool;

    protected final Set<Task<?>> tasks = new LinkedHashSet<>();
    protected final Logger logger;

    protected List<ExecutionContextService> ecs;

    protected AbstractManagedExecutorService(String name, long hungTime, List<ExecutionContextService> ecs) {
        Preconditions.checkNotNull(name);
        this.name = name;
        this.logger = LoggerFactory.getLogger("executor-" + name);
        this.hungTime = hungTime;
        this.ecs = ecs;
    }

    protected AbstractManagedExecutorService setInternalPool(ThreadPoolExecutor executor) {
        this.internalPool = executor;
        this.executor = MoreExecutors.listeningDecorator(this.internalPool);
        return this;
    }

    protected ThreadPoolExecutor getInternalPool() {
        return internalPool;
    }

    protected ListeningExecutorService getExecutor() {
        return executor;
    }

    public String name() {
        return this.name;
    }

    @Override
    public Collection<ManagedFutureTask> getHungTasks() {
        List<ManagedFutureTask> hung = new ArrayList<>();
        for (ManagedFutureTask task : tasks) {
            if (task.isTaskHang()) {
                hung.add(task);
            }
        }
        return hung;
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }


    @Override
    public List<Runnable> shutdownNow() {
        for (Task task : tasks) {
            task.cancel(true);
        }
        return executor.shutdownNow();
    }

    protected ExecutionContext createExecutionContext() {
        if (ecs == null) {
            return null;
        }
        List<ExecutionContextService> copy = new ArrayList<>(ecs);
        List<ExecutionContext> ec = new ArrayList<>();
        for (ExecutionContextService svc : copy) {
            ec.add(svc.prepare());
        }
        return CompositeExecutionContext.create(ec);
    }

    @Override
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     * Note that {@code isTerminated} is never {@code true} unless
     * either {@code shutdown} or {@code shutdownNow} was called first.
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    @Override
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<Future<T>> futures = executor.invokeAll(tasks);
        List<Future<T>> manageable = new ArrayList<>(futures.size());
        int i = 0;
        for (Callable<T> callable : tasks) {
            Task task = getNewTaskFor(callable);
            task.submitted(futures.get(i));
            manageable.add(task);
            i++;
        }
        return manageable;
    }

    protected abstract <T> Task getNewTaskFor(Callable<T> callable);

    protected abstract <V> Task<V> getNewTaskFor(Runnable task, V result);


    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        List<Future<T>> futures = executor.invokeAll(tasks, timeout, unit);
        List<Future<T>> manageable = new ArrayList<>(futures.size());
        int i = 0;
        for (Callable<T> callable : tasks) {
            Task task = getNewTaskFor(callable);
            task.submitted(futures.get(i));
            manageable.add(task);
            i++;
        }
        return manageable;
    }


    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return executor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        Task<Void> task = getNewTaskFor(command, null);
        task.execute();
    }



    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    @Override
    public int getLargestPoolSize() {
        return internalPool.getLargestPoolSize();
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     */
    @Override
    public int getMaximumPoolSize() {
        return internalPool.getMaximumPoolSize();
    }

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    @Override
    public int getPoolSize() {
        return internalPool.getPoolSize();
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     */
    @Override
    public int getCorePoolSize() {
        return internalPool.getCorePoolSize();
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    @Override
    public long getCompletedTaskCount() {
        return internalPool.getCompletedTaskCount();
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    @Override
    public int getActiveCount() {
        return internalPool.getActiveCount();
    }

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    @Override
    public BlockingQueue<Runnable> getQueue() {
        return internalPool.getQueue();
    }

    /**
     * Tries to remove from the work queue all {@link java.util.concurrent.Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     */
    @Override
    public void purge() {
        internalPool.purge();
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     * <p>
     * <p>This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue. For
     * example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return {@code true} if the task was removed
     */
    @Override
    public boolean remove(Runnable task) {
        return internalPool.remove(task);
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    @Override
    public long getTaskCount() {
        return internalPool.getTaskCount();
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     */
    @Override
    public long getKeepAliveTime(TimeUnit unit) {
        return internalPool.getKeepAliveTime(unit);
    }

    @Override
    public <T> ManagedFutureTask<T> submit(Callable<T> task) {
        if (task == null) {
            throw new NullPointerException();
        }
        return getNewTaskFor(task).execute();
    }

    @Override
    public <T> ManagedFutureTask<T> submit(Runnable task, T result) {
        if (task == null) {
            throw new NullPointerException();
        }
        final Task t = getNewTaskFor(task, result).execute();
        t.addListener(new Runnable() {
            @Override
            public void run() {
                tasks.remove(t);
            }
        });
        tasks.add(t);
        return t;
    }

    @Override
    public ManagedFutureTask<?> submit(Runnable task) {
        return submit(task, null);
    }

}
