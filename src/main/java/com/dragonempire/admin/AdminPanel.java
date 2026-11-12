package com.dragonempire.admin;

import org.bukkit.*;
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
import org.bukkit.inventory.meta.SkullMeta;
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
    private final Map<String, AbuseCommand> abuseCommands = new LinkedHashMap<>();
    private final Map<UUID, String> pendingAbuseNames = new HashMap<>();
    private final Map<UUID, String> selectedAbuseCommands = new HashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> tpFrom = new HashMap<>();
    private final Map<UUID, UUID> tpTo = new HashMap<>();
    private final Map<UUID, Location> tpCoords = new HashMap<>();
    private final Map<UUID, String> tpWaypoint = new HashMap<>();
    private final Map<UUID, Map<Integer, UUID>> fromSlots = new HashMap<>(), toSlots = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> waypointSlots = new HashMap<>();
    private final Map<UUID, Integer> waypointPages = new HashMap<>();
    private final Map<UUID, Integer> fromPlayerPages = new HashMap<>(), toPlayerPages = new HashMap<>();
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();
    private static final String PREFIX = ChatColor.DARK_PURPLE + "DES " + ChatColor.GRAY + "» ";
    private static final String MAIN = ChatColor.DARK_GRAY + "✦ DRAGON EMPIRE | CONTROL";
    private static final String CHAINS = ChatColor.DARK_GRAY + "✦ PERSONAL CHAINS";
    private static final String EDITOR = ChatColor.DARK_GRAY + "✦ CHAIN BUILDER";
    private static final String ABUSE_YOURS = ChatColor.DARK_PURPLE + "✦ YOUR ABUSE COMMANDS";
    private static final String ABUSE_SHARED = ChatColor.DARK_PURPLE + "✦ SHARED COMMANDS";
    private static final String ABUSE_DETAIL = ChatColor.DARK_PURPLE + "✦ COMMAND SETTINGS";
    private static final String DAILY_USE = ChatColor.DARK_GREEN + "✦ DAILY USE";
    private static final String DAILY_TP = ChatColor.DARK_GREEN + "✦ DAILY USE | TP";
    private static final String DAILY_WEATHER = ChatColor.DARK_GREEN + "✦ DAILY USE | WEATHER";
    private static final String DAILY_TIME = ChatColor.DARK_GREEN + "✦ DAILY USE | TIME";
    private static final String DAILY_RULES = ChatColor.DARK_GREEN + "✦ IMPORTANT GAMERULES";
    private static final String WAYPOINTS = ChatColor.DARK_GREEN + "✦ SHARED WAYPOINTS";

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
    private static final class AbuseCommand {
        String id, name, command; UUID owner; boolean shared;
        AbuseCommand(String id,String name,String command,UUID owner,boolean shared){this.id=id;this.name=name;this.command=command;this.owner=owner;this.shared=shared;}
    }
    private static final class RuleEntry {
        final String name, description; final GameRule<Boolean> rule;
        RuleEntry(String name, String description, GameRule<Boolean> rule) { this.name=name; this.description=description; this.rule=rule; }
    }
    private static final RuleEntry[] IMPORTANT_RULES = new RuleEntry[]{
        new RuleEntry("KEEP INVENTORY","Keep items after respawning",GameRule.KEEP_INVENTORY),
        new RuleEntry("ADVANCE TIME","Allow the day/night cycle to progress",GameRule.DO_DAYLIGHT_CYCLE),
        new RuleEntry("ADVANCE WEATHER","Allow weather to change naturally",GameRule.DO_WEATHER_CYCLE),
        new RuleEntry("MOB SPAWNS","Allow natural mob spawning",GameRule.DO_MOB_SPAWNING),
        new RuleEntry("COMMAND BLOCK OUTPUT","Show command block output to command-block users",GameRule.COMMAND_BLOCK_OUTPUT),
        new RuleEntry("COMMAND FEEDBACK","Show command feedback to the command sender",GameRule.SEND_COMMAND_FEEDBACK),
        new RuleEntry("FIRE SPREAD","Allow fire to spread and burn out",GameRule.DO_FIRE_TICK),
        new RuleEntry("MOB GRIEFING","Allow mobs to change blocks and interact with them",GameRule.MOB_GRIEFING),
        new RuleEntry("NATURAL REGENERATION","Allow players to regenerate health naturally",GameRule.NATURAL_REGENERATION),
        new RuleEntry("INSOMNIA","Allow phantom spawning from lack of sleep",GameRule.DO_INSOMNIA),
        new RuleEntry("PATROL SPAWNING","Allow pillager patrols to spawn",GameRule.DO_PATROL_SPAWNING),
        new RuleEntry("TRADER SPAWNING","Allow wandering traders to spawn",GameRule.DO_TRADER_SPAWNING)
    };
    private static final class Waypoint {
        String id,name,world; double x,y,z;
        Waypoint(String id,String name,String world,double x,double y,double z){this.id=id;this.name=name;this.world=world;this.x=x;this.y=y;this.z=z;}
        Location location(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x,y,z);}
    }

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        loadAbuseCommands();
        loadWaypoints();
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
        inv.setItem(29,item(Material.WRITABLE_BOOK,ChatColor.GREEN+"YOUR COMMANDS","",ChatColor.GRAY+"Your custom Admin Abuse commands",ChatColor.YELLOW+"Click to open"));
        inv.setItem(33,item(Material.BOOKSHELF,ChatColor.AQUA+"SHARED COMMANDS","",ChatColor.GRAY+"Commands shared by all admins",ChatColor.YELLOW+"Click to open"));
        inv.setItem(49,item(Material.ARROW,ChatColor.WHITE+"BACK")); fill(inv); p.openInventory(inv);
    }
    private File abuseCommandsFile(){return new File(getDataFolder(),"admin-abuse-commands.yml");}
    private void loadAbuseCommands(){
        abuseCommands.clear(); File f=abuseCommandsFile(); if(!f.exists())return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(f); if(y.getConfigurationSection("commands")==null)return;
        for(String id:y.getConfigurationSection("commands").getKeys(false)){
            String n=y.getString("commands."+id+".name"),cmd=y.getString("commands."+id+".command"),owner=y.getString("commands."+id+".owner");
            if(n==null||cmd==null||owner==null)continue;
            try{abuseCommands.put(id,new AbuseCommand(id,n,cmd,UUID.fromString(owner),y.getBoolean("commands."+id+".shared",true)));}
            catch(IllegalArgumentException ex){getLogger().warning("Skipping command with invalid owner UUID: "+id);}
        }
    }
    private void saveAbuseCommands(){
        YamlConfiguration y=new YamlConfiguration();
        for(AbuseCommand c:abuseCommands.values()){String path="commands."+c.id;y.set(path+".name",c.name);y.set(path+".command",c.command);y.set(path+".owner",c.owner.toString());y.set(path+".shared",c.shared);}
        try{y.save(abuseCommandsFile());}catch(IOException ex){getLogger().severe("Could not save Admin Abuse commands: "+ex.getMessage());}
    }
    private boolean canManageAbuse(Player p,AbuseCommand c){return p.getUniqueId().equals(c.owner)||p.hasPermission("dragonempire.admin.owner");}
    private void openAbuseList(Player p,boolean shared){
        String title=shared?ABUSE_SHARED:ABUSE_YOURS;Inventory inv=Bukkit.createInventory(null,54,title);int slot=0;
        for(AbuseCommand c:abuseCommands.values()){
            if(shared?!c.shared:!p.getUniqueId().equals(c.owner))continue;if(slot>=45)break;
            inv.setItem(slot++,item(Material.COMMAND_BLOCK,(shared?ChatColor.AQUA:ChatColor.GREEN)+c.name,"",ChatColor.GRAY+"Command: /"+c.command,ChatColor.GRAY+"Created by: "+Bukkit.getOfflinePlayer(c.owner).getName(),ChatColor.YELLOW+"Click to view / run"));
        }
        if(!shared)inv.setItem(49,item(Material.EMERALD,ChatColor.GREEN+"CREATE COMMAND","",ChatColor.GRAY+"Create a custom Admin Abuse command"));
        inv.setItem(45,item(Material.ARROW,ChatColor.WHITE+"BACK"));
        if(slot==0)inv.setItem(22,item(Material.PAPER,ChatColor.GRAY+"NO COMMANDS YET","",ChatColor.DARK_GRAY+(shared?"Shared commands will appear here.":"Create your first custom command.")));
        fill(inv);p.openInventory(inv);
    }
    private void openAbuseCommand(Player p,String key){
        AbuseCommand c=abuseCommands.get(key);if(c==null){openAbuseList(p,false);return;}selectedAbuseCommands.put(p.getUniqueId(),key);
        Inventory inv=Bukkit.createInventory(null,54,ABUSE_DETAIL);
        inv.setItem(13,item(Material.COMMAND_BLOCK,ChatColor.GOLD+c.name,"",ChatColor.GRAY+"Command: /"+c.command,ChatColor.GRAY+"Visibility: "+(c.shared?ChatColor.GREEN+"SHARED":ChatColor.YELLOW+"PRIVATE"),ChatColor.GRAY+"Owner: "+Bukkit.getOfflinePlayer(c.owner).getName()));
        inv.setItem(29,item(Material.LIME_DYE,ChatColor.GREEN+"RUN COMMAND"));
        if(canManageAbuse(p,c)){inv.setItem(31,item(c.shared?Material.ENDER_EYE:Material.PLAYER_HEAD,ChatColor.YELLOW+(c.shared?"MAKE PRIVATE":"MAKE SHARED"),"",ChatColor.GRAY+"Change who can see this command"));inv.setItem(33,item(Material.NAME_TAG,ChatColor.AQUA+"EDIT COMMAND"));inv.setItem(40,item(Material.BARRIER,ChatColor.RED+"DELETE COMMAND"));}
        inv.setItem(49,item(Material.ARROW,ChatColor.WHITE+"BACK"));fill(inv);p.openInventory(inv);
    }

    private File waypointsFile(){return new File(getDataFolder(),"shared-waypoints.yml");}
    private void loadWaypoints(){
        waypoints.clear();File f=waypointsFile();if(!f.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(f);
        if(y.getConfigurationSection("waypoints")==null)return;
        for(String id:y.getConfigurationSection("waypoints").getKeys(false)){String n=y.getString("waypoints."+id+".name"),w=y.getString("waypoints."+id+".world");if(n!=null&&w!=null)waypoints.put(id,new Waypoint(id,n,w,y.getDouble("waypoints."+id+".x"),y.getDouble("waypoints."+id+".y"),y.getDouble("waypoints."+id+".z")));}
    }
    private void saveWaypoints(){
        YamlConfiguration y=new YamlConfiguration();for(Waypoint w:waypoints.values()){String q="waypoints."+w.id;y.set(q+".name",w.name);y.set(q+".world",w.world);y.set(q+".x",w.x);y.set(q+".y",w.y);y.set(q+".z",w.z);}
        try{y.save(waypointsFile());}catch(IOException ex){getLogger().severe("Could not save shared waypoints: "+ex.getMessage());}
    }
    private ItemStack playerHead(Player target,boolean selected,String section){
        ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta m=(SkullMeta)head.getItemMeta();m.setOwningPlayer(target);
        m.setDisplayName((selected?ChatColor.GREEN+"✓ ":"")+ChatColor.YELLOW+target.getName());m.setLore(Arrays.asList(ChatColor.GRAY+section,selected?ChatColor.GREEN+"Selected":ChatColor.DARK_GRAY+"Left-click to select"));head.setItemMeta(m);return head;
    }
    private int[] fromHeadSlots(){return new int[]{0,1,2,3,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21};}
    private int[] toHeadSlots(){return new int[]{22,23,24,25,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44};}
    private void openDailyUse(Player p){Inventory inv=Bukkit.createInventory(null,54,DAILY_USE);inv.setItem(21,item(Material.ENDER_PEARL,ChatColor.GREEN+"TP","","Teleport players to players or coordinates",ChatColor.YELLOW+"Click to open"));inv.setItem(23,item(Material.WEATHERED_COPPER,ChatColor.AQUA+"WEATHER","","Change weather across every server world",ChatColor.YELLOW+"Click to open"));inv.setItem(25,item(Material.CLOCK,ChatColor.GOLD+"TIME","","Change time across every server world",ChatColor.YELLOW+"Click to open"));inv.setItem(31,item(Material.COMMAND_BLOCK,ChatColor.GOLD+"IMPORTANT GAMERULES","","View and toggle important gamerules across every server world",ChatColor.YELLOW+"Click to open"));inv.setItem(49,item(Material.ARROW,ChatColor.WHITE+"BACK"));fill(inv);p.openInventory(inv);}
    private void openDailyWeather(Player p){Inventory inv=Bukkit.createInventory(null,27,DAILY_WEATHER);inv.setItem(11,item(Material.WATER_BUCKET,ChatColor.AQUA+"RAIN","","Enable rain in every server world"));inv.setItem(13,item(Material.LIGHTNING_ROD,ChatColor.YELLOW+"THUNDER","","Enable thunderstorms in every server world"));inv.setItem(15,item(Material.SUNFLOWER,ChatColor.GOLD+"CLEAR","","Clear rain and thunder in every server world"));inv.setItem(22,item(Material.ARROW,ChatColor.WHITE+"BACK"));fill(inv);p.openInventory(inv);}
    private void setServerWeather(String weather){for(World world:Bukkit.getWorlds()){if(weather.equals("RAIN")){world.setStorm(true);world.setThundering(false);world.setWeatherDuration(20*60*10);world.setThunderDuration(0);}else if(weather.equals("THUNDER")){world.setStorm(true);world.setThundering(true);world.setWeatherDuration(20*60*10);world.setThunderDuration(20*60*10);}else{world.setStorm(false);world.setThundering(false);world.setWeatherDuration(20*60*10);world.setThunderDuration(20*60*10);}}}
    private void openDailyTime(Player p){Inventory inv=Bukkit.createInventory(null,27,DAILY_TIME);inv.setItem(10,item(Material.SUNRISE,ChatColor.YELLOW+"DAWN","","Set every server world to dawn"));inv.setItem(11,item(Material.SUNFLOWER,ChatColor.GOLD+"DAY","","Set every server world to daytime"));inv.setItem(12,item(Material.CLOCK,ChatColor.YELLOW+"NOON","","Set every server world to noon"));inv.setItem(13,item(Material.COMPASS,ChatColor.GOLD+"AFTERNOON","","Set every server world to afternoon"));inv.setItem(14,item(Material.CLOCK,ChatColor.YELLOW+"EVENING","","Set every server world to evening"));inv.setItem(15,item(Material.REDSTONE_TORCH,ChatColor.BLUE+"NIGHT","","Set every server world to night"));inv.setItem(16,item(Material.BLUE_ICE,ChatColor.DARK_PURPLE+"MIDNIGHT","","Set every server world to midnight"));inv.setItem(22,item(Material.ARROW,ChatColor.WHITE+"BACK"));fill(inv);p.openInventory(inv);}
    private void setServerTime(long time){for(World world:Bukkit.getWorlds())world.setTime(time);}
    private boolean isRuleEnabledInAllWorlds(GameRule<Boolean> rule){List<World> worlds=Bukkit.getWorlds();if(worlds.isEmpty())return false;for(World world:worlds)if(!Boolean.TRUE.equals(world.getGameRuleValue(rule)))return false;return true;}
    private String ruleStatus(GameRule<Boolean> rule){List<World> worlds=Bukkit.getWorlds();if(worlds.isEmpty())return "UNKNOWN";boolean on=false,off=false;for(World world:worlds){if(Boolean.TRUE.equals(world.getGameRuleValue(rule)))on=true;else off=true;}return on&&off?"MIXED":on?"ON":"OFF";}
    private void openDailyRules(Player p){Inventory inv=Bukkit.createInventory(null,54,DAILY_RULES);for(int i=0;i<IMPORTANT_RULES.length;i++){RuleEntry entry=IMPORTANT_RULES[i];String status=ruleStatus(entry.rule);boolean enabled=status.equals("ON");Material icon=status.equals("MIXED")?Material.YELLOW_DYE:enabled?Material.LIME_DYE:Material.GRAY_DYE;ChatColor color=status.equals("MIXED")?ChatColor.YELLOW:enabled?ChatColor.GREEN:ChatColor.RED;inv.setItem(10+i,item(icon,color+entry.name,"",ChatColor.GRAY+entry.description,ChatColor.GRAY+"All worlds: "+color+status,ChatColor.YELLOW+"Click to toggle in every world"));}inv.setItem(49,item(Material.ARROW,ChatColor.WHITE+"BACK"));fill(inv);p.openInventory(inv);}
    private void toggleRule(Player p,RuleEntry entry){boolean turnOn=!isRuleEnabledInAllWorlds(entry.rule);for(World world:Bukkit.getWorlds())world.setGameRule(entry.rule,turnOn);p.sendMessage(PREFIX+ChatColor.GREEN+entry.name+" set to "+(turnOn?"ON":"OFF")+" in all server worlds.");openDailyRules(p);}
    private void openDailyTp(Player p){
        UUID id=p.getUniqueId();Inventory inv=Bukkit.createInventory(null,54,DAILY_TP);Set<UUID> selected=tpFrom.computeIfAbsent(id,k->new LinkedHashSet<>());
        List<Player> online=new ArrayList<>(Bukkit.getOnlinePlayers());int fp=Math.max(0,Math.min(fromPlayerPages.getOrDefault(id,0),Math.max(0,(online.size()-1)/fromHeadSlots().length)));int tp=Math.max(0,Math.min(toPlayerPages.getOrDefault(id,0),Math.max(0,(online.size()-1)/toHeadSlots().length)));fromPlayerPages.put(id,fp);toPlayerPages.put(id,tp);
        inv.setItem(4,item(Material.NAME_TAG,ChatColor.GREEN+"FROM ("+(fp+1)+"/"+Math.max(1,(online.size()+fromHeadSlots().length-1)/fromHeadSlots().length)+")","","Players to teleport","Shift + left-click selects multiple","Left-click: next page | Right-click: previous"));
        inv.setItem(26,item(Material.COMPASS,ChatColor.AQUA+"TO ("+(tp+1)+"/"+Math.max(1,(online.size()+toHeadSlots().length-1)/toHeadSlots().length)+")","","Destination player","Left-click: next page | Right-click: previous"));
        Map<Integer,UUID> fm=new HashMap<>(),tm=new HashMap<>();int i=0,start=fp*fromHeadSlots().length;
        for(Player target:online.subList(Math.min(start,online.size()),Math.min(start+fromHeadSlots().length,online.size()))){int slot=fromHeadSlots()[i++];fm.put(slot,target.getUniqueId());inv.setItem(slot,playerHead(target,selected.contains(target.getUniqueId()),"FROM: teleport these players"));}
        i=0;start=tp*toHeadSlots().length;
        for(Player target:online.subList(Math.min(start,online.size()),Math.min(start+toHeadSlots().length,online.size()))){int slot=toHeadSlots()[i++];tm.put(slot,target.getUniqueId());inv.setItem(slot,playerHead(target,target.getUniqueId().equals(tpTo.get(id)),"TO: destination player"));}
        fromSlots.put(id,fm);toSlots.put(id,tm);
        inv.setItem(45,item(Material.LIME_DYE,ChatColor.GREEN+"RUN TP"));
        inv.setItem(47,item(Material.COMPASS,ChatColor.AQUA+"SET COORDINATES","","Enter world x y z in chat"));
        inv.setItem(48,item(Material.WRITABLE_BOOK,ChatColor.GOLD+"SAVE COORDINATES","","Save entered coordinates as a shared waypoint"));
        inv.setItem(49,item(Material.MAP,ChatColor.GOLD+"WAYPOINTS","","Open globally shared coordinates"));
        inv.setItem(51,item(Material.LODESTONE,ChatColor.YELLOW+"SAVE CURRENT LOCATION","","Save your position as a shared waypoint"));
        inv.setItem(53,item(Material.ARROW,ChatColor.WHITE+"BACK"));fill(inv);p.openInventory(inv);
    }
    private void openWaypoints(Player p,int page){
        UUID id=p.getUniqueId();Inventory inv=Bukkit.createInventory(null,54,WAYPOINTS);Set<UUID> selected=tpFrom.computeIfAbsent(id,k->new LinkedHashSet<>());
        List<Player> online=new ArrayList<>(Bukkit.getOnlinePlayers());int fp=Math.max(0,Math.min(fromPlayerPages.getOrDefault(id,0),Math.max(0,(online.size()-1)/fromHeadSlots().length)));fromPlayerPages.put(id,fp);
        inv.setItem(4,item(Material.NAME_TAG,ChatColor.GREEN+"FROM ("+(fp+1)+"/"+Math.max(1,(online.size()+fromHeadSlots().length-1)/fromHeadSlots().length)+")","","Players to teleport","Shift + left-click selects multiple","Left-click: next page | Right-click: previous"));
        Map<Integer,UUID> fm=new HashMap<>();int i=0,startPlayers=fp*fromHeadSlots().length;
        for(Player target:online.subList(Math.min(startPlayers,online.size()),Math.min(startPlayers+fromHeadSlots().length,online.size()))){int slot=fromHeadSlots()[i++];fm.put(slot,target.getUniqueId());inv.setItem(slot,playerHead(target,selected.contains(target.getUniqueId()),"FROM: teleport these players"));}fromSlots.put(id,fm);
        List<Waypoint> all=new ArrayList<>(waypoints.values());int pages=Math.max(1,(all.size()+22)/23);page=Math.max(0,Math.min(page,pages-1));waypointPages.put(id,page);
        Map<Integer,String> wm=new HashMap<>();int start=page*23;
        for(i=start;i<Math.min(start+23,all.size());i++){Waypoint w=all.get(i);int slot=22+i-start;wm.put(slot,w.id);boolean chosen=w.id.equals(tpWaypoint.get(id));inv.setItem(slot,item(Material.LODESTONE,(chosen?ChatColor.GREEN:ChatColor.GOLD)+w.name,"",ChatColor.GRAY+w.world+" "+w.x+" "+w.y+" "+w.z,chosen?ChatColor.GREEN+"Selected":ChatColor.YELLOW+"Click to select"));}
        waypointSlots.put(id,wm);if(all.isEmpty())inv.setItem(32,item(Material.PAPER,ChatColor.GRAY+"NO WAYPOINTS","","Save your current location from the TP menu."));
        inv.setItem(45,item(Material.ARROW,ChatColor.WHITE+"PREVIOUS"));inv.setItem(48,item(Material.ARROW,ChatColor.WHITE+"BACK TO TP"));inv.setItem(50,item(Material.LIME_DYE,ChatColor.GREEN+"RUN TP"));inv.setItem(53,item(Material.ARROW,ChatColor.WHITE+"NEXT"));fill(inv);p.openInventory(inv);
    }
    private void runDailyTp(Player p){
        UUID id=p.getUniqueId();Location dest=null;String wp=tpWaypoint.get(id);UUID to=tpTo.get(id);
        if(wp!=null&&waypoints.containsKey(wp))dest=waypoints.get(wp).location();
        if(dest==null)dest=tpCoords.get(id);
        if(dest==null&&to!=null){Player t=Bukkit.getPlayer(to);if(t!=null)dest=t.getLocation();}
        if(dest==null){p.sendMessage(PREFIX+ChatColor.RED+"Select a TO player, coordinate, or waypoint first.");return;}
        Set<UUID> from=tpFrom.getOrDefault(id,new LinkedHashSet<>());
        if(from.isEmpty()){
            if(wp==null&&!tpCoords.containsKey(id)&&to!=null){Player t=Bukkit.getPlayer(to);if(t!=null){p.teleport(t.getLocation());p.sendMessage(PREFIX+ChatColor.GREEN+"Teleported you to "+t.getName()+".");return;}}
            p.teleport(dest);p.sendMessage(PREFIX+ChatColor.GREEN+"Teleported you to the selected destination.");return;
        }
        int count=0;for(UUID uid:from){Player target=Bukkit.getPlayer(uid);if(target!=null){target.teleport(dest);count++;}}
        p.sendMessage(PREFIX+ChatColor.GREEN+"Teleported "+count+" player(s).");
    }
    private void saveWaypoint(Player p,String name,Location loc){
        String n=name.trim().replaceAll("[^A-Za-z0-9 _-]","");if(n.isEmpty()||n.length()>32){p.sendMessage(PREFIX+ChatColor.RED+"Waypoint name must be 1–32 characters.");return;}
        for(Waypoint w:waypoints.values())if(w.name.equalsIgnoreCase(n)){p.sendMessage(PREFIX+ChatColor.RED+"That waypoint name already exists.");return;}
        String id=UUID.randomUUID().toString();waypoints.put(id,new Waypoint(id,n,loc.getWorld().getName(),loc.getX(),loc.getY(),loc.getZ()));saveWaypoints();p.sendMessage(PREFIX+ChatColor.GREEN+"Saved shared waypoint: "+n);openWaypoints(p,0);
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
        inv.setItem(49,item(Material.COMPARATOR,ChatColor.GOLD+"CHAIN BUILDER","",ChatColor.GRAY+"Create a new saved command chain"));
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
            if(action.equals("newabuse-name")){String n=message.trim().replaceAll("[^A-Za-z0-9 _-]","");if(n.isEmpty()||n.length()>32){p.sendMessage(PREFIX+ChatColor.RED+"Use a name between 1 and 32 characters.");openAbuseList(p,false);return;}pendingAbuseNames.put(id,n);ask(p,"newabuse-command","Enter the command to run (without the leading slash):");}
            else if(action.equals("newabuse-command")){String n=pendingAbuseNames.remove(id),command=message.replaceFirst("^/","").trim();if(n==null||command.isEmpty()||command.contains("\n")){p.sendMessage(PREFIX+ChatColor.RED+"Invalid command.");openAbuseList(p,false);return;}String key=UUID.randomUUID().toString();abuseCommands.put(key,new AbuseCommand(key,n,command,id,true));saveAbuseCommands();p.sendMessage(PREFIX+ChatColor.GREEN+"Created and shared command: "+n);openAbuseList(p,false);}
            else if(action.startsWith("editabuse:")){String key=action.substring(9),command=message.replaceFirst("^/","").trim();AbuseCommand c=abuseCommands.get(key);if(c!=null&&canManageAbuse(p,c)&&!command.isEmpty()&&!command.contains("\n")){c.command=command;saveAbuseCommands();openAbuseCommand(p,key);}else{p.sendMessage(PREFIX+ChatColor.RED+"Could not update that command.");openAbuseList(p,false);}}
            else if(action.equals("tpcoords")){
                String[] a=message.trim().split("\\s+");try{if(a.length!=4)throw new IllegalArgumentException();World w=Bukkit.getWorld(a[0]);if(w==null)throw new IllegalArgumentException();tpCoords.put(id,new Location(w,Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3])));tpWaypoint.remove(id);tpTo.remove(id);p.sendMessage(PREFIX+ChatColor.GREEN+"Coordinate destination selected.");openDailyTp(p);}catch(IllegalArgumentException ex){p.sendMessage(PREFIX+ChatColor.RED+"Use: world x y z (with a loaded world name).");openDailyTp(p);}
            }
            else if(action.equals("savewaypoint-current-name")){saveWaypoint(p,message,p.getLocation());}
            else if(action.equals("savewaypoint-name")){String n=message.trim();if(n.isEmpty()||n.length()>32){p.sendMessage(PREFIX+ChatColor.RED+"Use a waypoint name between 1 and 32 characters.");openDailyTp(p);return;}pendingAbuseNames.put(id,"WAYPOINT:"+n);ask(p,"savewaypoint-coords","Enter coordinates as: world x y z");}
            else if(action.equals("savewaypoint-coords")){String pending=pendingAbuseNames.remove(id);String[] a=message.trim().split("\\s+");try{if(pending==null||!pending.startsWith("WAYPOINT:")||a.length!=4)throw new IllegalArgumentException();World w=Bukkit.getWorld(a[0]);if(w==null)throw new IllegalArgumentException();Location loc=new Location(w,Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3]));saveWaypoint(p,pending.substring(9),loc);}catch(IllegalArgumentException ex){p.sendMessage(PREFIX+ChatColor.RED+"Use valid coordinates: world x y z");openDailyTp(p);}}
            else if(action.equals("newchain")){String n=clean(message);if(n.isEmpty()){p.sendMessage(PREFIX+ChatColor.RED+"Invalid name.");return;}
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
        String title=e.getView().getTitle(); boolean known=title.equals(MAIN)||title.startsWith(CHAINS+" ")||title.startsWith(EDITOR+" | ")||title.equals(ChatColor.DARK_PURPLE+"✦ ADMIN ABUSE")||title.equals(ABUSE_YOURS)||title.equals(ABUSE_SHARED)||title.equals(ABUSE_DETAIL)||title.equals(DAILY_USE)||title.equals(DAILY_TP)||title.equals(DAILY_WEATHER)||title.equals(DAILY_TIME)||title.equals(DAILY_RULES)||title.equals(WAYPOINTS); if(!known)return;
        e.setCancelled(true); if(!(e.getWhoClicked() instanceof Player p)||!allowed(p)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta())return;
        String name=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()); if(name==null)return;
        UUID id=p.getUniqueId();
        if(title.equals(MAIN)){
            switch(name){case "ADMIN ABUSE":openAbuse(p);break;case "COMMAND CHAINS":openChains(p,0);break;case "DAILY USE":openDailyUse(p);break;case "MY PROFILE":p.sendMessage(PREFIX+"Your profile is stored separately from other admins.");break;case "CLOSE":p.closeInventory();break;}return;
        }
        if(title.equals(DAILY_USE)){if(name.equals("BACK")){openMain(p);return;}if(name.equals("TP")){openDailyTp(p);return;}if(name.equals("WEATHER")){openDailyWeather(p);return;}if(name.equals("TIME")){openDailyTime(p);return;}if(name.equals("IMPORTANT GAMERULES")){openDailyRules(p);return;}return;}
        if(title.equals(DAILY_WEATHER)){if(name.equals("BACK")){openDailyUse(p);return;}if(name.equals("RAIN")||name.equals("THUNDER")||name.equals("CLEAR")){setServerWeather(name);p.sendMessage(PREFIX+ChatColor.GREEN+"Weather set to "+name.toLowerCase(Locale.ROOT)+" in all server worlds.");openDailyWeather(p);return;}return;}
        if(title.equals(DAILY_TIME)){if(name.equals("BACK")){openDailyUse(p);return;}long time=switch(name){case "DAWN"->0L;case "DAY"->1000L;case "NOON"->6000L;case "AFTERNOON"->9000L;case "EVENING"->12000L;case "NIGHT"->13000L;case "MIDNIGHT"->18000L;default->-1L;};if(time>=0){setServerTime(time);p.sendMessage(PREFIX+ChatColor.GREEN+"Time set to "+name.toLowerCase(Locale.ROOT)+" in all server worlds.");openDailyTime(p);}return;}
        if(title.equals(DAILY_RULES)){if(name.equals("BACK")){openDailyUse(p);return;}for(RuleEntry entry:IMPORTANT_RULES)if(name.equals(entry.name)){toggleRule(p,entry);return;}return;}
        if(title.equals(DAILY_TP)){
            if(name.equals("BACK")){openDailyUse(p);return;}if(name.equals("RUN TP")){runDailyTp(p);return;}
            if(name.equals("SET COORDINATES")){ask(p,"tpcoords","Enter destination as: world x y z");return;}
            if(name.equals("SAVE COORDINATES")){ask(p,"savewaypoint-name","Enter a name for this shared waypoint:");return;}
            if(name.equals("WAYPOINTS")){openWaypoints(p,0);return;}
            if(name.equals("SAVE CURRENT LOCATION")){ask(p,"savewaypoint-current-name","Enter a name for this shared waypoint:");return;}
            if(e.getSlot()==4){int pages=Math.max(1,(Bukkit.getOnlinePlayers().size()+fromHeadSlots().length-1)/fromHeadSlots().length);int page=fromPlayerPages.getOrDefault(id,0)+(e.isRightClick()?-1:1);fromPlayerPages.put(id,Math.floorMod(page,pages));openDailyTp(p);return;}
            if(e.getSlot()==26){int pages=Math.max(1,(Bukkit.getOnlinePlayers().size()+toHeadSlots().length-1)/toHeadSlots().length);int page=toPlayerPages.getOrDefault(id,0)+(e.isRightClick()?-1:1);toPlayerPages.put(id,Math.floorMod(page,pages));openDailyTp(p);return;}
            UUID fuid=fromSlots.getOrDefault(id,Collections.emptyMap()).get(e.getSlot());
            if(fuid!=null&&e.isLeftClick()){Set<UUID> set=tpFrom.computeIfAbsent(id,k->new LinkedHashSet<>());if(e.isShiftClick()){if(!set.add(fuid))set.remove(fuid);}else{set.clear();set.add(fuid);}openDailyTp(p);return;}
            UUID tuid=toSlots.getOrDefault(id,Collections.emptyMap()).get(e.getSlot());
            if(tuid!=null&&e.isLeftClick()){tpTo.put(id,tuid);tpCoords.remove(id);tpWaypoint.remove(id);openDailyTp(p);return;}return;
        }
        if(title.equals(WAYPOINTS)){
            if(e.getSlot()==4){int pages=Math.max(1,(Bukkit.getOnlinePlayers().size()+fromHeadSlots().length-1)/fromHeadSlots().length);int page=fromPlayerPages.getOrDefault(id,0)+(e.isRightClick()?-1:1);fromPlayerPages.put(id,Math.floorMod(page,pages));openWaypoints(p,waypointPages.getOrDefault(id,0));return;}
            if(name.equals("BACK TO TP")){openDailyTp(p);return;}if(name.equals("PREVIOUS")){openWaypoints(p,waypointPages.getOrDefault(id,0)-1);return;}if(name.equals("NEXT")){openWaypoints(p,waypointPages.getOrDefault(id,0)+1);return;}if(name.equals("RUN TP")){runDailyTp(p);return;}
            UUID fuid=fromSlots.getOrDefault(id,Collections.emptyMap()).get(e.getSlot());
            if(fuid!=null&&e.isLeftClick()){Set<UUID> set=tpFrom.computeIfAbsent(id,k->new LinkedHashSet<>());if(e.isShiftClick()){if(!set.add(fuid))set.remove(fuid);}else{set.clear();set.add(fuid);}openWaypoints(p,waypointPages.getOrDefault(id,0));return;}
            String wid=waypointSlots.getOrDefault(id,Collections.emptyMap()).get(e.getSlot());if(wid!=null&&e.isLeftClick()){tpWaypoint.put(id,wid);tpCoords.remove(id);tpTo.remove(id);openWaypoints(p,waypointPages.getOrDefault(id,0));return;}return;
        }
        if(title.equals(ChatColor.DARK_PURPLE+"✦ ADMIN ABUSE")){
            switch(name){case "BACK":openMain(p);break;case "YOUR COMMANDS":openAbuseList(p,false);break;case "SHARED COMMANDS":openAbuseList(p,true);break;case "THUNDER CALL":p.getWorld().strikeLightningEffect(p.getTargetBlockExact(64)==null?p.getLocation():p.getTargetBlockExact(64).getLocation());break;case "LAUNCH":p.setVelocity(p.getVelocity().setY(2.2));break;case "INFERNO":p.getWorld().spawnParticle(org.bukkit.Particle.FLAME,p.getLocation(),100,2,1,2,0.04);p.getWorld().playSound(p.getLocation(),org.bukkit.Sound.ENTITY_BLAZE_SHOOT,1,0.7f);break;case "RANDOM TP":{var l=p.getLocation();var r=l.clone().add((Math.random()-.5)*24,0,(Math.random()-.5)*24);r.setY(p.getWorld().getHighestBlockYAt(r)+1);if(p.getWorld().getBlockAt(r).isPassable())p.teleport(r);break;}}return;
        }
        if(title.equals(ABUSE_YOURS)||title.equals(ABUSE_SHARED)){
            boolean shared=title.equals(ABUSE_SHARED);if(name.equals("BACK")){openAbuse(p);return;}
            if(name.equals("CREATE COMMAND")&&!shared){ask(p,"newabuse-name","Enter a name for your new command:");return;}
            if(name.equals("NO COMMANDS YET"))return;
            for(AbuseCommand c:abuseCommands.values()){if(shared?!c.shared:!p.getUniqueId().equals(c.owner))continue;String display=ChatColor.stripColor((shared?ChatColor.AQUA:ChatColor.GREEN)+c.name);if(display.equals(name)){openAbuseCommand(p,c.id);return;}}return;
        }
        if(title.equals(ABUSE_DETAIL)){
            String key=selectedAbuseCommands.get(id);AbuseCommand c=key==null?null:abuseCommands.get(key);if(c==null){openAbuseList(p,false);return;}
            if(name.equals("BACK")){openAbuseList(p,c.shared);return;}
            if(name.equals("RUN COMMAND")){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),c.command);p.sendMessage(PREFIX+ChatColor.GREEN+"Executed "+c.name+".");return;}
            if(!canManageAbuse(p,c))return;
            if(name.equals("MAKE PRIVATE")){c.shared=false;saveAbuseCommands();openAbuseCommand(p,key);return;}
            if(name.equals("MAKE SHARED")){c.shared=true;saveAbuseCommands();openAbuseCommand(p,key);return;}
            if(name.equals("EDIT COMMAND")){ask(p,"editabuse:"+key,"Enter the updated command (without the leading slash):");return;}
            if(name.equals("DELETE COMMAND")){abuseCommands.remove(key);saveAbuseCommands();p.sendMessage(PREFIX+ChatColor.RED+"Command deleted.");openAbuseList(p,false);return;}return;
        }
        if(title.startsWith(CHAINS)){
            int page=listPages.getOrDefault(id,0);if(name.equals("BACK")){openMain(p);return;}if(name.equals("CHAIN BUILDER")){ask(p,"newchain","Enter a name for your new chain:");return;}
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