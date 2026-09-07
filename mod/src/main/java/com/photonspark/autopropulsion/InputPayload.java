package com.photonspark.autopropulsion;

import com.photonspark.autopropulsion.sim.VehicleInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** No position, velocity, ownership or damage values are accepted from the client. */
public record InputPayload(int entityId, float throttle, float brake, float steer, float clutch, int gear, int flags) implements CustomPacketPayload {
    public static final Type<InputPayload> TYPE = new Type<>(AutoPropulsion.id("vehicle_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InputPayload> CODEC = new StreamCodec<>() {
        @Override public InputPayload decode(RegistryFriendlyByteBuf b) {
            return new InputPayload(b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readByte(), b.readUnsignedByte());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, InputPayload p) {
            b.writeVarInt(p.entityId); b.writeFloat(p.throttle); b.writeFloat(p.brake); b.writeFloat(p.steer);
            b.writeFloat(p.clutch); b.writeByte(p.gear); b.writeByte(p.flags);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (packet, context) -> {
            var player = context.player();
            if (!VehicleInput.validPacket(packet.throttle, packet.brake, packet.steer, packet.clutch, packet.gear) || (packet.flags & ~7) != 0) return;
            if (player.level().getEntity(packet.entityId) instanceof VehicleEntity car && car.getFirstPassenger() == player) {
                car.acceptInput(player, packet);
            }
        });
    }
}
