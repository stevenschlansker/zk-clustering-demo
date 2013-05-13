package com.nesscomputing.clustering;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.TimeSpan;

public interface ClusteringConfiguration
{
    @Config("ness.cluster.leader.tick-interval")
    @Default("100ms")
    TimeSpan getLeaderTickInterval();

    @Config("ness.cluster.zk-path")
    @Default("/ness-cluster")
    String getClusterZookeeperPath();
}
