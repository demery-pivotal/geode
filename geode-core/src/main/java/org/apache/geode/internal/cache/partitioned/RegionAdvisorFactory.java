package org.apache.geode.internal.cache.partitioned;

import org.apache.geode.internal.cache.PartitionedRegion;

@FunctionalInterface
public interface RegionAdvisorFactory {
  RegionAdvisor create(PartitionedRegion region);
}
