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
 * TODO Description of DefaultBouncerClient
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class DefaultBouncerClient implements BouncerClient {
    
    private final SocketAddress socketAddress;
    private final String lockName;
    private final AcquireLoop acquireLoop;
    private boolean exclusiveAccess;
    
    public DefaultBouncerClient(String hostname, int port, String lockName) {
        this(hostname, port, lockName, 4000, 2000, 2000);
    }
    
    /**
     * @param exclusiveAccess
     * @param socketAddress
     * @param lockName
     * @param keepAliveExecutorService
     */
    public DefaultBouncerClient(String hostname, int port, String lockName, int soTimeout, int connectTimeout, int retryDelay) {
        this.socketAddress = new InetSocketAddress(hostname, port);
        this.lockName = lockName;
        this.acquireLoop = new AcquireLoop(soTimeout, connectTimeout, retryDelay);
        String threadName = "Bouncer-" + hostname + "-" + lockName;
        Thread thread = new Thread(this.acquireLoop, threadName);
        // Make sure we don't prevent the JVM from shutting down.
        thread.setDaemon(true);
        thread.start();
    }
    
    /* (non-Javadoc)
     * @see org.brekka.bouncer.BouncerClient#hasExclusiveAccess()
     */
    @Override
    public boolean hasExclusiveAccess() {
        return exclusiveAccess;
    }
    
    /* (non-Javadoc)
     * @see org.brekka.bouncer.BouncerClient#shutdown()
     */
    @Override
    public void shutdown() {
        this.acquireLoop.stop();
    }
    
    private class AcquireLoop implements Runnable {
        private final int soTimeout;
        private final int connectTimeout;
        private final int retryDelay;
        
        private boolean running = true;
        
        /**
         * @param soTimeout
         * @param connectTimeout
         * @param running
         */
        public AcquireLoop(int soTimeout, int connectTimeout, int retryDelay) {
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
                    loop(socket);
                } catch (IOException e) {
                    // Ignore
                } finally {
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
        
        private void loop(Socket socket) throws IOException {
            try (InputStream is = socket.getInputStream();
                    OutputStream outputStream = socket.getOutputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    PrintWriter out = new PrintWriter(outputStream)) {
                out.println(lockName);
                out.flush();
                while (running) {
                    out.println("LOCK");
                    out.flush();
                    String line = br.readLine();
                    if (line == null) {
                        // No line, reconnect
                        break;
                    } else if (line.equals("LOCKED")) {
                        exclusiveAccess = true;
                    } else {
                        exclusiveAccess = false;
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        running = false;
                    }
                }
            }
        }
        
        public void stop() {
            running = false;
        }
    }
}
