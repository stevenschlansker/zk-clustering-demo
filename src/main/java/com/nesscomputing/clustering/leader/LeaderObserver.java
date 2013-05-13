package com.nesscomputing.clustering.leader;

import com.nesscomputing.clustering.NodeInfo;
import com.nesscomputing.clustering.ServiceCluster;

public interface LeaderObserver
{
    void newLeaderElected(ServiceCluster cluster, NodeInfo node);
}
