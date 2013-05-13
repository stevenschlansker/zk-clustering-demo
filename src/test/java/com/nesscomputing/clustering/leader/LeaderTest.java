package com.nesscomputing.clustering.leader;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.nesscomputing.clustering.NodeInfo;
import com.nesscomputing.clustering.ServiceCluster;
import com.nesscomputing.clustering.ServiceClusterModule;
import com.nesscomputing.config.Config;
import com.nesscomputing.config.ConfigModule;
import com.nesscomputing.lifecycle.Lifecycle;
import com.nesscomputing.lifecycle.LifecycleStage;
import com.nesscomputing.lifecycle.guice.LifecycleModule;
import com.nesscomputing.service.discovery.client.DiscoveryClientModule;
import com.nesscomputing.service.discovery.job.ZookeeperProcessingTask;
import com.nesscomputing.service.discovery.server.zookeeper.ZookeeperModule;

public class LeaderTest
{
    private final Config config = buildConfig();
    private Injector serverInjector;
    private Lifecycle serverLifecycle;
    private CountDownLatch latch;

    @Before
    public void setUp()
    {
        Logger.getLogger(ZookeeperProcessingTask.class).setLevel(Level.DEBUG);

        serverInjector = Guice.createInjector(new LifecycleModule(), new ZookeeperModule(config));
        serverLifecycle = serverInjector.getInstance(Lifecycle.class);

        serverLifecycle.executeTo(LifecycleStage.START_STAGE);
    }

    @After
    public void tearDown()
    {
        serverLifecycle.executeTo(LifecycleStage.STOP_STAGE);
    }

    @Test
    public void testLeaderElection() throws Exception
    {
        final int numNodes = 5;

        latch = new CountDownLatch(numNodes);

        List<Node> nodes = Lists.newArrayList();
        try {
            for (int i = 0; i < numNodes; i++) {
                Node node = createNode();
                node.start();
                nodes.add(node);
            }

            Preconditions.checkState(latch.await(10, TimeUnit.SECONDS), "latch never triggered");
        } finally {
            for (Node node : nodes) {
                Thread.sleep(10000);
                node.stop();
            }
        }
    }

    private Node createNode()
    {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure()
            {
                install (new ConfigModule(config));
                install (new LifecycleModule());
                install (new ServiceClusterModule("test"));

                bindConstant().annotatedWith(DiscoveryClientModule.ZOOKEEPER_CONNECT_NAMED)
                    .to(String.format("127.0.0.1:%s", config.getConfiguration().getString("ness.zookeeper.clientPort")));

                bind (Node.class);
                bind (CountDownLatch.class).toInstance(latch);

                LeaderClusterModule.bindLeaderObserver(binder(), "test").to(Node.class);
            }
        }).getInstance(Node.class);
    }

    static class Node implements LeaderObserver
    {
        private final Lifecycle lifecycle;
        private final CountDownLatch latch;

        @Inject
        Node(Lifecycle lifecycle, CountDownLatch latch)
        {
            this.lifecycle = lifecycle;
            this.latch = latch;
        }

        void start()
        {
            lifecycle.executeTo(LifecycleStage.START_STAGE);
        }

        void stop()
        {
            lifecycle.executeTo(LifecycleStage.STOP_STAGE);
        }

        @Override
        public void newLeaderElected(ServiceCluster cluster, NodeInfo node)
        {
            latch.countDown();
            System.out.println("Elected " + node);
        }
    }

    private Config buildConfig()
    {
        Map<String, String> properties = Maps.newHashMap();
        final File tmpDir = Files.createTempDir();
        tmpDir.deleteOnExit();
        properties.put("ness.zookeeper.dataDir", tmpDir.getAbsolutePath());
        properties.put("ness.zookeeper.clientPort", Integer.toString(findUnusedPort()));
        properties.put("ness.jmx.enabled", "false");

        int port1 = findUnusedPort();
        int port2 = findUnusedPort();
        properties.put("ness.zookeeper.server.1", format("127.0.0.1:%d:%d", port1, port2));

        return Config.getFixedConfig(properties);
    }

    private static final int findUnusedPort()
    {
        int port;

        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
        }
        catch (IOException ioe) {
            throw Throwables.propagate(ioe);
        }
        finally {
            try {
                socket.close();
            } catch (IOException ioe) {
                // GNDN
            }
        }

        return port;
    }
}
