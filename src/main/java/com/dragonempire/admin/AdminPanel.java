package com.dragonempire.admin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class AdminPanel extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final Map<UUID, String> chatActions = new HashMap<>();
    private final Map<UUID, String> editingChain = new HashMap<>();
    private final Map<UUID, Integer> listPages = new HashMap<>();
    private static final String PREFIX = ChatColor.DARK_PURPLE + "DES " + ChatColor.GRAY + "» ";
    private static final String MAIN = ChatColor.DARK_GRAY + "✦ DRAGON EMPIRE | CONTROL";
    private static final String CHAINS = ChatColor.DARK_GRAY + "✦ PERSONAL CHAINS";
    private static final String EDITOR = ChatColor.DARK_GRAY + "✦ CHAIN BUILDER";

    private static final class Step {
        String command; long delay;
        Step(String command, long delay) { this.command = command; this.delay = delay; }
    }
    private static final class Chain {
        String name; List<Step> steps = new ArrayList<>();
        Chain(String name) { this.name = name; }
    }
    private static final class Profile {
        LinkedHashMap<String, Chain> chains = new LinkedHashMap<>();
        Set<String> favourites = new HashSet<>();
    }

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("adminpanel")).setExecutor(this);
        Objects.requireNonNull(getCommand("adminpanel")).setTabCompleter(this);
        getLogger().info("Dragon Empire Admin Panel enabled.");
    }
    @Override public void onDisable() { for (UUID id : profiles.keySet()) saveProfile(id); }

    private boolean allowed(CommandSender sender) {
        if (sender.hasPermission("dragonempire.admin")) return true;
        sender.sendMessage(PREFIX + ChatColor.RED + "Admin access required.");
        return false;
    }
    private Profile profile(UUID id) { return profiles.computeIfAbsent(id, this::loadProfile); }
    private File profileFile(UUID id) { return new File(new File(getDataFolder(), "profiles"), id + ".yml"); }
    private Profile loadProfile(UUID id) {
        Profile p = new Profile(); File f = profileFile(id);
        if (!f.exists()) return p;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String key : y.getConfigurationSection("chains") == null ? Collections.<String>emptySet() : y.getConfigurationSection("chains").getKeys(false)) {
            Chain c = new Chain(key);
            for (Map<?, ?> row : y.getMapList("chains." + key + ".steps")) {
                Object cmd = row.get("command"), delay = row.get("delay");
                if (cmd != null) c.steps.add(new Step(String.valueOf(cmd), delay instanceof Number ? ((Number) delay).longValue() : 0));
            }
            p.chains.put(key, c);
        }
        p.favourites.addAll(y.getStringList("favourites"));
        return p;
    }
    private void saveProfile(UUID id) {
        Profile p = profiles.get(id); if (p == null) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Chain c : p.chains.values()) {
            List<Map<String,Object>> rows = new ArrayList<>();
            for (Step s : c.steps) { Map<String,Object> row = new LinkedHashMap<>(); row.put("command", s.command); row.put("delay", s.delay); rows.add(row); }
            y.set("chains." + c.name + ".steps", rows);
        }
        y.set("favourites", new ArrayList<>(p.favourites));
        try { File f = profileFile(id); f.getParentFile().mkdirs(); y.save(f); }
        catch (IOException ex) { getLogger().severe("Could not save profile " + id + ": " + ex.getMessage()); }
    }
    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); stack.setItemMeta(meta); return stack;
    }
    private void fill(Inventory inv) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i=0;i<inv.getSize();i++) if (inv.getItem(i)==null) inv.setItem(i,pane);
    }
    private void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null,54,MAIN);
        inv.setItem(10,item(Material.NETHER_STAR,ChatColor.LIGHT_PURPLE+"ADMIN ABUSE","",ChatColor.GRAY+"Abilities, chaos and server-wide fun",ChatColor.YELLOW+"Click to open"));
        inv.setItem(12,item(Material.WRITABLE_BOOK,ChatColor.AQUA+"COMMAND CHAINS","",ChatColor.GRAY+"Your personal saved sequences",ChatColor.YELLOW+"Click to open"));
        inv.setItem(14,item(Material.COMPARATOR,ChatColor.GOLD+"CHAIN BUILDER","",ChatColor.GRAY+"Create and edit multi-step sequences"));
        inv.setItem(16,item(Material.PLAYER_HEAD,ChatColor.GREEN+"PLAYER TOOLS","",ChatColor.GRAY+"Teleport and inspect players"));
        inv.setItem(31,item(Material.CHEST,ChatColor.WHITE+"MY PROFILE","",ChatColor.GRAY+"Your personal panel data is saved by UUID"));
        inv.setItem(49,item(Material.BARRIER,ChatColor.RED+"CLOSE")); fill(inv); p.openInventory(inv);
    }
    private void openAbuse(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,ChatColor.DARK_PURPLE+"✦ ADMIN ABUSE");
        inv.setItem(10,item(Material.LIGHTNING_ROD,ChatColor.YELLOW+"THUNDER CALL","",ChatColor.GRAY+"Strike your targeted location"));
        inv.setItem(12,item(Material.FIREWORK_ROCKET,ChatColor.LIGHT_PURPLE+"LAUNCH","",ChatColor.GRAY+"Launch yourself upward"));
        inv.setItem(14,item(Material.BLAZE_POWDER,ChatColor.RED+"INFERNO","",ChatColor.GRAY+"Ignite a dramatic particle burst"));
        inv.setItem(16,item(Material.ENDER_PEARL,ChatColor.AQUA+"RANDOM TP","",ChatColor.GRAY+"Teleport to a safe nearby spot"));
        inv.setItem(31,item(Material.ARROW,ChatColor.WHITE+"BACK")); fill(inv); p.openInventory(inv);
    }
    private void openChains(Player p,int page) {
        Profile pr=profile(p.getUniqueId()); List<Chain> all=new ArrayList<>(pr.chains.values());
        int pages=Math.max(1,(all.size()+44)/45); page=Math.max(0,Math.min(page,pages-1)); listPages.put(p.getUniqueId(),page);
        Inventory inv=Bukkit.createInventory(null,54,CHAINS+" "+(page+1)+"/"+pages);
        int start=page*45;
        for(int i=start;i<Math.min(start+45,all.size());i++) {
            Chain c=all.get(i); int slot=i-start;
            inv.setItem(slot,item(Material.WRITABLE_BOOK,ChatColor.AQUA+c.name,"",ChatColor.GRAY+"Steps: "+c.steps.size(),ChatColor.GREEN+"Left-click: Run",ChatColor.YELLOW+"Right-click: Edit",ChatColor.RED+"Shift-right: Delete"));
        }
        inv.setItem(45,item(Material.ARROW,ChatColor.WHITE+"PREVIOUS"));
        inv.setItem(48,item(Material.BARRIER,ChatColor.RED+"BACK"));
        inv.setItem(49,item(Material.EMERALD,ChatColor.GREEN+"CREATE CHAIN","",ChatColor.GRAY+"Start a new personal chain"));
        inv.setItem(53,item(Material.ARROW,ChatColor.WHITE+"NEXT")); fill(inv); p.openInventory(inv);
    }
    private void openEditor(Player p,String name) {
        editingChain.put(p.getUniqueId(), name);
        Chain c=profile(p.getUniqueId()).chains.get(name); if(c==null){openChains(p,0);return;}
        Inventory inv=Bukkit.createInventory(null,54,EDITOR+" | "+name);
        for(int i=0;i<Math.min(45,c.steps.size());i++){
            Step s=c.steps.get(i);
            inv.setItem(i,item(Material.COMMAND_BLOCK,ChatColor.YELLOW+"STEP "+(i+1),"",ChatColor.GRAY+s.command,ChatColor.DARK_GRAY+"Delay after: "+s.delay+" ticks",ChatColor.RED+"Right-click: remove"));
        }
        inv.setItem(45,item(Material.ARROW,ChatColor.WHITE+"BACK"));
        inv.setItem(47,item(Material.ANVIL,ChatColor.GREEN+"ADD COMMAND","",ChatColor.GRAY+"Type a command in chat"));
        inv.setItem(49,item(Material.CLOCK,ChatColor.AQUA+"SET NEXT DELAY","",ChatColor.GRAY+"Set delay for the next added step"));
        inv.setItem(51,item(Material.LIME_DYE,ChatColor.GREEN+"RUN CHAIN"));
        inv.setItem(53,item(Material.NAME_TAG,ChatColor.GOLD+"RENAME CHAIN"));
        fill(inv); p.openInventory(inv);
    }
    private void ask(Player p,String action,String prompt) {
        chatActions.put(p.getUniqueId(),action); p.closeInventory();
        p.sendMessage(PREFIX+ChatColor.YELLOW+prompt);
        p.sendMessage(PREFIX+ChatColor.GRAY+"Type CANCEL to abort.");
    }
    private String clean(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","");}
    private void execute(Player p,Chain c) {
        if(c.steps.isEmpty()){p.sendMessage(PREFIX+ChatColor.RED+"This chain is empty.");return;}
        p.sendMessage(PREFIX+ChatColor.GREEN+"Executing "+c.name+" ("+c.steps.size()+" steps).");
        long at=0;
        for(Step step:c.steps){ at+=Math.max(0,step.delay); final String cmd=step.command;
            new BukkitRunnable(){public void run(){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),cmd);}}.runTaskLater(this,at);
        }
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        UUID id=e.getPlayer().getUniqueId(); String action=chatActions.remove(id); if(action==null)return;
        e.setCancelled(true); String message=e.getMessage();
        Bukkit.getScheduler().runTask(this,()->{
            Player p=Bukkit.getPlayer(id); if(p==null)return;
            if(message.equalsIgnoreCase("cancel")){p.sendMessage(PREFIX+ChatColor.RED+"Cancelled.");return;}
            if(action.equals("newchain")){String n=clean(message);if(n.isEmpty()){p.sendMessage(PREFIX+ChatColor.RED+"Invalid name.");return;}
                Profile pr=profile(id);if(pr.chains.containsKey(n)){p.sendMessage(PREFIX+ChatColor.RED+"That name already exists.");return;}
                pr.chains.put(n,new Chain(n));saveProfile(id);openEditor(p,n);
            } else if(action.startsWith("add:")){String n=action.substring(4);String cmd=message.replaceFirst("^/","").trim();
                if(cmd.isEmpty()||cmd.contains("\n")){p.sendMessage(PREFIX+ChatColor.RED+"Invalid command.");return;}
                Chain c=profile(id).chains.get(n);if(c==null)return;long delay=delayNext.getOrDefault(id,0L);delayNext.remove(id);c.steps.add(new Step(cmd,delay));saveProfile(id);openEditor(p,n);
            } else if(action.startsWith("delay:")){String n=action.substring(6);try{long ticks=Math.max(0,Math.min(72000,Long.parseLong(message)));p.sendMessage(PREFIX+ChatColor.GREEN+"Next step delay set to "+ticks+" ticks.");delayNext.put(id,ticks);openEditor(p,n);}catch(NumberFormatException ex){p.sendMessage(PREFIX+ChatColor.RED+"Enter a number of ticks.");}}
            else if(action.startsWith("rename:")){String old=action.substring(7),n=clean(message);Profile pr=profile(id);Chain c=pr.chains.get(old);if(c!=null&&!n.isEmpty()&&!pr.chains.containsKey(n)){pr.chains.remove(old);c.name=n;pr.chains.put(n,c);saveProfile(id);openEditor(p,n);}else{p.sendMessage(PREFIX+ChatColor.RED+"Invalid name or name already exists.");openEditor(p,old);}}
        });
    }
    private final Map<UUID,Long> delayNext=new HashMap<>();
    @Override public boolean onCommand(CommandSender s,Command cmd,String label,String[] args) {
        if(!allowed(s))return true;
        if(args.length==0){if(s instanceof Player p)openMain(p);else s.sendMessage("/ap <open|create|list|run>");return true;}
        if(args[0].equalsIgnoreCase("open")&&s instanceof Player p){openMain(p);return true;}
        if(args[0].equalsIgnoreCase("create")&&args.length>1&&s instanceof Player p){String n=clean(args[1]);if(n.isEmpty())return true;profile(p.getUniqueId()).chains.putIfAbsent(n,new Chain(n));saveProfile(p.getUniqueId());openEditor(p,n);return true;}
        if(args[0].equalsIgnoreCase("list")&&s instanceof Player p){openChains(p,0);return true;}
        if(args[0].equalsIgnoreCase("run")&&args.length>1&&s instanceof Player p){Chain c=profile(p.getUniqueId()).chains.get(clean(args[1]));if(c!=null)execute(p,c);return true;}
        s.sendMessage(PREFIX+"Use /ap, /ap list, /ap create <name>, /ap run <name>");return true;
    }
    @EventHandler public void onClick(InventoryClickEvent e) {
        String title=e.getView().getTitle(); boolean known=title.equals(MAIN)||title.startsWith(CHAINS+" ")||title.startsWith(EDITOR+" | ")||title.equals(ChatColor.DARK_PURPLE+"✦ ADMIN ABUSE"); if(!known)return;
        e.setCancelled(true); if(!(e.getWhoClicked() instanceof Player p)||!allowed(p)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta())return;
        String name=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()); if(name==null)return;
        UUID id=p.getUniqueId();
        if(title.equals(MAIN)){
            switch(name){case "ADMIN ABUSE":openAbuse(p);break;case "COMMAND CHAINS":openChains(p,0);break;case "CHAIN BUILDER":ask(p,"newchain","Enter a name for your new chain:");break;case "PLAYER TOOLS":p.sendMessage(PREFIX+"Use /tp, /effect and other server commands in your chains.");break;case "MY PROFILE":p.sendMessage(PREFIX+"Your profile is stored separately from other admins.");break;case "CLOSE":p.closeInventory();break;}return;
        }
        if(title.equals(ChatColor.DARK_PURPLE+"✦ ADMIN ABUSE")){
            switch(name){case "BACK":openMain(p);break;case "THUNDER CALL":p.getWorld().strikeLightningEffect(p.getTargetBlockExact(64)==null?p.getLocation():p.getTargetBlockExact(64).getLocation());break;case "LAUNCH":p.setVelocity(p.getVelocity().setY(2.2));break;case "INFERNO":p.getWorld().spawnParticle(org.bukkit.Particle.FLAME,p.getLocation(),100,2,1,2,0.04);p.getWorld().playSound(p.getLocation(),org.bukkit.Sound.ENTITY_BLAZE_SHOOT,1,0.7f);break;case "RANDOM TP":{var l=p.getLocation();var r=l.clone().add((Math.random()-.5)*24,0,(Math.random()-.5)*24);r.setY(p.getWorld().getHighestBlockYAt(r)+1);if(p.getWorld().getBlockAt(r).isPassable())p.teleport(r);break;}}return;
        }
        if(title.startsWith(CHAINS)){
            int page=listPages.getOrDefault(id,0);if(name.equals("BACK")){openMain(p);return;}if(name.equals("CREATE CHAIN")){ask(p,"newchain","Enter a name for your new chain:");return;}
            if(name.equals("PREVIOUS")){openChains(p,page-1);return;}if(name.equals("NEXT")){openChains(p,page+1);return;}
            Profile pr=profile(id);String chainName=name;
            if(pr.chains.containsKey(chainName)){if(e.isShiftClick()&&e.isRightClick()){pr.chains.remove(chainName);saveProfile(id);openChains(p,page);}else if(e.isRightClick()){editingChain.put(id,chainName);openEditor(p,chainName);}else execute(p,pr.chains.get(chainName));}
            return;
        }
        if(title.startsWith(EDITOR)){
            String chainName=title.substring((EDITOR+" | ").length());Chain c=profile(id).chains.get(chainName);if(c==null)return;
            if(name.equals("BACK")){openChains(p,0);return;}
            if(name.equals("ADD COMMAND")){ask(p,"add:"+chainName,"Enter the command to append (without needing a slash):");return;}
            if(name.equals("SET NEXT DELAY")){ask(p,"delay:"+chainName,"Enter delay in ticks for the next step:");return;}
            if(name.equals("RUN CHAIN")){execute(p,c);return;}
            if(name.equals("RENAME CHAIN")){ask(p,"rename:"+chainName,"Enter the new chain name:");return;}
            if(name.startsWith("STEP ")){try{int idx=Integer.parseInt(name.substring(5))-1;if(idx>=0&&idx<c.steps.size()){if(e.isRightClick()){c.steps.remove(idx);}else if(e.isShiftClick()&&idx>0){Collections.swap(c.steps,idx,idx-1);}else{return;}saveProfile(id);openEditor(p,chainName);}}catch(NumberFormatException ignored){}}
        }
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length==1)return Arrays.asList("open","list","create","run");return Collections.emptyList();}
}