package com.photonspark.sparkmotors.net;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CarPackets {
    public static final int OPEN=0, IGNITION=1, LIGHTS=2, PANELS=3, HORN=4, REFUEL=5, REPAIR=6, INSTALL=7, PAINT=8, TUNE=9,
        HOOD=10,ENGINE_SWAP=11,ENGINE_PART=12,OPEN_ENGINE=13,REPAIR_PART=14;
    public record Input(int entity,int keys,float steer) implements CustomPacketPayload {
        public static final Type<Input> TYPE=new Type<>(AutoPropulsionAge.id("input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=new StreamCodec<>() {
            public Input decode(RegistryFriendlyByteBuf b){return new Input(b.readVarInt(),b.readUnsignedByte(),b.readFloat());}
            public void encode(RegistryFriendlyByteBuf b,Input p){b.writeVarInt(p.entity);b.writeByte(p.keys);b.writeFloat(p.steer);}
        };
        public Type<Input> type(){return TYPE;}
    }
    public record Action(int entity,int action,int a,int b) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(AutoPropulsionAge.id("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=new StreamCodec<>() {
            public Action decode(RegistryFriendlyByteBuf b){return new Action(b.readVarInt(),b.readVarInt(),b.readInt(),b.readInt());}
            public void encode(RegistryFriendlyByteBuf b,Action p){b.writeVarInt(p.entity);b.writeVarInt(p.action);b.writeInt(p.a);b.writeInt(p.b);}
        };
        public Type<Action> type(){return TYPE;}
    }
    public record Open(int entity,boolean engine) implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(AutoPropulsionAge.id("open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=new StreamCodec<>() {
            public Open decode(RegistryFriendlyByteBuf b){return new Open(b.readVarInt(),b.readBoolean());}
            public void encode(RegistryFriendlyByteBuf b,Open p){b.writeVarInt(p.entity);b.writeBoolean(p.engine);}
        };
        public Type<Open> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("3");
        r.playToServer(Input.TYPE,Input.CODEC,(p,ctx)->{
            if(ctx.player().level().getEntity(p.entity) instanceof CarEntity car && car.getControllingPassenger()==ctx.player())
                car.receiveInput(p.keys,p.steer);
        });
        r.playToServer(Action.TYPE,Action.CODEC,(p,ctx)->{
            if(ctx.player() instanceof ServerPlayer sp && sp.level().getEntity(p.entity) instanceof CarEntity car
                && car.distanceToSqr(sp)<=100 && car.mayModify(sp)) {
                if(p.action==OPEN)open(sp,car);else if(p.action==OPEN_ENGINE)openEngine(sp,car);else car.action(sp,p.action,p.a,p.b);
            }
        });
        r.playToClient(Open.TYPE,Open.CODEC,(p,ctx)->{if(p.engine)AutoPropulsionAge.openEngine.accept(p.entity);else AutoPropulsionAge.openGarage.accept(p.entity);});
    }
    public static void open(ServerPlayer player,CarEntity car) {
        if(car.mayModify(player)&&car.distanceToSqr(player)<=100) PacketDistributor.sendToPlayer(player,new Open(car.getId(),false));
    }
    public static void openEngine(ServerPlayer player,CarEntity car){
        if(car.mayModify(player)&&car.distanceToSqr(player)<=100)PacketDistributor.sendToPlayer(player,new Open(car.getId(),true));
    }
}
