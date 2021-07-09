package org.apache.geode.internal.cache;

import static org.apache.geode.internal.statistics.StatisticsClockFactory.disabledClock;
import static org.apache.geode.util.internal.UncheckedUtils.uncheckedCast;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.STRICT_STUBS;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import org.apache.geode.CancelCriterion;
import org.apache.geode.cache.AttributesFactory;
import org.apache.geode.cache.PartitionAttributes;
import org.apache.geode.cache.PartitionAttributesFactory;
import org.apache.geode.distributed.internal.DSClock;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.DistributionManager;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.distributed.internal.locks.DLockService;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.internal.cache.PartitionedRegion.DataStoreFactory;
import org.apache.geode.internal.cache.PartitionedRegion.RedundancyProviderFactory;
import org.apache.geode.internal.cache.control.InternalResourceManager;
import org.apache.geode.internal.cache.partitioned.RegionAdvisor;
import org.apache.geode.internal.cache.partitioned.colocation.ColocationLoggerFactory;

public class PartitionRegionVirtualPutTest {
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(STRICT_STUBS);

  @Mock
  InternalCache cache;

  @Mock
  PartitionedRegionDataStore dataStore;

  private PartitionedRegion region;

  @Before
  public void setUp() {
    ColocationLoggerFactory colocationLoggerFactory = mock(ColocationLoggerFactory.class);
    DataStoreFactory dataStoreFactory = mock(DataStoreFactory.class);
    InternalDistributedMember distributedMember = mock(InternalDistributedMember.class);
    DistributionManager distributionManager = mock(DistributionManager.class);
    InternalDataView internalDataView = mock(InternalDataView.class);
    DLockService lockService = mock(DLockService.class);
    Node node = mock(Node.class);
    PartitionedRegionStats.Factory partitionedRegionStatsFactory =
      mock(PartitionedRegionStats.Factory.class);
    RedundancyProviderFactory redundancyProviderFactory = mock(RedundancyProviderFactory.class);
    PRHARedundancyProvider redundancyProvider = mock(PRHARedundancyProvider.class);
    InternalResourceManager resourceManager = mock(InternalResourceManager.class);
    RegionAdvisor.Factory regionAdvisorFactory = mock(RegionAdvisor.Factory.class);
    RegionAdvisor regionAdvisor = mock(RegionAdvisor.class);
    DistributedRegion rootRegion = uncheckedCast(mock(DistributedRegion.class));
    SenderIdMonitor.Factory senderIdMonitorFactory = mock(SenderIdMonitor.Factory.class);
    InternalDistributedSystem system = mock(InternalDistributedSystem.class);

    AttributesFactory<?, ?> attributesFactory = new AttributesFactory<>();
    PartitionAttributes<?, ?> partitionAttributes = new PartitionAttributesFactory<>()
      // TODO: Move this to the test, because it determines whether there's local storage.
      .setLocalMaxMemory(1)
      .create();
    attributesFactory.setPartitionAttributes(partitionAttributes);

    when(cache.getCancelCriterion())
      .thenReturn(mock(CancelCriterion.class));
    when(cache.getCachePerfStats())
      .thenReturn(mock(CachePerfStats.class));
    when(cache.getDistributedSystem())
      .thenReturn(system);
    when(cache.getInternalDistributedSystem())
      .thenReturn(system);
    when(cache.getInternalResourceManager())
      .thenReturn(resourceManager);
    when(cache.getPartitionedRegionLockService())
      .thenReturn(lockService);
    when(cache.getRegion(eq(PartitionedRegionHelper.PR_ROOT_REGION_NAME), anyBoolean()))
      .thenReturn(rootRegion);

    when(dataStoreFactory.create(any()))
      .thenReturn(dataStore);

    when(distributionManager.getCancelCriterion())
      .thenReturn(mock(CancelCriterion.class));
    when(distributionManager.getConfig())
      .thenReturn(mock(DistributionConfig.class));
    when(distributionManager.getId())
      .thenReturn(distributedMember);

    when(lockService.lock(any(), anyLong(), anyLong()))
      .thenReturn(true);
    when(lockService.lock(any(), anyLong(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean()))
      .thenReturn(true);
    when(partitionedRegionStatsFactory.create(any(), any(), any()))
      .thenReturn(mock(PartitionedRegionStats.class));

    when(redundancyProviderFactory.create(any()))
      .thenReturn(redundancyProvider);
    when(redundancyProvider.createBucketAtomically(anyInt(), anyInt(), anyBoolean(), any()))
      .thenReturn(distributedMember);

    when(regionAdvisorFactory.create(any()))
      .thenReturn(regionAdvisor);

    when(rootRegion.getDistributionAdvisor())
      .thenReturn(mock(CacheDistributionAdvisor.class));

    when(system.getClock())
      .thenReturn(mock(DSClock.class));
    when(system.getDistributedMember())
      .thenReturn(distributedMember);
    when(system.getDistributionManager())
      .thenReturn(distributionManager);

    region = new PartitionedRegion("regionName", attributesFactory.create(), null, cache,
      mock(InternalRegionArguments.class), disabledClock(), colocationLoggerFactory,
      regionAdvisorFactory, internalDataView, node, system,
      partitionedRegionStatsFactory, senderIdMonitorFactory,
      redundancyProviderFactory, dataStoreFactory);
  }

  @Test
  public void createsLocallyIfIfNew() throws ForceReattemptException, ClassNotFoundException {
    BucketRegion bucketRegion = mock(BucketRegion.class);
    EntryEventImpl event = mock(EntryEventImpl.class);

    when(event.getKey())
      .thenReturn("event-key");
    when(event.getKeyInfo())
      .thenReturn(mock(KeyInfo.class));
    when(dataStore.getInitializedBucketForId(any(), any()))
      .thenReturn(bucketRegion);

    boolean ifNew = true; // To route the put to dataStore.createLocally() instead of putLocally()
    long lastModified = 9; // Ignored because ifNew == true
    boolean ifOld = false;
    Object expectedOldValue = null;
    boolean requireOldValue = false;
    boolean overwriteDestroyed = false;
    boolean invokeCallbacks = false;
    boolean throwConcurrentModification = false;

    region.initialize(null, null, null);

    region.virtualPut(event, ifNew, ifOld, expectedOldValue, requireOldValue, lastModified,
      overwriteDestroyed, invokeCallbacks, throwConcurrentModification);

    verify(dataStore)
      .createLocally(same(bucketRegion), same(event), eq(ifNew), eq(ifOld), eq(requireOldValue),
        eq(0L));
  }
}
