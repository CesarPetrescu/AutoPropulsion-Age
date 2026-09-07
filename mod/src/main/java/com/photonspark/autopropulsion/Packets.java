package com.photonspark.autopropulsion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.photonspark.autopropulsion.sim.VehicleInput;

/** Fixed-size C2S payloads, finite checks, owner/driver checks and per-entity rate limits. */
public final class Packets {
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String id){return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AutoPropulsion.ID,id));}
    public record Input(int entity,float throttle,float brake,float steer,int flags) implements CustomPacketPayload {
        public static final Type<Input> TYPE=Packets.type("input");
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=new StreamCodec<>(){
            public Input decode(RegistryFriendlyByteBuf b){return new Input(b.readVarInt(),b.readFloat(),b.readFloat(),b.readFloat(),b.readUnsignedByte());}
            public void encode(RegistryFriendlyByteBuf b,Input v){b.writeVarInt(v.entity);b.writeFloat(v.throttle);b.writeFloat(v.brake);b.writeFloat(v.steer);b.writeByte(v.flags);}
        };
        @Override public Type<Input> type(){return TYPE;}
    }
    public record Action(int entity,int action,float value) implements CustomPacketPayload {
        public static final Type<Action> TYPE=Packets.type("action");
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=new StreamCodec<>(){
            public Action decode(RegistryFriendlyByteBuf b){return new Action(b.readVarInt(),b.readUnsignedByte(),b.readFloat());}
            public void encode(RegistryFriendlyByteBuf b,Action v){b.writeVarInt(v.entity);b.writeByte(v.action);b.writeFloat(v.value);}
        };
        @Override public Type<Action> type(){return TYPE;}
    }
    public record Garage(int entity,String slots,String curve,String fault,float boost) implements CustomPacketPayload {
        public static final Type<Garage> TYPE=Packets.type("garage");
        public static final StreamCodec<RegistryFriendlyByteBuf,Garage> CODEC=new StreamCodec<>(){
            public Garage decode(RegistryFriendlyByteBuf b){return new Garage(b.readVarInt(),b.readUtf(4096),b.readUtf(4096),b.readUtf(128),b.readFloat());}
            public void encode(RegistryFriendlyByteBuf b,Garage v){b.writeVarInt(v.entity);b.writeUtf(v.slots,4096);b.writeUtf(v.curve,4096);b.writeUtf(v.fault,128);b.writeFloat(v.boost);}
        };
        @Override public Type<Garage> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent e) {
        var r=e.registrar("1");
        r.playToServer(Input.TYPE,Input.CODEC,(p,c)->{
            if(!(c.player().level().getEntity(p.entity) instanceof VehicleEntity car))return;
            if(!Float.isFinite(p.throttle)||!Float.isFinite(p.brake)||!Float.isFinite(p.steer)||(p.flags&~3)!=0)return;
            car.receiveInput(c.player(),new VehicleInput(p.throttle,p.brake,p.steer,(p.flags&1)!=0,(p.flags&2)!=0));
        });
        r.playToServer(Action.TYPE,Action.CODEC,(p,c)->{if(c.player().level().getEntity(p.entity) instanceof VehicleEntity car)car.action(c.player(),p.action,p.value);});
        r.playToClient(Garage.TYPE,Garage.CODEC,(p,c)->com.photonspark.autopropulsion.client.ClientEvents.openGarage(p));
    }
    private Packets(){}
}
