package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.network.TrailsNetwork;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Species tactics share the existing action clock, checked movement and authorized damage paths. */
final class TrailsBestiaryActions {
    record Plan(TrailsBestiary.Move move,TrailsNetwork.Threat warning) {}
    static void enemy(TrailsCombat c) {
        var b=c.b;var actor=b.actor;if(actor==null || c.busy())return;
        var target=b.enemies(actor).stream().min(Comparator.comparingDouble(actor::distanceToSqr)).orElse(null);
        if(target==null){b.close("战斗结束。");return;}
        var profile=TrailsBestiary.get(actor);
        if(profile==null){c.finish(1);return;}
        var plan=c.plans.remove(actor.getUUID());
        if(plan!=null){c.charging.remove(actor.getUUID());if(plan.warning.golden() || TrailsCp.get(actor)>=plan.move.cpCost()){cast(c,actor,target,plan.move,plan.warning.aim(),plan.warning.golden(),plan.warning.origin());return;}}
        int count=c.turns.getOrDefault(actor.getUUID(),1);
        if(profile.ultimate()!=null && count%3==0){prepare(c,actor,target,profile.ultimate(),true);return;}
        if(profile.basic().kind().equals("CHARGE")){
            if(TrailsCombat.gap(actor,target)>4){approach(c,actor,target);return;}
            if(TrailsCp.get(actor)<profile.skill().cpCost()){c.addCp(actor,com.matuvent.mineturn.TrailsConfig.current.cpPerAction);c.finish(1);return;}
            prepare(c,actor,target,profile.skill(),false);return;
        }
        var move=count%(profile.ultimate()!=null?2:3)==0?profile.skill():profile.basic();
        if(TrailsCp.get(actor)<move.cpCost())move=profile.basic();
        if(!move.friendly() && TrailsCombat.gap(actor,target)>move.range() && !move.shape().equals("self")
                || !move.ignoreSight() && !move.friendly() && !actor.hasLineOfSight(target)) {approach(c,actor,target);return;}
        cast(c,actor,target,move,move.aim(actor,target),false,actor.position());
    }
    static void prepare(TrailsCombat c,LivingEntity actor,LivingEntity target,TrailsBestiary.Move move,boolean golden) {
        Vec3 origin=actor.position();
        if(actor.getType()==EntityType.ENDER_DRAGON || actor.getType()==EntityType.WITHER)origin=new Vec3(origin.x,target.getY(),origin.z);
        var threat=new TrailsNetwork.Threat(move.name(),golden,origin,move.aim(actor,target),move.area());
        c.plans.put(actor.getUUID(),new Plan(move,threat));c.charging.add(actor.getUUID());
        c.perform(actor,"telegraph",32,!golden,target.position(),(golden?"gold_":"warn_")+move.animation(),move.name()+" · 下次自身回合");
        c.b.message((golden?"⚠ 金色蓄力 · ":"⚠ 危险蓄力 · ")+actor.getName().getString()+" 下次自身回合发动 "+move.name()+"！可移动避开范围、防御或抢 S。");
        c.finish(1.6);
    }
    static void approach(TrailsCombat c,LivingEntity source,LivingEntity target) {
        if(source instanceof Mob mob){
            var path=c.b.pursuitRoute(mob,target);
            if(path.cost()>.01){c.b.budget.move(path.cost());c.b.motion=new BattleSession.Movement(source,path,c.b.animationMode(source,path));c.b.motion.onLanding=()->c.finish(1);c.b.revision++;c.b.syncAll();return;}
        }
        c.finish(.8);
    }
    static void cast(TrailsCombat c,LivingEntity source,LivingEntity target,TrailsBestiary.Move move,Vec3 aim,boolean ultimate,Vec3 origin) {
        if(c.busy())throw new IllegalArgumentException("请等待当前动作完成。");
        int cpCost=ultimate?0:move.cpCost();
        TrailsCp.require(source,cpCost);
        if(!move.area().validAim(aim))throw new IllegalArgumentException("技能落点超出施放范围。");
        if(move.area().kind()==TrailsArea.Kind.SINGLE){
            if(target==null || !target.isAlive() || !c.enemy(source,target) || !c.b.members.containsKey(target.getUUID()))throw new IllegalArgumentException("请选择存活的敌人。");
            if(TrailsCombat.gap(source,target)>move.range() || !move.ignoreSight() && !source.hasLineOfSight(target))throw new IllegalArgumentException("目标超出技能射程或被遮挡。");
        }
        if((move.kind().equals("PHASE") || move.kind().equals("DASH")) && target!=null && TrailsCombat.gap(source,target)>2.2){
            Vec3 delta=target.position().subtract(source.position());
            Vec3 offset=delta.normalize().scale(Math.max(0,delta.length()-1.6));
            var route=SpatialPath.trace(source,offset,false,move.kind().equals("PHASE"),point->point.subtract(c.b.center).horizontalDistance()<=com.matuvent.mineturn.TrailsConfig.current.encounterRadius+4);
            if(route.cost()>.01 && source.level().noCollision(source,source.getBoundingBox().move(route.destination().subtract(source.position())))){
                c.perform(source,"draw",24,false,target.position(),move.animation(),move.name());
                c.b.motion=new BattleSession.Movement(source,route,move.kind().equals("PHASE")?"phasing":"leap");
                c.b.motion.onLanding=()->cast(c,source,target,move,Vec3.ZERO,ultimate,source.position());c.b.revision++;c.b.syncAll();return;
            }
            c.b.message("突袭路线受阻。");c.finish(1.2);return;
        }
        TrailsCp.spend(source,cpCost);
        String style=(ultimate?"boss_":"")+move.animation();
        c.performances.put(source.getUUID(),new String[]{style,move.name()});
        Vec3 point=move.shape().equals("circle")?origin.add(aim).add(0,1,0):target==null?source.getEyePosition():target.getEyePosition();
        if(ultimate && move.area().kind()!=TrailsArea.Kind.SINGLE)point=(move.area().kind()==TrailsArea.Kind.DISK
                ?move.area().center(origin,aim):origin.add(move.area().direction(aim).scale(move.area().radius()))).add(0,1,0);
        final Vec3 impactPoint=point;
        boolean red=!c.allied(source) && !ultimate && TrailsBestiary.get(source)!=null && move==TrailsBestiary.get(source).skill();
        c.b.message((ultimate?"S 技 · ":"")+source.getName().getString()+" · "+move.name());
        if(source.getType()==EntityType.WARDEN && move.animation().equals("sonic"))source.playSound(SoundEvents.WARDEN_SONIC_CHARGE,1,1);
        c.windupAt(source,impactPoint,move.animation().equals("sonic")?34:ultimate?28:move.friendly()?12:move.projectile().isBlank()?14:20,red,()->{
            if(move.kind().equals("TELEPORT") && target!=null)blink(c,source,target);
            int flight=move.animation().equals("sonic")?18:move.projectile().isBlank()?5:TrailsChoreography.flight(source.getEyePosition().distanceTo(impactPoint));
            c.perform(source,move.friendly()?"support":"strike",flight,red,impactPoint,style,move.name());
            if(!move.projectile().isBlank()){
                var item=BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(move.projectile()));
                c.projectile(source,impactPoint,item);
                if(ultimate || move.name().contains("齐射") || move.name().contains("三"))for(int lane:new int[]{-1,1})c.projectile(source,impactPoint.add(lane*.7,0,0),item);
            }
            if(source.getType()==EntityType.WARDEN)source.playSound(move.animation().equals("sonic")?SoundEvents.WARDEN_SONIC_BOOM:SoundEvents.WARDEN_ATTACK_IMPACT,1,1);
            c.pending=new TrailsCombat.Pending(source,c.turn,flight,()->{
                apply(c,source,target,move,aim,origin);
                if(c.b.closed)return;
                boolean canLaunch=target!=null && target.isAlive() && c.b.members.containsKey(target.getUUID()) && c.enemy(source,target)
                        && TrailsCombat.gap(source,target)<=move.range() && source.hasLineOfSight(target);
                if(!ultimate && cpCost==0)c.addCp(source,com.matuvent.mineturn.TrailsConfig.current.cpPerAction);
                if(move.kind().equals("WIND") && canLaunch && c.knockUp(source,target,move.power()>1?1.04:.92))return;
                if(move.kind().equals("LEVITATE") && canLaunch && c.knockUp(source,target,.65))return;
                if(move.kind().equals("READY")){c.pending=new TrailsCombat.Pending(source,c.turn,8,()->c.b.next(),false);return;}
                c.recover(source,ultimate?2.2:move.friendly()?1.3:1.25);
            },red);
        });
    }
    static void apply(TrailsCombat c,LivingEntity source,LivingEntity selected,TrailsBestiary.Move move,Vec3 aim,Vec3 origin) {
        if(c.b.closed || !source.isAlive())return;
        var recipients=new ArrayList<LivingEntity>();
        for(var member:List.copyOf(c.b.members.values())){
            var e=member.entity;
            if(!e.isAlive() || move.friendly()==c.enemy(source,e))continue;
            boolean aerial=source.getType()==EntityType.WITHER || source.getType()==EntityType.ENDER_DRAGON;
            Vec3 point=aerial?new Vec3(e.getX(),origin.y,e.getZ()):e.position();
            boolean contains=move.area().kind()==TrailsArea.Kind.SINGLE?e==selected && TrailsCombat.gap(source,e)<=move.range()
                    :move.area().contains(origin,aim,point) && (!aerial || Math.abs(e.getY()-origin.y)<=24);
            if(move.friendly() && move.shape().equals("self") && move.radius()<=.8 && e!=source)continue;
            if(contains && (move.friendly() || move.ignoreSight() || source.hasLineOfSight(e)))recipients.add(e);
        }
        for(var e:recipients){
            if(c.b.closed || !e.isAlive())break;
            switch(move.kind()){
                case "BUFF" -> buff(e,move);
                case "HEAL" -> e.heal((float)move.power());
                case "REGEN" -> {e.heal((float)move.power());c.regrowth.put(e.getUUID(),3);}
                case "CLEANSE" -> {for(var effect:List.copyOf(e.getActiveEffects()))if(!effect.getEffect().value().isBeneficial())BattleManager.authorized(()->e.removeEffect(effect.getEffect()));e.heal((float)move.power());}
                case "ADVANCE" -> c.b.clock.recovery(e,Math.max(0,c.b.clock.remaining(e)-20*c.speed(e)));
                case "READY" -> {}
                default -> {
                    if(move.kind().equals("STAGGER"))c.guarding.remove(e.getUUID());
                    var strength=source.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
                    var weakness=source.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
                    double power=Math.max(0,move.power()+(strength==null?0:3*(strength.getAmplifier()+1))-(weakness==null?0:4*(weakness.getAmplifier()+1)));
                    if(move.animation().equals("sonic"))c.damage(source,e,move.power(),source.damageSources().sonicBoom(source));else c.hit(source,e,power);
                    if(e.isAlive() && !move.buff().isBlank())buff(e,move);
                    if(move.kind().equals("FIRE"))BattleManager.authorized(()->e.igniteForSeconds(3));
                    if(move.kind().equals("STAGGER") && c.b.members.containsKey(e.getUUID()))c.b.clock.recovery(e,c.b.clock.remaining(e)+30*c.speed(e));
                    if(move.kind().equals("DRAIN"))source.heal((float)move.power()*.3f);
                }
            }
        }
        if(move.kind().equals("READY"))c.b.clock.readyFirst(recipients);
        if(TrailsBestiary.get(source)!=null && TrailsBestiary.get(source).id().equals("minecraft:witch") && move==TrailsBestiary.get(source).skill())source.heal(6);
        if(move.kind().equals("EXPLODE"))BattleManager.authorized(()->source.hurt(source.damageSources().generic(),Float.MAX_VALUE));
    }
    static void buff(LivingEntity e,TrailsBestiary.Move move){
        if(move.buff().isBlank())return;
        var holder=BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.withDefaultNamespace(move.buff()));
        holder.ifPresent(type->TrailsCombat.effect(e,type,move.ticks(),move.amplifier()));
    }
    static void blink(TrailsCombat c,LivingEntity source,LivingEntity target){
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4;Vec3 point=target.position().add(Math.cos(angle)*1.8,0,Math.sin(angle)*1.8);
            if(!source.level().hasChunkAt(net.minecraft.core.BlockPos.containing(point)) || !TerrainPath.supportedAt(source,point)
                    || !source.level().noCollision(source,source.getBoundingBox().move(point.subtract(source.position()))))continue;
            c.b.place(source,point);return;
        }
    }
    private TrailsBestiaryActions(){}
}
