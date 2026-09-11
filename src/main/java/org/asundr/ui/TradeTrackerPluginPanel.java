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

import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;
import org.asundr.TradeTrackerConfig;
import org.asundr.recovery.ConfigKey;
import org.asundr.recovery.EventTradeTrackerProfileChanged;
import org.asundr.recovery.SaveManager;
import org.asundr.trade.*;
import org.asundr.utility.CommonUtils;
import org.asundr.utility.StringUtils;
import org.asundr.utility.TimeUtils;
import org.asundr.trade.TradeData;
import org.asundr.trade.TradeUtils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TradeTrackerPluginPanel extends PluginPanel
{
	private static final int TIME_RESTART_TRADE_RECORD_REFRESH = 30; // seconds
	private static final int TRADE_RECORD_PADDING = 10;
	private static final int HEADER_PADDING = 7;
	private static final int HEADER_FILTER_ENTRY_HEIGHT = 25;
	private static final int HEADER_HEIGHT_DEFAULT = 100;
	private static final int SIZE_FILTER_BUTTON_X = 15;
	private static final int HEADER_HEIGHT_FILTERING = HEADER_HEIGHT_DEFAULT + (HEADER_FILTER_ENTRY_HEIGHT * 2 ) + 4;
	private static final Dimension DIMENSION_FILTER_WRAPPER = new Dimension(PANEL_WIDTH, HEADER_FILTER_ENTRY_HEIGHT);
	private static final Dimension DIMENSION_FILTER_TEXT = new Dimension(PANEL_WIDTH - SIZE_FILTER_BUTTON_X, HEADER_FILTER_ENTRY_HEIGHT);
	private static final Dimension DIMENSION_CLEAR_FILTER_WRAPPER = new Dimension(SIZE_FILTER_BUTTON_X, HEADER_FILTER_ENTRY_HEIGHT);
	private static final Dimension DIMENSION_CLEAR_FILTER_BUTTON = new Dimension(SIZE_FILTER_BUTTON_X - 9, HEADER_FILTER_ENTRY_HEIGHT - 16);
	private final static Color COLOR_TRANSPARENT = new Color(0, 0, 0, 0);
	private final static Color COLOR_HEADER_BACKGROUND = new Color(15, 15, 25);
	private final static Color COLOR_FILTER_TEXT_BACKGROUND = new Color(10, 10, 10);
	private final static Color COLOR_TOOLBAR_BACKGROUND = new Color(40, 40, 0);
	private final static Color COLOR_CLEAR_FILTER_BACKGROUND = new Color(80, 0, 0);
	private static final Color COLOR_BUTTON_BACKGROUND = new Color(20, 20, 30);
	private final static String TEMPLATE_EMPTY_LIST = "<html><body style='text-align:center'><span style='font-size:12px;color:white'>%s</span><br><span style='font-size:10px;color:#939393'>%s</span></body></html>";
	private final static String TEMPLATE_SUBTITLE = "<html><span style='font-size:13;color:white'><nobr>%s <span style='color:#909090'>%s</span></nobr></span><html>";
	private final static String TEMPLATE_PURGE_TOOLTIP = "<html><span>%s auto-remove old trades</span></html>";
	private final static String TEMPLATE_PURGE_TOOLTIP_AUTO = "<html><span>%s auto-remove old trades</span><br><span>Lifetime: %s %s</span></html>";
	private final static Border BORDER_EMPTY = BorderFactory.createEmptyBorder(0, 0, 0, 0);
	private final static Border BORDER_FILTER_TEXT_WRAPPER = BorderFactory.createLineBorder(new Color(90, 90, 30), 1);
	private final static Border BORDER_FILTER_TEXT = BorderFactory.createEmptyBorder(0, 5, 0, 5);
	private final static Border BORDER_HISTORY_PANEL = BorderFactory.createEmptyBorder(4, 2, 2, 3);
	private final static Border BORDER_TOOLBAR = BorderFactory.createLineBorder(new Color(40, 40, 0), 3);
	private final static Border BORDER_PADDING_CLEAR_FILTER_WRAPPER = BorderFactory.createEmptyBorder(6, 3, 6, 5);
	private final static Border BORDER_PADDING_CLEAR_FILTER_BUTTON = BorderFactory.createEmptyBorder(0, 0, 0, 0);
	final JPanel filterTextWrapper = new JPanel();
	final JPanel filterTotal = new JPanel();
	JLabel filterTotalLabel = new JLabel(String.format(TEMPLATE_EMPTY_LIST, "Trade History", "No trades have been recorded"));
	final JTextField filterText = new JTextField();
	final JPanel clearFilterWrapper = new JPanel();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private final ExecutorService executor = Executors.newFixedThreadPool(1);
	private final JPanel headerPanel = new JPanel();
	private final JPanel tradeHistoryPanel = new JPanel();
	private final JPanel emptyHistoryPanel = new JPanel();
	private final JLabel emptyHistoryLabel = new JLabel(String.format(TEMPLATE_EMPTY_LIST, "Trade History", "No trades have been recorded"));
	private final JLabel emptyFilterLabel = new JLabel(String.format(TEMPLATE_EMPTY_LIST, "Filter Results", "No recorded trades match your filter"));
	private final JLabel emptyLoadingLabel = new JLabel(String.format(TEMPLATE_EMPTY_LIST, "Loading History...", "Please wait until all records have been loaded"));
	private final JLabel profileNameLabel = new JLabel();
	final private JPopupMenu subtitlePopup = new JPopupMenu();
	ToolbarButton btnFilter;
	private ScheduledFuture<?> scheduledUpdateTimeFuture = null;
	private Future<?> uiExecutorFuture = null;
	private ToolbarButton btnSchedulePurge;
	private ToolbarButton btnToggleCollapseAll;

	public TradeTrackerPluginPanel()
	{
		super(false); // disables scrolling
		final int panelWidth = getPreferredSize().width;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		// Setup header panel
		headerPanel.setPreferredSize(new Dimension(panelWidth, HEADER_HEIGHT_DEFAULT));
		headerPanel.setMinimumSize(new Dimension(panelWidth, HEADER_HEIGHT_DEFAULT));
		buildHeader();

		// Setup history panel
		tradeHistoryPanel.setLayout(new BoxLayout(tradeHistoryPanel, BoxLayout.Y_AXIS));
		tradeHistoryPanel.setBorder(BORDER_HISTORY_PANEL);
		JScrollPane tradeHistoryScroll = new JScrollPane(tradeHistoryPanel);
		tradeHistoryScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		tradeHistoryScroll.setPreferredSize(new Dimension(panelWidth, 2000));
		// Create custom scroll bar
		JScrollBar customScrollBar = new JScrollBar(JScrollBar.VERTICAL);
		Dimension preferredSize = new Dimension(6, Integer.MAX_VALUE);
		customScrollBar.setPreferredSize(preferredSize);
		tradeHistoryScroll.setVerticalScrollBar(customScrollBar);
		// Set empty trade history visibility
		updateEmptyHistoryMessages();

		add(headerPanel);
		add(emptyHistoryPanel);
		add(tradeHistoryScroll);
	}

	public void shutdown()
	{
		scheduler.shutdownNow();
		executor.shutdownNow();
	}

	@Subscribe
	private void onEventTradeTrackerProfileChanged(EventTradeTrackerProfileChanged evt)
	{
		profileNameLabel.setVisible(evt.newProfile != null);
		if (evt.newProfile != null)
		{
			profileNameLabel.setText(String.format(TEMPLATE_SUBTITLE, evt.newProfile.getPlayerName(), evt.newProfile.getTypeString()));
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged e)
	{
		if (e.getGroup().equals(SaveManager.SAVE_GROUP))
		{
			switch (e.getKey())
			{
				case ConfigKey.USE_24_HOUR_TIME:
					executor.execute(() -> getTradeRecordPanels().forEach(TradeRecordPanel::updateTimeDisplay));
					break;
				case ConfigKey.PURGE_HISTORY_TYPE:
				case ConfigKey.PURGE_HISTORY_MAGNITUDE:
					if (TradeManager.isPurgingExpiredTrades())
					{
						btnSchedulePurge.setActive(false);
						JOptionPane.showMessageDialog(
								null,
								"Auto-remove as been disabled, re-enable it on the toolbar",
								"Enable auto-removing expired trades?",
								JOptionPane.PLAIN_MESSAGE
						);
					}
					break;
				case ConfigKey.DEFAULT_PRICE_TYPE:
					executor.execute(() -> getTradeRecordPanels().parallelStream().forEach(TradeRecordPanel::updateItemPriceType));
					break;
			}
		}

	}

	@Subscribe
	private void onEventTradeAdded(EventTradeAdded e)
	{
		addTradeRecord(e.tradeData);
	}

	@Subscribe
	private void onEventTradeRemoved(EventTradeRemoved e)
	{
		removeTradeRecord(e.tradeData);
	}

	@Subscribe
	private void onEventTradeResetHistory(EventTradeResetHistory e)
	{
		replaceAllTradeRecords(e.newTradeHistory);
	}

	public final Collection<TradeRecordPanel> getTradeRecordPanels()
	{
		return Arrays.stream(tradeHistoryPanel.getComponents()).filter(e -> e instanceof TradeRecordPanel).map(e -> (TradeRecordPanel) e).collect(Collectors.toList());
	}

	private void buildHeader()
	{
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
		headerPanel.setBackground(COLOR_HEADER_BACKGROUND);

		headerPanel.add(Box.createVerticalStrut(HEADER_PADDING));

		// Create title panel
		final JPanel titleWrapper = new JPanel();
		final JLabel titleLabel = new JLabel("<html><span style='font-size:16;color:yellow'><b><nobr>Trade Tracker</nobr></b></span><html><br>");
		titleLabel.setBorder(BORDER_EMPTY);
		titleWrapper.add(titleLabel);
		titleWrapper.setBackground(COLOR_HEADER_BACKGROUND);
		titleLabel.setToolTipText("Created by asundr");
		titleWrapper.setBorder(BORDER_EMPTY);
		titleWrapper.setPreferredSize(new Dimension(PANEL_WIDTH, 20));
		headerPanel.add(titleWrapper, CENTER_ALIGNMENT);

		// Create popup items for saving and loading history to disk
		final JMenuItem saveHistoryMenu = new JMenuItem("Save profile to file");
		saveHistoryMenu.addActionListener(a -> SaveManager.saveTradeHistoryToFile());
		final JMenuItem loadHistoryMenu = new JMenuItem(("Load profile from file"));
		loadHistoryMenu.addActionListener(a -> SaveManager.loadTradeHistoryFromFile());
		final JMenuItem refreshCurrentMenu = new JMenuItem("Refresh current trade history");
		refreshCurrentMenu.addActionListener(a -> replaceAllTradeRecords(TradeManager.getTradeHistory()));
		refreshCurrentMenu.setToolTipText("Updates the trade history UI using the currently loaded history");
		final JMenuItem saveCurrentMenu = new JMenuItem("Save current trade history");
		saveCurrentMenu.addActionListener(a -> SaveManager.requestTradeHistorySave());
		subtitlePopup.add(saveHistoryMenu);
		subtitlePopup.add(loadHistoryMenu);
		subtitlePopup.addSeparator();
		subtitlePopup.add(refreshCurrentMenu);
		subtitlePopup.add(saveCurrentMenu);

		// Create subtitle panel and setup popup events
		final JPanel subtitleWrapper = new JPanel();
		profileNameLabel.setVisible(false);
		if (SaveManager.getActiveProfile() != null && CommonUtils.getConfig().getAutoLoadLastProfile())
		{
			profileNameLabel.setText(String.format(TEMPLATE_SUBTITLE, SaveManager.getActiveProfile().getPlayerName(), SaveManager.getActiveProfile().getTypeString()));
			profileNameLabel.setVisible(true);
		}
		subtitleWrapper.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				super.mouseClicked(e);
				if (e.getButton() == MouseEvent.BUTTON3)
				{
					subtitlePopup.show(subtitleWrapper, e.getX(), e.getY());
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				super.mouseEntered(e);
				subtitleWrapper.setToolTipText(String.format("%s trades logged", QuantityFormatter.formatNumber(TradeManager.getTradeHistoryCount())));
			}
		});
		subtitleWrapper.setBackground(COLOR_HEADER_BACKGROUND);
		subtitleWrapper.add(profileNameLabel);
		profileNameLabel.setBorder(BORDER_EMPTY);
		subtitleWrapper.setBorder(BORDER_EMPTY);
		headerPanel.add(subtitleWrapper);

		// Setting up the toolbar panel
		buildToolbar();
	}

	private void buildToolbar()
	{
		final JPanel toolbarPanel = new JPanel();
		toolbarPanel.setBackground(COLOR_TOOLBAR_BACKGROUND);
		toolbarPanel.setBorder(BORDER_TOOLBAR);
		headerPanel.add(toolbarPanel);

		final GridBagConstraints gbc = new GridBagConstraints();
		toolbarPanel.setLayout(new GridBagLayout());
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.gridx = 1;

		// Adding button to clear all trades
		final ToolbarButton btnClearAll = new ToolbarButton(
				"clear_all.png", null,
				"Clear trade history", "Clear trade history",
				false, null);
		btnClearAll.setOnToggledActive(active ->
		{
			final int response = JOptionPane.showConfirmDialog(
					btnClearAll,
					"Are you sure you want to permanently remove all trades?",
					"Clear all trades?",
					JOptionPane.YES_NO_OPTION
			);
			if (response == JOptionPane.YES_OPTION)
			{
				TradeManager.requestClearAllTradeRecords();
			}
		});
		gbc.gridy = 1;
		toolbarPanel.add(btnClearAll, gbc);

		// Adding button to toggle auto-removing expired trades
		final boolean isPurgingExpired = TradeManager.isPurgingExpiredTrades();
		btnSchedulePurge = new ToolbarButton(
				"schedule_purge_on.png", "schedule_purge_off.png",
				String.format(TEMPLATE_PURGE_TOOLTIP, "Disable"), String.format(TEMPLATE_PURGE_TOOLTIP, "Enable"),
				isPurgingExpired, TradeManager::setPurgingExpiredTrades);
		btnSchedulePurge.setOnToggledValidate(active ->
		{
			if (!active)
			{
				return true;
			}
			if (CommonUtils.getConfig().getPurgeHistoryType() == TradeTrackerConfig.PurgeHistoryType.NEVER)
			{
				JOptionPane.showMessageDialog(
						btnSchedulePurge,
						"'Auto-remove type' is set to Never, or 'Auto-remove length' less than one.\n\nChange this in the TradeTracker config.",
						"Enable auto-removing expired trades?",
						JOptionPane.PLAIN_MESSAGE
				);
				return false;
			}
			final long numExpired = getTradeRecordPanels().stream().filter(e -> e.getTradeData().isExpired()).count();
			if (numExpired == 0)
			{
				return true;
			}
			final int response = JOptionPane.showConfirmDialog(
					btnSchedulePurge,
					"Enabling this will immediately remove " + numExpired + " trades that have already expired.\n\nTrade lifetime can be updated in the Trade Tracker config.\n\nContinue?",
					"Enable auto-removing expired trades?",
					JOptionPane.YES_NO_OPTION
			);
			return response == JOptionPane.YES_OPTION;
		});
		btnSchedulePurge.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				super.mouseEntered(e);
				var purgeType = CommonUtils.getConfig().getPurgeHistoryType();
				final int purgeMag = CommonUtils.getConfig().getPurgeHistoryMagnitude();
				final boolean isNever = purgeType == TradeTrackerConfig.PurgeHistoryType.NEVER || purgeMag < 1;
				if (isNever) purgeType = TradeTrackerConfig.PurgeHistoryType.NEVER;
				btnSchedulePurge.setToolTipText(String.format(TEMPLATE_PURGE_TOOLTIP_AUTO,
						btnSchedulePurge.isActive() ? "Disable" : "Enable",
						isNever ? "" : purgeMag,
						StringUtils.formatEnum(purgeType, isNever) + (!isNever && purgeMag > 1 ? "s" : ""))
				);
			}
		});
		gbc.gridx += 2;
		toolbarPanel.add(btnSchedulePurge, gbc);

		// Adding button to collapse / expand trade panels
		btnToggleCollapseAll = new ToolbarButton(
				"expand_all.png", "collapse_all.png",
				"Collapse all", "Expand all", false, null);
		btnToggleCollapseAll.setOnToggledActive(active -> getTradeRecordPanels().forEach(panel -> panel.setCollapsed(!active)));
		gbc.gridx += 2;
		toolbarPanel.add(btnToggleCollapseAll, gbc);

		// Setting up text entry field for filtering trades
		filterTextWrapper.setPreferredSize(DIMENSION_FILTER_WRAPPER);
		filterTextWrapper.setMinimumSize(DIMENSION_FILTER_WRAPPER);
		filterTextWrapper.setMaximumSize(DIMENSION_FILTER_WRAPPER);
		filterTextWrapper.setLayout(new BoxLayout(filterTextWrapper, BoxLayout.X_AXIS));
		filterTextWrapper.setBorder(BORDER_FILTER_TEXT_WRAPPER);
		filterTextWrapper.setBackground(COLOR_FILTER_TEXT_BACKGROUND);
		filterTextWrapper.add(filterText);

		// Setting up text entry field filtering trades totals
		filterTotal.setPreferredSize(DIMENSION_FILTER_WRAPPER);
		filterTotal.setMinimumSize(DIMENSION_FILTER_WRAPPER);
		filterTotal.setMaximumSize(DIMENSION_FILTER_WRAPPER);
		filterTotal.setLayout(new BorderLayout());
		filterTotal.setBorder(BorderFactory.createLineBorder(COLOR_BUTTON_BACKGROUND, 4));
		filterTotal.setBackground(COLOR_FILTER_TEXT_BACKGROUND);
		filterTotal.add(filterTotalLabel);

		clearFilterWrapper.setLayout(new BorderLayout());
		clearFilterWrapper.setPreferredSize(DIMENSION_CLEAR_FILTER_WRAPPER);
		clearFilterWrapper.setMinimumSize(DIMENSION_CLEAR_FILTER_WRAPPER);
		clearFilterWrapper.setMaximumSize(DIMENSION_CLEAR_FILTER_WRAPPER);
		clearFilterWrapper.setBorder(BORDER_PADDING_CLEAR_FILTER_WRAPPER);
		clearFilterWrapper.setBackground(COLOR_TRANSPARENT);

		final JButton clearFilterButton = new JButton("×");
		clearFilterButton.addActionListener(e -> SwingUtilities.invokeLater(this::clearFilter));
		clearFilterButton.setPreferredSize(DIMENSION_CLEAR_FILTER_BUTTON);
		clearFilterButton.setMinimumSize(DIMENSION_CLEAR_FILTER_BUTTON);
		clearFilterButton.setMaximumSize(DIMENSION_CLEAR_FILTER_BUTTON);
		clearFilterButton.setBackground(COLOR_CLEAR_FILTER_BACKGROUND);
		clearFilterButton.setBorder(BORDER_PADDING_CLEAR_FILTER_BUTTON);
		clearFilterButton.setToolTipText("Clear");
		clearFilterWrapper.setVisible(false);
		clearFilterWrapper.add(clearFilterButton, BorderLayout.CENTER);
		filterTextWrapper.add(clearFilterWrapper);

		headerPanel.add(filterTextWrapper);
		headerPanel.add(filterTotal);
		headerPanel.add(Box.createVerticalStrut(HEADER_PADDING / 2));
		filterText.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateFilter(filterText.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateFilter(filterText.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
			}
		});
		filterText.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				switch (e.getKeyCode())
				{
					case KeyEvent.VK_ESCAPE:
						btnFilter.setActive(false);
						break;
					case KeyEvent.VK_BACK_SPACE:
						if (filterText.getText().isEmpty())
						{
							e.consume();
						}
						break;
				}
				SwingUtilities.invokeLater(() -> clearFilterWrapper.setVisible(!filterText.getText().isEmpty()));
			}
		});
		filterText.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				super.mouseClicked(e);
				if (e.getButton() == MouseEvent.BUTTON3)
				{
					JPopupMenu filterTextPopup = new JPopupMenu();
					JMenuItem clearFilterTextItem = new JMenuItem("Clear");
					clearFilterTextItem.addActionListener(event -> SwingUtilities.invokeLater(TradeTrackerPluginPanel.this::clearFilter));
					filterTextPopup.add(clearFilterTextItem);
					filterTextPopup.show(filterText, e.getX(), e.getY());
				}
			}
		});
		filterText.setBackground(COLOR_TRANSPARENT);
		filterText.setBorder(BORDER_FILTER_TEXT);
		filterText.setPreferredSize(DIMENSION_FILTER_TEXT);
		filterText.setMinimumSize(DIMENSION_FILTER_TEXT);
		filterText.setMaximumSize(DIMENSION_FILTER_TEXT);
		filterTextWrapper.setVisible(false);

		filterTotal.setPreferredSize(DIMENSION_FILTER_TEXT);
		filterTotal.setMinimumSize(DIMENSION_FILTER_TEXT);
		filterTotal.setVisible(true);


		// Setting up button for toggling the filter
		btnFilter = new ToolbarButton(
				"filter_on.png", "filter_off.png",
				"Disable filter", "Enable filter",
				false, this::toggleFilter)
		{
			@Override
			protected void actionListenerCallback(ActionEvent e)
			{
				super.actionListenerCallback(e);
				if (isActive())
				{
					filterText.requestFocusInWindow();
				}
			}
		};
		gbc.gridx += 2;
		toolbarPanel.add(btnFilter, gbc);
	}

	private void toggleFilter(boolean active)
	{
		filterTextWrapper.setVisible(active);
		filterTotal.setVisible(active);
		final int headerHeight = active ? HEADER_HEIGHT_FILTERING : HEADER_HEIGHT_DEFAULT;
		headerPanel.setPreferredSize(new Dimension(PANEL_WIDTH, headerHeight));
		headerPanel.setMinimumSize(new Dimension(PANEL_WIDTH, headerHeight));
		headerPanel.setMaximumSize(new Dimension(PANEL_WIDTH, headerHeight));
		updateFilter(active ? filterText.getText() : "");
		revalidate();
		repaint();
	}

	// Adds new trade panel to the history in response to new trade being added
	private void addTradeRecord(TradeData tradeData)
	{
		CommonUtils.getClientThread().invokeLater(() ->
		{
			final TradeRecordPanel tradeRecordPanel = new TradeRecordPanel(tradeData);
			tradeRecordPanel.paddingStrut = Box.createVerticalStrut(TRADE_RECORD_PADDING);
			if (btnToggleCollapseAll.isActive())
			{
				tradeRecordPanel.setCollapsed(false);
			}
			if (btnFilter.isActive() && !tradeRecordPanel.match(filterText.getText()))
			{
				tradeRecordPanel.toggleVisible(false);
			}
			tradeHistoryPanel.add(tradeRecordPanel.paddingStrut, 0);
			tradeHistoryPanel.add(tradeRecordPanel, 0);
			updateEmptyHistoryMessages();
		});
	}

	// Removes a trade panel with the passed trade data in response to trade history removing trade
	private void removeTradeRecord(TradeData tradeData)
	{
		TradeRecordPanel toRemove = null;
		for (Component component : tradeHistoryPanel.getComponents())
		{
			if (component instanceof TradeRecordPanel)
			{
				final TradeRecordPanel curr = (TradeRecordPanel) component;
				if (curr.getTradeTime() == tradeData.tradeTime)
				{
					toRemove = curr;
					break;
				}
			}
		}
		if (toRemove == null)
		{
			return;
		}
		tradeHistoryPanel.remove(toRemove.paddingStrut);
		tradeHistoryPanel.remove(toRemove);
		updateEmptyHistoryMessages();
		updateUI();
	}

	// Removes all trade panels
	private void clearAllTradeRecords()
	{
		tradeHistoryPanel.removeAll();
		updateEmptyHistoryMessages();
		updateUI();
	}

	// Batch adds all trade panels from a trade history collection, replacing existing panels
	private void replaceAllTradeRecords(final Collection<TradeData> tradeHistory)
	{
		if (CommonUtils.isThreadActive(scheduledUpdateTimeFuture))
		{
			scheduledUpdateTimeFuture.cancel(true);
		}
		scheduledUpdateTimeFuture = scheduler.schedule(() ->
		{
			if (CommonUtils.isThreadActive(uiExecutorFuture))
			{
				uiExecutorFuture.cancel(true);
			}
			CommonUtils.getClientThread().invokeLater(() -> replaceAllTradeRecords(tradeHistory));
		}, TIME_RESTART_TRADE_RECORD_REFRESH, TimeUnit.SECONDS);

		clearAllTradeRecords();
		if (tradeHistory == null || tradeHistory.isEmpty())
		{
			updateEmptyHistoryMessages();
			if (scheduledUpdateTimeFuture != null)
			{
				scheduledUpdateTimeFuture.cancel(true);
				scheduledUpdateTimeFuture = null;
			}
			return;
		}
		btnFilter.setActive(false);
		tradeHistoryPanel.setVisible(false);
		updateEmptyHistoryMessages();
		CommonUtils.getClientThread().invoke(() ->
		{
			try // handle getting item composition failing gracefully
			{
				for (final TradeData tradeData : tradeHistory)
				{
					TradeUtils.fetchCompositionData(tradeData.givenItems);
					TradeUtils.fetchCompositionData(tradeData.receivedItems);
					tradeData.calculateAggregateValues();
				}
			} catch (Exception e)
			{
				return;
			}
			uiExecutorFuture = executor.submit(() ->
			{
				final List<TradeRecordPanel> panels = tradeHistory.parallelStream().map(e ->
				{
					TradeRecordPanel tradeRecordPanel = new TradeRecordPanel(e);
					tradeRecordPanel.paddingStrut = Box.createVerticalStrut(TRADE_RECORD_PADDING);
					return tradeRecordPanel;
				}).collect(Collectors.toList());
				// NOTE: when initial loading fails, it seems to get stuck on the above line
				for (final TradeRecordPanel panel : panels)
				{
					tradeHistoryPanel.add(panel.paddingStrut, 0);
					tradeHistoryPanel.add(panel, 0);
				}
				panels.forEach(TradeRecordPanel::updatePreferredSize); // fix for mis-sized panels on reload all
				tradeHistoryPanel.setVisible(true);
				updateEmptyHistoryMessages();
				if (scheduledUpdateTimeFuture != null)
				{
					scheduledUpdateTimeFuture.cancel(true);
					scheduledUpdateTimeFuture = null;
				}
				uiExecutorFuture = null;
				scheduledTimeLabelUpdate();
			});
		});
	}

	// Toggles the hidden status of trade panels depending on if they match the filter query
	private void updateFilter(final String query)
	{
		if (query.isBlank())
		{
			Arrays.stream(tradeHistoryPanel.getComponents())
					.filter(e -> e instanceof TradeRecordPanel)
					.forEach(e -> ((TradeRecordPanel) e).toggleVisible(true));
			updateEmptyHistoryMessages();
			return;
		}
		Arrays.stream(tradeHistoryPanel.getComponents()).parallel()
				.filter(e -> e instanceof TradeRecordPanel)
				.map(e -> (TradeRecordPanel) e)
				.forEach(e -> e.toggleVisible(e.match(query)));
		updateEmptyHistoryMessages();
		revalidate();
		repaint();
	}

	private void updateTotalPanel()
	{
		long sum;
		String footerPrefix = "";

		if (CommonUtils.getConfig().getFilterTotalType()) {
			sum = getTradeRecordPanels().stream()
					.filter(TradeRecordPanel::isVisible)
					.mapToLong(TradeRecordPanel::getPanelValue)
					.sum();

			footerPrefix = sum < 0 ? "you lost" : "you gained";

		} else
		{
			sum = getTradeRecordPanels().stream()
					.filter(TradeRecordPanel::isVisible)
					.mapToLong(TradeRecordPanel::getPanelValue)
					.map(Math::abs)
					.sum();

			footerPrefix = "you traded";
		}

		filterTotalLabel = new QuantityLabel(sum, "<html>" + "In total " + footerPrefix + ": %s  <span style='color:#909090'>[#P]</span></html>", "%s");
		filterTotal.removeAll();
		filterTotal.add(filterTotalLabel);

		//System.out.println("Array data: " + sum);
	}

	private boolean isRefreshingTradeHistory()
	{
		return !tradeHistoryPanel.isVisible();
	}

	// Toggles the visibility of empty trade history messages in cases where there are
	// no trades or when all existing trades have been filtered out
	private void updateEmptyHistoryMessages()
	{
		emptyHistoryPanel.removeAll();
		emptyHistoryPanel.setVisible(false);
		if (isRefreshingTradeHistory())
		{
			emptyHistoryPanel.add(emptyLoadingLabel);
			emptyHistoryPanel.setVisible(true);
		} else if (tradeHistoryPanel.getComponents().length == 0)
		{
			emptyHistoryPanel.add(emptyHistoryLabel);
			emptyHistoryPanel.setVisible(true);
		} else if (btnFilter != null && btnFilter.isActive())
		{
			boolean noneVisible = true;
			for (final Component comp : tradeHistoryPanel.getComponents())
			{
				if (comp.isVisible())
				{
					noneVisible = false;
					break;
				}
			}
			if (noneVisible)
			{
				emptyHistoryPanel.add(emptyFilterLabel);
				emptyHistoryPanel.setVisible(true);
			}
		}
		updateTotalPanel();
	}

	// Note: Must be called inside a SwingUtilities.invokeLater
	void setFilter(final String text)
	{
		if (!text.equalsIgnoreCase(filterText.getText()))
		{
			filterText.setText(text);
			clearFilterWrapper.setVisible(!text.isEmpty());
		}
	}

	// Note: Must be called inside a SwingUtilities.invokeLater
	void clearFilter()
	{
		filterText.setText("");
		clearFilterWrapper.setVisible(false);
	}

	// Schedule recurring update for time labels at midnight
	private void scheduledTimeLabelUpdate()
	{
		scheduledUpdateTimeFuture = scheduler.scheduleAtFixedRate(
				() -> getTradeRecordPanels().forEach(TradeRecordPanel::updateTimeDisplay),
				TimeUtils.getTimeUntilMidnight() + 1000L,
				TimeUtils.MILLISECONDS_IN_DAY,
				TimeUnit.MILLISECONDS);
	}
}
