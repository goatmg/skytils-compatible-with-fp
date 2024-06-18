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

package gg.skytils.skytilsmod.features.impl.handlers

import java.awt.*
import java.util.ArrayList
import java.util.List
import java.util.Objects
import java.util.regex.Matcher
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.core.PersistentSave
import gg.skytils.skytilsmod.events.impl.GuiContainerEvent
import gg.skytils.skytilsmod.listeners.DungeonListener
import gg.skytils.skytilsmod.utils.RenderUtil.highlight
import gg.skytils.skytilsmod.utils.ScoreboardUtil
import gg.skytils.skytilsmod.utils.Utils
import gg.skytils.skytilsmod.utils.graphics.ScreenRenderer
import gg.skytils.skytilsmod.utils.graphics.SmartFontRenderer
import gg.skytils.skytilsmod.utils.stripControlCodes
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.entity.RenderItem
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.inventory.ContainerChest
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.item.Item
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.util.StringUtils
import net.minecraftforge.client.event.GuiScreenEvent
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import skytils.hylin.skyblock.dungeons.DungeonClass
import java.io.File
import java.awt.Color
import java.lang.reflect.Field
import java.util.regex.Pattern
import java.io.Reader
import java.io.Writer
import java.util.*

object SpiritLeap : PersistentSave(File(Skytils.modDir, "spiritleap.json")) {

    private val playerPattern: Pattern = Pattern.compile("(?:\\[.+?] )?(\\w+)")
    private val doorOpenedPattern = Regex("^(?:\\[.+] )?(?<name>\\w+) opened a WITHER door!$")
    private const val bloodOpenedString = "§cThe §c§lBLOOD DOOR§c has been opened!"
    private var doorOpener: String? = null
    val names = HashMap<String, Boolean>()
    val classes = DungeonClass.values()
        .associateWithTo(EnumMap(DungeonClass::class.java)) { false }
    private val shortenedNameCache = WeakHashMap<String, String>()
    private val nameSlotCache = HashMap<Int, String>()

    @SubscribeEvent
    fun onGuiDrawPost(event: GuiScreenEvent.DrawScreenEvent.Post) {
        if (!Utils.inSkyblock) return
        if (event.gui is GuiChest) {
            val inventory = event.gui as GuiChest
            val containerChest = inventory.inventorySlots
            if (containerChest is ContainerChest) {
                val sr = ScaledResolution(mc)
                val fr = mc.fontRendererObj
                val guiLeft = (sr.scaledWidth - 176) / 2
                val guiTop = (sr.scaledHeight - 222) / 2

                val invSlots = inventory.inventorySlots.inventorySlots
                val displayName = containerChest.lowerChestInventory.displayName.unformattedText.trim()
                val chestSize = inventory.inventorySlots.inventorySlots.size

                if (Utils.inDungeons && ((Skytils.config.spiritLeapNames && displayName == "Spirit Leap") || (Skytils.config.spiritLeapNames && displayName == "Teleport to a teammate") || (Skytils.config.reviveStoneNames && displayName == "Revive a teammate"))) {
                    var people = 0
                    for (slot in invSlots) {
                        if (slot.inventory === mc.thePlayer.inventory) continue
                        if (slot.hasStack) {
                            val item = slot.stack
                            if (item.item === Items.skull) {
                                people++

                                //slot is 16x16
                                var x = guiLeft + slot.xDisplayPosition + 8
                                var y = guiTop + slot.yDisplayPosition
                                // Move down when chest isn't 6 rows
                                if (chestSize != 90) y += (6 - (chestSize - 36) / 9) * 9

                                if (people % 2 != 0) {
                                    y -= 15
                                } else {
                                    y += 20
                                }

                                val matcher = playerPattern.matcher(StringUtils.stripControlCodes(item.displayName))
                                if (!matcher.find()) continue
                                val name = matcher.group(1)
                                if (name == "Unknown") continue
                                var dungeonClass = ""
                                for (l in ScoreboardUtil.getSidebarLinesList()) {
                                    val line = ScoreboardUtil.cleanSB(l)
                                    if (line.contains(name)) {
                                        dungeonClass = line.substring(line.indexOf("[") + 1, line.indexOf("]"))
                                        break
                                    }
                                }
                                val text = fr.trimStringToWidth(item.displayName.substring(0, 2) + name, 32)
                                x -= fr.getStringWidth(text) / 2

                                var shouldDrawBkg = true
                                if (Skytils.usingNEU && displayName != "Revive a teammate") {
                                    try {
                                        val neuClass = Class.forName("io.github.moulberry.notenoughupdates.NotEnoughUpdates")
                                        val neuInstance = neuClass.getDeclaredField("INSTANCE")
                                        val neu = neuInstance.get(null)
                                        val neuConfig = neuClass.getDeclaredField("config")
                                        val config = neuConfig.get(neu)
                                        val improvedSBMenu = config.javaClass.getDeclaredField("improvedSBMenu")
                                        val improvedSBMenuS = improvedSBMenu.get(config)
                                        val enableSbMenus = improvedSBMenuS.javaClass.getDeclaredField("enableSbMenus")
                                        val customGuiEnabled = enableSbMenus.getBoolean(improvedSBMenuS)
                                        if (customGuiEnabled) shouldDrawBkg = false
                                    } catch (ignored: ClassNotFoundException) {
                                    } catch (ignored: NoSuchFieldException) {
                                    } catch (ignored: IllegalAccessException) {
                                    }
                                }

                                val scale = 0.9f
                                val scaleReset = 1 / scale
                                GlStateManager.disableLighting()
                                GlStateManager.disableDepth()
                                GlStateManager.disableBlend()
                                GlStateManager.translate(0.0, 0.0, 1.0)
                                if (shouldDrawBkg)
                                    Gui.drawRect(x - 2, y - 2, x + fr.getStringWidth(text) + 2, y + fr.FONT_HEIGHT + 2, Color(47, 40, 40).rgb)
                                fr.drawStringWithShadow(text, x.toFloat(), y.toFloat(), Color(255, 255, 255).rgb)
                                GlStateManager.scale(scale.toDouble(), scale.toDouble(), scale.toDouble())
                                fr.drawString(dungeonClass, (scaleReset * (x + 7)).toFloat(), (scaleReset * (guiTop + slot.yDisplayPosition + 18)).toFloat(), Color(255, 255, 0).rgb, true)
                                GlStateManager.scale(scaleReset, scaleReset, scaleReset)
                                GlStateManager.translate(0.0, 0.0, -1.0)
                                GlStateManager.enableLighting()
                                GlStateManager.enableDepth()
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onDrawSlot(event: GuiContainerEvent.DrawSlotEvent.Pre) {
        if (!Utils.inSkyblock) return
        val slot = event.slot
        if (event.container is ContainerChest) {
            val cc = event.container as ContainerChest
            val displayName = cc.lowerChestInventory.displayName.unformattedText.trim()
            if (slot.hasStack) {
                val item = slot.stack
                if (Skytils.config.spiritLeapNames && displayName == "Spirit Leap") {
                    if (item.item === Item.getItemFromBlock(Blocks.stained_glass_pane)) {
                        event.isCanceled = true
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onChatReceived(event: ClientChatReceivedEvent) {
        if (!Skytils.config.highlightDoorOpener || !Utils.inDungeons || event.type == 2.toByte()) return
        doorOpener = if (event.message.formattedText == bloodOpenedString) null
        else (doorOpenedPattern.find(event.message.unformattedText)?.groups?.get("name")?.value ?: return)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        nameSlotCache.clear()
    }

    override fun read(reader: Reader) {
        val data = json.decodeFromString<SaveData>(reader.readText())
        names.putAll(data.users.entries.associate { it.key to it.value.enabled })
        data.classes.forEach { (clazz, state) ->
            classes[clazz] = state.enabled
        }
    }

    override fun write(writer: Writer) {
        writer.write(
            json.encodeToString(
                SaveData(
                    names.entries.associate { it.key to SaveComponent(it.value) },
                    classes.entries.associate { it.key to SaveComponent(it.value) })
            )
        )
    }

    override fun setDefault(writer: Writer) {
        write(writer)
    }
}

@Serializable
private data class SaveData(val users: Map<String, SaveComponent>, val classes: Map<DungeonClass, SaveComponent>)

@Serializable
private data class SaveComponent(val enabled: Boolean)