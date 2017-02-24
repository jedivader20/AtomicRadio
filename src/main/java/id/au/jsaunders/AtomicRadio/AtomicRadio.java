package id.au.jsaunders.AtomicRadio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Logger;

public final class AtomicRadio extends JavaPlugin {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static String djName;
    private static ArrayList<String> requestList;
    private static ArrayList<String> requesterList;
    private FileConfiguration config;
    private String[] radioResult;
    private String streamType;
    private String errorType;
    private String listenURL;

    @Override
    public void onEnable(){
        PluginDescriptionFile pdfFile = this.getDescription();
        // Save a copy of the default config.yml if one is not there
        this.saveDefaultConfig();
        config = getConfig();
        requesterList = new ArrayList<>();
        requestList = new ArrayList<>();
        // "AtomicRadio version blah has been enabled!"
        log.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
    }

    @Override
    public void onDisable() {
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
            URL checkURL = new URL("https://api.dubtrack.fm/room/" + dubURL);
            HttpsURLConnection request = (HttpsURLConnection) checkURL.openConnection();
            request.connect();
            JsonParser jp = new JsonParser();
            JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent()));
            JsonObject rootobj = root.getAsJsonObject();
            JsonObject data = rootobj.getAsJsonObject("data");
            JsonObject currentSong = data.getAsJsonObject("currentSong");

            // Check if currentSong is a null node, means no song playing
            if (currentSong.isJsonNull()) {
                errorType = "streamOffline";
                return false;
            } else {
                // There is a song, we can continue
                result[0] = data.get("activeUsers").getAsString(); // Current Listeners
                result[1] = currentSong.get("name").getAsString(); // Current Song Name

                // Get DJ Name
                URL playlistURL = new URL("https://api.dubtrack.fm/room/" + data.get("_id").getAsString() + "/playlist/active");
                request = (HttpsURLConnection) playlistURL.openConnection();
                request.connect();
                jp = new JsonParser();
                root = jp.parse(new InputStreamReader((InputStream) request.getContent()));
                rootobj = root.getAsJsonObject();
                data = rootobj.getAsJsonObject("data");
                JsonObject song = data.getAsJsonObject("song");
                URL userURL = new URL("https://api.dubtrack.fm/user/" + song.get("userid").getAsString());
                request = (HttpsURLConnection) userURL.openConnection();
                request.connect();
                jp = new JsonParser();
                root = jp.parse(new InputStreamReader((InputStream) request.getContent()));
                rootobj = root.getAsJsonObject();
                data = rootobj.getAsJsonObject("data");
                result[2] = data.get("username").getAsString(); // Current DJ Username
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
        TextComponent message = new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) +
	                                                                               " " + ChatColor.DARK_RED + "LIVE" + ChatColor.RESET + " (Click to listen!)"));
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, listenURL));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                             new ComponentBuilder(ChatColor.GREEN + "DJ " + ChatColor.RESET + username + "\n" +
												 ChatColor.GREEN + ChatColor.BOLD + "♬" + "\n" +
												 ChatColor.RESET + radioSong + "\n" +
												 ChatColor.AQUA + radioListeners + " listeners").create()));
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
        TextComponent message = new TextComponent(TextComponent.fromLegacyText(
	                                                ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) +
	                                                ChatColor.RESET + " " + username + ChatColor.RESET + "'s " +
		                                            ChatColor.translateAlternateColorCodes('&', config.getString(messageID))));
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, listenURL));
        return message;
    }

    // Error Message Generator
    private TextComponent radioError(String command, String errorMessage) {
        // Build the message to return the broadcast information
        return new TextComponent(TextComponent.fromLegacyText(
	                                ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) +
	                                ChatColor.RESET + " " + ChatColor.translateAlternateColorCodes('&', errorMessage) +
	                                ChatColor.RESET + " (" + command + ")"));
    }

    // Message Generator
    private TextComponent radioMessage(String messageType) {
        // Build the message to return the broadcast information
        return new TextComponent(TextComponent.fromLegacyText(
	        ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.RESET + " " +
		    ChatColor.translateAlternateColorCodes('&', messageType)));
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
	            Bukkit.getScheduler().runTaskAsynchronously(this, () ->
	            {
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
					            } else {
						            // Broadcast the details of the current stream
						            player.spigot().sendMessage(radioBroadcast(Integer.parseInt(radioResult[0]), radioResult[1], radioResult[2], listenURL));
					            }
				            } catch (Exception e) {
					            e.printStackTrace();
					            player.spigot().sendMessage(radioError("streamStatus", config.getString("internalError")));
				            }
			            } else {
				            player.spigot().sendMessage(radioError("checkDJ", config.getString("noDJ")));
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
						            } else {
							            // Broadcast the details of the current stream
							            Bukkit.spigot().broadcast(radioBroadcast(Integer.parseInt(radioResult[0]), radioResult[1], radioResult[2], listenURL));
						            }
					            } catch (Exception e) {
						            e.printStackTrace();
						            player.spigot().sendMessage(radioError("bc", config.getString("internalError")));
					            }
				            } else {
					            // No DJ online
					            player.spigot().sendMessage(radioError("bc", config.getString("noDJ")));
				            }
			            } else {
				            player.spigot().sendMessage(radioError("bc", config.getString("noPermission")));
			            }
		            } else if(args[0].equalsIgnoreCase("off")) { // Player typed /radio off
			            if(player.hasPermission("atomicradio.admin") || p.equals(djName)) {
				            if (djName == null) { // Check to see if there is a DJ
					            player.spigot().sendMessage(radioError("off", config.getString("noDJ")));
				            } else {
					            // Broadcast that DJ is going offline
					            Bukkit.spigot().broadcast(radioMessage(config.getString("goingOffline")));
					            //Reset DJ status variable
					            djName = null;
					            streamType = null;
					            requestList.clear();
				            }
			            } else {
				            player.spigot().sendMessage(radioError("off", config.getString("noPermission")));
			            }
		            } else if(args[0].equalsIgnoreCase("on")) {
			            // Check to see if player has atomicradio.use permission
			            if (player.hasPermission("atomicradio.use")) {
				            if (args.length == 1) { // if no arguments entered
					            // No arguments entered
					            player.spigot().sendMessage(radioError("on", config.getString("incorrectParameter") + " " + config.getString("noStreamType")));
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
						            } else {
							            // If there is a current DJ
							            player.spigot().sendMessage(radioError("on", config.getString("notDJ")));
						            }
					            } else {
						            // Incorrect arguments entered
						            player.spigot().sendMessage(radioError("on", config.getString("incorrectParameter") + " " + config.getString("noStreamType")));
					            }
				            }
			            } else {
				            player.spigot().sendMessage(radioError("on", config.getString("noPermission")));
			            }
		            } else if(args[0].equalsIgnoreCase("reload")) {
			            // Reload command issued, are they an admin?
			            if (player.hasPermission("atomicradio.admin")) {
				            radioConfigReload();
				            player.spigot().sendMessage(radioMessage(config.getString("configReloaded")));
			            } else {
				            player.spigot().sendMessage(radioError("reload", config.getString("noPermission")));
			            }
		            } else if(args[0].equalsIgnoreCase("request") || args[0].equalsIgnoreCase("req")) {
			            // first things first, is there a DJ?
			            if(djName == null) {
				            // If there is no current DJ
				            player.spigot().sendMessage(radioError("", config.getString("noDJ")));
			            } else if(args.length == 1) {
				            // No request given
				            player.spigot().sendMessage(radioError("request", config.getString("noRequest")));
			            } else {
				            // oooer, someone is requesting something, better add it to the list and notify the DJ!
				            StringBuilder request = new StringBuilder(args[1]);
				            for (int i = 2; i < args.length; i++) {
					            request.append(" ").append(args[i]);
				            }
				            player.spigot().sendMessage(radioMessage(config.getString("youRequested") + ChatColor.RESET + ": " + request));
				            getServer().getPlayer(djName).sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.DARK_RED + ChatColor.BOLD + " REQUEST" + ChatColor.RESET + ": " + sender.getName() + " requested: " + request);
				            requestList.add(request.toString());
				            requesterList.add(player.getDisplayName());
			            }
		            } else if(args[0].equalsIgnoreCase("reqlist")) {
			            if(djName == null) {
				            // If there is no current DJ
				            player.spigot().sendMessage(radioError("reqlist", config.getString("noDJ")));
			            } else if(requestList.isEmpty()) {
				            // If there is no requests
				            player.spigot().sendMessage(radioError("reqlist", config.getString("noRequests")));
			            } else if(args.length == 1) {
				            // No arguments, return the first 5 requests
				            int reqSize = 5;
				            if (reqSize > requestList.size()) {
					            reqSize = requestList.size();
				            }
				            player.spigot().sendMessage(radioMessage(ChatColor.GOLD + " " + "Requests " + ChatColor.DARK_GREEN + "1-" + reqSize + ChatColor.GOLD + " of " + ChatColor.DARK_GREEN + requestList.size() + ChatColor.GOLD + ":"));
				            for(int i = 0; (i < requestList.size() && i < 5); i++) {
					            player.spigot().sendMessage(radioMessage(ChatColor.GOLD + String.valueOf(i+1) + ". " + ChatColor.RESET + requestList.get(i) + " " + config.getString("requestedBy") + ": " + ChatColor.RESET + requesterList.get(i)));
				            }
			            } else if(args[1].equalsIgnoreCase("del")) {
				            // Delete a request
				            if (player.hasPermission("atomicradio.admin") || p.equals(djName)) {
					            // Player has permissions, proceed
					            if (args.length == 2) {
						            // No arguments
						            player.spigot().sendMessage(radioError("reqlist", config.getString("incorrectParameter") + " " + config.getString("noRequestSpecified")));
					            } else if (args[2].equals("all")) {
						            // Clear all requests
						            requestList.clear();
						            player.spigot().sendMessage(radioMessage(config.getString("allRequestsDeleted")));
					            } else if (isInteger(args[2])) {
						            // Number provided, does that request exist?
						            int i = Integer.parseInt(args[2]);
						            if (i > requestList.size()) {
							            player.spigot().sendMessage(radioError("reqlist", config.getString("incorrectParameter") + " " + config.getString("requestNotExist")));
						            } else {
							            requestList.remove((i - 1));
							            player.spigot().sendMessage(radioMessage(ChatColor.RED + " " + "Request " + i + " deleted!"));
						            }
					            } else {
						            player.spigot().sendMessage(radioError("reqlist", config.getString("incorrectParameter")));
					            }
				            } else {
					            player.spigot().sendMessage(radioError("reqlist del", config.getString("noPermission")));
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
					            player.spigot().sendMessage(radioError("reqlist", ChatColor.RED + " " + "There is no page" + args[1] + "."));
				            } else {
					            player.spigot().sendMessage(radioMessage(ChatColor.GOLD + " " + "Requests " + ChatColor.DARK_GREEN + j + ChatColor.GOLD + " to " + ChatColor.DARK_GREEN + reqSize + ChatColor.GOLD + " of " + ChatColor.DARK_GREEN + requestList.size() + ":"));
					            for (int index = j-1; (index < requestList.size() && index < k); index++) {
						            player.spigot().sendMessage(radioMessage(ChatColor.GOLD + String.valueOf(i+1) + ". " + ChatColor.RESET + requestList.get(i) + " " + config.getString("requestedBy") + ": " + ChatColor.RESET + requesterList.get(i)));
					            }
				            }
			            }
		            } else {
			            player.spigot().sendMessage(radioError("reqlist", config.getString("incorrectParameter")));
		            }
	            });
	            return true;
            }
            else {
	            log.warning(ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix")) + ChatColor.RED + " " + "This command may only be issued by a player!");
	            return true;
            }
        }
        return false;
    }
}