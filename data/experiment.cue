import (
    "strings"
    "math"
    "list"
)


#ObjectID: string & =~"^[A-Fa-f0-9]{24}$"

#ObjectIDSet: [...#ObjectID] & list.UniqueItems

#BpsValue: int & >=0 & <=10000

#Ascii: string & =~"^[\\x00-\\x7F]*$"

#Experiment: {
    // Required: ASCII name with max length 255
    name: #Ascii & strings.MaxRunes(255) @tag(filename)

    // Required: ASCII description with max length 1023
    description: #Ascii & strings.MaxRunes(1023)

    // Required: bps is an object with optional dev/prod keys
    bps: {
        dev: #BpsValue
    		prod: #BpsValue & <= bps.dev
    }

    // Optional: excluded_clusters is a set of IDs excluded from the experiment
    excluded_clusters?: #ObjectIDSet & <= 100

    // Optional: pinned_clusters is a set of IDs pinned to the experiment group
    pinned_clusters?: #ObjectIDSet & <= 100

    // Constraint: pinned_clusters and excluded_clusters must not overlap
    if pinned_clusters != _|_ & excluded_clusters != _|_ {
    	  assert: len(pinned_clusters) + len(excluded_clusters) <= 100
        for i, pin in pinned_clusters {
            for j, ex in excluded_clusters {
                assert: pin != ex
            }
        }
    }

    // Optional: seed must be a 32-bit signed integer
    seed?: int & >= math.MinInt32 & <= math.MaxInt32
}
