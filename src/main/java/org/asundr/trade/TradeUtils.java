package org.asundr.trade;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.asundr.utility.CommonUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

@Slf4j
final public class TradeUtils
{
	private final static HashMap<Integer, CachedItemComposition> itemCompositionMap = new HashMap<>();
	private static ItemManager itemManager;

	public static void initialize(final ItemManager itemManager)
	{
		TradeUtils.itemManager = itemManager;
	}

	// Returns the cached item name of the passed id. Assumes the cached value exists.
	public static String getCachedItemName(final int id)
	{
		return itemCompositionMap.get(id).getName();
	}

	// Returns the cached item name of the passed id, or the passed default value if no ached value is found
	public static String getOrDefaultCachedItemName(final int id, final String defaultValue)
	{
		final CachedItemComposition comp = itemCompositionMap.get(id);
		return comp == null ? defaultValue : comp.getName();
	}

	// Returns the cached high alchemy price of the passed item id
	public static int getHaPrice(final int id)
	{
		final CachedItemComposition cachedComp = itemCompositionMap.get(id);
		return cachedComp == null ? 0 : cachedComp.getHaPrice();
	}

	// Returns the cached low alchemy price of the passed item
	public static int getLaPrice(final int id)
	{
		final CachedItemComposition cachedComp = itemCompositionMap.get(id);
		return cachedComp == null ? 0 : isCurrency(id) ? cachedComp.getHaPrice() : cachedComp.getLaPrice();
	}

	// Returns either the GE price at the time of the trade, or the cached HA / LA price
	public static int getConfiguredPrice(final TradeItemData itemData)
	{
		switch (CommonUtils.getConfig().getDefaultPriceType())
		{
			case LOW_ALCHEMY:
				return getLaPrice(itemData.getUnnotedID());
			case HIGH_ALCHEMY:
				return getHaPrice(itemData.getUnnotedID());
			case GRAND_EXCHANGE:
				return itemData.getGEValue();
		}
		return -1;
	}

	// Returns the price of the item with the passed ID
	// Note: Should be called via clientThread.invokeLater()
	public static int fetchItemGePrice(final int itemID)
	{
		return itemManager.getItemPrice(itemID);
	}

	// Fetches and assigns the Grand Exchange prices of the passed items
	// Note: Should be called via clientThread.invokeLater()
	public static void fetchGePrices(final Collection<TradeItemData> itemDataList)
	{
		for (TradeItemData itemData : itemDataList)
		{
			itemData.setGEValue(fetchItemGePrice(itemData.getUnnotedID()));
		}
	}

	// Fetches item names and will update the id of noted items
	// Note: Should be called via clientThread.invokeLater()
	public static void fetchCompositionData(final Collection<TradeItemData> itemDataList)
	{
		for (final TradeItemData itemData : itemDataList)
		{
			if (!itemCompositionMap.containsKey(itemData.getID()))
			{
				final ItemComposition comp = itemManager.getItemComposition(itemData.getID());
				if (comp.getNote() != -1)
				{
					itemData.setUnnotedId(comp.getLinkedNoteId());
					if (itemCompositionMap.containsKey(itemData.getUnnotedID()))
					{
						continue;
					}
				}
				final int haPrice = isCurrency(itemData.getUnnotedID()) ? comp.getPrice() : comp.getHaPrice();
				itemCompositionMap.put(itemData.getUnnotedID(), new CachedItemComposition(comp.getMembersName(), haPrice));
			}
		}
	}

	// Returns the image for the passed item with the quantity count,
	public static AsyncBufferedImage getItemImage(final int itemId, final int quantity, final boolean stackable)
	{
		return itemManager.getImage(itemId, quantity, stackable);
	}

	// Returns the aggregate quantity of all items with the specified ID in the passed item collection
	public static long getTotalItemQuantity(final Collection<TradeItemData> items, int id)
	{
		return items.stream().filter(i -> i.getUnnotedID() == id).reduce(0L, (a, i) -> a + i.getQuantity(), Long::sum);
	}

	// Evaluates the aggregate Grand Exchange value of all passed item stacks
	public static long totalConfiguredValue(final Collection<TradeItemData> items)
	{
		return items.stream().reduce(0L, (acc, item) -> acc + ((long) item.getConfiguredValue() * (long) item.getQuantity()), Long::sum);
	}

	// Returns true if the only items in the passed collection currency such as coins or platinum
	public static boolean isOnlyCurrency(final Collection<TradeItemData> items)
	{
		if (items.isEmpty())
		{
			return false;
		}
		for (TradeItemData itemData : items)
		{
			if (!isCurrency(itemData.getUnnotedID()))
			{
				return false;
			}
		}
		return true;
	}

	public static boolean isCurrency(final int id)
	{
		return id == ItemID.COINS || id == ItemID.PLATINUM;
	}

	// Returns true if all items in the passed collection have the same ID.
	public static boolean hasOnlyOneTypeOfItem(final Collection<TradeItemData> items)
	{
		if (items.isEmpty())
		{
			return false;
		}
		Iterator<TradeItemData> itr = items.iterator();
		final int id = itr.next().getUnnotedID();
		while (itr.hasNext())
		{
			if (id != itr.next().getUnnotedID())
			{
				return false;
			}
		}
		return true;
	}


	// Returns a map of item IDs to the aggregate quantity of items with that id in the passed collection
	public static HashMap<Integer, Long> getItemCountsNoGP(final Collection<TradeItemData> items)
	{
		final HashMap<Integer, Long> counts = new HashMap<>();
		for (final TradeItemData item : items)
		{
			final int id = item.getUnnotedID();
			log.debug("{} id", id);
			if (id != 995)
			{
				Long count = counts.getOrDefault(id, 0L);
				counts.put(id, count + item.getQuantity());
			} else {
				Long count = counts.getOrDefault(id, 0L);
				counts.put(id, count);
			}
		}
		return counts;
	}

	// Returns a map of item IDs to the aggregate quantity of items with that id in the passed collection
	public static HashMap<Integer, Long> getItemCounts(final Collection<TradeItemData> items)
	{
		final HashMap<Integer, Long> counts = new HashMap<>();
		for (final TradeItemData item : items)
		{
			final int id = item.getUnnotedID();
			Long count = counts.getOrDefault(id, 0L);
			counts.put(id, count + item.getQuantity());
		}
		return counts;
	}

	// Contains cached item data
	private static class CachedItemComposition
	{
		private final String name;
		private final int haPrice;

		CachedItemComposition(final String membersName, final int haPrice)
		{
			this.name = membersName;
			this.haPrice = haPrice;
		}

		public String getName()
		{
			return name;
		}

		public int getStorePrice()
		{
			return (int) (haPrice / 0.6f);
		}

		public int getHaPrice()
		{
			return haPrice;
		}

		public int getLaPrice()
		{
			return (int) ((haPrice * 2L) / 3L);
		}
	}
}
