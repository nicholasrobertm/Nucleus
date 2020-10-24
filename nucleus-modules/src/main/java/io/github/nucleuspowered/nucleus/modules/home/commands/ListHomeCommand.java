/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.home.commands;

import io.github.nucleuspowered.nucleus.Util;
import io.github.nucleuspowered.nucleus.api.module.home.data.Home;
import io.github.nucleuspowered.nucleus.api.util.data.NamedLocation;
import io.github.nucleuspowered.nucleus.modules.home.HomePermissions;
import io.github.nucleuspowered.nucleus.modules.home.config.HomeConfig;
import io.github.nucleuspowered.nucleus.modules.home.services.HomeService;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandContext;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandExecutor;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandResult;
import io.github.nucleuspowered.nucleus.scaffold.command.annotation.Command;
import io.github.nucleuspowered.nucleus.services.INucleusServiceCollection;
import io.github.nucleuspowered.nucleus.services.interfaces.IMessageProviderService;
import io.github.nucleuspowered.nucleus.services.interfaces.IPermissionService;
import io.github.nucleuspowered.nucleus.services.interfaces.IReloadableService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.world.ServerLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Command(
        aliases = {"list", "#listhomes", "#homes"},
        basePermission = HomePermissions.BASE_HOME_LIST,
        commandDescriptionKey = "home.list",
        parentCommand = HomeCommand.class,
        async = true,
        associatedPermissions = HomePermissions.OTHERS_LIST_HOME
)
public class ListHomeCommand implements ICommandExecutor, IReloadableService.Reloadable {

    private boolean isOnlySameDimension = false;

    @Override
    public Parameter[] parameters(final INucleusServiceCollection serviceCollection) {
        return new Parameter[] {
                serviceCollection.commandElementSupplier().createOnlyOtherUserPermissionElement(HomePermissions.OTHERS_LIST_HOME)
        };
    }

    @Override public ICommandResult execute(final ICommandContext context) throws CommandException {
        final User user = context.getUserFromArgs();
        final Component header;

        final boolean other = !context.is(user);
        if (other && (context.isConsoleAndBypass() || context.testPermissionFor(user, HomePermissions.HOME_OTHER_EXEMPT_TARGET))) {
            return context.errorResult("command.listhome.exempt");
        }

        final List<Home> msw = context.getServiceCollection().getServiceUnchecked(HomeService.class).getHomes(user);
        if (msw.isEmpty()) {
            return context.errorResult("command.home.nohomes");
        }

        final Audience audience = context.getAudience();
        final IMessageProviderService messageProviderService = context.getServiceCollection().messageProvider();
        if (other) {
            header = messageProviderService.getMessageFor(audience, "home.title.name", user.getName());
        } else {
            header = messageProviderService.getMessageFor(audience, "home.title.normal");
        }

        final IPermissionService permissionService = context.getServiceCollection().permissionService();
        final List<Component> lt = msw.stream().sorted(Comparator.comparing(NamedLocation::getName)).map(x -> {
            final Optional<ServerLocation> olw = x.getLocation();
            if (!olw.isPresent()) {
                return Component.text().append(
                                Component.text()
                                        .content(x.getName())
                                        .color(NamedTextColor.RED)
                                        .hoverEvent(HoverEvent.showText(
                                                messageProviderService.getMessageFor(audience, "home.warphoverinvalid", x.getName())))
                                        .build())
                        .build();
            } else {
                final ServerLocation lw = olw.get();
                final Component textMessage = messageProviderService.getMessageFor(audience, "home.location",
                                                 lw.getWorldKey().asString(), lw.getBlockX(), lw.getBlockY(), lw.getBlockZ());

                final Optional<ServerPlayer> optional = context.getAsPlayer();
                if (this.isOnlySameDimension && optional.isPresent() && !other) {
                    final ServerPlayer player = optional.get();
                    if (!lw.getWorldKey().equals(player.getWorld().getKey())) {
                        if (!context.isConsoleAndBypass() && !permissionService.hasPermission(user, HomePermissions.HOME_EXEMPT_SAMEDIMENSION)) {
                            return Component.text()
                                       .append(Component.text().content(x.getName())
                                                   .color(NamedTextColor.LIGHT_PURPLE)
                                                   .hoverEvent(HoverEvent.showText(
                                                           messageProviderService.getMessageFor(audience, "home.warphoverotherdimension", x.getName())))
                                                   .build())
                                       .append(textMessage)
                                       .build();
                        }
                    }
                }

                return Component.text()
                           .append(
                                Component.text().content(x.getName())
                                    .color(NamedTextColor.GREEN).style(Style.style(TextDecoration.UNDERLINED))
                                    .hoverEvent(HoverEvent.showText(messageProviderService.getMessageFor(audience, "home.warphover", x.getName())))
                                    .clickEvent(ClickEvent.runCommand(other ? "/nucleus:home " + user.getName() + " " + x.getName()
                                                                          : "/nucleus:home " + x.getName()))
                                    .build())
                           .append(textMessage)
                           .build();
            }
        }).collect(Collectors.toList());

        final PaginationList.Builder pb =
            Util.getPaginationBuilder(audience).title(header)
                    .padding(Component.text("-", NamedTextColor.GREEN)).contents(lt);

        pb.sendTo(audience);
        return context.successResult();
    }

    @Override
    public void onReload(final INucleusServiceCollection serviceCollection) {
        final HomeConfig hc = serviceCollection.configProvider().getModuleConfig(HomeConfig.class);
        this.isOnlySameDimension = hc.isOnlySameDimension();
    }
}
