package id.au.jsaunders.AtomicRadio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class AtomicRadio extends JavaPlugin {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static String djName;
    private static final List<String> requestList = new ArrayList<>();
    private static final List<String> requesterList = new ArrayList<>();
    private FileConfiguration config;
    private String[] radioResult;
    private String streamType;
    private String errorType;
    private String listenURL;
    private String oldSong;
    private String newSong;

    @Override
    public void onEnable(){
        PluginDescriptionFile pdfFile = this.getDescription();
        // Save a copy of the default config.yml if one is not there
        this.saveDefaultConfig();
        config = getConfig();
        newSong = null;
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
        config = null;
        // read the config values fresh from config.yml
        reloadConfig();
        // repopulate config variables
        config = getConfig();
    }

    private boolean radioCompareSong(String oldSong, String newSong) {
        // Compare song title to last-fetched data
        return !oldSong.equals(newSong);
    }

    // XML Parsing
    private boolean getShoutStatus(String scURL) {
        String[] result;
        result = new String[3];
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(scURL);
            doc.getDocumentElement().normalize();
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
                // Check if value is null, if so, cannot continue, no song
                if (shoutcastNode.getNodeValue().equals("0")) {
                    errorType = "streamOffline";
                    return false;
                } else {
                    // Fetch value of CURRENTLISTENERS
                    NodeList listeners = doc.getElementsByTagName("CURRENTLISTENERS");
                    for (i = 0; i < listeners.getLength(); i++) {
                        n = listeners.item(i);
                        if (n.getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        e = (Element) n;
                        shoutcastNode = e.getChildNodes().item(0);
                        result[0] = shoutcastNode.getNodeValue(); // Current Listeners
                    }
                    // Fetch value of SONGTITLE
                    NodeList song = doc.getElementsByTagName("SONGTITLE");
                    for (i = 0; i < song.getLength(); i++) {
                        n = song.item(i);
                        if (n.getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        e = (Element) n;
                        shoutcastNode = e.getChildNodes().item(0);
                        result[1] = shoutcastNode.getNodeValue(); // Song Title
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            errorType = "internalError";
            return false;
        }

        // Publish new result set
        radioResult = new String[3];
        radioResult = result;
        return true;
    }

    // JSON Parsing
    private boolean getDubStatus(String dubURL) {
        String[] result;
        result = new String[3];
        try {
            // Parse JSON at checkURL
            String checkURL = IOUtils.toString(new URL("https://api.dubtrack.fm/room/" + dubURL).openStream());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(checkURL);
            JsonNode data = node.get("data");
            JsonNode currentSong = data.get("currentSong");

            // Check if currentSong is a null node, means no song playing
            if (currentSong.isNull()) {
                errorType = "streamOffline";
                return false;
            } else {
                // There is a song, we can continue
                result[0] = data.get("activeUsers").asText(); // Current Listeners
                result[1] = currentSong.get("name").asText(); // Current Song Name

                // Get DJ Name
                String playlistURL = IOUtils.toString(new URL("https://api.dubtrack.fm/room/" + data.get("_id").asText() + "/playlist/active").openStream());
                ObjectMapper mapper2 = new ObjectMapper();
                JsonNode node2 = mapper2.readTree(playlistURL);
                JsonNode data2 = node2.get("data");
                JsonNode song2 = data2.get("song");
                String userURL = IOUtils.toString(new URL("https://api.dubtrack.fm/user/" + song2.get("userid").asText()).openStream());
                ObjectMapper mapper3 = new ObjectMapper();
                JsonNode node3 = mapper3.readTree(userURL);
                JsonNode data3 = node3.get("data");
                result[2] = data3.get("username").asText(); // Current DJ Username
            }
        } catch (Exception e) {
            e.printStackTrace();
            errorType = "internalError";
            return false;
        }

        // Publish new result set
        radioResult = new String[3];
        radioResult = result;
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean getStreamStatus (String streamType) {
        try {
            if (streamType.equals("shoutcast")) {
                // is a ShoutCast stream
                return getShoutStatus(config.getString("scStatusURL"));
            } else {
                // is a Dubtrack stream
                return getDubStatus(config.getString("dubRoomName"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            errorType = "internalError";
            return false;
        }
    }

    // Compile the broadcast message
    private TextComponent radioBroadcast(int radioListeners, String radioSong, String username, String listenURL) {
        // Build the message to return the broadcast information
        TextComponent message = new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + " " + ChatColor.DARK_RED + "LIVE"));
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, listenURL));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.DARK_RED + "DJ" + ChatColor.GRAY + ": " + ChatColor.RESET + username + "\n" + ChatColor.GRAY + radioListeners + " listeners " + "\n" + ChatColor.DARK_RED + "NP" + ChatColor.GRAY + ": " + radioSong).create()));
        return message;
    }

    // Compile the broadcast message
    private TextComponent radioOnline(String username, String type) {
        // Build the message to return the broadcast information
        String messageID;
        if (type.equals("shoutcast")) {
            messageID = "goingOnlineShoutcast";
            listenURL = config.getString("scListenURL");
        } else {
            messageID = "goingOnlineDubtrack";
            listenURL = config.getString("dubListenURL");
        }
        TextComponent message = new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.RESET + " " + username + ChatColor.RESET + " " + config.getString(messageID)));
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, listenURL));
        return message;
    }

    // Error Message Generator
    private TextComponent radioError(String command, String errorMessage) {
        // Build the message to return the broadcast information
        return new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.RESET + errorMessage + ChatColor.RESET + " (" + command + ")"));
    }

    // Message Generator
    private TextComponent radioMessage(String messageType) {
        // Build the message to return the broadcast information
        return new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.RESET + messageType));
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
                String djDisplayName;
                if(args.length == 0) { // No arguments
                    // Is there a DJ online?
                    if(djName != null) {
                        // Get Radio Status Result Set
                        try {
                            if (!getStreamStatus(streamType)) { // Stream offline or error
                                player.spigot().sendMessage(radioError("streamStatus", config.getString(errorType)));
                                return true;
                            } else {
                                // Broadcast the details of the current stream
                                player.spigot().sendMessage(radioBroadcast(Integer.parseInt(radioResult[0]), radioResult[1], radioResult[2], listenURL));
                                return true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            player.spigot().sendMessage(radioError("streamStatus", config.getString("internalError")));
                            return true;
                        }
                    } else {
                        player.spigot().sendMessage(radioError("checkDJ", config.getString("noDJ")));
                        return true;
                    }
                } else if((args[0].equalsIgnoreCase("bc")) || (args[0].equalsIgnoreCase("broadcast"))) { // /radio bc or /radio broadcast
                    // Check if player has permission to use
                    if(player.hasPermission("atomicradio.admin") || (djName.equals(player.getName()))) {
                        // Is there a current DJ?
                        if(djName != null) {
                            // Get Radio Status Result Set
                            try {
                                if (!getStreamStatus(streamType)) { // Stream offline or error
                                    player.spigot().sendMessage(radioError("streamStatus", config.getString(errorType)));
                                    return true;
                                } else {
                                    // Is this the first time that broadcast is being run?
                                    //noinspection ObjectEqualsNull
                                    if(newSong.equals(null)) {
                                        newSong = radioResult[1];
                                        if(radioCompareSong(oldSong, newSong)) {
                                            player.spigot().sendMessage(radioError("bc", config.getString("sameSong")));
                                            return true;
                                        }
                                    }
                                    // Broadcast the details of the current stream
                                    Bukkit.spigot().broadcast(radioBroadcast(Integer.parseInt(radioResult[0]), radioResult[1], radioResult[2], listenURL));
                                    oldSong = radioResult[1];
                                    newSong = null;
                                    return true;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                player.spigot().sendMessage(radioError("bc", config.getString("internalError")));
                                return true;
                            }
                        } else {
                            // No DJ online
                            player.spigot().sendMessage(radioError("bc", config.getString("noDJ")));
                            return true;
                        }
                    } else {
                        player.spigot().sendMessage(radioError("bc", config.getString("noPermission")));
                        return true;
                    }
                } else if(args[1].equalsIgnoreCase("off")) { // Player typed /radio off
                    if(player.hasPermission("atomicradio.admin") || p.equals(djName)) {
                        if (djName == null) { // Check to see if there is a DJ
                            player.spigot().sendMessage(radioError("off", config.getString("noDJ")));
                            return true;
                        } else {
                            // Broadcast that DJ is going offline
                            Bukkit.spigot().broadcast(radioMessage(config.getString("goingOffline")));
                            //Reset DJ status variable
                            djName = null;
                            streamType = null;
                            requestList.clear();
                            return true;
                        }
                    } else {
                        player.spigot().sendMessage(radioError("off", config.getString("noPermission")));
                        return true;
                    }
                } else if(args[0].equalsIgnoreCase("on")) {
                        // Check to see if player has atomicradio.use permission
                        if (player.hasPermission("atomicradio.use")) {
                            if (args.length == 1) { // if no arguments entered
                                // No arguments entered
                                player.spigot().sendMessage(radioError("on", config.getString("incorrectParameter") + " " + config.getString("noStreamType")));
                                return true;
                            } else {
                                if (args[1].equalsIgnoreCase("shoutcast") || args[1].equalsIgnoreCase("dubtrack")) {
                                    if (djName == null) {
                                        // If there is no current DJ
                                        // Going online with a Shoutcast stream
                                        streamType = args[1].toLowerCase();
                                        djName = p;
                                        // Does the player have a nickname? If so, use that shit.
                                        if (!(player.getDisplayName().equals(djName))) {
                                            djDisplayName = player.getDisplayName();
                                        } else {
                                            djDisplayName = djName;
                                        }
                                        // Broadcast that DJ is going online
                                        Bukkit.spigot().broadcast(radioOnline(djDisplayName, streamType));
                                        return true;
                                    } else {
                                        // If there is a current DJ
                                        player.spigot().sendMessage(radioError("on", config.getString("notDJ")));
                                        return true;
                                    }
                                } else {
                                    // Incorrect arguments entered
                                    player.spigot().sendMessage(radioError("on", config.getString("incorrectParameter") + " " + config.getString("noStreamType")));
                                    return true;
                                }
                            }
                        } else {
                            player.spigot().sendMessage(radioError("on", config.getString("noPermission")));
                            return true;
                        }
                    } else if(args[0].equalsIgnoreCase("reload")) {
                    // Reload command issued, are they an admin?
                    if (player.hasPermission("atomicradio.admin")) {
                        radioConfigReload();
                        Bukkit.spigot().broadcast(radioMessage(config.getString("configReloaded")));
                        return true;
                    } else {
                        player.spigot().sendMessage(radioError("reload", config.getString("noPermission")));
                        return true;
                    }
                } else if(args[0].equalsIgnoreCase("request") || args[0].equalsIgnoreCase("req")) {
                    // first things first, is there a DJ?
                    if(djName == null) {
                        // If there is no current DJ
                        player.spigot().sendMessage(radioError("", config.getString("noDJ")));
                        return true;
                    } else if(args.length == 1) {
                        // No request given
                        player.spigot().sendMessage(radioError("request", config.getString("noRequest")));
                        return true;
                    } else {
                        // oooer, someone is requesting something, better add it to the list and notify the DJ!
                        String request = args[1];
                        for (int i = 2; i < args.length; i++) {
                            request = request + " " + args[i];
                        }
                        player.spigot().sendMessage(radioMessage(config.getString("requested")));
                        getServer().getPlayer(djName).sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.DARK_RED + ChatColor.BOLD + " REQUEST" + ChatColor.RESET + ": " + sender.getName() + " requested: " + request);
                        requestList.add(request);
                        requesterList.add(player.getDisplayName());
                    }
                } else if(args[0].equalsIgnoreCase("reqlist")) {
                    if(djName == null) {
                        // If there is no current DJ
                        player.spigot().sendMessage(radioError("reqlist", config.getString("noDJ")));
                        return true;
                    } else if(requestList.size() == 0) {
                        // If there is no requests
                        player.spigot().sendMessage(radioError("reqlist", config.getString("noRequests")));
                        return true;
                    } else if(args.length == 1) {
                        // No arguments, return the first 5 requests
                        int reqSize = 5;
                        if (reqSize > requestList.size()) {
                            reqSize = requestList.size();
                        }
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.GOLD + " " + "Requests " + ChatColor.DARK_GREEN + "1-" + reqSize + ChatColor.GOLD + " of " + ChatColor.DARK_GREEN + requestList.size() + ChatColor.GOLD + ":");
                        for(int i = 0; (i < requestList.size() && i < 5); i++) {
                            sender.sendMessage(ChatColor.GOLD + String.valueOf(i+1) + ". " + ChatColor.RESET + requestList.get(i) + " " + config.getString("requestedBy") + " " + ChatColor.RESET + requesterList.get(i));
                        }
                        return true;
                    } else if(args[1].equalsIgnoreCase("del")) {
                        // Delete a request
                        if (player.hasPermission("atomicradio.admin") || p.equals(djName)) {
                            // Player has permissions, proceed
                            if (args.length == 2) {
                                // No arguments
                                player.spigot().sendMessage(radioError("reqlist", config.getString("incorrectParameter") + " " + config.getString("noRequestSpecified")));
                                return true;
                            } else if (args[2].equals("all")) {
                                // Clear all requests
                                requestList.clear();
                                player.spigot().sendMessage(radioMessage(config.getString("allRequestsDeleted")));
                                return true;
                            } else if (isInteger(args[2])) {
                                // Number provided, does that request exist?
                                int i = Integer.parseInt(args[2]);
                                if (i > requestList.size()) {
                                    player.spigot().sendMessage(radioError("reqlist", config.getString("incorrectParameter") + " " + config.getString("requestNotExist")));
                                    return true;
                                } else {
                                    requestList.remove((i - 1));
                                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.RED + " " + "Request " + i + " deleted!");
                                    return true;
                                }
                            } else {
                                player.spigot().sendMessage(radioError("reqlist", config.getString("incorrectParameter")));
                                return false;
                            }
                        } else {
                            player.spigot().sendMessage(radioError("reqlist del", config.getString("noPermission")));
                            return true;
                        }
                    } else if (isInteger(args[1])) {
                        // Is an integer, but is it one of the results?
                        int i = Integer.parseInt(args[1]) * 5;
                        int j = (Integer.parseInt(args[1]) * 5) - 4;
                        int k = i + 5;
                        int reqSize = i;
                        if (reqSize > requestList.size()) {
                            reqSize = requestList.size();
                        }
                        if (j > requestList.size()) {
                            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.RED + " " + "There is no page" + args[1] + ".");
                        } else {
                            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.GOLD + " " + "Requests " + ChatColor.DARK_GREEN + j + ChatColor.GOLD + " to " + ChatColor.DARK_GREEN + reqSize + ChatColor.GOLD + " of " + ChatColor.DARK_GREEN + requestList.size() + ":");
                            for (int index = j-1; (index < requestList.size() && index < k); index++) {
                                sender.sendMessage(ChatColor.GOLD + String.valueOf(i+1) + ". " + ChatColor.RESET + requestList.get(i) + " " + config.getString("requestedBy") + " " + ChatColor.RESET + requesterList.get(i));
                            }
                            return true;
                        }
                    }
                } else {
                    player.spigot().sendMessage(radioError("reqlist", config.getString("incorrectParameter")));
                    return false;
                }
            }
        } else {
            log.warning(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.RED + " " + "This command may only be issued by a player!");
            return true;
        }
        return false;
    }
}