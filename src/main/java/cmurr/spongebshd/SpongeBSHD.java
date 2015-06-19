/*
 * This file is part of SpongeBSHD, licensed under the MIT License (MIT).
 * 
 * Copyright (c) 2015 Christopher Murray
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package cmurr.spongebshd;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.TargetError;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.entity.player.Player;
import org.spongepowered.api.event.Subscribe;
import org.spongepowered.api.event.state.InitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.config.ConfigDir;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.command.CommandCallable;
import org.spongepowered.api.util.command.CommandResult;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.source.ConsoleSource;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Plugin(id = "spongebshd", name = "SpongeBSHD", version = "1.0")
public class SpongeBSHD {
    public static SpongeBSHD instance;
    @Inject
    public Logger log;
    @Inject
    public Game game;
    @Inject
    @ConfigDir(sharedRoot = false)
    public File configDir;
    public File configFile;
    public ConfigurationNode configRoot;
    public ConfigurationLoader<? extends ConfigurationNode> configLoader;
    // use a separate file. access to the server permission system shouldn't mean complete control of the machine
    public File allowedPlayersFile;
    public Interpreter shell;
    
    @Subscribe
    public void onInitialization(InitializationEvent event) {
        if (!game.getServer().getOnlineMode()) {
            log.error("For security reasons, the server must be in online-mode to use SpongeBSHD.");
            return;
        }
        if (instance != null) {
            log.error("SpongeBSHD already initialized!");
            return;
        }
        instance = this;
        shell = new Interpreter();
        try {
            shell.set("game", game);
            shell.set("server", game.getServer());
            for (PluginContainer container : game.getPluginManager().getPlugins()) {
                Object plugin = container.getInstance();
                if (plugin == null) {
                    log.info("Not registering null plugin object in container: " + container);
                    continue;
                }
                String[] longName = plugin.getClass().getName().split("\\.");
                log.info("Registering plugin object: " + longName[longName.length - 1]);
                shell.set(longName[longName.length - 1], plugin);
            }
            shell.eval("setAccessibility(true)");
            shell.eval("import org.spongepowered.api.*");
            shell.eval("import org.spongepowered.api.data.*");
            shell.eval("import org.spongepowered.api.data.manipulator.*");
            shell.eval("import org.spongepowered.api.data.types.*");
            shell.eval("import org.spongepowered.api.entity.*");
            shell.eval("import org.spongepowered.api.entity.living.*");
            shell.eval("import org.spongepowered.api.entity.living.animal.*");
            shell.eval("import org.spongepowered.api.entity.living.complex.*");
            shell.eval("import org.spongepowered.api.entity.living.golem.*");
            shell.eval("import org.spongepowered.api.entity.living.monster.*");
            shell.eval("import org.spongepowered.api.entity.player.*");
            shell.eval("import org.spongepowered.api.item.*");
            shell.eval("import org.spongepowered.api.item.inventory.*");
            shell.eval("import org.spongepowered.api.text.*");
            shell.eval("import org.spongepowered.api.world.*");
        } catch (EvalError err) {
            log.warn("Exception during interpreter environment initialization.", err);
        }
        loadConfig();
        registerCommands();
    }
    
    private void loadConfig() {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        allowedPlayersFile = new File(configDir, "allowedPlayers.txt");
        if (!allowedPlayersFile.exists()) {
            log.info("No allowedPlayers.txt found; creating one.");
            try {
                allowedPlayersFile.createNewFile();
            } catch (IOException exc) {
                exc.printStackTrace();
            }
        }
    }
    
    private void registerCommands() {
        game.getCommandDispatcher().register(this, new CommandCallable(){
            public final Text desc = Texts.of("Executes BeanShell code on the server.");
            public final Text help = Texts.of("Executes BeanShell code on the server. Only usable by players whose UUID's are in allowedPlayers.txt");
            public final Text usage = Texts.of("<code>");
            
            public Optional<CommandResult> process(CommandSource source, String arg) {
                if (!testPermission(source)) {
                    source.sendMessage(Texts.of("You fail the permission test."));
                    return Optional.of(CommandResult.empty());
                }
                try {
                    shell.set("me", source);
                } catch (EvalError err) {
                    err.printStackTrace();
                }
                try {
                    source.sendMessage(Texts.of(TextColors.BLUE, "Result: ", TextColors.RESET, String.valueOf(shell.eval(arg))));
                    return Optional.of(CommandResult.success());
                } catch (TargetError err) {
                    source.sendMessage(Texts.of(err.getTarget().getMessage()));
                } catch (EvalError err) {
                    source.sendMessage(Texts.of(err.getMessage()));
                } catch (Throwable thr) {
                    StringWriter writer = new StringWriter();
                    thr.printStackTrace(new PrintWriter(writer));
                    source.sendMessage(Texts.of(writer.toString()));
                }
                return Optional.of(CommandResult.empty());
            }
            
            public boolean testPermission(CommandSource source) {
                return hasPermission(source);
            }
            
            public List<String> getSuggestions(CommandSource source, String arg) {
                return Collections.emptyList();
            }
            
            public Optional<Text> getShortDescription(CommandSource source) { return Optional.of(desc); }
            public Optional<Text> getHelp(CommandSource source) { return Optional.of(help); }
            public Text getUsage(CommandSource source) { return usage; }
        }, "bshd");
//        game.getCommandDispatcher().register(this, new CommandCallable(){
//            public final Text desc = Texts.of("View or change your per-player SpongeBSHD options.");
//            public final Text help = Texts.of("View or change your per-player SpongeBSHD options. Only usable by players whose UUID's are in allowedPlayers.txt");
//            public final Text usage = Texts.of("<list|bookin|bookout>");
//            
//            public Optional<CommandResult> process(CommandSource source, String arg) {
//                if (!testPermission(source)) {
//                    source.sendMessage(Texts.of("You fail the permission test."));
//                    return Optional.of(CommandResult.empty());
//                }
//                
//                return Optional.of(CommandResult.empty());
//            }
//            
//            public boolean testPermission(CommandSource source) {
//                return hasPermission(source);
//            }
//            
//            public List<String> getSuggestions(CommandSource source, String arg) {
//                return Collections.emptyList();
//            }
//            
//            public Optional<Text> getShortDescription(CommandSource source) { return Optional.of(desc); }
//            public Optional<Text> getHelp(CommandSource source) { return Optional.of(help); }
//            public Text getUsage(CommandSource source) { return usage; }
//        }, "bshdopt");
    }
    
    public boolean hasPermission(CommandSource source) {
        if (source instanceof ConsoleSource) {
            return true;
        }
        if (source instanceof Player) {
            Player player = (Player) source;
            UUID playerId = player.getUniqueId();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(allowedPlayersFile));
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (playerId.equals(UUID.fromString(line))) {
                        reader.close();
                        return true;
                    }
                }
                reader.close();
                return false;
            } catch (Throwable thr) {
                thr.printStackTrace();
            }
        }
        // we don't want command blocks or RCON users dynamically running code
        return false;
    }
}
