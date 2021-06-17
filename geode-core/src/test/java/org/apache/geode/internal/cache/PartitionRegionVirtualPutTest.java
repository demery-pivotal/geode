package org.apache.geode.internal.cache;

import static org.apache.geode.internal.statistics.StatisticsClockFactory.disabledClock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.STRICT_STUBS;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import org.apache.geode.CancelCriterion;
import org.apache.geode.Statistics;
import org.apache.geode.cache.AttributesFactory;
import org.apache.geode.cache.PartitionAttributes;
import org.apache.geode.cache.PartitionAttributesFactory;
import org.apache.geode.distributed.internal.DSClock;
import org.apache.geode.distributed.internal.DistributionManager;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.internal.cache.control.InternalResourceManager;
import org.apache.geode.internal.cache.partitioned.RegionAdvisor;
import org.apache.geode.internal.cache.partitioned.colocation.ColocationLoggerFactory;

public class PartitionRegionVirtualPutTest {
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(STRICT_STUBS);

  private PartitionedRegion region;

  @Before
  public void setUp() {
    InternalCache cache = mock(InternalCache.class);
    ColocationLoggerFactory colocationLoggerFactory = mock(ColocationLoggerFactory.class);
    InternalDistributedMember distributedMember = mock(InternalDistributedMember.class);
    DistributionManager distributionManager = mock(DistributionManager.class);
    InternalDataView internalDataView = mock(InternalDataView.class);
    InternalDistributedSystem system = mock(InternalDistributedSystem.class);
    InternalResourceManager resourceManager = mock(InternalResourceManager.class);
    PartitionedRegionStats.Factory partitionedRegionStatsFactory =
      mock(PartitionedRegionStats.Factory.class);
    RegionAdvisor.Factory regionAdvisorFactory = mock(RegionAdvisor.Factory.class);
    SenderIdMonitor.Factory senderIdMonitorFactory = mock(SenderIdMonitor.Factory.class);

    AttributesFactory<?, ?> attributesFactory = new AttributesFactory<>();
    PartitionAttributes<?, ?> partitionAttributes = new PartitionAttributesFactory<>()
      .setLocalMaxMemory(1)
      .create();
    attributesFactory.setPartitionAttributes(partitionAttributes);

    when(cache.getCancelCriterion())
      .thenReturn(mock(CancelCriterion.class));
    when(cache.getDistributedSystem())
      .thenReturn(system);
    when(cache.getInternalDistributedSystem())
      .thenReturn(system);
    when(cache.getInternalResourceManager())
      .thenReturn(resourceManager);
    when(distributionManager.getId())
      .thenReturn(distributedMember);
    when(partitionedRegionStatsFactory.create(any(), any(), any()))
      .thenReturn(mock(PartitionedRegionStats.class));
    when(regionAdvisorFactory.create(any()))
      .thenReturn(mock(RegionAdvisor.class));
    when(system.createAtomicStatistics(any(), any()))
      .thenReturn(mock(Statistics.class));
    when(system.getClock())
      .thenReturn(mock(DSClock.class));
    when(system.getDistributedMember())
      .thenReturn(distributedMember);
    when(system.getDistributionManager())
      .thenReturn(distributionManager);

    region = new PartitionedRegion("regionName", attributesFactory.create(), null, cache,
      mock(InternalRegionArguments.class), disabledClock(), colocationLoggerFactory,
      regionAdvisorFactory, internalDataView, null /* Node*/, system,
      partitionedRegionStatsFactory, senderIdMonitorFactory);
  }

  @Test
  public void foo() {
    EntryEventImpl event = mock(EntryEventImpl.class);
    when(event.getKeyInfo())
      .thenReturn(mock(KeyInfo.class));

    boolean ifNew = false;
    boolean ifOld = false;
    Object expectedOldValue = null;
    boolean requireOldValue = false;
    long lastModified = 9;
    boolean overwriteDestroyed = false;
    boolean invokeCallbacks = false;
    boolean throwConcurrentModification = false;
    region.virtualPut(event, ifNew, ifOld, expectedOldValue, requireOldValue, lastModified,
      overwriteDestroyed, invokeCallbacks, throwConcurrentModification);
  }
}
