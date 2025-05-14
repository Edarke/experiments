package com.xgen.svc.nds.svc.experiments.model;

import java.util.Set;
import java.util.UUID;

public record MaterializedExperiment(String name, BPS bps, Set<UUID> excludedClusters, Set<UUID> pinnedClusters, int seed) {

  public ExperimentState getStateForID(UUID clusterId, ExperimentEnv env) {
    if (pinnedClusters.contains(clusterId)) {
      return new ExperimentState(name, BPS.MAX_BPS, seed);
    } else if (excludedClusters.contains(clusterId)) {
      return new ExperimentState(name, 0, seed);
    } else {
      return getDefaultStateForEnv(env);
    }
  }

  public ExperimentState getDefaultStateForEnv(ExperimentEnv env) {
    int bps =
        switch (env) {
          case DEV -> bps().dev();
          case PROD -> bps().prod();
        };
    return new ExperimentState(name, bps, seed);
  }
}
