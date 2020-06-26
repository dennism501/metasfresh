package de.metas.handlingunits.allocation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import javax.annotation.Nullable;

import org.adempiere.model.InterfaceWrapperHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.metas.handlingunits.HUTestHelper;
import de.metas.handlingunits.HuPackingInstructionsItemId;
import de.metas.handlingunits.IMutableHUContext;
import de.metas.handlingunits.allocation.IAllocationRequest;
import de.metas.handlingunits.allocation.IAllocationResult;
import de.metas.handlingunits.allocation.IAllocationStrategyFactory;
import de.metas.handlingunits.hutransaction.IHUTransactionCandidate;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.model.I_M_HU_Item;
import de.metas.handlingunits.model.X_M_HU_Item;
import de.metas.quantity.Capacity;
import de.metas.util.time.SystemTime;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class UpperBoundsAllocationStrategyTests
{
	private static final BigDecimal sixThousand = new BigDecimal("6000");

	private HUTestHelper helper;

	/**
	 * a hu that is set up in {@link #init()} to contain one virtual {@link I_M_HU_Item}.
	 */
	private I_M_HU vhu;

	@BeforeEach
	public void init()
	{
		helper = new HUTestHelper();
		helper.init();

		vhu = InterfaceWrapperHelper.newInstance(I_M_HU.class);
		InterfaceWrapperHelper.save(vhu);

		// create the hu-item that shall hold the tomatoes
		final I_M_HU_Item vhuItem = InterfaceWrapperHelper.newInstance(I_M_HU_Item.class);
		vhuItem.setM_HU(vhu);
		vhuItem.setM_HU_PI_Item_ID(HuPackingInstructionsItemId.VIRTUAL.getRepoId());
		vhuItem.setItemType(X_M_HU_Item.ITEMTYPE_Material);
		InterfaceWrapperHelper.save(vhuItem);
	}

	private static UpperBoundAllocationStrategy createUpperBoundAllocationStrategy(@Nullable Capacity capacity)
	{
		final AllocationStrategySupportingServicesFacade services = AllocationStrategySupportingServicesFacade.newInstance();
		final IAllocationStrategyFactory strategyFactory = null;
		return new UpperBoundAllocationStrategy(capacity, services, strategyFactory);
	}

	/**
	 * Verifies that if there is a virtual HU item with type {@link X_M_HU_Item#ITEMTYPE_Material} then the full qty is allocated to that item.
	 */
	@Test
	public void testAllocateAllToVirtualItemWithoutUpperBound()
	{
		// allocate this, despite it not really fitting (because that's not the testee's business).
		final BigDecimal requestQty = sixThousand.add(new BigDecimal("0.321"));
		final IAllocationRequest request = mkRequest(requestQty);

		final UpperBoundAllocationStrategy testee = createUpperBoundAllocationStrategy(null);
		final IAllocationResult result = testee.execute(vhu, request);

		assertThat(result.isCompleted()).isTrue();
		assertThat(result.getQtyAllocated()).isEqualByComparingTo(requestQty);
		assertThat(result.getQtyToAllocate()).isZero();
		assertThat(result.getTransactions()).hasSize(1);

		final IHUTransactionCandidate huTransaction = result.getTransactions().get(0);
		assertThat(huTransaction.getProductId()).isEqualTo(helper.pTomatoProductId);
		assertThat(huTransaction.getQuantity().toBigDecimal()).isEqualTo(requestQty);
		assertThat(huTransaction.getQuantity().getUOM()).isEqualTo(helper.uomKg);
		assertThat(huTransaction.getM_HU()).isEqualTo(vhu);
	}

	/**
	 * Similar to {@link #testAllocateAllToVirtualItemWithoutUpperBound()}, but in this test, the testee has an upper bound to the quantity that may be allocated.
	 */
	@Test
	public void testAllocateAllToVirtualItemWithUpperBound()
	{
		// request this, note that the 0.321 are above the upper build that we will specify.
		final BigDecimal requestQty = sixThousand.add(new BigDecimal("0.321"));

		final IAllocationRequest request = mkRequest(requestQty);

		final Capacity capacity = Capacity.createCapacity(sixThousand, helper.pTomatoProductId, helper.uomKg, false);

		final UpperBoundAllocationStrategy testee = createUpperBoundAllocationStrategy(capacity);
		final IAllocationResult result = testee.execute(vhu, request);

		assertThat(result.isCompleted()).isFalse();
		assertThat(result.getQtyAllocated()).isEqualByComparingTo(sixThousand);
		assertThat(result.getQtyToAllocate()).isEqualTo("0.321");
		assertThat(result.getTransactions()).hasSize(1);

		final IHUTransactionCandidate huTransaction = result.getTransactions().get(0);
		assertThat(huTransaction.getProductId()).isEqualTo(helper.pTomatoProductId);
		assertThat(huTransaction.getQuantity().toBigDecimal()).isEqualTo(sixThousand);
		assertThat(huTransaction.getQuantity().getUOM()).isEqualTo(helper.uomKg);
		assertThat(huTransaction.getM_HU()).isEqualTo(vhu);
	}

	private IAllocationRequest mkRequest(final BigDecimal qtyTomatoes)
	{
		final IMutableHUContext huContext0 = helper.createMutableHUContextOutOfTransaction();

		return AllocationUtils.createQtyRequest(
				huContext0,
				helper.pTomato, // product
				qtyTomatoes, // qty
				helper.uomKg, // uom
				SystemTime.asZonedDateTime());
	}
}
