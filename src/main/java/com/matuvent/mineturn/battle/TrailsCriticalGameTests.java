package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.TrailsConfig;
import com.matuvent.mineturn.network.TrailsNetwork;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("trails")
@PrefixGameTestTemplate(false)
public final class TrailsCriticalGameTests {
    @GameTest(template="empty")
    public static void criticalMultipliesBeforeArmorAndReportsActualHealthLoss(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            var z=f.mob(EntityType.ZOMBIE,7,5);z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);z.setHealth(100);f.start(false);var c=f.b.trails;
            float hp=z.getHealth();c.hit(f.p,z,8);float white=hp-z.getHealth();
            h.assertTrue(!c.lastImpact.critical() && Math.abs(c.lastImpact.amount()-white)<.001,"Normal hit flagged critical or reported raw damage");
            c.criticalRoll=()->0;hp=z.getHealth();c.hit(f.p,z,8);float red=hp-z.getHealth();
            h.assertTrue(c.lastImpact.critical() && red>white && Math.abs(c.lastImpact.amount()-red)<.001,"Critical did not increase real damage or report actual loss");
            z.setInvulnerable(true);hp=z.getHealth();c.hit(f.p,z,8);
            h.assertTrue(z.getHealth()==hp && c.lastImpact.amount()==0 && !c.lastImpact.critical(),"Immune hit incorrectly displayed red critical damage");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void chanceUsesOneRollPerContactAndHonorsZeroAndFullProbability(GameTestHelper h){
        double saved=TrailsConfig.current.criticalChance;
        try(var f=new TrailsGameTests.Fixture(h)){
            var z=f.mob(EntityType.ZOMBIE,7,5);f.start(false);var c=f.b.trails;var calls=new java.util.concurrent.atomic.AtomicInteger();c.criticalRoll=()->calls.getAndIncrement()/100.;
            TrailsConfig.current.criticalChance=.1;int crits=0;
            for(int i=0;i<100;i++){z.setHealth(z.getMaxHealth());c.hit(f.p,z,1);if(c.lastImpact.critical())crits++;}
            h.assertTrue(crits==10 && calls.get()==100,"10% boundary or one-roll-per-contact failed");
            c.hit(f.p,z,0);h.assertTrue(calls.get()==100 && !c.lastImpact.critical() && c.lastImpact.amount()==0,"Zero damage rolled critical");
            TrailsConfig.current.criticalChance=0;c.criticalRoll=()->0;c.hit(f.p,z,1);h.assertTrue(!c.lastImpact.critical(),"0% still crit");
            TrailsConfig.current.criticalChance=1;c.criticalRoll=()->.999;c.hit(f.p,z,1);h.assertTrue(c.lastImpact.critical(),"100% failed to crit");
        }finally{TrailsConfig.current.criticalChance=saved;}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void criticalArrowStillConsumesOneArrowAndOneDurability(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            var z=f.mob(EntityType.ZOMBIE,5,11);z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);z.setHealth(100);
            f.p.getInventory().setItem(1,new ItemStack(Items.BOW));f.p.getInventory().setItem(2,new ItemStack(Items.ARROW,3));f.start(false);var c=f.b.trails;c.criticalRoll=()->0;
            c.attack(z.getId(),1,false);h.assertTrue(c.lastImpact==null,"Arrow damage preceded flight");TrailsGameTests.resolve(c);
            h.assertTrue(c.lastImpact!=null && c.lastImpact.critical() && f.p.getInventory().getItem(2).getCount()==2 && f.p.getInventory().getItem(1).getDamageValue()==1,"Critical duplicated payment or was lost across flight");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void protocolKeepsCriticalFlagSeparateFromAmount(GameTestHelper h){
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            for(boolean critical:new boolean[]{false,true}){
                var p=new TrailsNetwork.Impact(java.util.UUID.randomUUID(),17,5,critical?2:24,net.minecraft.world.phys.Vec3.ZERO,critical);
                TrailsNetwork.Impact.CODEC.encode(buf,p);h.assertTrue(TrailsNetwork.Impact.CODEC.decode(buf).equals(p),"Impact flag/amount codec mismatch");
            }
            var settings=new TrailsNetwork.Settings(20,6,4,.37,2.4,true,"saved");TrailsNetwork.Settings.CODEC.encode(buf,settings);
            h.assertTrue(TrailsNetwork.Settings.CODEC.decode(buf).equals(settings) && !buf.isReadable(),"Critical settings codec mismatch");
        }finally{buf.release();}h.succeed();
    }
    @GameTest(template="empty")
    public static void criticalSettingsPersistAndRejectInvalidValuesWithoutPartialWrite(GameTestHelper h) throws Exception {
        var path=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("mineturn-trails.json");byte[] saved=java.nio.file.Files.readAllBytes(path);var previous=TrailsConfig.current;
        try{
            TrailsConfig.current=new com.google.gson.Gson().fromJson(new String(saved,java.nio.charset.StandardCharsets.UTF_8),TrailsConfig.class);
            TrailsConfig.saveCombat(14,7,4,.25,2);
            var text=java.nio.file.Files.readString(path);var json=com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            h.assertTrue(json.get("criticalChance").getAsDouble()==.25 && json.get("criticalMultiplier").getAsDouble()==2,"Critical settings not saved");
            for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1}){
                try{TrailsConfig.saveCombat(20,6,4,invalid,2);throw new IllegalStateException("Invalid critical chance accepted");}catch(IllegalArgumentException expected){}
            }
            h.assertTrue(java.nio.file.Files.readString(path).equals(text),"Invalid input partially overwrote config");
            TrailsConfig.load();h.assertTrue(TrailsConfig.current.criticalChance==.25 && TrailsConfig.current.criticalMultiplier==2,"Saved settings not loaded");
        }finally{java.nio.file.Files.write(path,saved);TrailsConfig.current=previous;}h.succeed();
    }
    private TrailsCriticalGameTests(){}
}
