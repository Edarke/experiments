import (
    "strings"
    "encoding"
    "math"
    "list"
)

Experiment: {
    // Required: ASCII name with max length 255
    name: string & strings.MaxRunes(255) & strings.Ascii

    // Required: bps must be an integer between 0 and 10000
    bps: int & >=0 & <=10000

    // Optional: seed must be a 32-bit signed integer
    seed: int & >= math.MinInt32 & <= math.MaxInt32
}


Response: {
    experiments: [...experiments.model.ExperimentDefinition]
}