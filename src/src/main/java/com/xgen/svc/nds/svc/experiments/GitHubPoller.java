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

public class GitHubPoller {

  private static final Logger logger = LoggerFactory.getLogger(GitHubPoller.class);

  private static final ConcurrentHashMap<String, ExperimentMeta> cache = new ConcurrentHashMap<>();

  private static final ExecutorService executorService =
      Executors.newVirtualThreadPerTaskExecutor();

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private final ConfigCallback callback;

  public static boolean upsertIfNew(ExperimentMeta data) {
    ExperimentMeta latest =
        cache.compute(
            data.fileName(),
            (name, existing) -> {
              // TODO: Use lastModified time rather than sha
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

  public GitHubPoller(String repoName, ConfigCallback callback) {
    this.callback = callback;
    this.uncheckedRepositorySupplier =
        Suppliers.memoize(
            () -> {
              try {
                return GitHub.connect().getRepository(repoName);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  public void run() {
    try {
      GHRepository repository = this.uncheckedRepositorySupplier.get(); // Might throw Exception

      // Get the list of files in the repository at the specified branch
      List<GHContent> contents = repository.getDirectoryContent("/data", "main");

      Set<String> liveExperiments = contents.stream().map(GHContent::getName).collect(toSet());
      logger.info("Found {} live experiments", liveExperiments.size());
      cache.keySet().retainAll(liveExperiments);
      logger.info("Current live set: {}", cache.keySet());

      // Iterate over the files and download them
      List<CompletableFuture<Boolean>> updates = new ArrayList<>();
      for (GHContent file : contents) {
        if (file.isFile() && file.getName().endsWith(".json")) {

          logger.info("Found Experiment Definition: {}", file.getName());
          @Nullable ExperimentMeta existing = cache.getOrDefault(file.getName(), null);

          // We check lastUpdated here to prune unnecessary network calls, but we'll check this
          // condition again when applying an atomic map update after fetching the file content
          if (existing == null || !existing.contentSha().equals(file.getSha())) {
            updates.add(
                CompletableFuture.supplyAsync(() -> fetchBlocking(file), executorService)
                    .thenApply(GitHubPoller::upsertIfNew)
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
                  ImmutableList<ExperimentMeta> experimentCopy = ImmutableList.copyOf(cache.values());
                  callback.onUpdate(experimentCopy);
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

    var downloader = new GitHubPoller("Edarke/experiments", publisher);
    pool.scheduleWithFixedDelay(downloader::run, 0, 1, TimeUnit.MINUTES);
  }
}
