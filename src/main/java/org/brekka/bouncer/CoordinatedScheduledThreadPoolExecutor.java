/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brekka.bouncer;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;

/**
 * Tasks submitted to this {@link ScheduledThreadPoolExecutor} extension will need to acquire a lock on the provided
 * {@link Lock} instance in order to run. Intended to be used to support distributed execution of scheduled tasks in a
 * cluster where a given task only executes on one server at any given time.
 * 
 * Note that the current implementation has some sub-optimal implementations of {@link Future#get()} and
 * {@link Future#get(long, TimeUnit)} so the caller should avoid calling those methods on the returned future if at all
 * possible. The assumption is that with scheduled tasks the get methods will never be called.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class CoordinatedScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    private final Lock lock;

    /**
     * @param corePoolSize
     * @param handler
     */
    public CoordinatedScheduledThreadPoolExecutor(Lock lock, int corePoolSize, RejectedExecutionHandler handler) {
        super(corePoolSize, handler);
        this.lock = lock;
    }

    /**
     * @param corePoolSize
     * @param threadFactory
     * @param handler
     */
    public CoordinatedScheduledThreadPoolExecutor(Lock lock, int corePoolSize, ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        super(corePoolSize, threadFactory, handler);
        this.lock = lock;
    }

    /**
     * @param corePoolSize
     * @param threadFactory
     */
    public CoordinatedScheduledThreadPoolExecutor(Lock lock, int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
        this.lock = lock;
    }

    /**
     * @param corePoolSize
     */
    public CoordinatedScheduledThreadPoolExecutor(Lock lock, int corePoolSize) {
        super(corePoolSize);
        this.lock = lock;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.ScheduledThreadPoolExecutor#decorateTask(java.util.concurrent.Callable,
     * java.util.concurrent.RunnableScheduledFuture)
     */
    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
        RunnableScheduledFuture<V> wrappedTask = new LockAcquiringRunnableScheduledFuture<>(lock, task);
        return super.decorateTask(callable, wrappedTask);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.ScheduledThreadPoolExecutor#decorateTask(java.lang.Runnable,
     * java.util.concurrent.RunnableScheduledFuture)
     */
    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable, RunnableScheduledFuture<V> task) {
        RunnableScheduledFuture<V> wrappedTask = new LockAcquiringRunnableScheduledFuture<>(lock, task);
        return super.decorateTask(runnable, wrappedTask);
    }

    private static class LockAcquiringRunnableScheduledFuture<V> implements RunnableScheduledFuture<V> {
        private final Lock lock;
        private final RunnableScheduledFuture<V> actual;
        private Boolean skipped = null;

        /**
         * @param actual
         */
        public LockAcquiringRunnableScheduledFuture(Lock lock, RunnableScheduledFuture<V> actual) {
            this.lock = lock;
            this.actual = actual;
        }

        /**
         * @param unit
         * @return
         * @see java.util.concurrent.Delayed#getDelay(java.util.concurrent.TimeUnit)
         */
        public long getDelay(TimeUnit unit) {
            return actual.getDelay(unit);
        }

        /**
         * 
         * @see java.util.concurrent.RunnableFuture#run()
         */
        public void run() {
            if (lock.tryLock()) {
                skipped = Boolean.FALSE;
                try {
                    actual.run();
                } finally {
                    lock.unlock();
                }
            } else {
                skipped = Boolean.TRUE;
            }
        }

        /**
         * @return
         * @see java.util.concurrent.RunnableScheduledFuture#isPeriodic()
         */
        public boolean isPeriodic() {
            return actual.isPeriodic();
        }

        /**
         * @return
         * @see java.lang.Object#hashCode()
         */
        public int hashCode() {
            return actual.hashCode();
        }

        /**
         * @param mayInterruptIfRunning
         * @return
         * @see java.util.concurrent.Future#cancel(boolean)
         */
        public boolean cancel(boolean mayInterruptIfRunning) {
            return actual.cancel(mayInterruptIfRunning);
        }

        /**
         * @param obj
         * @return
         * @see java.lang.Object#equals(java.lang.Object)
         */
        public boolean equals(Object obj) {
            return actual.equals(obj);
        }

        /**
         * @param o
         * @return
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        public int compareTo(Delayed o) {
            return actual.compareTo(o);
        }

        /**
         * @return
         * @see java.util.concurrent.Future#isCancelled()
         */
        public boolean isCancelled() {
            return actual.isCancelled();
        }

        /**
         * @return
         * @see java.util.concurrent.Future#isDone()
         */
        public boolean isDone() {
            return skipped != null;
        }

        /**
         * Make sure we don't call the {@link #get()} on the actual future instance as when we skip execution of
         * {@link #run()}, the future does not know it has been executed and will block indefinitely.
         * 
         * Scheduling systems should really not be calling the get method, but just in case provide a crude workaround
         * that avoids calling to {@link #get()} on the actual future unless we know that run has been called. A more
         * ideal solution would be to re-implement the private class ScheduledFutureTask where we could implement this
         * the correct way.
         */
        public V get() throws InterruptedException, ExecutionException {
            while (skipped == null) {
                Thread.sleep(10);
            }
            if (skipped) {
                return null;
            }
            return actual.get();
        }

        /**
         * Make sure we don't call the {@link #get(long, TimeUnit))} on the actual future instance as when we skip
         * execution of {@link #run()}, the future does not know it has been executed and will block for at least as
         * long as the timeout, even if the run has already been skipped.
         * 
         * Scheduling systems should really not be calling the get method, but just in case provide a crude workaround
         * that avoids calling to {@link #get(long, TimeUnit))} on the actual future unless we know that run has been
         * called. A more ideal solution would be to re-implement the private class ScheduledFutureTask where we could
         * implement this the correct way.
         */
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            long pollIntervalMillis = 10;
            for (long i = 0; i < unit.toMillis(timeout); i += pollIntervalMillis) {
                if (skipped != null) {
                    break;
                }
                Thread.sleep(pollIntervalMillis);
            }
            if (skipped == null) {
                throw new TimeoutException();
            }
            if (skipped) {
                return null;
            }
            return actual.get();
        }

        /**
         * @return
         * @see java.lang.Object#toString()
         */
        public String toString() {
            return "Locking wrapper of: [" + actual.toString() + "]";
        }
    }
}
