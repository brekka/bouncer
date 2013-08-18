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
 * Server component to the bouncer library that acts as the mediator between many clients attempting to obtain a given
 * named lock.
 * 
 * BouncerServer establishes a TCP port on which is listens for clients. When a client connects, a new daemon thread
 * will be created to handle communications with that client. That thread will exist as long as the client connection is
 * active. Messages will be exchanged between client/server at a fixed interval for the duration of the connection. Only
 * one named lock can be held per connection.
 * 
 * The protocol is very simple, a client starts its request with the name of the lock it wants, then asks for the lock.
 * If no other client owns the lock, the server assigns the lock to the client. The lock will then be owned by the
 * client until the client looses connection with the server. Regardless of lock status, the client will continue
 * sending messages to the server to either keep alive its ownership of the lock, or attempt to take ownership.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class BouncerServer {

    private final int port;
    private final int soTimeout;
    private final int interval;
    private final boolean verbose;
    private final Set<String> acceptFromAddresses;
    /**
     * Assigning unique id's to thread names.
     */
    private final AtomicLong threadId = new AtomicLong();
    /**
     * Identifies which named lock is held by a given handler. The atomic operations of {@link ConcurrentHashMap} ensure
     * that only one handler can obtain a given named lock at a time.
     */
    private final ConcurrentHashMap<String, Handler> locksHeldBy = new ConcurrentHashMap<>();
    /**
     * The server socket listening for client connections.
     */
    private ServerSocket serverSocket;

    /**
     * @param port
     * @param soTimeoutMillis
     * @param intervalMillis
     * @param acceptFromAddresses
     * @param verbose
     * @throws IllegalArgumentException
     *             if any of the time based values are out of range.
     */
    public BouncerServer(int port, int soTimeoutMillis, int intervalMillis, Set<String> acceptFromAddresses,
            boolean verbose) {
        this.port = port;
        if (soTimeoutMillis < 1000) {
            throw new IllegalArgumentException(String.format(
                    "Socket timeout must be at least 1000 milliseconds, actual: %d", soTimeoutMillis));
        }
        if (intervalMillis < 1000) {
            throw new IllegalArgumentException(String.format("Interval must be at least 1000 milliseconds, actual: %d",
                    intervalMillis));
        }
        this.soTimeout = soTimeoutMillis;
        this.interval = intervalMillis;
        this.acceptFromAddresses = acceptFromAddresses;
        this.verbose = verbose;
    }

    /**
     * Blocking operation that dispatches client requests. The calling thread can be released by invoking the
     * {@link #shutdown()} method.
     * 
     * @throws IOException
     *             problem with the server socket, most likely while establishing.
     */
    public void serve() throws IOException {
        try (ServerSocket sock = new ServerSocket(port)) {
            this.serverSocket = sock;
            if (this.verbose) {
                if (this.acceptFromAddresses.isEmpty()) {
                    info("Bouncer listening on port %d for connections from any address", this.port);
                } else {
                    info("Bouncer listening on port %d for connections from any of %s", this.port,
                            this.acceptFromAddresses);
                }
            }
            while (listen(sock)) {
                // Keep listening
            }
        }
    }

    /**
     * Closes the server socket, thus releasing the thread that calls the {@link #serve()} method. Individual client
     * threads are marked daemon so will die naturally.
     */
    public void shutdown() {
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            error(e, "Shutdown");
        }
    }

    /**
     * Listen for new connections on the socket, calling {@link #createClientHandler(Socket)} for each socket
     * encountered.
     * 
     * @param socketServer
     *            the server to call {@link ServerSocket#accept()} on.
     * @return true if the socket was successfully dispatched.
     */
    protected boolean listen(ServerSocket socketServer) {
        Socket socket;
        try {
            socket = socketServer.accept();
            createClientHandler(socket);
            return true;
        } catch (IOException e) {
            if (!this.serverSocket.isClosed()) {
                // Only attempt to output the error if the socket is closed.
                error(e, "Listen");
            }
        }
        return false;
    }

    /**
     * Called when a new client request is encountered. A new thread will be created to handle the client with an
     * instance of {@link Handler}.
     * 
     * @param socket
     *            the TCP socket representing the client
     */
    protected void createClientHandler(Socket socket) {
        try {
            InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
            String address = remoteSocketAddress.getAddress().getHostAddress();
            if (acceptFromAddresses.isEmpty() || acceptFromAddresses.contains(address)) {
                socket.setSoTimeout(soTimeout);
                socket.setKeepAlive(true);
                String threadName = "Handler-" + address + "-" + threadId.incrementAndGet();
                Handler handler = prepareHandler(socket, address);
                Thread thread = new Thread(handler, threadName);
                thread.setDaemon(true);
                thread.start();
            } else {
                socket.close();
            }
        } catch (IOException e) {
            error(e, "Handle");
        }
    }

    /**
     * Extension point allowing the {@link Handler} implementation to be overridden and returned.
     * 
     * @param socket
     */
    protected Handler prepareHandler(Socket socket, String address) {
        return new Handler(socket, address);
    }

    /**
     * Very simple log method for information messages, can be overridden to use a log library instead
     * 
     * @param message
     * @param args
     */
    protected void info(String message, Object... args) {
        log(System.out, " INFO", null, message, args);
    }

    /**
     * Very simple log method for error messages, can be overridden to use a log library instead
     * 
     * @param message
     * @param args
     */
    protected void error(Throwable throwable, String message, Object... args) {
        log(System.err, "ERROR", throwable, message, args);
    }

    private void log(PrintStream out, String level, Throwable t, String message, Object... args) {
        if (!verbose) {
            // Only output messages if specifically asked to do so.
            return;
        }
        String prefix = String.format("%tFT%<tTZ %s ", new Date(), level);
        out.printf(prefix + message + "\n", args);
        if (t != null) {
            t.printStackTrace(out);
        }
    }

    /**
     * Handle the socket of a client connection. An instance of this class will exist per client
     */
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

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            this.lockName = null;
            try (InputStream is = socket.getInputStream();
                    OutputStream os = socket.getOutputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    PrintWriter writer = new PrintWriter(os)) {
                // The first line written will be used as the lock name.
                lockName = reader.readLine();
                if (lockName != null) {
                    while (serve(reader, writer)) {
                        // Do loop
                    }
                }
            } catch (IOException e) {
                // Ignore any IO problems, simply force the client to reconnect.
                error(e, "[%s] [%s] General IO", remoteAddress, lockName);
            } finally {
                if (lockName != null) {
                    if (locksHeldBy.remove(lockName, this)) {
                        info("[%s] [%s] Released, held since %tFT%<tTZ", remoteAddress, lockName, lockAcquired);
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    error(e, "[%s] [%s] Socket closed", remoteAddress, lockName);
                }
                info("[%s] [%s] Terminated, total messages: %d", remoteAddress, lockName, messageCount);
            }
        }

        /**
         * Reads the message form the client (doesn't matter what it is) and attempts to acquire the lock. If the lock
         * is acquired then "LOCKED" will be written back, otherwise "REJECTED" will be sent.
         * 
         * @param reader
         *            read lines from the client
         * @param writer
         *            write the response to the client
         * @return true if request/response messages were successfully exchanged and the socket can be retained for the
         *         next message
         * @throws IOException
         *             if there is any problem reading/writing.
         */
        protected boolean serve(BufferedReader reader, PrintWriter writer) throws IOException {
            // Read the starting line
            if (reader.readLine() == null) {
                // No line read within the timeout, abort this connection
                return false;
            }
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
            try {
                // Throttling mechanism controlled by the server. Determines the frequency with which the lock is
                // maintained.
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        }
    }
}
