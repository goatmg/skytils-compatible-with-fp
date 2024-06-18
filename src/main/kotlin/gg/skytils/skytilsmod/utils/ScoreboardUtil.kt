/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2020-2023 Skytils
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package gg.skytils.skytilsmod.utils

import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import net.minecraft.client.Minecraft
import net.minecraft.scoreboard.Score
import gg.skytils.skytilsmod.Skytils.Companion.mc
import net.minecraft.scoreboard.ScoreObjective
import net.minecraft.scoreboard.ScorePlayerTeam
import net.minecraft.scoreboard.Scoreboard
import net.minecraft.util.StringUtils
import java.util.ArrayList
import java.util.stream.Collectors

/**
 * Taken from https://gist.github.com/aaron1998ish/33c4e1836bd5cf79501d163a1b5c8304
 */
object ScoreboardUtil {

    @JvmStatic
    fun cleanSB(scoreboard: String): String {
        return scoreboard.stripControlCodes().toCharArray().filter { it.code in 32..126 }.joinToString(separator = "")
    }

    var sidebarLines: List<String> = emptyList()

    fun fetchScoreboardLines(): List<String> {
        val scoreboard = mc.theWorld?.scoreboard ?: return emptyList()
        val objective = scoreboard.getObjectiveInDisplaySlot(1) ?: return emptyList()
        val scores = scoreboard.getSortedScores(objective).filter { input: Score? ->
            input != null && input.playerName != null && !input.playerName
                .startsWith("#")
        }.take(15)
        return scores.map {
            ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(it.playerName), it.playerName)
        }.asReversed()
    }

    fun getSidebarLinesList(): List<String> {
        val lines = mutableListOf<String>()
        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld

        if (world == null) {
            return lines
        }

        val scoreboard: Scoreboard? = world.scoreboard
        if (scoreboard == null) {
            return lines
        }

        val objective: ScoreObjective? = scoreboard.getObjectiveInDisplaySlot(1)
        if (objective == null) {
            return lines
        }

        val scores: Collection<Score> = scoreboard.getSortedScores(objective)
        val list: List<Score> = scores.stream()
            .filter { input -> input != null && input.playerName != null && !input.playerName.startsWith("#") }
            .collect(Collectors.toList())

        val limitedScores = if (list.size > 15) {
            Lists.newArrayList(Iterables.skip(list, scores.size - 15))
        } else {
            list
        }

        for (score in limitedScores) {
            val team: ScorePlayerTeam? = scoreboard.getPlayersTeam(score.playerName)
            lines.add(ScorePlayerTeam.formatPlayerName(team, score.playerName))
        }

        return lines
    }


}