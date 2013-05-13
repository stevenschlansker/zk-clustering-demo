package com.nesscomputing.clustering.leader;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;

import com.nesscomputing.clustering.ClusteringConfiguration;
import com.nesscomputing.clustering.NodeInfo;
import com.nesscomputing.lifecycle.LifecycleStage;
import com.nesscomputing.lifecycle.guice.OnStage;
import com.nesscomputing.logging.Log;
import com.nesscomputing.service.discovery.client.DiscoveryClientModule;
import com.nesscomputing.service.discovery.job.ZookeeperProcessingTask;

class LeaderZookeeperJob extends ZookeeperProcessingTask
{
    private static final String BASE_PATH = "leader-election";
    private static final byte[] ZERO_BYTES = new byte[0];
    private static final Log LOG = Log.findLog();

    private final ExecutorService service;
    private final Map<String, Set<LeaderObserver>> observers;
    private final ClusteringConfiguration config;
    private final ObjectMapper mapper;
    private final NodeInfo myNode;
    private final byte[] myNodeInfoBytes;
    private final Map<String, NodeInfo> currentLeaders = Maps.newHashMap();
    private final Map<String, String> myPaths = Maps.newHashMap();

    private volatile Thread workerThread;
    private volatile boolean shouldTick;

    @Inject
    public LeaderZookeeperJob(
            @Named(DiscoveryClientModule.ZOOKEEPER_CONNECT_NAME) String connectString,
            ClusteringConfiguration config,
            @Named(LeaderClusterModule.NAME) ExecutorService service,
            @Named(LeaderClusterModule.NAME) Map<String, Set<LeaderObserver>> observers,
            ObjectMapper mapper,
            NodeInfo myNode)
    throws IOException
    {
        super(connectString, config.getLeaderTickInterval().getMillis());
        this.config = config;
        this.service = service;
        this.observers = observers;
        this.mapper = mapper;
        this.myNode = myNode;

        myNodeInfoBytes = mapper.writeValueAsBytes(myNode);
    }

    @OnStage(LifecycleStage.START)
    void start()
    {
        service.submit(this);
    }

    @OnStage(LifecycleStage.STOP)
    void stop()
    {
        Thread myWorkerThread = workerThread;
        if (myWorkerThread != null) {
            myWorkerThread.interrupt();
        }
    }

    @Override
    protected long determineCurrentGeneration(AtomicLong generation, long tick)
    {
        if (shouldTick) {
            LOG.info("Tick!");
            shouldTick = false;
            return generation.incrementAndGet();
        }
        return generation.get();
    }

    @Override
    protected synchronized boolean doWork(ZooKeeper zookeeper, long tick) throws IOException, KeeperException, InterruptedException
    {
        workerThread = Thread.currentThread();

        mkdir(zookeeper, path());
        mkdir(zookeeper, path(BASE_PATH));

        for (Entry<String, Set<LeaderObserver>> clusterEntry : observers.entrySet())
        {
            String clusterName = clusterEntry.getKey();
            Set<LeaderObserver> clusterObservers = clusterEntry.getValue();
            String clusterDirectory = path(BASE_PATH, clusterName);
            NodeInfo oldLeader = currentLeaders.get(clusterName);

            mkdir(zookeeper, clusterDirectory);

            String myElectPath = myPaths.get(clusterName);
            if (myElectPath == null || zookeeper.exists(myElectPath, false) == null) {
                myElectPath = zookeeper.create(path(BASE_PATH, clusterName, "elect_"), myNodeInfoBytes, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
                myPaths.put(clusterName, myElectPath);
            }

            while (true) {
                List<String> candidates = Lists.newArrayList(zookeeper.getChildren(clusterDirectory, watcher));
                Collections.sort(candidates);
                String winner = candidates.get(0);

                final String winnerPath = path(BASE_PATH, clusterName, winner);
                final byte[] leaderNodeDataBytes;
                try {
                    LOG.info("Watching path %s for changes", winnerPath);
                    leaderNodeDataBytes = zookeeper.getData(winnerPath, false, null);
                } catch (NoNodeException e) {
                    LOG.debug(e, "Leader disappeared, retrying");
                    // The leader is already gone...
                    continue;
                }

                NodeInfo newLeader;
                try {
                    newLeader = mapper.readValue(leaderNodeDataBytes, NodeInfo.class);
                } catch (IOException e) {
                    LOG.error(e, "Bogus data in ZK node %s", winnerPath);
                    newLeader = null;
                }

                currentLeaders.put(clusterName, newLeader);

                if (!newLeader.equals(oldLeader)) {
                    for (LeaderObserver o : clusterObservers) {
                        try {
                            o.newLeaderElected(null, newLeader);
                        } catch (Exception e) {
                            LOG.error(e, "While processing LeaderObserver %s", o);
                        }
                    }
                }
                break;
            }
        }
        return true;
    }

    private final Watcher watcher = new Watcher() {
        @Override
        public void process(WatchedEvent event)
        {
            shouldTick = true;
        }
    };

    private String path(String... parts)
    {
        return Joiner.on('/').join(Iterables.concat(Collections.singleton(config.getClusterZookeeperPath()), Arrays.asList(parts)));
    }

    private void mkdir(ZooKeeper zookeeper, String path) throws KeeperException, InterruptedException
    {
        if (zookeeper.exists(path, false) != null) {
            return;
        }
        try {
            zookeeper.create(path, ZERO_BYTES, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (NodeExistsException e) {
            // ok
        }
    }
}
