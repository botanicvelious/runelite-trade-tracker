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

package org.asundr.ui;

import net.runelite.client.util.QuantityFormatter;
import org.asundr.recovery.SaveManager;
import org.asundr.trade.SimpleTradeData;
import org.asundr.trade.TradeData;
import org.asundr.trade.TradeItemData;
import org.asundr.trade.TradeUtils;
import org.asundr.utility.CommonUtils;
import org.asundr.utility.StringUtils;
import org.asundr.utility.TimeUtils;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

class TradeRecordPanel extends CollapsiblePanel
{
	private static final ImageIcon ICON_NOTE = CommonUtils.getIconFromName("note_icon.png", 14, 14, Image.SCALE_SMOOTH);
	private static final String TRADE_NAME_TEMPLATE = "<html><body style='color:#A0A0EE;font-size:13px;translate:-100px 0'><b>%s</b></body></html>";
	private static final String TRADE_TIME_TEMPLATE = "<html><body style='color:yellow'>%s</body></html>";
	private static final String TEMPLATE_TRADE_TOTAL = "<html><body style='color:white'><nobr>%s: (<span style='color:%s'> #P: %s </nobr></span>)</body></html>";
	private static final String TRADE_PRICE_PER_TEMPLATE = "<html><span style=''>@</span> %s<span style=''> ea</span></html>";
	private static final Color COLOR_BUTTON_BACKGROUND = new Color(20, 20, 30);
	private static final Color COLOR_FOOTER_PROFIT = new Color(10, 50, 10);
	private static final Color COLOR_FOOTER_LOSS = new Color(50, 10, 10);
	private static final Color COLOR_FOOTER_EVEN = new Color(20, 20, 20);
	private static final Color COLOR_CONTENT_BACKGROUND = new Color(15, 15, 20);
	private static final Border BORDER_RECORD_PANEL = BorderFactory.createLineBorder(Color.black, 1);
	private static final Border BORDER_ITEM_GRID = BorderFactory.createCompoundBorder(null, BorderFactory.createLineBorder(Color.BLACK, 1));
	private static final Border BORDER_CONTENT = BorderFactory.createCompoundBorder(null, BorderFactory.createLineBorder(Color.BLACK, 1));

	private static TradeRecordPopUpMenu buttonPopup = null;

	public final TradeData tradeData;
	private final SimpleTradeData simpleData;
	private final JLabel tradeTimeLabel = new JLabel();
	private final QuantityLabel givenTotalLabel;
	private final QuantityLabel receivedTotalLabel;
	public Component paddingStrut = null;
	private JLabel noteIconLabel = null;

	TradeRecordPanel(TradeData tradeData)
	{
		super();
		this.tradeData = tradeData;
		setBorder(BORDER_RECORD_PANEL);

		// Setting up traded player name and trade time

		JLabel playerNameLabel = new JLabel(String.format(TRADE_NAME_TEMPLATE, tradeData.tradedPlayer.tradeName));
		updateTimeDisplay();
		tradeTimeLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				super.mouseEntered(e);
				final String timeString = CommonUtils.getConfig().use24HourTime() ? "HH:mm:ss" : "hh:mm:ss a";
				tradeTimeLabel.setToolTipText(TimeUtils.timestampToString(tradeData.tradeTime, "E dd LLL yyyy @ " + timeString + " z"));
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				super.mouseClicked(e);
				switch (e.getButton())
				{
					case MouseEvent.BUTTON1:
						toggleButton.doClick(100);
						break;
					case MouseEvent.BUTTON3:
						buttonPopup.setTradeRecordPanel(TradeRecordPanel.this);
						buttonPopup.show(e.getComponent(), e.getX(), e.getY());
						break;
				}
			}
		});
		if (buttonPopup == null)
		{
			buttonPopup = new TradeRecordPopUpMenu();
		}

		// Setting up header button

		toggleButton.setLayout(new BorderLayout());
		toggleButton.add(playerNameLabel, BorderLayout.CENTER);
		toggleButton.add(tradeTimeLabel, BorderLayout.EAST);
		toggleButton.setBackground(COLOR_BUTTON_BACKGROUND);
		toggleButton.setBorder(BorderFactory.createLineBorder(COLOR_BUTTON_BACKGROUND, 4));
		toggleButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				super.mouseClicked(e);
				if (e.getButton() == 3)
				{
					buttonPopup.setTradeRecordPanel(TradeRecordPanel.this);
					buttonPopup.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
		updateNoteUI();

		// Setting up simple trade panel if valid

		simpleData = new SimpleTradeData(tradeData);
		if (simpleData.isValid())
		{
			final JPanel summaryPanel = new JPanel();
			final ItemLabel imgLabel = new ItemLabel(simpleData.getItem(), e ->
			{ // create custom image stack
				if (e.getButton() == MouseEvent.BUTTON1) // spoof mouse events since icon is blocking due to tooltip
				{
					toggleButton.doClick(100);
				} else if (e.getButton() == MouseEvent.BUTTON3)
				{
					buttonPopup.setTradeRecordPanel(TradeRecordPanel.this);
					buttonPopup.show(e.getComponent(), e.getX(), e.getY());
				}
			}, simpleData.getQuantity());
			summaryPanel.setBackground(COLOR_BUTTON_BACKGROUND);
			summaryPanel.setOpaque(false);
			String Descriptor = "";
			final SimpleTradeData.Type tradeType = simpleData.getTradeType();
			switch (tradeType)
			{
				case Sold_Item:
					Descriptor = "Sold";
					break;
				case Bought_Item:
					Descriptor = "Bought";
					break;
				case Gift_Giving:
					Descriptor = "Gave away gift of";
					break;
				case Gift_Receiving:
					Descriptor = "Received gift of";
					break;
			}
			summaryPanel.add(new JLabel(Descriptor));
			summaryPanel.add(imgLabel);
			if (tradeType == SimpleTradeData.Type.Sold_Item || simpleData.getTradeType() == SimpleTradeData.Type.Bought_Item)
			{
				final String pricePerString = simpleData.getPricePerItem() < 100d
						? Double.toString(Math.round(1000d * simpleData.getPricePerItem()) / 1000d)
						: StringUtils.quantityToRSDecimalStackLong((int) simpleData.getPricePerItem(), true);
				JLabel pricePerLabel = new JLabel(String.format(TRADE_PRICE_PER_TEMPLATE, pricePerString));
				pricePerLabel.setToolTipText(QuantityFormatter.formatNumber(simpleData.getPricePerItem()) + " gp each");
				pricePerLabel.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e) // spoof mouse events since icon is blocking due to tooltip
					{
						super.mouseClicked(e);
						if (e.getButton() == MouseEvent.BUTTON1)
						{
							toggleButton.doClick(100);
						} else if (e.getButton() == MouseEvent.BUTTON3)
						{
							buttonPopup.setTradeRecordPanel(TradeRecordPanel.this);
							buttonPopup.show(e.getComponent(), e.getX(), e.getY());
						}
					}
				});
				summaryPanel.add(pricePerLabel);
			}
			toggleButton.add(summaryPanel, BorderLayout.SOUTH);
		}

		// Setting up content panel with trade item grids

		final IconGridPanel myItemGrid = new IconGridPanel();
		myItemGrid.updateIcons(tradeData.givenItems);
		myItemGrid.setBorder(BORDER_ITEM_GRID);
		final IconGridPanel otherItemGrid = new IconGridPanel();
		otherItemGrid.updateIcons(tradeData.receivedItems);
		otherItemGrid.setBorder(BORDER_ITEM_GRID);

		contentPanel.setBackground(COLOR_CONTENT_BACKGROUND);
		contentPanel.setBorder(BORDER_CONTENT);
		contentPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.gridx = 0;

		gbc.gridy = 0;
		givenTotalLabel = new QuantityLabel(
				tradeData.givenTotalValue,
				String.format(TEMPLATE_TRADE_TOTAL, "Given items", "#FF9090", "%s"),
				"Total #P value of items you gave away: %s"
		);
		contentPanel.add(givenTotalLabel, gbc);

		gbc.gridy = 1;
		contentPanel.add(myItemGrid, gbc);

		gbc.gridy = 2;
		receivedTotalLabel = new QuantityLabel(
				tradeData.receivedTotalValue,
				String.format(TEMPLATE_TRADE_TOTAL, "Received items", "#90FF90", "%s"),
				"Total #P value of items you were given: %s"
		);
		contentPanel.add(receivedTotalLabel, gbc);

		gbc.gridy = 3;
		contentPanel.add(otherItemGrid, gbc);

		updateFooterPanel();
		updatePreferredSize();
	}

	private void updateFooterPanel()
	{
		final long netTransfer = tradeData.receivedTotalValue - tradeData.givenTotalValue;
		final Color footerColor = netTransfer > 0 ? COLOR_FOOTER_PROFIT : netTransfer < 0 ? COLOR_FOOTER_LOSS : COLOR_FOOTER_EVEN;
		footerPanel.setBackground(footerColor);
		JLabel footerLabel;
		if (tradeData.givenItems.isEmpty() && tradeData.receivedItems.isEmpty())
		{
			footerLabel = new JLabel("No items traded");
		} else if (netTransfer == 0)
		{
			final String shortPriceName = CommonUtils.getConfig().getDefaultPriceType().shortName;
			footerLabel = new JLabel(shortPriceName + " values matched exactly");
		} else
		{
			final String footerPrefix = netTransfer < 0 ? "You lost" : "You gained";
			footerLabel = new QuantityLabel(netTransfer, "<html>" + footerPrefix + ": %s  <span style='color:#909090'>[#P]</span></html>", "%s");
		}
		footerPanel.removeAll();
		footerPanel.add(footerLabel);
	}

	// Collapses the entire panel to zero height to prevent it form taking space in the history
	public void updatePreferredSize()
	{
		double contentSize = 0d;
		if (!isCollapsed())
		{
			int rows = (int) (Math.ceil(tradeData.givenItems.size() / 6f) + Math.ceil(tradeData.receivedItems.size() / 6f));
			contentSize = ItemLabel.ICON_SIZE * Math.max(2, rows) + 42d;
		}
		final double height = toggleButton.getPreferredSize().getHeight() + footerPanel.getPreferredSize().getHeight() + contentSize;
		final Dimension size = new Dimension(TradeTrackerPluginPanel.PANEL_WIDTH + 2, (int) height);
		setPreferredSize(size);
		setMaximumSize(size);
	}

	public final long getTradeTime()
	{
		return tradeData.tradeTime;
	}

	public final TradeData getTradeData()
	{
		return tradeData;
	}

	public final String getNote()
	{
		return tradeData.note;
	}

	public void setNote(String note)
	{
		if (!note.trim().equals(tradeData.note))
		{
			tradeData.note = note.trim();
			updateNoteUI();
			SaveManager.requestTradeHistorySave();
		}
	}

	// Updates the visuals and tooltip for note icon
	private void updateNoteUI()
	{
		if (tradeData.note.isBlank())
		{
			if (noteIconLabel != null)
			{
				noteIconLabel.setToolTipText("");
				noteIconLabel.setVisible(false);
			}
		} else
		{
			if (noteIconLabel == null)
			{
				noteIconLabel = new JLabel();
				noteIconLabel.setIcon(ICON_NOTE);
				noteIconLabel.setSize(new Dimension(6, 7));
				toggleButton.add(noteIconLabel, BorderLayout.WEST);
			}
			noteIconLabel.setToolTipText(tradeData.note);
			noteIconLabel.setVisible(true);
		}
		SwingUtilities.invokeLater(toggleButton::updateUI);
	}

	public void toggleVisible(final boolean visible)
	{
		setVisible(visible);
		paddingStrut.setVisible(visible);
	}

	// Updates the text for the time label. Used when toggling the config for 24 hour time.
	public void updateTimeDisplay()
	{
		tradeTimeLabel.setText(String.format(TRADE_TIME_TEMPLATE, TimeUtils.timestampToStringTime(tradeData.tradeTime)));
	}

	// Get panel value.
	public long getPanelValue()
	{
		return tradeData.receivedTotalValue - tradeData.givenTotalValue;
	}

	// Get panel quantity.
	public long getPanelQuantity()
	{
		return tradeData.receivedItemsTraded - tradeData.givenItemsTraded;
	}

	// Get panel quantity.
	public long getPanelSum()
	{
		return tradeData.receivedItemsTraded + tradeData.givenItemsTraded;
	}

	// Updates the aggregate item prices after changing price type
	public void updateItemPriceType()
	{
		tradeData.calculateAggregateValues();
		givenTotalLabel.update(tradeData.givenTotalValue);
		receivedTotalLabel.update(tradeData.receivedTotalValue);
		updateFooterPanel();
	}

	@Override
	protected void onToggleCollapsed()
	{
		updatePreferredSize();
	}

	// Returns true if any strings or quantities in this trade record match the passed string.
	// Ignores case.
	public boolean match(String query)
	{
		query = query.trim().toLowerCase();
		// check if traded player's name contains query
		if (tradeData.tradedPlayer.tradeName.toLowerCase().contains(query.toLowerCase()))
			return true;

		// check for - for day/week/month
		if (query != null && !query.isEmpty())
		{
			char firstChar = query.charAt(0);
			if (firstChar == '-')
			{
				String digits = query.replaceAll("[^0-9]", "");
				long number = 0;
				if (digits != null && !digits.isEmpty())
				{
					number = Integer.parseInt(digits);
				} else {
					number = 0;
				}
				if (number > 0)
				{
					char lastChar = query.charAt(query.length() - 1);
					if (lastChar == 'd')
					{
						//System.out.println("Query numb: " + number);
						//System.out.println("Query date: " + (Instant.now().getEpochSecond() - (1000*60*60*24*number)));
						//System.out.println("now date  : " + Instant.now().getEpochSecond());
						//System.out.println("trade date: " + tradeData.tradeTime);
						//System.out.println("ofset date: " + (1000*60*60*24*number));
						if ((Instant.now().getEpochSecond() - (60*60*24*number)) <= tradeData.tradeTime)
						{
							return true;
						}
					} else if (lastChar == 'w') {
						if ((Instant.now().getEpochSecond() - (60*60*24*number*7)) <= tradeData.tradeTime)
						{
							return true;
						}
					} else if (lastChar == 'm') {
						if ((Instant.now().getEpochSecond() - (60*60*24*number*30)) <= tradeData.tradeTime)
						{
							return true;
						}
					}
				}
			}
		}
		// check if string is a date
		try {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/uuuu")
					.withResolverStyle(ResolverStyle.STRICT);
			LocalDate parsedDate = LocalDate.parse(query, formatter);
			TimeUtils.timestampToString(tradeData.tradeTime, "MM/dd/uuuu");
			//System.out.println("Query date: " + query);
			//System.out.println("Trade date: " + TimeUtils.timestampToString(tradeData.tradeTime, "MM/dd/uuuu"));
			if (query.equals(TimeUtils.timestampToString(tradeData.tradeTime, "MM/dd/uuuu")))
			{
				return true;
			}
			//System.out.println("Valid date: " + parsedDate);
		} catch (DateTimeParseException e) {
			//System.out.println("Invalid date format or value: " + query);
		}

		// check if any words in notes start with query
		if (tradeData.note != null && !tradeData.note.isBlank())
		{
			final int noteLength = tradeData.note.length(), queryLength = query.length();
			final HashMap<Character, ArrayList<Integer>> wordIndexes = StringUtils.getIndexesOfFirstLetterOfWord(tradeData.note);
			for (final char key : wordIndexes.keySet())
			{
				for (final int index : wordIndexes.get(key))
				{
					final int subStrLength = Math.min(queryLength, noteLength - index);
					if (queryLength <= subStrLength)
					{
						if (query.equals(tradeData.note.substring(index, index + subStrLength)))
						{
							return true;
						}
					}
				}
			}
		}
		// float numeric queries
		if (simpleData != null && simpleData.isValid())
		{
			if (Double.toString(simpleData.getPricePerItem()).startsWith(query))
				return true;
		}
		// int/long numeric queries
		if (StringUtils.isStringLong(query) || query.equals("-"))
		{
			// searching for balance values
			if (Long.toString(tradeData.receivedTotalValue).startsWith(query))
				return true;
			if (Long.toString(tradeData.givenTotalValue).startsWith(query))
				return true;
			if (Long.toString(tradeData.receivedTotalValue - tradeData.givenTotalValue).startsWith(query))
				return true;

			// query simple trade values
			if (simpleData != null && simpleData.isValid())
			{
				if (Long.toString(simpleData.getQuantity()).startsWith(query))
					return true;
				if (Long.toString(simpleData.getQuantity() * simpleData.getItem().getGEValue()).startsWith(query))
					return true;
			}

			// query properties on given and received items
			for (var list : new ArrayList<>(Arrays.asList(tradeData.givenItems, tradeData.receivedItems)))
			{
				// query individual item counts and values
				for (final TradeItemData item : list)
				{
					if (CommonUtils.getConfig().filterMatchItemId())
					{
						if (Integer.toString(item.getUnnotedID()).startsWith(query))
							return true;
						if (item.isNoted() && Integer.toString(item.getID()).startsWith(query))
							return true;
					}
					if (Integer.toString(item.getQuantity()).startsWith(query))
						return true;
					if (Integer.toString(item.getGEValue()).startsWith(query))
						return true;
					if (Integer.toString(item.getLaValue()).startsWith(query))
						return true;
					if (Integer.toString(item.getHaValue()).startsWith(query))
						return true;
				}

				// query cumulative item quantities (if simple trade, this has already been done)
				if (simpleData != null && simpleData.isValid())
				{
					final var itemCountSums = TradeUtils.getItemCounts(list);
					for (final int key : itemCountSums.keySet())
					{
						if (Long.toString(itemCountSums.get(key)).startsWith(query))
						{
							return true;
						}
					}
				}
			}
		}
		// Text queries
		else
		{
			// check item names
			for (var list : new ArrayList<>(Arrays.asList(tradeData.givenItems, tradeData.receivedItems)))
			{
				for (final TradeItemData item : list)
				{
					final String itemName = TradeUtils.getOrDefaultCachedItemName(item.getUnnotedID(), null);
					if (itemName != null && itemName.toLowerCase().contains(query))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

}
