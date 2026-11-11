package com.dragonempire.admin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AdminPanel extends JavaPlugin implements Listener, TabExecutor {
    private static final String MENU_TITLE = ChatColor.DARK_GRAY + "Dragon Empire | Admin Panel";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("adminpanel") != null) {
            getCommand("adminpanel").setExecutor(this);
            getCommand("adminpanel").setTabCompleter(this);
        }
        getLogger().info("Dragon Empire Admin Panel enabled!");
    }

    private boolean validName(String name) {
        return name.matches("[a-zA-Z0-9_-]+");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dragonempire.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) openPanel(player);
            else sender.sendMessage("Use: /ap create|add|run|delete|list");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                if (args.length != 2 || !validName(args[1])) {
                    sender.sendMessage("Usage: /ap create <name>");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                String path = "chains." + name;
                if (getConfig().contains(path)) {
                    sender.sendMessage(ChatColor.RED + "That chain already exists.");
                    return true;
                }
                getConfig().set(path, new ArrayList<String>());
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Created chain: " + name);
            }
            case "add" -> {
                if (args.length < 3 || !validName(args[1])) {
                    sender.sendMessage("Usage: /ap add <name> <command>");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                String path = "chains." + name;
                if (!getConfig().contains(path)) {
                    sender.sendMessage(ChatColor.RED + "Chain not found.");
                    return true;
                }
                String cmd = String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        .replaceFirst("^/", "").trim();
                if (cmd.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Command cannot be empty.");
                    return true;
                }
                List<String> commands = getConfig().getStringList(path);
                commands.add(cmd);
                getConfig().set(path, commands);
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Added command to " + name);
            }
            case "run" -> {
                if (args.length != 2) sender.sendMessage("Usage: /ap run <name>");
                else runChain(sender, args[1].toLowerCase(Locale.ROOT));
            }
            case "delete" -> {
                if (args.length != 2) {
                    sender.sendMessage("Usage: /ap delete <name>");
                    return true;
                }
                String path = "chains." + args[1].toLowerCase(Locale.ROOT);
                if (!getConfig().contains(path)) {
                    sender.sendMessage(ChatColor.RED + "Chain not found.");
                    return true;
                }
                getConfig().set(path, null);
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Chain deleted.");
            }
            case "list" -> {
                ConfigurationSection section = getConfig().getConfigurationSection("chains");
                if (section == null || section.getKeys(false).isEmpty()) {
                    sender.sendMessage("No command chains saved.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "Saved chains:");
                    for (String name : section.getKeys(false)) sender.sendMessage(ChatColor.YELLOW + "- " + name);
                }
            }
            default -> sender.sendMessage("Commands: /ap, /ap create, /ap add, /ap run, /ap delete, /ap list");
        }
        return true;
    }

    private void runChain(CommandSender sender, String name) {
        String path = "chains." + name;
        if (!getConfig().contains(path)) {
            sender.sendMessage(ChatColor.RED + "Chain not found.");
            return;
        }
        List<String> commands = getConfig().getStringList(path);
        if (commands.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "This chain has no commands.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Running chain: " + name);
        for (String cmd : commands) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        sender.sendMessage(ChatColor.GREEN + "Chain completed.");
    }

    private void openPanel(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, MENU_TITLE);
        ConfigurationSection section = getConfig().getConfigurationSection("chains");
        if (section != null) {
            int slot = 10;
            for (String name : section.getKeys(false)) {
                if (slot > 43) break;
                List<String> commands = getConfig().getStringList("chains." + name);
                ItemStack item = new ItemStack(Material.BOOK);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.GOLD + name);
                meta.setLore(List.of(
                        ChatColor.GRAY + "Commands: " + commands.size(),
                        ChatColor.GREEN + "Left-click: Run chain",
                        ChatColor.YELLOW + "Right-click: View commands"
                ));
                item.setItemMeta(meta);
                inventory.setItem(slot++, item);
            }
        }
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "How to use");
        infoMeta.setLore(List.of(
                ChatColor.GRAY + "/ap create <name>",
                ChatColor.GRAY + "/ap add <name> <command>",
                ChatColor.GRAY + "/ap delete <name>",
                ChatColor.GRAY + "/ap list"
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(49, info);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("dragonempire.admin")) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.BOOK || !clicked.hasItemMeta()) return;
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (name == null || !getConfig().contains("chains." + name)) return;
        if (event.isLeftClick()) {
            player.closeInventory();
            runChain(player, name);
        } else if (event.isRightClick()) {
            List<String> commands = getConfig().getStringList("chains." + name);
            player.sendMessage(ChatColor.GOLD + "Commands in " + name + ":");
            for (int i = 0; i < commands.size(); i++) {
                player.sendMessage(ChatColor.YELLOW.toString() + (i + 1) + ". " + commands.get(i));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("create", "add", "run", "delete", "list");
        if (args.length == 2 && Arrays.asList("run", "delete", "add").contains(args[0].toLowerCase(Locale.ROOT))) {
            ConfigurationSection section = getConfig().getConfigurationSection("chains");
            if (section != null) return new ArrayList<>(section.getKeys(false));
        }
        return List.of();
    }
}
