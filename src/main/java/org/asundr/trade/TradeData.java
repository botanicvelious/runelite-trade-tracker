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
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import org.asundr.utility.CommonUtils;

import java.util.ArrayList;
import java.util.HashMap;

import static java.lang.System.currentTimeMillis;

@Slf4j
// Contains all data to describe a trade between players
public class TradeData
{
	public long tradeTime = 0L;                                     // timestamp in seconds
	public TradePlayerData tradedPlayer = new TradePlayerData();    // Data from the other player such as name at time of trade
	public final ArrayList<TradeItemData> givenItems = new ArrayList<>();
	public final ArrayList<TradeItemData> receivedItems = new ArrayList<>();
	transient public long givenTotalValue = 0L;                             // aggregate grand exchange value of given items in coins at the time of trade
	transient public long receivedTotalValue = 0L;                          // aggregate grand exchange value of received items in coins at the time of trade
	public String note = "";                                        // player-authored note
	transient public long givenItemsTraded = 0L;                                        // total items given
	transient public long receivedItemsTraded = 0L;                                        // total items received

	// Refreshes the tracked items of this player, or the traded player by querying their respective trade container
	public void updateItems(boolean isCurrentPlayer, ItemContainer itemContainer)
	{
		final ArrayList<TradeItemData> updatedItems = isCurrentPlayer ? givenItems : receivedItems;
		updatedItems.clear();
		if (itemContainer == null)
		{
			return;
		}
		for (Item item : itemContainer.getItems())
		{
			if (item == null || item.getId() == -1)
			{
				continue;
			}
			updatedItems.add(new TradeItemData(item.getId(), item.getQuantity()));
		}
	}

	// Calculates the total quantity of items traded not value minus coins
	public void calculateTotalItems()
	{
		// query cumulative item quantities (if simple trade, this has already been done)
		final HashMap<Integer, Long> givenItemCountSums = TradeUtils.getItemCountsNoGP(givenItems);
		long givenSum = givenItemCountSums.values().stream()
				.mapToLong(Long::longValue)
				.sum();

		final HashMap<Integer, Long> receivedItemCountSums = TradeUtils.getItemCountsNoGP(receivedItems);
		long receivedSum = receivedItemCountSums.values().stream()
				.mapToLong(Long::longValue)
				.sum();

		givenItemsTraded = givenSum;
		receivedItemsTraded = receivedSum;
		// log.debug("{} givenSum", givenSum);
		// log.debug("{} receivedSum", receivedSum);
	}

	// Calculates the total value of items given and received. Should only be called after ge prices for all items have been fetched.
	public void calculateAggregateValues()
	{
		this.calculateTotalItems();
		givenTotalValue = TradeUtils.totalConfiguredValue(givenItems);
		receivedTotalValue = TradeUtils.totalConfiguredValue(receivedItems);
	}

	public final boolean isEmpty()
	{
		return givenItems.isEmpty() && receivedItems.isEmpty();
	}

	public final boolean isExpired()
	{
		if (CommonUtils.isValidPurgeConfig())
		{
			return false;
		}
		return tradeTime * 1000L + CommonUtils.getRecordLifetime() < currentTimeMillis();
	}

}
