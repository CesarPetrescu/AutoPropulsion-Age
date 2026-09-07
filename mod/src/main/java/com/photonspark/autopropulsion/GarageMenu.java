package com.photonspark.autopropulsion;

import com.photonspark.autopropulsion.sim.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Server-owned telemetry and workshop actions. Session slot names do not change under button indices. */
public final class GarageMenu extends AbstractContainerMenu {
    public static final int SAMPLES = 32, DATA_SIZE = 16 + SAMPLES;
    public final int vehicleId;
    public final VehicleEntity car;
    public final List<String> sessionPaths;
    public final boolean dyno;
    public final ContainerData data;
    public GarageMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, extra.readVarInt(), extra.readBoolean(), readPaths(extra));
    }
    private static List<String> readPaths(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt(); if (count < 0 || count > 128) throw new IllegalArgumentException("Invalid garage slot count");
        List<String> values = new ArrayList<>(); for (int i = 0; i < count; i++) values.add(buffer.readUtf(160)); return List.copyOf(values);
    }
    private GarageMenu(int id, Inventory inventory, int carId, boolean dyno, List<String> paths) {
        super(AutoPropulsion.GARAGE_MENU.get(), id);
        vehicleId = carId; this.dyno = dyno; sessionPaths = paths;
        car = inventory.player.level().getEntity(carId) instanceof VehicleEntity vehicle ? vehicle : null;
        data = new SimpleContainerData(DATA_SIZE); addDataSlots(data);
    }
    public GarageMenu(int id, Inventory inventory, VehicleEntity car, List<String> paths, boolean dyno) {
        super(AutoPropulsion.GARAGE_MENU.get(), id);
        vehicleId = car.getId(); this.car = car; sessionPaths = List.copyOf(paths); this.dyno = dyno;
        data = new ContainerData() {
            @Override public int getCount() { return DATA_SIZE; }
            @Override public void set(int index, int value) { /* Only server simulation owns these values. */ }
            @Override public int get(int index) {
                var s = car.simulation().snapshot(); var spec = car.simulation().spec();
                if (index >= 16 && index < DATA_SIZE) {
                    double rpm = spec.idleRpm() + (spec.redlineRpm() - spec.idleRpm()) * (index - 16) / (SAMPLES - 1);
                    return (int)Math.round(EngineModel.torqueNm(spec, rpm, EngineModel.targetBoost(spec, rpm, 1)) * 10);
                }
                return switch (index) {
                    case 0 -> (int)s.rpm(); case 1 -> (int)(s.speed() * 36); case 2 -> (int)(s.fuel() * 100);
                    case 3 -> (int)((s.coolantK() - 273.15) * 10); case 4 -> (int)(s.health() * 100); case 5 -> (int)(s.boost() * 1000);
                    case 6 -> s.gear(); case 7 -> s.running() ? 1 : 0; case 8 -> car.hoodOpen() ? 1 : 0;
                    case 9 -> (int)spec.redlineRpm(); case 10 -> (int)spec.structuralTorqueNm();
                    case 11 -> car.assembly().missingRequired().size(); case 12 -> car.assembly().installed().size();
                    case 13 -> (int)s.odometer(); case 14 -> (int)((s.oilK() - 273.15) * 10); case 15 -> (int)spec.idleRpm(); default -> 0;
                };
            }
        };
        addDataSlots(data);
    }
    @Override public boolean stillValid(Player player) { return player.level().isClientSide || car != null && car.isAlive() && car.canModify(player) && player.distanceToSqr(car) <= 64; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level().isClientSide || !stillValid(player) || car == null) return false;
        if (button == 0) { car.toggleEngine(player); return true; }
        if (button == 1 || button == 2) { car.adjustRedline(player, button == 1 ? -200 : 200); return true; }
        if (button >= 100 && button < 100 + sessionPaths.size()) return car.removePart(player, sessionPaths.get(button - 100));
        return false;
    }
}
