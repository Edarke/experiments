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
    name: #Ascii & strings.MaxRunes(255) @tag(filename)
    description: #Ascii
    bps: {
        dev: >= 0 & <= 10000
    		prod: >= 0 & <= bps.dev
    }
    excluded_clusters?: #ObjectIDSet & <= 100
    pinned_clusters?: #ObjectIDSet & <= 100
    seed?: int & >= math.MinInt32 & <= math.MaxInt32

    if pinned_clusters != _|_ & excluded_clusters != _|_ {
    	  assert: len(pinned_clusters) + len(excluded_clusters) <= 100
        for i, pin in pinned_clusters {
            for j, ex in excluded_clusters {
                assert: pin != ex
            }
        }
    }
}
