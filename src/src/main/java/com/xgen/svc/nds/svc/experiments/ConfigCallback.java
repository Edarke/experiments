package com.xgen.svc.nds.svc.experiments;

import com.google.common.collect.ImmutableList;
import com.xgen.svc.nds.svc.experiments.model.ExperimentMeta;

public interface ConfigCallback {

  void onUpdate(ImmutableList<ExperimentMeta> experiments);
}
