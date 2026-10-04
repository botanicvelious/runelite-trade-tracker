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

// contains data used to describe an item stack at the time of a trade
public class TradeItemData
{
	private final int id;                   // original id (maybe noted)
	private final int num;                  // the item quantity
	private transient int unnotedId = 0;    // Optional unnoted id if this.id is noted. Dwarf remains (id=0) can never be noted so this should be ok
	private long ge;                    // this is the GE value at the time of the trade and should not be updated

	TradeItemData(int id, int quantity, long value)
	{
		this.id = id;
		this.num = quantity;
		this.ge = value;
	}

	public TradeItemData(int id, int quantity)
	{
		this(id, quantity, -1);
	}

	public TradeItemData(TradeItemData other)
	{
		this.id = other.id;
		this.num = other.num;
		this.ge = other.ge;
		this.unnotedId = other.unnotedId;
	}

	public final boolean isNoted()
	{
		return unnotedId > 0;
	}

	public final int getID()
	{
		return id;
	}

	public final int getUnnotedID()
	{
		return isNoted() ? unnotedId : id;
	}

	public final int getQuantity()
	{
		return num;
	}

	public final long getGEValue()
	{
		return ge;
	}

	public void setGEValue(final long value)
	{
		setGEValue(value, false);
	}

	public final int getHaValue()
	{
		return TradeUtils.getHaPrice(getUnnotedID());
	}

	public final int getLaValue()
	{
		return TradeUtils.getLaPrice(getUnnotedID());
	}

	public final long getConfiguredValue()
	{
		return TradeUtils.getConfiguredPrice(this);
	}

	public void setGEValue(final long value, final boolean override)
	{
		if (ge == -1 || override)
		{
			ge = value;
		}
	}

	public void setUnnotedId(final int unnotedId)
	{
		if (isNoted())
		{
			return;
		}
		this.unnotedId = unnotedId;
	}

}
