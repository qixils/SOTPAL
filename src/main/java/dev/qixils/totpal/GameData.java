package dev.qixils.totpal;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public final class GameData {
	private static final Random RNG = new Random();
	private final Map<Long, String> articleTitles = new Long2ObjectOpenHashMap<>();
	private final JDA jda;
	private final long host;
	private final long guild;
	private RoundData roundData;
	private boolean guessed;

	public GameData(JDA jda, long host, long guild) {
		this.jda = jda;
		this.host = host;
		this.guild = guild;
	}

	public boolean isHost(long id) {
		return host == id;
	}

	public boolean isHost(ISnowflake snowflake) {
		return snowflake.getIdLong() == host;
	}

	public void updateArticle(long user, String articleName) {
		articleTitles.put(user, articleName);
	}

	public void clearArticles() {
		articleTitles.clear();
	}

	@Nullable
	public RoundData getRound() {
		return roundData;
	}

	public boolean canStartNewRound(byte players) {
		return articleTitles.size() >= players;
	}

	@NotNull
	public RoundData newRound(byte players) {
		if (players < articleTitles.size()) {
			throw new IllegalArgumentException("Not enough players have signed up for this game");
		}
		guessed = false;
		List<Entry<Long, String>> randomPlayers = new ArrayList<>(articleTitles.entrySet());
		Collections.shuffle(randomPlayers, RNG);
		randomPlayers = randomPlayers.subList(0, Math.min(players, randomPlayers.size()));
		Entry<Long, String> selection = randomPlayers.get(RNG.nextInt(randomPlayers.size()));
		articleTitles.remove(selection.getKey(), selection.getValue());
		Set<Long> playerIds = randomPlayers.stream().map(Entry::getKey).collect(Collectors.toUnmodifiableSet());
		return roundData = new RoundData(jda, guild, selection.getKey(), selection.getValue(), playerIds);
	}

	public boolean hasGuessed() {
		return guessed;
	}

	public void guess() {
		if (guessed)
			throw new IllegalStateException("Host has already guessed");
		guessed = true;
	}
}
