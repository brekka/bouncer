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


/**
 * TODO Description of Main
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        
        BouncerClient client = new DefaultBouncerClient("localhost", 12321, "Bob");
        BouncerCoordinatedLock lock = new BouncerCoordinatedLock(client);
        CoordinatedScheduledThreadPoolExecutor ex = new CoordinatedScheduledThreadPoolExecutor(lock, 1);
        
        
        ex.schedule(new Runnable() {
            
            public void run() {
                // TODO Auto-generated method stub
                System.out.println("Hey");
            }
        }, 4, TimeUnit.SECONDS);
        
        ex.shutdown();
        ex.awaitTermination(7, TimeUnit.SECONDS);
        client.shutdown();
    }
}
