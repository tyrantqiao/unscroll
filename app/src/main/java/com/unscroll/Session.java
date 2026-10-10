package com.unscroll;

/** Pure Java timing policy; time inputs are monotonic milliseconds. */
public final class Session {
 public long used, awaySince=-1, blockedUntil; public boolean warned;
 public enum Action { NONE, REMIND, BLOCK }
 public Action advance(long now, long delta, boolean active, long warn, long stop, long rest) {
  if(blockedUntil>0) {
   if(now<blockedUntil) return active?Action.BLOCK:Action.NONE;
   blockedUntil=0; used=0; warned=false; awaySince=-1;
  }
  if(!active) {
   if(awaySince<0) awaySince=now;
   if(now-awaySince>=rest) {used=0;warned=false;}
   return Action.NONE;
  }
  if(awaySince>=0 && now-awaySince>=rest) {used=0;warned=false;}
  awaySince=-1; used+=Math.max(0,delta);
  if(used>=stop) { blockedUntil=now+rest; return Action.BLOCK; }
  if(used>=warn&&!warned) {warned=true;return Action.REMIND;}
  return Action.NONE;
 }
}
