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

package org.brekka.bouncer.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TODO Description of BouncerServer
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class BouncerServer {
    
    private final int port;
    private final int soTimeout;
    private final int interval;
    private final boolean verbose;
    private final Set<String> acceptFromAddresses;
    private final AtomicLong threadId = new AtomicLong();
    private final ConcurrentHashMap<String, Handler> locksHeldBy = new ConcurrentHashMap<>();
    
    private ServerSocket serverSocket;
    
    public BouncerServer(int port, int soTimeout, int interval, Set<String> acceptFromAddresses, boolean verbose) {
        this.port = port;
        this.soTimeout = soTimeout;
        this.interval = interval;
        this.acceptFromAddresses = acceptFromAddresses;
        this.verbose = verbose;
    }
    
    public void serve() throws IOException {
        try (ServerSocket sock = new ServerSocket(port)) {
            this.serverSocket = sock;
            if (this.verbose) {
                if (this.acceptFromAddresses.isEmpty()) {
                    info("Bouncer listening on port %d for connections from any address", this.port);
                } else {
                    info("Bouncer listening on port %d for connections from any of %s", this.port, this.acceptFromAddresses);
                }
            }
            while (listen(sock)) {
            }
        }
    }
    
    public void shutdown() {
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            error(e, "Shutdown");
        }
    }
    
    protected boolean listen(ServerSocket sock) {
        Socket socket;
        try {
            socket = sock.accept();
            handle(socket);
            return true;
        } catch (IOException e) {
            if (!this.serverSocket.isClosed()) {
                error(e, "Listen");
            }
        }
        return false;
    }
    
    protected void handle(Socket socket) {
        try {
            InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
            String address = remoteSocketAddress.getAddress().getHostAddress();
            if (acceptFromAddresses.isEmpty() || acceptFromAddresses.contains(address)) {
                socket.setSoTimeout(soTimeout);
                socket.setKeepAlive(true);
                String threadName = "Handler-" + address + "-" + threadId.incrementAndGet();
                Handler handler = prepareHandler(socket, address);
                Thread thread = new Thread(handler, threadName);
                thread.start();
            } else {
                socket.close();
            }
        } catch (IOException e) {
            error(e, "Handle");
        }
    }
    
    protected Handler prepareHandler(Socket socket, String address) {
        return new Handler(socket, address);
    }

    protected void info(String message, Object... args) {
        log(System.out, " INFO", null, message, args);
    }
    
    protected void error(Throwable throwable, String message, Object... args) {
        log(System.err, "ERROR", throwable, message, args);
    }
    
    private void log(PrintStream out, String level, Throwable t, String message, Object... args) {
        if (verbose) {
            String prefix = String.format("%tFT%<tTZ %s ", new Date(), level);
            out.printf(prefix + message + "\n", args);
            if (t != null) {
                t.printStackTrace(out);
            }
        }
    }

    
    protected class Handler implements Runnable {
        
        private final Socket socket;
        private final String remoteAddress;
        private String lockName;
        private long messageCount;
        private Date lockAcquired;
        
        /**
         * @param socket
         */
        public Handler(Socket socket, String remoteAddress) {
            this.socket = socket;
            this.remoteAddress = remoteAddress;
        }

        /* (non-Javadoc)
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            this.lockName = null;
            try (InputStream is = socket.getInputStream();
                    OutputStream os = socket.getOutputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    PrintWriter writer = new PrintWriter(os)) {
                lockName = reader.readLine();
                if (lockName != null) {
                    while (loop(reader, writer)) {
                        // Do loop
                    }
                }
            } catch (IOException e) {
                // Dump
            } finally {
                if (lockName != null) {
                    if (locksHeldBy.remove(lockName, this)) {
                        info("[%s] [%s] Released, held since %tFT%<tTZ", 
                                remoteAddress, lockName, lockAcquired);
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    error(e, "[%s] [%s] Socket closed", remoteAddress, lockName);
                }
                info("[%s] [%s] Terminated, total messages: %d", 
                        remoteAddress, lockName, messageCount);
            }
        }
        
        protected boolean loop(BufferedReader reader, PrintWriter writer) throws IOException {
            // Read the starting line
            if (reader.readLine() != null) {
                Runnable existing = locksHeldBy.putIfAbsent(lockName, this);
                if (existing == null) {
                    info("[%s] [%s] Acquired", remoteAddress, lockName);
                    lockAcquired = new Date();
                }
                if (existing == this || existing == null) {
                    writer.println("LOCKED");
                } else {
                    writer.println("REJECTED");
                }
                writer.flush();
                messageCount++;
            } else {
                // No line read
                return false;
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        }
    }
}
