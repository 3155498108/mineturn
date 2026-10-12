package com.matuvent.mineturn.test;

import com.matuvent.mineturn.battle.Timeline;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mineturn")
@PrefixGameTestTemplate(false)
public final class TimelineReplacementGameTests {
    @GameTest(template="empty")
    public static void conversionPreservesProgressAndTieOrder(GameTestHelper h){
        var timeline=new Timeline<String>();timeline.add("zombie");timeline.add("player");timeline.advance(40,e->100);
        h.assertTrue(timeline.replace("zombie","drowned"),"Missing conversion source");
        var forecast=timeline.forecast(4,e->100);
        h.assertTrue(timeline.time()==40 && timeline.remaining("drowned")==6000 && forecast.get(0).actor().equals("drowned") && forecast.get(0).inAv()==60
                && forecast.get(1).actor().equals("player") && forecast.get(1).inAv()==60,"Conversion reset progress or moved tied actor to end");
        h.assertTrue(timeline.next(e->100).equals("drowned") && timeline.next(e->100).equals("player") && timeline.time()==100,"Forecast and execution disagree after conversion");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void conversionSpeedChangesOnlyRemainingTravelTime(GameTestHelper h){
        var timeline=new Timeline<String>();timeline.add("old");timeline.add("other");timeline.advance(30,e->100);
        timeline.replace("old","fast");
        java.util.function.ToDoubleFunction<String> speed=e->e.equals("fast")?200:100;
        h.assertTrue(timeline.remaining("fast")==7000 && timeline.until("fast",speed)==35 && timeline.until("other",speed)==70,"Conversion granted progress instead of applying new speed to remaining distance");
        h.assertTrue(timeline.next(speed).equals("fast") && timeline.time()==65 && timeline.remaining("other")==3500,"Conversion altered another actor's travel");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void rejectedConversionCannotMergeOrDuplicateActors(GameTestHelper h){
        var timeline=new Timeline<String>();timeline.add("a");timeline.add("b");timeline.advance(99,e->100);
        var before=timeline.forecast(5,e->100);boolean rejected=false;
        try{timeline.replace("a","b");}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected && !timeline.replace("missing","c") && timeline.replace("a","a") && timeline.forecast(5,e->100).equals(before)
                && timeline.time()==99,"Rejected conversion changed time/order or merged actors");
        timeline.advance(1,e->100);timeline.replace("a","converted");
        h.assertTrue(timeline.next(e->100).equals("converted") && timeline.time()==100,"Ready actor lost its turn during conversion");h.succeed();
    }
    private TimelineReplacementGameTests(){}
}
