package com.xgen.svc.nds.svc.experiments.model;


public record Bps(int dev, int prod) {

  public static final int MAX_BPS = 10_000;

}
