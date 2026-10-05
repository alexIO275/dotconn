package com.vnhackers.dotconn.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Records a completed demo seed so restarts never reset edits made during the demo. */
@Entity
@Table(name = "demo_seed_runs")
public class DemoSeedRun {
  @Id
  @Column(length = 50)
  private String version;

  @Column(nullable = false, updatable = false)
  private Instant completedAt = Instant.now();

  protected DemoSeedRun() {}

  public DemoSeedRun(String version) {
    this.version = version;
  }
}
