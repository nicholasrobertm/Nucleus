/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.core.services.impl.teleport;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.nucleuspowered.nucleus.api.EventContexts;
import io.github.nucleuspowered.nucleus.api.teleport.data.NucleusTeleportHelperFilters;
import io.github.nucleuspowered.nucleus.api.teleport.data.TeleportResult;
import io.github.nucleuspowered.nucleus.api.teleport.data.TeleportScanner;
import io.github.nucleuspowered.nucleus.core.core.config.SafeTeleportConfig;
import io.github.nucleuspowered.nucleus.core.core.events.AboutToTeleportEvent;
import io.github.nucleuspowered.nucleus.core.services.INucleusServiceCollection;
import io.github.nucleuspowered.nucleus.core.services.interfaces.INucleusLocationService;
import io.github.nucleuspowered.nucleus.core.services.interfaces.IReloadableService;
import net.kyori.adventure.audience.Audience;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Cause;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.world.ServerLocation;
import org.spongepowered.api.world.WorldBorder;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.api.world.teleport.TeleportHelper;
import org.spongepowered.api.world.teleport.TeleportHelperFilter;
import org.spongepowered.api.world.teleport.TeleportHelperFilters;
import org.spongepowered.math.vector.Vector3d;

import java.util.Optional;

@Singleton
public class SafeLocationService implements INucleusLocationService, IReloadableService.Reloadable {

    private static final BorderDisableSession DUMMY = new BorderDisableSession() {};
    private SafeTeleportConfig config = new SafeTeleportConfig();

    @Inject
    public SafeLocationService(final IReloadableService reloadable) {
        reloadable.registerReloadable(this);
    }

    @Override
    public TeleportResult teleportPlayerSmart(final ServerPlayer player,
            final ServerLocation location,
            final Vector3d rotation,
            final boolean centreBlock,
            final boolean safe,
            final TeleportScanner scanner) {
        return this.teleportPlayerSmart(player, location, rotation, centreBlock, safe, scanner);
    }

    @Override
    public TeleportResult teleportPlayerSmart(final ServerPlayer player,
            final ServerLocation location,
            final boolean centreBlock,
            final boolean safe,
            final TeleportScanner scanner) {
        return this.teleportPlayer(player,
                location,
                player.getRotation(),
                centreBlock,
                scanner,
                this.getAppropriateFilter(player, safe));
    }

    @Override
    public TeleportResult teleportPlayer(final ServerPlayer player,
            final ServerLocation location,
            final Vector3d rotation,
            final boolean centreBlock,
            final TeleportScanner scanner,
            final TeleportHelperFilter filter,
            final TeleportHelperFilter... filters) {

        final Optional<ServerLocation> optionalWorldTransform = this.getSafeLocation(
                location,
                scanner,
                filter,
                filters
        );

        final Cause cause = Sponge.getGame().getServer().getCauseStackManager().getCurrentCause();
        if (optionalWorldTransform.isPresent()) {
            ServerLocation targetLocation = optionalWorldTransform.get();
            final AboutToTeleportEvent event = new AboutToTeleportEvent(
                    cause,
                    targetLocation,
                    rotation,
                    player.getUniqueId()
            );

            if (Sponge.getEventManager().post(event)) {
                event.getCancelMessage().ifPresent(x -> {
                    final Object o = cause.root();
                    if (o instanceof Audience) {
                        ((Audience) o).sendMessage(x);
                    }
                });
                return TeleportResult.FAIL_CANCELLED;
            }

            try (final CauseStackManager.StackFrame frame = Sponge.getServer().getCauseStackManager().pushCauseFrame()) {
                frame.addContext(EventContexts.BYPASS_JAILING_RESTRICTION, true);
                /*final Optional<Entity> oe = player.getVehicle();
                if (oe.isPresent()) {
                    player.setVehicle(null);
                }*/

                // Do it, tell the routine if it worked.
                if (centreBlock) {
                    targetLocation = ServerLocation.of(
                            targetLocation.getWorld(),
                            targetLocation.getBlockPosition().toDouble().add(0.5, 0.5, 0.5));
                }

                if (player.setLocationAndRotation(targetLocation, rotation)) {
                    // TODO: player.setSpectatorTarget(null);
                    return TeleportResult.SUCCESS;
                }

                // TODO? oe.ifPresent(player::setVehicle);
            }
        }

        return TeleportResult.FAIL_NO_LOCATION;
    }

    @Override
    public Optional<ServerLocation> getSafeLocation(
            final ServerLocation location,
            final TeleportScanner scanner,
            final TeleportHelperFilter filter,
            final TeleportHelperFilter... filters) {
        return scanner.scanFrom(
                location.getWorld(),
                location.getBlockPosition(),
                this.config.getHeight(),
                this.config.getWidth(),
                TeleportHelper.DEFAULT_FLOOR_CHECK_DISTANCE,
                filter,
                filters
        );
    }

    @Override
    public TeleportHelperFilter getAppropriateFilter(final ServerPlayer src, final boolean safeTeleport) {
        if (safeTeleport && !src.get(Keys.GAME_MODE).filter(x -> x == GameModes.SPECTATOR).isPresent()) {
            if (src.get(Keys.IS_FLYING).orElse(false)) {
                return TeleportHelperFilters.FLYING.get();
            } else {
                return TeleportHelperFilters.DEFAULT.get();
            }
        } else {
            return NucleusTeleportHelperFilters.NO_CHECK.get();
        }
    }

    @Override
    public void onReload(final INucleusServiceCollection serviceCollection) {
        this.config = serviceCollection.configProvider().getCoreConfig().getSafeTeleportConfig();
    }

    @Override public BorderDisableSession temporarilyDisableBorder(final boolean reset, final ServerWorld world) {
        if (reset) {
            final WorldBorder border = world.getBorder();
            return new WorldBorderReset(border);
        }

        return DUMMY;
    }

    static class WorldBorderReset implements BorderDisableSession {

        private final double x;
        private final double z;
        private final double diameter;
        private final WorldBorder border;

        WorldBorderReset(final WorldBorder border) {
            this.border = border;
            this.x = border.getCenter().getX();
            this.z = border.getCenter().getZ();
            this.diameter = border.getDiameter();
        }

        @Override
        public void close() {
            this.border.setCenter(this.x, this.z);
            this.border.setDiameter(this.diameter);
        }
    }
}
