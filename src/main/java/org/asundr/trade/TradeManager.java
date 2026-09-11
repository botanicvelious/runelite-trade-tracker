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

package org.asundr.trade;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import org.asundr.TradeTrackerConfig;
import org.asundr.recovery.ConfigKey;
import org.asundr.recovery.EventTradeHistoryProfileRestored;
import org.asundr.recovery.SaveManager;
import org.asundr.screenshot.ScreenshotUtils;
import org.asundr.ui.GuiUtils;
import org.asundr.utility.CommonUtils;
import org.asundr.utility.MathUtils;

import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
public class TradeManager
{
	public final static int MAX_HISTORY_COUNT = 1024;

	private static final String MESSAGE_ACCEPTED_TRADE = "Accepted trade.";
	private static final String MESSAGE_DECLINED_TRADE = "Other player declined trade.";
	private final static Pattern PATTERN_TRADE_USERNAME = Pattern.compile("^Trading [Ww]ith:\\s*(.*)$");
	private final static int CHILD_TRADE_USERNAME = 31;
	private final static int CHILD_TRADE_CONFIRMATION_USERNAME = 30;
	private final static TradeManager instance = new TradeManager();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private TradeData currentTrade = null;
	private ArrayDeque<TradeData> tradeHistory = new ArrayDeque<>();
	private TradeState tradeState = TradeState.NOT_TRADING;
	private ScheduledFuture<?> scheduledPurgeFuture = null;

	public static TradeManager getInstance()
	{
		return instance;
	}

	// returns a copy of the current trade history
	public static ArrayDeque<TradeData> getTradeHistory()
	{
		return new ArrayDeque<>(instance.tradeHistory);
	}

	// Overrides the current history
	private void setTradeHistory(ArrayDeque<TradeData> tradeHistory)
	{
		this.tradeHistory = tradeHistory;
		CommonUtils.postEvent(new EventTradeResetHistory(tradeHistory));
	}

	// get total number of recorded trades
	public static int getTradeHistoryCount()
	{
		return instance.tradeHistory.size();
	}

	public static void requestRemoveTradeRecord(TradeData tradeData)
	{
		instance.removeTradeRecord(tradeData);
	}

	public static void requestClearAllTradeRecords()
	{
		instance.clearAllTradeRecords();
	}

	// Returns true if expired trades are set to be auto-removed after they expire
	public static boolean isPurgingExpiredTrades()
	{
		Boolean isPurging = SaveManager.restoreFromKey(ConfigKey.SCHEDULED_PURGE);
		return isPurging != null && isPurging;
	}

	// Sets the active state of the auto-remove for expired trades, potentially starting or cancelling the timer
	public static void setPurgingExpiredTrades(boolean enabled)
	{
		SaveManager.saveWithKey(ConfigKey.SCHEDULED_PURGE, enabled);
		instance.updateRemoveExpiredRecordTimer();
	}

	@Subscribe
	private void onWidgetLoaded(final WidgetLoaded event)
	{
		final int groupId = event.getGroupId();
		if (groupId == TradeMenuId.TRADE_MENU)
		{
			if (currentTrade == null)
			{
				currentTrade = new TradeData();
			}
			setTradeState(TradeState.TRADING);
			fetchTradedPlayerName();
		} else if (groupId == TradeMenuId.TRADE_CONFIRMATION_MENU)
		{
			ScreenshotUtils.takeScreenshot();
			setTradeState(TradeState.TRADE_CONFIRMATION);
		}
	}

	@Subscribe
	private void onWidgetClosed(final WidgetClosed event)
	{
		final int groupId = event.getGroupId();
		if (groupId == TradeMenuId.TRADE_MENU)
		{
			CommonUtils.getClientThread().invokeLater(() ->
			{
				if (tradeState != TradeState.TRADE_CONFIRMATION)
				{
					setTradeState(TradeState.NOT_TRADING);
				}
			});
		} else if (groupId == TradeMenuId.TRADE_CONFIRMATION_MENU)
		{
			setTradeState(TradeState.NOT_TRADING);
		}
	}

	@Subscribe
	private void onItemContainerChanged(ItemContainerChanged event)
	{
		if (currentTrade == null)
		{
			return;
		}
		final int inventoryId = event.getContainerId();
		if (inventoryId == TradeContainerId.GIVEN)
		{
			currentTrade.updateItems(true, CommonUtils.getItemContainer(inventoryId));
		} else if (inventoryId == TradeContainerId.RECEIVED)
		{
			currentTrade.updateItems(false, CommonUtils.getItemContainer(inventoryId));
		}
	}

	@Subscribe
	private void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getType() != ChatMessageType.TRADE || currentTrade == null)
		{
			return;
		}
		switch (chatMessage.getMessage())
		{
			case MESSAGE_ACCEPTED_TRADE:
				if (CommonUtils.getConfig().ignoreEmptyTrades() && currentTrade.isEmpty())
				{
					return;
				}
				currentTrade.tradeTime = chatMessage.getTimestamp();
				ScreenshotUtils.saveScreenshot(currentTrade.tradedPlayer.tradeName);
				addTradeRecord(currentTrade);
				setTradeState(TradeState.TRADE_ACCEPTED);
				break;
			case MESSAGE_DECLINED_TRADE:
				setTradeState(TradeState.NOT_TRADING);
				ScreenshotUtils.clearScreenshot();
				break;
			default:
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged configChanged)
	{
		if (configChanged.getGroup().equals(SaveManager.SAVE_GROUP))
		{
			if (configChanged.getKey().equals(ConfigKey.MAX_HISTORY))
			{
				removeOverflowRecords(0);
			}
		}
	}

	@Subscribe
	private void onEventTradeHistoryProfileRestored(EventTradeHistoryProfileRestored e)
	{
		setTradeHistory(e.tradeHistory);
		updateRemoveExpiredRecordTimer();
	}

	// Should be called when plugin shuts down to cancel potentially scheduled purge timers
	public void shutdown()
	{
		scheduler.shutdown();
	}

	// Updates what stage of a trade the player is in, and fires relevant events
	private void setTradeState(TradeState newState)
	{
		if (tradeState == newState)
		{
			return;
		}
		switch (newState)
		{
			case TRADE_ACCEPTED:
				CommonUtils.getClientThread().invokeLater(() -> setTradeState(TradeState.NOT_TRADING));
				break;
			case TRADING:
				CommonUtils.postEvent(new EventTradeBegan(currentTrade == null ? null : currentTrade.tradedPlayer));
				break;
			case NOT_TRADING:
				if (tradeState != TradeState.TRADE_ACCEPTED)
				{
					CommonUtils.postEvent(new EventTradeDeclined(currentTrade == null ? null : currentTrade.tradedPlayer));
				}
				currentTrade = null;
				if (CommonUtils.getConfig().getAutoFilterOnTrade() != TradeTrackerConfig.AutoFilterOnTrade.NEVER)
				{
					GuiUtils.restoreFilterPostTrade();
				}
				break;
		}
		//log.debug(String.format("%s  --->  %s", tradeState, newState));
		tradeState = newState;
	}

	// Repeatedly tries to find the name of the traded player in the trade window, then updates the current trade data
	private void fetchTradedPlayerName()
	{
		if (tradeState != TradeState.NOT_TRADING && currentTrade != null && (currentTrade.tradedPlayer == null || currentTrade.tradedPlayer.isValid()))
		{
			TradePlayerData tradePlayerData;
			if (tradeState == TradeState.TRADING)
			{
				tradePlayerData = new TradePlayerData(CommonUtils.extractPatternFromWidget(TradeMenuId.TRADE_MENU, CHILD_TRADE_USERNAME, PATTERN_TRADE_USERNAME));
			} else
			{
				tradePlayerData = new TradePlayerData(CommonUtils.extractPatternFromWidget(TradeMenuId.TRADE_CONFIRMATION_MENU, CHILD_TRADE_CONFIRMATION_USERNAME, PATTERN_TRADE_USERNAME));
			}
			if (tradePlayerData.isValid())
			{
				CommonUtils.getClientThread().invokeLater(this::fetchTradedPlayerName);
			} else
			{
				currentTrade.tradedPlayer = tradePlayerData;
				if (CommonUtils.getConfig().getAutoFilterOnTrade() != TradeTrackerConfig.AutoFilterOnTrade.NEVER)
				{
					GuiUtils.filterOnTrade(currentTrade);
				}
			}
		}
	}

	// Adds passed trade data as new trade to history, fetching relevant data, removing overflow trades, firing traded added events and saving the updated history
	// This is the function to call to add any new trades to the history
	private void addTradeRecord(TradeData tradeData)
	{
		removeOverflowRecords(1);
		tradeHistory.addLast(tradeData);
		CommonUtils.getClientThread().invokeLater(() ->
		{
			TradeUtils.fetchCompositionData(tradeData.givenItems);
			TradeUtils.fetchCompositionData(tradeData.receivedItems);
			TradeUtils.fetchGePrices(tradeData.givenItems);
			TradeUtils.fetchGePrices(tradeData.receivedItems);
			tradeData.calculateAggregateValues();
			CommonUtils.postEvent(new EventTradeAdded(tradeData));
			SaveManager.requestTradeHistorySave();
			if (tradeHistory.size() == 1)
			{
				updateRemoveExpiredRecordTimer();
			}
		});
	}

	// Removes the passed trade data if it is found inn the history, and fires a corresponding event
	private void removeTradeRecord(TradeData tradeData)
	{
		tradeHistory.removeIf(e -> e.tradeTime == tradeData.tradeTime);
		CommonUtils.postEvent(new EventTradeRemoved(tradeData));
		SaveManager.requestTradeHistorySave();
		if (!tradeHistory.isEmpty())
		{
			updateRemoveExpiredRecordTimer();
		}
	}

	// Removes all trades from the current history
	private void clearAllTradeRecords()
	{
		tradeHistory.clear();
		CommonUtils.postEvent(new EventTradeResetHistory(tradeHistory));
		SaveManager.requestTradeHistorySave();
	}

	// Removes the oldest trades in excess of the user-specified max history count
	// extra param is useful to preemptively remove if you know you're going to add another record
	private void removeOverflowRecords(int extra)
	{
		final int maxRecords = MathUtils.clamp(CommonUtils.getConfig().maxHistoryCount(), 1, TradeManager.MAX_HISTORY_COUNT);
		final int overflow = Math.min(tradeHistory.size() - maxRecords + extra, Math.max(0, tradeHistory.size() - 1));
		removeOldestRecords(overflow);
	}

	// Called whenever the timer to purge expired trades needs to be changed or cancelled
	private void updateRemoveExpiredRecordTimer()
	{
		if (CommonUtils.isThreadActive(scheduledPurgeFuture))
		{
			//log.debug("Cancelled scheduled removal of expired trade.");
			scheduledPurgeFuture.cancel(false);
		}
		if (tradeHistory.isEmpty())
		{
			//log.debug("No trade set to expire.");
			return;
		}
		if (!isPurgingExpiredTrades())
		{
			//log.debug("Removing expired records currently disabled");
			return;
		}
		final long lifetime = CommonUtils.getRecordLifetime();
		if (lifetime <= 0L)
		{
			//log.debug("No trade set to expire.");
			return;
		}
		final long expireTime = tradeHistory.getFirst().tradeTime * 1000L + lifetime;
		final long destroyDelay = Math.max(1000, expireTime - System.currentTimeMillis());
		scheduledPurgeFuture = scheduler.schedule(this::removeExpiredRecords, destroyDelay, TimeUnit.MILLISECONDS);
		//log.debug("Scheduled to remove expired trade at: " + TradeUtils.timeStampToString(expireTime/1000));
	}

	// Removes all trades from history that are older than the user-configured lifetime
	private void removeExpiredRecords()
	{
		final long lifetime = CommonUtils.getRecordLifetime();
		if (lifetime <= 0L)
		{
			return;
		}
		while (!tradeHistory.isEmpty() && tradeHistory.getFirst().isExpired())
		{
			removeOldestRecords(1);
		}
		updateRemoveExpiredRecordTimer();
	}

	// Removes the passed number of oldest trades from the history
	private void removeOldestRecords(int count)
	{
		count = Math.min(count, tradeHistory.size());
		while (count > 0)
		{
			CommonUtils.postEvent(new EventTradeRemoved(tradeHistory.getFirst()));
			tradeHistory.removeFirst();
			--count;
		}
	}

	public enum TradeState
	{
		NOT_TRADING,
		TRADING,
		TRADE_CONFIRMATION,
		TRADE_ACCEPTED
	}

	private static final class TradeMenuId
	{
		public static final int TRADE_MENU = 335;
		public static final int TRADE_CONFIRMATION_MENU = 334;
	}

	private static final class TradeContainerId
	{
		public static final int GIVEN = InventoryID.TRADEOFFER;
		public static final int RECEIVED = InventoryID.TRADEOFFER | 0x8000;
	}

}
