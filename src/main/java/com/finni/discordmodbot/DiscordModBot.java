/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.finni.discordmodbot;


import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.List;
import java.util.stream.Stream;

import com.finni.discordmodbot.command.discord.slashcommand.SlashRestartTimes;
import net.essentialsx.api.v2.services.discord.MessageType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.intent.Intent;

import com.finni.discordmodbot.command.MainCommand;
import com.finni.discordmodbot.command.discord.McUserLookup;
import com.finni.discordmodbot.command.discord.slashcommand.SlashPlayerList;
import com.finni.discordmodbot.listener.EssentialsDiscordModlogs;

import net.essentialsx.api.v2.services.discord.DiscordService;
import net.essentialsx.api.v2.services.discord.InteractionException;

/** @author Finn Teichmann */


public class DiscordModBot extends JavaPlugin
{

	private static  DiscordModBot instance;
	private DiscordService discordService;
	private DiscordApi discordAPI;
	private YamlConfiguration mcModBotConfig;
	private MainCommand mainDMBCommand;
	private MessageType logChannel;


	private void mcModBotLoadConf(){
		instance = this;
		File dir = this.getDataFolder(); //Your plugin folder

		if(!dir.exists() && !dir.mkdirs()) { //making sure the plugin folder exists
			getLogger().severe("Plugin folder couldn't be created! Please provide a folder with the config.yml restart the server!");
			getLogger().info("Disabling the plugin.");
			getPluginLoader().disablePlugin(this);
		}

		File conf = new File(this.getDataFolder() + "/conf.yml"); //This is your external file
		this.mcModBotConfig = YamlConfiguration.loadConfiguration(conf); //Get the configuration of your external File

		if(!conf.exists()) { //Check if your external file exists
			try {
				conf.createNewFile(); //if not so, create a new one
				YamlConfiguration config = createDefaultConfiguration();
				config.save(conf); //save the configuration of config1 or config2 to your new file
				getLogger().severe("Configuration not found! A file config.yml has been generated in the plugin folder. Please populate the values and restart the server!");
			} catch (IOException e) {
				getLogger().severe("Configuration file couldn't be created! Please provide a config.yml in the plugin folder and restart the server!");
			} finally {
				getLogger().info("Disabling plugin due to missing configuration file");
				getPluginLoader().disablePlugin(this);
			}

		} else {
			if (this.mcModBotConfig.getString("bot-token") == null) {
				getLogger().severe("Bot token not set in config file!!");
				getPluginLoader().disablePlugin(this);
			}

		}
	}

	@Override
	public void onEnable() {
		this.mcModBotLoadConf();
		this.mainDMBCommand = (MainCommand)MainCommand.getNewInstance();
		this.mainDMBCommand.setPlugin( this );
		this.mainDMBCommand.registerMainCommand();

		this.discordService = Bukkit.getServicesManager().load(DiscordService.class);
		try {
			essentialsRegisterSlashCommandsAndLogChannel();
		}
			catch( InteractionException e )
		{
			getLogger().severe("no discord service! can't add slash command.");
		}
		mCModBotLoginToDiscord();
		registerListeners();
	}

	@Override
	public void onDisable() {
		mCModBotLogOutFromDiscord();
	}

	private void mCModBotLoginToDiscord(){
		new DiscordApiBuilder()
			.setToken(this.mcModBotConfig.getString("bot-token")) // Set the token of the bot here
			.setIntents( Intent.GUILD_PRESENCES, Intent.GUILD_MESSAGES, Intent.GUILD_MEMBERS, Intent.MESSAGE_CONTENT)
			.login().thenAccept( api -> this.discordAPI = api )
			.exceptionally(error -> {
				// Log a warning when the login to Discord failed (wrong token?)
				getLogger().severe("Failed to connect to Discord! Disabling plugin! Warning: "+error.getMessage());
				getPluginLoader().disablePlugin(this);
				return null;
		}).join();
		// Log a message that the connection was successful and log the url that is needed to invite the bot
		getLogger().info("Connected to Discord as " + this.discordAPI.getYourself().getDiscriminatedName());
		//getLogger().info("Open the following url to invite the bot: " + api.createBotInvite());
	}

	private void mCModBotLogOutFromDiscord(){
		if (this.discordAPI != null)
		{
			// Make sure to disconnect the bot when the plugin gets disabled
			this.discordAPI.disconnect().join();
		}
	}

	public void reloadBot() {

		if (this.discordAPI != null)
			mCModBotLogOutFromDiscord();
		mcModBotLoadConf();
		mCModBotLoginToDiscord();
		registerListeners();
		System.gc();
	}

	private void registerListeners() {
		McUserLookup.createNewInstance();
		EssentialsDiscordModlogs.createNewInstance();
		if(McUserLookup.getInstance() != null && EssentialsDiscordModlogs.getInstance() != null ) {
			this.discordAPI.addListener(McUserLookup.getInstance());
			this.getServer().getPluginManager().registerEvents( EssentialsDiscordModlogs.getInstance(), this);
		} else {
			getLogger().severe("no discord API! can't add listeners.");
		}
	}
	private void essentialsRegisterSlashCommandsAndLogChannel() throws InteractionException{
		if( this.discordService != null ){

			this.discordService.getInteractionController().registerCommand(new SlashPlayerList());
			this.discordService.getInteractionController().registerCommand(new SlashRestartTimes());
			this.logChannel = new MessageType("modlogs");
			this.discordService.registerMessageType(this, this.logChannel);
		}
	}

	public static DiscordModBot getInstance(){
		return instance;
	}
	public MessageType getLogChannel(){
		return this.logChannel;
	}
	public DiscordApi getDiscordAPI(){
		return this.discordAPI;
	}
	public YamlConfiguration getMcModBotConfig(){
		return this.mcModBotConfig;
	}

	public static YamlConfiguration createDefaultConfiguration() {
		YamlConfiguration config = new YamlConfiguration();
		config.set("bot-token", "<botToken here>");
		config.set("command-channel-id", "123456789");
		config.setInlineComments("command-channel-id", List.of("replace with actual channel id"));
		config.set("allowed-roles", List.of(11111111, 222222222));
		config.setInlineComments("allowed-roles", List.of("replace with actual role ids"));
		config.set("allowed-channels", List.of(11111111, 222222222));
		config.setInlineComments("allowed-channels", List.of("replace with actual channel ids"));
		config.set("logChannelID", "11111111");
		config.setInlineComments("logChannelID", List.of("replace with actual channel id"));
		config.set("moderationLogSettings", Stream.of(
				new AbstractMap.SimpleEntry<>( "kick", true ),
				new AbstractMap.SimpleEntry<>( "ban", true ),
				new AbstractMap.SimpleEntry<>( "tempBan", true ),
				new AbstractMap.SimpleEntry<>( "tempIPBan", true ),
				new AbstractMap.SimpleEntry<>( "ipBan", true ),
				new AbstractMap.SimpleEntry<>( "mute", true ),
				new AbstractMap.SimpleEntry<>( "tempMute", true ),
				new AbstractMap.SimpleEntry<>( "unban", true ),
				new AbstractMap.SimpleEntry<>( "unmute", true ),
				new AbstractMap.SimpleEntry<>( "ipUnban", true )
			)
		);
		config.setComments("moderationLogSettings", List.of("whether logging is activated for those events. Will be posted into the channel with ID specified in logChannelID ."));
		return  config;
	}
}