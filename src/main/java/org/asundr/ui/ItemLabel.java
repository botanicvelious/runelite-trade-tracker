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

import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;
import org.asundr.TradeTrackerConfig;
import org.asundr.trade.TradeItemData;
import org.asundr.trade.TradeUtils;
import org.asundr.utility.CommonUtils;
import org.asundr.utility.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

class ItemLabel extends JLabel
{
	public static final int ICON_SIZE = 36;
	private static final Dimension PREFERRED_SIZE = new Dimension(ICON_SIZE, ICON_SIZE);
	private static final String ITEM_TOOLTIP_TEMPLATE = "<html><body style><b style='color:#A0A0FF'>%s</b><br>Quantity: %s<br>GE: %s<br>HA: %s<br>Total: %s</body></html>";

	private static final String TEMPLATE_MENU_PRICE = "#P";
	private static final String TEMPLATE_MENU_PRICE_TOTAL = "#P total";
	private static final String SUFFIX_MENU_PRICE_GE = " at trade time";
	private static final String TEXT_MENU_PRICE_LA = TEMPLATE_MENU_PRICE.replaceAll("#P", TradeTrackerConfig.ItemPriceType.LOW_ALCHEMY.fullName);
	private static final String TEXT_MENU_PRICE_HA = TEMPLATE_MENU_PRICE.replaceAll("#P", TradeTrackerConfig.ItemPriceType.HIGH_ALCHEMY.fullName);
	private static final String TEXT_MENU_PRICE_GE = TEMPLATE_MENU_PRICE.replaceAll("#P", TradeTrackerConfig.ItemPriceType.GRAND_EXCHANGE.shortName) + SUFFIX_MENU_PRICE_GE;
	private static final String TEXT_MENU_PRICE_TOTAL_LA = TEMPLATE_MENU_PRICE_TOTAL.replaceAll("#P", TradeTrackerConfig.ItemPriceType.LOW_ALCHEMY.fullName);
	private static final String TEXT_MENU_PRICE_TOTAL_HA = TEMPLATE_MENU_PRICE_TOTAL.replaceAll("#P", TradeTrackerConfig.ItemPriceType.HIGH_ALCHEMY.fullName);
	private static final String TEXT_MENU_PRICE_TOTAL_GE = TEMPLATE_MENU_PRICE_TOTAL.replaceAll("#P", TradeTrackerConfig.ItemPriceType.GRAND_EXCHANGE.shortName) + SUFFIX_MENU_PRICE_GE;
	private static final String TEXT_MENU_ITEM_NAME = "Item name";
	private static final String TEXT_MENU_QUANTITY = "Quantity";
	private static final String TEXT_MENU_ID = "Item ID";
	private static final String TEXT_MENU_ID_UNNOTED = "Item ID (un-noted)";

	private static JPopupMenu popupMenu = null;
	private static String popupItemName = null;
	private static int popupItemId;
	private static TradeItemData popupItemData = null;

	private final TradeItemData itemData;
	private final long quantityOverride;
	private final JMenuItem copyUnnotedId = new JMenuItem(TEXT_MENU_ID_UNNOTED);
	private final JMenuItem copyId = new JMenuItem(TEXT_MENU_ID);
	private final JMenuItem filterUnnotedId = new JMenuItem(TEXT_MENU_ID_UNNOTED);
	private final JMenuItem filterId = new JMenuItem(TEXT_MENU_ID);


	ItemLabel(final TradeItemData itemData, final Consumer<MouseEvent> mouseClickedCallback, final long quantityOverride)
	{
		this.itemData = itemData;
		this.quantityOverride = quantityOverride;
		final int displayId = itemData.getID();
		final AsyncBufferedImage img = TradeUtils.getItemImage(displayId, itemData.getQuantity(), true);
		img.addTo(this);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				super.mouseEntered(e);
				ToolTipManager.sharedInstance().setEnabled(true);
				final String tipStr = String.format(
						ITEM_TOOLTIP_TEMPLATE,
						TradeUtils.getOrDefaultCachedItemName(itemData.getUnnotedID(), "Item"),
						QuantityFormatter.formatNumber(getTrueQuantity()),
						QuantityFormatter.formatNumber(itemData.getGEValue()),
						QuantityFormatter.formatNumber(itemData.getHaValue()),
						QuantityFormatter.formatNumber(itemData.getConfiguredValue() * getTrueQuantity()) + " [" + CommonUtils.getConfig().getDefaultPriceType().shortName + "]"
				);
				setToolTipText(tipStr);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				super.mouseClicked(e);
				if (e.getButton() == MouseEvent.BUTTON3)
				{
					popupItemId = itemData.getUnnotedID();
					popupItemName = TradeUtils.getCachedItemName(popupItemId);
					popupItemData = new TradeItemData(itemData);
					if (popupItemName != null)
					{
						if (popupMenu == null)
						{
							popupMenu = new JPopupMenu()
							{
								@Override
								public void show(Component invoker, int x, int y)
								{
									final boolean showIDOptions = CommonUtils.getConfig().filterMatchItemId();
									copyUnnotedId.setVisible(popupItemData.isNoted());
									filterId.setVisible(showIDOptions);
									filterUnnotedId.setVisible(showIDOptions && popupItemData.isNoted());
									super.show(invoker, x, y);
								}
							};

							final JMenu openSubmenu = new JMenu("Open in");
							final JMenuItem openWiki = new JMenuItem("Wiki page");
							openWiki.addActionListener(evt -> CommonUtils.openItemWiki(getCurrentItemName()));
							openSubmenu.add(openWiki);
							final JMenuItem openWikiGE = new JMenuItem("Wiki price history");
							openWikiGE.addActionListener(evt -> CommonUtils.openItemPriceHistory(popupItemId));
							openSubmenu.add(openWikiGE);
							popupMenu.add(openSubmenu);

							// Add copy submenu
							final JMenu copySubmenu = new JMenu("Copy");
							final JMenuItem copyName = new JMenuItem(TEXT_MENU_ITEM_NAME);
							copyName.addActionListener(evt -> StringUtils.copyToClipboard(getCurrentItemName()));
							copySubmenu.add(copyName);
							final JMenuItem copyQuantity = new JMenuItem(TEXT_MENU_QUANTITY);
							copyQuantity.addActionListener(evt -> StringUtils.copyToClipboard(Long.toString(getTrueQuantity())));
							copySubmenu.add(copyQuantity);
							// copy price sub-sub menu
							final JMenu copyPriceSubmenu = new JMenu("Prices");
							final JMenuItem copyLAPrice = new JMenuItem(TEXT_MENU_PRICE_LA);
							copyLAPrice.addActionListener(evt -> StringUtils.copyToClipboard(Integer.toString(popupItemData.getLaValue())));
							copyPriceSubmenu.add(copyLAPrice);
							final JMenuItem copyLATotal = new JMenuItem(TEXT_MENU_PRICE_TOTAL_LA);
							copyLATotal.addActionListener(evt -> StringUtils.copyToClipboard(Long.toString((long) popupItemData.getLaValue() * getTrueQuantity())));
							copyPriceSubmenu.add(copyLATotal);
							final JMenuItem copyHAPrice = new JMenuItem(TEXT_MENU_PRICE_HA);
							copyHAPrice.addActionListener(evt -> StringUtils.copyToClipboard(Integer.toString(popupItemData.getHaValue())));
							copyPriceSubmenu.add(copyHAPrice);
							final JMenuItem copyHATotal = new JMenuItem(TEXT_MENU_PRICE_TOTAL_HA);
							copyHATotal.addActionListener(evt -> StringUtils.copyToClipboard(Long.toString((long) popupItemData.getHaValue() * getTrueQuantity())));
							copyPriceSubmenu.add(copyHATotal);
							final JMenuItem copyGEPrice = new JMenuItem(TEXT_MENU_PRICE_GE);
							copyGEPrice.addActionListener(evt -> StringUtils.copyToClipboard(Long.toString(popupItemData.getGEValue())));
							copyPriceSubmenu.add(copyGEPrice);
							final JMenuItem copyGETotal = new JMenuItem(TEXT_MENU_PRICE_TOTAL_GE);
							copyGETotal.addActionListener(evt -> StringUtils.copyToClipboard(Long.toString(popupItemData.getGEValue() * getTrueQuantity())));
							copyPriceSubmenu.add(copyGETotal);
							copySubmenu.add(copyPriceSubmenu);
							copyId.addActionListener(evt -> StringUtils.copyToClipboard(Integer.toString(popupItemData.getID())));
							copySubmenu.add(copyId);
							copyUnnotedId.addActionListener(evt -> StringUtils.copyToClipboard(Integer.toString(popupItemData.getUnnotedID())));
							copySubmenu.add(copyUnnotedId);
							popupMenu.add(copySubmenu);

							// Add filter submenu
							final JMenu filterSubmenu = new JMenu("Filter by");
							final JMenuItem filterName = new JMenuItem(TEXT_MENU_ITEM_NAME);
							filterName.addActionListener(evt -> GuiUtils.setFilterAndEnabled(getCurrentItemName()));
							filterSubmenu.add(filterName);
							final JMenuItem filterQuantity = new JMenuItem(TEXT_MENU_QUANTITY);
							filterQuantity.addActionListener(evt -> GuiUtils.setFilterAndEnabled(Long.toString(getTrueQuantity())));
							filterSubmenu.add(filterQuantity);
							// filter by prices sub-sub menu
							final JMenu filterPricesSubmenu = new JMenu("Prices");
							final JMenuItem filterLaPrice = new JMenuItem(TEXT_MENU_PRICE_LA);
							filterLaPrice.addActionListener(evt -> GuiUtils.setFilterAndEnabled(Integer.toString(popupItemData.getLaValue())));
							filterPricesSubmenu.add(filterLaPrice);
							final JMenuItem filterLaTotal = new JMenuItem(TEXT_MENU_PRICE_TOTAL_LA);
							filterLaTotal.addActionListener(evt -> GuiUtils.setFilterAndEnabled(Long.toString((long) popupItemData.getLaValue() * getTrueQuantity())));
							filterPricesSubmenu.add(filterLaTotal);
							final JMenuItem filterHaPrice = new JMenuItem(TEXT_MENU_PRICE_HA);
							filterHaPrice.addActionListener(evt -> GuiUtils.setFilterAndEnabled(Integer.toString(popupItemData.getHaValue())));
							filterPricesSubmenu.add(filterHaPrice);
							final JMenuItem filterHaTotal = new JMenuItem(TEXT_MENU_PRICE_TOTAL_HA);
							filterHaTotal.addActionListener(evt -> GuiUtils.setFilterAndEnabled(Long.toString((long) popupItemData.getHaValue() * getTrueQuantity())));
							filterPricesSubmenu.add(filterHaTotal);
							final JMenuItem filterGEPrice = new JMenuItem(TEXT_MENU_PRICE_GE);
							filterGEPrice.addActionListener(evt -> GuiUtils.setFilterAndEnabled(Long.toString(popupItemData.getGEValue())));
							filterPricesSubmenu.add(filterGEPrice);
							final JMenuItem filterGETotal = new JMenuItem(TEXT_MENU_PRICE_TOTAL_GE);
							filterGETotal.addActionListener(evt -> GuiUtils.setFilterAndEnabled(Long.toString(popupItemData.getGEValue() * getTrueQuantity())));
							filterPricesSubmenu.add(filterGETotal);
							filterSubmenu.add(filterPricesSubmenu);

							filterId.addActionListener(evt -> GuiUtils.setFilterAndEnabled(Integer.toString(popupItemData.getID())));
							filterSubmenu.add(filterId);
							filterUnnotedId.addActionListener(evt -> GuiUtils.setFilterAndEnabled(Integer.toString(popupItemData.getUnnotedID())));
							filterSubmenu.add(filterUnnotedId);
							popupMenu.add(filterSubmenu);
						}


						popupMenu.show(e.getComponent(), e.getX(), e.getY());
					}
					return;
				}
				if (mouseClickedCallback != null)
				{
					mouseClickedCallback.accept(e);
				}
			}
		});
		setPreferredSize(PREFERRED_SIZE);
		setMinimumSize(PREFERRED_SIZE);
	}

	ItemLabel(TradeItemData itemData)
	{
		this(itemData, null, -1L);
	}

	private static String getCurrentItemName()
	{
		return popupItemName;
	}

	public long getTrueQuantity()
	{
		return quantityOverride == -1 ? itemData.getQuantity() : quantityOverride;
	}

}
