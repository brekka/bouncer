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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Default implementation of the bouncer client. Upon construction, a new daemon thread will be created that will
 * attempt to permanently lock and maintain that lock with a bouncer server. That thread can be stopped by calling the
 * {@link #shutdown()} method.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class DefaultBouncerClient implements BouncerClient {

    private final String lockName;
    private final Handler handler;

    public DefaultBouncerClient(String hostname, int port, String lockName) {
        this(hostname, port, lockName, 4000, 2000, 2000);
    }

    /**
     * @param hostname
     * @param port
     * @param lockName
     * @param soTimeout
     * @param connectTimeout
     * @param retryDelay
     */
    public DefaultBouncerClient(String hostname, int port, String lockName, int soTimeout, int connectTimeout,
            int retryDelay) {
        this.lockName = lockName;
        InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);
        this.handler = new Handler(socketAddress, soTimeout, connectTimeout, retryDelay);
        String threadName = "Bouncer-" + hostname + "-" + lockName;
        Thread thread = new Thread(this.handler, threadName);
        // Make sure we don't prevent the JVM from shutting down.
        thread.setDaemon(true);
        thread.start();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.bouncer.BouncerClient#hasExclusiveAccess()
     */
    @Override
    public boolean hasExclusiveAccess() {
        return handler.hasExclusiveAccess();
    }

    /**
     * @return the lockName
     */
    public String getLockName() {
        return lockName;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.bouncer.BouncerClient#shutdown()
     */
    @Override
    public void shutdown() {
        this.handler.stop();
    }

    private class Handler implements Runnable {
        private final SocketAddress socketAddress;
        private final int soTimeout;
        private final int connectTimeout;
        private final int retryDelay;
        private boolean exclusiveAccess;
        private boolean running = true;

        /**
         * @param soTimeout
         * @param connectTimeout
         * @param running
         */
        public Handler(SocketAddress socketAddress, int soTimeout, int connectTimeout, int retryDelay) {
            this.socketAddress = socketAddress;
            this.soTimeout = soTimeout;
            this.connectTimeout = connectTimeout;
            this.retryDelay = retryDelay;
        }

        public void run() {
            while (running) {
                try (Socket socket = new Socket()) {
                    socket.setSoTimeout(soTimeout);
                    socket.setKeepAlive(true);
                    socket.connect(socketAddress, connectTimeout);
                    serve(socket);
                } catch (IOException e) {
                    // Ignore, client will reconnect until stopped.
                } finally {
                    // We only get to this point if something went wrong
                    exclusiveAccess = false;
                    try {
                        // Avoid thrashing
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException e) {
                        running = false;
                    }
                }
            }
        }

        private void serve(Socket socket) throws IOException {
            try (InputStream is = socket.getInputStream();
                    OutputStream outputStream = socket.getOutputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    PrintWriter out = new PrintWriter(outputStream)) {
                out.println(lockName);
                out.flush();
                while (running) {
                    // While everything is okay, we stay in this loop.
                    serve(br, out);
                }
            }
        }

        private void serve(BufferedReader reader, PrintWriter writer) throws IOException {
            writer.println("LOCK");
            writer.flush();
            String line = reader.readLine();
            if (line == null) {
                // No line, reconnect
                return;
            }

            if (line.equals("LOCKED")) {
                exclusiveAccess = true;
            } else {
                exclusiveAccess = false;
            }
            if (Thread.currentThread().isInterrupted()) {
                running = false;
            }
        }

        /**
         * @return the exclusiveAccess
         */
        public boolean hasExclusiveAccess() {
            return exclusiveAccess;
        }

        public void stop() {
            running = false;
        }
    }
}
