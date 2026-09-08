package com.photonspark.sparkmotors.gametest;

import com.mojang.authlib.GameProfile;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class DrivingGameTests {
    private CarEntity car(GameTestHelper h){var c=AutoPropulsionAge.CAR.get().create(h.getLevel());var p=h.absoluteVec(new Vec3(8,2.15,8));c.moveTo(p.x,p.y,p.z,0,0);h.getLevel().addFreshEntity(c);return c;}
    private ServerPlayer owner(GameTestHelper h,CarEntity c){var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"drive_mechanic"));p.setGameMode(GameType.SURVIVAL);p.moveTo(c.position().add(2,0,0));c.setOwner(p.getUUID());return p;}
    private void act(CarEntity c,ServerPlayer p,int op,int a,int b){c.tickCount+=4;c.action(p,op,a,b);}
    private void fit(CarEntity c,ServerPlayer p,DriveConfig next){act(c,p,CarPackets.DRIVE_SETUP,next.packed(),next.frontPercent());}
    @GameTest(template="test_track") public void layoutDifferentialSplitAndUsedPartsSurviveSaveAndMigration(GameTestHelper h){
        var c=car(h);var used=c.mechanics().get("driveline.differential").condition(.37,.11,0);
        c.setMechanics(c.mechanics().with("driveline.differential",used));
        for(var layout:DriveConfig.Layout.values())for(var diff:DriveConfig.Differential.values())for(int split=20;split<=80;split+=10){
            var expected=new DriveConfig(layout,diff,split);c.setDriveConfig(expected);
            var tag=new CompoundTag();c.saveWithoutId(tag);c.load(tag);
            h.assertTrue(c.driveConfig().equals(expected),"Driveline configuration must survive NBT: "+expected);
            h.assertTrue(c.mechanics().get("driveline.differential").equals(used),"Saving/reloading cannot replace or heal the differential");
        }
        var legacy=new CompoundTag();c.saveWithoutId(legacy);legacy.putInt("DataVersion",4);legacy.remove("DriveSetup");legacy.remove("FrontSplit");c.load(legacy);
        h.assertTrue(c.driveConfig().equals(DriveConfig.stock()),"Pre-handling saves migrate to the RWD preset");
        legacy.putInt("DriveSetup",Integer.MAX_VALUE);legacy.putInt("FrontSplit",-100);c.load(legacy);
        h.assertTrue(c.driveConfig().equals(DriveConfig.stock()),"Malformed saved layout falls back safely");h.succeed();
    }
    @GameTest(template="test_track") public void conversionsNeedAccessMaterialsAndPreserveActualParts(GameTestHelper h){
        var c=car(h);var p=owner(h,c);var next=DriveConfig.preset(DriveConfig.Layout.FWD);
        var used=c.mechanics().get("driveline.differential").condition(.42,.10,0);c.setMechanics(c.mechanics().with("driveline.differential",used));
        p.getInventory().add(new ItemStack(Items.IRON_INGOT,16));fit(c,p,next);
        h.assertTrue(c.driveConfig().equals(DriveConfig.stock())&&p.getInventory().countItem(Items.IRON_INGOT)==16,"Conversion needs a raised jack");
        p.getInventory().add(AutoPropulsionAge.PART_ITEMS.get("service_jack").toStack());act(c,p,CarPackets.JACK,0,0);
        h.assertTrue(c.raised(),"Actual service jack raises the car");fit(c,p,next);
        h.assertTrue(c.driveConfig().equals(next)&&p.getInventory().countItem(Items.IRON_INGOT)==8,"Conversion consumes exactly eight ingots");
        h.assertTrue(c.mechanics().get("driveline.differential").equals(used),"Conversion keeps differential identity, wear and damage");
        fit(c,p,next);h.assertTrue(p.getInventory().countItem(Items.IRON_INGOT)==8,"Repeated current preset must not charge twice");
        fit(c,p,DriveConfig.preset(DriveConfig.Layout.AWD));
        h.assertTrue(c.driveConfig().layout()==DriveConfig.Layout.AWD&&p.getInventory().countItem(Items.IRON_INGOT)==0,"Second conversion consumes remaining materials");
        var split=new DriveConfig(DriveConfig.Layout.AWD,DriveConfig.Differential.LIMITED_SLIP,70);fit(c,p,split);
        h.assertTrue(c.driveConfig().equals(split),"Center split adjustment is free and separate from conversion");
        fit(c,p,next);h.assertTrue(c.driveConfig().equals(split),"No free conversion without materials");h.succeed();
    }
    @GameTest(template="test_track") public void malformedForeignRemoteRunningAndBrokenDrivelineEditsAreRejected(GameTestHelper h){
        var c=car(h);var p=owner(h,c);p.setGameMode(GameType.CREATIVE);act(c,p,CarPackets.JACK,0,0);
        var next=DriveConfig.preset(DriveConfig.Layout.FWD);var original=c.driveConfig();
        act(c,p,CarPackets.DRIVE_SETUP,999,40);act(c,p,CarPackets.DRIVE_SETUP,next.packed(),-1);
        h.assertTrue(c.driveConfig().equals(original),"Malformed packets must be rejected");
        var stranger=owner(h,c);c.setOwner(p.getUUID());stranger.setGameMode(GameType.SURVIVAL);fit(c,stranger,next);
        h.assertTrue(c.driveConfig().equals(original),"Other owners cannot change the driveline");
        p.moveTo(c.position().add(30,0,0));fit(c,p,next);h.assertTrue(c.driveConfig().equals(original),"Remote work rejected");
        p.moveTo(c.position().add(2,0,0));c.setMechanics(c.mechanics().with("driveline.differential",null));fit(c,p,next);
        h.assertTrue(c.driveConfig().equals(original),"Conversion cannot bypass a missing differential");
        var fresh=car(h);p.moveTo(fresh.position().add(2,0,0));fresh.setOwner(p.getUUID());act(fresh,p,CarPackets.IGNITION,0,0);
        fit(fresh,p,next);h.assertTrue(fresh.ignition()&&fresh.driveConfig().equals(original),"Running engine blocks service");h.succeed();
    }
    @GameTest(template="test_track",timeoutTicks=80) public void unsupportedChassisFallsAndSuspensionSettlesAboveTheTrack(GameTestHelper h){
        var c=car(h);c.setPos(c.getX(),c.getY()+2,c.getZ());double initial=c.getY();
        h.runAfterDelay(5,()->{h.assertTrue(c.getY()<initial-.10,"Unsupported chassis must fall under gravity");for(int i=0;i<4;i++)h.assertTrue(!c.wheelContact(i),"High chassis has no imaginary road contact");});
        h.runAfterDelay(65,()->{double gap=c.getY()-h.absoluteVec(new Vec3(8,2,8)).y;
            h.assertTrue(gap>.08&&gap<.23,"Springs settle above the road instead of snapping to it: "+gap);
            for(int i=0;i<4;i++)h.assertTrue(c.wheelContact(i),"Settled wheel must reach road");h.succeed();});
    }
}
