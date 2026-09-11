/*
 * Copyright (c) 2025, Arun <trade-tracker-plugin.acwel@dralias.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.asundr;

import net.runelite.client.config.*;
import org.asundr.recovery.ConfigKey;

import static org.asundr.recovery.SaveManager.SAVE_GROUP;
import static org.asundr.trade.TradeManager.MAX_HISTORY_COUNT;

@ConfigGroup(SAVE_GROUP)
public interface TradeTrackerConfig extends Config
{
	@ConfigSection(
			name = "General",
			description = "General settings",
			position = 0
	)
	String SECTION_GENERAL = "general";

	@ConfigSection(
			name = "Display",
			description = "Change the plugin's visuals",
			position = 1
	)
	String SECTION_DISPLAY = "display";

	@ConfigSection(
			name = "History Limits",
			description = "Settings to manage when the trade history culls old trades",
			position = 2
	)
	String SECTION_HISTORY_LIMITS = "historyLimits";

	@ConfigSection(
			name = "Player lookup",
			description = "Options to enable player menu options",
			position = 3
	)
	String SECTION_PLAYER_LOOKUP = "player_lookup";

	@ConfigSection(
			name = "Miscellaneous",
			description = "Additional features",
			position = 4
	)
	String SECTION_MISCELLANEOUS = "miscellaneous";

	@ConfigSection(
			name = "Debug",
			description = "For advanced users or submitting bug reports",
			position = 10,
			closedByDefault = true
	)
	String SECTION_DEBUG = "Debug";


///////////////////////////////

	@ConfigItem(
			keyName = ConfigKey.AUTOLOAD_LAST_PROFILE,
			name = "Auto-load profile on launch",
			description = "If enabled, the last trade profile will be visible on the login screen when RuneLite is launched",
			section = SECTION_GENERAL
	)
	default boolean getAutoLoadLastProfile()
	{
		return true;
	}

	@ConfigItem(
			keyName = ConfigKey.AUTO_FILTER_ON_TRADE,
			name = "Auto-filter trade",
			description = "When a trade starts, determines how the history is auto-filtered using the traded player's name." +
					"<br>- Never: Never auto-filters during trades" +
					"<br>- Always - Auto-filter if the player has been traded before" +
					"<br>- Empty: Only auto-filter if the filter text is empty" +
					"<br>- Inactive: Only auto-filter if the filter is currently disabled" +
					"<br>- Inactive Empty: Only auto-filter if the filter is both empty and disabled",
			section = SECTION_GENERAL,
			position = -10
	)
	default AutoFilterOnTrade getAutoFilterOnTrade()
	{
		return AutoFilterOnTrade.NEVER;
	}

	@ConfigItem(
			keyName = ConfigKey.AUTO_FILTER_OPENS_PANEL,
			name = "Auto-filter opens panel",
			description = "If auto-filter enabled, will open the side bar and switch to the Trade Tracker panel tab",
			section = SECTION_GENERAL,
			position = -9
	)
	default boolean autoFilterOpensPanel()
	{
		return true;
	}

	@ConfigItem(
			keyName = ConfigKey.SCREENSHOT_ON_TRADE,
			name = "Screenshot on trade",
			description = "If enabled, a screenshot of the trade is also saved as an image to disk",
			section = SECTION_GENERAL
	)
	default boolean getScreenshotOnTrade()
	{
		return false;
	}

	@ConfigItem(
			keyName = ConfigKey.USE_24_HOUR_TIME,
			name = "Display 24-hour time",
			description = "If enabled, displays 13:00 instead of 1:00 pm",
			section = SECTION_DISPLAY
	)
	default boolean use24HourTime()
	{
		return false;
	}

	@ConfigItem(
			keyName = ConfigKey.DEFAULT_PRICE_TYPE,
			name = "Price type",
			description = "Uses the set price source to calculate aggregate values",
			section = SECTION_DISPLAY
	)
	default ItemPriceType getDefaultPriceType()
	{
		return ItemPriceType.GRAND_EXCHANGE;
	}

	@ConfigItem(
			keyName = ConfigKey.IGNORE_EMPTY_TRADES,
			name = "Ignore empty trades",
			description = "<html><span>If enabled, accepted trades with no items given or received are not tracked.</span><br><span>Setting to false does not clear exiting empty trades.</span>",
			section = SECTION_GENERAL
	)
	default boolean ignoreEmptyTrades()
	{
		return false;
	}

	@ConfigItem(
			keyName = ConfigKey.FILTER_ITEM_ID,
			name = "Filter matches for Item ID",
			description = "When filtering the trade history, item IDs will also be checked for a match",
			section = SECTION_DEBUG
	)
	default boolean filterMatchItemId()
	{
		return false;
	}

	@ConfigItem(
			keyName = ConfigKey.COPY_TRADE_DATE_MENU,
			name = "Enable copy trade data",
			description = "<html><span>Adds ability to copy trade data by right clicking on trade record</span><br><span>May require restarting RuneLite</span>",
			section = SECTION_DEBUG
	)
	default boolean canCopyTradeData()
	{
		return false;
	}

	@Range(
			min = 1, max = MAX_HISTORY_COUNT
	)
	@ConfigItem(
			keyName = ConfigKey.MAX_HISTORY,
			name = "Maximum trade history",
			description = "<html><span>Maximum number of trade records before the oldest is deleted</span><br><span>Valid range: [1, " + MAX_HISTORY_COUNT + "]",
			section = SECTION_HISTORY_LIMITS,
			position = 1
	)
	default int maxHistoryCount()
	{
		return 256;
	}

	@ConfigItem(
			keyName = ConfigKey.PURGE_HISTORY_TYPE,
			name = "Auto-remove type",
			description = "When should older trades be removed from the history?",
			section = SECTION_HISTORY_LIMITS,
			position = 2
	)
	default PurgeHistoryType getPurgeHistoryType()
	{
		return PurgeHistoryType.YEAR;
	}

	@Range(
	)
	@ConfigItem(
			keyName = ConfigKey.PURGE_HISTORY_MAGNITUDE,
			name = "Auto-remove length",
			description = "After how many of the 'Auto-remove type' should old trades be removed?",
			section = SECTION_HISTORY_LIMITS,
			position = 3
	)
	default int getPurgeHistoryMagnitude()
	{
		return 1;
	}

	@ConfigItem(
			keyName = ConfigKey.PLAYER_LOOKUP_MENU,
			name = "Enable on menus",
			description = "Add option to filter by player name when right-clicking their chat messages and friends list",
			section = SECTION_PLAYER_LOOKUP,
			position = 1
	)
	default LookupType getPlayerLookupMenu()
	{
		return LookupType.REQUIRE_SHIFT;
	}

	@ConfigItem(
			keyName = ConfigKey.PLAYER_LOOKUP_CHARACTER,
			name = "Enable on player",
			description = "Add option to filter by player name when right-clicking a player",
			section = SECTION_PLAYER_LOOKUP,
			position = 0
	)
	default LookupType getPlayerLookupCharacter()
	{
		return LookupType.REQUIRE_SHIFT;
	}

	@ConfigItem(
			keyName = ConfigKey.ADD_NAME_TO_TRADE_OFFER,
			name = "Player name in trade offer",
			description = "Shows the name of the player you traded in the \"Sending trade offer...\" chat message",
			section = SECTION_MISCELLANEOUS,
			position = 0
	)
	default boolean addNameToTradeOfferChat()
	{
		return true;
	}

	enum AutoFilterOnTrade
	{
		NEVER, //
		ALWAYS,
		EMPTY,
		INACTIVE,
		INACTIVE_EMPTY //
	}

	enum ItemPriceType
	{
		LOW_ALCHEMY("LA", "Low Alchemy"),
		HIGH_ALCHEMY("HA", "High Alchemy"),
		GRAND_EXCHANGE("GE", "Grand Exchange");

		public final String shortName;
		public final String fullName;
		ItemPriceType(final String shortName, final String fullName)
		{
			this.shortName = shortName;
			this.fullName = fullName;
		}
	}

	enum PurgeHistoryType
	{
		NEVER(Long.MAX_VALUE),
		MINUTE(60000L),
		HOUR(MINUTE.ms * 60L),
		DAY(HOUR.ms * 24L),
		YEAR(DAY.ms * 365L);
		public final long ms;

		PurgeHistoryType(long ms)
		{
			this.ms = ms;
		}
	}

	enum LookupType
	{
		DISABLED,
		ENABLED,
		REQUIRE_SHIFT
	}

}
