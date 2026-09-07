package com.photonspark.sparkmotors.gametest;
import com.mojang.authlib.GameProfile;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.Assembly;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("sparkmotors")
@PrefixGameTestTemplate(false)
public final class CarGameTests {
    private CarEntity car(GameTestHelper h){
        var car=AutoPropulsionAge.CAR.get().create(h.getLevel());var p=h.absoluteVec(new Vec3(8,2.05,8));
        car.moveTo(p.x,p.y,p.z,0,0);h.getLevel().addFreshEntity(car);return car;
    }
    private ServerPlayer owner(GameTestHelper h,CarEntity car){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"alpha_mechanic"));
        p.setGameMode(GameType.SURVIVAL);p.moveTo(car.position().add(2,0,0));car.setOwner(p.getUUID());return p;
    }
    @GameTest(template="test_track") public void persistence(GameTestHelper h){
        var car=car(h);var player=owner(h,car);
        car.setConfiguration(Assembly.WHEELS.with(car.config(),2));
        car.tickCount+=4;car.action(player,CarPackets.TUNE,6200,420);
        var tag=new CompoundTag();car.saveWithoutId(tag);
        var loaded=AutoPropulsionAge.CAR.get().create(h.getLevel());loaded.load(tag);
        h.assertTrue(loaded.config()==car.config(),"Assembly state must survive save/load");
        h.assertTrue(loaded.fuel()==40&&loaded.health()==100,"Fuel and condition must survive save/load");
        h.assertTrue(loaded.limiter()==6200&&Math.abs(loaded.finalDrive()-4.2)<.001,"Tune must survive save/load");
        h.assertTrue(loaded.mayModify(player),"Owner must survive save/load");h.succeed();
    }
    @GameTest(template="test_track") public void inventoryTransactionAndOwnership(GameTestHelper h){
        var car=car(h);var player=owner(h,car);var sport=AutoPropulsionAge.partItem(Assembly.ENGINE,2);var stock=AutoPropulsionAge.partItem(Assembly.ENGINE,1);
        car.action(player,CarPackets.INSTALL,0,2);h.assertTrue(Assembly.ENGINE.variant(car.config())==1,"Cannot install missing inventory item");
        player.getInventory().add(new ItemStack(sport));car.tickCount+=4;car.action(player,CarPackets.INSTALL,0,2);
        h.assertTrue(Assembly.ENGINE.variant(car.config())==2,"Sport engine must install");
        h.assertTrue(player.getInventory().countItem(sport)==0&&player.getInventory().countItem(stock)==1,"Swap must consume new assembly and return old once");
        var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"alpha_stranger"));stranger.setGameMode(GameType.SURVIVAL);stranger.moveTo(player.position());
        car.tickCount+=4;car.action(stranger,CarPackets.INSTALL,0,0);h.assertTrue(Assembly.ENGINE.variant(car.config())==2,"Non-owner cannot remove parts");
        car.tickCount+=4;car.action(player,CarPackets.INSTALL,99,99);h.assertTrue(Assembly.ENGINE.variant(car.config())==2,"Invalid slot/variant cannot corrupt assembly state");
        h.succeed();
    }
    @GameTest(template="test_track") public void refuelAndTuningBounds(GameTestHelper h){
        var car=car(h);var player=owner(h,car);player.getInventory().add(new ItemStack(AutoPropulsionAge.FUEL_CAN.get()));
        car.action(player,CarPackets.REFUEL,0,0);h.assertTrue(car.fuel()==50,"Fuel can adds 10 L");
        h.assertTrue(player.getInventory().countItem(AutoPropulsionAge.FUEL_CAN.get())==0,"Fuel can is consumed");
        car.tickCount+=4;car.action(player,CarPackets.TUNE,Integer.MAX_VALUE,Integer.MIN_VALUE);
        h.assertTrue(car.limiter()==7000&&Math.abs(car.finalDrive()-2.8)<.001,"Server must clamp tuning values");
        car.tickCount+=4;car.action(player,CarPackets.IGNITION,0,0);car.tickCount+=4;car.action(player,CarPackets.INSTALL,0,0);
        h.assertTrue(Assembly.ENGINE.variant(car.config())==1,"Running engine blocks garage changes");h.succeed();
    }
    @GameTest(template="test_track",timeoutTicks=160) public void drivesAndBrakesInWorld(GameTestHelper h){
        var car=car(h);var player=h.makeMockServerPlayerInLevel();player.moveTo(car.position().add(2,0,0));car.setOwner(player.getUUID());
        h.assertTrue(player.startRiding(car,true),"Test driver must mount the car");car.action(player,CarPackets.IGNITION,0,0);
        double startZ=car.getZ();int[] t={0};
        h.onEachTick(()->{t[0]++;car.receiveInput(t[0]<55?1:4,0);});
        h.runAfterDelay(50,()->{h.assertTrue(car.getZ()>startZ+3,"Car must move under server simulation: dz="+(car.getZ()-startZ)+", speed="+car.speed()+", rpm="+car.rpm()+", on="+car.ignition()+", driver="+car.getControllingPassenger()+", ticks="+car.tickCount+", grounded="+car.onGround());h.assertTrue(car.rpm()>850,"Driving RPM must rise");});
        h.runAfterDelay(125,()->{h.assertTrue(Math.abs(car.speed())<.2,"Brakes must stop the car");h.assertTrue(car.fuel()<40,"Driving must consume fuel");h.assertTrue(car.getY()>h.absolutePos(new net.minecraft.core.BlockPos(0,0,0)).getY(),"Car must remain above track");player.stopRiding();h.succeed();});
    }
    @GameTest(template="test_track",timeoutTicks=140) public void solidWallStopsTheCar(GameTestHelper h){
        for(int x=3;x<14;x++)for(int y=2;y<5;y++)h.setBlock(new net.minecraft.core.BlockPos(x,y,18),net.minecraft.world.level.block.Blocks.STONE);
        var car=car(h);var p=h.makeMockServerPlayerInLevel();p.moveTo(car.position());car.setOwner(p.getUUID());p.startRiding(car,true);car.action(p,CarPackets.IGNITION,0,0);
        h.onEachTick(()->car.receiveInput(1,0));
        h.runAfterDelay(110,()->{
            double wall=h.absoluteVec(new Vec3(8,2,18)).z;
            h.assertTrue(car.getZ()<wall-1.9,"The car must not pass through a solid wall");
            h.assertTrue(car.health()<100,"A substantial collision must damage the car");
            h.assertTrue(Math.abs(car.speed())<.5,"A wall must stop forward movement");p.stopRiding();h.succeed();
        });
    }
}
