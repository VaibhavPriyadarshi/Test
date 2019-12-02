package com.abof.finance.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
 * This class is used to publish a document with invoicing details of a sales or
 * exchange order
 * 
 * @author Expicient
 */
public class ABOFOrderDetailsOnInvoicing implements YIFCustomApi {

	private static YFCLogCategory logger = YFCLogCategory.instance(ABOFOrderDetailsOnInvoicing.class);

	public void setProperties(Properties arg0) throws Exception {
	}

	/**
	 * This method is invoked on the SEND_INVOICE as well as Create Shipment
	 * Invoice transaction Create Shipment Invoice transaction is further used
	 * only when SO invoicing happens for COD amount. Since in this case, Send
	 * Invoice is executed only when goods are delivered. Hence in case of COD
	 * amount, invoice tables (of staging table) are populated when invoice
	 * created and population is avoided during Send Invoice when it is COD
	 * amount In other case, send Invoice is used for both SO and EO
	 * 
	 * @param env
	 * @param invoiceDetailsDoc
	 * @throws Exception
	 */
	public Document populateOrderDetailsOnInvoicing(YFSEnvironment env, Document invoiceDetailsDoc) throws Exception {
		logger.beginTimer("populateOrderDetailsOnInvoicing");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for 	"
			        + XMLUtil.getXmlString(invoiceDetailsDoc.getDocumentElement()));
		}
		Element eleRoot = invoiceDetailsDoc.getDocumentElement();
		String strName = eleRoot.getNodeName();
		Document outDoc = null;
		if (FinanceConstants.ORDER_INVOICE.equalsIgnoreCase(strName)) {
			String strShipmentKey = eleRoot.getAttribute(FinanceConstants.SHIPMENT_KEY);
			String strOrderHeaderKey = eleRoot.getAttribute(FinanceConstants.ORDER_HEADER_KEY);
			boolean isExchange = isExchangeOrder(env, strOrderHeaderKey);
			double codAmount = getCODAmountForSOInvoice(env, strShipmentKey);
			if (codAmount <= 0 && !isExchange) {
				return invoiceDetailsDoc;
			}
			String strInvoiceNo = eleRoot.getAttribute(FinanceConstants.INVOICE_NO);
			String strOrderInvKey = eleRoot.getAttribute(FinanceConstants.ORDER_INVOICE_KEY);
			//update the Shipment_Invoice Collection Details
			//Call requestCollection
			callRequestCollection(env, eleRoot.getAttribute(FinanceConstants.ORDER_HEADER_KEY));
			invoiceDetailsDoc = ABOFROInvoiceDetailsOnCreation.getOrderInvoiceDetails(env, strInvoiceNo,
			        strOrderInvKey);
			if (isExchange) {
				ABOFEOInvoiceDetailsOnCreation obj = new ABOFEOInvoiceDetailsOnCreation();
				outDoc = obj.publishEOInvoiceDetails(env, invoiceDetailsDoc);
			} else {
				outDoc = populateSODetailsOnInvoicing(env, invoiceDetailsDoc, codAmount);
			} // if COD is in the
			  // order
			  // value > 0
		} else {
			Element eleInvHeader = (Element) eleRoot.getElementsByTagName(FinanceConstants.INVOICE_HEADER).item(0);
			Element eleOrder = (Element) eleInvHeader.getElementsByTagName(FinanceConstants.ORDER).item(0);
			String strOrderType = eleOrder.getAttribute(FinanceConstants.ORDER_TYPE);
			if (!FinanceConstants.RETURN_EXCHANGE.equalsIgnoreCase(strOrderType)) {

				Node nodeShipment = XPathAPI.selectSingleNode(eleRoot, "InvoiceHeader/Shipment");
				if (!YFCCommon.isVoid(nodeShipment)) {
					Element eleShpmnt = (Element) nodeShipment;
					String strShipmentKey = eleShpmnt.getAttribute(FinanceConstants.SHIPMENT_KEY);
					double codAmount = getCODAmountForSOInvoice(env, strShipmentKey);
					if (codAmount > 0) {
						logger.verbose(
						        "As sales order invoice with COD is already published during invoice creation, not publishing it during send invoice");
						return invoiceDetailsDoc;
					}
				}
				outDoc = populateSODetailsOnInvoicing(env, invoiceDetailsDoc, 0); // if COD is not in
				                                                                  
			}
			// the order value ==0
		}
		if (outDoc == null) {
			logger.verbose("As input document is null, can't publish it to Finance");
			return invoiceDetailsDoc;
		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateOrderDetailsOnInvoicing"
			        + XMLUtil.getXmlString(outDoc.getDocumentElement()));
		}
		XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q, outDoc);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for populateOrderDetailsOnInvoicing"
			        + XMLUtil.getXmlString(outDoc.getDocumentElement()));
		}
		logger.endTimer("populateOrderDetailsOnInvoicing");
		return invoiceDetailsDoc;
	}

	private boolean isExchangeOrder(YFSEnvironment env, String strOrderHeaderKey) throws Exception {
		boolean isExchange = false;
		if (YFCCommon.isVoid(strOrderHeaderKey)) {
			return isExchange;
		}
		Document inDocGetOrderList = XMLUtil.createDocument(FinanceConstants.ORDER);
		Element inEleGetOrderList = inDocGetOrderList.getDocumentElement();
		inEleGetOrderList.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strOrderHeaderKey);
		Document docTemplate = XMLUtil
		        .getDocument("<OrderList><Order OrderNo='' OrderHeaderKey='' OrderType=''/></OrderList>");
		Document outDocGetOrderList = XMLUtil.invokeAPI(env, FinanceConstants.GET_ORDER_LIST, inDocGetOrderList,
		        docTemplate);
		Element eleRootOutGetOLList = outDocGetOrderList.getDocumentElement();
		Element eleOrderGetOrderList = (Element) eleRootOutGetOLList.getElementsByTagName(FinanceConstants.ORDER)
		        .item(0);
		String strOrderType = eleOrderGetOrderList.getAttribute(FinanceConstants.ORDER_TYPE);
		if (FinanceConstants.RETURN_EXCHANGE.equalsIgnoreCase(strOrderType)) {
			isExchange = true;
		}
		return isExchange;
	}

	/**
	 * This method fetches COD amount on a Shipment
	 * 
	 * @param env
	 * @param strShipmentKey
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	private double getCODAmountForSOInvoice(YFSEnvironment env, String strShipmentKey)
	        throws IllegalArgumentException, Exception {
		double codAmount = 0;
		logger.beginTimer("getCODAmountForSOInvoice");
		Document shipmentInDoc = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
		Element shipmentEle = shipmentInDoc.getDocumentElement();
		shipmentEle.setAttribute(FinanceConstants.SHIPMENT_KEY, strShipmentKey);
		Document shipmentListTempDoc = XMLUtil
		        .getDocument("<Shipments><Shipment ShipmentKey=''><Extn/></Shipment></Shipments>");

		Document shipmentListOpDoc = XMLUtil.invokeAPI(env, FinanceConstants.GET_SHIPMENT_LIST, shipmentInDoc,
		        shipmentListTempDoc);
		Element shipmentListOpElement = shipmentListOpDoc.getDocumentElement();
		Node nodeExtn = XPathAPI.selectSingleNode(shipmentListOpElement, "//Shipments/Shipment/Extn");
		if (!YFCCommon.isVoid(nodeExtn)) {
			Element extnEle = (Element) nodeExtn;
			String codAmountStr = extnEle.getAttribute("ExtnCODPayAmount");
			if (!YFCCommon.isVoid(codAmountStr)) {
				codAmount = Double.valueOf(codAmountStr);
			}
		}

		logger.endTimer("getCODAmountForSOInvoice");
		return codAmount;
	}

	/**
	 * This method is used to populate header level data in the publish document
	 * for SEND_INVOICE
	 * 
	 * @param env
	 * @param eleRootIn
	 * @param eleInvHeadRoot
	 * @param strPrimaryKey
	 * @param invoiceDetailsDoc
	 * @throws Exception
	 */
	private void populateInvoiceHeader(YFSEnvironment env, Element eleRootIn, Element eleInvHeadRoot,
	        String strPrimaryKey, Document invoiceDetailsDoc) throws Exception {
		logger.beginTimer("populateInvoiceHeader");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateInvoiceHeader" + XMLUtil.getXmlString(eleRootIn));
			logger.verbose("input string for populateInvoiceHeader" + strPrimaryKey);
		}
		Element eleOrder = (Element) eleRootIn.getElementsByTagName(FinanceConstants.ORDER).item(0);
		eleInvHeadRoot.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
		// Calling method to set root level attributes
		ABOFFinanceUtils.setRootLevelAttributes(eleOrder, eleInvHeadRoot);

		eleInvHeadRoot.setAttribute(FinanceConstants.CREATETS, ABOFFinanceUtils.getCreateTimeStamp());
		eleInvHeadRoot.setAttribute(FinanceConstants.IS_PROCESSED, FinanceConstants.NO);
		// Adding invoice details to the publish document

		//B2B Start
		//eleInvHeadRoot.setAttribute(FinanceConstants.ORDER_TYPE, "SalesOrder");
		if(FinanceConstants.B2B.equalsIgnoreCase(eleOrder.getAttribute(FinanceConstants.ORDER_TYPE)))
		{
			eleInvHeadRoot.setAttribute(FinanceConstants.ORDER_TYPE, FinanceConstants.B2B);
		}
		else
		{
			eleInvHeadRoot.setAttribute(FinanceConstants.ORDER_TYPE, "SalesOrder");
		}
		//B2B End
		eleInvHeadRoot.setAttribute(FinanceConstants.INVOICE_DATE,
		        eleRootIn.getAttribute(FinanceConstants.DATE_INVOICED));
		eleInvHeadRoot.setAttribute(FinanceConstants.INVOICE_TYPE, FinanceConstants.SALES);
		eleInvHeadRoot.setAttribute(FinanceConstants.INVOICE_VALUE,
		        eleRootIn.getAttribute(FinanceConstants.TOTAL_AMOUNT));
		// COMMENT
		// Adding billing and shipping details to the publish document
		Element elePersonInfoBillTo = (Element) XPathAPI.selectSingleNode(eleRootIn,
		        "//InvoiceHeader/Order/PersonInfoBillTo");
		String strBilling[] = ABOFFinanceUtils.strAddress(elePersonInfoBillTo);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_NAME, strBilling[0]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_ADDRESS, strBilling[1]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_CITY, strBilling[2]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_STATE, strBilling[3]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_COUNTRY, strBilling[4]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_ZIPCODE, strBilling[5]);
		Element eleShpmnt = (Element) XPathAPI.selectSingleNode(eleRootIn, "Shipment");
		Element eleShpNode = (Element) XPathAPI.selectSingleNode(eleShpmnt, "ShipNode/ShipNodePersonInfo");
		StringBuffer strShpFrm = new StringBuffer();
		strShpFrm.append(eleShpNode.getAttribute(FinanceConstants.CITY));
		strShpFrm.append(FinanceConstants.ADDRESS_SPACE);
		strShpFrm.append(eleShpNode.getAttribute(FinanceConstants.STATE));
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIP_FROM, strShpFrm.toString());
		Element eleToAdd = (Element) eleShpmnt.getElementsByTagName(FinanceConstants.TO_ADDRESS).item(0);
		String strShipping[] = ABOFFinanceUtils.strAddress(eleToAdd);
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_NAME, strShipping[0]);
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_ADDRESS, strShipping[1]);
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_CITY, strBilling[2]);
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_STATE, strBilling[3]);
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_COUNTRY, strBilling[4]);
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_ZIPCODE, strBilling[5]);
		StringBuffer strShpTo = new StringBuffer();
		strShpTo.append(eleToAdd.getAttribute(FinanceConstants.CITY));
		strShpTo.append(FinanceConstants.ADDRESS_SPACE);
		strShpTo.append(eleToAdd.getAttribute(FinanceConstants.STATE));
		strShpTo.append(FinanceConstants.ADDRESS_SPACE);
		strShpTo.append(eleToAdd.getAttribute(FinanceConstants.ZIPCODE));
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIP_TO, strShpTo.toString());
		String strShmntNo = eleShpmnt.getAttribute(FinanceConstants.SHIPMENT_NO);
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIPMENT_NO, strShmntNo);
		eleInvHeadRoot.setAttribute(FinanceConstants.INVOICE_NO, FinanceConstants.INVOICE_PREFIX.concat(strShmntNo));
		eleInvHeadRoot.setAttribute(FinanceConstants.CARRIER_NAME, eleShpmnt.getAttribute(FinanceConstants.SCAC));
		eleInvHeadRoot.setAttribute(FinanceConstants.AWB_NO_FOR_CAMEL,
		        ABOFFinanceUtils.getAirwayBillNo(env, strShmntNo));

		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for populateInvoiceHeader" + XMLUtil.getXmlString(eleInvHeadRoot));
		}
		logger.endTimer("populateInvoiceHeader");
	}

	/**
	 * This method is used to set line level data for each order line in the
	 * publish document for SEND_INVOICE
	 * 
	 * @param docInvHeader
	 * @param eleRootIn
	 * @param eleInvHeadRoot
	 * @throws Exception
	 */
	private void populateInvoiceLines(YFSEnvironment env, Document docInvHeader, Element eleRootIn,
	        Element eleInvHeadRoot, String strPrimaryKey) throws Exception {
		logger.beginTimer("populateInvoiceLines");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateInvoiceLines" + XMLUtil.getXmlString(eleRootIn));
		}
		Element eleInvoiceLinesOut = docInvHeader.createElement(FinanceConstants.INVOICE_LINES);
		eleInvHeadRoot.appendChild(eleInvoiceLinesOut);

		// Getting Cost Of Goods Sold
		Map<String, Double> orderLineKeyCOGSMap = ABOFFinanceUtils.getCOGSForOrderLines(env, eleRootIn);
		// Finding each Line Detail element
		NodeList nlLineDetails = XPathAPI.selectNodeList(eleRootIn, "LineDetails/LineDetail");
		for (int intLD = 0; intLD < nlLineDetails.getLength(); intLD++) {
			Element eleLineDetailIn = (Element) nlLineDetails.item(intLD);
			Element eleOrderLine = (Element) eleLineDetailIn.getElementsByTagName(FinanceConstants.ORDER_LINE).item(0);
			Element eleInvoiceLineOut = docInvHeader.createElement(FinanceConstants.INVOICE_LINE);
			eleInvoiceLinesOut.appendChild(eleInvoiceLineOut);
			eleInvoiceLineOut.setAttribute(FinanceConstants.PRIME_LINE_NO,
			        eleOrderLine.getAttribute(FinanceConstants.PRIME_LINE_NO));
			String strLineKey = eleLineDetailIn.getAttribute(FinanceConstants.ORDER_LINE_KEY);

			Double dblCOGS = orderLineKeyCOGSMap.get(strLineKey);
			eleInvoiceLineOut.setAttribute(FinanceConstants.INVOICE_LINE_KEY,
			        ABOFFinanceUtils.getPrimaryKey(strLineKey));
			eleInvoiceLineOut.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
			Element eleItem = (Element) XPathAPI.selectSingleNode(eleLineDetailIn, "OrderLine/ItemDetails");
			if (!YFCCommon.isVoid(eleItem)) {
				eleInvoiceLineOut.setAttribute(FinanceConstants.SKU_CODE,
				        eleItem.getAttribute(FinanceConstants.ITEM_ID));
				Element elePrimInf = (Element) eleItem.getElementsByTagName(FinanceConstants.PRIMARY_INFORMATION)
				        .item(0);
				eleInvoiceLineOut.setAttribute(FinanceConstants.PRODUCT_DESC,
				        elePrimInf.getAttribute("ShortDescription"));
			}
			eleInvoiceLineOut.setAttribute(FinanceConstants.QUANTITY,
			        eleLineDetailIn.getAttribute(FinanceConstants.SHIPPED_QTY));
			String strLineTotal = eleLineDetailIn.getAttribute(FinanceConstants.LINE_TOTAL);
			/*GST Start:
			// Adding line tax value to the publish document
			if (null != XPathAPI.selectSingleNode(eleLineDetailIn, "LineTaxes/LineTax")) {
				Element eleTax = (Element) XPathAPI.selectSingleNode(eleLineDetailIn, "LineTaxes/LineTax");
				eleInvoiceLineOut.setAttribute(FinanceConstants.TAX_TYPE,
				        eleTax.getAttribute(FinanceConstants.REFERENCE_1));
				eleInvoiceLineOut.setAttribute(FinanceConstants.TAX_RATE,
				        eleTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
				eleInvoiceLineOut.setAttribute(FinanceConstants.TAX_AMOUNT, ABOFFinanceUtils.getLineTax(strLineTotal,
				        eleTax.getAttribute(FinanceConstants.TAX_PERCENTAGE)));
			}*/
			//Fetching LineTax elements for OrderLine
			NodeList nlLineTax = XPathAPI.selectNodeList(eleLineDetailIn, "LineTaxes/LineTax");
			
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
					eleInvoiceLineOut.setAttribute(FinanceConstants.EXT_TAX_TYPE,
							eleLineTax.getAttribute(FinanceConstants.REFERENCE_1));
					eleInvoiceLineOut.setAttribute(FinanceConstants.EXT_TAX_RATE,
							eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
					eleInvoiceLineOut.setAttribute(FinanceConstants.EXT_TAX_AMOUNT, ABOFFinanceUtils.getLineTax(strLineTotal,
							eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE),Double.toString(dblTotalTaxPct)));
				}else
				{
					eleInvoiceLineOut.setAttribute(FinanceConstants.TAX_TYPE,
							eleLineTax.getAttribute(FinanceConstants.REFERENCE_1));
					eleInvoiceLineOut.setAttribute(FinanceConstants.TAX_RATE,
							eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
					eleInvoiceLineOut.setAttribute(FinanceConstants.TAX_AMOUNT, ABOFFinanceUtils.getLineTax(strLineTotal,
							eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE),Double.toString(dblTotalTaxPct)));
					
				}
			
			}
			//GST end:
			// Adding pricing details of the item to the publish document
			eleInvoiceLineOut.setAttribute(FinanceConstants.COST_OF_GOODS_SOLD, String.valueOf(dblCOGS));
			eleInvoiceLineOut.setAttribute(FinanceConstants.LINE_TOTAL, strLineTotal);
			eleInvoiceLineOut.setAttribute(FinanceConstants.MRP,
			        XPathAPI.selectSingleNode(eleOrderLine, "LinePriceInfo/@UnitPrice").getNodeValue());

			// Setting line discounts
			Element eleSOLineDists = docInvHeader.createElement(FinanceConstants.INVOICE_LINE_DISCOUNTS);
			eleInvoiceLineOut.appendChild(eleSOLineDists);
			NodeList nlLineDist = XPathAPI.selectNodeList(eleLineDetailIn, "LineCharges/LineCharge");
			for (int intLC = 0; intLC < nlLineDist.getLength(); intLC++) {
				Element eleLineCharge = (Element) nlLineDist.item(intLC);
				String strChargeCategory = eleLineCharge.getAttribute(FinanceConstants.CHARGE_CATEGORY);
				if (FinanceConstants.DISCOUNT.equalsIgnoreCase(strChargeCategory)) {
					Element eleSOLineDist = docInvHeader.createElement(FinanceConstants.INVOICE_LINE_DISCOUNT);
					eleSOLineDists.appendChild(eleSOLineDist);
					eleSOLineDist.setAttribute(FinanceConstants.DISCOUNT_NAME,
					        eleLineCharge.getAttribute(FinanceConstants.CHARGE_NAME));
					eleSOLineDist.setAttribute(FinanceConstants.DISCOUNT_AMOUNT,
					        eleLineCharge.getAttribute(FinanceConstants.CHARGE_AMOUNT));
					eleSOLineDist.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
				}
			}
		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for populateInvoiceLines" + XMLUtil.getXmlString(eleInvHeadRoot));
		}
		logger.endTimer("populateInvoiceLines");
	}

	/**
	 * This method is used to set payments that have been invoiced in the
	 * publish document for SEND_INVOICE
	 * 
	 * @param invoiceDetailsDoc
	 * @param docInvHeader
	 * @param eleInvHeadRoot
	 * @param strOrderType
	 * @param strPrimaryKey
	 * @throws Exception
	 */
	private Map<String, Double> populateInvoicePayment(YFSEnvironment env, Document invoiceDetailsDoc,
	        Document docInvHeader, Element eleInvHeadRoot, String strOrderType, String strPrimaryKey) throws Exception {
			Element eleRootIn = invoiceDetailsDoc.getDocumentElement();
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateInvoicePayment" + XMLUtil.getXmlString(eleRootIn));
			logger.verbose("input document for populateInvoicePayment" + XMLUtil.getXmlString(eleInvHeadRoot));
			logger.verbose("input string for populateInvoicePayment" + strOrderType);
			logger.verbose("input string for populateInvoicePayment" + strPrimaryKey);
		}
			
		Element eleSOPmnts = docInvHeader.createElement(FinanceConstants.INVOICE_PAYMENTS);
		eleInvHeadRoot.appendChild(eleSOPmnts);
		NodeList nlCollectionDetail = XPathAPI.selectNodeList(eleRootIn,
		        "InvoiceHeader/CollectionDetails/CollectionDetail");
		Map<String, Double> paymentMethodAmountMap = new HashMap<String, Double>();
		double codPayment = 0.0;
		double storeCreditPayment = 0.0;
		double prepaidPayment = 0.0;
		double qwikcilverWallet=0.0;

		for (int intCD = 0; intCD < nlCollectionDetail.getLength(); intCD++) {
			Element eleCollectionDetail = (Element) nlCollectionDetail.item(intCD);
			
			double intDistAmt = Double.valueOf(eleCollectionDetail.getAttribute(FinanceConstants.DISTRIBUTED_AMOUNT))
			        * -1;
			
			if (intDistAmt != 0) {

				Element eleInvoicePmnts = docInvHeader.createElement(FinanceConstants.INVOICE_PAYMENT);
				eleSOPmnts.appendChild(eleInvoicePmnts);
				eleInvoicePmnts.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);

				eleInvoicePmnts.setAttribute(FinanceConstants.AMOUNT_RECEIVED, String.valueOf(intDistAmt));

				NodeList nlPayment = eleCollectionDetail.getElementsByTagName(FinanceConstants.PAYMENT_METHOD);
				if (YFCCommon.isVoid(nlPayment) || nlPayment.getLength() == 0) {
					logger.verbose("As PaymentMethod is blank, no need to move further");
					continue;
				}
				Element elePayment = (Element) nlPayment.item(0);
				String paymentType = elePayment.getAttribute(FinanceConstants.PAYMENT_TYPE);
				String amountCollected = eleCollectionDetail.getAttribute(FinanceConstants.AMOUNT_COLLECTED);
					
				/*
				 * double dblAmtCollected = Double.parseDouble(amountCollected)
				 * * -1; amountCollected = String.valueOf(dblAmtCollected);
				 */
				double dAmountCollected = YFCCommon.isVoid(amountCollected) ? 0.0 : Double.valueOf(amountCollected);
				
					if ("COD".equalsIgnoreCase(paymentType)) {
					logger.verbose("Entering COD Payment Method");
					
					codPayment = codPayment + dAmountCollected;
					
					logger.verbose("codPayment is ::"+codPayment);
				} else if ("STORE_CREDIT".equalsIgnoreCase(paymentType)) {
					logger.verbose("Entering STORE_CREDIT Payment Method");
					storeCreditPayment = storeCreditPayment + dAmountCollected;
					
					logger.verbose("storeCreditPayment is ::"+storeCreditPayment);
				} else if (FinanceConstants.QWIKCILVER_WALLET.equalsIgnoreCase(paymentType)) {
					logger.verbose("Entering QwikCilver Wallet Payment Method");
					qwikcilverWallet = qwikcilverWallet + dAmountCollected;
					
					logger.verbose("storeCreditPayment is ::"+storeCreditPayment);
				}  else {
					logger.verbose("Neither COD/Store_Credit Payment Method");
					prepaidPayment = prepaidPayment + dAmountCollected;
					logger.verbose("prepaidPayment is ::"+prepaidPayment);
				}

				eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TYPE, paymentType);
				eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_GATEWAY,
				        elePayment.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SIX));
				eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_REFERENCE,
				        elePayment.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SEVEN));
				eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TENDER,
				        elePayment.getAttribute(FinanceConstants.CREDIT_CARD_TYPE));

				// Calling method to check if payment type is prepaid or postpaid
				ArrayList<String> arrPayType = ABOFFinanceUtils.getCommonCodeForPrepaid(env);
				if (arrPayType.contains(paymentType)) 
				{
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
				} 
				//B2B Start
				else if(FinanceConstants.PAYMENT_TYPE_CREDIT.equalsIgnoreCase(paymentType))
				{
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE,paymentType);	
				}
				//B2B End
				else{				
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.COD);
					// Fixes to change from Postpaid to COD Sept 18
				}
			

			}
		}
		paymentMethodAmountMap.put("COD", codPayment);
		paymentMethodAmountMap.put("STORE_CREDIT", storeCreditPayment);
		paymentMethodAmountMap.put("QWIKCILVER_WALLET", qwikcilverWallet);
		paymentMethodAmountMap.put("PREPAID", prepaidPayment);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for populateInvoicePayment" + XMLUtil.getXmlString(eleInvHeadRoot));
			logger.verbose("output map for setInvoicePaymentDetails" + paymentMethodAmountMap);
		}
		logger.endTimer("populateInvoicePayment");
		return paymentMethodAmountMap;
	}

	/**
	 * This method is used to check if order is SO or EO and call methods to set
	 * attributes in the publish document for SEND_INVOICE transaction
	 * 
	 * @param env
	 * @param invoiceDetailsDoc
	 * @param codAmount
	 * @return
	 * @throws Exception
	 */
	private Document populateSODetailsOnInvoicing(YFSEnvironment env, Document invoiceDetailsDoc, double codAmount)
	        throws Exception {
		logger.beginTimer("populateSOAndEODetailsOnInvoicing");
		Element eleRoot = invoiceDetailsDoc.getDocumentElement();
		Element eleRootIn = (Element) eleRoot.getElementsByTagName(FinanceConstants.INVOICE_HEADER).item(0);
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateSOAndEODetailsOnInvoicing" + XMLUtil.getXmlString(eleRoot));
			logger.verbose(XMLUtil.getXmlString(eleRootIn));
		}
		Element elOrder = (Element) eleRootIn.getElementsByTagName(FinanceConstants.ORDER).item(0);
		// Creating document with Invoice Header
		Document docInvHeader = XMLUtil.createDocument(FinanceConstants.INVOICE_HEADER);
		Element eleInvHeadRoot = docInvHeader.getDocumentElement();
		String strPrimaryKey = ABOFFinanceUtils.getPrimaryKey(elOrder.getAttribute(FinanceConstants.ORDER_NO));
		// Setting header level attributes
		populateInvoiceHeader(env, eleRootIn, eleInvHeadRoot, strPrimaryKey, invoiceDetailsDoc);
		// Setting header level attributes based on order type
		String strOrderType = XPathAPI.selectSingleNode(eleRootIn, "Order/@OrderType").getNodeValue();
		eleInvHeadRoot.setAttribute(FinanceConstants.DOCUMENT_TYPE, FinanceConstants.SO);
		// Adding line level attributes to publish document
		populateInvoiceLines(env, docInvHeader, eleRootIn, eleInvHeadRoot, strPrimaryKey);
		NodeList nlHeaderChargeIn = XPathAPI.selectNodeList(eleRootIn, "HeaderCharges/HeaderCharge");
		String referenceKey = FinanceConstants.CHARGE_NAME;
		// Adding Header Level Charges to the publish document
		boolean isInvoicingEvent = true;
		//GST Change:start
		//Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getHeaderTaxPercentageAndType(env, eleRoot,isInvoicingEvent);
		Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getExtHdrTaxPercentAndType(env, eleRoot,isInvoicingEvent);
		//GST Change:end
		ABOFROInvoiceDetailsOnCreation.populateHeaderChargesDuringInvoiceCreation(env, nlHeaderChargeIn, eleInvHeadRoot,
		        referenceKey, headerTaxPercentageAndType);
		// Adding payment details to the publish document
		Map<String, Double> paymentMethodAmountMap = null;
		if (codAmount <= 0) {
			paymentMethodAmountMap = populateInvoicePayment(env, invoiceDetailsDoc, docInvHeader, eleInvHeadRoot,
			        strOrderType, strPrimaryKey);
		} else {
			paymentMethodAmountMap = populateInvoicePaymentForCOD(env, invoiceDetailsDoc, docInvHeader, eleInvHeadRoot,
			        codAmount, strPrimaryKey);
		}

		eleInvHeadRoot.setAttribute(FinanceConstants.STORE_CREDIT_AMOUNT,
		        "" + paymentMethodAmountMap.get("STORE_CREDIT"));

		eleInvHeadRoot.setAttribute(FinanceConstants.QWIKCILVER_WALLET_AMOUNT, "" + paymentMethodAmountMap.get(FinanceConstants.QWIKCILVER_WALLET));
		eleInvHeadRoot.setAttribute(FinanceConstants.PREPAID_AMOUNT, "" + paymentMethodAmountMap.get("PREPAID"));
		eleInvHeadRoot.setAttribute(FinanceConstants.COD_AMOUNT, "" + paymentMethodAmountMap.get("COD"));
		if (logger.isVerboseEnabled()) {
			logger.verbose(
			        "output document for populateSOAndEODetailsOnInvoicing" + XMLUtil.getXmlString(eleInvHeadRoot));
		}
		logger.endTimer("populateSOAndEODetailsOnInvoicing");
		return docInvHeader;
	}

	/**
	 * This method populate COD payment amount and remaining as Store credit in
	 * case of SO invoicing with COD payment
	 * 
	 * @param env
	 * @param invoiceDetailsDoc
	 * @param docInvHeader
	 * @param eleInvHeadRoot
	 * @param strOrderType
	 * @param strPrimaryKey
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private Map<String, Double> populateInvoicePaymentForCOD(YFSEnvironment env, Document invoiceDetailsDoc,
	        Document docInvHeader, Element eleInvHeadRoot, double codAmount, String strPrimaryKey)
	                throws IllegalArgumentException, Exception {
		logger.beginTimer("populateInvoicePaymentForCOD");
		Element eleRootIn = invoiceDetailsDoc.getDocumentElement();
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateInvoicePaymentForCOD" + XMLUtil.getXmlString(eleRootIn));
			logger.verbose("input document for populateInvoicePaymentForCOD" + XMLUtil.getXmlString(eleInvHeadRoot));
			logger.verbose("input string for populateInvoicePaymentForCOD" + strPrimaryKey);
		}
		Element eleSOPmnts = docInvHeader.createElement(FinanceConstants.INVOICE_PAYMENTS);
		eleInvHeadRoot.appendChild(eleSOPmnts);

		Element eleInvoicePmnt = docInvHeader.createElement(FinanceConstants.INVOICE_PAYMENT);
		eleSOPmnts.appendChild(eleInvoicePmnt);
		eleInvoicePmnt.setAttribute(FinanceConstants.AMOUNT_RECEIVED, "" + codAmount);
		eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_GATEWAY, "");
		eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.COD);
		eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_REFERENCE, "");
		eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_TYPE, "COD");
		eleInvoicePmnt.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);

		String totalAmount = eleInvHeadRoot.getAttribute(FinanceConstants.INVOICE_VALUE);
		double dStoreCreditAmount = getStoreCreditAmount(eleRootIn);
		double dQwikcilverWalletAmount = getQwikcilverWalletAmount(eleRootIn);
		double prepaidAmount = 0.0;
		if (!YFCCommon.isVoid(totalAmount)) {
			double dTotalAmount = Double.valueOf(totalAmount);
						
			//Calculating QWIKCILVER_WALLET money if exists
			prepaidAmount = dTotalAmount - (codAmount + dStoreCreditAmount+dQwikcilverWalletAmount);	
			
		}
		
		if (dQwikcilverWalletAmount > 0) {
			Element eleInvoicePmntForQC = docInvHeader.createElement(FinanceConstants.INVOICE_PAYMENT);
			eleSOPmnts.appendChild(eleInvoicePmntForQC);
			eleInvoicePmntForQC.setAttribute(FinanceConstants.AMOUNT_RECEIVED, "" + dQwikcilverWalletAmount);
			eleInvoicePmntForQC.setAttribute(FinanceConstants.PAYMENT_GATEWAY, "");
			eleInvoicePmntForQC.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
			eleInvoicePmntForQC.setAttribute(FinanceConstants.PAYMENT_REFERENCE, "");
			eleInvoicePmntForQC.setAttribute(FinanceConstants.PAYMENT_TYPE, FinanceConstants.QWIKCILVER_WALLET);
			eleInvoicePmntForQC.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
		}
		
		if (dStoreCreditAmount > 0) {
			Element eleInvoicePmntForSC = docInvHeader.createElement(FinanceConstants.INVOICE_PAYMENT);
			eleSOPmnts.appendChild(eleInvoicePmntForSC);
			eleInvoicePmntForSC.setAttribute(FinanceConstants.AMOUNT_RECEIVED, "" + dStoreCreditAmount);
			eleInvoicePmntForSC.setAttribute(FinanceConstants.PAYMENT_GATEWAY, "");
			eleInvoicePmntForSC.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
			eleInvoicePmntForSC.setAttribute(FinanceConstants.PAYMENT_REFERENCE, "");
			eleInvoicePmntForSC.setAttribute(FinanceConstants.PAYMENT_TYPE, "STORE_CREDIT");
			eleInvoicePmntForSC.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
		}
		
		if (prepaidAmount > 0) {
			Element eleInvoicePmntForPP = docInvHeader.createElement(FinanceConstants.INVOICE_PAYMENT);
			eleSOPmnts.appendChild(eleInvoicePmntForPP);
			eleInvoicePmntForPP.setAttribute(FinanceConstants.AMOUNT_RECEIVED, "" + prepaidAmount);
			eleInvoicePmntForPP.setAttribute(FinanceConstants.PAYMENT_GATEWAY, "");
			eleInvoicePmntForPP.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
			eleInvoicePmntForPP.setAttribute(FinanceConstants.PAYMENT_REFERENCE, "");
			eleInvoicePmntForPP.setAttribute(FinanceConstants.PAYMENT_TYPE,FinanceConstants.PREPAID);
			eleInvoicePmntForPP.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
		}
				

		Map<String, Double> paymentMethodAmountMap = new HashMap<String, Double>();
		paymentMethodAmountMap.put("COD", codAmount);
		paymentMethodAmountMap.put("STORE_CREDIT", dStoreCreditAmount);
		paymentMethodAmountMap.put(FinanceConstants.QWIKCILVER_WALLET, dQwikcilverWalletAmount);
		paymentMethodAmountMap.put("PREPAID", prepaidAmount);

		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for populateInvoicePayment" + XMLUtil.getXmlString(eleInvHeadRoot));
		}
		logger.endTimer("populateInvoicePayment");
		return paymentMethodAmountMap;
	}
	
	private Double getStoreCreditAmount (Element eleRootIn) throws Exception {
				
		NodeList nlCollectionDetail = XPathAPI.selectNodeList(eleRootIn,"InvoiceHeader/CollectionDetails/CollectionDetail");
		double storeCreditPayment = 0.0;

		for (int intCD = 0; intCD < nlCollectionDetail.getLength(); intCD++) {
			Element eleCollectionDetail = (Element) nlCollectionDetail.item(intCD);
			
			double intDistAmt = Double.valueOf(eleCollectionDetail.getAttribute(FinanceConstants.DISTRIBUTED_AMOUNT)) * -1;
			
			if (intDistAmt != 0) {

				NodeList nlPayment = eleCollectionDetail.getElementsByTagName(FinanceConstants.PAYMENT_METHOD);
				if (YFCCommon.isVoid(nlPayment) || nlPayment.getLength() == 0) {
					logger.verbose("As PaymentMethod is blank, no need to move further");
					continue;
				}
				Element elePayment = (Element) nlPayment.item(0);
				String paymentType = elePayment.getAttribute(FinanceConstants.PAYMENT_TYPE);
				String amountCollected = eleCollectionDetail.getAttribute(FinanceConstants.AMOUNT_COLLECTED);
				
				/*
				 * double dblAmtCollected = Double.parseDouble(amountCollected)
				 * * -1; amountCollected = String.valueOf(dblAmtCollected);
				 */
				double dAmountCollected = YFCCommon.isVoid(amountCollected) ? 0.0 : Double.valueOf(amountCollected);
				
				if ("STORE_CREDIT".equalsIgnoreCase(paymentType)) {
					storeCreditPayment = storeCreditPayment + dAmountCollected;
					
					logger.verbose("storeCreditPayment is ::"+storeCreditPayment);
				} 

			}
		}
		return storeCreditPayment;
	}
	private Double getQwikcilverWalletAmount (Element eleRootIn) throws Exception {
		
		NodeList nlCollectionDetail = XPathAPI.selectNodeList(eleRootIn,"InvoiceHeader/CollectionDetails/CollectionDetail");
		double qwikcilverWalletAmount = 0.0;

		for (int intCD = 0; intCD < nlCollectionDetail.getLength(); intCD++) {
			Element eleCollectionDetail = (Element) nlCollectionDetail.item(intCD);
			
			double intDistAmt = Double.valueOf(eleCollectionDetail.getAttribute(FinanceConstants.DISTRIBUTED_AMOUNT)) * -1;
			
			if (intDistAmt != 0) {

				NodeList nlPayment = eleCollectionDetail.getElementsByTagName(FinanceConstants.PAYMENT_METHOD);
				if (YFCCommon.isVoid(nlPayment) || nlPayment.getLength() == 0) {
					logger.verbose("As PaymentMethod is blank, no need to move further");
					continue;
				}
				Element elePayment = (Element) nlPayment.item(0);
				String paymentType = elePayment.getAttribute(FinanceConstants.PAYMENT_TYPE);
				String amountCollected = eleCollectionDetail.getAttribute(FinanceConstants.AMOUNT_COLLECTED);
				
				/*
				 * double dblAmtCollected = Double.parseDouble(amountCollected)
				 * * -1; amountCollected = String.valueOf(dblAmtCollected);
				 */
				double dAmountCollected = YFCCommon.isVoid(amountCollected) ? 0.0 : Double.valueOf(amountCollected);
				
				if (FinanceConstants.QWIKCILVER_WALLET.equalsIgnoreCase(paymentType)) {
					qwikcilverWalletAmount = qwikcilverWalletAmount + dAmountCollected;
					
					logger.verbose("qwikcilverWalletAmount is ::"+qwikcilverWalletAmount);
				} 

			}
		}
		return qwikcilverWalletAmount;
	}
	private void callRequestCollection (YFSEnvironment env,String sOrderHeaderKey) throws Exception {
		Document inDocReqCollection = XMLUtil.createDocument(FinanceConstants.ORDER);
		inDocReqCollection.getDocumentElement().setAttribute(FinanceConstants.ORDER_HEADER_KEY, sOrderHeaderKey);
		XMLUtil.invokeAPI(env, FinanceConstants.API_REQUEST_COLLECTION, inDocReqCollection,
		        null);
		
	}

}
