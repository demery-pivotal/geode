package org.apache.geode.internal.cache;

import static org.apache.geode.internal.cache.PartitionedRegionHelper.PR_ROOT_REGION_NAME;
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
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.internal.DSClock;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.DistributionManager;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.distributed.internal.locks.DLockService;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.internal.cache.control.InternalResourceManager;
import org.apache.geode.internal.cache.partitioned.PartitionMessageDistribution;
import org.apache.geode.internal.cache.partitioned.RegionAdvisor;
import org.apache.geode.internal.cache.partitioned.RegionAdvisorFactory;
import org.apache.geode.internal.cache.partitioned.colocation.ColocationLoggerFactory;

public class PartitionRegionVirtualPutTest {
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(STRICT_STUBS);

  @Mock
  InternalCache cache;
  @Mock
  PartitionedRegionDataStore dataStore;
  @Mock
  PartitionMessageDistribution distribution;
  @Mock
  RegionAdvisorFactory regionAdvisorFactory;
  @Mock
  PartitionedRegionDataStoreFactory dataStoreFactory;
  @Mock
  PartitionedRegionStatsFactory partitionedRegionStatsFactory;
  @Mock
  PartitionedRegionStats prStats;
  @Mock
  PRHARedundancyProviderFactory redundancyProviderFactory;
  @Mock
  InternalDistributedSystem system;

  @Before
  public void setUp() {
    DistributionManager distributionManager = mock(DistributionManager.class);
    InternalDistributedMember distributedMember = mock(InternalDistributedMember.class);
    DLockService lockService = mock(DLockService.class);
    PRHARedundancyProvider redundancyProvider = mock(PRHARedundancyProvider.class);
    RegionAdvisor regionAdvisor = mock(RegionAdvisor.class);
    InternalResourceManager resourceManager = mock(InternalResourceManager.class);
    DistributedRegion rootRegion = uncheckedCast(mock(DistributedRegion.class));

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
    when(cache.getRegion(eq(PR_ROOT_REGION_NAME), anyBoolean()))
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

    when(partitionedRegionStatsFactory.create(any()))
        .thenReturn(prStats);

    when(redundancyProvider.createBucketAtomically(anyInt(), anyInt(), anyBoolean(), any()))
        .thenReturn(distributedMember);
    when(redundancyProviderFactory.create(any()))
        .thenReturn(redundancyProvider);

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
  }

  @Test
  public void withDataStore_createsLocally_ifOkToCreateEntry()
      throws ForceReattemptException, ClassNotFoundException {
    Integer bucketId = 12;
    String key = "create-event-key";
    PartitionedRegion region = initializePartitionedRegion(true);
    EntryEventImpl event = createEntryEvent(key, bucketId);
    BucketRegion bucketRegion = createBucketForEntryEvent(event);

    boolean isOkToCreateEntry = true;
    long lastModified = 9; // Ignored because isOkToCreateEntry == true
    boolean isOkToUpdateEntry = false;
    boolean requireOldValue = false;

    region.virtualPut(event, isOkToCreateEntry, isOkToUpdateEntry, null, requireOldValue,
        lastModified, false, false, false);

    verify(dataStore)
        .createLocally(same(bucketRegion), same(event), eq(isOkToCreateEntry),
            eq(isOkToUpdateEntry), eq(requireOldValue), eq(0L));
  }

  @Test
  public void withDataStore_putsLocally_ifNotOkToCreateEntry()
      throws ForceReattemptException, ClassNotFoundException {
    Integer bucketId = 34;
    String key = "put-event-key";
    PartitionedRegion partitionedRegion = initializePartitionedRegion(true);
    EntryEventImpl event = createEntryEvent(key, bucketId);
    BucketRegion bucketRegion = createBucketForEntryEvent(event);

    when(dataStore.putLocally(
        same(bucketRegion), same(event), anyBoolean(), anyBoolean(), any(), anyBoolean(),
        anyLong()))
            .thenReturn(true);

    boolean isOkToCreateEntry = false;
    long lastModified = 9;
    boolean isOkToUpdateEntry = false;
    Object expectedOldValue = "expected old value";
    boolean requireOldValue = false;

    partitionedRegion.virtualPut(event, isOkToCreateEntry, isOkToUpdateEntry, expectedOldValue,
        requireOldValue, lastModified, false, false, false);

    verify(dataStore)
        .putLocally(same(bucketRegion), same(event), eq(isOkToCreateEntry), eq(isOkToUpdateEntry),
            eq(expectedOldValue), eq(requireOldValue), eq(lastModified));
  }

  @Test
  public void withoutDataStore_createsRemotely_ifOkToCreateEntry()
      throws ForceReattemptException, ClassNotFoundException {
    Integer bucketId = 12;
    String key = "create-event-key";
    PartitionedRegion region = initializePartitionedRegion(false);
    EntryEventImpl event = createEntryEvent(key, bucketId);
    DistributedMember recipient = mock(DistributedMember.class);

    boolean isOkToCreateEntry = true;
    long lastModified = 9; // Ignored because isOkToCreateEntry == true
    boolean isOkToUpdateEntry = false;
    boolean requireOldValue = false;

    region.virtualPut(event, isOkToCreateEntry, isOkToUpdateEntry, null, requireOldValue,
        lastModified, false, false, false);

    verify(distribution).createRemotely(
        same(region), same(prStats), same(recipient), same(event), eq(requireOldValue));
  }


  private BucketRegion createBucketForEntryEvent(EntryEventImpl entryEvent)
      throws ForceReattemptException {
    Object key = entryEvent.getKey();
    int bucketId = entryEvent.getKeyInfo().getBucketId();
    BucketRegion bucketRegion = mock(BucketRegion.class);
    when(dataStore.getInitializedBucketForId(eq(key), eq(bucketId)))
        .thenReturn(bucketRegion);
    return bucketRegion;
  }

  private PartitionedRegion initializePartitionedRegion(boolean hasDataStore)
      throws ClassNotFoundException {
    PartitionAttributes<?, ?> partitionAttributes = new PartitionAttributesFactory<>()
        .setLocalMaxMemory(hasDataStore ? 1 : 0)
        .create();
    AttributesFactory<?, ?> attributesFactory = new AttributesFactory<>();
    attributesFactory.setPartitionAttributes(partitionAttributes);
    PartitionedRegion partitionedRegion =
        new PartitionedRegion("regionName", attributesFactory.create(), null, cache,
            mock(InternalRegionArguments.class), disabledClock(),
            mock(ColocationLoggerFactory.class),
            regionAdvisorFactory, mock(InternalDataView.class), mock(Node.class), system,
            partitionedRegionStatsFactory,
            mock(SenderIdMonitorFactory.class), redundancyProviderFactory, dataStoreFactory,
            distribution);
    partitionedRegion.initialize(null, null, null);
    return partitionedRegion;
  }

  private static EntryEventImpl createEntryEvent(Object key, Integer bucketId) {
    EntryEventImpl event = mock(EntryEventImpl.class);
    KeyInfo keyInfo = mock(KeyInfo.class);
    when(event.getKey())
        .thenReturn(key);
    when(event.getKeyInfo())
        .thenReturn(keyInfo);
    when(keyInfo.getBucketId())
        .thenReturn(bucketId);
    return event;
  }
}
