import (
    "strings"
    "encoding"
    "math"
    "list"
)

UUIDList: [...encoding.UUID] & list.UniqueItems & len <= 100
BPSValue: int & >=0 & <=10000

Experiment: {
    // Required: ASCII name with max length 255
    name: string & strings.MaxRunes(255) & strings.Ascii

    // Required: ASCII description with max length 1023
    description: string & strings.MaxRunes(1023) & strings.Ascii

    // Required: bps is an object with optional dev/prod keys
    bps: {
        dev: BPSValue
        prod: BPSValue & >= dev
    }

    // Optional: excluded_clusters is a set of UUIDs excluded from the experiment
    excluded_clusters?: UUIDList

    // Optional: pinned_clusters is a set of UUIDs pinned to the experiment group
    pinned_clusters?: UUIDList

    // Constraint: pinned_clusters and excluded_clusters must not overlap
    if pinned_clusters != _|_ & excluded_clusters != _|_ {
        for i, pin in pinned_clusters {
            for j, ex in excluded_clusters {
                assert: pin != ex
            }
        }
    }

    // Optional: seed must be a 32-bit signed integer
    seed?: int & >= math.MinInt32 & <= math.MaxInt32
}
