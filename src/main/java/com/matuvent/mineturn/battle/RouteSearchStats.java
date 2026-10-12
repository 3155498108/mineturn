package com.matuvent.mineturn.battle;

/** Per-query diagnostics; never shared across battles or used as wall-clock limits. */
final class RouteSearchStats {
    int polled, expanded, edges, replayedSamples, queued, peakOpen, budgetPruned;
    long storedSamples, avoidedPrefixCopies;
    boolean capped;
    void queued(int size){queued++;peakOpen=Math.max(peakOpen,size);}
    @Override public String toString(){
        return "polled="+polled+", expanded="+expanded+", edges="+edges+", replayed="+replayedSamples
                +", storedSamples="+storedSamples+", avoidedPrefixCopies="+avoidedPrefixCopies
                +", queued="+queued+", peak="+peakOpen+", budgetPruned="+budgetPruned+", capped="+capped;
    }
}
