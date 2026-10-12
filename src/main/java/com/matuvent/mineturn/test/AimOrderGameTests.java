package com.matuvent.mineturn.test;

import com.matuvent.mineturn.network.AimOrder;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mineturn")
@PrefixGameTestTemplate(false)
public final class AimOrderGameTests {
    @GameTest(template="empty")
    public static void aimDuplicateAndLatePacketsDoNotReviveAttempts(GameTestHelper h){
        var order=new AimOrder();var first=UUID.randomUUID();var second=UUID.randomUUID();
        h.assertTrue(order.start(first) && !order.start(first),"Duplicate start reset aiming progress/submission");
        h.assertTrue(order.start(second) && !order.stop(first) && !order.start(first),"Old stop/start replaced newer attempt");
        h.assertTrue(order.stop(second) && !order.start(second) && !order.stop(second),"Resolved attempt revived or closed twice");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void aimCloseBeforeStartAndBattleReset(GameTestHelper h){
        var order=new AimOrder();var token=UUID.randomUUID();
        h.assertTrue(!order.stop(token) && !order.start(token),"Start arriving after close revived invisible attempt");
        for(int i=0;i<1000;i++){var next=UUID.randomUUID();h.assertTrue(order.start(next) && order.stop(next),"Bounded history prevented fresh attempt");}
        var latest=UUID.randomUUID();order.start(latest);order.clear();
        h.assertTrue(order.start(latest),"New battle inherited previous token lifecycle");h.succeed();
    }
    private AimOrderGameTests(){}
}
