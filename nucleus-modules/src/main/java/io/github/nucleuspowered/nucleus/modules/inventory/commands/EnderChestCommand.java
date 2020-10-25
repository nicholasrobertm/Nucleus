/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.inventory.commands;

import com.google.inject.Inject;
import io.github.nucleuspowered.nucleus.modules.inventory.InventoryPermissions;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandContext;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandExecutor;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandResult;
import io.github.nucleuspowered.nucleus.scaffold.command.NucleusParameters;
import io.github.nucleuspowered.nucleus.scaffold.command.annotation.Command;
import io.github.nucleuspowered.nucleus.scaffold.command.annotation.CommandModifier;
import io.github.nucleuspowered.nucleus.scaffold.command.annotation.EssentialsEquivalent;
import io.github.nucleuspowered.nucleus.scaffold.command.modifier.CommandModifiers;
import io.github.nucleuspowered.nucleus.services.INucleusServiceCollection;
import io.github.nucleuspowered.nucleus.services.interfaces.IPermissionService;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.item.inventory.ContainerTypes;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.menu.InventoryMenu;
import org.spongepowered.api.item.inventory.type.ViewableInventory;

import java.util.UUID;

;

@Command(
        aliases = {"enderchest", "ec", "echest"},
        basePermission = InventoryPermissions.BASE_ENDERCHEST,
        commandDescriptionKey = "enderchest",
        modifiers = {
                @CommandModifier(value = CommandModifiers.HAS_COOLDOWN, exemptPermission = InventoryPermissions.EXEMPT_COOLDOWN_ENDERCHEST),
                @CommandModifier(value = CommandModifiers.HAS_WARMUP, exemptPermission = InventoryPermissions.EXEMPT_WARMUP_ENDERCHEST),
                @CommandModifier(value = CommandModifiers.HAS_COST, exemptPermission = InventoryPermissions.EXEMPT_COST_ENDERCHEST)
        },
        associatedPermissions = {
                InventoryPermissions.OTHERS_ENDERCHEST,
                InventoryPermissions.ENDERCHEST_EXEMPT_INSPECT,
                InventoryPermissions.ENDERCHEST_EXEMPT_MODIFY,
                InventoryPermissions.ENDERCHEST_MODIFY,
                InventoryPermissions.ENDERCHEST_OFFLINE
        }
)
@EssentialsEquivalent({"enderchest", "echest", "endersee", "ec"})
public class EnderChestCommand implements ICommandExecutor {

    private final Parameter.Value<ServerPlayer> onlinePlayerParameter;
    private final Parameter.Value<User> offlinePlayerParameter;

    @Inject
    public EnderChestCommand(final IPermissionService permissionService) {
        this.onlinePlayerParameter = Parameter.player()
                .setRequirements(cause -> permissionService.hasPermission(cause, InventoryPermissions.OTHERS_ENDERCHEST))
                .build();
        this.offlinePlayerParameter = Parameter.user()
                .setRequirements(cause -> permissionService.hasPermission(cause, InventoryPermissions.OTHERS_ENDERCHEST))
                .build();
    }

    @Override
    public Parameter[] parameters(final INucleusServiceCollection serviceCollection) {
        return new Parameter[] {
                Parameter.firstOfBuilder(this.onlinePlayerParameter).or(this.offlinePlayerParameter).optional().build()
        };
    }

    @Override public ICommandResult execute(final ICommandContext context) throws CommandException {
        final ServerPlayer currentPlayer = context.requirePlayer();
        final User target = context.getUserFromArgs(NucleusParameters.Keys.PLAYER);

        if (!context.is(target)) {
            if (context.testPermissionFor(target, InventoryPermissions.ENDERCHEST_EXEMPT_INSPECT)) {
                return context.errorResult("command.enderchest.targetexempt", target.getName());
            }

            final Inventory ec = target.getEnderChestInventory();
            if (!context.testPermission(InventoryPermissions.ENDERCHEST_MODIFY)
                    || context.testPermissionFor(target, InventoryPermissions.ENDERCHEST_EXEMPT_MODIFY)) {
                final UUID uuid = UUID.randomUUID();
                final InventoryMenu menu =
                        ViewableInventory.builder().type(ContainerTypes.GENERIC_9x5).slots(ec.slots(), 0).completeStructure()
                                .identity(uuid).build().asMenu();
                menu.setReadOnly(true);
                return menu.open(currentPlayer)
                        .map(x -> context.successResult())
                        .orElseGet(() -> context.errorResult("command.invsee.failed"));
            } else {
                return currentPlayer.openInventory(ec)
                        .map(x -> context.successResult())
                        .orElseGet(() -> context.errorResult("command.invsee.failed"));
            }
        } else {
            return context.requirePlayer().openInventory(
                        context.getCommandSourceAsPlayerUnchecked().getEnderChestInventory())
                    .map(x -> context.successResult())
                    .orElseGet(() -> context.errorResult("command.invsee.failed"));
        }

    }

}