package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.battle.TrailsSmokeScenario;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.LivingEntity;

/** Real renderer QA uses only the named disposable save already checked by TrailsSmoke. */
final class TrailsBestiarySmoke {
    private static final String[] IDS={"wither","ender_dragon","warden","vex","phantom","evoker","shulker","frog","pufferfish","armadillo","allay","sheep"};
    private static int mode=-1,ticks;private static long models,dangers,effects;private static boolean golden,struck,nativePose,sonicComplete;
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var server=Minecraft.getInstance().getSingleplayerServer();server.execute(()->action.accept(server.getPlayerList().getPlayers().getFirst()));}
    private static void prepare(){
        ticks=0;golden=false;struck=false;nativePose=false;sonicComplete=false;models=TrailsWarnings.goldenModels;dangers=TrailsWarnings.dangerFrames;effects=TrailsEffects.renderedEffects;
        BattleClient.yaw=0;BattleClient.pitch=32;BattleClient.distance=24;TrailsPresentationOptions.effects=mode!=2;
        server(p->TrailsSmokeScenario.bestiary(p,IDS[mode],mode<3));
    }
    static void tick(){
        var mc=Minecraft.getInstance();if(mode==-1){mode=0;prepare();return;}ticks++;
        if(mode==IDS.length){if(ticks==15){
            if(TrailsClient.active() || TrailsPerformances.count()!=0 || TrailsCinematics.active())throw new IllegalStateException("Bestiary exit left presentation state");
            MineTurn.LOGGER.info("TRAILS_BESTIARY_CLIENT_PASSED: three persistent gold bosses, warnings with effects disabled, native S casts, Vex/Phantom/Evoker/Shulker/Frog/Pufferfish/Armadillo/Allay/Sheep and exit cleanup; gold models={} danger frames={}",TrailsWarnings.goldenModels,TrailsWarnings.dangerFrames);mc.stop();
        }return;}
        if(ticks==8){BattleClient.yaw=0;BattleClient.pitch=28;BattleClient.distance=24;}
        if(!TrailsClient.active()) {if(ticks>40)throw new IllegalStateException("Bestiary scenario did not start "+IDS[mode]);return;}
        if(mode<3){
            var boss=TrailsClient.fighters.stream().filter(f->f.enemy()).findFirst().orElseThrow();var entity=(LivingEntity)mc.level.getEntity(boss.id());
            if(ticks<130 && entity!=null && TrailsWarnings.gold(entity) && boss.threat()!=null && boss.threat().golden())golden=true;
            if(ticks==100 || ticks==120)shot("gold-"+IDS[mode]+"-"+ticks);
            if(ticks==110){
                if(!golden || !TrailsWarnings.gold(entity) || TrailsWarnings.goldenModels<=models || TrailsWarnings.dangerFrames<=dangers || TrailsClient.view.busy())throw new IllegalStateException("Persistent native gold / playable warning failed "+IDS[mode]);
                if(mode==2 && TrailsEffects.renderedEffects!=effects)throw new IllegalStateException("Decorative effects were not disabled");
                shot("gold-"+IDS[mode]);
            }
            if(ticks==130){TrailsPresentationOptions.effects=true;server(TrailsSmokeScenario::releaseBestiary);}
            if(ticks>130 && entity!=null){var frame=TrailsPerformances.frame(entity);if(frame!=null && frame.style().startsWith("boss_")){
                if(frame.phase().equals("strike"))struck=true;
                if(mode==2 && ((net.minecraft.world.entity.monster.warden.Warden)entity).sonicBoomAnimationState.isStarted())nativePose=true;
                if(mode==2 && frame.phase().equals("recover") && ((net.minecraft.world.entity.monster.warden.Warden)entity).sonicBoomAnimationState.getAccumulatedTime()>=2500)sonicComplete=true;
                if(frame.phase().equals("strike") && frame.progress()<.3)shotOnce("s-"+IDS[mode]);
            }}
            if(ticks==215){if(!struck || mode==2 && (!nativePose || !sonicComplete))throw new IllegalStateException("Native boss S missing "+IDS[mode]);next();}
        }else {
            if(ticks==12 && mode>=7)server(TrailsSmokeScenario::releaseBestiary);
            for(var fighter:TrailsClient.fighters)if(mc.level.getEntity(fighter.id()) instanceof LivingEntity entity){
                var frame=TrailsPerformances.frame(entity);if(frame==null || entity==mc.player)continue;
                if(frame.phase().equals("strike") || frame.phase().equals("support"))struck=true;
                if(entity instanceof net.minecraft.world.entity.monster.Vex vex && vex.isCharging())nativePose=true;
                if(entity instanceof net.minecraft.world.entity.monster.SpellcasterIllager spell && spell.isCastingSpell())nativePose=true;
                if(entity instanceof net.minecraft.world.entity.animal.frog.Frog frog && frog.getPose()==net.minecraft.world.entity.Pose.CROAKING)nativePose=true;
                if(entity instanceof net.minecraft.world.entity.animal.Pufferfish fish && fish.getPuffState()==2)nativePose=true;
                if(entity instanceof net.minecraft.world.entity.animal.armadillo.Armadillo armadillo && armadillo.shouldHideInShell())nativePose=true;
                if(ticks==30 || ticks==50)shot("species-"+IDS[mode]+"-"+ticks);
            }
            if(ticks==110){
                if(!struck)throw new IllegalStateException("No staged species action "+IDS[mode]);
                if(java.util.Set.of(3,5,7,8,9).contains(mode) && !nativePose)throw new IllegalStateException("Native species flag missing "+IDS[mode]);
                next();
            }
        }
    }
    private static final java.util.Set<String> SHOTS=new java.util.HashSet<>();
    private static void shotOnce(String label){if(SHOTS.add(label))shot(label);}
    private static void shot(String label){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"bestiary-"+label+"-"+System.getProperty("mineturn.qaTag")+".png",mc.getMainRenderTarget(),message->{});}
    private static void next(){MineTurn.LOGGER.info("QA_BESTIARY_OK: {} gold={} strike={} native={}",IDS[mode],golden,struck,nativePose);mode++;if(mode<IDS.length)prepare();else{ticks=0;TrailsPresentationOptions.effects=true;server(TrailsSmokeScenario::stop);}}
    private TrailsBestiarySmoke(){}
}
