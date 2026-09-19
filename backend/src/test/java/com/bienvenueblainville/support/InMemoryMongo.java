package com.bienvenueblainville.support;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;

import java.net.InetSocketAddress;

/**
 * A MongoDB wire-protocol server inside the test JVM.
 *
 * <p>The usual embedded-Mongo library downloads a real {@code mongod} binary
 * at test time, which makes the suite depend on network access to a third
 * party — something CI should not need and a firewalled machine cannot do.
 * This is an ordinary jar speaking the real protocol, so the real Spring Data
 * MongoDB driver, the real queries and the real documents are all exercised;
 * only the storage engine underneath differs.
 */
public final class InMemoryMongo {
    private static MongoServer server;
    private static String connectionString;

    private InMemoryMongo() {
    }

    public static synchronized String connectionString() {
        if (server == null) {
            server = new MongoServer(new MemoryBackend());
            InetSocketAddress address = server.bind();
            connectionString = "mongodb://" + address.getHostString() + ":" + address.getPort()
                    + "/bienvenue_blainville_test";
            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        }
        return connectionString;
    }
}
