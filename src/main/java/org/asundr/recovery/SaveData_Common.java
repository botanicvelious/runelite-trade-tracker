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

import org.asundr.TradeHistoryProfile;

import java.util.ArrayList;

// Serializable data used to store and maintain data common to all profiles
public class SaveData_Common
{
	final private ArrayList<TradeHistoryProfile> savedProfiles = new ArrayList<>();
	private int saveVersion;
	private TradeHistoryProfile activeProfile;

	SaveData_Common()
	{
		this.saveVersion = SaveManager.SAVE_VERSION;
		this.activeProfile = null;
		savedProfiles.add(null);
	}

	public void updateSaveVersion(final int newSaveVersion)
	{
		assert (newSaveVersion > this.saveVersion);
		this.saveVersion = newSaveVersion;
	}

	public final TradeHistoryProfile getActiveProfile()
	{
		return activeProfile;
	}

	public void setActiveProfile(final TradeHistoryProfile activeProfile)
	{
		if (activeProfile != null)
		{
			final int existingIndex = savedProfiles.indexOf(activeProfile);
			if (existingIndex == -1)
			{
				savedProfiles.add(activeProfile);
			} else
			{
				savedProfiles.set(existingIndex, activeProfile);
			}
		}
		this.activeProfile = activeProfile;
	}

	public final ArrayList<TradeHistoryProfile> getSavedProfiles()
	{
		return new ArrayList<>(savedProfiles);
	}

	public void removeSavedProfile(final TradeHistoryProfile profile)
	{
		savedProfiles.removeIf(e -> e == profile);
	}

}
