package com.xgen.svc.nds.svc.experiments;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.xgen.svc.nds.svc.experiments.model.ExperimentDefinition;
import com.xgen.svc.nds.svc.experiments.model.ExperimentMeta;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHubPoller implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(GitHubPoller.class);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final ConcurrentHashMap<String, ExperimentMeta> cache = new ConcurrentHashMap<>();

  private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

  private final String path;

  private final ConfigCallback callback;

  public boolean upsertIfNew(ExperimentMeta data) {
    ExperimentMeta latest =
        this.cache.compute(
            data.fileName(),
            (name, existing) -> {
              // TODO(edarke): Use lastModified time rather than sha
              if (existing == null || !existing.contentSha().equals(data.contentSha())) {
                return data;
              } else {
                return existing;
              }
            });
    return latest == data;
  }

  public static ExperimentMeta fetchBlocking(GHContent reference) {
    logger.info("Fetching {}", reference.getName());

    try (InputStream in = reference.read()) {
      ExperimentDefinition ex = objectMapper.readValue(in, ExperimentDefinition.class);
      return new ExperimentMeta(reference.getName(), reference.getSha(), ex.materialize());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse experiment file", e);
    }
  }

  private final Supplier<GHRepository> uncheckedRepositorySupplier;

  public GitHubPoller(String repoName, String path, ConfigCallback callback) {
    this.path = path;
    this.callback = callback;
    this.uncheckedRepositorySupplier =
        Suppliers.memoize(
            () -> {
              try {
                return GitHub.connectAnonymously().getRepository(repoName);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  @Override
  public void run() {
    try {
      GHRepository repository = this.uncheckedRepositorySupplier.get(); // Might throw Exception

      // Get the list of files in the repository at the specified branch
      List<GHContent> contents = repository.getDirectoryContent(this.path, "main");

      Set<String> liveExperiments = contents.stream().map(GHContent::getName).collect(toSet());
      logger.info("Found {} live experiments", liveExperiments.size());
      this.cache.keySet().retainAll(liveExperiments);
      logger.info("Current live set: {}", this.cache.keySet());

      // Iterate over the files and download them
      List<CompletableFuture<Boolean>> updates = new ArrayList<>();
      for (GHContent file : contents) {
        if (file.isFile() && file.getName().endsWith(".json")) {

          logger.info("Found Experiment Definition: {}", file.getName());
          @Nullable ExperimentMeta existing = this.cache.getOrDefault(file.getName(), null);

          // We check lastUpdated here to prune unnecessary network calls, but we'll check this
          // condition again when applying an atomic map update after fetching the file content
          if (existing == null || !existing.contentSha().equals(file.getSha())) {
            updates.add(
                CompletableFuture.supplyAsync(() -> fetchBlocking(file), this.executorService)
                    .thenApply(this::upsertIfNew)
                    .thenApply(
                        v -> {
                          logger.info("Updated {} [{}]", file.getName(), v);
                          return v;
                        })
                    .exceptionally(
                        e -> {
                          logger.info("Failed to fetch experiment {}", file.getName(), e);
                          return false;
                        }));
          } else {
            logger.info("No update for {}", file.getName());
          }
        }
      }

      CompletableFuture.allOf(updates.toArray(CompletableFuture[]::new))
          .thenApply(
              f ->
                  updates.stream()
                      .map(CompletableFuture::join)
                      .reduce(Boolean::logicalOr)
                      .orElse(false))
          .thenAccept(
              needsUpdate -> {
                if (needsUpdate) {
                  ImmutableList<ExperimentMeta> experimentCopy =
                      ImmutableList.copyOf(this.cache.values());
                  this.callback.onUpdate(experimentCopy);
                } else {
                  logger.info("No experiments updated, not calling onUpdate");
                }
              });
    } catch (IOException | RuntimeException ex) {
      logger.error("Caught Exception when fetching experiments. Retrying in 1 minute...", ex);
      // DO NOT throw exception
    }
  }

  public static void main(String[] args) {
    var pool = Executors.newSingleThreadScheduledExecutor();
    var publisher = new ExperimentPublisher();

    var poller = new GitHubPoller("Edarke/experiments", "data/mongot", publisher);
    pool.scheduleWithFixedDelay(poller, 0, 1, TimeUnit.MINUTES);
  }
}
