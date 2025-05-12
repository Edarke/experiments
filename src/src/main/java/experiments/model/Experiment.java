package experiments;

import java.io.File;
import java.util.Set;
import java.util.UUID;

/**
 * experiments.Experiment: { // Required: ASCII name with max length 255 name: string &
 * strings.MaxRunes(255) & strings.Ascii
 *
 * <p>// Required: ASCII description with max length 1023 description: string &
 * strings.MaxRunes(1023) & strings.Ascii
 *
 * <p>// Required: bps must be an integer between 0 and 10000 bps: int & >=0 & <=10000
 *
 * <p>// Optional: pinned_clusters is a set of UUIDs to pin to the control group excluded_clusters?:
 * [...encoding.UUID] & list.UniqueItems & len <= 100
 *
 * <p>// Optional: pinned_clusters is a set of UUIDs to pin to the experiment group
 * pinned_clusters?: [...encoding.UUID] & list.UniqueItems & len <= 100
 *
 * <p>// Constraint: pinned_clusters and excluded_clusters must not overlap if pinned_clusters !=
 * _|_ & excluded_clusters != _|_ { for i, pin in pinned_clusters { for j, ex in excluded_clusters {
 * assert: pin != ex } } }
 *
 * <p>// Optional: seed must be a 32-bit signed integer seed?: int & >= math.MinInt32 & <=
 * math.MaxInt32 }
 */
public record Experiment(
    String name,
    String description,
    BPS bps,
    Set<UUID> excludedClusters,
    Set<UUID> pinnedClusters,
    int seed) {
}
