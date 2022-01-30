package dev.qixils.totpal;

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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class Bot extends ListenerAdapter {
	public static void main(String[] args) throws LoginException {
		String token = args.length > 0
				? args[0]
				: getTokenFromFile();
		if (token == null) {
			System.out.println("A token must be supplied as a command-line argument or placed in the `token.txt` file in the working directory.");
			System.exit(1);
		}
		new Bot(token);
	}

	private static String getTokenFromFile() {
		File tokenFile = new File("token.txt");
		if (!tokenFile.exists())
			return null;
		try {
			try (FileReader fileReader = new FileReader(tokenFile)) {
				try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
					return bufferedReader.readLine();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static <T> String commaJoinCollection(Collection<T> collection) {
		return commaJoinCollection(collection, Objects::toString);
	}

	private static <T> String commaJoinCollection(Collection<T> collection, Function<T, String> mapper) {
		if (collection.isEmpty())
			throw new IllegalArgumentException("Collection should not be empty");
		else if (collection.size() == 1) {
			for (T item : collection)
				return item.toString();
			throw new IllegalArgumentException("Broken iterator; size returned 1 but no object was found");
		} else if (collection.size() == 2) {
			boolean obj1Found = false;
			T obj1 = null;
			T obj2 = null;
			for (T item : collection) {
				if (obj1Found) {
					obj2 = item;
				} else {
					obj1 = item;
					obj1Found = true;
				}
			}
			return obj1 + " and " + obj2;
		}

		StringBuilder builder = new StringBuilder();
		Iterator<T> iterator = collection.iterator();
		while (iterator.hasNext()) {
			T obj = iterator.next();
			boolean hasNext = iterator.hasNext();
			if (!hasNext) {
				builder.append("and ");
			}
			builder.append(mapper.apply(obj));
			if (hasNext)
				builder.append(", ");
		}
		return builder.toString();
	}

	// real class

	private static final Set<String> HOST_COMMANDS = Set.of(
			"start",
			"clear",
			"end",
			"guess",
			"remove"
	);
	private final JDA jda;
	private final Map<Long, GameData> gameDataMap = new HashMap<>();

	public Bot(String token) throws LoginException {
		jda = JDABuilder.createDefault(token)
				.setActivity(Activity.competing("Wikipedia"))
				.addEventListeners(this)
				.build();

		jda.updateCommands().addCommands(
				new CommandData("submit", "Enter in to a SOTPAL game by submitting a Wikipedia article subject")
						.addOption(
								OptionType.STRING,
								"title",
								"The subject of a Wikipedia article (the article title, minus clarifying parentheses)",
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

				new CommandData("remove", "Kick a user from the SOTPAL game")
						.addOption(
								OptionType.USER,
								"user",
								"The user to kick from the game",
								true
						),

				new CommandData("open", "Opens a new game of SOTPAL"),
				new CommandData("article", "Fetches the current SOTPAL article"),
				new CommandData("clear", "Clears the entered SOTPAL articles"),
				new CommandData("end", "Ends the SOTPAL game"),
				new CommandData("quit", "Retract your submission from the SOTPAL game")
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
				RoundData roundData = gameDataMap.get(guildId).getRound();
				final ReplyAction action;
				if (roundData != null) {
					action = event.reply("The current Wikipedia article is **" + roundData.articleTitle() + "**");
				} else {
					action = event.reply("A round has not yet started.");
				}

				action.setEphemeral(true).queue();
			}
			case "submit" -> {
				if (!Objects.requireNonNull(event.getMember().getVoiceState()).inAudioChannel()) {
					event.reply("You must be in a voice channel to use this command.").setEphemeral(true).queue();
					return;
				}
				String title = Objects.requireNonNull(event.getOption("title")).getAsString();
				if (title.startsWith("http://") || title.startsWith("https://")) {
					event.reply("Your submission should be the title of a Wikipedia article, not its URL.").setEphemeral(true).queue();
				} else {
					gameDataMap.get(guildId).updateArticle(memberId, title);
					String reply = "Your Wikipedia article has been set as or updated to **" + title + "**";
					if (title.contains("(") || title.contains(")"))
						reply = reply + "\n__**Warning:**__ It is generally not recommended to submit the part of article titles with parentheses.";
					event.reply(reply).setEphemeral(true).queue();
				}
			}
			case "clear" -> {
				gameDataMap.get(guildId).clearArticles();
				event.reply("All submitted Wikipedia articles have been cleared. Please re-enter them using `/submit`.").queue();
			}
			case "start" -> {
				GameData gameData = gameDataMap.get(guildId);
				gameData.clearObsolete();

				byte players = (byte) Objects.requireNonNull(event.getOption("players")).getAsLong();
				if (!gameData.canStartNewRound(players)) {
					event.reply("Not enough players have signed up for the game.").setEphemeral(true).queue();
					return;
				}
				RoundData data = gameData.newRound(players);
				String message = "A new round has started! Joining <@" +
						memberId +
						"> today is " +
						commaJoinCollection(data.playerIds(), id -> "<@" + id + ">") +
						". One of these people is going to be telling the truth about the contents of the Wikipedia article **" +
						data.articleTitle() +
						"** while the others will be lying.";
				event.reply(message).queue();
			}
			case "guess" -> {
				GameData gameData = gameDataMap.get(guildId);
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
				if (!data.playerIds().contains(guess)) {
					event.reply("That user is not participating in this round.").setEphemeral(true).queue();
					return;
				}
				gameData.guess();

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
			case "entrants" -> {
				Set<Long> contestants = gameDataMap.get(guildId).contestants();
				event.reply("The entered contestants are "
						+ commaJoinCollection(contestants, id -> "<@" + id + ">")
						+ " (" + contestants.size() + " people)"
				).setEphemeral(true).queue();
			}
			case "remove" -> {
				long target = Objects.requireNonNull(event.getOption("user")).getAsLong();
				if (gameDataMap.get(guildId).remove(target))
					event.reply("<@" + target + "> has been removed from the game.").queue();
				else
					event.reply("<@" + target + "> has not signed up to play.").setEphemeral(true).queue();
			}
			case "quit" -> {
				String reply;
				if (gameDataMap.get(guildId).remove(memberId))
					reply = "You have retracted your submission from the game.";
				else
					reply = "You have not signed up to play.";
				event.reply(reply).setEphemeral(true).queue();
			}
		}
	}
}
