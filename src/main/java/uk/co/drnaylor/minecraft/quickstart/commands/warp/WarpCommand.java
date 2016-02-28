/*
 * This file is part of QuickStart, licensed under the MIT License (MIT). See the LICENCE.txt file
 * at the root of this project for more details.
 */
package uk.co.drnaylor.minecraft.quickstart.commands.warp;

import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import uk.co.drnaylor.minecraft.quickstart.Util;
import uk.co.drnaylor.minecraft.quickstart.api.PluginModule;
import uk.co.drnaylor.minecraft.quickstart.argumentparsers.WarpParser;
import uk.co.drnaylor.minecraft.quickstart.internal.CommandBase;
import uk.co.drnaylor.minecraft.quickstart.internal.CommandPermissionHandler;
import uk.co.drnaylor.minecraft.quickstart.internal.annotations.Modules;
import uk.co.drnaylor.minecraft.quickstart.internal.annotations.Permissions;
import uk.co.drnaylor.minecraft.quickstart.internal.annotations.RegisterCommand;
import uk.co.drnaylor.minecraft.quickstart.internal.permissions.PermissionInformation;
import uk.co.drnaylor.minecraft.quickstart.internal.permissions.SuggestedLevel;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows a user to warp to the specified warp.
 *
 * Command Usage: /warp [warp]
 * Permission: quickstart.warp.base
 *
 * If <code>warp.separate-permissions</code> = <code>true</code> in the commands config, also requires
 * <code>quickstart.warps.[warpname]</code> permission, or the QuickStart admin permission.
 */
@Permissions(suggestedLevel = SuggestedLevel.USER)
@Modules(PluginModule.WARPS)
@RegisterCommand("warp")
public class WarpCommand extends CommandBase<Player> {
    static final String warpNameArg = Util.getMessageWithFormat("args.name.warpname");

    @Override
    protected Map<String, PermissionInformation> permissionsToRegister() {
        Map<String, PermissionInformation> m = new HashMap<>();
        m.put(CommandPermissionHandler.PERMISSIONS_PREFIX + "warps", new PermissionInformation(Util.getMessageWithFormat("permissions.warps"), SuggestedLevel.ADMIN));
        return m;
    }

    @Override
    public CommandSpec createSpec() {
        return CommandSpec.builder().executor(this)
                .children(this.createChildCommands()).arguments(
                        GenericArguments.onlyOne(GenericArguments.optionalWeak(GenericArguments.flags().flag("f", "-force").setAnchorFlags(false).buildWith(GenericArguments.none()))),
                        GenericArguments.onlyOne(new WarpParser(Text.of(warpNameArg), plugin, true))
                ).build();
    }

    @Override
    public CommandResult executeCommand(Player pl, CommandContext args) throws Exception {
        // Permission checks are done by the parser.
        WarpParser.WarpData wd = args.<WarpParser.WarpData>getOne(warpNameArg).get();

        // We have a warp data, warp them.
        pl.sendMessage(Text.of(TextColors.YELLOW, MessageFormat.format(Util.getMessageWithFormat("command.warps.start"), wd.warp)));

        // Warp them.
        if (args.getOne("f").isPresent()) { // Force the position.
            pl.setLocationAndRotation(wd.loc.getLocation(), wd.loc.getRotation());
        } else if(!pl.setLocationAndRotationSafely(wd.loc.getLocation(), wd.loc.getRotation())) { // No force, try teleport, if failed, tell them.
            pl.sendMessage(Text.of(TextColors.RED, Util.getMessageWithFormat("command.warps.nosafe")));

            // Don't add the cooldown if enabled.
            return CommandResult.empty();
        }

        return CommandResult.success();
    }
}
