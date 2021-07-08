package org.apache.geode.internal.cache;

import static org.apache.geode.internal.statistics.StatisticsClockFactory.disabledClock;
import static org.apache.geode.util.internal.UncheckedUtils.uncheckedCast;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.STRICT_STUBS;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import org.apache.geode.CancelCriterion;
import org.apache.geode.Statistics;
import org.apache.geode.cache.AttributesFactory;
import org.apache.geode.cache.PartitionAttributes;
import org.apache.geode.cache.PartitionAttributesFactory;
import org.apache.geode.cache.Region;
import org.apache.geode.distributed.internal.DSClock;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.DistributionManager;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.distributed.internal.locks.DLockService;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.internal.cache.control.InternalResourceManager;
import org.apache.geode.internal.cache.partitioned.RegionAdvisor;
import org.apache.geode.internal.cache.partitioned.colocation.ColocationLoggerFactory;
import org.apache.geode.internal.statistics.StatisticsManager;

public class PartitionRegionVirtualPutTest {
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(STRICT_STUBS);

  @Mock
  InternalCache cache;

  private PartitionedRegion region;

  @Before
  public void setUp() {
    ColocationLoggerFactory colocationLoggerFactory = mock(ColocationLoggerFactory.class);
    InternalDistributedMember distributedMember = mock(InternalDistributedMember.class);
    DistributionManager distributionManager = mock(DistributionManager.class);
    InternalDataView internalDataView = mock(InternalDataView.class);
    InternalRegionFactory<Object, Object> internalRegionFactory =
      uncheckedCast(mock(InternalRegionFactory.class));
    MeterRegistry meterRegistry = mock(MeterRegistry.class);
    Node node = mock(Node.class);
    PartitionedRegionStats.Factory partitionedRegionStatsFactory =
      mock(PartitionedRegionStats.Factory.class);
    InternalResourceManager resourceManager = mock(InternalResourceManager.class);
    RegionAdvisor.Factory regionAdvisorFactory = mock(RegionAdvisor.Factory.class);
    SenderIdMonitor.Factory senderIdMonitorFactory = mock(SenderIdMonitor.Factory.class);
    StatisticsManager statisticsManager = mock(StatisticsManager.class);
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
      .thenReturn(mock(DLockService.class));
    when(cache.getMeterRegistry())
      .thenReturn(meterRegistry);
    when(cache.createInternalRegionFactory(any()))
      .thenReturn(internalRegionFactory);
    when(distributionManager.getConfig())
      .thenReturn(mock(DistributionConfig.class));
    when(distributionManager.getId())
      .thenReturn(distributedMember);
    when(meterRegistry.config())
      .thenReturn(mock(MeterRegistry.Config.class));
    when(partitionedRegionStatsFactory.create(any(), any(), any()))
      .thenReturn(mock(PartitionedRegionStats.class));
    when(regionAdvisorFactory.create(any()))
      .thenReturn(mock(RegionAdvisor.class));
    when(statisticsManager.createAtomicStatistics(any(), any()))
      .thenReturn(mock(Statistics.class));
    when(system.createAtomicStatistics(any(), any()))
      .thenReturn(mock(Statistics.class));
    when(system.getClock())
      .thenReturn(mock(DSClock.class));
    when(system.getDistributedMember())
      .thenReturn(distributedMember);
    when(system.getDistributionManager())
      .thenReturn(distributionManager);
    when(system.getStatisticsManager())
      .thenReturn(statisticsManager);

    region = new PartitionedRegion("regionName", attributesFactory.create(), null, cache,
      mock(InternalRegionArguments.class), disabledClock(), colocationLoggerFactory,
      regionAdvisorFactory, internalDataView, node, system,
      partitionedRegionStatsFactory, senderIdMonitorFactory);
  }

  @Test
  public void createsLocallyIfIfNew() throws ForceReattemptException, ClassNotFoundException {
    EntryEventImpl event = mock(EntryEventImpl.class);
    DistributedRegion rootRegion = uncheckedCast(mock(DistributedRegion.class));

    when(cache.getRegion(eq(PartitionedRegionHelper.PR_ROOT_REGION_NAME), anyBoolean()))
      .thenReturn(rootRegion);
    assertThat(cache.getRegion(PartitionedRegionHelper.PR_ROOT_REGION_NAME, true))
      .isSameAs(rootRegion);
    when(event.getKeyInfo())
      .thenReturn(mock(KeyInfo.class));

    boolean ifNew = true; // To route the put to dataStore.createLocally() instead of putLocally()
    boolean ifOld = false;
    Object expectedOldValue = null;
    boolean requireOldValue = false;
    long lastModified = 9;
    boolean overwriteDestroyed = false;
    boolean invokeCallbacks = false;
    boolean throwConcurrentModification = false;

    region.initialize(null, null, null);

    region.virtualPut(event, ifNew, ifOld, expectedOldValue, requireOldValue, lastModified,
      overwriteDestroyed, invokeCallbacks, throwConcurrentModification);

    PartitionedRegionDataStore dataStore = region.getDataStore();

    // TODO: How to verify correct bucket region?
    BucketRegion bucketRegion = null;
    verify(dataStore)
      .createLocally(same(bucketRegion), same(event), eq(ifNew), eq(ifOld), eq(requireOldValue),
        eq(lastModified));
  }
}
