package com.nesscomputing.clustering.leader;

import com.nesscomputing.clustering.NodeInfo;
import com.nesscomputing.clustering.ServiceCluster;

public abstract class AbstractLeaderObserver implements LeaderObserver
{
    protected abstract void didBecomeLeader(ServiceCluster cluster);
    protected abstract void didRelinquishLeader(ServiceCluster cluster);

    @Override
    public void newLeaderElected(ServiceCluster cluster, NodeInfo node)
    {
    }
}
