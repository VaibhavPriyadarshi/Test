package com.abof.finance.utils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.transform.TransformerException;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.abof.sterling.util.XmlConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;

public class ABOFFinanceUtils {
	private static YFCLogCategory logger = YFCLogCategory.instance(ABOFFinanceUtils.class);

	/**
	 * This method is used to set the sales order details by calling get order
	 * list with the order header key
	 * 
	 * @param env
	 * @param strOrderHeaderKey
	 * @param orderInvoiceEle
	 * @throws Exception
	 */
	public static void setSalesOrderValues(YFSEnvironment env, String strOrderHeaderKey, Element orderInvoiceEle)
			throws Exception {
		logger.beginTimer("setSalesOrderValues");
		Document inDoc = XMLUtil.createDocument(FinanceConstants.ORDER);
		Element eleRootInput = inDoc.getDocumentElement();
		eleRootInput.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strOrderHeaderKey);
		Document docTemp = XMLUtil.getDocument(
				"<OrderList><Order OrderDate='' CustomerFirstName='' CustomerLastName='' CustomerPhoneNo='' CustomerEMailID='' /></OrderList>");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for get order list" + XMLUtil.getXmlString(inDoc.getDocumentElement()));
		}
		Document outDoc = XMLUtil.invokeAPI(env, FinanceConstants.GET_ORDER_LIST, inDoc, docTemp);
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for get order list" + XMLUtil.getXmlString(outDoc.getDocumentElement()));
		}
		Element eleRootOutput = outDoc.getDocumentElement();
		Element eleOrder = (Element) eleRootOutput.getElementsByTagName(FinanceConstants.ORDER).item(0);
		orderInvoiceEle.setAttribute(FinanceConstants.SALES_ORDER_DATE,
				eleOrder.getAttribute(FinanceConstants.ORDER_DATE));
		StringBuffer strCustName = new StringBuffer();
		strCustName.append(eleOrder.getAttribute(FinanceConstants.CUSTOMER_FIRST_NAME));
		strCustName.append(FinanceConstants.SPACE);
		strCustName.append(eleOrder.getAttribute(FinanceConstants.CUSTOMER_LAST_NAME));
		orderInvoiceEle.setAttribute(FinanceConstants.CUSTOMER_NAME, strCustName.toString());
		orderInvoiceEle.setAttribute(FinanceConstants.CUSTOMER_MOBILE_NO,
				eleOrder.getAttribute(FinanceConstants.CUSTOMER_PHONE_NUMBER));
		orderInvoiceEle.setAttribute("CustomerEmailID", eleOrder.getAttribute(FinanceConstants.CUSTOMER_EMAIL_ID));
		logger.endTimer("setSalesOrderValues");
	}

	/**
	 * This method is used to fetch the line tax amount based on the tax
	 * percentage
	 * @param strLineTotal
	 * @param strTaxPerc
	 * @param strTotalTaxPect [added as part of GST]
	 * @return
	 */
	public static String getLineTax(String strLineTotal, String strTaxPerc,String strTotalTaxPect) {
		logger.beginTimer("getLineTax");
		if (logger.isVerboseEnabled()) {
			logger.verbose("LineTotal=" + strLineTotal);
			logger.verbose("TaxPercentage=" + strTaxPerc);
			logger.verbose("Total TaxPercentage=" + strTotalTaxPect);
		}
		double dblLineTotal = Double.parseDouble(strLineTotal);
		double dblTaxPerc = Double.parseDouble(strTaxPerc);
		double dblTotalTaxPerc = Double.parseDouble(strTotalTaxPect);
		DecimalFormat f = new DecimalFormat("##.00");
		double dblTaxAmount = (dblLineTotal / (100 + dblTotalTaxPerc)) * dblTaxPerc;
		String strTaxAmount = String.valueOf(f.format(dblTaxAmount));
		if (logger.isVerboseEnabled()) {
			logger.verbose("TaxAmount=" + strTaxAmount);
		}
		logger.endTimer("getLineTax");
		return strTaxAmount;
	}

	/**
	 * This method is used to fetch the airway bill number for an invoice
	 * 
	 * @param env
	 * @param strShipmentNo
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public static String getAirwayBillNo(YFSEnvironment env, String strShipmentNo)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("getAirwayBillNo");
		if (logger.isVerboseEnabled()) {
			logger.verbose("ShipmentNo=" + strShipmentNo);
		}
		Document inDoc = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
		Element eleRootIn = inDoc.getDocumentElement();
		eleRootIn.setAttribute(FinanceConstants.SHIPMENT_NO, strShipmentNo);
		Document tempDoc = XMLUtil.getDocument("<Shipments><Shipment AirwayBillNo='' /></Shipments>");
		Document outDoc = XMLUtil.invokeAPI(env, FinanceConstants.GET_SHIPMENT_LIST, inDoc, tempDoc);
		Element eleRootOut = outDoc.getDocumentElement();
		if (logger.isVerboseEnabled()) {
			logger.verbose("output of get shipment list" + XMLUtil.getXmlString(eleRootOut));
		}
		Element eleShmnt = (Element) eleRootOut.getElementsByTagName(FinanceConstants.SHIPMENT).item(0);
		String strAWBNo = eleShmnt.getAttribute(FinanceConstants.AWB_NO);
		logger.endTimer("getAirwayBillNo");
		return strAWBNo;
	}

	/**
	 * This method is used to fetch the current system time and date for
	 * time-stamp
	 * 
	 * @return
	 * @throws Exception
	 */
	public static String getCreateTimeStamp() throws Exception {
		logger.beginTimer("getCreateTimeStamp");
		String strTS = XMLUtil.getSystemTime("yyyy-MM-dd'T'HH:mm:ssZ");
		String split1 = strTS.substring(0, 22);
		String split2 = strTS.substring(22);
		StringBuilder sbCreateTs = new StringBuilder();
		sbCreateTs.append(split1);
		sbCreateTs.append(":");
		sbCreateTs.append(split2);
		logger.endTimer("getCreateTimeStamp");
		return sbCreateTs.toString();
	}

	/**
	 * This method is used to populate the header level data for order and
	 * invoice snapshot
	 * 
	 * @param eleRootIn
	 * @param eleRootOut
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public static void setRootLevelAttributes(Element eleRootIn, Element eleRootOut)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("setRootLevelAttributes");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input element for setRootLevelAttributes" + XMLUtil.getXmlString(eleRootIn));
			logger.verbose("input element for setRootLevelAttributes" + XMLUtil.getXmlString(eleRootOut));
		}
		eleRootOut.setAttribute(FinanceConstants.ORDER_NO, eleRootIn.getAttribute(FinanceConstants.ORDER_NO));
		eleRootOut.setAttribute(FinanceConstants.ORDER_DATE, eleRootIn.getAttribute(FinanceConstants.ORDER_DATE));
		StringBuffer strCustName = new StringBuffer();
		strCustName.append(eleRootIn.getAttribute(FinanceConstants.CUSTOMER_FIRST_NAME));
		strCustName.append(FinanceConstants.SPACE);
		strCustName.append(eleRootIn.getAttribute(FinanceConstants.CUSTOMER_LAST_NAME));
		eleRootOut.setAttribute(FinanceConstants.CUSTOMER_NAME, strCustName.toString());
		eleRootOut.setAttribute(FinanceConstants.CUSTOMER_MOBILE_NO,
				eleRootIn.getAttribute(FinanceConstants.CUSTOMER_PHONE_NUMBER));
		eleRootOut.setAttribute(FinanceConstants.CUSTOMER_EMAIL_ID, eleRootIn.getAttribute(FinanceConstants.CUSTOMER_EMAIL_ID));
		//B2B change for CustomerPONumber and CustomerPODate
		eleRootOut.setAttribute(FinanceConstants.CUSTOMER_PO_NUMBER, eleRootIn.getAttribute(FinanceConstants.A_CUST_PO_NO));
		eleRootOut.setAttribute(FinanceConstants.CUSTOMER_PO_DATE, eleRootIn.getAttribute(FinanceConstants.A_CUST_PO_DATE));
		//B2B change End
		//JIRA ABOF-12386:B2BStoreId start
		String strBuyerRecNodeId = eleRootIn.getAttribute(FinanceConstants.A_BUYER_REC_NODE_ID);
		if(!YFCObject.isVoid(strBuyerRecNodeId) && 
				FinanceConstants.B2B.equalsIgnoreCase(eleRootIn.getAttribute(FinanceConstants.ORDER_TYPE)))
		{
			eleRootOut.setAttribute(FinanceConstants.B2B_STORE_ID,strBuyerRecNodeId);
		}
		//JIRA ABOF-12386:B2BStoreId end

		logger.endTimer("setRootLevelAttributes");
	}

	/**
	 * This method is used to set the document type for sales, return and
	 * exchange order
	 * 
	 * @param strDocumentType
	 * @param eleRootOut
	 * @param strOrderType
	 */
	public static void setDocumentType(String strDocumentType, Element eleRootOut, String strOrderType) {
		logger.beginTimer("setDocumentType");
		if (logger.isVerboseEnabled()) {
			logger.verbose("document type string=" + strDocumentType);
		}
		if ("Return-Exchange".equalsIgnoreCase(strOrderType)
				&& FinanceConstants.DOC_TYPE_SALES_ORDER.equalsIgnoreCase(strDocumentType)) {
			eleRootOut.setAttribute(FinanceConstants.DOCUMENT_TYPE, FinanceConstants.EO);
			eleRootOut.setAttribute(FinanceConstants.ORDER_TYPE, "Return-Exchange");
		}
		//B2B Start:
		else if (FinanceConstants.DOC_TYPE_SALES_ORDER.equalsIgnoreCase(strDocumentType)
				&& "B2B".equalsIgnoreCase(strOrderType)){
			eleRootOut.setAttribute(FinanceConstants.DOCUMENT_TYPE, FinanceConstants.SO);
			eleRootOut.setAttribute(FinanceConstants.ORDER_TYPE, "B2B");
		}
		//B2B End
		else if (FinanceConstants.DOC_TYPE_SALES_ORDER.equalsIgnoreCase(strDocumentType)){
			eleRootOut.setAttribute(FinanceConstants.DOCUMENT_TYPE, FinanceConstants.SO);
			eleRootOut.setAttribute(FinanceConstants.ORDER_TYPE, "SalesOrder");
		} else {
			eleRootOut.setAttribute(FinanceConstants.DOCUMENT_TYPE, FinanceConstants.RO);
			eleRootOut.setAttribute(FinanceConstants.ORDER_TYPE, strOrderType);
		}
		logger.endTimer("setDocumentType");
	}

	/**
	 * This method is used to get the primary key required for the financial
	 * tables. It uses system time in nano seconds and last five characters of
	 * the suffix provided to get the primary key
	 * 
	 * @param suffix
	 * @return
	 */
	public static String getPrimaryKey(String suffix) {
		logger.beginTimer("getPrimaryKey");
		int suffixLen = suffix.length();
		if (suffixLen > 5) {
			suffix = suffix.substring(suffixLen - 5);
		}
		String primaryKey = "" + System.nanoTime() + suffix;
		logger.verbose("Obtained primary key is " + primaryKey);
		logger.endTimer("getPrimaryKey");
		return primaryKey;
	}

	/**
	 * This method returns the billing and shipping name and address of the
	 * customer
	 * 
	 * @param eleInput
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public static String[] strAddress(Element eleInput) throws IllegalArgumentException, Exception {
		logger.beginTimer("strAddress");
		if (logger.isVerboseEnabled()) {
			logger.verbose("Input element for strAddress:" + XMLUtil.getXmlString(eleInput));
		}
		String[] strProfile = new String[6];
		StringBuilder strName = new StringBuilder();
		if (null != eleInput) {
			strName.append(eleInput.getAttribute(FinanceConstants.FIRST_NAME));
			strName.append(FinanceConstants.SPACE);
			strName.append(eleInput.getAttribute(FinanceConstants.LAST_NAME));
			StringBuilder strAddress = new StringBuilder();
			strAddress.append(eleInput.getAttribute(FinanceConstants.ADDRESS_LINE_ONE));
			strAddress.append(FinanceConstants.ADDRESS_SPACE);
			strAddress.append(eleInput.getAttribute(FinanceConstants.ADDRESS_LINE_TWO));
			strAddress.append(FinanceConstants.ADDRESS_SPACE);
			strAddress.append(eleInput.getAttribute(FinanceConstants.ADDRESS_LINE_THREE));
			strAddress.append(FinanceConstants.ADDRESS_SPACE);
			strAddress.append(eleInput.getAttribute(FinanceConstants.CITY));
			strAddress.append(FinanceConstants.ADDRESS_SPACE);
			strAddress.append(eleInput.getAttribute(FinanceConstants.STATE));
			strAddress.append(FinanceConstants.ADDRESS_SPACE);
			strAddress.append(eleInput.getAttribute(FinanceConstants.COUNTRY));
			strAddress.append(FinanceConstants.ADDRESS_SPACE);
			strAddress.append(eleInput.getAttribute(FinanceConstants.ZIP_CODE));

			strProfile[0] = strName.toString();
			strProfile[1] = strAddress.toString();

			strProfile[2] = eleInput.getAttribute(FinanceConstants.CITY);
			strProfile[3] = eleInput.getAttribute(FinanceConstants.STATE);
			strProfile[4] = eleInput.getAttribute(FinanceConstants.COUNTRY);
			strProfile[5] = eleInput.getAttribute(FinanceConstants.ZIP_CODE);
		} else {
			strProfile[0] = FinanceConstants.BLANK;
			strProfile[1] = FinanceConstants.BLANK;
			strProfile[2] = FinanceConstants.BLANK;
			strProfile[3] = FinanceConstants.BLANK;
			strProfile[4] = FinanceConstants.BLANK;
			strProfile[5] = FinanceConstants.BLANK;
		}
		logger.endTimer("strAddress");
		return strProfile;
	}

	/**
	 * This method returns the list of all payment types that are prepaid
	 * 
	 * @param env
	 * @return
	 * @throws Exception
	 */
	public static ArrayList<String> getCommonCodeForPrepaid(YFSEnvironment env) throws Exception {
		logger.beginTimer("getCommonCodeForPrepaid");
		ArrayList<String> arrPaymentType = new ArrayList<String>();
		Document docGetCCLInput = XMLUtil.createDocument(FinanceConstants.COMMON_CODE);
		Element eleCCLInput = docGetCCLInput.getDocumentElement();
		eleCCLInput.setAttribute(FinanceConstants.CALLING_ORGANIZATION_CODE, FinanceConstants.ENTERPRISE_CODE_VALUE);
		eleCCLInput.setAttribute(FinanceConstants.CODE_TYPE, FinanceConstants.PAYMENT_CODE_TYPE);
		Document docGetCCLOutput = XMLUtil.invokeAPI(env, FinanceConstants.GET_COMMON_CODE_LIST_API, docGetCCLInput);
		if (logger.isVerboseEnabled()) {
			logger.verbose("get common code list for getCommonCodeForPrepaid"
					+ XMLUtil.getXmlString(docGetCCLOutput.getDocumentElement()));
		}
		Element eleRootCCLOut = docGetCCLOutput.getDocumentElement();
		NodeList nlCommonCode = eleRootCCLOut.getElementsByTagName(FinanceConstants.COMMON_CODE);
		for (int intCommCode = 0; intCommCode < nlCommonCode.getLength(); intCommCode++) {
			Element eleCommCode = (Element) nlCommonCode.item(intCommCode);
			arrPaymentType.add(eleCommCode.getAttribute(FinanceConstants.CODE_VALUE));
		}
		logger.endTimer("getCommonCodeForPrepaid");
		return arrPaymentType;
	}

	/**
	 * This method is used to set payment details in the document
	 * 
	 * @param elePaymentMethod
	 * @param eleRootIn
	 * @param outDoc
	 * @param eleInvoicePmnt
	 * @throws DOMException
	 * @throws TransformerException
	 */
	public static void setInvoicePayments(Element elePaymentMethod, Element eleRootIn, Document outDoc,
			Element eleInvoicePmnt) throws DOMException, TransformerException {
		logger.beginTimer("setInvoicePayments");
		String paymentType = elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_TYPE);
		eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_TYPE, paymentType);
		eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_GATEWAY,
				elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SIX));
		eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_REFERENCE,
				elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SEVEN));
		double dblAmtRec = getDoubleFromString(elePaymentMethod.getAttribute(FinanceConstants.TOTAL_CHARGED))
				- getDoubleFromString(elePaymentMethod.getAttribute(FinanceConstants.TOTAL_REFUNDED_AMOUNT));
		eleInvoicePmnt.setAttribute(FinanceConstants.AMOUNT_RECEIVED, String.valueOf(dblAmtRec));
		eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
		String strPmntTender = FinanceConstants.BLANK;
		if (null != elePaymentMethod.getAttribute("CreditCardType")) {
			strPmntTender = elePaymentMethod.getAttribute("CreditCardType");
		}
		eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_TENDER, strPmntTender);
		logger.endTimer("setInvoicePayments");
	}

	/**
	 * This method is used to return the double value for a string
	 * 
	 * @param strInput
	 * @return
	 */
	public static double getDoubleFromString(String strInput) {
		logger.beginTimer("getDoubleFromString");
		if (strInput == null || strInput.isEmpty() || "".equalsIgnoreCase(strInput)) {
			return 0.00;
		} else {
			return Double.parseDouble(strInput);
		}
	}

	/**
	 * This method is used to set header level charges in the document
	 * 
	 * @param eleHeaderChargeIn
	 * @param eleHeaderChargeOut
	 */
	public static void setHeaderCharges(Element eleHeaderChargeIn, Element eleHeaderChargeOut) {
		logger.beginTimer("setHeaderCharges");
		eleHeaderChargeOut.setAttribute(FinanceConstants.CHARGE_NAME,
				eleHeaderChargeIn.getAttribute(FinanceConstants.CHARGE_NAME));
		eleHeaderChargeOut.setAttribute(FinanceConstants.CHARGE_AMOUNT,
				eleHeaderChargeIn.getAttribute(FinanceConstants.CHARGE_AMOUNT));
		eleHeaderChargeOut.setAttribute(FinanceConstants.TAX_TYPE, "find where this value is present in the output");
		eleHeaderChargeOut.setAttribute(FinanceConstants.TAX_RATE, "find where this value is present in the output");
		eleHeaderChargeOut.setAttribute(FinanceConstants.TAX_AMOUNT, "find where this value is present in the output");
		logger.endTimer("setHeaderCharges");
	}

	/**
	 * This method fetches all the Sales Order Invoices associated to list of
	 * order line key
	 * 
	 * @param env
	 * @param derivedFromOrderLineKeyList
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public static Document getSalesInvoiceListForOrderLines(YFSEnvironment env, Set<String> derivedFromOrderLineKeyList)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("getSalesInvoiceListForOrderLines");
		Document getSalesInvoiceListInput = XMLUtil.getDocument(FinanceConstants.GET_INVOICE_LIST_FROM_OL_INPUT);
		Element getSalesInvoiceListInputEle = getSalesInvoiceListInput.getDocumentElement();
		Node orEle = XPathAPI.selectSingleNode(getSalesInvoiceListInputEle,
				"//InvoiceHeader/LineDetails/LineDetail/ComplexQuery/And/Or");
		for (String derivedFromOLKey : derivedFromOrderLineKeyList) {
			Element expEle = getSalesInvoiceListInput.createElement("Exp");
			expEle.setAttribute("Name", FinanceConstants.ORDER_LINE_KEY);
			expEle.setAttribute("QryType", "EQ");
			expEle.setAttribute("Value", derivedFromOLKey);
			orEle.appendChild(expEle);
		}
		Document getSalesInvoiceListTemp = XMLUtil.getDocument(FinanceConstants.GET_INVOICE_LIST_FROM_OL_TEMPLATE);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Going to call getOrderInvoiceDetailsList with input\n"
					+ XMLUtil.getXmlString(getSalesInvoiceListInputEle) + " and template\n"
					+ XMLUtil.getXmlString(getSalesInvoiceListTemp.getDocumentElement()));
		}
		Document orderInvoiceDtlsList = XMLUtil.invokeAPI(env, FinanceConstants.GET_ORDER_INVOICE_DTLS_LIST_API,
				getSalesInvoiceListInput, getSalesInvoiceListTemp);
		if (logger.isVerboseEnabled()) {
			logger.verbose("ouput of getOrderInvoiceDetailsList is\n"
					+ XMLUtil.getXmlString(orderInvoiceDtlsList.getDocumentElement()));
		}
		logger.endTimer("getSalesInvoiceListForOrderLines");
		return getSalesInvoiceListTemp;
	}

	/**
	 * This method publishes the inventory update for given serialNo as part of
	 * GRN process/ Inventory movement and Return to Vendor
	 * 
	 * @param env
	 * @param serialNoList
	 * @param positiveInvChange
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public static List<Document> publishInventoryUpdateForSerialNos(YFSEnvironment env, Set<String> serialNoList,
			int invMovementValue) throws IllegalArgumentException, Exception {
		logger.beginTimer("publishInventoryUpdateForSerialNos");
		List<Document> ouputDocumentList = new ArrayList<Document>();
		Map<String, Integer> orderLineKeyQtyMap = new HashMap<String, Integer>(); // Map
		// that
		// stores
		// total no. of qty
		// movement
		// belonging to each
		// PO order line
		// on ReceiptLine
		Map<String, List<Element>> receiptHeaderKeyReceiptLinesMap = new HashMap<String, List<Element>>(); // Map
		// that
		// stores
		// ReceiptHeaderKey (PO header)
		// and list of all ReceiptLines
		// associated to that

		Map<String, String> receiptLinekeyOrderLineKeyMap = new HashMap<String, String>();

		Document poReceiptLineList = ABOFFinanceUtils.getReceiptLineListForSerials(env, serialNoList);
		List<Element> poReceiptLineEleList = XMLUtil.getElementsByXpath(poReceiptLineList,
				"//ReceiptLineList/ReceiptLine");
		// This method iterates through each ReceiptLine of the PO and does
		// 1. if this Receipt line is already fetched
		for (int i = 0; i < poReceiptLineEleList.size(); i++) {
			Element poReceiptLineEle = poReceiptLineEleList.get(i);
			String receiptLineKey = poReceiptLineEle.getAttribute(FinanceConstants.RECEIPT_LINE_KEY);
			Element orderLineEle = (Element) poReceiptLineEle.getElementsByTagName(FinanceConstants.ORDER_LINE).item(0);
			NodeList shipmentLineNL = poReceiptLineEle.getElementsByTagName(FinanceConstants.SHIPMENT_LINE);
			if (YFCCommon.isVoid(shipmentLineNL) || shipmentLineNL.getLength() == 0) {
				logger.verbose("ReceiptElement with receipt line key =" + receiptLineKey
						+ " is receipt of return. Hence skipping this. As it is not associated to any PO");
				String sRetRecSer = poReceiptLineEle.getAttribute("SerialNo");
				String strItem = poReceiptLineEle.getAttribute("ItemID");

				Element eleLoopReturnReceiptLine = poReceiptLineEle;
				boolean receiptFound = false;
				while(!receiptFound){		
					Element eleOrderLine = (Element) eleLoopReturnReceiptLine.getElementsByTagName("OrderLine").item(0);
					String sDerivedFromOrderHeaderKey = eleOrderLine.getAttribute("DerivedFromOrderHeaderKey");
					String sDerivedFromOrderLineKey = eleOrderLine.getAttribute("DerivedFromOrderLineKey");
					eleLoopReturnReceiptLine = null;

					YFCDocument inShipListOrder = YFCDocument.createDocument("Order");
					YFCElement eleOrder = inShipListOrder.getDocumentElement();
					eleOrder.setAttribute("OrderHeaderKey",sDerivedFromOrderHeaderKey);
					YFCDocument shipListTemp = YFCDocument.getDocumentFor("<Shipments><Shipment><Containers><Container><ContainerDetails>"
							+ "<ContainerDetail><ShipmentTagSerials/><ShipmentLine/></ContainerDetail></ContainerDetails>"
							+ "</Container></Containers></Shipment></Shipments>");
					Document outShipListOrder = XMLUtil.invokeAPI(env, "getShipmentListForOrder",inShipListOrder.getDocument(),shipListTemp.getDocument());
					Element eleOutShip =  (Element) outShipListOrder.getDocumentElement().getElementsByTagName("Shipment").item(0);
					NodeList nlContDet = eleOutShip.getElementsByTagName("ContainerDetail");

					for(int iContDet=0; iContDet <nlContDet.getLength();iContDet++){

						Element eleContDet = (Element) nlContDet.item(iContDet);
						Element eleShipLine = (Element) eleContDet.getElementsByTagName("ShipmentLine").item(0);
						if(!eleShipLine.getAttribute("OrderLineKey").equalsIgnoreCase(sDerivedFromOrderLineKey)){
							continue;
						}

						String sShipLineQty = eleShipLine.getAttribute("Quantity");
						Double dShipLineQty = Double.valueOf(sShipLineQty);
						if(dShipLineQty > 1){
							HashMap<String,Element> hmSerRecLine = new HashMap<String,Element>();
							NodeList nlShipTagSer = eleContDet.getElementsByTagName("ShipmentTagSerial");
							for(int iShipTagSer=0; iShipTagSer <nlShipTagSer.getLength();iShipTagSer++){

								Element eleShipTagSer = (Element) nlShipTagSer.item(iShipTagSer);

								if(sRetRecSer.equalsIgnoreCase(eleShipTagSer.getAttribute("SerialNo"))){
									receiptFound = true;
									break;
								}

								HashMap<String, HashSet<String>> hmItemSerials = new HashMap<String, HashSet<String>>();
								HashSet<String> hsSerials = new HashSet<String>();
								hsSerials.add(eleShipTagSer.getAttribute("SerialNo"));
								hmItemSerials.put(strItem, hsSerials);
								Element eleOutLoopRecLineList = getRecLineList(env,hmItemSerials, strItem);


								if(!YFCObject.isVoid(eleOutLoopRecLineList.getElementsByTagName("ReceiptLine").item(0))){
									NodeList nlRecLines = eleOutLoopRecLineList.getElementsByTagName("ReceiptLine");
									for(int iRecLine=0; iRecLine <nlRecLines.getLength();iRecLine++){
										Element eleRecLine = (Element) nlRecLines.item(iRecLine);
										if(!hmSerRecLine.containsKey(eleRecLine.getAttribute("SerialNo"))){
											hmSerRecLine.put(eleRecLine.getAttribute("SerialNo"),
													eleRecLine);
										}else{											
											Element eleRec = (Element) eleRecLine.getElementsByTagName("Receipt").item(0);
											if("0005".equalsIgnoreCase(eleRec.getAttribute("DocumentType"))){
												hmSerRecLine.put(eleRecLine.getAttribute("SerialNo"),eleRecLine);
											}
										}
									}
								}else{
									receiptFound =true;
								}
							}

							if(receiptFound){
								break;
							}

							if(hmSerRecLine.isEmpty()){
								receiptFound = true;
							}else if(dShipLineQty != hmSerRecLine.size()){
								receiptFound = true;
							}else{
								String sRecHdrKey = "";
								boolean allSame = false;
								Element eleFinalRecLine = null; 
								Iterator<String> itHmSerRecLine = hmSerRecLine.keySet().iterator();
								while(itHmSerRecLine.hasNext()){
									String sSerial = itHmSerRecLine.next();
									Element eleMapRecLine = hmSerRecLine.get(sSerial);
									Element eleMapRec = (Element) eleMapRecLine.getElementsByTagName("Receipt").item(0);
									if("".equalsIgnoreCase(sRecHdrKey)){
										sRecHdrKey = eleMapRec.getAttribute("ReceiptHeaderKey");
									}else if(sRecHdrKey.equalsIgnoreCase(eleMapRec.getAttribute("ReceiptHeaderKey"))){
										allSame = true;
										eleFinalRecLine = eleMapRecLine;
									}else if(!sRecHdrKey.equalsIgnoreCase(eleMapRec.getAttribute("ReceiptHeaderKey"))){
										allSame = false;
										break;
									}
								}
								if(allSame){
									Element eleRec = (Element) eleFinalRecLine.getElementsByTagName("Receipt").item(0);
									if("0005".equalsIgnoreCase(eleRec.getAttribute("DocumentType"))){
										poReceiptLineEle.appendChild(eleFinalRecLine.getElementsByTagName("ShipmentLine").item(0));
									}else{
										eleLoopReturnReceiptLine = eleFinalRecLine;
										break;
									}				    
								}
								receiptFound = true;
							}

						}else if(dShipLineQty == 1){
							Element eleShipTagSer = (Element) eleContDet.getElementsByTagName("ShipmentTagSerial").item(0);

							if(sRetRecSer.equalsIgnoreCase(eleShipTagSer.getAttribute("SerialNo"))){
								receiptFound = true;
								break;
							}

							HashMap<String, HashSet<String>> hmItemSerials = new HashMap<String, HashSet<String>>();
							HashSet<String> hsSerials = new HashSet<String>();
							hsSerials.add(eleShipTagSer.getAttribute("SerialNo"));
							hmItemSerials.put(strItem, hsSerials);
							Element eleOutLoopRecLineList = getRecLineList(env,hmItemSerials, strItem);

							if(!YFCObject.isVoid(eleOutLoopRecLineList.getElementsByTagName("ReceiptLine").item(0))){
								NodeList nlRecLines = eleOutLoopRecLineList.getElementsByTagName("ReceiptLine");
								for(int iRecLine=0; iRecLine <nlRecLines.getLength();iRecLine++){
									Element eleRecLine = (Element) nlRecLines.item(iRecLine);
									Element eleRec = (Element) eleRecLine.getElementsByTagName("Receipt").item(0);
									if("0005".equalsIgnoreCase(eleRec.getAttribute("DocumentType"))){
										poReceiptLineEle.appendChild(eleRecLine.getElementsByTagName("ShipmentLine").item(0));
										receiptFound = true;
										break;
									}else{
										if(null == eleLoopReturnReceiptLine){
											eleLoopReturnReceiptLine = eleRecLine;
										}
										continue;
									}
								}
							}else{
								receiptFound = true;
							}
							break;
						}			
					}
				}
			}
			String sQty = poReceiptLineEle.getAttribute(FinanceConstants.QUANTITY);
			double dQty = 0.00;
			if (!YFCCommon.isVoid(sQty)) {
				dQty = Double.valueOf(sQty);
			}
			String orderLineKey = orderLineEle.getAttribute(FinanceConstants.ORDER_LINE_KEY);
			logger.verbose("orderLineKey=" + orderLineKey + " Quantity=" + dQty);

			if (YFCCommon.isVoid(orderLineKey) || dQty <= 0.00) {
				logger.verbose("orderLineKey=" + orderLineKey + " Quantity=" + dQty
						+ " As either OrderLineKey is blank or Quantity is zero, this ReceiptLine will not be considered");
				continue;
			}
			String receiptHeaderKey = poReceiptLineEle.getAttribute(FinanceConstants.RECEIPT_HEADER_KEY);
			// OrderLine is already covered as earlier ReceiptLine of iteration.
			// No need to do anything except the addition of quantity for
			// GRN Lines
			if (logger.isVerboseEnabled()) {
				logger.verbose("Iterating with OrderLineKey:" + orderLineKey + ": ReceiptLineKey:" + receiptLineKey
						+ ": ReceiptHeaderKey:" + receiptHeaderKey);
				logger.verbose("PO Receipt Line Element is " + XMLUtil.getXmlString(poReceiptLineEle));
			}
			if (orderLineKeyQtyMap.containsKey(orderLineKey)) {
				int currentOrderLineQty = orderLineKeyQtyMap.get(orderLineKey);
				logger.verbose("stored quantity for OrderLineKey=" + orderLineKey + ", currentOrderLineQty="
						+ currentOrderLineQty);
				currentOrderLineQty++;
				orderLineKeyQtyMap.put(orderLineKey, currentOrderLineQty);
			} else {
				receiptLinekeyOrderLineKeyMap.put(receiptLineKey, orderLineKey);

				// Add a new qty=1 for corresponding receipt line as new entry
				orderLineKeyQtyMap.put(orderLineKey, 1);
				// Add this ReceiptLine as part of one of ReceiptHeaderKey
				List<Element> receiptLineEleList = receiptHeaderKeyReceiptLinesMap.get(receiptHeaderKey);
				if (YFCCommon.isVoid(receiptLineEleList)) {
					receiptLineEleList = new ArrayList<Element>();
				}
				receiptLineEleList.add(poReceiptLineEle);

				receiptHeaderKeyReceiptLinesMap.put(receiptHeaderKey, receiptLineEleList);
			}
		}
		Iterator<Entry<String, List<Element>>> entries = receiptHeaderKeyReceiptLinesMap.entrySet().iterator();
		while (entries.hasNext()) {
			Entry<String, List<Element>> thisEntry = entries.next();
			String receiptHeaderKey = thisEntry.getKey();
			List<Element> receiptLineList = thisEntry.getValue();
			Document inventoryUpdateDoc = getInventoryUpdateDocumentFromReceiptDtls(env, receiptHeaderKey,
					receiptLineList, orderLineKeyQtyMap, receiptLinekeyOrderLineKeyMap, invMovementValue);
			if (logger.isVerboseEnabled()) {
				logger.verbose(
						"GRNInventory update doc is\n" + XMLUtil.getXmlString(inventoryUpdateDoc.getDocumentElement()));
			}
			ouputDocumentList.add(inventoryUpdateDoc);
		}
		logger.endTimer("publishInventoryUpdateForSerialNos");
		return ouputDocumentList;
	}


	private static Element getRecLineList(
			YFSEnvironment env, HashMap<String, HashSet<String>> hmItemSerials, String strItem) throws Exception {
		YFCDocument docRecLineListInp =  YFCDocument.createDocument("ReceiptLine");
		YFCElement eleRecLineListInp = docRecLineListInp.getDocumentElement();
		eleRecLineListInp.setAttribute("Quantity", "1");
		eleRecLineListInp.setAttribute(XmlConstants.A_ITEM_ID, strItem);
		YFCElement eleOrderBy = eleRecLineListInp.createChild("OrderBy");
		YFCElement eleAttribute = eleOrderBy.createChild("Attribute");
		eleAttribute.setAttribute("Name", "ReceiptLineKey");
		eleAttribute.setAttribute("Desc", "Y");
		YFCElement eleComplex = eleRecLineListInp.createChild("ComplexQuery");
		eleComplex.setAttribute("Operator", "AND");
		YFCElement eleAnd = eleComplex.createChild("And");
		YFCElement eleOr = eleAnd.createChild("Or");
		HashSet<String> setSer = hmItemSerials.get(strItem);
		Iterator<String> itSer = setSer.iterator();
		while(itSer.hasNext()){
			YFCElement eleOrExp = eleOr.createChild("Exp");
			eleOrExp.setAttribute(XmlConstants.A_NAME, XmlConstants.A_SERIAL_NO);
			eleOrExp.setAttribute(XmlConstants.A_VALUE, itSer.next());
		}
		Document docRecLineListOut = XMLUtil.invokeService(env,"ABOFgetReceiptLineList",docRecLineListInp.getDocument());
		return docRecLineListOut.getDocumentElement();
	}
	/**
	 * This method prepares GRNHeader document as an output using
	 * ReceiptHeaderKey and ReceiptLine associated to that
	 * 
	 * @param env
	 * @param receiptHeaderKey
	 * @param receiptLineList
	 * @param orderLinekeyReceiptLineKeyMap
	 * @param receiptLineQtyMap
	 * @param invMovementValue
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 *             added IsModified = 'Y',IsRTV='Y'
	 */
	private static Document getInventoryUpdateDocumentFromReceiptDtls(YFSEnvironment env, String receiptHeaderKey,
			List<Element> receiptLineList, Map<String, Integer> orderLineKeyQtyMap,
			Map<String, String> receiptLinekeyOrderLineKeyMap, int invMovementValue)
					throws IllegalArgumentException, Exception {
		logger.beginTimer("getInventoryUpdateDocumentFromReceiptDtls");

		Document docGRNHeader = XMLUtil.createDocument(FinanceConstants.GRN_HEADER);
		Element receiptLineEle = receiptLineList.get(0);
		Element orderLineEle = (Element) XPathAPI.selectSingleNode(receiptLineEle, FinanceConstants.ORDER_LINE);
		populateGRNHeaderDetails(env, docGRNHeader, receiptHeaderKey,
				orderLineEle.getAttribute(FinanceConstants.ORDER_LINE_KEY), invMovementValue);
		String voucharNo = docGRNHeader.getDocumentElement().getAttribute(FinanceConstants.VOUCHER_NO);
		Element grnLines = docGRNHeader.createElement(FinanceConstants.GRN_LINES);
		docGRNHeader.getDocumentElement().appendChild(grnLines);
		/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - Start*/
		populateGRNLines(env, receiptLineList, orderLineKeyQtyMap, grnLines, voucharNo, receiptLinekeyOrderLineKeyMap,
				invMovementValue,receiptHeaderKey);
		/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - End*/
		logger.endTimer("getInventoryUpdateDocumentFromReceiptDtls");
		return docGRNHeader;
	}

	/**
	 * This method populate GRNLines using receipt lines This method also
	 * populate GRNLines according to inventory movement. i.e. if it is Return
	 * to Vendor (invMovementValue=0), there is negative inventory changes from
	 * Inventory Status = GOOD if it is Moving out of Quarantine
	 * (invMovementValue=1), there is positive inventory changes from Inventory
	 * Status = GOOD and negative from "QUARANTINE" if it is Moving to
	 * Quarantine (invMovementValue=2), there is negative inventory changes from
	 * Inventory Status = GOOD and positive from "QUARANTINE"
	 * 
	 * @param env
	 * @param receiptLineList
	 * @param itemIDTotalQtyMap
	 * @param grnLines
	 * @param voucharNo
	 * @param orderLinekeyReceiptLineKeyMap
	 * @param invMovementValue
	 * @param receiptHeaderKey 
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private static void populateGRNLines(YFSEnvironment env, List<Element> receiptLineList,
			Map<String, Integer> orderLineKeyQtyMap, Element grnLines, String voucharNo,
			Map<String, String> receiptLinekeyOrderLineKeyMap, int invMovementValue, String receiptHeaderKey)
					throws IllegalArgumentException, Exception {
		logger.beginTimer("populateGRNLines");
		Element receiptLineEle = null;
		Map<String, Element> receiptLineKeyGRNLineMap = new HashMap<String, Element>();
		for (int i = 0; i < receiptLineList.size(); i++) {
			receiptLineEle = receiptLineList.get(i);
			if (logger.isVerboseEnabled()) {
				logger.verbose("Iterating through ReceiptLine" + XMLUtil.getXmlString(receiptLineEle));
			}
			String receiptLineKey = receiptLineEle.getAttribute(FinanceConstants.RECEIPT_LINE_KEY);
			/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - Start*/
			Element grnLine = populateGRNLineDetails(receiptLineEle, grnLines, voucharNo, invMovementValue,receiptHeaderKey,env);
			/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - End*/
			receiptLineKeyGRNLineMap.put(receiptLineKey, grnLine);
		}

		Iterator<Entry<String, Element>> entries = receiptLineKeyGRNLineMap.entrySet().iterator();
		while (entries.hasNext()) {
			Entry<String, Element> thisEntry = entries.next();
			String receiptLineKey = thisEntry.getKey();
			String orderlinekey = receiptLinekeyOrderLineKeyMap.get(receiptLineKey);
			Element grnLine = thisEntry.getValue();
			int qty = orderLineKeyQtyMap.get(orderlinekey);

			Element elordLine = (Element) receiptLineEle.getElementsByTagName(FinanceConstants.ORDER_LINE).item(0);
			String strUnitPrice = XPathAPI.selectSingleNode(elordLine, "LinePriceInfo/@UnitPrice").getNodeValue();
			Element strLineTax = (Element) elordLine.getElementsByTagName(FinanceConstants.LINE_TAX).item(0);

			double strTaxAmt = 0.0;
			String strTaxPercentage = "";

			if (!(null == strLineTax)) {
				strTaxPercentage = strLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE);
				strTaxAmt = (qty * Double.parseDouble(strUnitPrice) * Double.parseDouble(strTaxPercentage)) / 100;
			}

			grnLine.setAttribute(FinanceConstants.TAX_AMOUNT, "" + strTaxAmt);

			grnLine.setAttribute(FinanceConstants.RATE, strUnitPrice);
			Element eleItem = (Element) elordLine.getElementsByTagName(FinanceConstants.ITEM).item(0);
			grnLine.setAttribute(FinanceConstants.MRP, eleItem.getAttribute(FinanceConstants.UNIT_COST));

			String strRecewiptLineKey = receiptLineEle.getAttribute(FinanceConstants.RECEIPT_LINE_NO);

			if (invMovementValue == 0) {
				// This is RTV
				grnLine.setAttribute(FinanceConstants.QUANTITY, "" + (-qty));
				grnLine.setAttribute(FinanceConstants.INVENTORY_STATUS, FinanceConstants.GOOD);
				grnLine.setAttribute(FinanceConstants.GRN_LINE_KEY,
						ABOFFinanceUtils.getPrimaryKey(strRecewiptLineKey + 00));
			} else if (invMovementValue > 0) {
				Element grnLineDuplicate = (Element) grnLine.cloneNode(true);
				grnLineDuplicate.setAttribute(FinanceConstants.TAX_AMOUNT, "" + strTaxAmt);
				grnLines.appendChild(grnLineDuplicate);
				grnLine.setAttribute(FinanceConstants.INVENTORY_STATUS, FinanceConstants.GOOD);
				grnLineDuplicate.setAttribute(FinanceConstants.INVENTORY_STATUS, "QUARANTINE");
				grnLine.setAttribute(FinanceConstants.GRN_LINE_KEY,
						ABOFFinanceUtils.getPrimaryKey(strRecewiptLineKey + 01));
				grnLineDuplicate.setAttribute(FinanceConstants.GRN_LINE_KEY,
						ABOFFinanceUtils.getPrimaryKey(strRecewiptLineKey + 02));
				if (invMovementValue == 1) {
					grnLine.setAttribute(FinanceConstants.QUANTITY, "" + qty);
					grnLineDuplicate.setAttribute(FinanceConstants.QUANTITY, "" + (-qty));
				} else {
					grnLine.setAttribute(FinanceConstants.QUANTITY, "" + (-qty));
					grnLineDuplicate.setAttribute(FinanceConstants.QUANTITY, "" + qty);
				}
			}else if(invMovementValue < 0){
				if(invMovementValue==-1){ //case RTV Packed Serial is Unpacked
					grnLine.setAttribute(FinanceConstants.QUANTITY, "" + (qty));
					grnLine.setAttribute(FinanceConstants.INVENTORY_STATUS, FinanceConstants.GOOD);
					grnLine.setAttribute(FinanceConstants.GRN_LINE_KEY,
							ABOFFinanceUtils.getPrimaryKey(strRecewiptLineKey + 00));
				}
			}
			if (logger.isVerboseEnabled()) {
				logger.verbose("GRNLines in this iteration " + XMLUtil.getXmlString(grnLines));
			}
		}
		logger.endTimer("populateGRNLines");
	}

	/**
	 * This method goes to the attribute level details of ReceiptLine element
	 * and stamps these attributes on GRN lines
	 * 
	 * @param receiptLineEle
	 * @param grnLines
	 * @param voucharNo
	 * @param receiptHeaderKey 
	 * @param env 
	 * @param receiptLineQtyMap
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private static Element populateGRNLineDetails(Element receiptLineEle, Element grnLines, String voucharNo,
			int invMovementValue, String receiptHeaderKey, YFSEnvironment env) throws IllegalArgumentException, Exception {
		logger.beginTimer("populateGRNLineDetails");
		Document grnHeaderDoc = grnLines.getOwnerDocument();
		Element grnHeaderEle = grnHeaderDoc.getDocumentElement();
		Element grnLine = grnHeaderDoc.createElement(FinanceConstants.GRN_LINE);

		grnLines.appendChild(grnLine);
		Element itemEle = (Element) XPathAPI.selectSingleNode(receiptLineEle, "OrderLine/Item");
		grnLine.setAttribute(FinanceConstants.VOUCHER_NO, voucharNo);
		String strGrnHederKey = grnHeaderEle.getAttribute(FinanceConstants.GRN_HEADER_KEY);
		grnLine.setAttribute(FinanceConstants.GRN_HEADER_KEY, strGrnHederKey);
		grnLine.setAttribute(FinanceConstants.SKU_CODE, itemEle.getAttribute(FinanceConstants.ITEM_ID));
		grnLine.setAttribute(FinanceConstants.UOM, itemEle.getAttribute(FinanceConstants.UNIT_OF_MEASURE));
		grnLine.setAttribute(FinanceConstants.STYLE_CODE, itemEle.getAttribute(FinanceConstants.MANUFACTURER_NAME));
		grnLine.setAttribute(FinanceConstants.EAN_CODE, itemEle.getAttribute(FinanceConstants.ISBN));
		grnLine.setAttribute(FinanceConstants.BRAND_STYLE_CODE,
				itemEle.getAttribute(FinanceConstants.MANUFACTURER_NAME));
		grnLine.setAttribute(FinanceConstants.COLOR_DESC, itemEle.getAttribute(FinanceConstants.ITEM_SHORT_DESC));
		grnLine.setAttribute(FinanceConstants.PRODUCT_DESC, itemEle.getAttribute(FinanceConstants.ITEM_DESC));

		Element elordLine = (Element) receiptLineEle.getElementsByTagName(FinanceConstants.ORDER_LINE).item(0);
		NodeList lineTaxNl = elordLine.getElementsByTagName(FinanceConstants.LINE_TAX);
		if (!YFCCommon.isVoid(lineTaxNl) && lineTaxNl.getLength() > 0) {
			Element strLineTax = (Element) lineTaxNl.item(0);

			grnLine.setAttribute(FinanceConstants.TAX_TYPE, strLineTax.getAttribute(FinanceConstants.REFERENCE_1));
			grnLine.setAttribute(FinanceConstants.TAX_RATE, strLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
		}
		if (invMovementValue == 0 || invMovementValue== -1) {
			grnLine.setAttribute(FinanceConstants.IS_MODIFID, FinanceConstants.YES);
			grnLine.setAttribute(FinanceConstants.IS_RTV, FinanceConstants.YES);

			/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - Start*/
			Document receiptListTemp = XMLUtil.getDocument("<ReceiptList><Receipt ReceiptNo='' ShipmentKey='' ReceiptDate='' ReceivingNode=''></Receipt></ReceiptList>");				
			Document receiptListInput = XMLUtil.createDocument(FinanceConstants.RECEIPT);
			Element elereceiptListInput = receiptListInput.getDocumentElement();
			elereceiptListInput.setAttribute(FinanceConstants.RECEIPT_HEADER_KEY, receiptHeaderKey);

			Document receiptListOut = XMLUtil.invokeAPI(env, FinanceConstants.GET_RECEIPT_LIST_API, receiptListInput,
					receiptListTemp);
			Element receiptEle = XMLUtil.getFirstElementByXPath(receiptListOut, "//ReceiptList/Receipt");

			Document shipListTemp = XMLUtil.getDocument("<Shipments><Shipment ShipmentNo='' ShipmentKey='' PickticketNo=''><Extn/></Shipment></Shipments>");	
			Document shipListInput = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
			Element eleShipListInput = shipListInput.getDocumentElement();
			eleShipListInput.setAttribute(FinanceConstants.SHIPMENT_KEY, receiptEle.getAttribute(FinanceConstants.SHIPMENT_KEY));

			Document shipListOut = XMLUtil.invokeAPI(env, FinanceConstants.GET_SHIPMENT_LIST, shipListInput,
					shipListTemp);
			Element eleShips = shipListOut.getDocumentElement();
			Element eleShip = (Element) eleShips.getElementsByTagName(FinanceConstants.SHIPMENT).item(0);
			Element eleExtn = (Element) eleShip.getElementsByTagName("Extn").item(0);
			grnLine.setAttribute(FinanceConstants.INVOICE_NO, eleShip.getAttribute("PickticketNo"));
			grnLine.setAttribute(FinanceConstants.INVOICE_DATE, eleExtn.getAttribute("ExtnInvoiceDate"));
			logger.verbose("Invoice No and Invoice Date appended to input");
			/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - End*/

			// ABOF-11733 - change Start
			logger.verbose("Logic to fetch EAN Code - Start");
			String sItem=grnLine.getAttribute(FinanceConstants.SKU_CODE);
			String sEAN="";
			if(!YFCObject.isVoid(sItem)){
				logger.verbose("fetching EAN for Item: "+sItem);
				Document itemListTemp = XMLUtil.getDocument("<ItemList><Item ItemID=''><ItemAliasList><ItemAlias AliasName='' AliasValue=''/></ItemAliasList></Item></ItemList>");
				Document getItemListIn = XMLUtil.getDocument("<Item ItemID='"+sItem+"' />");
				Document getItemListOut = XMLUtil.invokeAPI(env, FinanceConstants.GET_ITEM_LIST_API, getItemListIn,itemListTemp);
				Element itemList = getItemListOut.getDocumentElement();
				Element itemAliasList = (Element) itemList.getElementsByTagName("ItemAliasList").item(0);

				if(!YFCObject.isVoid(itemAliasList)){
					NodeList nodeList = itemAliasList.getElementsByTagName("ItemAlias");
					for(int i = 0; i < nodeList.getLength(); i++){
						Element itemAlias = (Element) nodeList.item(i);

						if("EAN".equalsIgnoreCase(itemAlias.getAttribute("AliasName"))){
							sEAN=itemAlias.getAttribute("AliasValue");
							logger.verbose("EAN Code: "+ sEAN);
							break;
						}
					}
				}

			}

			grnLine.setAttribute(FinanceConstants.EAN_CODE, sEAN);
			logger.verbose("Logic to fetch EAN Code - End");
			//ABOF-11733 - change End


		} else {
			grnLine.setAttribute(FinanceConstants.IS_MODIFID, FinanceConstants.YES);
			grnLine.setAttribute(FinanceConstants.IS_RTV, FinanceConstants.NO);

		}

		NodeList lineChargesList = XPathAPI.selectNodeList(receiptLineEle, "/OrderLine/LineCharges/LineCharge");
		Element grnLineDiscounts = grnHeaderDoc.createElement(FinanceConstants.GRN_LINE_DISCOUNTS);
		grnLine.appendChild(grnLineDiscounts);

		for (int i = 0; i < lineChargesList.getLength(); i++) {
			Element linerChargeEle = (Element) lineChargesList.item(i);
			String lineChargeName = linerChargeEle.getAttribute(FinanceConstants.CHARGE_NAME);
			String lineChargeAmount = linerChargeEle.getAttribute(FinanceConstants.CHARGE_AMOUNT);
			String lineChargeCategory = linerChargeEle.getAttribute(FinanceConstants.CHARGE_CATEGORY);
			double dlineChargeAmount = YFCCommon.isVoid(lineChargeAmount) ? 0.0 : Double.valueOf(lineChargeAmount);

			if (lineChargeCategory.indexOf("Discount") >= 0 && dlineChargeAmount > 0) {
				Element grnLineDiscountEle = grnHeaderDoc.createElement(FinanceConstants.GRN_LINE_DISCOUNT);
				grnLineDiscounts.appendChild(grnLineDiscountEle);
				grnLineDiscountEle.setAttribute(FinanceConstants.GRN_LINE_KEY,
						grnLine.getAttribute(FinanceConstants.GRN_LINE_KEY));
				grnLineDiscountEle.setAttribute(FinanceConstants.DISCOUNT_NAME, lineChargeName);
				grnLineDiscountEle.setAttribute(FinanceConstants.DISCOUNT_AMOUNT, lineChargeAmount);
				grnLineDiscountEle.setAttribute(FinanceConstants.GRN_HEADER_KEY, strGrnHederKey);
			}
		}
		logger.endTimer("populateGRNLineDetails");
		return grnLine;
	}

	/**
	 * This method populates GRN header from Receipt, PO details
	 * 
	 * @param env
	 * @param docGRNHeader
	 * @param receiptHeaderKey
	 * @param orderLineKey
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	private static void populateGRNHeaderDetails(YFSEnvironment env, Document docGRNHeader, String receiptHeaderKey,
			String orderLineKey, int invMovementValue) throws IllegalArgumentException, Exception {
		logger.beginTimer("populateGRNHeaderDetails");
		Document poOrderLineListTemp = XMLUtil.getDocument(FinanceConstants.GET_PO_ORDER_LINE_LIST_TEMPLATE);

		Document poOrderLineListInput = XMLUtil.createDocument(FinanceConstants.ORDER_LINE);
		Element eleGetOrderInvRoot = poOrderLineListInput.getDocumentElement();
		eleGetOrderInvRoot.setAttribute(FinanceConstants.ORDER_LINE_KEY, orderLineKey);

		Document poOrderLineOutputDoc = XMLUtil.invokeAPI(env, FinanceConstants.GET_PO_ORDER_LINE_LIST_API,
				poOrderLineListInput, poOrderLineListTemp);
		Element poOrderDetailsEle = XMLUtil.getFirstElementByXPath(poOrderLineOutputDoc,
				"//OrderLineList/OrderLine/Order");

		/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - Start*/

		//		Document receiptListTemp = XMLUtil.getDocument(FinanceConstants.GET_RECEIPT_LIST_TEMPLATE_FOR_GRN);
		Document receiptListTemp = XMLUtil.getDocument("<ReceiptList><Receipt ReceiptNo='' ShipmentKey='' ReceiptDate='' ReceivingNode=''></Receipt></ReceiptList>");	

		/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - End*/
		Document receiptListInput = XMLUtil.createDocument(FinanceConstants.RECEIPT);
		Element elereceiptListInput = receiptListInput.getDocumentElement();
		elereceiptListInput.setAttribute(FinanceConstants.RECEIPT_HEADER_KEY, receiptHeaderKey);

		Document receiptListOut = XMLUtil.invokeAPI(env, FinanceConstants.GET_RECEIPT_LIST_API, receiptListInput,
				receiptListTemp);
		Element receiptEle = XMLUtil.getFirstElementByXPath(receiptListOut, "//ReceiptList/Receipt");

		Element grnHeaderEle = docGRNHeader.getDocumentElement();
		String receiptNo = receiptEle.getAttribute(FinanceConstants.RECEIPT_NO);
		String strHeaderKey = ABOFFinanceUtils.getPrimaryKey(receiptNo);
		grnHeaderEle.setAttribute(FinanceConstants.GRN_HEADER_KEY, strHeaderKey);
		grnHeaderEle.setAttribute(FinanceConstants.VOUCHER_NO, receiptNo);
		grnHeaderEle.setAttribute(FinanceConstants.VOUCHER_DATE,
				receiptEle.getAttribute(FinanceConstants.RECEIPT_DATE));
		grnHeaderEle.setAttribute(FinanceConstants.DESTINATION,
				receiptEle.getAttribute(FinanceConstants.RECEIVING_NODE));
		grnHeaderEle.setAttribute(FinanceConstants.DELIVERY_DATE,
				receiptEle.getAttribute(FinanceConstants.RECEIPT_DATE));
		grnHeaderEle.setAttribute(FinanceConstants.DELIVERY_DUE_DATE,
				receiptEle.getAttribute(FinanceConstants.RECEIPT_DATE));
		grnHeaderEle.setAttribute(FinanceConstants.Is_PROCESSED, FinanceConstants.NO);
		grnHeaderEle.setAttribute(FinanceConstants.FREIGHT_CHARGE_NAME, FinanceConstants.BLANK);
		grnHeaderEle.setAttribute(FinanceConstants.FREIGHT_CHARGE_AMOUNT, FinanceConstants.DOUBLE_ZERO);
		grnHeaderEle.setAttribute(FinanceConstants.TAX_TYPE, FinanceConstants.BLANK);
		grnHeaderEle.setAttribute(FinanceConstants.TAX_RATE, FinanceConstants.BLANK);
		grnHeaderEle.setAttribute(FinanceConstants.TAX_AMOUNT, FinanceConstants.DOUBLE_ZERO);
		grnHeaderEle.setAttribute(FinanceConstants.REMARKS, FinanceConstants.BLANK);
		grnHeaderEle.setAttribute(FinanceConstants.CREATE_TIME_STAMP, getCreateTimeStamp());

		if (invMovementValue == 0 || invMovementValue == -1) {
			grnHeaderEle.setAttribute(FinanceConstants.IS_MODIFID, FinanceConstants.YES);
			grnHeaderEle.setAttribute(FinanceConstants.IS_RTV, FinanceConstants.YES);

			/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - Start*/
			Document shipListTemp = XMLUtil.getDocument("<Shipments><Shipment ShipmentNo='' ShipmentKey='' PickticketNo=''><Extn/></Shipment></Shipments>");	
			Document shipListInput = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
			Element eleShipListInput = shipListInput.getDocumentElement();
			eleShipListInput.setAttribute(FinanceConstants.SHIPMENT_KEY, receiptEle.getAttribute(FinanceConstants.SHIPMENT_KEY));

			Document shipListOut = XMLUtil.invokeAPI(env, FinanceConstants.GET_SHIPMENT_LIST, shipListInput,
					shipListTemp);
			Element eleShips = shipListOut.getDocumentElement();
			Element eleShip = (Element) eleShips.getElementsByTagName(FinanceConstants.SHIPMENT).item(0);
			Element eleExtn = (Element) eleShip.getElementsByTagName("Extn").item(0);
			grnHeaderEle.setAttribute(FinanceConstants.INVOICE_NO, eleShip.getAttribute("PickticketNo"));
			grnHeaderEle.setAttribute(FinanceConstants.INVOICE_DATE, eleExtn.getAttribute("ExtnInvoiceDate"));
			/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - End*/
		} else {
			grnHeaderEle.setAttribute(FinanceConstants.IS_MODIFID, FinanceConstants.YES);
			grnHeaderEle.setAttribute(FinanceConstants.IS_RTV, FinanceConstants.NO);

		}

		populateHeaderDetailsFromPO(poOrderDetailsEle, grnHeaderEle);

		logger.endTimer("populateGRNHeaderDetails");

	}

	/**
	 * This method populates GRN header details using PO
	 * 
	 * @param poOrderDetailsEle
	 * @param grnHeaderEle
	 * @throws TransformerException
	 */
	private static void populateHeaderDetailsFromPO(Element poOrderDetailsEle, Element grnHeaderEle)
			throws TransformerException {
		logger.beginTimer("populateHeaderDetailsFromPO");
		grnHeaderEle.setAttribute(FinanceConstants.VENDOR_CODE,
				poOrderDetailsEle.getAttribute(FinanceConstants.SELLER_ORG_CODE));
		grnHeaderEle.setAttribute(FinanceConstants.PO_NUMBER,
				poOrderDetailsEle.getAttribute(FinanceConstants.ORDER_NO));
		grnHeaderEle.setAttribute(FinanceConstants.PO_DATE,
				poOrderDetailsEle.getAttribute(FinanceConstants.ORDER_DATE));
		grnHeaderEle.setAttribute(FinanceConstants.PO_TYPE,
				poOrderDetailsEle.getAttribute(FinanceConstants.ORDER_TYPE));
		NodeList headerChargesNL = XPathAPI.selectNodeList(poOrderDetailsEle, "HeaderCharges/HeaderCharge");

		Document grnHeaderDoc = grnHeaderEle.getOwnerDocument();
		Element grnHeaderDiscountsEle = grnHeaderDoc.createElement(FinanceConstants.GRN_HEADER_DISCOUNTS);
		grnHeaderEle.appendChild(grnHeaderDiscountsEle);

		for (int i = 0; i < headerChargesNL.getLength(); i++) {
			Element headerChargeEle = (Element) headerChargesNL.item(i);
			String headerChargeName = headerChargeEle.getAttribute(FinanceConstants.CHARGE_NAME);
			String headerChargeAmount = headerChargeEle.getAttribute(FinanceConstants.CHARGE_AMOUNT);
			String headerChargeCategory = headerChargeEle.getAttribute(FinanceConstants.CHARGE_CATEGORY);
			double dheaderChargeAmount = YFCCommon.isVoid(headerChargeAmount) ? 0.0
					: Double.valueOf(headerChargeAmount);

			if (headerChargeName.indexOf(FinanceConstants.PO_FREIGHT_CHARGE) >= 0 && dheaderChargeAmount > 0) {
				grnHeaderEle.setAttribute(FinanceConstants.FREIGHT_CHARGE_NAME, headerChargeName);
				grnHeaderEle.setAttribute(FinanceConstants.FREIGHT_CHARGE_AMOUNT, headerChargeAmount);
			}
			if (headerChargeCategory.indexOf("Discount") >= 0 && dheaderChargeAmount > 0) {
				Element grnHeaderDiscountEle = grnHeaderDoc.createElement(FinanceConstants.GRN_HEADER_DISCOUNT);
				grnHeaderDiscountsEle.appendChild(grnHeaderDiscountEle);
				grnHeaderDiscountEle.setAttribute(FinanceConstants.GRN_HEADER_KEY,
						grnHeaderEle.getAttribute(FinanceConstants.GRN_HEADER_KEY));
				grnHeaderDiscountEle.setAttribute(FinanceConstants.DISCOUNT_NAME, headerChargeName);
				grnHeaderDiscountEle.setAttribute(FinanceConstants.DISCOUNT_AMOUNT, headerChargeAmount);
			}
		}

		NodeList headerTaxesNL = XPathAPI.selectNodeList(poOrderDetailsEle, "HeaderTaxes/HeaderTax");
		for (int i = 0; i < headerTaxesNL.getLength(); i++) {
			Element headerTaxEle = (Element) headerTaxesNL.item(i);
			String headerTaxName = headerTaxEle.getAttribute(FinanceConstants.TAX_NAME);
			String headerTaxAmount = headerTaxEle.getAttribute(FinanceConstants.TAX);
			String headerTaxPercent = headerTaxEle.getAttribute(FinanceConstants.TAX_PERCENTAGE);
			double dheaderTaxAmount = YFCCommon.isVoid(headerTaxAmount) ? 0.0 : Double.valueOf(headerTaxAmount);
			if (dheaderTaxAmount > 0) {
				grnHeaderEle.setAttribute(FinanceConstants.TAX_TYPE, headerTaxName);
				grnHeaderEle.setAttribute(FinanceConstants.TAX_RATE, headerTaxPercent);
				grnHeaderEle.setAttribute(FinanceConstants.TAX_AMOUNT, headerTaxAmount);
			}
		}
		logger.endTimer("populateHeaderDetailsFromPO");

	}

	/**
	 * This method takes list of serial no. as input and invoke
	 * getReceiptLineList API to fetch all the PO receipt associated to these
	 * serial no.
	 * 
	 * @param env
	 * @param serialNoList
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public static Document getReceiptLineListForSerials(YFSEnvironment env, Set<String> serialNoList)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("getReceiptLineListForSerials");

		Boolean isRTV=false;

		if(!YFCObject.isVoid(env.getTxnObject("isRTV"))){

			isRTV=(Boolean) env.getTxnObject("isRTV");

		}


		Document getReceiptLineListInput = XMLUtil.getDocument(FinanceConstants.GET_RECEIPT_LINE_LIST_INPUT);
		Node orEle = XPathAPI.selectSingleNode(getReceiptLineListInput, "//ReceiptLine/ComplexQuery/And/Or");
		for (String serialNo : serialNoList) {
			Element expEle = getReceiptLineListInput.createElement("Exp");
			expEle.setAttribute("Name", FinanceConstants.SERIAL_NO);
			expEle.setAttribute("QryType", "EQ");
			expEle.setAttribute("Value", serialNo);

			if(isRTV){
				String[] ItemSrl=serialNo.split(","); //ItemId,SerialNo
				Element recptLine = getReceiptLineListInput.getDocumentElement();
				recptLine.setAttribute("ItemID", ItemSrl[0]); // expected to have only one Serial in RTV for each process
				expEle.setAttribute("Value", ItemSrl[1]);
			}

			orEle.appendChild(expEle);
		}

		Document getReceiptLineListTemp = XMLUtil.getDocument(FinanceConstants.GET_RECEIPT_LINE_LIST_TEMPLATE);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Going to call getReceiptLineList with input\n"
					+ XMLUtil.getXmlString(getReceiptLineListInput.getDocumentElement()) + " and template\n"
					+ XMLUtil.getXmlString(getReceiptLineListTemp.getDocumentElement()));
		}
		Document receiptLineList = XMLUtil.invokeAPI(env, FinanceConstants.GET_RECEIPT_LINE_LIST_API,
				getReceiptLineListInput, getReceiptLineListTemp);

		if (logger.isVerboseEnabled()) {
			logger.verbose(
					"ouput of getReceiptLineList is\n" + XMLUtil.getXmlString(receiptLineList.getDocumentElement()));
		}
		logger.endTimer("getReceiptLineListForSerials");

		return receiptLineList;

	}

	/**
	 * This method fetches the PO receipt lines for given serial no. and
	 * calculate the cost associated to those serial
	 * 
	 * @param env
	 * @param serialNoList
	 * @param itemIDTotalUnitCostMap
	 * @param itemIDTotalQtyMap
	 * @param itemIDDescriptionMap
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public static void getItemDetailsForCost(YFSEnvironment env, Set<String> serialNoList,
			Map<String, Double> itemIDTotalUnitCostMap, Map<String, Integer> itemIDTotalQtyMap,
			Map<String, String> itemIDDescriptionMap) throws IllegalArgumentException, Exception {
		logger.beginTimer("getItemDetailsForCost");
		Document poReceiptLineList = ABOFFinanceUtils.getReceiptLineListForSerials(env, serialNoList);
		List<Element> poItemEleList = XMLUtil.getElementsByXpath(poReceiptLineList,
				"//ReceiptLineList/ReceiptLine/OrderLine/Item");
		for (Element elePOItem : poItemEleList) {
			String itemID = elePOItem.getAttribute(FinanceConstants.ITEM_ID);
			String itemDesc = elePOItem.getAttribute("ItemDesc");
			String strUnitCost = elePOItem.getAttribute(FinanceConstants.UNIT_COST);
			double dUnitCost = YFCCommon.isVoid(strUnitCost) ? 0.0 : Double.valueOf(strUnitCost);
			Double totalUnitCost = itemIDTotalUnitCostMap.get(itemID);
			Integer totalQty = itemIDTotalQtyMap.get(itemID);
			if (YFCCommon.isVoid(totalUnitCost)) {
				totalUnitCost = dUnitCost;
				totalQty = 1;
				itemIDDescriptionMap.put(itemID, itemDesc);
			} else {
				totalUnitCost = totalUnitCost + dUnitCost;
				totalQty++;
			}

			itemIDTotalUnitCostMap.put(itemID, totalUnitCost);
			itemIDTotalQtyMap.put(itemID, totalQty);
		}
		logger.endTimer("getItemDetailsForCost");
	}

	/**
	 * This method is used to fetch the serial number of items in a shipment
	 * 
	 * @param env
	 * @param shipmentKey
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public static Map<String, List<String>> getSerialNumberDtlsOfShipment(YFSEnvironment env, String shipmentKey)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("getSerialNumberDtlsOfShipment");
		Map<String, List<String>> hmSerialNo = new HashMap<String, List<String>>();
		// Creating input document for calling getShipmentList
		Document docGetShipListInput = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
		Element eleGetShipListRoot = docGetShipListInput.getDocumentElement();
		eleGetShipListRoot.setAttribute(FinanceConstants.SHIPMENT_KEY, shipmentKey);
		// Calling getShipmentList
		Document docGetShipListOutput = XMLUtil.invokeAPI(null, FinanceConstants.GET_SHIPMENT_LIST, docGetShipListInput,
				XMLUtil.getDocument(FinanceConstants.GET_SHIPMENT_LIST_TMP));
		if (logger.isVerboseEnabled()) {
			logger.verbose("ouput of GET_SHIPMENT_LIST is\n"
					+ XMLUtil.getXmlString(docGetShipListOutput.getDocumentElement()));
		}
		NodeList nlContainerDetail = XPathAPI.selectNodeList(docGetShipListOutput,
				"//ShipmentList/Shipment/Containers/Container/ContainerDetails/ContainerDetail");
		for (int intCD = 0; intCD < nlContainerDetail.getLength(); intCD++) {
			Element eleContainerDetail = (Element) nlContainerDetail.item(intCD);
			String strItemID = eleContainerDetail.getAttribute(FinanceConstants.ITEM_ID);
			NodeList nlShipTagSerial = XPathAPI.selectNodeList(eleContainerDetail,
					"//ShipmentTagSerials/ShipmentTagSerial");
			ArrayList<String> arrSerialNo = new ArrayList<String>();
			for (int intSTS = 0; intSTS < nlShipTagSerial.getLength(); intSTS++) {
				Element eleShipTagSerial = (Element) nlShipTagSerial.item(intSTS);
				String strSerialNo = eleShipTagSerial.getAttribute(FinanceConstants.SERIAL_NO);
				arrSerialNo.add(strSerialNo);
			}
			hmSerialNo.put(strItemID, arrSerialNo);
		}
		logger.endTimer("getSerialNumberDtlsOfShipment");
		return hmSerialNo;
	}

	/**
	 * This method is used to fetch the sales invoice detail from the order line
	 * key
	 * 
	 * @param env
	 * @param strOrderHeaderKey
	 * @return
	 * @throws Exception
	 */
	public static Document getSalesInvoiceListForOrderLine(YFSEnvironment env, String strOrderHeaderKey)
			throws Exception {
		logger.beginTimer("getSalesInvoiceListForOrderLine");
		// Creating input document for calling getOrderInvoiceDetails API
		Document docGetOrderInvoiceInput = XMLUtil.createDocument(FinanceConstants.INVOICE_DETAIL);
		Element eleGetOrderInvoiceRoot = docGetOrderInvoiceInput.getDocumentElement();
		Element eleInvoiceHeader = docGetOrderInvoiceInput.createElement(FinanceConstants.INVOICE_HEADER);

		eleInvoiceHeader.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strOrderHeaderKey);
		eleGetOrderInvoiceRoot.appendChild(eleInvoiceHeader);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Input document for get order invoice details list:"
					+ XMLUtil.getXmlString(eleGetOrderInvoiceRoot));
		}
		// Calling getOrderInvoiceDetails API
		Document docGetOrderInvoiceOut = XMLUtil.invokeAPI(env, FinanceConstants.GET_ORDER_INVOICE_DETAILS_LIST,
				docGetOrderInvoiceInput, XMLUtil.getDocument(FinanceConstants.GET_ORDER_INVOICE_DETAIL_TMP));
		if (logger.isVerboseEnabled()) {
			logger.verbose("Output document for get order invoice details list:"
					+ XMLUtil.getXmlString(docGetOrderInvoiceOut.getDocumentElement()));
		}
		logger.endTimer("getSalesInvoiceListForOrderLine");
		return docGetOrderInvoiceOut;
	}

	/**
	 * This method is used to get the current system time
	 * 
	 * @param strDateFormat
	 * @return
	 * @throws Exception
	 */
	public static String getSystemTime(String strDateFormat) throws Exception {
		logger.beginTimer("getSystemTime");
		Calendar calSystemDate = new GregorianCalendar();
		SimpleDateFormat sdf = new SimpleDateFormat(strDateFormat);
		logger.endTimer("getSystemTime");
		return sdf.format(calSystemDate.getTime());
	}

	/**
	 * This method will return a list of sales invoice numbers associated to a
	 * return or exchange order
	 *
	 * @param env
	 * @param strDerFromOHKey
	 * @param outputInvHeaderEle
	 * @param valueOfSOnROnCOD
	 * @param soOrderLineKeyForEO
	 * @return
	 * @throws Exception
	 */
	public static List<Map<String, HashSet<String>>> getAssSalesInvoiceNos(YFSEnvironment env, String strDerFromOHKey,
			Element outputInvHeaderEle, Set<String> soOrderLineKey) throws Exception {
		logger.beginTimer("getAssSalesInvoiceNos");
		// Creating HashMap to store order line key and invoice numbers
		List<Map<String, HashSet<String>>> listOfHashMap = new ArrayList<Map<String, HashSet<String>>>();
		Map<String, HashSet<String>> hmInvoiceNo = new HashMap<String, HashSet<String>>();
		Map<String, HashSet<String>> hmDateInvoiced = new HashMap<String, HashSet<String>>();
		if (logger.isVerboseEnabled()) {
			logger.verbose("soOrderLineKeyForEO or RO:" + soOrderLineKey);
		}

		// Creating document to call get order invoice list api
		Document docSOInvocieOutput = getSalesOrderInvoiceList(env, strDerFromOHKey);
		Element eleSOInvoiceOut = docSOInvocieOutput.getDocumentElement();
		NodeList nlOrderInvocie = eleSOInvoiceOut.getElementsByTagName(FinanceConstants.ORDER_INVOICE);
		Element eleOrderInvFirstItem = (Element) nlOrderInvocie.item(0);
		outputInvHeaderEle.setAttribute(FinanceConstants.SALES_ORDER_NO,
				eleOrderInvFirstItem.getAttribute(FinanceConstants.ORDER_NO));
		outputInvHeaderEle.setAttribute(FinanceConstants.SALES_ORDER_DATE, getOrderDate(env, strDerFromOHKey));
		for (int i = 0; i < nlOrderInvocie.getLength(); i++) {
			Element eleOrderInvocie = (Element) nlOrderInvocie.item(i);
			String strOrderInvKey = eleOrderInvocie.getAttribute(FinanceConstants.ORDER_INVOICE_KEY);
			// Call get order invoice details service
			Document docSOInvDetailOut = getSalesOrderInvoiceDetails(env, strOrderInvKey);
			Element eleSOInvDetRootOut = docSOInvDetailOut.getDocumentElement();
			Element eleSOInvoiceHeader = (Element) eleSOInvDetRootOut
					.getElementsByTagName(FinanceConstants.INVOICE_HEADER).item(0);
			String strShipmentNo = eleSOInvoiceHeader.getAttribute(FinanceConstants.SHIPMENT_NO);
			String strDateInvoiced = eleSOInvoiceHeader.getAttribute(FinanceConstants.DATE_INVOICED);
			NodeList nlLineDetail = XPathAPI.selectNodeList(eleSOInvoiceHeader, "LineDetails/LineDetail");
			for (int j = 0; j < nlLineDetail.getLength(); j++) {
				Element eleLineDetail = (Element) nlLineDetail.item(j);
				String strOLKey = eleLineDetail.getAttribute(FinanceConstants.ORDER_LINE_KEY);
				if (!soOrderLineKey.contains(strOLKey)) {
					logger.verbose("OrderLineKey of SO Invoice:" + strOLKey
							+ " is not part of Exchange/ Return Order. Hence going for next LineDetail");
					continue;
				}
				logger.verbose("OrderLineKey of SO Invoice:" + strOLKey
						+ " is  part of Exchange Order. Hence going for this LineDetail");
				if (!hmInvoiceNo.containsKey(strOLKey)) {
					HashSet<String> setInvoiceNo = new HashSet<String>();
					HashSet<String> setDateInvoiced = new HashSet<String>();
					setInvoiceNo.add(FinanceConstants.INVOICE_PREFIX.concat(strShipmentNo));
					setDateInvoiced.add(strDateInvoiced);
					hmInvoiceNo.put(strOLKey, setInvoiceNo);
					hmDateInvoiced.put(strOLKey, setDateInvoiced);
					hmInvoiceNo.put("Key1", setInvoiceNo);
					hmInvoiceNo.put("Key2", setDateInvoiced);
				} else {
					HashSet<String> setInvNoKey = hmInvoiceNo.get("Key1");
					HashSet<String> setDateInvKey = hmInvoiceNo.get("Key2");
					if (!setInvNoKey.contains(FinanceConstants.INVOICE_PREFIX.concat(strShipmentNo))) {
						setInvNoKey.add(FinanceConstants.INVOICE_PREFIX.concat(strShipmentNo));
						hmInvoiceNo.put("Key1", setInvNoKey);
					}
					if (!setDateInvKey.contains(strDateInvoiced)) {
						setDateInvKey.add(strDateInvoiced);
						hmInvoiceNo.put("Key2", setDateInvKey);
					}
					HashSet<String> setInvNo = hmInvoiceNo.get(strOLKey);
					HashSet<String> setDateInv = hmDateInvoiced.get(strOLKey);
					setInvNo.add(FinanceConstants.INVOICE_PREFIX.concat(strShipmentNo));
					setDateInv.add(strDateInvoiced);
					hmInvoiceNo.put(strOLKey, setInvNo);
				}
			}
		}
		listOfHashMap.add(hmInvoiceNo);
		listOfHashMap.add(hmDateInvoiced);
		logger.endTimer("getAssSalesInvoiceNos");
		return listOfHashMap;
	}

	public static String getOrderDate(YFSEnvironment env, String strOrderHeaderKey) throws Exception {
		Document inDoc = XMLUtil.createDocument(FinanceConstants.ORDER);
		Element eleRootIn = inDoc.getDocumentElement();
		eleRootIn.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strOrderHeaderKey);
		Document docTemplate = XMLUtil.getDocument("<OrderList><Order OrderDate=''/></OrderList>");
		Document outDoc = XMLUtil.invokeAPI(env, FinanceConstants.GET_ORDER_LIST, inDoc, docTemplate);
		Element eleOrder = (Element) outDoc.getDocumentElement().getElementsByTagName(FinanceConstants.ORDER).item(0);
		String salesOrderDate = eleOrder.getAttribute(FinanceConstants.ORDER_DATE);
		return salesOrderDate;
	}

	/**
	 * This method will return the sales order invoice list output for an order
	 * from the order header key
	 * 
	 * @param env
	 * @param strDerFromOHKey
	 * @return
	 * @throws Exception
	 */
	public static Document getSalesOrderInvoiceList(YFSEnvironment env, String strDerFromOHKey) throws Exception {
		logger.beginTimer("getSalesOrderInvoiceList");
		Document docSOInvocieInput = XMLUtil.createDocument(FinanceConstants.ORDER_INVOICE);
		Element eleRootSOInvoiceIn = docSOInvocieInput.getDocumentElement();
		eleRootSOInvoiceIn.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strDerFromOHKey);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Input document for get order invoice list:" + XMLUtil.getXmlString(eleRootSOInvoiceIn));
		}
		Document docSOInvocieOutput = XMLUtil.invokeService(env, FinanceConstants.GET_ORDER_INV_LIST_SERVICE,
				docSOInvocieInput);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Output document for get order invoice list:"
					+ XMLUtil.getXmlString(docSOInvocieOutput.getDocumentElement()));
		}
		logger.endTimer("getSalesOrderInvoiceList");
		return docSOInvocieOutput;
	}

	/**
	 * This method will return the sales invoice detail for a particular invoice
	 * key
	 * 
	 * @param env
	 * @param strOrderInvKey
	 * @return
	 * @throws Exception
	 */
	public static Document getSalesOrderInvoiceDetails(YFSEnvironment env, String strOrderInvKey) throws Exception {
		logger.beginTimer("getSalesOrderInvoiceDetails");
		Document docGetOrdInvDet = XMLUtil.createDocument(FinanceConstants.GET_ORDER_INVOICE_DETAILS);
		Element eleRootGetOrdInvDet = docGetOrdInvDet.getDocumentElement();
		eleRootGetOrdInvDet.setAttribute(FinanceConstants.INVOICE_KEY, strOrderInvKey);
		if (logger.isVerboseEnabled()) {
			logger.verbose(
					"Input document for get order invoice details for RO:" + XMLUtil.getXmlString(eleRootGetOrdInvDet));
		}
		Document docSOInvDetailOut = XMLUtil.invokeService(env, FinanceConstants.GET_ORDER_INV_DET_FOR_RO,
				docGetOrdInvDet);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Output document for get order invoice details for RO:"
					+ XMLUtil.getXmlString(docSOInvDetailOut.getDocumentElement()));
		}
		logger.endTimer("getSalesOrderInvoiceDetails");
		return docSOInvDetailOut;
	}

	/**
	 * This method gets the list of serial no. for provided ShipmentKey
	 * 
	 * @param env
	 * @param shipmentKey
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public static Set<String> getSerialNumberListOfShipment(YFSEnvironment env, String shipmentKey)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("getSerialNumberDtlsOfShipment");
		Set<String> arrSerialNo = new HashSet<String>();

		// Creating input document for calling getShipmentList
		Document docGetShipListInput = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
		Element eleGetShipListRoot = docGetShipListInput.getDocumentElement();
		eleGetShipListRoot.setAttribute(FinanceConstants.SHIPMENT_KEY, shipmentKey);
		if (logger.isVerboseEnabled()) {
			logger.verbose("input of GET_SHIPMENT_LIST is\n" + XMLUtil.getXmlString(eleGetShipListRoot));
		}
		Document docGetShipListOutput = XMLUtil.invokeAPI(env, FinanceConstants.GET_SHIPMENT_LIST, docGetShipListInput,
				XMLUtil.getDocument(FinanceConstants.GET_SHIPMENT_LIST_TMP));
		if (logger.isVerboseEnabled()) {
			logger.verbose("ouput of GET_SHIPMENT_LIST is\n"
					+ XMLUtil.getXmlString(docGetShipListOutput.getDocumentElement()));
		}
		NodeList nlContainerDetail = XPathAPI.selectNodeList(docGetShipListOutput,
				"//Shipments/Shipment/Containers/Container/ContainerDetails/ContainerDetail");
		for (int intCD = 0; intCD < nlContainerDetail.getLength(); intCD++) {
			Element eleContainerDetail = (Element) nlContainerDetail.item(intCD);
			NodeList nlShipTagSerial = XPathAPI.selectNodeList(eleContainerDetail,
					"ShipmentTagSerials/ShipmentTagSerial");

			for (int intSTS = 0; intSTS < nlShipTagSerial.getLength(); intSTS++) {
				Element eleShipTagSerial = (Element) nlShipTagSerial.item(intSTS);
				String strSerialNo = eleShipTagSerial.getAttribute(FinanceConstants.SERIAL_NO);
				arrSerialNo.add(strSerialNo);
			}
		}
		logger.endTimer("getSerialNumberDtlsOfShipment");
		return arrSerialNo;
	}

	/**
	 * This method returns the cost of goods sold for each order line of a
	 * shipment present in invoice element
	 * 
	 * @param env
	 * @param eleRootIn
	 * @throws Exception
	 */
	public static Map<String, Double> getCOGSForOrderLines(YFSEnvironment env, Element eleRootIn) throws Exception {
		logger.beginTimer("getCOGSForOrderLines");
		Map<String, List<String>> orderLineKeySerialsMap = new HashMap<String, List<String>>();
		Map<String, Double> serialNoCostMap = new HashMap<String, Double>();
		Element eleShpmnt = (Element) XPathAPI.selectSingleNode(eleRootIn, FinanceConstants.SHIPMENT);
		String strShipmentKey = eleShpmnt.getAttribute(FinanceConstants.SHIPMENT_KEY);
		getSerialNoOfInvoice(env, strShipmentKey, orderLineKeySerialsMap, serialNoCostMap);
		getCostForSerialNos(env, serialNoCostMap);
		Map<String, Double> orderLineKeyCOGSMap = getCostForOrderLine(orderLineKeySerialsMap, serialNoCostMap);
		logger.endTimer("getCOGSForOrderLines");
		return orderLineKeyCOGSMap;
	}

	/**
	 * This method will return the sum of all cost price of all the goods in a
	 * shipment line
	 *
	 * @param env
	 * @param strOrderHeaderKey
	 * @param orderLineKeyCarrierDtlsPerShipment
	 * @param valueOfSOnROnCOD
	 * @return
	 * @throws Exception
	 */
	public static Map<String, Double> getCOGSForOrderLinesForReturn(YFSEnvironment env, String strOrderHeaderKey,
			Map<String, Double> orderLineKeyCODAmountPerShipment,
			Map<String, String> orderLineKeyCarrierDtlsPerShipment) throws Exception {
		logger.beginTimer("getCOGSForOrderLinesForReturn");
		Map<String, List<String>> orderLineKeySerialsMap = new HashMap<String, List<String>>();
		Map<String, Double> serialNoCostMap = new HashMap<String, Double>();
		// Creating document to call get shipment list
		Document inDoc = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
		Element eleRootIn = inDoc.getDocumentElement();
		Element eleShmntLines = inDoc.createElement(FinanceConstants.SHIPMENT_LINES);
		eleRootIn.appendChild(eleShmntLines);
		Element eleShmntLine = inDoc.createElement(FinanceConstants.SHIPMENT_LINE);
		eleShmntLines.appendChild(eleShmntLine);
		Element eleOrder = inDoc.createElement(FinanceConstants.ORDER);
		eleShmntLine.appendChild(eleOrder);
		eleOrder.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strOrderHeaderKey);
		String strTemp = "<Shipments><Shipment ShipmentKey='' AirwayBillNo='' SCAC=''><ShipmentLines><ShipmentLine OrderLineKey=''/></ShipmentLines><Extn/></Shipment></Shipments>";
		if (logger.isVerboseEnabled()) {
			logger.verbose("Input document for get shipment list:" + XMLUtil.getXmlString(eleRootIn));
		}
		Document outDoc = XMLUtil.invokeAPI(env, FinanceConstants.GET_SHIPMENT_LIST, inDoc,
				XMLUtil.getDocument(strTemp));
		Element eleRootOut = outDoc.getDocumentElement();
		if (logger.isVerboseEnabled()) {
			logger.verbose("Output document for get shipment list:" + XMLUtil.getXmlString(eleRootOut));
		}
		NodeList nlShipment = eleRootOut.getElementsByTagName(FinanceConstants.SHIPMENT);
		int length = nlShipment.getLength();

		for (int i = 0; i < length; i++) {
			Element eleShipment = (Element) nlShipment.item(i);
			double codAmount = 0.0;
			String airWayBillNo = eleShipment.getAttribute(FinanceConstants.AWB_NO);
			String scac = eleShipment.getAttribute(FinanceConstants.SCAC);
			String strShipmentKey = eleShipment.getAttribute(FinanceConstants.SHIPMENT_KEY);
			Node nodeExtn = XPathAPI.selectSingleNode(eleShipment, "Extn");
			if (!YFCCommon.isVoid(nodeExtn)) {
				Element extnEle = (Element) nodeExtn;
				String codAmountStr = extnEle.getAttribute("ExtnCODPayAmount");
				logger.verbose("Fetched ExtnCODPayAmount");
				if (!YFCCommon.isVoid(codAmountStr)) {
					codAmount = Double.valueOf(codAmountStr);
					logger.verbose("Now total COD amount is:" + codAmount);
				}
				Node nodeShipmentLine = XPathAPI.selectSingleNode(eleShipment, "ShipmentLines/ShipmentLine");
				if (!YFCCommon.isVoid(nodeShipmentLine)) {
					Element shipmentEle = (Element) nodeShipmentLine;
					String orderLineKey = shipmentEle.getAttribute(FinanceConstants.ORDER_LINE_KEY);
					logger.verbose("Going to add OrderLineKey in the list: " + orderLineKey);
					orderLineKeyCODAmountPerShipment.put(orderLineKey, codAmount);
					orderLineKeyCarrierDtlsPerShipment.put(orderLineKey, airWayBillNo + ":" + scac);
				}
				if (logger.isVerboseEnabled()) {
					logger.verbose("OrderLineKey:CODAmount" + orderLineKeyCODAmountPerShipment);
					logger.verbose("OrderLineKey:CarrierDtls" + orderLineKeyCarrierDtlsPerShipment);
				}
			}

			getSerialNoOfInvoice(env, strShipmentKey, orderLineKeySerialsMap, serialNoCostMap);
			getCostForSerialNos(env, serialNoCostMap);
		}
		Map<String, Double> orderLineKeyCOGSMap = getCostForOrderLine(orderLineKeySerialsMap, serialNoCostMap);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Current value of valueOfSOnROnCOD:" + orderLineKeyCODAmountPerShipment);
		}
		logger.endTimer("getCOGSForOrderLinesForReturn");
		return orderLineKeyCOGSMap;
	}

	/**
	 * This method fetches shipment details using shipment key and accordingly
	 * get all serials associated to an order line and subsequently cogs for
	 * each serial
	 * 
	 * @param env
	 * @param strShipmentKey
	 * @param orderLineKeySerialsMap
	 * @param serialNoCostMap
	 * @throws Exception
	 */
	private static void getSerialNoOfInvoice(YFSEnvironment env, String strShipmentKey,
			Map<String, List<String>> orderLineKeySerialsMap, Map<String, Double> serialNoCostMap) throws Exception {
		logger.beginTimer("getSerialNoOfInvoice");
		Document docGetShmntDetInput = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
		Element eleRootShmntIn = docGetShmntDetInput.getDocumentElement();
		eleRootShmntIn.setAttribute(FinanceConstants.SHIPMENT_KEY, strShipmentKey);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Input document for get shipment details:" + XMLUtil.getXmlString(eleRootShmntIn));
		}
		Document docGetShmntDetOutput = XMLUtil.invokeService(env, "GetShipmentDetails", docGetShmntDetInput);
		Element eleRootShmntOut = docGetShmntDetOutput.getDocumentElement();
		if (logger.isVerboseEnabled()) {
			logger.verbose("Output document for get shipment details:" + XMLUtil.getXmlString(eleRootShmntOut));
		}
		NodeList nlContainerDetail = XPathAPI.selectNodeList(eleRootShmntOut,
				"//Containers/Container/ContainerDetails/ContainerDetail");
		int intCD = nlContainerDetail.getLength();
		for (int i = 0; i < intCD; i++) {
			Element eleContainerDetail = (Element) nlContainerDetail.item(i);
			String strOLKey = eleContainerDetail.getAttribute(FinanceConstants.ORDER_LINE_KEY);
			NodeList nlShpmntTagSrl = XPathAPI.selectNodeList(eleContainerDetail,
					"ShipmentTagSerials/ShipmentTagSerial");
			int intSTS = nlShpmntTagSrl.getLength();
			List<String> listSerialNo = new ArrayList<String>();
			for (int j = 0; j < intSTS; j++) {
				Element eleShpmnttagSrl = (Element) nlShpmntTagSrl.item(j);
				String strSerialNo = eleShpmnttagSrl.getAttribute(FinanceConstants.SERIAL_NO);
				listSerialNo.add(strSerialNo);
				if (!serialNoCostMap.containsKey(strSerialNo)) {
					serialNoCostMap.put(strSerialNo, 0.00);
				}
			}
			if (!orderLineKeySerialsMap.containsKey(strOLKey)) {
				orderLineKeySerialsMap.put(strOLKey, listSerialNo);
			} else {
				List<String> listOrgSerial = orderLineKeySerialsMap.get(strOLKey);
				listOrgSerial.addAll(listSerialNo);
				orderLineKeySerialsMap.put(strOLKey, listOrgSerial);
			}
		}
		logger.endTimer("getSerialNoOfInvoice");
	}

	/**
	 * This method fetches the cost of goods sold for list of serials
	 * 
	 * @param env
	 * @param serialNoCostMap
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public static Map<String, Double> getCostForSerialNos(YFSEnvironment env, Map<String, Double> serialNoCostMap)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("getCostForSerialNos");
		Set<String> listOfSerialNo = serialNoCostMap.keySet();
		if (serialNoCostMap == null || serialNoCostMap.isEmpty()) {
			logger.verbose("serialNoCostMap is empty so no need to go ahead to fetch PO receipt based upon serial no");
			return serialNoCostMap;
		}
		Document docSerial = getReceiptLineListForSerials(env, listOfSerialNo);
		Element eleRootSerial = docSerial.getDocumentElement();
		NodeList nlReceiptLine = eleRootSerial.getElementsByTagName(FinanceConstants.RECEIPT_LINE);
		int intRL = nlReceiptLine.getLength();
		for (int i = 0; i < intRL; i++) {
			Element eleReceiptLine = (Element) nlReceiptLine.item(i);
			NodeList shipmentLineNL = eleReceiptLine.getElementsByTagName(FinanceConstants.SHIPMENT_LINE);
			if (YFCCommon.isVoid(shipmentLineNL) || shipmentLineNL.getLength() == 0) {
				logger.verbose(
						"ReceiptElement is receipt of return. Hence skipping this. As it is not associated to any PO. ReceiptLine element is \n"
								+ XMLUtil.getXmlString(eleReceiptLine));
				continue;
			}
			String sQty = eleReceiptLine.getAttribute(FinanceConstants.QUANTITY);
			double dQty = 0.00;
			if (!YFCCommon.isVoid(sQty)) {
				dQty = Double.valueOf(sQty);
			}
			logger.verbose(" Quantity=" + dQty);

			if (dQty <= 0.00) {
				logger.verbose(" Quantity=" + dQty + " As  Quantity is zero, this ReceiptLine will not be considered");
				continue;
			}

			String strSerialNo = eleReceiptLine.getAttribute(FinanceConstants.SERIAL_NO);
			if (serialNoCostMap.containsKey(strSerialNo)) {
				NodeList nlOrderLine = eleReceiptLine.getElementsByTagName(FinanceConstants.ORDER_LINE);
				if (YFCCommon.isVoid(nlOrderLine) || nlOrderLine.getLength() == 0) {
					logger.verbose(
							" Since OrderLine element is not present. No need to further go ahead for COGS calculation of this ReceiptLine");
					continue;
				}
				Element eleOrderLine = (Element) nlOrderLine.item(0);
				Node nodeLinePriceInfo = XPathAPI.selectSingleNode(eleOrderLine, FinanceConstants.LINE_PRICE_INFO);
				if (YFCCommon.isVoid(nodeLinePriceInfo)) {
					logger.verbose(
							" Since LinePriceInfo element is not present. No need to further go ahead for COGS calculation of this ReceiptLine");
					continue;
				}
				Element eleLinePriceInfo = (Element) nodeLinePriceInfo;
				double dblcogs = Double.parseDouble(eleLinePriceInfo.getAttribute(FinanceConstants.UNIT_PRICE));
				NodeList nlLineTax = XPathAPI.selectNodeList(eleOrderLine, "LineTaxes/LineTax");
				for (int j = 0; j < nlLineTax.getLength(); j++) {
					Element eleLineTax = (Element) nlLineTax.item(j);
					String strRef1 = eleLineTax.getAttribute(FinanceConstants.REFERENCE_1);
					if ("CST".equalsIgnoreCase(strRef1)) {
						double dblTaxPerc = Double
								.parseDouble(eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
						dblcogs = (dblcogs * (100 + dblTaxPerc)) / 100;
						break;
					}
				}
				serialNoCostMap.put(strSerialNo, dblcogs);
				if (logger.isVerboseEnabled()) {
					logger.verbose("Added COGS:" + dblcogs + " for Serial No:" + strSerialNo);
				}
			}
		}
		logger.endTimer("getCostForSerialNos");
		return serialNoCostMap;
	}

	/**
	 * This method sums up the cost of all serial numbers associated to an order
	 * line key
	 * 
	 * @param orderLineKeySerialsMap
	 * @param serialNoCostMap
	 * @return
	 */
	public static Map<String, Double> getCostForOrderLine(Map<String, List<String>> orderLineKeySerialsMap,
			Map<String, Double> serialNoCostMap) {
		logger.beginTimer("getCostForOrderLine");
		Map<String, Double> orderLineKeyCOGSMap = new HashMap<String, Double>();
		for (Map.Entry<String, List<String>> entry : orderLineKeySerialsMap.entrySet()) {
			String strOLKey = entry.getKey();
			List<String> listOfSerialNo = entry.getValue();
			double dblTotalCost = 0.00;
			for (int i = 0; i < listOfSerialNo.size(); i++) {
				String strSerialNo = listOfSerialNo.get(i);
				Double dblCostOfSerialNo = serialNoCostMap.get(strSerialNo);
				dblTotalCost = dblTotalCost + dblCostOfSerialNo;
			}
			orderLineKeyCOGSMap.put(strOLKey, dblTotalCost);
			if (logger.isVerboseEnabled()) {
				logger.verbose("Added strOLKey:" + strOLKey + " dblTotalCost:" + dblTotalCost);
			}
		}
		logger.endTimer("getCostForOrderLine");
		return orderLineKeyCOGSMap;
	}

	/**
	 * This method gets the tax percentage for Header charges (i.e. shipping or
	 * gift wrapping charges) Logic is as: Max tax percentage value is taken for
	 * all the item used in the order/ invoice (tax percentage for items are
	 * added as Extended field Tax1 and Tax2 during Item feed) During invoicing
	 * if it is other than first invoice then it should not calculate the
	 * HeaderTax percentage value
	 *
	 * @param env
	 * @param eleRootIn
	 * @param isInvoicingEvent
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public static Object[] getHeaderTaxPercentageAndType(YFSEnvironment env, Element eleRootIn,
			boolean isInvoicingEvent) throws IllegalArgumentException, Exception {
		logger.beginTimer("getHeaderTaxPercentage");
		if (logger.isVerboseEnabled()) {
			logger.verbose(
					"Going to fetch line tax details from input element i.e. " + XMLUtil.getXmlString(eleRootIn));
		}

		Object[] headerTaxValues = new Object[2]; // This array stores
		// taxPercentage and taxType
		// in this order only
		double taxPercentage = 0;
		String taxType = "";

		NodeList nlLineTax = null;
		if (isInvoicingEvent) {
			nlLineTax = XPathAPI.selectNodeList(eleRootIn, "InvoiceHeader/LineDetails/LineDetail/LineTaxes/LineTax");
		} else {
			nlLineTax = XPathAPI.selectNodeList(eleRootIn, "OrderLines/OrderLine/LineTaxes/LineTax");
		}
		if (YFCCommon.isVoid(nlLineTax) || nlLineTax.getLength() == 0) {
			headerTaxValues[0] = taxPercentage;
			headerTaxValues[1] = taxType;
			return headerTaxValues;
		} else {
			for (int i = 0; i < nlLineTax.getLength(); i++) {
				Element lineTaxEle = (Element) nlLineTax.item(i);
				if (logger.isVerboseEnabled()) {
					logger.verbose(
							"Going to fetch line tax details from lineTaxEle i.e. " + XMLUtil.getXmlString(lineTaxEle));
				}
				String lineTaxPercentage = lineTaxEle.getAttribute(FinanceConstants.TAX_PERCENTAGE);
				String lineTaxName = lineTaxEle.getAttribute(FinanceConstants.REFERENCE_1);
				if (!YFCCommon.isVoid(lineTaxPercentage)) {
					double dLineTaxPercentage = Double.valueOf(lineTaxPercentage);
					if (dLineTaxPercentage > taxPercentage) {
						taxPercentage = dLineTaxPercentage;
						taxType = lineTaxName;
						logger.verbose("LineTax percentage value has changed to " + dLineTaxPercentage + " and name to:"
								+ lineTaxName);
					}
				}
			}
		}
		logger.verbose(
				"Going to return the header tax percentage value as: " + taxPercentage + " and name as:" + taxType);
		headerTaxValues[0] = taxPercentage;
		headerTaxValues[1] = taxType;
		logger.endTimer("getHeaderTaxPercentage");
		return headerTaxValues;
	}

	/**
	 * This method takes chargeAmount and taxPercentage as input and calculate
	 * taxAmount as chargeAmount* 100/(100+TaxPercentage). Here chargeAmount
	 * includes taxes
	 *
	 * @param dChargeAmount
	 * @param taxPercentage
	 * @return
	 */
	public static double getTaxAmountFromPercentage(double dChargeAmount, double taxPercentage,double dTotalTaxPct) {
		logger.beginTimer("getTaxAmountFromPercentage");
		double taxAmount = (dChargeAmount * taxPercentage) / (100 + dTotalTaxPct);
		logger.verbose("Returning taxAmount as: " + taxAmount);
		logger.endTimer("getTaxAmountFromPercentage");
		return taxAmount;
	}

	/**
	 * GST change
	 * This method gets the extended tax percentage and type for Header charges for GST (i.e. shipping or
	 * gift wrapping charges) Logic is as: Max of sum(linetax) percentage value is taken for
	 * all the items used in the order/ invoice (tax percentage for items are
	 * added in LineTaxes/LineTax) During invoicing.
	 * if it is other than first invoice then it should not calculate the
	 * HeaderTax percentage value
	 * @param env
	 * @param eleRootIn
	 * @param isInvoicingEvent
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public static Object[] getExtHdrTaxPercentAndType(YFSEnvironment env, Element eleRootIn,
			boolean isInvoicingEvent) throws IllegalArgumentException, Exception {
		logger.beginTimer("getHeaderTaxPercentage");
		if (logger.isVerboseEnabled()) {
			logger.verbose(
					"Going to fetch line tax details from input element i.e. " + XMLUtil.getXmlString(eleRootIn));
		}
		//default tax percentage
		double dDefaultTaxPct = 0;
		//This array stores taxPercentage and taxType in order e.g) TaxPect1,TaxName1,TaxPct2,TaxName2
		Object[] headerTaxValues = {dDefaultTaxPct,"",dDefaultTaxPct,""}; 
		//Nodelist for Order or invoice lines
		NodeList nlLines = null;
		if (isInvoicingEvent) {
			nlLines = XPathAPI.selectNodeList(eleRootIn, "InvoiceHeader/LineDetails/LineDetail");
		} else {
			nlLines = XPathAPI.selectNodeList(eleRootIn, "OrderLines/OrderLine");
		}
		//Variable to store previous line's consolidated tax percentage
		double dPrvTotalTaxPect=0;
		//iterating through lines
		for(int iline=0;iline<nlLines.getLength();iline++)
		{
			//variable to store current line's consolidated tax percentage
			double dCurTotalTaxPect=0;
			//Nodelist for line Added predicate for JIRA-ABOF-13470
			NodeList nlLineTax = XPathAPI.selectNodeList(nlLines.item(iline),"LineTaxes/LineTax[@TaxPercentage > 0]");
			//Array to hold current line's tax informations.Currently maximum of 2 LineTax elements can come under LineTaxes
			Object [][] lineTaxInfo = new Object[2][2];
			//Iterating through LineTax elements
			for (int iLineTax = 0; iLineTax < nlLineTax.getLength(); iLineTax++) 
			{
				Element lineTaxEle = (Element) nlLineTax.item(iLineTax);
				if (logger.isVerboseEnabled())
				{
					logger.verbose("Going to fetch line tax details from lineTaxEle i.e. " + XMLUtil.getXmlString(lineTaxEle));
				}
				String lineTaxPercentage = lineTaxEle.getAttribute(FinanceConstants.TAX_PERCENTAGE);
				String lineTaxName = lineTaxEle.getAttribute(FinanceConstants.REFERENCE_1);
				//if percentage is not void then persist the tax percentage and tax name else set default 0 and ""
				if (!YFCCommon.isVoid(lineTaxPercentage)) 
				{
					double dLineTaxPercentage = Double.valueOf(lineTaxPercentage);
					dCurTotalTaxPect = dCurTotalTaxPect + dLineTaxPercentage;
					lineTaxInfo[iLineTax][0]=dLineTaxPercentage;
					lineTaxInfo[iLineTax][1]=lineTaxName;
				}else
				{
					lineTaxInfo[iLineTax][0]=dDefaultTaxPct;
					lineTaxInfo[iLineTax][1]="";
				}
			}
			//if current line's consolidated tax percentage is greater,then replace in the output array	
			if (dCurTotalTaxPect > dPrvTotalTaxPect) 
			{
				dPrvTotalTaxPect = dCurTotalTaxPect;
				headerTaxValues[0]=lineTaxInfo[0][0];
				headerTaxValues[1]=lineTaxInfo[0][1];
				if (YFCObject.isVoid(lineTaxInfo[1][0]))
				{
					headerTaxValues[2]=dDefaultTaxPct;
					headerTaxValues[3]="";
				}else
				{
					headerTaxValues[2]=lineTaxInfo[1][0];
					headerTaxValues[3]=lineTaxInfo[1][1];
				}

				logger.verbose("Max Tax percentage value has changed.Tax percent1 :" + headerTaxValues[0] + " Tax Name1:"
						+ headerTaxValues[1]+"Tax percent2 :" + headerTaxValues[2] + " Tax Name2:"+ headerTaxValues[3]);
			}
		}
		if (logger.isVerboseEnabled())
		{
			logger.verbose("Returning ...Tax percent1 :" + headerTaxValues[0] + ",Tax Name1:"
					+ headerTaxValues[1]+",Tax percent2 :" + headerTaxValues[2] + ",Tax Name2:"+ headerTaxValues[3]);
		}
		logger.endTimer("getHeaderTaxPercentage");
		return headerTaxValues;
	}



}
