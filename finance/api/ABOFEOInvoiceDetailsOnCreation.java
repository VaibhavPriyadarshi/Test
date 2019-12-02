package com.abof.finance.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.abof.finance.utils.ABOFFinanceUtils;
import com.abof.finance.utils.FinanceConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;

public class ABOFEOInvoiceDetailsOnCreation implements YIFCustomApi {

	@Override
	public void setProperties(Properties arg0) throws Exception {

	}

	private static YFCLogCategory logger = YFCLogCategory.instance(ABOFEOInvoiceDetailsOnCreation.class);

	/**
	 * This method is invoked on Create_Order_Inovoice0003. On_Invoice_Creation
	 * event and on Send_Invoice0003.On_Publish_Invoice
	 * 
	 * @param env
	 * @param inputDoc
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public Document publishEOInvoiceDetails(YFSEnvironment env, Document inputDoc)
	        throws IllegalArgumentException, Exception {
		logger.beginTimer("publishEOInvoiceDetails");
		if (logger.isVerboseEnabled()) {
			logger.verbose(
			        "input document for publishEOInvoiceDetails" + XMLUtil.getXmlString(inputDoc.getDocumentElement()));
		}
		Document invoiceHeaderOutDoc = populateEODetailsOnInvoiceCreation(env, inputDoc);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for publishEOInvoiceDetails"
			        + XMLUtil.getXmlString(invoiceHeaderOutDoc.getDocumentElement()));
		}
		return invoiceHeaderOutDoc;
	}

	/**
	 * This method is used to check if order is SO or EO and call methods to set
	 * attributes in the publish document for SEND_INVOICE transaction
	 * 
	 * @param env
	 * @param invoiceDetailsDoc
	 * @return
	 * @throws Exception
	 */
	private Document populateEODetailsOnInvoiceCreation(YFSEnvironment env, Document invoiceDetailsDoc)
	        throws Exception {
		logger.beginTimer("populateEODetailsOnInvoiceCreation");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateEODetailsOnInvoiceCreation"
			        + XMLUtil.getXmlString(invoiceDetailsDoc.getDocumentElement()));
		}
		Element eleRoot = invoiceDetailsDoc.getDocumentElement();
		Element eleRootIn = (Element) eleRoot.getElementsByTagName(FinanceConstants.INVOICE_HEADER).item(0);
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateEODetailsOnInvoiceCreation" + XMLUtil.getXmlString(eleRoot));
			logger.verbose(XMLUtil.getXmlString(eleRootIn));
		}
		Element elOrder = (Element) eleRootIn.getElementsByTagName(FinanceConstants.ORDER).item(0);
		Document docInvHeader = XMLUtil.createDocument(FinanceConstants.INVOICE_HEADER);
		Element eleInvHeadRoot = docInvHeader.getDocumentElement();
		String invoiceNo = eleRootIn.getAttribute(FinanceConstants.INVOICE_NO);
		String strPrimaryKey = ABOFFinanceUtils
		        .getPrimaryKey(elOrder.getAttribute(FinanceConstants.ORDER_NO) + invoiceNo);
		populateInvoiceHeader(env, eleRootIn, eleInvHeadRoot, strPrimaryKey, invoiceDetailsDoc);
		String strOrderType = XPathAPI.selectSingleNode(eleRootIn, "Order/@OrderType").getNodeValue();
		Set<String> soOrderLineKeyForEO = new HashSet<String>();
		if (FinanceConstants.RETURN_EXCHANGE.equalsIgnoreCase(strOrderType)) {
			eleInvHeadRoot.setAttribute(FinanceConstants.DOCUMENT_TYPE, FinanceConstants.EO);
			String strEOOrderHeaderKey = XPathAPI.selectSingleNode(eleRootIn, "Order/@OrderHeaderKey").getNodeValue();
			String strDredFromOrderHeaderKey = getDerivedOrderHeaderKey(env, strEOOrderHeaderKey, soOrderLineKeyForEO);
			populateEOAttributes(env, strDredFromOrderHeaderKey, eleInvHeadRoot, soOrderLineKeyForEO);
		}
		// Adding line level attributes to publish document
		populateInvoiceLines(env, docInvHeader, eleRootIn, eleInvHeadRoot, strPrimaryKey, soOrderLineKeyForEO);
		NodeList nlHeaderChargeIn = XPathAPI.selectNodeList(eleRootIn, "HeaderCharges/HeaderCharge");
		String referenceKey = FinanceConstants.CHARGE_NAME;
		// Adding Header Level Charges to the publish document
		boolean isInvoicingEvent = true;
		//GST start:
		//Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getHeaderTaxPercentageAndType(env, eleRoot,isInvoicingEvent);
		Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getExtHdrTaxPercentAndType(env, eleRoot,isInvoicingEvent);
		//GST End:
		ABOFROInvoiceDetailsOnCreation.populateHeaderChargesDuringInvoiceCreation(env, nlHeaderChargeIn, eleInvHeadRoot,
		        referenceKey, headerTaxPercentageAndType);
		// Adding payment details to the publish document
		populateInvoicePayment(env, invoiceDetailsDoc, docInvHeader, eleInvHeadRoot, strOrderType, strPrimaryKey);
		if (logger.isVerboseEnabled()) {
			logger.verbose(
			        "output document for populateSOAndEODetailsOnInvoicing" + XMLUtil.getXmlString(eleInvHeadRoot));
		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for populateEODetailsOnInvoiceCreation"
			        + XMLUtil.getXmlString(docInvHeader.getDocumentElement()));
		}
		logger.endTimer("populateSOAndEODetailsOnInvoicing");
		return docInvHeader;
	}

	/**
	 * This method returns derivedOrderHeaderKey transaction
	 * 
	 * @param env
	 * @param strEOOrderHeaderKey
	 * @return String
	 * @throws Exception
	 */

	private String getDerivedOrderHeaderKey(YFSEnvironment env, String strEOOrderHeaderKey,
	        Set<String> soOrderLineKeyForEO) throws ParserConfigurationException, Exception, TransformerException {
		logger.beginTimer("getDerivedOrderHeaderKey");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input string for getDerivedOrderHeaderKey" + strEOOrderHeaderKey);
		}
		Document orderDoc = XMLUtil.createDocument(FinanceConstants.ORDER);
		Element elementRoot = orderDoc.getDocumentElement();
		elementRoot.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strEOOrderHeaderKey);
		Document orderListTempDoc = XMLUtil.getDocument(
		        "<OrderList><Order OrderNo=''><ReturnOrdersForExchange><ReturnOrderForExchange OrderNo='' OrderHeaderKey=''/></ReturnOrdersForExchange></Order></OrderList>");
		Document orderListopDOc = XMLUtil.invokeAPI(env, FinanceConstants.GET_ORDER_LIST_API, orderDoc,
		        orderListTempDoc);

		Element eleOrd = orderListopDOc.getDocumentElement();

		String strROOrderHeaderKey = XPathAPI
		        .selectSingleNode(eleOrd, "Order/ReturnOrdersForExchange/ReturnOrderForExchange/@OrderHeaderKey")
		        .getNodeValue();

		Document roOrderDoc = XMLUtil.createDocument(FinanceConstants.ORDER);
		Element elemenRoot = roOrderDoc.getDocumentElement();
		elemenRoot.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strROOrderHeaderKey);
		Document roOrderListTempDoc = XMLUtil.getDocument(
		        "<OrderList><Order OrderNo=''><OrderLines><OrderLine DerivedFromOrderHeaderKey='' DerivedFromOrderLineKey=''/></OrderLines></Order></OrderList>");
		Document roOrderListopDOc = XMLUtil.invokeAPI(env, FinanceConstants.GET_ORDER_LIST_API, roOrderDoc,
		        roOrderListTempDoc);

		NodeList nlOrderLine = XPathAPI.selectNodeList(roOrderListopDOc, "OrderList/Order/OrderLines/OrderLine");
		if (soOrderLineKeyForEO.isEmpty()) {
			for (int i = 0; i < nlOrderLine.getLength(); i++) {
				Element orderLineEle = (Element) nlOrderLine.item(i);
				String derivedFromOLKey = orderLineEle.getAttribute(FinanceConstants.DERIVED_FROM_OL_KEY);
				logger.verbose(
				        "Got Sales OrderLineKey (associated to this EO):" + derivedFromOLKey + ": for iteration:" + i);
				soOrderLineKeyForEO.add(derivedFromOLKey);
			}
		}

		String strDredFromOrderHeaderKey = XPathAPI
		        .selectSingleNode(roOrderListopDOc, "OrderList/Order/OrderLines/OrderLine/@DerivedFromOrderHeaderKey")
		        .getNodeValue();
		if (logger.isVerboseEnabled()) {
			logger.verbose("output string for getDerivedOrderHeaderKey" + strDredFromOrderHeaderKey);
			logger.verbose("Set of OrderLineKey of SO associated to EO is" + soOrderLineKeyForEO);
		}
		logger.endTimer("getDerivedOrderHeaderKey");
		return strDredFromOrderHeaderKey;
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

		eleInvHeadRoot.setAttribute(FinanceConstants.IS_PROCESSED, FinanceConstants.NO);
		eleInvHeadRoot.setAttribute(FinanceConstants.CREATE_TIME_STAMP, ABOFFinanceUtils.getCreateTimeStamp());

		// Adding invoice details to the publish document

		eleInvHeadRoot.setAttribute(FinanceConstants.ORDER_TYPE, FinanceConstants.RETURN_EXCHANGE);
		eleInvHeadRoot.setAttribute(FinanceConstants.INVOICE_DATE,
		        eleRootIn.getAttribute(FinanceConstants.DATE_INVOICED));
		eleInvHeadRoot.setAttribute(FinanceConstants.INVOICE_TYPE, FinanceConstants.EXCHANGE);
		String totalAmount = eleRootIn.getAttribute(FinanceConstants.TOTAL_AMOUNT);
		eleInvHeadRoot.setAttribute(FinanceConstants.INVOICE_VALUE, totalAmount);
		eleInvHeadRoot.setAttribute(FinanceConstants.PREPAID_AMOUNT, totalAmount);

		Element elePersonInfoBillTo = (Element) XPathAPI.selectSingleNode(eleRootIn,
		        "//InvoiceHeader/Order/PersonInfoBillTo");
		String strBilling[] = ABOFFinanceUtils.strAddress(elePersonInfoBillTo);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_NAME, strBilling[0]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_ADDRESS, strBilling[1]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_CITY, strBilling[2]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_STATE, strBilling[3]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_COUNTRY, strBilling[4]);
		eleInvHeadRoot.setAttribute(FinanceConstants.BILLING_ZIPCODE, strBilling[5]);
		Element eleShpmnt = null;
		String strShpmntNo = FinanceConstants.BLANK;
		if (null != XPathAPI.selectSingleNode(eleRootIn, FinanceConstants.SHIPMENT)) {
			eleShpmnt = (Element) XPathAPI.selectSingleNode(eleRootIn, FinanceConstants.SHIPMENT);
			strShpmntNo = eleShpmnt.getAttribute(FinanceConstants.SHIPMENT_NO);
		}
		StringBuffer strShpFrm = new StringBuffer();
		if (null != XPathAPI.selectSingleNode(eleShpmnt, "ShipNode/ShipNodePersonInfo")) {
			Element eleShpNode = (Element) XPathAPI.selectSingleNode(eleShpmnt, "ShipNode/ShipNodePersonInfo");
			strShpFrm.append(eleShpNode.getAttribute(FinanceConstants.CITY));
			strShpFrm.append(FinanceConstants.ADDRESS_SPACE);
			strShpFrm.append(eleShpNode.getAttribute(FinanceConstants.STATE));
		}
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIP_FROM, strShpFrm.toString());
		StringBuffer strShpTo = new StringBuffer();
		if (null != eleShpmnt.getElementsByTagName(FinanceConstants.TO_ADDRESS)) {
			Element eleToAdd = (Element) eleShpmnt.getElementsByTagName(FinanceConstants.TO_ADDRESS).item(0);
			String strShipping[] = ABOFFinanceUtils.strAddress(eleToAdd);
			eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_NAME, strShipping[0]);
			eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_ADDRESS, strShipping[1]);
			eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_CITY, strBilling[2]);
			eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_STATE, strBilling[3]);
			eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_COUNTRY, strBilling[4]);
			eleInvHeadRoot.setAttribute(FinanceConstants.SHIPPING_ZIPCODE, strBilling[5]);
			strShpTo.append(eleToAdd.getAttribute(FinanceConstants.CITY));
			strShpTo.append(FinanceConstants.ADDRESS_SPACE);
			strShpTo.append(eleToAdd.getAttribute(FinanceConstants.STATE));
			strShpTo.append(FinanceConstants.ADDRESS_SPACE);
			strShpTo.append(eleToAdd.getAttribute(FinanceConstants.ZIPCODE));
		}
		eleInvHeadRoot.setAttribute(FinanceConstants.SHIP_TO, strShpTo.toString());

		eleInvHeadRoot.setAttribute(FinanceConstants.SHIPMENT_NO, strShpmntNo);
		eleInvHeadRoot.setAttribute(FinanceConstants.INVOICE_NO, FinanceConstants.INVOICE_PREFIX.concat(strShpmntNo));
		eleInvHeadRoot.setAttribute(FinanceConstants.CARRIER_NAME, eleShpmnt.getAttribute(FinanceConstants.SCAC));
		eleInvHeadRoot.setAttribute(FinanceConstants.AWB_NO_FOR_CAMEL,
		        ABOFFinanceUtils.getAirwayBillNo(env, strShpmntNo));
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for populateInvoiceHeader"
			        + XMLUtil.getXmlString(invoiceDetailsDoc.getDocumentElement()));
		}
	}

	/**
	 * This method is used to set attributes for Exchange Order in the publish
	 * document for SEND_INVOICE
	 * 
	 * @param env
	 * @param strOrderHeaderKey
	 * @param eleInvHeadRoot
	 * @param soOrderLineKeyForEO
	 * @throws Exception
	 */
	private void populateEOAttributes(YFSEnvironment env, String strOrderHeaderKey, Element eleInvHeadRoot,
	        Set<String> soOrderLineKeyForEO) throws Exception {
		logger.beginTimer("populateEOAttributes");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input string for populateEOAttributes" + strOrderHeaderKey);
			logger.verbose("input element for populateEOAttributes" + XMLUtil.getXmlString(eleInvHeadRoot));
		}

		// Creating input document for calling get order list API
		List<Map<String, HashSet<String>>> listOfHashMap = ABOFFinanceUtils.getAssSalesInvoiceNos(env,
		        strOrderHeaderKey, eleInvHeadRoot, soOrderLineKeyForEO);
		Map<String, HashSet<String>> hmInvoiceNo = listOfHashMap.get(0);

		HashSet<String> setInvoiceNo = hmInvoiceNo.get("Key1");
		HashSet<String> setDateInvoiced = hmInvoiceNo.get("Key2");
		String sep1 = "";
		String sep2 = "";
		Iterator<String> itr1 = setInvoiceNo.iterator();
		Iterator<String> itr2 = setDateInvoiced.iterator();

		StringBuilder sbSalesInvoice = new StringBuilder();
		StringBuilder sbInvoiceDate = new StringBuilder();
		while (itr1.hasNext()) {
			sbSalesInvoice.append(sep1);
			sep1 = ",";
			sbSalesInvoice.append(itr1.next());
		}
		while (itr2.hasNext()) {
			sbInvoiceDate.append(sep2);
			sep2 = ",";
			sbInvoiceDate.append(itr2.next());
		}

		eleInvHeadRoot.setAttribute(FinanceConstants.SALES_INVOICE_DATE, String.valueOf(sbInvoiceDate));
		eleInvHeadRoot.setAttribute(FinanceConstants.SALES_INVOICE_NO, String.valueOf(sbSalesInvoice));
		eleInvHeadRoot.setAttribute(FinanceConstants.ORDER_TYPE, FinanceConstants.RETURN_EXCHANGE);

		if (logger.isVerboseEnabled()) {
			logger.verbose("get order list output for populateEOAttributes" + XMLUtil.getXmlString(eleInvHeadRoot));
		}
		logger.endTimer("populateEOAttributes");
	}

	/**
	 * This method is used to set line level data for each order line in the
	 * publish document for SEND_INVOICE
	 * 
	 * @param docInvHeader
	 * @param eleRootIn
	 * @param eleInvHeadRoot
	 * @param soOrderLineKeyForEO
	 * @throws Exception
	 */
	private void populateInvoiceLines(YFSEnvironment env, Document docInvHeader, Element eleRootIn,
	        Element eleInvHeadRoot, String strPrimaryKey, Set<String> soOrderLineKeyForEO) throws Exception {
		logger.beginTimer("populateInvoiceLines");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for populateInvoiceLines" + XMLUtil.getXmlString(eleRootIn));
		}
		// String strEOOrderHeaderKey = XPathAPI.selectSingleNode(eleRootIn,
		// "Order/@OrderHeaderKey").getNodeValue();
		// String strSalesOrderHeaderKey = getDerivedOrderHeaderKey(env,
		// strEOOrderHeaderKey);

		Map<String, Double> orderLineKeyCOGSMap = ABOFFinanceUtils.getCOGSForOrderLines(env, eleRootIn);
		if (logger.isVerboseEnabled()) {
			logger.verbose("COGS map is " + orderLineKeyCOGSMap);
		}

		// Element eleOrder = (Element)
		// eleRootIn.getElementsByTagName(FinanceConstants.ORDER).item(0);
		// String strOrderHeaderKey =
		// eleOrder.getAttribute(FinanceConstants.ORDER_HEADER_KEY);

		Element eleInvoiceLinesOut = docInvHeader.createElement(FinanceConstants.INVOICE_LINES);
		eleInvHeadRoot.appendChild(eleInvoiceLinesOut);
		// Finding each Line Detail element
		NodeList nlLineDetails = XPathAPI.selectNodeList(eleRootIn, "LineDetails/LineDetail");
		for (int intLD = 0; intLD < nlLineDetails.getLength(); intLD++) {
			Element eleLineDetailIn = (Element) nlLineDetails.item(intLD);
			Element eleOrderLine = (Element) eleLineDetailIn.getElementsByTagName(FinanceConstants.ORDER_LINE).item(0);
			// String strOrderLineKey =
			// eleOrderLine.getAttribute(FinanceConstants.ORDER_LINE_KEY);
			Element eleInvoiceLineOut = docInvHeader.createElement(FinanceConstants.INVOICE_LINE);
			eleInvoiceLinesOut.appendChild(eleInvoiceLineOut);
			eleInvoiceLineOut.setAttribute(FinanceConstants.PRIME_LINE_NO,
			        eleOrderLine.getAttribute(FinanceConstants.PRIME_LINE_NO));
			String strLineKey = eleLineDetailIn.getAttribute(FinanceConstants.ORDER_LINE_KEY);
			eleInvoiceLineOut.setAttribute(FinanceConstants.INVOICE_LINE_KEY,
			        ABOFFinanceUtils.getPrimaryKey(strLineKey));
			eleInvoiceLineOut.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
			Double dblCOGS = orderLineKeyCOGSMap.get(strLineKey);
			// Element eleItem = (Element)
			// XPathAPI.selectSingleNode(eleLineDetailIn, "OrderLine/Item");
			Element eleItemDetails = (Element) XPathAPI.selectSingleNode(eleLineDetailIn, "OrderLine/ItemDetails");

			if (!YFCCommon.isVoid(eleItemDetails)) {
				eleInvoiceLineOut.setAttribute(FinanceConstants.SKU_CODE,
				        eleItemDetails.getAttribute(FinanceConstants.ITEM_ID));
				Element elePrimInf = (Element) eleItemDetails.getElementsByTagName(FinanceConstants.PRIMARY_INFORMATION)
				        .item(0);
				eleInvoiceLineOut.setAttribute(FinanceConstants.PRODUCT_DESC,
				        elePrimInf.getAttribute("ShortDescription"));
			}
			eleInvoiceLineOut.setAttribute(FinanceConstants.QUANTITY,
			        eleLineDetailIn.getAttribute(FinanceConstants.SHIPPED_QTY));

			// Adding line tax value to the publish document
			String strLineTotal = eleLineDetailIn.getAttribute(FinanceConstants.LINE_TOTAL);
			eleInvoiceLineOut.setAttribute(FinanceConstants.LINE_TOTAL, strLineTotal);
			/*GST Start:
			Element eleTax = (Element) XPathAPI.selectSingleNode(eleLineDetailIn, "LineTaxes/LineTax");
			if (null != XPathAPI.selectSingleNode(eleLineDetailIn, "LineTaxes/LineTax")) {
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
			
			//GST End:
			// Adding pricing details of the item to the publish document
			eleInvoiceLineOut.setAttribute(FinanceConstants.COST_OF_GOODS_SOLD, String.valueOf(dblCOGS));

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
			// populateROInvoiceLineDiscounts(env, eleLineDetailIn,
			// eleInvoiceLineOut);

			// String strSalesOrderHeaderKey = getDerivedOrderHeaderKey(env,
			// strOrderHeaderKey,
			// soOrderLineKeyForEO);
			// setAssocSalesAtHeaderLevel(env, strSalesOrderHeaderKey,
			// eleInvoiceLineOut, eleInvHeadRoot,
			// strOrderLineKey);
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
	private void populateInvoicePayment(YFSEnvironment env, Document invoiceDetailsDoc, Document docInvHeader,
	        Element eleInvHeadRoot, String strOrderType, String strPrimaryKey) throws Exception {
		logger.beginTimer("populateInvoicePayment");
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

		String strChargeType = "";
		if (YFCCommon.isVoid(nlCollectionDetail) || nlCollectionDetail.getLength() == 0) {
			Element eleInvoicePmnts = docInvHeader.createElement(FinanceConstants.INVOICE_PAYMENT);
			eleSOPmnts.appendChild(eleInvoicePmnts);
			eleInvoicePmnts.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
			eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TYPE, "ADJUSTED_TO_RETURN");
			eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
			eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_REFERENCE, "");
			String strAmountReceived = eleInvHeadRoot.getAttribute(FinanceConstants.INVOICE_VALUE);
			eleInvoicePmnts.setAttribute(FinanceConstants.AMOUNT_RECEIVED, strAmountReceived);
			eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_GATEWAY, "");
			eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TENDER, "");
			if (logger.isVerboseEnabled()) {
				logger.verbose("Adding payment details when collection details not found. Now output document is "
				        + XMLUtil.getXmlString(eleInvHeadRoot));
			}
		}

		for (int intCD = 0; intCD < nlCollectionDetail.getLength(); intCD++) {
			Element eleCollectionDetail = (Element) nlCollectionDetail.item(intCD);
			double intDistAmt = Double.valueOf(eleCollectionDetail.getAttribute(FinanceConstants.DISTRIBUTED_AMOUNT))
			        * -1;
			if (intDistAmt != 0) {

				Element eleInvoicePmnts = docInvHeader.createElement(FinanceConstants.INVOICE_PAYMENT);
				eleSOPmnts.appendChild(eleInvoicePmnts);
				eleInvoicePmnts.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);

				eleInvoicePmnts.setAttribute(FinanceConstants.AMOUNT_RECEIVED, String.valueOf(intDistAmt));
				strChargeType = eleCollectionDetail.getAttribute("ChargeType");

				if ("TRANSFER_OUT".equalsIgnoreCase(strChargeType)) {

					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TYPE, "ADJUSTED_TO_EXCHANGE");
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_REFERENCE, "");
				} else if ("TRANSFER_IN".equalsIgnoreCase(strChargeType)) {

					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TYPE, "ADJUSTED_TO_RETURN");
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_REFERENCE, "");
				} else {
					NodeList nlPayment = eleCollectionDetail.getElementsByTagName(FinanceConstants.PAYMENT_METHOD);
					if (YFCCommon.isVoid(nlPayment) || nlPayment.getLength() == 0) {
						logger.verbose("As PaymentMethod is blank, no need to move further");
						continue;
					}
					Element elePayment = (Element) nlPayment.item(0);

					String strPayMode = elePayment.getAttribute(FinanceConstants.PAYMENT_TYPE);
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TYPE, strPayMode);
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_GATEWAY,
					        elePayment.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SIX));
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_REFERENCE,
					        elePayment.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SEVEN));
					eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_TENDER,
					        elePayment.getAttribute(FinanceConstants.CREDIT_CARD_TYPE));

					// Calling method to check if payment type is prepaid or
					// postpaid
					ArrayList<String> arrPayType = ABOFFinanceUtils.getCommonCodeForPrepaid(env);
					if (arrPayType.contains(strPayMode)) {
						eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
					} else {
						eleInvoicePmnts.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.COD);
					}

				}

			}
		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for populateInvoicePayment" + XMLUtil.getXmlString(eleInvHeadRoot));
		}
		logger.endTimer("populateInvoicePayment");
	}

}
