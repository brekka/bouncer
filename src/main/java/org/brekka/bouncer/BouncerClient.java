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

/**
 * Contract for a client that provides the means to obtain exclusive access to a bouncer server lock.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public interface BouncerClient {

    /**
     * Return the name of the lock this client is trying to gain exclusive access to.
     * 
     * @return the lock name
     */
    String getLockName();

    /**
     * Does this client currently have exclusive access to the lock?
     * 
     * @return true if exclusive access has been gained.
     */
    boolean hasExclusiveAccess();

    /**
     * Shutdown this client.
     */
    void shutdown();

}