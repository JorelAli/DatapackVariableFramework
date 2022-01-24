# DatapackVariableFramework
A proof-of-concept of better variables for Minecraft commands

## What this is
This is a Bukkit/Spigot/Paper plugin that uses the latest (unreleased) development build of the [CommandAPI](https://github.com/JorelAli/CommandAPI) based on its upcoming 7.0.0 release which tests a proof-of-concept implementation of adding variables in Minecraft in a more intuitive manner.

## Motivation
Variables are useful. In Minecraft, the closest thing we have to storing variables and state is to use scoreboards. The problem with this is that passing the values of those scoreboards to other commands is a pain. Consider a simple case where you want to give a player some stone based on a scoreboard value. Minecraft doesn't have a way to "extract" a scoreboard value intuitively, so a simple approach to this would be to run the following:

```mcfunction
scoreboard objectives add counter dummy
scoreboard players set @p counter 20
give @p[scores={counter=1..}] diamond 1
scoreboard players remove @p counter 1
give @p[scores={counter=1..}] diamond 1
scoreboard players remove @p counter 1
give @p[scores={counter=1..}] diamond 1
scoreboard players remove @p counter 1
give @p[scores={counter=1..}] diamond 1
scoreboard players remove @p counter 1
give @p[scores={counter=1..}] diamond 1
scoreboard players remove @p counter 1
# And so on...
```

Of course, this can be condensed using a datapack and a recursive function that calls itself, for example:

```mcfunction
give @p[scores={counter=1..}] diamond 1
scoreboard players remove @p counter 1
execute as @p[scores={counter=1..}] run function mynamespace:recursivegive
```

That said, this is unnecessarily long-winded and could be much simpler...

## What this does
This uses the CommandAPI to create variables and uses the [CommandAPI's Brigadier API](https://commandapi.jorel.dev/6.4.0/brigadier.html) to add variables as valid values to all existing Minecraft commands. In short, you can do:

```mcfunction
var set mut counter = 20
give @p diamond var:counter
```
