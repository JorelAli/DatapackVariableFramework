package dev.jorel.dvf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import dev.jorel.commandapi.Brigadier;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.StringTooltip;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.CustomArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MathOperationArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.SafeSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.wrappers.MathOperation;

public class Main<T> extends JavaPlugin implements Listener {

	TreeMap<String, Variable> variables = new TreeMap<>();
	List<String[]> pathsToAddVars = new ArrayList<>();
	
	final static Set<String> EXCLUDED_COMMANDS = Set.of("var");
	final static ChatColor CONST = ChatColor.BLUE;
	final static ChatColor MUT   = ChatColor.YELLOW;
	
	@SuppressWarnings("unchecked")
	private CommandNode<T> makeVariableNode(CommandNode<T> currentNode) {
		Argument variable = new CustomArgument<>("var", inputInfo -> {
			// this isn't even actually necessary, but it's cool that this can exists here :)
			//return variables.get(inputInfo.input().substring(4)); // Remove "var:
			return null;
		}, true).replaceSuggestions(ArgumentSuggestions.stringsWithTooltips(info -> 
			variables.entrySet().stream()
				.map((Entry<String, Variable> e) -> StringTooltip.of(e.getValue().getPrefix() + e.getKey(), e.getValue().getTypeDescription()))
				.toArray(StringTooltip[]::new)
		));
		
		CommandNode<T> result = Brigadier.fromArgument(variable)
			.executes(cmdCtx -> {
				//Object[] out = Brigadier.parseArguments(cmdCtx, List.of(variable));
				
				String newCmd = cmdCtx.getInput();
				for(Entry<String, Variable> entry : variables.entrySet()) {
					newCmd = newCmd.replace("var:" + entry.getKey(), String.valueOf(entry.getValue().intValue()));
					newCmd = newCmd.replace("const:" + entry.getKey(), String.valueOf(entry.getValue().intValue()));
				}
				
				if(newCmd.contains("var:") || newCmd.contains("const:")) {
					CommandSender sender = Brigadier.getBukkitCommandSenderFromContext(cmdCtx);
					sender.sendMessage("Failed to run command, variable lookups failed:");
					sender.sendMessage("  /" + newCmd);
					return 0;
				} else {
					return Brigadier.getCommandDispatcher().execute(newCmd, cmdCtx.getSource());
				}
			})
			//result.currentNode.getRedirect();
			.build();
		
		currentNode.getChildren().forEach(result::addChild);
		return result;
	}
	
	private void traverse(String[] path, Collection<?> collection) {
		// Nope. Not dealing with this
		if(path.length > 0 && EXCLUDED_COMMANDS.contains(path[0])) {
			return;
		}
		
		@SuppressWarnings("unchecked")
		Collection<CommandNode<?>> commandNodes = (Collection<CommandNode<?>>) collection;
		for(CommandNode<?> node : commandNodes) {
			if(node instanceof ArgumentCommandNode<?, ?> argNode && argNode.getType() instanceof IntegerArgumentType) {
				String humanReadableCommand = "/" + path[0] + " " + Arrays.stream(path).skip(1).map(x -> "<" + x + ">").collect(Collectors.joining(" "));
				getLogger().info("Adding variable support for " + humanReadableCommand + " " + node.getName());
				this.pathsToAddVars.add(path);
			} else {
				List<String> newPath = new ArrayList<>();
				newPath.addAll(List.of(path));
				newPath.add(node.getName());
				traverse(newPath.toArray(new String[0]), node.getChildren());
			}
		}
	}
	
	@Override
	public void onEnable() {
		registerVarCommand();
		traverse(new String[0], Brigadier.getRootNode().getChildren());
		
		// Get the last child node in the path and add a variable node to it
		for(String[] path : this.pathsToAddVars) {
			@SuppressWarnings("unchecked")
			CommandNode<T> commandNode = Brigadier.getRootNode();
			for(String arg : path) {
				commandNode = commandNode.getChild(arg);
			}
			commandNode.addChild(makeVariableNode(commandNode));
		}
	}
	
	private void registerVarCommand() {
		new CommandAPICommand("var")
			.withArguments(new LiteralArgument("list"))
			.executes((sender, args) -> {
				sender.sendMessage(this.variables.size() + " variables declared:");
				for(Entry<String, Variable> entry : this.variables.entrySet()) {
					sender.sendMessage("  " + entry.getValue().getColor() + entry.getKey() + ChatColor.RESET + ": " + entry.getValue().intValue());
				}
			})
			.register();
		
		new CommandAPICommand("var")
			.withArguments(new LiteralArgument("set"))
			.withArguments(new MultiLiteralArgument("const", "mut")) // 0
			.withArguments(new StringArgument("variablename"))       // 1
			.withArguments(new MathOperationArgument("eq").replaceSafeSuggestions(SafeSuggestions.suggest(MathOperation.ASSIGN)))
			.withArguments(new IntegerArgument("value"))             // 3
			.executes((sender, args) -> {
				String variableName = (String) args[1];
				String constness = (String) args[0];
				int intValue = (int) args[3];
				
				if(!variableName.toLowerCase().equals(variableName)) {
					CommandAPI.fail("Variable '" + variableName + "' must be lowercase");
				}
				
				Variable variable = new Variable(intValue, constness.equals("const"));
				if(variable.isConst() && variables.containsKey(variableName)) {
					sender.sendMessage("Variable " + variableName + " is const and cannot be changed!");
				} else {
					variables.put(variableName, variable);
					sender.sendMessage("Set " + variableName + " to " + intValue);
				}
			})
			.register();
	}
	
	private record Variable(int intValue, boolean isConst) {
		public String getPrefix() {
			return isConst() ? "const:" : "var:";
		}
		
		public String getColor() {
			return String.valueOf(isConst() ? CONST : MUT);
		}
		
		public String getTypeDescription() {
			return isConst() ? "A " + CONST + "constant" + ChatColor.RESET + " value (doesn't change)" : 
				"A " + MUT + "variable" + ChatColor.RESET + " value (can be changed)";
		}
	}
}