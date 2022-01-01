package dev.qixils.totpal;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Bot extends ListenerAdapter {
	public static void main(String[] args) throws LoginException {
		String token = args.length > 0
				? args[0]
				: getToken();
		new Bot(token);
	}

	private static String getToken() {
		// this is dumb lmfao
		// TODO: this doesn't work
		try (InputStream stream = Bot.class.getResourceAsStream("token.txt")) {
			try (InputStreamReader rawReader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				try (BufferedReader reader = new BufferedReader(rawReader)) {
					return reader.readLine();
				}
			}
		} catch (IOException e) {
			return null;
		}
	}

	// real class

	private static final Set<String> HOST_COMMANDS = Set.of(
			"start",
			"clear",
			"end",
			"guess"
	);
	private final JDA jda;
	private final Map<Long, GameData> gameDataMap = new Long2ObjectOpenHashMap<>();

	public Bot(String token) throws LoginException {
		jda = JDABuilder.createDefault(token)
				.setActivity(Activity.competing("Wikipedia"))
				.addEventListeners(this)
				.build();
		jda.updateCommands().addCommands(
				new CommandData("submit", "Enter in to a SOTPAL game by submitting a Wikipedia article title")
						.addOption(
								OptionType.STRING,
								"title",
								"The title of a Wikipedia article",
								true
						),

				new CommandData("start", "Starts a new round of SOTPAL")
						.addOptions(
								new OptionData(
										OptionType.INTEGER,
										"players",
										"The amount of players who should be randomly selected to participate in the game",
										true
								).setMinValue(1).setMaxValue(10)
						),

				new CommandData("guess", "Submits a guess for who is telling the truth during the SOTPAL round")
						.addOption(
								OptionType.USER,
								"user",
								"The user who you believe is telling the truth",
								true
						),

				new CommandData("open", "Opens a new game of SOTPAL"),
				new CommandData("article", "Fetches the current SOTPAL article"),
				new CommandData("clear", "Clears the entered SOTPAL articles"),
				new CommandData("end", "Ends the SOTPAL game")
		).queue();
	}

	@Override
	public void onSlashCommand(@NotNull SlashCommandEvent event) {
		if (event.getGuild() == null || event.getMember() == null)
			return;

		long guildId = event.getGuild().getIdLong();
		long memberId = event.getMember().getIdLong();

		if (!event.getName().equals("open") && !gameDataMap.containsKey(guildId)) {
			event.reply("No SOTPAL game is currently active.").setEphemeral(true).queue();
			return;
		} else if (HOST_COMMANDS.contains(event.getName())
				&& !gameDataMap.get(guildId).isHost(memberId)
				&& !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
			event.reply("This command is only accessible to game hosts.").setEphemeral(true).queue();
			return;
		}

		switch (event.getName()) {
			case "open" -> {
				if (!event.getMember().hasPermission(Permission.PRIORITY_SPEAKER)) {
					event.reply("You must have the permission Priority Speaker to use this command.").setEphemeral(true).queue();
				} else if (gameDataMap.containsKey(guildId)) {
					event.reply("A game is already active. The host must run `/end` before another game can be started.").setEphemeral(true).queue();
				} else {
					gameDataMap.put(guildId, new GameData(jda, memberId, guildId));
					event.reply("A new game of **Some Of These People Are Lying** hosted by <@" + memberId + "> is starting! " +
							"Join us in the voice channel (with microphone on, please!) and submit articles via `/submit`.").queue();
				}
			}
			case "end" -> {
				gameDataMap.remove(guildId);
				event.reply("The game of SOTPAL has ended. Thank you all for playing!").queue();
			}
			case "article" -> {
				GameData gameData = gameDataMap.get(guildId);
				assert gameData != null; // should not be null per an earlier check
				RoundData roundData = gameData.getRound();

				final ReplyAction action;
				if (roundData != null) {
					action = event.reply("The current Wikipedia article title is **" + roundData.articleTitle() + "**");
				} else {
					action = event.reply("A round has not yet started.");
				}

				action.setEphemeral(true).queue();
			}
			case "submit" -> {
				GameData gameData = gameDataMap.get(guildId);
				assert gameData != null; // should not be null per an earlier check

				if (!Objects.requireNonNull(event.getMember().getVoiceState()).inAudioChannel()) {
					event.reply("You must be in a voice channel to use this command.").setEphemeral(true).queue();
					return;
				}
				String title = Objects.requireNonNull(event.getOption("title")).getAsString();
				gameData.updateArticle(memberId, title);
				event.reply("Your Wikipedia article has been set as or updated to **" + title + "**").setEphemeral(true).queue();
			}
			case "clear" -> {
				GameData gameData = gameDataMap.get(guildId);
				assert gameData != null; // should not be null per an earlier check

				gameData.clearArticles();
				event.reply("All submitted Wikipedia articles have been cleared. Please re-enter them using `/submit`.").queue();
			}
			case "start" -> {
				GameData gameData = gameDataMap.get(guildId);
				assert gameData != null; // should not be null per an earlier check

				byte players = (byte) Objects.requireNonNull(event.getOption("players")).getAsLong();
				if (!gameData.canStartNewRound(players)) {
					event.reply("Not enough players have signed up for the game.").setEphemeral(true).queue();
					return;
				}
				RoundData data = gameData.newRound(players);
				StringBuilder message = new StringBuilder()
						.append("A new round has started! Joining <@")
						.append(memberId)
						.append("> today is ");
				Iterator<Long> idIterator = data.playerIds().iterator();
				while (idIterator.hasNext()) {
					long id = idIterator.next();
					boolean hasNext = idIterator.hasNext();
					if (!hasNext) {
						message.append("and ");
					}
					message.append("<@").append(id).append(">");
					if (hasNext)
						message.append(", ");
				}
				message.append(". One of these people is going to be telling the truth about the contents of the Wikipedia article **")
						.append(data.articleTitle())
						.append("** while the others will be lying.");
				event.reply(message.toString()).queue();
			}
			case "guess" -> {
				GameData gameData = gameDataMap.get(guildId);
				assert gameData != null; // should not be null per an earlier check
				RoundData data = gameData.getRound();
				if (data == null) {
					event.reply("A round has not yet started.").setEphemeral(true).queue();
					return;
				}
				if (gameData.hasGuessed()) {
					event.reply("You have already guessed!").setEphemeral(true).queue();
					return;
				}

				long guess = Objects.requireNonNull(event.getOption("user")).getAsLong();
				long truther = data.trutherId();
				if (truther == guess) {
					event.reply("Correct! That's a point to <@" + memberId
							+ "> for identifying the real article and a point to <@" + guess
							+ "> for getting picked, not that you should be keeping track.").queue();
				} else {
					event.reply("Sorry, but <@" + guess
							+ "> was telling a lie! They win a point for deceiving <@" + memberId
							+ ">.\nThe person telling the truth was ||<@" + truther + ">||.").queue();
				}
			}
		}
	}
}
