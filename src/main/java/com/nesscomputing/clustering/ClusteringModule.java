package com.nesscomputing.clustering;

import java.util.UUID;

import com.google.inject.AbstractModule;

import com.nesscomputing.clustering.leader.LeaderClusterModule;
import com.nesscomputing.config.ConfigProvider;

public class ClusteringModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind (NodeInfo.class).toInstance(new NodeInfo(UUID.randomUUID()));
        bind (ClusteringConfiguration.class).toProvider(ConfigProvider.of(ClusteringConfiguration.class));
        install (new LeaderClusterModule());
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj != null && obj.getClass() == ClusteringModule.class;
    }
}
