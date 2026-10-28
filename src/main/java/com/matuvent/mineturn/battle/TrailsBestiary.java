package com.matuvent.mineturn.battle;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Explicit species catalog for the AT branch, separate from the upstream datapack AI. */
public final class TrailsBestiary {
    public record Move(String name,String kind,String animation,String shape,double range,double radius,double width,
                       double power,String buff,int amplifier,int ticks,String projectile,boolean ignoreSight,int cpCost) {
        public TrailsArea area() {
            return switch(shape) {
                case "self" -> new TrailsArea(TrailsArea.Kind.DISK,0,radius,0);
                case "circle" -> new TrailsArea(TrailsArea.Kind.DISK,range,radius,0);
                case "line" -> new TrailsArea(TrailsArea.Kind.RECTANGLE,0,range,width);
                case "cone" -> new TrailsArea(TrailsArea.Kind.SECTOR,0,range,width);
                default -> new TrailsArea(TrailsArea.Kind.SINGLE,range,0,0);
            };
        }
        public boolean friendly() {return Set.of("BUFF","HEAL","REGEN","CLEANSE","READY","ADVANCE").contains(kind);}
        public Vec3 aim(LivingEntity actor,LivingEntity target) {
            if(shape.equals("self"))return Vec3.ZERO;
            Vec3 delta=target.position().subtract(actor.position()).multiply(1,0,1);
            return shape.equals("circle") && delta.length()>range?delta.normalize().scale(range):delta;
        }
    }
    public record Profile(String id,Move basic,Move skill,Move ultimate) {}
    private static final Map<String,Profile> PROFILES=load();
    private static Map<String,Profile> load() {
        try(var stream=TrailsBestiary.class.getResourceAsStream("/data/mineturn/trails_bestiary.json")) {
            if(stream==null)throw new IllegalStateException("Missing AT bestiary");
            var entries=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Profile[].class);
            var result=new LinkedHashMap<String,Profile>();
            for(var p:entries){
                if(result.put(p.id,p)!=null || p.basic==null || p.skill==null)throw new IllegalStateException("Invalid bestiary entry: "+p.id);
                if(p.basic.cpCost!=0 || p.skill.cpCost<1 || p.skill.cpCost>TrailsCp.MAX)throw new IllegalStateException("Invalid CP cost: "+p.id);
                for(var move:List.of(p.basic,p.skill)){if(move.name==null || move.name.isBlank() || move.animation==null || move.range<0 || move.range>32 || move.radius<0 || move.radius>12)throw new IllegalStateException("Invalid move: "+p.id);}
            }
            return Collections.unmodifiableMap(result);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static Profile get(String id){return PROFILES.get(id);}
    public static Profile get(LivingEntity e){return get(TrailsCombat.id(e));}
    public static Collection<Profile> all(){return PROFILES.values();}
    private TrailsBestiary(){}
}
