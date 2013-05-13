package com.nesscomputing.clustering;

import com.google.inject.AbstractModule;

public class ServiceClusterModule extends AbstractModule
{
    private final String clusterName;

    public ServiceClusterModule(String clusterName)
    {
        this.clusterName = clusterName;
    }

    @Override
    protected void configure()
    {
        install (new ClusteringModule());
    }
}
