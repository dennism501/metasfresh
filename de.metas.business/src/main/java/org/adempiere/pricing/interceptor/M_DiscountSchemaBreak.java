package org.adempiere.pricing.interceptor;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.adempiere.ad.modelvalidator.annotations.Interceptor;
import org.adempiere.ad.modelvalidator.annotations.ModelChange;
import org.adempiere.bpartner.service.IBPartnerDAO;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.pricing.api.CalculateDiscountRequest;
import org.adempiere.pricing.api.IEditablePricingContext;
import org.adempiere.pricing.api.IMDiscountSchemaBL;
import org.adempiere.pricing.api.IPricingBL;
import org.adempiere.pricing.api.PricingConditionsBreak;
import org.adempiere.pricing.api.PricingConditionsBreakMatchCriteria;
import org.adempiere.pricing.api.impl.MDiscountSchemaDAO;
import org.adempiere.pricing.limit.PriceLimitRuleContext;
import org.adempiere.pricing.limit.PriceLimitRuleResult;
import org.adempiere.util.Services;
import org.compiere.model.I_M_DiscountSchemaBreak;
import org.compiere.model.ModelValidator;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import de.metas.logging.LogManager;
import de.metas.product.IProductBL;
import lombok.NonNull;

/*
 * #%L
 * de.metas.business
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

@Interceptor(I_M_DiscountSchemaBreak.class)
@Component
public class M_DiscountSchemaBreak
{
	private static final Logger logger = LogManager.getLogger(M_DiscountSchemaBreak.class);

	private static final String MSG_UnderLimitPriceWithExplanation = "UnderLimitPriceWithExplanation";

	@ModelChange(timings = { ModelValidator.TYPE_BEFORE_NEW, ModelValidator.TYPE_BEFORE_CHANGE })
	public void beforeSave(final I_M_DiscountSchemaBreak schemaBreak)
	{
		// If M_Product_ID and M_Product_Category_ID is set, M_Product_ID has priority - teo_sarca [ 2012439 ]
		if (schemaBreak.getM_Product_ID() > 0 && schemaBreak.getM_Product_Category_ID() > 0)
		{
			schemaBreak.setM_Product_Category_ID(0);
		}

		//
		// Validate
		try
		{
			enforcePriceLimit(schemaBreak);

			schemaBreak.setIsValid(true);
			schemaBreak.setNotValidReason(null);
		}
		catch (AdempiereException ex)
		{
			// NOTE: catch only AdempiereExceptions. All others like NPE etc shall be propagated.
			logger.debug("Schema break become invalid", ex);
			schemaBreak.setIsValid(false);
			schemaBreak.setNotValidReason(AdempiereException.extractMessage(ex));
		}
	}

	private void enforcePriceLimit(@NonNull final I_M_DiscountSchemaBreak schemaBreak)
	{
		if (!schemaBreak.isActive())
		{
			return;
		}

		final int productId = schemaBreak.getM_Product_ID();
		if (productId <= 0)
		{
			return;
		}

		final PriceLimitEnforceContext context = PriceLimitEnforceContext.builder()
				.pricingConditionsBreak(MDiscountSchemaDAO.toPricingConditionsBreak(schemaBreak))
				.isSOTrx(true)
				.build();

		Stream.of(context)
				.flatMap(this::explodeByBPartnerId)
				.flatMap(this::explodeByCountryId)
				.forEach(this::enforcePriceLimit);
	}

	private Stream<PriceLimitEnforceContext> explodeByBPartnerId(@NonNull final PriceLimitEnforceContext context)
	{
		final IBPartnerDAO bpartnersRepo = Services.get(IBPartnerDAO.class);
		return bpartnersRepo.retrieveBPartnerIdsForDiscountSchemaId(context.getPricingConditionsBreak().getDiscountSchemaId(), context.getIsSOTrx())
				.stream()
				.map(bpartnerId -> context.toBuilder().bpartnerId(bpartnerId).build());
	}

	private Stream<PriceLimitEnforceContext> explodeByCountryId(@NonNull final PriceLimitEnforceContext context)
	{
		return Services.get(IBPartnerDAO.class).retrieveCountryIdsOfBPartnerLocations(context.getBpartnerId())
				.stream()
				.map(countryId -> context.toBuilder().countryId(countryId).build());
	}

	private void enforcePriceLimit(@NonNull final PriceLimitEnforceContext context)
	{
		final CalculateDiscountRequest request = createCalculateDiscountRequest(context);

		final BigDecimal price = Services.get(IMDiscountSchemaBL.class)
				.calculateDiscount(request)
				.getPriceStdOverride();
		if (price == null)
		{
			return;
		}

		final IPricingBL pricingBL = Services.get(IPricingBL.class);
		final PriceLimitRuleResult priceLimitResult = pricingBL.computePriceLimit(PriceLimitRuleContext.builder()
				.priceActual(BigDecimal.ZERO) // N/A
				.priceLimit(BigDecimal.ZERO) // N/A
				.pricingContext(request.getPricingCtx())
				.build());
		if (!priceLimitResult.isApplicable())
		{
			return;
		}

		if (priceLimitResult.isBelowPriceLimit(price))
		{
			throw new AdempiereException(MSG_UnderLimitPriceWithExplanation, new Object[] { price, priceLimitResult.getPriceLimit(), priceLimitResult.getPriceLimitExplanation() })
					.markAsUserValidationError()
					.setParameter("context", context)
					.setParameter("pricingCtx", request.getPricingCtx());
		}
	}

	private CalculateDiscountRequest createCalculateDiscountRequest(@NonNull final PriceLimitEnforceContext context)
	{
		final PricingConditionsBreak pricingConditionsBreak = context.getPricingConditionsBreak();
		final PricingConditionsBreakMatchCriteria matchCriteria = pricingConditionsBreak.getMatchCriteria();

		final int productId = matchCriteria.getProductId();
		final BigDecimal qty = matchCriteria.getBreakValue();

		final IEditablePricingContext pricingCtx = Services.get(IPricingBL.class).createPricingContext();
		pricingCtx.setConvertPriceToContextUOM(true);
		pricingCtx.setM_Product_ID(productId);
		pricingCtx.setC_UOM_ID(Services.get(IProductBL.class).getStockingUOM(productId).getC_UOM_ID());
		pricingCtx.setSOTrx(context.getIsSOTrx());
		pricingCtx.setQty(qty);

		pricingCtx.setC_BPartner_ID(context.getBpartnerId());
		pricingCtx.setC_Country_ID(context.getCountryId());

		final CalculateDiscountRequest request = CalculateDiscountRequest.builder()
				.discountSchemaId(pricingConditionsBreak.getDiscountSchemaId())
				.forceSchemaBreak(pricingConditionsBreak)
				.qty(pricingCtx.getQty())
				.price(BigDecimal.ZERO) // N/A
				.productId(pricingCtx.getM_Product_ID())
				.pricingCtx(pricingCtx)
				.build();
		return request;
	}

	@lombok.Value
	@lombok.Builder(toBuilder = true)
	private static class PriceLimitEnforceContext
	{
		@NonNull
		final PricingConditionsBreak pricingConditionsBreak;
		@NonNull
		final Boolean isSOTrx;
		final Integer bpartnerId;
		private Integer countryId;
	}

}
