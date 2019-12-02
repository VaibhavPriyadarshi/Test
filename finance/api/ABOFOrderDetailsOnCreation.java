package com.abof.finance.api;

import java.util.ArrayList;
import java.util.Properties;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.abof.finance.utils.ABOFFinanceUtils;
import com.abof.finance.utils.FinanceConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;

/**
 * This class sends the inventory and payment picture whenever an order is
 * created
 * 
 * @author Expicient
 */
public class ABOFOrderDetailsOnCreation implements YIFCustomApi {

	private static YFCLogCategory logger = YFCLogCategory.instance(ABOFOrderDetailsOnCreation.class);

	@Override
	public void setProperties(Properties arg0) throws Exception {
		// TODO Auto-generated method stub
	}

	/**
	 * This method is used to invoke the below methods to invoke services on
	 * create order transactions
	 * 
	 * @param env
	 * @param inDoc
	 * @throws Exception
	 */
	public Document populateOrderDetailsOnCreation(YFSEnvironment env, Document inDoc) throws Exception {
		logger.beginTimer("populateOrderDetailsOnCreation");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateOrderDetailsOnCreation"
			        + XMLUtil.getXmlString(inDoc.getDocumentElement()));
		}
		Element eleRootIn = inDoc.getDocumentElement();
		String strOrderType = eleRootIn.getAttribute(FinanceConstants.ORDER_TYPE);
		String strDocumentType = eleRootIn.getAttribute(FinanceConstants.DOCUMENT_TYPE);
		if (FinanceConstants.DOC_TYPE_SALES_ORDER.equalsIgnoreCase(strDocumentType)
		        && !FinanceConstants.RETURN_EXCHANGE.equalsIgnoreCase(strOrderType)) {
			XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q,
			        createAndPostSOAccountingData(env, inDoc));
			XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q,
			        createAndPostOrderSnapshotData(env, inDoc));
		}
		else {
			XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q,
			        createAndPostOrderSnapshotData(env, inDoc));
		}

		logger.endTimer("populateOrderDetailsOnCreation");
		return inDoc;
	}

	/**
	 * This method is invoked on the transaction CreateOrder.ON_SUCCESS
	 * 
	 * @param env
	 * @param orderDetailsDoc
	 * @throws Exception
	 */
	private Document createAndPostSOAccountingData(YFSEnvironment env, Document orderDetailsDoc) throws Exception {
		logger.beginTimer("createAndPostSOAccountingData");
		Element eleRootIn = orderDetailsDoc.getDocumentElement();
		double codPayment = 0.0;
		double storeCreditPayment = 0.0;
		double prepaidPayment = 0.0;
		double dQwikcilverWalletAmount=0.0;
		//B2B Start
		//double dCreditAmount=0.0;
		//B2B End
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for createAndPostSOAccountingData" + XMLUtil.getXmlString(eleRootIn));
		}
		// Making document to publish for SO_Creation_Accounting
		Document outDoc = XMLUtil.createDocument(FinanceConstants.INVOICE_HEADER);
		Element eleRootOut = outDoc.getDocumentElement();

		// Setting order header elements for publish document
		String strPrimaryKey = ABOFFinanceUtils.getPrimaryKey(eleRootIn.getAttribute(FinanceConstants.ORDER_NO));
		eleRootOut.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
		ABOFFinanceUtils.setRootLevelAttributes(eleRootIn, eleRootOut);
		String strDocumentType = eleRootIn.getAttribute(FinanceConstants.DOCUMENT_TYPE);
		String strOrderType = eleRootIn.getAttribute(FinanceConstants.ORDER_TYPE);
		ABOFFinanceUtils.setDocumentType(strDocumentType, eleRootOut, strOrderType);
		eleRootOut.setAttribute(FinanceConstants.CREATETS, ABOFFinanceUtils.getCreateTimeStamp());
		eleRootOut.setAttribute(FinanceConstants.IS_PROCESSED, FinanceConstants.NO);
		// Setting shipping and billing info in the publish document
		Element elePersonInfoBillTo = (Element) XPathAPI.selectSingleNode(eleRootIn,
		        FinanceConstants.PERSON_INFO_BILL_TO);
		String strBilling[] = ABOFFinanceUtils.strAddress(elePersonInfoBillTo);
		eleRootOut.setAttribute(FinanceConstants.BILLING_NAME, strBilling[0]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_ADDRESS, strBilling[1]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_CITY, strBilling[2]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_STATE, strBilling[3]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_COUNTRY, strBilling[4]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_ZIPCODE, strBilling[5]);
		Element elePersonInfoShipTo = (Element) XPathAPI.selectSingleNode(eleRootIn,
		        FinanceConstants.PERSON_INFO_SHIP_TO);
		String strShipping[] = ABOFFinanceUtils.strAddress(elePersonInfoShipTo);
		eleRootOut.setAttribute(FinanceConstants.SHIPPING_NAME, strShipping[0]);
		eleRootOut.setAttribute(FinanceConstants.SHIPPING_ADDRESS, strShipping[1]);
		eleRootOut.setAttribute(FinanceConstants.SHIPPING_CITY, strBilling[2]);
		eleRootOut.setAttribute(FinanceConstants.SHIPPING_STATE, strBilling[3]);
		eleRootOut.setAttribute(FinanceConstants.SHIPPING_COUNTRY, strBilling[4]);
		eleRootOut.setAttribute(FinanceConstants.SHIPPING_ZIPCODE, strBilling[5]);
		// Calling method to find if payment type is prepaid or postpaid
		ArrayList<String> arrPaymentType = ABOFFinanceUtils.getCommonCodeForPrepaid(env);
		// Creating invoice payments element in publish document
		Element eleInvoicePmnts = outDoc.createElement(FinanceConstants.INVOICE_PAYMENTS);
		eleRootOut.appendChild(eleInvoicePmnts);
		// Finding all Payment Method elements from input document
		NodeList nlPaymentMethod = XPathAPI.selectNodeList(eleRootIn, "PaymentMethods/PaymentMethod");
		for (int intPM = 0; intPM < nlPaymentMethod.getLength(); intPM++) {
			Element elePaymentMethod = (Element) nlPaymentMethod.item(intPM);
			String strPmntType = elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_TYPE);
			// Adding payment method to publish document if it is prepaid
			if (arrPaymentType.contains(strPmntType)) {
				Element eleInvoicePmnt = outDoc.createElement(FinanceConstants.INVOICE_PAYMENT);
				eleInvoicePmnts.appendChild(eleInvoicePmnt);
				// Setting all payment methods in the publish document
				eleInvoicePmnt.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
				ABOFFinanceUtils.setInvoicePayments(elePaymentMethod, eleRootIn, outDoc, eleInvoicePmnt);
				String paymentType = eleInvoicePmnt.getAttribute(FinanceConstants.PAYMENT_TYPE);
				String paymentAmount = eleInvoicePmnt.getAttribute(FinanceConstants.AMOUNT_RECEIVED);
				double dPaymentAmount = YFCCommon.isVoid(paymentAmount) ? 0.0 : Double.valueOf(paymentAmount);
				if ("COD".equalsIgnoreCase(paymentType)) {
					codPayment = codPayment + dPaymentAmount;
				}
				//B2B Start
				/*else if ("CREDIT".equalsIgnoreCase(paymentType)) {
					dCreditAmount = dCreditAmount + dPaymentAmount;
				}*/
				//B2B End
				else if ("STORE_CREDIT".equalsIgnoreCase(paymentType)) {
					storeCreditPayment = storeCreditPayment + dPaymentAmount;
				} else if (FinanceConstants.QWIKCILVER_WALLET.equalsIgnoreCase(paymentType)) {
					dQwikcilverWalletAmount = dQwikcilverWalletAmount + dPaymentAmount;
				} else {
					prepaidPayment = prepaidPayment + dPaymentAmount;
				}
			}
		}

		eleRootOut.setAttribute(FinanceConstants.STORE_CREDIT_AMOUNT, "" + storeCreditPayment);
		eleRootOut.setAttribute(FinanceConstants.PREPAID_AMOUNT, "" + prepaidPayment);
		eleRootOut.setAttribute(FinanceConstants.COD_AMOUNT, "" + codPayment);
		eleRootOut.setAttribute(FinanceConstants.QWIKCILVER_WALLET_AMOUNT, "" + dQwikcilverWalletAmount);
		//B2B Start
		//eleRootOut.setAttribute(FinanceConstants.CREDIT_AMOUNT, "" + dCreditAmount);
		//B2B End
		// Code not required any more - Start
		if (strOrderType.equals("Return-Exchange")) {
			Element eleInvoicePmnt = outDoc.createElement(FinanceConstants.INVOICE_PAYMENT);
			eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_TYPE, "ADJUSTED_TO_EXCHANGE");
			eleInvoicePmnt.setAttribute(FinanceConstants.AMOUNT_COLLECTED, eleRootIn.getAttribute("PendingTransferIn"));
			eleInvoicePmnt.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strPrimaryKey);
			eleInvoicePmnts.appendChild(eleInvoicePmnt);

		}
		// Code not required any more - End
		// Fix for HeaderCharge Start - Sept 18
		NodeList nlHeaderChargeIn = XPathAPI.selectNodeList(eleRootIn, "HeaderCharges/HeaderCharge");
		String referenceKey = FinanceConstants.CHARGE_NAME;
		// Adding Header Level Charges to the publish document
		boolean isInvoicingEvent = false;
		//GST Start
		//Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getHeaderTaxPercentageAndType(env, eleRootIn,isInvoicingEvent);
		Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getExtHdrTaxPercentAndType(env, eleRootIn,
				        isInvoicingEvent);
		//GST End
		ABOFROInvoiceDetailsOnCreation.populateHeaderChargesDuringInvoiceCreation(env, nlHeaderChargeIn, eleRootOut,
		        referenceKey, headerTaxPercentageAndType);
		// Fix for HeaderCharge End - Sept 18
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for createAndPostSOAccountingData" + XMLUtil.getXmlString(eleRootOut));
		}

		logger.endTimer("createAndPostSOAccountingData");
		return outDoc;
	}

	/**
	 * This method is invoked on the transaction CreateOrder.ON_SUCCESS
	 * 
	 * @param env
	 * @param orderDetailsDoc
	 * @throws Exception
	 */
	public static Document createAndPostOrderSnapshotData(YFSEnvironment env, Document orderDetailsDoc)
	        throws Exception {
		logger.beginTimer("createAndPostOrderSnapshotData");
		Element eleRootIn = orderDetailsDoc.getDocumentElement();
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for createAndPostOrderSnapshotData" + XMLUtil.getXmlString(eleRootIn));
		}
		// Creating publish document for Order Create Cancel Snapshot
		Document outDoc = XMLUtil.createDocument(FinanceConstants.ORDER_HEADER);
		Element eleRootOut = outDoc.getDocumentElement();
		// Setting header attributes for the publish document
		String strPrimaryKey = ABOFFinanceUtils.getPrimaryKey(eleRootIn.getAttribute(FinanceConstants.ORDER_NO));
		eleRootOut.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strPrimaryKey);
		// Adding time stamp and is processed values
		eleRootOut.setAttribute(FinanceConstants.CREATETS, ABOFFinanceUtils.getCreateTimeStamp());
		eleRootOut.setAttribute(FinanceConstants.IS_PROCESSED, FinanceConstants.NO);
		ABOFFinanceUtils.setRootLevelAttributes(eleRootIn, eleRootOut);
		String strDocumentType = eleRootIn.getAttribute(FinanceConstants.DOCUMENT_TYPE);
		String strOrderType = eleRootIn.getAttribute(FinanceConstants.ORDER_TYPE);
		ABOFFinanceUtils.setDocumentType(strDocumentType, eleRootOut, strOrderType);
		// Setting billing and shipping addresses
		Element elePersonInfoBillTo = (Element) XPathAPI.selectSingleNode(eleRootIn,
		        FinanceConstants.PERSON_INFO_BILL_TO);
		String strBilling[] = ABOFFinanceUtils.strAddress(elePersonInfoBillTo);
		eleRootOut.setAttribute(FinanceConstants.BILLING_NAME, strBilling[0]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_ADDRESS, strBilling[1]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_CITY, strBilling[2]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_STATE, strBilling[3]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_COUNTRY, strBilling[4]);
		eleRootOut.setAttribute(FinanceConstants.BILLING_ZIPCODE, strBilling[5]);
		Element ordLinePersonInfoShipTo = (Element) XPathAPI.selectSingleNode(eleRootIn, "OrderLines/OrderLine/PersonInfoShipTo");
		
			String strShippingLine[] = ABOFFinanceUtils.strAddress(ordLinePersonInfoShipTo);
			eleRootOut.setAttribute(FinanceConstants.SHIPPING_NAME, strShippingLine[0]);
			eleRootOut.setAttribute(FinanceConstants.SHIPPING_ADDRESS, strShippingLine[1]);
			eleRootOut.setAttribute(FinanceConstants.SHIPPING_CITY, strShippingLine[2]);
			eleRootOut.setAttribute(FinanceConstants.SHIPPING_STATE, strShippingLine[3]);
			eleRootOut.setAttribute(FinanceConstants.SHIPPING_COUNTRY, strShippingLine[4]);
			eleRootOut.setAttribute(FinanceConstants.SHIPPING_ZIPCODE, strShippingLine[5]);
	
			
		Node overAllTotalsNode = XPathAPI.selectSingleNode(eleRootIn, FinanceConstants.OVERALL_TOTALS);
		double orderTotal = 0.0;
		if (!YFCCommon.isVoid(overAllTotalsNode)) {
			Element overAllTotalsEle = (Element) overAllTotalsNode;
			String grandTotal = overAllTotalsEle.getAttribute(FinanceConstants.GRAND_TOTAL);
			orderTotal = YFCCommon.isVoid(grandTotal) ? 0.0 : Double.valueOf(grandTotal);
			logger.verbose("Found orderTotal value as:" + orderTotal);
		}
		eleRootOut.setAttribute(FinanceConstants.ORDER_TOTAL, "" + orderTotal);

		setLineLevelAttributes(outDoc, eleRootOut, eleRootIn, strPrimaryKey);
		ArrayList<String> arrPaymentType = ABOFFinanceUtils.getCommonCodeForPrepaid(env);
		// Adding Payment Methods to the publish document
		Element eleSOPmnts = outDoc.createElement(FinanceConstants.ORDER_PAYMENTS);
		eleRootOut.appendChild(eleSOPmnts);
		// Setting all payment methods in the publish document
		NodeList nlPaymentMethod = XPathAPI.selectNodeList(eleRootIn, "PaymentMethods/PaymentMethod");
		for (int intPM = 0; intPM < nlPaymentMethod.getLength(); intPM++) {
			Element elePaymentMethod = (Element) nlPaymentMethod.item(intPM);
			Element eleInvoicePmnts = outDoc.createElement(FinanceConstants.ORDER_PAYMENT);
			eleSOPmnts.appendChild(eleInvoicePmnts);
			eleInvoicePmnts.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strPrimaryKey);
			// Calling method to set the Payment Methods to document
			ABOFFinanceUtils.setInvoicePayments(elePaymentMethod, eleRootIn, outDoc, eleInvoicePmnts);
			if (!arrPaymentType.contains(elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_TYPE))) {
				//B2B Strat:
				if(FinanceConstants.PAYMENT_TYPE_CREDIT.equalsIgnoreCase(elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_TYPE)))
				{
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE, elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_TYPE));	
				}
				else{				
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.COD);
				// Fixes to change from Postpaid to COD Sept 18
				}
				//B2B End
			}
		}

		if (strOrderType.equals("Return-Exchange")) {
			Element eleInvoicePmnts = outDoc.createElement(FinanceConstants.ORDER_PAYMENT);
			if (strDocumentType.equals(FinanceConstants.DOC_TYPE_SALES_ORDER)) {
				eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TYPE, "ADJUSTED_TO_RETURN");
				eleInvoicePmnts.setAttribute(FinanceConstants.AMOUNT_COLLECTED,
				        eleRootIn.getAttribute("PendingTransferIn"));
			} else {
				Node nodeOverAllTotals = XPathAPI.selectSingleNode(eleRootIn, "OverallTotals");
				if (!YFCCommon.isVoid(nodeOverAllTotals)) {
					Element overAllTotals = (Element) nodeOverAllTotals;
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TYPE, "ADJUSTED_TO_EXCHANGE");
					eleInvoicePmnts.setAttribute(FinanceConstants.AMOUNT_COLLECTED,
					        overAllTotals.getAttribute("GrandTotal"));
				}
			}
			eleInvoicePmnts.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strPrimaryKey);
			eleSOPmnts.appendChild(eleInvoicePmnts);
		}

		// Setting attributes for header charge and header tax in publish
		// document
		NodeList nlHeaderChargeIn = XPathAPI.selectNodeList(eleRootIn, "HeaderCharges/HeaderCharge");

		String eleKey = FinanceConstants.HEADER_CHARGE;
		boolean isInvoicingEvent = false;
		//GST Start:
		//Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getHeaderTaxPercentageAndType(env, eleRootIn,isInvoicingEvent);
		Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getExtHdrTaxPercentAndType(env, eleRootIn,isInvoicingEvent);
		//GST End:
		ABOFROInvoiceDetailsOnCreation.populateHeaderChargesDuringInvoiceCreation(env, nlHeaderChargeIn, eleRootOut,
		        eleKey, headerTaxPercentageAndType);

		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for createAndPostOrderSnapshotData" + XMLUtil.getXmlString(eleRootOut));
		}
		logger.endTimer("createAndPostOrderSnapshotData");
		return outDoc;
	}

	/**
	 * This method is used to set line level attributes of an order line in the
	 * XML that is published
	 * 
	 * @param outDoc
	 * @param eleRootOut
	 * @param eleRootIn
	 * @param strPrimaryKey
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private static void setLineLevelAttributes(Document outDoc, Element eleRootOut, Element eleRootIn,
	        String strPrimaryKey) throws IllegalArgumentException, Exception {
		logger.beginTimer("setLineLevelAttributes");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for setLineLevelAttributes" + XMLUtil.getXmlString(eleRootIn));
			logger.verbose("input document for setLineLevelAttributes" + XMLUtil.getXmlString(eleRootOut));
			logger.verbose("input string for setLineLevelAttributes" + strPrimaryKey);
		}
		// Setting attributes for SO Line in publish document
		Element eleSOLinesOut = outDoc.createElement(FinanceConstants.ORDER_LINES);
		eleRootOut.appendChild(eleSOLinesOut);
		// Fetching each order line from input document
		NodeList nlOrderLine = XPathAPI.selectNodeList(eleRootIn, "OrderLines/OrderLine");
		for (int intOL = 0; intOL < nlOrderLine.getLength(); intOL++) {
			Element eleOrderLineIn = (Element) nlOrderLine.item(intOL);
			Element eleSOLineOut = outDoc.createElement(FinanceConstants.ORDER_LINE);
			eleSOLinesOut.appendChild(eleSOLineOut);
			String strOLKey = ABOFFinanceUtils.getPrimaryKey(eleRootIn.getAttribute(FinanceConstants.ORDER_NO)
			        + eleOrderLineIn.getAttribute(FinanceConstants.PRIME_LINE_NO));
			eleSOLineOut.setAttribute(FinanceConstants.ORDER_LINE_KEY, strOLKey);
			eleSOLineOut.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strPrimaryKey);
			eleSOLineOut.setAttribute(FinanceConstants.PRIME_LINE_NO,
			        eleOrderLineIn.getAttribute(FinanceConstants.PRIME_LINE_NO));
			// Populating ItemID
			if (null != XPathAPI.selectSingleNode(eleOrderLineIn, "ItemDetails/@ItemID")) {
				eleSOLineOut.setAttribute(FinanceConstants.SKU_CODE,
				        XPathAPI.selectSingleNode(eleOrderLineIn, "ItemDetails/@ItemID").getNodeValue());
			}
			eleSOLineOut.setAttribute(FinanceConstants.QUANTITY,
			        eleOrderLineIn.getAttribute(FinanceConstants.ORDERED_QTY));
			Element eleLineTotal = (Element) eleOrderLineIn.getElementsByTagName(FinanceConstants.LINE_OVERALL_TOTALS)
			        .item(0);
			eleSOLineOut.setAttribute(FinanceConstants.SALES_PRICE,
			        eleLineTotal.getAttribute(FinanceConstants.LINE_TOTAL));
			/*GST Start:
			// Adding tax values to the document
			if (null != XPathAPI.selectSingleNode(eleOrderLineIn, "LineTaxes/LineTax")) {
				Element eleTax = (Element) XPathAPI.selectSingleNode(eleOrderLineIn, "LineTaxes/LineTax");
				eleSOLineOut.setAttribute(FinanceConstants.TAX_TYPE, eleTax.getAttribute(FinanceConstants.REFERENCE_1));
				eleSOLineOut.setAttribute(FinanceConstants.TAX_RATE,
				        eleTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
				eleSOLineOut.setAttribute(FinanceConstants.TAX_AMOUNT,
				        ABOFFinanceUtils.getLineTax(eleLineTotal.getAttribute(FinanceConstants.LINE_TOTAL),
				                eleTax.getAttribute(FinanceConstants.TAX_PERCENTAGE)));
			}*/
			//Fetching LineTax elements for OrderLine
			NodeList nlLineTax = XPathAPI.selectNodeList(eleOrderLineIn, "LineTaxes/LineTax");
			//Variable to store total tax percentage of the Line
			double dblTotalTaxPct = 0.0;
			//Iterating through line taxes to calculate the total line tax percentage
			for (int intTaxCount = 0; intTaxCount < nlLineTax.getLength(); intTaxCount++) {
				Element eleLTax = (Element) nlLineTax.item(intTaxCount);
				dblTotalTaxPct = dblTotalTaxPct + Double.valueOf(eleLTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
			}
			//Iterating through line taxes to stamp the tax informations
			for (int intLT = 0; intLT < nlLineTax.getLength(); intLT++) {
				Element eleLineTax = (Element) nlLineTax.item(intLT);
				String strTaxRef1 = eleLineTax.getAttribute(FinanceConstants.REFERENCE_1);
				if(FinanceConstants.SGST.equalsIgnoreCase(strTaxRef1))
				{
					eleSOLineOut.setAttribute(FinanceConstants.EXT_TAX_TYPE,
							eleLineTax.getAttribute(FinanceConstants.REFERENCE_1));
					eleSOLineOut.setAttribute(FinanceConstants.EXT_TAX_RATE,
							eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
					eleSOLineOut.setAttribute(FinanceConstants.EXT_TAX_AMOUNT,
							ABOFFinanceUtils.getLineTax(eleLineTotal.getAttribute(FinanceConstants.LINE_TOTAL),
									eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE),Double.toString(dblTotalTaxPct)));
				}else
				{
					eleSOLineOut.setAttribute(FinanceConstants.TAX_TYPE,
							eleLineTax.getAttribute(FinanceConstants.REFERENCE_1));
					eleSOLineOut.setAttribute(FinanceConstants.TAX_RATE,
							eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
					eleSOLineOut.setAttribute(FinanceConstants.TAX_AMOUNT, 
							ABOFFinanceUtils.getLineTax(eleLineTotal.getAttribute(FinanceConstants.LINE_TOTAL),
									eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE),Double.toString(dblTotalTaxPct)));
					
				}
			
			}
			//GST end:

			// Creating order line level discounts element
			Element eleSOLineDists = outDoc.createElement(FinanceConstants.ORDER_LINE_DISCOUNTS);
			eleSOLineOut.appendChild(eleSOLineDists);
			
			// Creating order line level Charges element
			Element eleSOLineCharges = outDoc.createElement(FinanceConstants.ORDER_LINE_CHARGES);
			eleSOLineOut.appendChild(eleSOLineCharges);
			
			
			// Adding line charges to the publish document
			NodeList nlLineDist = XPathAPI.selectNodeList(eleOrderLineIn, "LineCharges/LineCharge");
			for (int intLC = 0; intLC < nlLineDist.getLength(); intLC++) {
				Element eleLineCharge = (Element) nlLineDist.item(intLC);
				String strChargeCategory = eleLineCharge.getAttribute(FinanceConstants.CHARGE_CATEGORY);
				if (FinanceConstants.DISCOUNT.equalsIgnoreCase(strChargeCategory)) {
					Element eleSOLineDist = outDoc.createElement(FinanceConstants.ORDER_LINE_DISCOUNT);
					eleSOLineDists.appendChild(eleSOLineDist);
					eleSOLineDist.setAttribute(FinanceConstants.ORDER_LINE_KEY, strOLKey);
					eleSOLineDist.setAttribute(FinanceConstants.DISCOUNT_NAME,
					        eleLineCharge.getAttribute(FinanceConstants.CHARGE_NAME));
					eleSOLineDist.setAttribute(FinanceConstants.DISCOUNT_AMOUNT,
					        eleLineCharge.getAttribute(FinanceConstants.CHARGE_AMOUNT));
					eleSOLineDist.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strPrimaryKey);
				
			}else if (FinanceConstants.VAT
					.equalsIgnoreCase(strChargeCategory)) {
				Element eleSOLineCharge = outDoc.createElement(FinanceConstants.ORDER_LINE_CHARGE);
				eleSOLineCharges.appendChild(eleSOLineCharge);
				eleSOLineCharge.setAttribute(FinanceConstants.ORDER_LINE_KEY, strOLKey);
				eleSOLineCharge
						.setAttribute(
								FinanceConstants.CHARGE_NAME,
								eleLineCharge
										.getAttribute(FinanceConstants.CHARGE_NAME));
				eleSOLineCharge
						.setAttribute(
								FinanceConstants.CHARGE_AMOUNT,
								eleLineCharge
										.getAttribute(FinanceConstants.CHARGE_AMOUNT));
				eleSOLineCharge.setAttribute(
						FinanceConstants.ORDER_HEADER_KEY, strPrimaryKey);
			}

			}
		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("out document for setLineLevelAttributes" + XMLUtil.getXmlString(eleRootOut));
		}
		logger.endTimer("setLineLevelAttributes");
	}
}
