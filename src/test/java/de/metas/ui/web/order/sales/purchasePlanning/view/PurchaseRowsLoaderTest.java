package de.metas.ui.web.order.sales.purchasePlanning.view;

import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.save;
import static org.adempiere.model.InterfaceWrapperHelper.saveRecord;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.adempiere.bpartner.BPartnerId;
import org.adempiere.service.OrgId;
import org.adempiere.test.AdempiereTestHelper;
import org.adempiere.util.time.SystemTime;
import org.adempiere.warehouse.WarehouseId;
import org.compiere.model.I_AD_Org;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_Currency;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.I_C_UOM;
import org.compiere.model.I_M_Product;
import org.compiere.model.I_M_Product_Category;
import org.compiere.model.I_M_Warehouse;
import org.compiere.util.TimeUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.collect.ImmutableList;

import de.metas.ShutdownListener;
import de.metas.StartupListener;
import de.metas.material.dispo.commons.repository.AvailableToPromiseRepository;
import de.metas.money.Currency;
import de.metas.money.CurrencyRepository;
import de.metas.money.MoneyService;
import de.metas.money.grossprofit.GrossProfitPriceFactory;
import de.metas.order.OrderAndLineId;
import de.metas.order.OrderLineRepository;
import de.metas.pricing.conditions.PricingConditions;
import de.metas.product.ProductAndCategoryId;
import de.metas.product.ProductId;
import de.metas.purchasecandidate.PurchaseCandidate;
import de.metas.purchasecandidate.PurchaseCandidatesGroup;
import de.metas.purchasecandidate.PurchaseDemand;
import de.metas.purchasecandidate.PurchaseDemandWithCandidates;
import de.metas.purchasecandidate.SalesOrderLine;
import de.metas.purchasecandidate.SalesOrderLineRepository;
import de.metas.purchasecandidate.VendorProductInfo;
import de.metas.purchasecandidate.availability.AvailabilityCheckService;
import de.metas.purchasecandidate.availability.AvailabilityMultiResult;
import de.metas.purchasecandidate.availability.AvailabilityResult;
import de.metas.purchasecandidate.availability.AvailabilityResult.Type;
import de.metas.purchasecandidate.availability.PurchaseCandidatesAvailabilityRequest;
import de.metas.purchasecandidate.grossprofit.PurchaseProfitInfo;
import de.metas.quantity.Quantity;
import de.metas.ui.web.order.sales.purchasePlanning.view.PurchaseRowsLoader.PurchaseRowsList;
import mockit.Expectations;
import mockit.Mocked;

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

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { StartupListener.class, ShutdownListener.class, GrossProfitPriceFactory.class })
public class PurchaseRowsLoaderTest
{
	@Mocked
	private AvailabilityCheckService availabilityCheckService;

	private I_M_Product product;
	private I_C_Order salesOrderRecord;
	private I_C_BPartner bPartnerVendor;

	private I_C_Currency currency;

	private Quantity TEN;

	private I_M_Warehouse warehouse;

	private I_AD_Org org;

	private static CurrencyRepository currencyRepository;

	@Before
	public void init()
	{
		AdempiereTestHelper.get().init();

		org = newInstance(I_AD_Org.class);
		saveRecord(org);

		final I_C_UOM uom = newInstance(I_C_UOM.class);
		uom.setUOMSymbol("testUOMSympol");
		saveRecord(uom);

		this.TEN = Quantity.of(BigDecimal.TEN, uom);

		warehouse = newInstance(I_M_Warehouse.class);
		saveRecord(warehouse);

		final I_M_Product_Category productCategory = newInstance(I_M_Product_Category.class);
		saveRecord(productCategory);

		product = newInstance(I_M_Product.class);
		product.setM_Product_Category_ID(productCategory.getM_Product_Category_ID());
		product.setC_UOM(uom);
		saveRecord(product);

		final I_C_BPartner bPartnerCustomer = newInstance(I_C_BPartner.class);
		bPartnerCustomer.setName("bPartnerCustomer.Name");
		saveRecord(bPartnerCustomer);

		salesOrderRecord = newInstance(I_C_Order.class);
		salesOrderRecord.setC_BPartner(bPartnerCustomer);
		salesOrderRecord.setPreparationDate(SystemTime.asTimestamp());
		salesOrderRecord.setC_PaymentTerm_ID(30);
		saveRecord(salesOrderRecord);

		bPartnerVendor = newInstance(I_C_BPartner.class);
		bPartnerVendor.setName("bPartnerVendor.Name");
		saveRecord(bPartnerVendor);

		currency = newInstance(I_C_Currency.class);
		currency.setStdPrecision(2);
		saveRecord(currency);

		currencyRepository = new CurrencyRepository();
	}

	@Test
	public void load()
	{
		final I_C_OrderLine salesOrderLineRecord = newInstance(I_C_OrderLine.class);
		salesOrderLineRecord.setAD_Org(org);
		salesOrderLineRecord.setM_Product(product);
		salesOrderLineRecord.setM_Warehouse(warehouse);
		salesOrderLineRecord.setC_Order(salesOrderRecord);
		salesOrderLineRecord.setC_Currency(currency);
		salesOrderLineRecord.setC_UOM_ID(TEN.getUOMId());
		salesOrderLineRecord.setQtyEntered(TEN.getAsBigDecimal());
		salesOrderLineRecord.setQtyOrdered(TEN.getAsBigDecimal());
		salesOrderLineRecord.setDatePromised(SystemTime.asTimestamp());
		save(salesOrderLineRecord);

		final SalesOrderLineRepository salesOrderLineRepository = new SalesOrderLineRepository(new OrderLineRepository(currencyRepository));
		final SalesOrderLine salesOrderLine = salesOrderLineRepository.ofRecord(salesOrderLineRecord);

		final VendorProductInfo vendorProductInfo = VendorProductInfo.builder()
				.vendorId(BPartnerId.ofRepoId(bPartnerVendor.getC_BPartner_ID()))
				.productAndCategoryId(ProductAndCategoryId.of(product.getM_Product_ID(), product.getM_Product_Category_ID()))
				.vendorProductNo("bPartnerProduct.VendorProductNo")
				.vendorProductName("bPartnerProduct.ProductName")
				.pricingConditions(PricingConditions.builder()
						.build())
				.build();

		final PurchaseDemand demand = SalesOrder2PurchaseViewFactory.createDemand(salesOrderLine);
		final PurchaseCandidate purchaseCandidate = createPurchaseCandidate(salesOrderLineRecord, vendorProductInfo);
		final ImmutableList<PurchaseDemandWithCandidates> demandWithCandidates = createPurchaseDemandWithCandidates(demand, purchaseCandidate);

		final PurchaseRowsLoader loader = PurchaseRowsLoader.builder()
				.purchaseDemandWithCandidatesList(demandWithCandidates)
				.viewSupplier(() -> null)
				.purchaseRowFactory(new PurchaseRowFactory(
						new AvailableToPromiseRepository(),
						new MoneyService()))
				.availabilityCheckService(availabilityCheckService)
				.build();

		//
		// invoke the method under test
		final PurchaseRowsList rowsList = loader.load();

		//
		// Check result
		final List<PurchaseRow> topLevelRows = rowsList.getTopLevelRows();

		assertThat(topLevelRows).hasSize(1);
		final PurchaseRow groupRow = topLevelRows.get(0);
		assertThat(groupRow.getType()).isEqualTo(PurchaseRowType.GROUP);
		assertThat(groupRow.getIncludedRows()).hasSize(1);

		final PurchaseRow purchaseRow = groupRow.getIncludedRows().iterator().next();
		assertThat(purchaseRow.getType()).isEqualTo(PurchaseRowType.LINE);
		assertThat(purchaseRow.getIncludedRows()).isEmpty();

		// @formatter:off
		new Expectations()
		{{
			final PurchaseCandidatesAvailabilityRequest request = loader.createAvailabilityRequest(rowsList);
			availabilityCheckService.checkAvailability(request);
			result = AvailabilityMultiResult.of(AvailabilityResult.builder()
					.trackingId(request.getTrackingIds().iterator().next())
					.qty(TEN)
					.type(Type.AVAILABLE)
					.build());
		}};	// @formatter:on

		loader.createAndAddAvailabilityResultRows(rowsList);
		assertThat(purchaseRow.getIncludedRows()).hasSize(1);

		final PurchaseRow availabilityRow = purchaseRow.getIncludedRows().iterator().next();
		assertThat(availabilityRow.getType()).isEqualTo(PurchaseRowType.AVAILABILITY_DETAIL);
		assertThat(availabilityRow.getRowId().toDocumentId()).isNotEqualTo(purchaseRow.getRowId().toDocumentId());
	}

	private static PurchaseCandidate createPurchaseCandidate(
			final I_C_OrderLine orderLine,
			final VendorProductInfo vendorProductInfo)
	{
		final Currency currency = currencyRepository.getById(orderLine.getC_Currency_ID());

		final PurchaseProfitInfo profitInfo = PurchaseRowTestTools.createProfitInfo(currency);

		final PurchaseCandidate purchaseCandidate = PurchaseCandidate.builder()
				.orgId(OrgId.ofRepoId(20))
				.dateRequired(TimeUtil.asLocalDateTime(orderLine.getDatePromised()))
				.productId(ProductId.ofRepoId(orderLine.getM_Product_ID()))
				.qtyToPurchase(Quantity.of(orderLine.getQtyOrdered(), orderLine.getM_Product().getC_UOM()))
				.salesOrderAndLineId(OrderAndLineId.ofRepoIds(orderLine.getC_Order_ID(), orderLine.getC_OrderLine_ID()))
				.vendorId(vendorProductInfo.getVendorId())
				.vendorProductNo(vendorProductInfo.getVendorProductNo())
				.aggregatePOs(vendorProductInfo.isAggregatePOs())
				.warehouseId(WarehouseId.ofRepoId(30))
				.profitInfo(profitInfo)
				.build();
		return purchaseCandidate;
	}

	private static ImmutableList<PurchaseDemandWithCandidates> createPurchaseDemandWithCandidates(
			final PurchaseDemand demand,
			final PurchaseCandidate purchaseCandidate)
	{
		return ImmutableList.of(PurchaseDemandWithCandidates.builder()
				.purchaseDemand(demand)
				.purchaseCandidatesGroup(PurchaseCandidatesGroup.of(demand.getId(), purchaseCandidate))
				.build());
	}
}
