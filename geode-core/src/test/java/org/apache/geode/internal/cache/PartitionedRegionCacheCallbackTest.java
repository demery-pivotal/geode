package org.apache.geode.internal.cache;

import static java.util.Arrays.asList;
import static org.apache.geode.internal.statistics.StatisticsClockFactory.disabledClock;
import static org.apache.geode.util.internal.UncheckedUtils.uncheckedCast;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.STRICT_STUBS;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import org.apache.geode.annotations.internal.MakeNotStatic;
import org.apache.geode.cache.AttributesFactory;
import org.apache.geode.cache.CacheLoader;
import org.apache.geode.cache.CacheWriter;
import org.apache.geode.cache.PartitionAttributes;
import org.apache.geode.cache.PartitionAttributesFactory;
import org.apache.geode.cache.Region;
import org.apache.geode.distributed.internal.DSClock;
import org.apache.geode.distributed.internal.DistributionManager;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.internal.cache.control.InternalResourceManager;
import org.apache.geode.internal.cache.partitioned.RegionAdvisor;
import org.apache.geode.internal.cache.partitioned.colocation.ColocationLoggerFactory;

@RunWith(JUnitParamsRunner.class)
public class PartitionedRegionCacheCallbackTest {

  // TODO: DHE Why is this static?
  @MakeNotStatic
  private static final AtomicInteger SERIAL_NUMBER_GENERATOR = new AtomicInteger();

  private PartitionedRegion partitionedRegion;

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(STRICT_STUBS);

  @Before
  public void setUp() {
    InternalCache cache = mock(InternalCache.class);
    DistributionManager distributionManager = mock(DistributionManager.class);
    InternalDistributedMember distributedMember = mock(InternalDistributedMember.class);
    InternalDataView internalDataView = mock(InternalDataView.class);
    PartitionedRegionStats.Factory partitionedRegionStatsFactory =
      mock(PartitionedRegionStats.Factory.class);
    RegionAdvisor.Factory regionAdvisorFactory = mock(RegionAdvisor.Factory.class);
    InternalResourceManager resourceManager = mock(InternalResourceManager.class);
    SenderIdMonitor.Factory senderIdMonitorFactory = mock(SenderIdMonitor.Factory.class);
    InternalDistributedSystem system = mock(InternalDistributedSystem.class);

    PartitionAttributes<?,?> partitionAttributes =
      new PartitionAttributesFactory<>().setTotalNumBuckets(1).setRedundantCopies(1).create();
    AttributesFactory<?,?> attributesFactory = new AttributesFactory<>();
    attributesFactory.setPartitionAttributes(partitionAttributes);

    when(cache.getInternalDistributedSystem())
      .thenReturn(system);
    when(cache.getInternalResourceManager())
      .thenReturn(resourceManager);
    when(partitionedRegionStatsFactory.create(any(), any(), any()))
      .thenReturn(mock(PartitionedRegionStats.class));
    when(system.getClock())
      .thenReturn(mock(DSClock.class));
    when(system.getDistributedMember())
      .thenReturn(distributedMember);
    when(system.getDistributionManager())
      .thenReturn(distributionManager);
    when(distributionManager.getId())
      .thenReturn(distributedMember);

    Node node = new Node(distributionManager.getId(), SERIAL_NUMBER_GENERATOR.getAndIncrement());

    partitionedRegion =
      new PartitionedRegion("regionName", attributesFactory.create(), null, cache,
        mock(InternalRegionArguments.class), disabledClock(), ColocationLoggerFactory.create(),
        regionAdvisorFactory, internalDataView, node, system, partitionedRegionStatsFactory,
        senderIdMonitorFactory,
        pr -> new PRHARedundancyProvider(pr, cache.getInternalResourceManager()),
        pr -> new PartitionedRegionDataStore(pr, disabledClock())
      );
  }

  @SuppressWarnings("unused")
  private Object[] cacheLoaderAndWriter() {
    CacheLoader<?, ?> mockLoader = mock(CacheLoader.class);
    CacheWriter<?, ?> mockWriter = mock(CacheWriter.class);
    return new Object[]{
      new Object[]{mockLoader, null},
      new Object[]{null, mockWriter},
      new Object[]{mockLoader, mockWriter},
      new Object[]{null, null}
    };
  }

  @Test
  @Parameters(method = "cacheLoaderAndWriter")
  @TestCaseName("{method}(CacheLoader={0}, CacheWriter={1})")
  public void verifyPRConfigUpdatedAfterLoaderUpdate(CacheLoader<?, ?> cacheLoader,
    CacheWriter<?, ?> cacheWriter) {
    // ARRANGE
    PartitionRegionConfig partitionRegionConfig = mock(PartitionRegionConfig.class);
    Region<String, PartitionRegionConfig> partitionedRegionRoot =
      uncheckedCast(mock(InternalRegion.class));
    PartitionedRegion.RegionLock regionLock = mock(PartitionedRegion.RegionLock.class);
    PartitionedRegion spyPartitionedRegion = spy(partitionedRegion);
    InternalDistributedMember ourMember = spyPartitionedRegion.getDistributionManager().getId();
    InternalDistributedMember otherMember1 = mock(InternalDistributedMember.class);
    InternalDistributedMember otherMember2 = mock(InternalDistributedMember.class);
    Node ourNode = mock(Node.class, "ourNode");
    Node otherNode1 = mock(Node.class, "otherNode1");
    Node otherNode2 = mock(Node.class, "otherNode2");

    when(otherNode1.getMemberId())
      .thenReturn(otherMember1);
    when(otherNode2.getMemberId())
      .thenReturn(otherMember2);
    when(ourNode.getMemberId())
      .thenReturn(ourMember);
    when(ourNode.isCacheLoaderAttached())
      .thenReturn(cacheLoader != null);
    when(ourNode.isCacheWriterAttached())
      .thenReturn(cacheWriter != null);
    when(partitionRegionConfig.getNodes())
      .thenReturn(new HashSet<>(asList(otherNode1, ourNode, otherNode2)));
    when(partitionedRegionRoot.get(spyPartitionedRegion.getRegionIdentifier()))
      .thenReturn(partitionRegionConfig);
    when(spyPartitionedRegion.getPRRoot())
      .thenReturn(partitionedRegionRoot);

    doReturn(cacheLoader)
      .when(spyPartitionedRegion).basicGetLoader();
    doReturn(cacheWriter)
      .when(spyPartitionedRegion).basicGetWriter();
    doReturn(regionLock)
      .when(spyPartitionedRegion).getRegionLock();

    // ACT
    spyPartitionedRegion.updatePRNodeInformation();

    // ASSERT
    assertThat(partitionRegionConfig.getNodes())
      .contains(ourNode);

    Node verifyOurNode = null;
    for (Node node : partitionRegionConfig.getNodes()) {
      if (node.getMemberId().equals(ourMember)) {
        verifyOurNode = node;
      }
    }
    assertThat(verifyOurNode)
      .withFailMessage("Failed to find " + ourMember + " in " + partitionRegionConfig.getNodes())
      .isNotNull();

    verify(partitionedRegionRoot)
      .get(spyPartitionedRegion.getRegionIdentifier());
    verify(partitionedRegionRoot)
      .put(spyPartitionedRegion.getRegionIdentifier(), partitionRegionConfig);
    verify(spyPartitionedRegion)
      .updatePRConfig(partitionRegionConfig, false);

    assertThat(verifyOurNode.isCacheLoaderAttached())
      .isEqualTo(cacheLoader != null);
    assertThat(verifyOurNode.isCacheWriterAttached())
      .isEqualTo(cacheWriter != null);
  }
}
