package com.matuvent.mineturn.client;

/** One physical key cycle belongs either to encounter entry or to exit, even across an S2C close. */
public final class EncounterSwitchGesture {
    public enum Action { NONE, PREVIEW, ENTER }
    private boolean armed, exiting;
    public boolean exitPressed() {
        if (exiting) return false;
        exiting = true; armed = false;
        return true;
    }
    public Action outside(boolean down, boolean screenOpen) {
        if (exiting || screenOpen) { armed = false; return Action.NONE; }
        if (down && !armed) { armed = true; return Action.PREVIEW; }
        if (!down && armed) { armed = false; return Action.ENTER; }
        return Action.NONE;
    }
    public void released() { if (exiting) { exiting = false; armed = false; } }
    public void leftBattle() { armed = false; }
    public void reset() { armed = false; exiting = false; }
}
