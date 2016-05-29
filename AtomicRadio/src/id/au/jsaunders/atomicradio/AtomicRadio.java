package id.au.jsaunders.atomicradio;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginDescriptionFile;
//import org.bukkit.plugin.RegisteredServiceProvider;
// import org.bukkit.plugin.PluginManager;
// import net.milkbowl.vault.chat.Chat;
import java.util.logging.Logger;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.bukkit.configuration.file.FileConfiguration;

public final class AtomicRadio extends JavaPlugin {
	private FileConfiguration config;

	// Vault hooking
	//public static Chat chat = null;

	private static final Logger log = Logger.getLogger("Minecraft");
	private static String listenURL;
	private static String statusURL;
	private static String messagePrefix;
	private static String djPrefix;
	private static String djName;

	@Override
	public void onEnable(){

		//PluginManager pm = getServer().getPluginManager();
		PluginDescriptionFile pdfFile = this.getDescription();

		// Save a copy of the default config.yml if one is not there
		this.saveDefaultConfig();

		config = getConfig();
		
		listenURL = config.getString("listenURL");
		statusURL = config.getString("statusURL");
		messagePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("messagePrefix"));
		djPrefix = config.getString("djPrefix");
		djName = null;

		// Vault Hooking
		//		if (!setupChat() ) {
		//			log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", pdfFile.getName()));
		//			pm.disablePlugin(this);
		//			return;
		//		}

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
		messagePrefix = config.getString("messagePrefix");
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

	// Vault Hooking (Chat)

	//	private boolean setupChat() {
	//		if (getServer().getPluginManager().getPlugin("Vault") == null) {
	//			return false;
	//		}
	//		RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
	//		if (rsp == null) {
	//			return false;
	//		}
	//		chat = rsp.getProvider();
	//		return chat != null;
	//	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		// Get variables from config
		//String preDJPrefix = config.getString("preDJPrefix");
		if(cmd.getName().equalsIgnoreCase("radio")) {
			// If the player typed /radio
			// Check to see if the command was issued via player or console
			if(sender instanceof Player) {
				Player player = (Player) sender;
				if(args.length == 0) { // No arguments
					// Check to see if player has atomicradio.status
					if (player.hasPermission("atomicradio.status")) {
						// Is there a DJ online?
						if(djName != null) {
							// Get and parse the XML for the radio (returns an array)
							String[] result = getStatusXML(statusURL);
							int radioStatus = Integer.parseInt(result[0]);
							int radioListeners = Integer.parseInt(result[1]);
							String radioSong = result[2];
							// Check the status of the stream
							if (radioStatus == 1) {
								// Broadcast the details of the current stream
								sender.sendMessage(messagePrefix + ChatColor.RESET + " " + ChatColor.DARK_RED + "LIVE" + ChatColor.GRAY + " with " + ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + djPrefix + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " " + djName + ChatColor.GOLD + " | " + ChatColor.GRAY + radioListeners + " listeners " + ChatColor.GOLD + "|" + ChatColor.DARK_RED + " NP" + ChatColor.GRAY + ": " + radioSong + " " + ChatColor.GOLD + "|" + ChatColor.GRAY + " Listen at: " + ChatColor.GOLD + listenURL);
								return true;
							} else {
								sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Stream is currently offline! Please try again later.");
								return true;
							}
						} else {
							sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no DJ online, please try again later.");
							return true;
						}
					} else {
						sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "NOPE. You do not have permission to use this command.");
						return true;
					}
				} else if((args[0].equalsIgnoreCase("bc")) || (args[0].equalsIgnoreCase("broadcast"))) { // /radio bc or /radio broadcast
					if(player.hasPermission("atomicradio.admin")) { // Check to see if player has atomicradio.admin
						// Is there a current DJ?
						if(djName != null) {
							// Get and parse the XML for the radio (returns an array)
							String[] result = getStatusXML(statusURL);
							int radioStatus = Integer.parseInt(result[0]);
							int radioListeners = Integer.parseInt(result[1]);
							String radioSong = result[2];
							// Check the status of the stream
							if (radioStatus == 1) {
								// Broadcast the details of the current stream
								Bukkit.broadcastMessage(messagePrefix + ChatColor.RESET + " " + ChatColor.DARK_RED + "LIVE" + ChatColor.GRAY + " with " + ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + djPrefix + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " " + djName + ChatColor.GOLD + " | " + ChatColor.GRAY + radioListeners + " listeners " + ChatColor.GOLD + "|" + ChatColor.DARK_RED + " NP" + ChatColor.GRAY + ": " + radioSong + " " + ChatColor.GOLD + "|" + ChatColor.GRAY + " Listen at: " + ChatColor.GOLD + listenURL);
								return true;
							} else {
								sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Stream is currently offline! Please try again later.");
								return true;
							}
						} else {
							sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no DJ online, please try again later.");
							return true;
						}
					} else {
						// Is the person issuing this command the online DJ?
						String p = player.getName();
						if(p.equals(djName)) {
							// Check to see if player has atomicradio.use
							if(player.hasPermission("atomicradio.use")) {
								// Get and parse the XML for the radio (returns an array)
								String[] result = getStatusXML(statusURL);
								int radioStatus = Integer.parseInt(result[0]);
								int radioListeners = Integer.parseInt(result[1]);
								String radioSong = result[2];
								// Check the status of the stream
								if (radioStatus == 1) {
									// Broadcast the details of the current stream
									Bukkit.broadcastMessage(messagePrefix + ChatColor.RESET + " " + ChatColor.DARK_RED + "LIVE" + ChatColor.GRAY + " with " + ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + djPrefix + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " " + djName + ChatColor.GOLD + " | " + ChatColor.GRAY + radioListeners + " listeners " + ChatColor.GOLD + "|" + ChatColor.DARK_RED + " NP" + ChatColor.GRAY + ": " + radioSong + " " + ChatColor.GOLD + "|" + ChatColor.GRAY + " Listen at: " + ChatColor.GOLD + listenURL);
									return true;
								} else {
									sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Stream is currently offline! Please try again later.");
									return true;
								}
							}
						} else {
							sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "NOPE. You do not have permission to use this command.");
							return true;
						}
					}
				} else if(args[0].equalsIgnoreCase("dj")) { // Player typed /radio dj
					// Check to see if player has atomicradio.status permission
					if(player.hasPermission("atomicradio.status")) {
						if(args.length == 1) { // if no arguments entered
							// Player seeking current DJ
							// Check to see if there is a current DJ
							if(djName == null) {
								// If no current DJ
								sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no DJ online, please try again later!");
								return true;	
							} else {
								// If there is a DJ
								sender.sendMessage(messagePrefix + ChatColor.RESET + " " + ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + djPrefix + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " " + djName + " is the current DJ!");
								return true;
							}
						} else if(args[1].equalsIgnoreCase("on")){ // Player typed /radio dj on
							// Check to see if player has atomicradio.use permission
							if(player.hasPermission("atomicradio.use")) {
								if(djName == null) {
									// If there is no current DJ
									djName = player.getName();
									// Get Vault prefix and store it under preDJPrefix
									//preDJPrefix = chat.getPlayerPrefix(player);
									// Set Vault prefix to defined DJ prefix (djPrefix)
									//chat.setPlayerPrefix(player, djPrefix);
									// Broadcast that DJ is going online
									Bukkit.broadcastMessage(messagePrefix + ChatColor.RESET + " " + ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + djPrefix + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " " + djName + " is going online! Listen in at: " + listenURL);
									//this.getConfig().set("djName", djName);
									//this.saveConfig();
									return true;
								} else {
									// If there is a current DJ
									sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is already a DJ online! Please try again later.");
									return true;
								}
							} else {
								sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "NOPE. You do not have permission to use this command. (/radio dj on)");
								return true;
							}
						} else if(args[1].equalsIgnoreCase("off")) { // Player typed /radio dj off
							String p = player.getName();
							// Check to see if player is an admin
							if(player.hasPermission("atomicradio.admin")) {
								//Check to see if there is a current DJ
								if(djName != null) {
									// Broadcast that DJ is going offline
									Bukkit.broadcastMessage(messagePrefix + ChatColor.RESET + " " + ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + djPrefix + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " " + djName + " has stopped broadcasting! Thanks for listening!");
									// Get custom prefix from before (if any) and set it back via Vault
									//if(preDJPrefix != "none") {
										// Set Vault prefix to what pre-DJ prefix was
										//Player djPlayer = (Player) Bukkit.getOfflinePlayer(djName); 
										//chat.setPlayerPrefix(djPlayer, preDJPrefix);
										// Reset Vault prefix variable
										//preDJPrefix = "none";
									//}
									//Reset DJ status variable
									djName = null;
									//this.getConfig().set("djName", djName);
									//this.saveConfig();
									return true;
								} else {
									sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no current DJ to take offline!");
									return true;
								}
							} else if(player.hasPermission("atomicradio.use")) {
								//Check to see if there is a current DJ
								if(djName != null) {
									if(p.equals(djName)) { // Check to see if player is the current DJ
										// Broadcast that DJ is going offline
										Bukkit.broadcastMessage(messagePrefix + ChatColor.RESET + " " + ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + djPrefix + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " " + djName + " has stopped broadcasting! Thanks for listening!");
										// Get custom prefix from before (if any) and set it back via Vault
										//if(preDJPrefix != "none") {
											// Set Vault prefix to what pre-DJ prefix was
											//chat.setPlayerPrefix(player, preDJPrefix);
											// Reset Vault prefix variable
											//preDJPrefix = "none";
										//}
										// Reset DJ status variable
										djName = null;
										this.getConfig().set("djName", djName);
										this.saveConfig();
										return true;
									} else {
										sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "You are not the current DJ! Only they can issue this command.");
										return true;
									}
								} else {
									sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "There is no current DJ to take offline!");
									return true;
								}
							} else {
								sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "NOPE. You do not have permission to use this command. (/radio dj off)");
								return true;
							}
						}
					} else {
						sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "NOPE. You do not have permission to use this command. (/radio)");
						return true;
					}
				} else if(args[0].equalsIgnoreCase("reload")) {
					// Reload command issued, are they an admin?
					if (player.hasPermission("atomicradio.admin")) {
						radioConfigReload();
						sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "Configuration reloaded!");
						return true;
					} else {
						sender.sendMessage(messagePrefix + ChatColor.RESET + " " + "NOPE. You do not have permission to use this command. (/radio reload)");
						return true;
					}
				}
			} else {
				log.warning(messagePrefix + ChatColor.RESET + " " + "This command may only be issued by a player!");
				return true;
			}
		}
		return false;
	}
}