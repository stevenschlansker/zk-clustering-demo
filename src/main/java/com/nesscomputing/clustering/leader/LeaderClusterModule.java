package com.nesscomputing.clustering.leader;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;

import com.nesscomputing.clustering.ClusteringModule;
import com.nesscomputing.lifecycle.executor.LifecycledThreadPoolModule;

public class LeaderClusterModule extends AbstractModule
{
    public static final String NAME = "cluster-leader";

    @Override
    protected void configure()
    {
        install (new LifecycledThreadPoolModule(NAME));
        bind (LeaderZookeeperJob.class).asEagerSingleton();
        install (new ClusteringModule());

        mapBinder(binder());
    }

    private static MapBinder<String, LeaderObserver> mapBinder(Binder binder)
    {
        return MapBinder.newMapBinder(binder, String.class, LeaderObserver.class, Names.named(NAME)).permitDuplicates();
    }

    public static LinkedBindingBuilder<LeaderObserver> bindLeaderObserver(Binder binder, String clusterName)
    {
        return mapBinder(binder).addBinding(clusterName);
    }
}
