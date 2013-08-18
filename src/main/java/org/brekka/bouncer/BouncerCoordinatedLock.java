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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * A wrapper to make the {@link BouncerClient} behave a little bit like a {@link Lock}. However it does not implement
 * all of the operations of {@link Lock} so should only be used as an adapter for
 * {@link CoordinatedScheduledThreadPoolExecutor}.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class BouncerCoordinatedLock implements Lock {

    /**
     * The client to wrap
     */
    private final BouncerClient remote;

    public BouncerCoordinatedLock(BouncerClient remote) {
        this.remote = remote;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.locks.Lock#lock()
     */
    public void lock() {
        throw new UnsupportedOperationException();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.locks.Lock#lockInterruptibly()
     */
    public void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.locks.Lock#tryLock()
     */
    public boolean tryLock() {
        return remote.hasExclusiveAccess();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.locks.Lock#tryLock(long, java.util.concurrent.TimeUnit)
     */
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return tryLock();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.locks.Lock#unlock()
     */
    public void unlock() {
        // Nothing to unlock, client holds lock as long as it can
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.locks.Lock#newCondition()
     */
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

}
