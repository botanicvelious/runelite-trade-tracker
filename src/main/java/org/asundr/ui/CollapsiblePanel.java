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

import javax.swing.*;
import java.awt.*;

class CollapsiblePanel extends JPanel
{
	protected final JPanel contentPanel;
	protected final JPanel footerPanel;
	protected final JButton toggleButton;
	private boolean isExpanded = false;

	CollapsiblePanel()
	{
		setLayout(new BorderLayout());
		toggleButton = new JButton("");
		contentPanel = new JPanel();
		footerPanel = new JPanel();
		toggleButton.addActionListener(e -> toggleCollapsed());
		add(toggleButton, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);
		add(footerPanel, BorderLayout.SOUTH);
		contentPanel.setVisible(isExpanded);
	}

	// Changes the collapsed state of the panel to open if closed, and closed if open
	public void toggleCollapsed()
	{
		isExpanded = !isExpanded;
		onToggleCollapsed();
		contentPanel.setVisible(isExpanded);
		revalidate();
		repaint();
	}

	// Overrideable function to respond to when the panel changes its collapsed state
	protected void onToggleCollapsed()
	{
	}

	public final boolean isCollapsed()
	{
		return !isExpanded;
	}

	// Explicitly set the collapsed state. Does nothing if already in the state
	public void setCollapsed(boolean newCollapsed)
	{
		if (newCollapsed == !isExpanded)
		{
			return;
		}
		toggleCollapsed();
	}

}
