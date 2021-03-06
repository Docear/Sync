package org.docear.syncdaemon;

import java.io.IOException;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

public class Main {
    final static Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) throws IOException, InterruptedException {
        SysOutOverSLF4J.sendSystemOutAndErrToSLF4J();
        final Daemon daemon = new Daemon(loadConfig());
        setupShutdownHandling(daemon);
        startDaemon(daemon);
        awaitTermination();
    }

    private static void awaitTermination() throws InterruptedException {
        Thread.currentThread().join();
    }

    private static void startDaemon(Daemon daemon) {
        new DaemonThread(daemon).start();
    }

    private static void setupShutdownHandling(final Daemon daemon) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                daemon.onStop();
            }
        });
    }

    private static Config loadConfig() {
        final boolean startedWithSbt = System.getProperty("started_with_sbt", "false").equals("true");
        final String defaultConfigFile = startedWithSbt ? "application.conf" : "prod.conf";
        final String configFile = System.getProperty("config.file", defaultConfigFile);
        return ConfigFactory.load(configFile);
    }
}
