package com.matuvent.mineturn.network;

import java.util.LinkedHashSet;
import java.util.UUID;

/** Per-battle client token lifecycle. A delayed start cannot revive a resolved attempt. */
public final class AimOrder {
    private final LinkedHashSet<UUID> retired=new LinkedHashSet<>();
    private UUID current;
    public boolean start(UUID token) {
        if(token==null || token.equals(current) || retired.contains(token))return false;
        if(current!=null)retire(current);
        current=token;return true;
    }
    /** Returns whether the visible attempt, rather than an older attempt, was closed. */
    public boolean stop(UUID token) {
        if(token==null)return false;
        retire(token);
        if(!token.equals(current))return false;
        current=null;return true;
    }
    private void retire(UUID token){
        retired.add(token);
        // Tokens are random; bounded history protects recent in-flight packets without growing forever.
        while(retired.size()>64)retired.removeFirst();
    }
    public void clear(){current=null;retired.clear();}
}
