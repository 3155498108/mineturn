package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TrailsPartyScreen extends Screen {
    private final Screen parent;private EditBox name;private int page;
    static TrailsNetwork.PartyState state=new TrailsNetwork.PartyState("","","","正在读取队伍…");
    public TrailsPartyScreen(Screen parent){super(Component.literal("玩家队伍"));this.parent=parent;}
    static void received(TrailsNetwork.PartyState p){state=p;var mc=net.minecraft.client.Minecraft.getInstance();if(mc.screen instanceof TrailsPartyScreen s)s.rebuildWidgets();}
    private void send(String op,String value){PacketDistributor.sendToServer(new TrailsNetwork.PartyRequest(op,value));}
    @Override protected void init(){
        name=addRenderableWidget(new EditBox(font,width/2-130,57,174,20,Component.literal("队伍名称")));name.setMaxLength(24);name.setValue(minecraft.player.getScoreboardName()+"的小队");
        button("创建",width/2+48,57,80,()->send("create",name.getValue())).active=state.team().isEmpty();
        button("接受邀请",width/2-130,83,126,()->send("accept","")).active=!state.inviter().isEmpty();
        button("离开队伍",width/2+2,83,126,()->send("leave","")).active=!state.team().isEmpty();
        var players=minecraft.getConnection().getOnlinePlayers().stream().filter(p->!p.getProfile().getId().equals(minecraft.player.getUUID())).sorted(java.util.Comparator.comparing(p->p.getProfile().getName())).toList();
        int rows=Math.max(1,(height-180)/23),pages=Math.max(1,(players.size()+rows-1)/rows);page=Math.clamp(page,0,pages-1);
        for(int i=page*rows;i<Math.min(players.size(),(page+1)*rows);i++){var p=players.get(i);button("邀请 "+p.getProfile().getName(),width/2-130,112+(i-page*rows)*23,258,()->send("invite",p.getProfile().getId().toString()));}
        if(pages>1)button("在线玩家 "+(page+1)+"/"+pages+" ›",width/2-130,height-61,126,()->{page=(page+1)%pages;rebuildWidgets();});
        button("返回",width/2+2,height-61,126,this::onClose);
    }
    private Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
    @Override public void added(){super.added();send("state","");}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public void render(GuiGraphics g,int x,int y,float p){g.fill(0,0,width,height,0xee101521);g.drawCenteredString(font,title,width/2,12,-1);g.drawCenteredString(font,"当前："+(state.team().isEmpty()?"未组队（其他玩家为对手）":state.team()),width/2,29,0xff80eec4);g.drawCenteredString(font,state.inviter().isEmpty()?"命名牌归属个人；同队也不能操作别人的生物":"邀请："+state.inviter()+" → "+state.inviteTeam(),width/2,43,0xffffd486);super.render(g,x,y,p);g.drawCenteredString(font,font.plainSubstrByWidth(state.message(),width-16),width/2,height-29,0xffffd486);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
