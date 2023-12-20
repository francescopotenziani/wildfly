/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ee.infinispan.scheduler;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.infinispan.Cache;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.distribution.ch.KeyPartitioner;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.TopologyChanged;
import org.infinispan.notifications.cachelistener.event.TopologyChangedEvent;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.concurrent.BlockingManager;
import org.wildfly.clustering.context.DefaultExecutorService;
import org.wildfly.clustering.context.DefaultThreadFactory;
import org.wildfly.clustering.ee.Key;
import org.wildfly.clustering.infinispan.distribution.ConsistentHashLocality;
import org.wildfly.clustering.infinispan.distribution.Locality;
import org.wildfly.clustering.infinispan.listener.ListenerRegistrar;
import org.wildfly.clustering.infinispan.listener.ListenerRegistration;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author Paul Ferraro
 */
@Listener
public class SchedulerTopologyChangeListener<I, K extends Key<I>, V> implements ListenerRegistrar {
    private static final ThreadFactory THREAD_FACTORY = new DefaultThreadFactory(SchedulerTopologyChangeListener.class);

    private final Cache<K, V> cache;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(THREAD_FACTORY);
    private final AtomicReference<Future<?>> scheduleTaskFuture = new AtomicReference<>();
    private final Consumer<Locality> cancelTask;
    private final BiConsumer<Locality, Locality> scheduleTask;
    private final KeyPartitioner partitioner;
    private final BlockingManager blocking;

    public SchedulerTopologyChangeListener(Cache<K, V> cache, CacheEntryScheduler<I, ?> scheduler, BiConsumer<Locality, Locality> scheduleTask) {
        this(cache, scheduler::cancel, scheduleTask);
    }

    @SuppressWarnings("deprecation")
    public SchedulerTopologyChangeListener(Cache<K, V> cache, Consumer<Locality> cancelTask, BiConsumer<Locality, Locality> scheduleTask) {
        this.cache = cache;
        this.cancelTask = cancelTask;
        this.scheduleTask = scheduleTask;
        this.blocking = this.cache.getCacheManager().getGlobalComponentRegistry().getComponent(BlockingManager.class);
        this.partitioner = this.cache.getAdvancedCache().getComponentRegistry().getLocalComponent(KeyPartitioner.class);
    }

    @Override
    public ListenerRegistration register() {
        this.cache.addListener(this);
        return () -> {
            this.cache.removeListener(this);
            WildFlySecurityManager.doUnchecked(this.executor, DefaultExecutorService.SHUTDOWN_NOW_ACTION);
            try {
                this.executor.awaitTermination(this.cache.getCacheConfiguration().transaction().cacheStopTimeout(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    @TopologyChanged
    public CompletionStage<Void> topologyChanged(TopologyChangedEvent<K, V> event) {
        Cache<K, V> cache = event.getCache();
        Address address = cache.getCacheManager().getAddress();
        ConsistentHash oldHash = event.getWriteConsistentHashAtStart();
        Set<Integer> oldSegments = oldHash.getMembers().contains(address) ? oldHash.getPrimarySegmentsForOwner(address) : Collections.emptySet();
        ConsistentHash newHash = event.getWriteConsistentHashAtEnd();
        Set<Integer> newSegments = newHash.getMembers().contains(address) ? newHash.getPrimarySegmentsForOwner(address) : Collections.emptySet();
        if (event.isPre()) {
            // If there are segments that we no longer own, then run cancellation task
            if (!newSegments.containsAll(oldSegments)) {
                Future<?> future = this.scheduleTaskFuture.getAndSet(null);
                if (future != null) {
                    future.cancel(true);
                }
                return this.blocking.runBlocking(() -> this.cancelTask.accept(new ConsistentHashLocality(this.partitioner, newHash, address)), this.getClass().getName());
            }
        } else {
            // If we have newly owned segments, then run schedule task
            if (!oldSegments.containsAll(newSegments)) {
                Locality oldLocality = new ConsistentHashLocality(this.partitioner, oldHash, address);
                Locality newLocality = new ConsistentHashLocality(this.partitioner, newHash, address);
                try {
                    Future<?> future = this.scheduleTaskFuture.getAndSet(this.executor.submit(() -> this.scheduleTask.accept(oldLocality, newLocality)));
                    if (future != null) {
                        future.cancel(true);
                    }
                } catch (RejectedExecutionException e) {
                    // Executor was shutdown
                }
            }
        }
        return CompletableFuture.completedStage(null);
    }
}
