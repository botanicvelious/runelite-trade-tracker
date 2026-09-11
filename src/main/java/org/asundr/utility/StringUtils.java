package org.asundr.utility;

import com.google.gson.Gson;
import net.runelite.client.util.QuantityFormatter;
import org.asundr.TradeTrackerConfig;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class StringUtils
{
	// Note: from Quantity formatter
	private static final NumberFormat DECIMAL_FORMATTER = new DecimalFormat("#,###.#", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
	private static final NumberFormat PRECISE_DECIMAL_FORMATTER = new DecimalFormat("#,###.###", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
	private static final String[] QUANTITY_SUFFIXES = {"", "K", "M", "B", "T", "Q", "Qt"}; // 2^32 * 2^32 * 28 = 5.165 quintillion.
	private static Gson gson;

	public static void initialize(final Gson gson)
	{
		StringUtils.gson = gson;
	}

	// Returns an abbreviated string representation of the passed long in the style of QuantityFormatter but is capable of handling quantities up to Long.MAX_VALUE
	public static String quantityToRSDecimalStackLong(long quantity, boolean precise)
	{
		final String quantityStr = QuantityFormatter.formatNumber(quantity);
		if (quantityStr.length() <= 6 || (quantity < 0 && quantityStr.length() == 7))
		{
			return quantityStr;
		}
		final int power = (int) Math.log10(Math.abs(quantity));
		final NumberFormat numberFormat = precise && power >= 6 ? PRECISE_DECIMAL_FORMATTER : DECIMAL_FORMATTER;
		return numberFormat.format(quantity / (Math.pow(10, power - power % 3))) + QUANTITY_SUFFIXES[power / 3];
	}

	public static Gson getGsonBuilder()
	{
		return gson.newBuilder().create();
	}

	// Converts the passed object to a json string
	public static <T> String stringify(T object)
	{
		return gson.newBuilder().create().toJson(object);
	}

	// Returns true if the passed String is a Long type
	public static boolean isStringLong(final String str)
	{
		try
		{
			Long.parseLong(str);
		} catch (NumberFormatException e)
		{
			return false;
		}
		return true;
	}

	// Maps characters of to a list of all indexes where that character is the first letter of a word
	// note: converts all to lower case
	public static HashMap<Character, ArrayList<Integer>> getIndexesOfFirstLetterOfWord(final String str)
	{
		HashMap<Character, ArrayList<Integer>> indexMap = new HashMap<>();
		boolean addNextChar = true;
		final char[] characters = str.trim().toLowerCase().toCharArray();
		for (int i = 0; i < characters.length; ++i)
		{
			final char c = characters[i];
			if (Character.isWhitespace(c))
			{
				addNextChar = true;
			} else if (addNextChar)
			{
				indexMap.putIfAbsent(c, new ArrayList<>());
				indexMap.get(c).add(i);
				addNextChar = false;
			}
		}
		return indexMap;
	}

	// Removes all html tags from widget text
	public static String sanitizeWidgetText(final String s)
	{
		return s.replaceAll("<[^>]*>", "").trim();
	}

	// Copies the passed String to the user's clipboard
	public static void copyToClipboard(final String content)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content), null);
	}

	public static String colorToHexString(final Color color, final boolean includeHash)
	{
		return (includeHash ? "#" : "") + String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	public static String capitalize(final String str)
	{
		return org.apache.commons.lang3.StringUtils.capitalize(str.toLowerCase());
	}

	public static String formatEnum(Enum enumValue, boolean capitalized)
	{
		return (capitalized ? capitalize(enumValue.toString()) : enumValue.toString().toLowerCase()).replace("_", " ");
	}

	public static String replacePriceType(final String original, final boolean isShort)
	{
		final TradeTrackerConfig.ItemPriceType type = CommonUtils.getConfig().getDefaultPriceType();
		return original.replaceAll("#[pP]", isShort ? type.shortName : type.fullName);
	}
}
