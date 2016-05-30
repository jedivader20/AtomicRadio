package id.au.jsaunders.atomicradio;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginDescriptionFile;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.bukkit.configuration.file.FileConfiguration;

public final class AtomicRadio extends JavaPlugin {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static String listenURL;
    private static String statusURL;
    private static String messagePrefix;
    private static String djPrefix;
    private static String djName;
    private static String djDisplayName;
    private static final List<String> requestList = new ArrayList<>();
    private FileConfiguration config;

    @Override
    public void onEnable(){
        PluginDescriptionFile pdfFile = this.getDescription();

        // Save a copy of the default config.yml if one is not there
        this.saveDefaultConfig();

        config = getConfig();

        listenURL = config.getString("listenURL");
        statusURL = config.getString("statusURL");
        messagePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix"));
        djPrefix = config.getString("djPrefix");
        djName = null;
        djDisplayName = null;

        // "AtomicRadio version blah has been enabled!"
        log.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
    }

    @Override
    public void onDisable() {
        this.saveConfig();
        PluginDescriptionFile pdfFile = this.getDescription();
        // "AtomicRadio version blah has been disabled!"
        log.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is disabled!" );
    }

    private void radioConfigReload() {
        // set config variables to null
        listenURL = null;
        statusURL = null;
        messagePrefix = null;
        djPrefix = null;
        config = null;
        // read the config values fresh from config.yml
        reloadConfig();
        // repopulate config variables
        config = getConfig();
        listenURL = config.getString("listenURL");
        statusURL = config.getString("statusURL");
        messagePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix"));
        djPrefix = config.getString("djPrefix");
        log.info(djPrefix);
    }

    // XML Parsing
    private String[] getStatusXML(String statusURL) {
        String[] result;
        result = new String[3];
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(statusURL);
            doc.getDocumentElement().normalize();
            // Fetch value of CURRENTLISTENERS
            NodeList listeners = doc.getElementsByTagName("CURRENTLISTENERS");
            for (int i = 0; i < listeners.getLength(); i++)
            {
                Node n = listeners.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element e = (Element) n;
                Node shoutcastNode = e.getChildNodes().item(0);
                result[1] = shoutcastNode.getNodeValue();
            }
            // Fetch value of STREAMSTATUS
            NodeList status = doc.getElementsByTagName("STREAMSTATUS");
            for (int i = 0; i < status.getLength(); i++)
            {
                Node n = status.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element e = (Element) n;
                Node shoutcastNode = e.getChildNodes().item(0);
                result[0] = shoutcastNode.getNodeValue();
            }
            // Fetch value of SONGTITLE
            NodeList song = doc.getElementsByTagName("SONGTITLE");
            for (int i = 0; i < song.getLength(); i++)
            {
                Node n = song.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element e = (Element) n;
                Node shoutcastNode = e.getChildNodes().item(0);
                result[2] = shoutcastNode.getNodeValue();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return result;
    }

    // Compile the broadcast message
    private TextComponent radioBroadcast(int radioListeners, String radioSong) {
        // Build the message to return the broadcast information
        TextComponent message = new TextComponent(TextComponent.fromLegacyText(messagePrefix + " " + ChatColor.DARK_RED + "LIVE"));
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, listenURL));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.DARK_RED + djPrefix + ChatColor.GRAY + ": " + ChatColor.RESET + djDisplayName + "\n" + ChatColor.GRAY + radioListeners + " listeners " + "\n" + ChatColor.DARK_RED + "NP" + ChatColor.GRAY + ": " + radioSong).create()));
        return message;
    }

    // Integer checker
    private boolean isInteger(String input) {
        try {
            //noinspection ResultOfMethodCallIgnored
            Integer.parseInt(input);
            return true;
        }
        catch(NumberFormatException e) {
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(cmd.getName().equalsIgnoreCase("radio")) {
            // If the player typed /radio
            // Check to see if the command was issued via player or console
            if(sender instanceof Player) {
                Player player = (Player) sender;
                String p = player.getName();
                if(args.length == 0) { // No arguments
                    // Is there a DJ online?
                    if(djName != null) {
                        // Get and parse the XML for the radio (returns an array)
                        String[] result = getStatusXML(statusURL);
                        // Check the status of the stream
                        if (Integer.parseInt(result[0]) == 1) {
                            // Broadcast the details of the current stream
                            player.spigot().sendMessage(radioBroadcast(Integer.parseInt(result[1]), result[2]));
                            return true;
                        } else {
                            sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Stream is currently offline! Please try again later.");
                            return true;
                        }
                    } else {
                        sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no DJ online, please try again later.");
                        return true;
                    }
                } else if((args[0].equalsIgnoreCase("bc")) || (args[0].equalsIgnoreCase("broadcast"))) { // /radio bc or /radio broadcast
                    // Check if player has permission to use
                    if(player.hasPermission("atomicradio.admin") || (djName.equals(player.getName()))) {
                        // Is there a current DJ?
                        if(djName != null) {
                            // Get and parse the XML for the radio (returns an array)
                            String[] result = getStatusXML(statusURL);
                            // Check the status of the stream
                            if (Integer.parseInt(result[0]) == 1) {
                                // Broadcast the details of the current stream
                                Bukkit.spigot().broadcast(radioBroadcast(Integer.parseInt(result[1]), result[2]));
                                return true;
                            } else {
                                // DJ connected and not broadcasting
                                sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Stream is currently offline! Tell " + djDisplayName + ChatColor.RESET + " to reconnect, or message an admin.");
                                return true;
                            }
                        } else {
                            // No DJ online
                            sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no DJ online, please try again later.");
                            return true;
                        }
                    } else {
                        sender.sendMessage(messagePrefix + ChatColor.RED + " " + "NOPE. You do not have permission to use this command.");
                        return true;
                    }
                } else if(args[0].equalsIgnoreCase("dj")) { // Player typed /radio dj
                    if(args.length == 1) { // if no arguments entered
                        // Player seeking current DJ
                        // Check to see if there is a current DJ
                        if(djName == null) {
                            // If no current DJ
                            sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no DJ online, please try again later!");
                            return true;
                        } else {
                            // If there is a DJ
                            sender.sendMessage(messagePrefix + ChatColor.RESET + djDisplayName + ChatColor.RESET +" is the current DJ!");
                            return true;
                        }
                    } else if(args[1].equalsIgnoreCase("on")){ // Player typed /radio dj on
                        // Check to see if player has atomicradio.use permission
                        if(player.hasPermission("atomicradio.use")) {
                            if(djName == null) {
                                // If there is no current DJ
                                djName = p;
                                // Does the player have a nickname? If so, use that shit.
                                if (!(player.getDisplayName().equals(djName))) {
                                    djDisplayName = player.getDisplayName();
                                } else {
                                    djDisplayName = djName;
                                }
                                // Broadcast that DJ is going online
                                TextComponent message = new TextComponent(TextComponent.fromLegacyText(messagePrefix + ChatColor.RESET + " " + ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + djPrefix + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " " + djName + " is going online! Click to listen!"));
                                message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, listenURL));
                                Bukkit.spigot().broadcast(message);
                                return true;
                            } else {
                                // If there is a current DJ
                                sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is already a DJ online! Please try again later.");
                                return true;
                            }
                        } else {
                            sender.sendMessage(messagePrefix + ChatColor.RED + " " + "NOPE. You do not have permission to use this command. (/radio dj on)");
                            return true;
                        }
                    } else if(args[1].equalsIgnoreCase("off")) { // Player typed /radio dj off
                        // Check to see if there is a DJ
                        if(djName == null) {
                            sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no current DJ to take offline!");
                            return true;
                        } else if(player.hasPermission("atomicradio.admin") || p.equals(djName)) {
                            // Broadcast that DJ is going offline
                            Bukkit.broadcastMessage(messagePrefix + ChatColor.RESET + " " + djDisplayName + ChatColor.RESET + " has stopped broadcasting! Thanks for listening!");
                            //Reset DJ status variable
                            djName = null;
                            return true;
                        } else {
                            sender.sendMessage(messagePrefix + ChatColor.RED + " " + "NOPE. You do not have permission to use this command. (/radio dj off)");
                            return true;
                        }
                    }
                } else if(args[0].equalsIgnoreCase("reload")) {
                    // Reload command issued, are they an admin?
                    if (player.hasPermission("atomicradio.admin")) {
                        radioConfigReload();
                        sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Configuration reloaded!");
                        return true;
                    } else {
                        sender.sendMessage(messagePrefix + ChatColor.RED + " " + "NOPE. You do not have permission to use this command. (/radio reload)");
                        return true;
                    }
                } else if(args[0].equalsIgnoreCase("request")) {
                    // first things first, is there a DJ?
                    if(djName == null) {
                        // If there is no current DJ
                        sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no DJ online, please try again later.");
                        return true;
                    } else if(args.length == 1) {
                        // No request given
                        sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "No request given. (usage: /radio request artist/song)");
                        return true;
                    } else {
                        // oooer, someone is requesting something, better add it to the list and notify the DJ!
                        String request = args[1];
                        for (int i = 2; i > args.length; i++) {
                            request = request + " " + args[i];
                        }
                        sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "You requested: " + request);
                        getServer().getPlayer(djName).sendMessage(messagePrefix + ChatColor.DARK_RED + ChatColor.BOLD + "REQUEST" + ChatColor.RESET + ": " + sender.getName() + " requested: " + request);
                        request = request + ", requested by: " + sender.getName();
                        requestList.add(request);
                    }
                } else if(args[0].equalsIgnoreCase("reqlist")) {
                    if(djName == null) {
                        // If there is no current DJ
                        sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no DJ online, please try again later.");
                        return true;
                    } else if(requestList.size() == 0) {
                        // If there is no requests
                        sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "No requests currently. Request something!");
                        return true;
                    } else if(args.length == 1) {
                        // No arguments, return the first 5 requests
                        sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Request List: Page 1 of " + (requestList.size()/5) + ":");
                        for(int i = 0; (i < requestList.size() || i < 5); i++) {
                            String value = requestList.get(i);
                            sender.sendMessage((i+1) + ". " + value);
                        }
                        sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Use reqlist del <num> to delete a request, or reqlist clear to remove all requests.");
                        return true;
                    } else if(args[1].equals("del")) {
                        // Delete a request
                        if (player.hasPermission("atomicradio.admin") || p.equals(djName)) {
                            // Player has permissions, proceed
                            if (args.length == 2) {
                                // No arguments
                                sender.sendMessage(messagePrefix + ChatColor.RED + " " + "Incorrect parameter: You need to specify a number, or ''all'' to clear all requests.");
                                return true;
                            } else if (args[2].equals("all")) {
                                // Clear all requests
                                requestList.clear();
                                sender.sendMessage(messagePrefix + ChatColor.RED + " " + "All requests deleted!");
                                return true;
                            } else if (isInteger(args[2])) {
                                // Number provided, does that request exist?
                                int i = Integer.parseInt(args[1]);
                                if (i > requestList.size()) {
                                    sender.sendMessage(messagePrefix + ChatColor.RED + " " + "Incorrect parameter: That request does not exist!");
                                    return true;
                                } else {
                                    requestList.remove((i - 1));
                                    sender.sendMessage(messagePrefix + ChatColor.RED + " " + "Request " + i + " deleted!");
                                    return true;
                                }
                            } else {
                                sender.sendMessage(messagePrefix + ChatColor.RED + " " + "Incorrect parameter.");
                                return true;
                            }
                        } else {
                            sender.sendMessage(messagePrefix + ChatColor.RED + " " + "NOPE. You do not have permission to use this command. (/radio reload)");
                            return true;
                        }
                    } else if (isInteger(args[1])) {
                        // Is an integer, but is it one of the results?
                        int i = Integer.parseInt(args[1]) * 5;
                        if (i > requestList.size()) {
                            sender.sendMessage(messagePrefix + ChatColor.RED + " " + "There is no page" + i + ".");
                        } else {
                            sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Request List: Page " + i + " of " + (requestList.size()/5) + ":");
                            for (int index = i; (index > requestList.size() || index > (index+5)); i++) {
                                String value = requestList.get(index);
                                sender.sendMessage((index+1) + ". " + value);
                            }
                            sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Use reqlist del <num> to delete a request, or reqlist clear to remove all requests.");
                            return true;
                        }
                    }
                } else {
                    sender.sendMessage(messagePrefix + ChatColor.RED + " " + "Incorrect parameter.");
                }
            }
        } else {
            log.warning(messagePrefix + ChatColor.RED + " " + "This command may only be issued by a player!");
            return true;
        }
        return false;
    }
}