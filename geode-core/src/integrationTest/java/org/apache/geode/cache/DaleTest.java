package org.apache.geode.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import org.apache.geode.distributed.internal.DSClock;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.internal.cache.ForceReattemptException;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.InternalRegionArguments;
import org.apache.geode.internal.cache.PartitionedRegion;
import org.apache.geode.internal.cache.PartitionedRegionDataStore;

public class DaleTest {

  @Test
  public void foo() throws ForceReattemptException, ClassNotFoundException {
    InternalCache cache = mock(InternalCache.class);
    DSClock clock = mock(DSClock.class);
    InternalRegionArguments regionArgs = mock(InternalRegionArguments.class);
    InternalDistributedSystem system = mock(InternalDistributedSystem.class);

    when(cache.getInternalDistributedSystem()).thenReturn(system);
    when(system.getClock()).thenReturn(clock);

    RegionAttributes<Object, Object> regionAttributes = createRegionAttributes(true);

    PartitionedRegion region = new PartitionedRegion("my-region",
      regionAttributes, null, cache, regionArgs, null, null, null, null, null, system, null, null);

    region.initialize(null, null, null);
    region.virtualPut(null, true, false, null, false, 0, false, false, false);

    PartitionedRegionDataStore dataStore = region.getDataStore();

    verify(dataStore)
      .createLocally(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyLong());

    // Verify that dataStore.createLocally(…) was called
  }

  private static RegionAttributes<Object, Object> createRegionAttributes(boolean hasLocalDataStore) {
    RegionAttributes<Object, Object> regionAttributes;

    int localMaxMemory = hasLocalDataStore ? 1 : 0;
    PartitionAttributesFactory<Object, Object> attributesFactory =
      new PartitionAttributesFactory<>().setLocalMaxMemory(localMaxMemory);
    AttributesFactory<Object, Object> regionAttributesFactory = new AttributesFactory<>();
    regionAttributesFactory.setPartitionAttributes(attributesFactory.create());
    regionAttributes = regionAttributesFactory.create();
    return regionAttributes;
  }
}
