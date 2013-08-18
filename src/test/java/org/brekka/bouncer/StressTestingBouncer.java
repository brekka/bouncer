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

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.brekka.bouncer.server.BouncerServer;

/**
 * Starts a server, then runs a number of clients that will simulate running tasks. If any task gets run out of
 * sequence, an exception is thrown. Also randomly start/stop the server to simulate the failure of the coordinating
 * server.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class StressTestingBouncer {

    private static Lock anActualLock = new ReentrantLock();

    public static void main(String[] args) {
        // Start the server
        Thread serverThread = new Thread(new ReliableBouncerServer(), "ServerControl");
        serverThread.start();

        // 20 clients
        for (int i = 1; i <= 20; i++) {
            StandardClientApp client = new StandardClientApp(i);
            Thread clientThread = new Thread(client, "Client-" + i);
            clientThread.start();
        }
    }

    private static class StandardClientApp implements Runnable {

        private final int id;

        /**
         * 
         */
        public StandardClientApp(int id) {
            this.id = id;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            while (true) {
                clientRun();
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Runnable#run()
         */
        private void clientRun() {
            BouncerClient client = new DefaultBouncerClient("localhost", 12321, "Default");
            BouncerCoordinatedLock lock = new BouncerCoordinatedLock(client);
            CoordinatedScheduledThreadPoolExecutor ex = new CoordinatedScheduledThreadPoolExecutor(lock, 1);
            long waitTime = 10 + (long) (Math.random() * 500);
            for (int task = 0; task < 1 + (int) (Math.random() * 5);) {
                ScheduledFuture<?> scheduled = ex.schedule(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        if (anActualLock.tryLock()) {
                            try {
                                long workTime = 10 + (long) (Math.random() * 2000);
                                System.out.printf("Client %d doing %d ms of work%n", id, workTime);
                                // Simulate doing some work
                                try {
                                    Thread.sleep(workTime);
                                } catch (InterruptedException e) {
                                    throw new IllegalStateException(e);
                                }
                            } finally {
                                anActualLock.unlock();
                            }
                        } else {
                            // FAIL
                            System.err.printf("Client %d was unable to obtain lock. Test failed%n", id);
                            System.exit(1);
                        }
                        return Boolean.TRUE;
                    }
                }, waitTime, TimeUnit.MILLISECONDS);
                try {
                    if (scheduled.get(5000, TimeUnit.MILLISECONDS) != null) {
                        task++;
                    }
                } catch (InterruptedException | ExecutionException | TimeoutException e1) {
                    e1.printStackTrace();
                }
                long betweenTasks = waitTime + ((long) (Math.random() * 500));
                try {
                    Thread.sleep(betweenTasks);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            ex.shutdownNow();
            try {
                ex.awaitTermination(5000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            client.shutdown();
            System.out.printf("Client %d will refresh now%n", id);
        }

    }

    private static class ReliableBouncerServer implements Runnable {
        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            while (true) {
                BouncerServer server = new BouncerServer(12321, 4000, 2000, Collections.<String> emptySet(), false);
                try {
                    System.out.println("Server - UP");
                    server.serve();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private static class UnreliableBouncerServer implements Runnable {
        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            while (true) {
                final BouncerServer server = new BouncerServer(12321, 4000, 2000, Collections.<String> emptySet(), true);
                Thread t = new Thread("Server") {
                    public void run() {
                        try {
                            System.out.println("Server - UP");
                            server.serve();
                        } catch (IOException e) {
                            // Ignore
                        }
                        System.out.println("Server - DOWN");
                    }
                };
                t.start();
                // Running interval
                try {
                    Thread.sleep((long) (500 + (Math.random() * 300000))); // Max 5 minutes
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                server.shutdown();
                // Stopped interval
                try {
                    Thread.sleep((long) (2000 + (Math.random() * 60000))); // Max 1 minute
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
