/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.scaffold.command.tree;

import io.github.nucleuspowered.nucleus.scaffold.command.ICommandContext;

import java.util.function.Predicate;

public interface IParameterElement<R> {

    boolean optional();

    Predicate<ICommandContext<?>> condition();

    IParameterType<R> type();

}
