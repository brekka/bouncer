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

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.brekka.bouncer.server.BouncerServer;
import org.junit.Before;
import org.junit.Test;

/**
 * Basic integration tests
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class IntegrationTest {

    private BouncerServer server;
    
    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        server = new BouncerServer(12321, 4000, 2000, Collections.<String>emptySet(), false);
        Thread t = new Thread() {
            public void run() {
                try {
                    server.serve();
                } catch (IOException e) {
                    // Ignore
                }
            }
        };
        t.start();
    }
    
    public void tearDown() throws Exception {
        server.shutdown();
    }

    /**
     * TODO Crude simple test
     * @throws Exception
     */
    @Test
    public void test() throws Exception {
        BouncerClient client = new DefaultBouncerClient("localhost", 12321, "Bob");
        BouncerCoordinatedLock lock = new BouncerCoordinatedLock(client);
        CoordinatedScheduledThreadPoolExecutor ex = new CoordinatedScheduledThreadPoolExecutor(lock, 1);
        
        final AtomicBoolean called = new AtomicBoolean();
        
        ex.schedule(new Runnable() {
            
            public void run() {
                called.set(true);
            }
        }, 4, TimeUnit.SECONDS);
        
        ex.shutdown();
        ex.awaitTermination(7, TimeUnit.SECONDS);
        client.shutdown();
        assertTrue(called.get());
    }

}
