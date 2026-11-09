# WorkManager creates workers by class name, so the class name must survive R8.
-keep class net.reddec.mylinks.work.** { *; }
