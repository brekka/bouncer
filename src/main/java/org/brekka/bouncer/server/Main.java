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
 * TODO Description of Main
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class Main {

    public static void main(String[] args) throws IOException {
        int port = 12321;
        int soTimeout = 4000;
        int interval = 2000;
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
                    soTimeout = Integer.parseInt(args[++i]);
                    break;
                case "verbose":
                    verbose = true;
                    break;
                case "interval":
                    interval = Integer.parseInt(args[++i]);
                    break;
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
                System.out.println("Shutdown normally");
            } 
        });
        server.serve();
    }
    
    
    /**
     * 
     */
    private static void printOptionsAndExit() {
        System.out.println("bouncer [--port 12321] [--allow IP_ADDRESS1,IP_ADDRESS2] [--timeout 4000] [--verbose]");
        System.exit(1);
    }
}
