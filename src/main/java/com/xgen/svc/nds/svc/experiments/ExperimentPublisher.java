package com.xgen.svc.nds.svc.experiments;


import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.xgen.svc.nds.svc.experiments.model.ExperimentMeta;
import com.xgen.svc.nds.svc.experiments.model.ExperimentState;
import com.xgen.svc.nds.svc.experiments.model.ExperimentEnv;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;


public class ExperimentPublisher implements ConfigCallback {

  private final Object lock = new Object();

  private ImmutableList<ExperimentMeta> experiments = ImmutableList.of();

  private ImmutableSet<UUID> pinnedClusters = ImmutableSet.of();

  private EnumMap<ExperimentEnv, List<ExperimentState>> cache = new EnumMap<>(ExperimentEnv.class);

  @Override
  public void onUpdate(ImmutableList<ExperimentMeta> updatedExperiments) {
    ImmutableSet<UUID> newPins =
        updatedExperiments.stream()
            .map(ExperimentMeta::experiment)
            .flatMap(e -> Stream.concat(e.pinnedClusters().stream(), e.excludedClusters().stream()))
            .collect(toImmutableSet());
    EnumMap<ExperimentEnv, List<ExperimentState>> newCache = new EnumMap<>(ExperimentEnv.class);
    for (ExperimentEnv env : ExperimentEnv.values()) {
      var defaultEnvConf =
          updatedExperiments.stream().map(e -> e.experiment().getDefaultStateForEnv(env)).toList();
      newCache.put(env, defaultEnvConf);
    }

    // Atomically update cached values. Restricting the synchronized scope is safe because
    // this method is only ever called by a single thread.
    synchronized (this.lock) {
      this.experiments = updatedExperiments;
      this.pinnedClusters = newPins;
      this.cache = newCache;
    }
  }

  public List<ExperimentState> getExperiments(UUID clusterId, ExperimentEnv type) {
    ImmutableList<ExperimentMeta> experimentsCopy;
    ImmutableSet<UUID> pinnedClustersCopy;
    EnumMap<ExperimentEnv, List<ExperimentState>> cacheCopy;

    // Copy to local variables. We need a consistent view of the state, but we don't need to block
    // updates or other reads. That is, consecutive calls to getExperiments always return a valid
    // snapshot of experiments, but are not strictly guaranteed to provide a monotonic
    // view of the state.
    synchronized (this.lock) {
      experimentsCopy = this.experiments;
      pinnedClustersCopy = this.pinnedClusters;
      cacheCopy = this.cache;
    }

    if (!pinnedClustersCopy.contains(clusterId)) {
      return cacheCopy.getOrDefault(type, List.of());
    }

    // TODO(edarke): We don't need to re-allocate experiments that aren't pinned
    return experimentsCopy.stream()
        .map(e -> e.experiment().getStateForID(clusterId, type))
        .toList();
  }
}