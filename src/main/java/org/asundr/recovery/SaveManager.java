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

package org.asundr.recovery;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import org.asundr.TradeHistoryProfile;
import org.asundr.trade.TradeData;
import org.asundr.trade.TradeManager;
import org.asundr.utility.CommonUtils;
import org.asundr.utility.StringUtils;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class SaveManager
{
	public static final String REGEX_EMPTY_NOTES = ",\"note\":(?:null|\"\")";
	public static final int SAVE_VERSION = 2; // This should increase whenever save data or method changes
	public static final String SAVE_GROUP = "TradeTracker";
	private static final String DEFAULT_SAVE_FILENAME = "profile";
	private static final AtomicInteger tradeHistorySaveState = new AtomicInteger(SaveState.INACTIVE); // flags for trade history save operation
	private static final AtomicInteger tradeHistoryLoadState = new AtomicInteger(SaveState.INACTIVE); // flags for trade history load operation
	private static ConfigManager configManager;
	private static SaveData_Common saveDataCommon;
	private static ExecutorService ioExecutor = null;
	private static Future<?> ioExecutorFuture = null;
	private Player currentPlayer;
	private String playerName;
	@Inject
	@Getter
	private Client client;

	private boolean LoggedIn = false;

	// Sets the active profile to null without updating the UI or saving
	public static void forgetActiveHistoryProfile()
	{
		saveDataCommon.setActiveProfile(null);
	}

	public static void initialize(ConfigManager configManager)
	{
		SaveManager.configManager = configManager;

		SaveManager.ioExecutor = Executors.newFixedThreadPool(1);
		SaveManager.ioExecutorFuture = null;
		SaveManager.tradeHistorySaveState.set(SaveState.INACTIVE);
		SaveManager.tradeHistoryLoadState.set(SaveState.INACTIVE);
	}

	public static void shutdown()
	{
		if (hasFlag(tradeHistorySaveState, SaveState.ACTIVE))
		{
			ioExecutor.shutdown();
		} else
		{
			ioExecutor.shutdownNow();
		}
	}

	private static SaveData_Common getSaveDataCommon()
	{
		if (saveDataCommon == null)
		{
			saveDataCommon = new SaveData_Common();
		}
		return saveDataCommon;
	}

	public static TradeHistoryProfile getActiveProfile()
	{
		return saveDataCommon == null ? null : saveDataCommon.getActiveProfile();
	}

	public static boolean isSaving()
	{
		return tradeHistorySaveState.get() != 0;
	}

	// serializes and saves common data
	private static void saveCommonData()
	{
		Gson gson = StringUtils.getGsonBuilder();
		String json = gson.toJson(getSaveDataCommon());
		configManager.setConfiguration(SAVE_GROUP, ConfigKey.COMMON, json);
	}

	// Converts the active profile's trade history to a json string, or null if there is not a populated history
	public static String getTradeHistoryAsJson()
	{
		if (saveDataCommon == null || saveDataCommon.getActiveProfile() == null)
		{
			return null;
		}
		final ArrayDeque<TradeData> tradeHistory = TradeManager.getTradeHistory();
		if (tradeHistory.isEmpty())
		{
			return null;
		}
		final Gson gson = StringUtils.getGsonBuilder();
		String historyJson = gson.toJson(tradeHistory);
		historyJson = historyJson.replaceAll(REGEX_EMPTY_NOTES, ""); // remove empty notes
		final SaveData_Profile saveData = new SaveData_Profile(
				SaveManager.saveDataCommon.getActiveProfile().getKeyString(),
				CompressionUtils.compressToEncode(historyJson));
		return gson.toJson(saveData);
	}

	// Saves the current trade history to the config using the profile's hash and account type as a key
	private static void saveTradeHistoryData()
	{
		toggleFlag(tradeHistorySaveState); // removed requested, add enabled active
		try
		{
			final String json = getTradeHistoryAsJson();
			if (saveDataCommon == null || saveDataCommon.getActiveProfile() == null)
			{
				return;
			}
			if (json == null)
			{
				configManager.unsetConfiguration(SAVE_GROUP, saveDataCommon.getActiveProfile().getKeyString());
				return;
			}
			configManager.setConfiguration(SAVE_GROUP, SaveManager.saveDataCommon.getActiveProfile().getKeyString(), json);
		} finally
		{
			clearFlag(tradeHistorySaveState);
			ioExecutorFuture = null;
		}
	}

	// Reads the common save data from config and sets that as the active save data
	public static void restoreCommonData()
	{
		final Gson gson = StringUtils.getGsonBuilder();
		final String json = configManager.getConfiguration(SAVE_GROUP, ConfigKey.COMMON);
		if (json == null || json.isBlank())
		{
			saveDataCommon = new SaveData_Common();
			return;
		}
		saveDataCommon = gson.fromJson(json, SaveData_Common.class);
	}

	// Restores the history of trades from the config entry associated with the currently active account
	private static void restoreTradeHistoryData()
	{
		if (saveDataCommon == null || saveDataCommon.getActiveProfile() == null)
		{
			return;
		}
		toggleFlag(tradeHistoryLoadState);
		final String json = configManager.getConfiguration(SAVE_GROUP, saveDataCommon.getActiveProfile().getKeyString());
		restoreTradeHistoryDataFromJson(json);
		clearFlag(tradeHistoryLoadState);
		ioExecutorFuture = null;
	}

	// Restores the trade history using a json string serialized from SaveData_Profile
	private static void restoreTradeHistoryDataFromJson(final String json)
	{
		final String profileKey = getSaveDataCommon().getActiveProfile() == null ? null : saveDataCommon.getActiveProfile().getKeyString();
		if (json == null || json.isEmpty())
		{
			CommonUtils.postEvent(new EventTradeHistoryProfileRestored(profileKey, new ArrayDeque<>()));
			return;
		}
		final Type dequeType = new TypeToken<ArrayDeque<TradeData>>()
		{
		}.getType();
		try
		{
			final Gson gson = StringUtils.getGsonBuilder();
			final SaveData_Profile saveData = gson.fromJson(json, SaveData_Profile.class);
			if (saveData == null)
			{
				log.error("Failed to get trade history json");
				return;
			}
			String decompressedHistory = CompressionUtils.decompressFromEncode(saveData.encodedTradeHistory);
			if (saveData.saveVersion == 1)
			{
				decompressedHistory = SaveUpgradeUtils.version1to2json(decompressedHistory);
			}
			CommonUtils.postEvent(new EventTradeHistoryProfileRestored(
					profileKey,
					gson.fromJson(decompressedHistory, dequeType)
			));
		} catch (Exception e)
		{
			log.error("Failed to parse trade history json");
		}
	}

	// Repeatedly attempts to start a new save or load thread while a queued save or load is pending
	private static void scheduleRecoveryOperation()
	{
		if (!CommonUtils.isThreadActive(ioExecutorFuture) && !hasFlag(tradeHistorySaveState, SaveState.ACTIVE) && !hasFlag(tradeHistoryLoadState, SaveState.ACTIVE))
		{
			if (tradeHistorySaveState.get() == SaveState.REQUESTED)
			{
				ioExecutorFuture = ioExecutor.submit(SaveManager::saveTradeHistoryData);
			} else if (tradeHistoryLoadState.get() == SaveState.REQUESTED)
			{
				ioExecutorFuture = ioExecutor.submit(SaveManager::restoreTradeHistoryData);
			}
		} else if (hasFlag(tradeHistorySaveState, SaveState.REQUESTED) || hasFlag(tradeHistoryLoadState, SaveState.REQUESTED))
		{
			CommonUtils.getClientThread().invokeLater(SaveManager::scheduleRecoveryOperation);
		}
	}

	// The public method that should be called to reload from the current trade history profile
	public static void requestRestoreTradeHistory()
	{
		setFlag(tradeHistoryLoadState);
		CommonUtils.getClientThread().invokeLater(SaveManager::scheduleRecoveryOperation);
	}

	// The public method that should be called to save the current trade history
	public static void requestTradeHistorySave()
	{
		if (tradeHistoryLoadState.get() > 0)
		{
			return;
		}
		setFlag(tradeHistorySaveState);
		CommonUtils.getClientThread().invokeLater(SaveManager::scheduleRecoveryOperation);
	}

	// Saves to the plugin's default group with the passed key
	public static void saveWithKey(final String key, Object data)
	{
		final Gson gson = StringUtils.getGsonBuilder();
		configManager.setConfiguration(SAVE_GROUP, key, gson.toJson(data));
	}

	// Restores from the plugin's default group with the passed key.
	// Return type matches that of the variable this function output is assigned to.
	public static <T> T restoreFromKey(final String key)
	{
		return restoreFromKey(SAVE_GROUP, key);
	}

	public static <T> T restoreFromKey(final String group, final String key)
	{
		final Gson gson = StringUtils.getGsonBuilder();
		final String value = configManager.getConfiguration(group, key);
		if (value == null)
		{
			return null;
		}
		return gson.fromJson(value, new TypeToken<T>()
		{
		}.getType());
	}

	// Exports the active profile's trade history profile as json to the file specified by the user
	public static void saveTradeHistoryToFile()
	{
		final String json = getTradeHistoryAsJson();
		if (json != null)
		{
			FileUtils.writeStringToFile(saveDataCommon.getActiveProfile().getKeyString(), json);
		}
	}

	// Imports a trade history profile to the active profile from a file specified by the user
	public static void loadTradeHistoryFromFile()
	{
		String filename = DEFAULT_SAVE_FILENAME;
		if (saveDataCommon != null && saveDataCommon.getActiveProfile() != null)
		{
			filename = saveDataCommon.getActiveProfile().getKeyString();
		}
		final String json = FileUtils.readStringFromFile(filename);
		if (json == null || json.isBlank())
		{
			return;
		}
		restoreTradeHistoryDataFromJson(json);
	}

	// Operations for setting and queries flags set on an atomic integer
	private static void toggleFlag(final AtomicInteger mask)
	{
		mask.set(mask.get() ^ SaveState.ACTIVE_REQUESTED);
	}

	private static void clearFlag(final AtomicInteger mask)
	{
		mask.set(mask.get() & ~SaveState.ACTIVE);
	}

	private static void setFlag(final AtomicInteger mask)
	{
		mask.set(mask.get() | SaveState.REQUESTED);
	}

	private static boolean hasFlag(final AtomicInteger mask, final int flag)
	{
		return (mask.get() & flag) > 0;
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (!LoggedIn) {
			onLoggedInGameState();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			onLoggedInGameState();
		} else if (event.getGameState() == GameState.LOGIN_SCREEN && LoggedIn) {
			//this randomly fired at night hours after i had logged off...so i'm adding this guard here.
			if (this.currentPlayer != null && client.getGameState() != GameState.LOGGED_IN) {
				handleLogout();
			}
		}
	}

	private void onLoggedInGameState() {
		//keep scheduling this task until it returns true (when we have access to a display name)
		CommonUtils.getClientThread().invokeLater(() ->
		{
			//we return true in this case as something went wrong and somehow the state isn't logged in, so we don't
			//want to keep scheduling this task.
			client = CommonUtils.getClient();
			if (client == null) {
				return true;
			}
			if (client.getGameState() != GameState.LOGGED_IN) {
				return true;
			}

			final Player player = client.getLocalPlayer();

			//player is null, so we can't get the display name so, return false, which will schedule
			//the task on the client thread again.
			if (player == null) {
				return false;
			}

			final String name = player.getName();

			if (name == null) {
				return false;
			}

			if (name.isEmpty()) {
				return false;
			}

			handleLogin(name);
			//stops scheduling this task
			return true;
		});
	}

	public void handleLogin(String displayName) {
		this.currentPlayer = this.client.getLocalPlayer();
		this.playerName = this.currentPlayer.getName();
		log.debug("{} is logging in", this.playerName);
		LoggedIn = true;
		setActiveHistoryProfile(new TradeHistoryProfile(client.getAccountHash(), playerName, RuneScapeProfileType.getCurrent(client)));
	}

	public void handleLogout() {
		log.debug("{} is logging out", this.playerName);
		//
	}

	// Sets the history associated with the passed profile
	private void setActiveHistoryProfile(final TradeHistoryProfile profile)
	{
		if (profile == null || profile.equals(getActiveProfile()))
		{
			return;
		}
		final SaveData_Common saveData = getSaveDataCommon();
		final TradeHistoryProfile oldProfile = saveData.getActiveProfile();
		saveData.setActiveProfile(profile);
		CommonUtils.postEvent(new EventTradeTrackerProfileChanged(oldProfile, profile));
		SaveManager.saveCommonData();
		SaveManager.requestRestoreTradeHistory();
	}

	// used to schedule saves and prevent save operations from being interrupted
	private static final class SaveState
	{
		static final int INACTIVE = 0;
		static final int REQUESTED = 1;
		static final int ACTIVE = 1 << 1;
		static final int ACTIVE_REQUESTED = REQUESTED | ACTIVE;

	}
}
