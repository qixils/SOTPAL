package dev.qixils.totpal;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.Collection;

public record RoundData(JDA jda, long guildId, long trutherId, String articleTitle, Collection<Long> playerIds) {
	public Guild guild() {
		return jda.getGuildById(guildId);
	}

	public Member truther() {
		return guild().getMemberById(guildId);
	}

	public Collection<Member> players() {
		Guild guild = guild();
		return playerIds.stream().map(guild::getMemberById).toList();
	}
}
