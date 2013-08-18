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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Run a bouncer server instance as an application. Takes five optional arguments in "--name" format. See
 * {@link #printOptionsAndExit()} for details.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class Main {

    /**
     * The amount of time to wait before responding to the client message (in millisconds). This determines the
     * frequency of messaging between the client and server. Note the argument value of "interval" is in seconds
     * (minimum 1).
     */
    private static final int DEFAULT_MESSAGE_INTERVAL = 2000;
    /**
     * Default socket timeout in milliseconds. Determines how long reads on the socket will wait before timing out and
     * returning control to the application Note the argument value of "timeout" is in seconds (minimum 1).
     */
    private static final int DEFAULT_SOCKET_TIMEOUT = 4000;
    /**
     * The default port number to use
     */
    private static final int DEFAULT_PORT = 12321;

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        int soTimeout = DEFAULT_SOCKET_TIMEOUT;
        int interval = DEFAULT_MESSAGE_INTERVAL;
        boolean verbose = false;
        Set<String> allowFromAddresses = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i].substring(2);
            switch (key) {
                case "port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "allow":
                    allowFromAddresses.addAll(Arrays.asList(args[++i].split(",")));
                    break;
                case "timeout":
                    soTimeout = Integer.parseInt(args[++i]) * 1000;
                    break;
                case "verbose":
                    verbose = true;
                    break;
                case "interval":
                    interval = Integer.parseInt(args[++i]) * 1000;
                    break;
                case "help":
                default:
                    printOptionsAndExit();
                    break;
            }
        }
        final BouncerServer server = new BouncerServer(port, soTimeout, interval, allowFromAddresses, verbose);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.shutdown();
                System.out.println("Bouncer has shutdown normally");
            }
        });
        server.serve();
    }

    /**
     * Display help message
     */
    private static void printOptionsAndExit() {
        System.out.println("Usage: bouncer [--port n] [--allow ADDRESS,...] [--timeout n] [--interval n] [--verbose] [--help]");
        System.out.println("  --port     The TCP port to listen on for client requests, default 12321.");
        System.out.println("  --allow    Comma separated list of IP addresses that connections should be allowed from.");
        System.out.println("             If none are specified then connections are allowed from any IP address.");
        System.out.println("  --timeout  The length of time the server will wait for read/writes to the client in seconds,");
        System.out.println("             default 4.");
        System.out.println("  --interval The number of seconds to wait before responding, default 2.");
        System.out.println("  --verbose  (no argument) enable printing of information messages and ignorable errors.");
        System.out.println("  --help     Just this message.");
        System.exit(1);
    }
}
