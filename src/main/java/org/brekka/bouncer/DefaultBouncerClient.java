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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of the bouncer client. Upon construction, a new daemon thread will be created that will
 * attempt to permanently lock and maintain that lock with a bouncer server. That thread can be stopped by calling the
 * {@link #shutdown()} method.
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class DefaultBouncerClient implements BouncerClient {
    
    private static final Logger log = Logger.getLogger(DefaultBouncerClient.class.getName());

    protected final String lockName;
    private final Handler handler;

    public DefaultBouncerClient(String hostname, int port, String lockName) {
        this(hostname, port, lockName, 4000, 2000, 5000);
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
        return this.handler.hasExclusiveAccess();
    }

    /**
     * @return the lockName
     */
    @Override
    public String getLockName() {
        return this.lockName;
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

        @Override
        public void run() {
            while (this.running) {
                try (Socket socket = new Socket()) {
                    socket.setSoTimeout(this.soTimeout);
                    socket.setKeepAlive(true);
                    socket.connect(this.socketAddress, this.connectTimeout);
                    serve(socket);
                } catch (IOException e) {
                    // Ignore, client will reconnect until stopped.
                    if (log.isLoggable(Level.INFO)) {
                        log.log(Level.INFO, "Resetting connection to bouncer server", e);
                    }
                } catch (Throwable e) {
                    // More interesting, lets write out an error
                    log.log(Level.SEVERE, "Encountered unexpected throwable, will retry", e);
                } finally {
                    // We only get to this point if something went wrong
                    this.exclusiveAccess = false;
                    try {
                        // Avoid thrashing
                        Thread.sleep(this.retryDelay);
                    } catch (InterruptedException e) {
                        this.running = false;
                    }
                }
            }
        }

        private void serve(Socket socket) throws IOException {
            try (InputStream is = socket.getInputStream();
                    OutputStream outputStream = socket.getOutputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    PrintWriter out = new PrintWriter(outputStream)) {
                out.println(DefaultBouncerClient.this.lockName);
                out.flush();
                while (this.running) {
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
                throw new IOException("End of stream reached, will reconnect");
            }

            if (line.equals("LOCKED")) {
                this.exclusiveAccess = true;
            } else if (line.equals("REJECTED")) {
                this.exclusiveAccess = false;
            } else {
                // Force reconnect
                throw new IOException("Unexpected response");
            }
            if (Thread.currentThread().isInterrupted()) {
                this.running = false;
            }
        }

        /**
         * @return the exclusiveAccess
         */
        public boolean hasExclusiveAccess() {
            return this.exclusiveAccess;
        }

        public void stop() {
            this.running = false;
        }
    }
}
