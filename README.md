Brekka Bouncer
==============

Overview
--------

A solution to the problem of scheduled tasks on clusters, where a task must 
only be run on one server at a given time. It is based on a very simple lock 
server that ensures only one client owns a 'named lock' at a given time. 

It is important to note that this is NOT a job scheduler. The only guarantee 
this library provides is exclusivity, and should only be used to trigger 
operations that themselves manage state. An example would be an index update 
operation that doesn't matter exactly when it runs, just that it ONLY runs on 
one server at any given time.

Integration is based around a customised implementation of 
java.util.concurrent.ScheduledThreadPoolExecutor so should be usable in a 
variety of applications.


Usage
-----

The server component should be run as a standalone Java application using a 
command such as:

   java -jar bouncer-1.0.0.jar --port 12321 --verbose
   
Within the 'client' java application, something like the following would be
used:

   BouncerClient client = new DefaultBouncerClient("lockserver", 12321, "ClusterName");
   BouncerCoordinatedLock lock = new BouncerCoordinatedLock(client);
   CoordinatedScheduledThreadPoolExecutor executor = new CoordinatedScheduledThreadPoolExecutor(lock, 1);
   
