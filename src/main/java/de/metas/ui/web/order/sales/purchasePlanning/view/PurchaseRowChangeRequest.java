package de.metas.ui.web.order.sales.purchasePlanning.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.Check;

import de.metas.quantity.Quantity;
import de.metas.ui.web.window.datatypes.json.JSONDocumentChangedEvent;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2018 metas GmbH
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

@Value
public class PurchaseRowChangeRequest
{
	Quantity qtyToPurchase;
	BigDecimal qtyToPurchaseWithoutUOM;

	LocalDateTime purchaseDatePromised;

	@Builder
	private PurchaseRowChangeRequest(
			final BigDecimal qtyToPurchaseWithoutUOM,
			final Quantity qtyToPurchase,
			final LocalDateTime purchaseDatePromised)
	{
		if (qtyToPurchase != null && qtyToPurchaseWithoutUOM != null)
		{
			throw new AdempiereException("Only qtyToPurchase or qtyToPurchaseWithoutUOM shall be specified but not both");
		}

		this.qtyToPurchase = qtyToPurchase;
		this.qtyToPurchaseWithoutUOM = qtyToPurchaseWithoutUOM;

		this.purchaseDatePromised = purchaseDatePromised;
	}

	public static PurchaseRowChangeRequest of(@NonNull final List<JSONDocumentChangedEvent> fieldChangeRequests)
	{
		Check.assumeNotEmpty(fieldChangeRequests, "field change requests shall not be empty");

		final PurchaseRowChangeRequestBuilder builder = builder();

		for (final JSONDocumentChangedEvent fieldChangeRequest : fieldChangeRequests)
		{
			final String fieldName = fieldChangeRequest.getPath();
			if (PurchaseRow.FIELDNAME_QtyToPurchase.equals(fieldName))
			{
				final BigDecimal qtyToPurchase = fieldChangeRequest.getValueAsBigDecimal();
				Check.assumeNotNull(qtyToPurchase, "Parameter qtyToPurchase is not null for {}", fieldChangeRequest);
				builder.qtyToPurchaseWithoutUOM(qtyToPurchase);
			}
			else if (PurchaseRow.FIELDNAME_DatePromised.equals(fieldName))
			{
				final LocalDateTime datePromised = fieldChangeRequest.getValueAsLocalDateTime();
				Check.assumeNotNull(datePromised, "Parameter datePromised is not null for {}", fieldChangeRequest);
				builder.purchaseDatePromised(datePromised);
			}
			else
			{
				throw new AdempiereException("Field " + fieldName + " is not editable");
			}
		}

		return builder.build();
	}
}
