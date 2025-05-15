package com.xgen.svc.nds.svc.experiments.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;


@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperimentDefinition(
    @JsonSetter(nulls = Nulls.AS_EMPTY) String name,
    Bps bps,
    @JsonSetter(nulls = Nulls.AS_EMPTY) Set<UUID> excludedClusters,
    @JsonSetter(nulls = Nulls.AS_EMPTY) Set<UUID> pinnedClusters,
    int seed) {

  public MaterializedExperiment materialize() {
    int s =
        seed() != 0
            ? seed()
            : Hashing.murmur3_32_fixed().hashString(name(), StandardCharsets.UTF_8).asInt();
    return new MaterializedExperiment(name(), bps(), excludedClusters(), pinnedClusters(), s);
  }
}
