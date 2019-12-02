package com.abof.finance.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.abof.finance.utils.ABOFFinanceUtils;
import com.abof.finance.utils.FinanceConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;

public class ABOFROInvoiceDetailsOnCreation implements YIFCustomApi {

	@Override
	public void setProperties(Properties arg0) throws Exception {

	}

	private static YFCLogCategory logger = YFCLogCategory
			.instance(ABOFROInvoiceDetailsOnCreation.class);

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
	public Document publishROInvoiceDetails(YFSEnvironment env,
			Document inputDoc) throws IllegalArgumentException, Exception {
		logger.beginTimer("publishROInvoiceDetails");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for publishROInvoiceDetails"
					+ XMLUtil.getXmlString(inputDoc.getDocumentElement()));
		}
		Element eleRoot = inputDoc.getDocumentElement();
		String strName = eleRoot.getNodeName();
		boolean isInvoiceCreationEvent = true;
		boolean bDebitCourier = false;
		String sDebitCourier=FinanceConstants.NO;
		Document invoiceHeaderDoc = null;
		if (FinanceConstants.INVOICE_DETAIL.equalsIgnoreCase(strName)) {
			Element orderInvoiceEle = (Element) inputDoc.getDocumentElement()
					.getElementsByTagName(FinanceConstants.INVOICE_HEADER)
					.item(0);
			isInvoiceCreationEvent = false;
			invoiceHeaderDoc = sendInvoice(env, orderInvoiceEle,
					isInvoiceCreationEvent, bDebitCourier);
		} else {
			Element eleOrderInvoice = inputDoc.getDocumentElement();

			 sDebitCourier = eleOrderInvoice
					.getAttribute(FinanceConstants.DEBIT_COURIER);
			bDebitCourier = YFCCommon.equals(sDebitCourier,
					FinanceConstants.YES);
			String strInvoiceNo = eleOrderInvoice
					.getAttribute(FinanceConstants.INVOICE_NO);
			String strOrderInvKey = eleOrderInvoice
					.getAttribute(FinanceConstants.ORDER_INVOICE_KEY);
			isInvoiceCreationEvent = true;
			Document docGetOrderInvOutput = getOrderInvoiceDetails(env,
					strInvoiceNo, strOrderInvKey);
			Element orderInvoiceEle = (Element) docGetOrderInvOutput
					.getDocumentElement()
					.getElementsByTagName(FinanceConstants.INVOICE_HEADER)
					.item(0);
			invoiceHeaderDoc = sendInvoice(env, orderInvoiceEle,
					isInvoiceCreationEvent, bDebitCourier);
		}
		if (invoiceHeaderDoc == null) {
			logger.verbose("As input document is null, can't publish it to Finance");
			return inputDoc;
		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for publishROInvoiceDetails"
					+ XMLUtil.getXmlString(invoiceHeaderDoc
							.getDocumentElement()));
		}

		XMLUtil.invokeService(env,
				FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q,
				invoiceHeaderDoc);
		logger.endTimer("publishROInvoiceDetails");
		return inputDoc;
	}

	/**
	 * This method invokes service to get the Invoice details with
	 * OrderInvoiceKey using getOrderInvoiceDetails call
	 * 
	 * @param env
	 * @param strInvoiceNo
	 * @param strOrderInvKey
	 * @return
	 * @throws Exception
	 */
	public static Document getOrderInvoiceDetails(YFSEnvironment env,
			String strInvoiceNo, String strOrderInvKey) throws Exception {
		logger.beginTimer("getOrderInvoiceDetails");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input string for getOrderInvoiceDetails"
					+ strInvoiceNo);
		}
		Document docGetOrderInvInput = XMLUtil
				.createDocument("GetOrderInvoiceDetails");
		Element eleGetOrderInvRoot = docGetOrderInvInput.getDocumentElement();
		eleGetOrderInvRoot.setAttribute(FinanceConstants.ENTERPRISE_CODE,
				FinanceConstants.ENTERPRISE_CODE_VALUE);
		eleGetOrderInvRoot.setAttribute(FinanceConstants.INVOICE_KEY,
				strOrderInvKey);
		eleGetOrderInvRoot.setAttribute(FinanceConstants.INVOICE_NO,
				strInvoiceNo);
		Document docGetOrderInvOutput = XMLUtil.invokeService(env,
				"GetOrderInvoiceDetailsForRO", docGetOrderInvInput);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for getOrderInvoiceDetails"
					+ XMLUtil.getXmlString(docGetOrderInvOutput
							.getDocumentElement()));
		}
		logger.endTimer("getOrderInvoiceDetails");
		return docGetOrderInvOutput;
	}

	/**
	 * This method gives the order release details to fetch the airway bill
	 * number and shipment number
	 * 
	 * @param env
	 * @param strOrderHeaderKey
	 * @return
	 * @throws Exception
	 */
	private String[] getOrderReleaseDetails(YFSEnvironment env,
			String strOrderHeaderKey) throws Exception {
		logger.beginTimer("getOrderReleaseDetails");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input string for getOrderReleaseDetails"
					+ strOrderHeaderKey);
		}
		Document docGetReleaseList = XMLUtil.createDocument("OrderRelease");
		Element eleRootReleaseList = docGetReleaseList.getDocumentElement();
		eleRootReleaseList.setAttribute(FinanceConstants.ORDER_HEADER_KEY,
				strOrderHeaderKey);
		String strTemplate = "<OrderReleaseList><OrderRelease ReleaseNo='' ShipNode='' OrderReleaseKey='' SCAC=''><ShipNode ShipNode=''><ShipNodePersonInfo/></ShipNode><PersonInfoShipTo/><Extn /></OrderRelease></OrderReleaseList>";
		Document docGetReleaseListOut = XMLUtil.invokeAPI(env,
				"getOrderReleaseList", docGetReleaseList,
				XMLUtil.getDocument(strTemplate));
		Element eleReleaseOut = docGetReleaseListOut.getDocumentElement();
		Element eleRelease = (Element) eleReleaseOut.getElementsByTagName(
				"OrderRelease").item(0);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Release element for getOrderInvoiceDetails"
					+ XMLUtil.getXmlString(eleReleaseOut));
		}
		Element eleExtn = (Element) XPathAPI.selectSingleNode(eleReleaseOut,
				"OrderRelease/Extn");
		String[] strExtn = new String[8];
		if (YFCCommon.isVoid(eleExtn)) {
			logger.verbose("Did not find any Release. Its seems Lost in Transit case. Hence returning all release details as blank");
			strExtn[0] = "";
			strExtn[1] = "";
			strExtn[2] = "";
			strExtn[3] = "";
			strExtn[4] = "";
			strExtn[5] = "";
			return strExtn;
		}

		strExtn[0] = eleExtn.getAttribute("ExtnAirwayBillNo");
		strExtn[1] = eleRelease.getAttribute("SCAC");
		Element elePersonInfoShipTo = (Element) XPathAPI.selectSingleNode(
				eleReleaseOut, "OrderRelease/PersonInfoShipTo");
		String strShipping[] = ABOFFinanceUtils.strAddress(elePersonInfoShipTo);
		strExtn[2] = strShipping[0];
		strExtn[3] = strShipping[1];
		strExtn[4] = "";
		strExtn[5] = "";
		Node shipNodePersonInfo = XPathAPI.selectSingleNode(eleReleaseOut,
				"OrderRelease/ShipNode/ShipNodePersonInfo");
		if (!YFCCommon.isVoid(shipNodePersonInfo)) {
			Element eleShipNode = (Element) shipNodePersonInfo;
			strExtn[4] = eleShipNode.getAttribute(FinanceConstants.CITY)
					+ FinanceConstants.ADDRESS_SPACE
					+ eleShipNode.getAttribute(FinanceConstants.STATE);
		}
		if (!YFCCommon.isVoid(elePersonInfoShipTo)) {
			Element elePersonInfo = elePersonInfoShipTo;
			strExtn[5] = elePersonInfo.getAttribute(FinanceConstants.CITY)
					+ FinanceConstants.ADDRESS_SPACE
					+ elePersonInfo.getAttribute(FinanceConstants.STATE);
		}

		logger.endTimer("getOrderReleaseDetails");
		return strExtn;
	}

	/**
	 * This method is used to publish header level data
	 * 
	 * @param env
	 * @param orderInvoiceEle
	 * @param isInvoiceCreationEvent
	 * @param bDebitCourier
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	private Document sendInvoice(YFSEnvironment env, Element orderInvoiceEle,
			boolean isInvoiceCreationEvent, boolean bDebitCourier)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("sendInvoice");
		Element eleOrder = (Element) orderInvoiceEle.getElementsByTagName(
				FinanceConstants.ORDER).item(0);
		Element elePersonInfoBillTo = (Element) eleOrder.getElementsByTagName(
				FinanceConstants.PERSON_INFO_BILL_TO).item(0);
		Document invoiceHeaderDoc = XMLUtil
				.createDocument(FinanceConstants.INVOICE_HEADER);
		Element outputInvHeaderEle = invoiceHeaderDoc.getDocumentElement();
		String strBilling[] = ABOFFinanceUtils.strAddress(elePersonInfoBillTo);
		outputInvHeaderEle.setAttribute(FinanceConstants.BILLING_NAME,
				strBilling[0]);
		outputInvHeaderEle.setAttribute(FinanceConstants.BILLING_ADDRESS,
				strBilling[1]);
		outputInvHeaderEle.setAttribute(FinanceConstants.BILLING_CITY,
				strBilling[2]);
		outputInvHeaderEle.setAttribute(FinanceConstants.BILLING_STATE,
				strBilling[3]);
		outputInvHeaderEle.setAttribute(FinanceConstants.BILLING_COUNTRY,
				strBilling[4]);
		outputInvHeaderEle.setAttribute(FinanceConstants.BILLING_ZIPCODE,
				strBilling[5]);

		String strOrderHeaderKey = eleOrder.getAttribute("OrderHeaderKey");
		// FI-67 Fix start
		// stores the entry - OrderLineKey with Qty associated to QC_Passed,
		// QC_Failed and Req_Inspct
		// Qty
		List<Map<String, Double>> listOfOrderLineKeyDispCodeMap = updateReturnDispositionCode(
				env, strOrderHeaderKey, outputInvHeaderEle);

		// FI-67 Fix end

		String[] strExtn = getOrderReleaseDetails(env, strOrderHeaderKey);
		outputInvHeaderEle.setAttribute(FinanceConstants.AWB_NO_FOR_CAMEL,
				strExtn[0]);
		outputInvHeaderEle.setAttribute(FinanceConstants.CARRIER_NAME,
				strExtn[1]);

		Element elePersonInfoShipTo = null;
		if (isInvoiceCreationEvent) {
			outputInvHeaderEle.setAttribute(FinanceConstants.SHIPPING_NAME,
					strExtn[2]);
			outputInvHeaderEle.setAttribute(FinanceConstants.SHIPPING_ADDRESS,
					strExtn[3]);
		} else {
			elePersonInfoShipTo = (Element) XPathAPI.selectSingleNode(
					orderInvoiceEle,
					"LineDetails/LineDetail/OrderLine/PersonInfoShipTo");
			String strShipping[] = ABOFFinanceUtils
					.strAddress(elePersonInfoShipTo);
			outputInvHeaderEle.setAttribute(FinanceConstants.SHIPPING_NAME,
					strShipping[0]);
			outputInvHeaderEle.setAttribute(FinanceConstants.SHIPPING_ADDRESS,
					strShipping[1]);
			outputInvHeaderEle.setAttribute(FinanceConstants.SHIPPING_CITY,
					strBilling[2]);
			outputInvHeaderEle.setAttribute(FinanceConstants.SHIPPING_STATE,
					strBilling[3]);
			outputInvHeaderEle.setAttribute(FinanceConstants.SHIPPING_COUNTRY,
					strBilling[4]);
			outputInvHeaderEle.setAttribute(FinanceConstants.SHIPPING_ZIPCODE,
					strBilling[5]);
		}

		outputInvHeaderEle.setAttribute(FinanceConstants.SHIP_TO, strExtn[4]);
		outputInvHeaderEle.setAttribute(FinanceConstants.SHIP_FROM, strExtn[5]);

		outputInvHeaderEle.setAttribute(FinanceConstants.CREATETS,
				ABOFFinanceUtils.getCreateTimeStamp());
		outputInvHeaderEle.setAttribute(FinanceConstants.IS_PROCESSED,
				FinanceConstants.NO);
		setInvHeaderAttrFromInvoiceDetails(orderInvoiceEle, outputInvHeaderEle);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document after setInvHeaderAttrFromInvoiceDetails"
					+ XMLUtil.getXmlString(invoiceHeaderDoc
							.getDocumentElement()));
		}

		Set<String> soOrderLineKeyForRO = setInvHeaderAttrFromRODetails(env,
				strOrderHeaderKey, outputInvHeaderEle);
		// Check where strOrderHeaderKey is being used
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document after setInvHeaderAttrFromRODetails"
					+ XMLUtil.getXmlString(invoiceHeaderDoc
							.getDocumentElement()));
		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document after setInvHeaderAttrFromSOInvoiceDetails"
					+ XMLUtil.getXmlString(invoiceHeaderDoc
							.getDocumentElement()));
		}
		NodeList headerChargesNL = null;
		Map<String, Double> paymentMethodAmountMap = new HashMap<String, Double>();
		;

		String returnType = outputInvHeaderEle
				.getAttribute(FinanceConstants.RETURN_TYPE);
		boolean isLIT = false;

		if (!YFCCommon.isVoid(returnType)
				&& returnType.indexOf(FinanceConstants.LOST_IN_TRANSIT) >= 0) {
			isLIT = true;
		} else {
			isLIT = isReturnLostInTransit(env, strOrderHeaderKey);
		}

		// This map stores the OrderLine Key of each Shipment with corresponding
		// COD amount of that
		// shipment (of SO)
		// This is further used to get the COD amount of a Return order
		Map<String, Double> orderLineKeyCODAmountPerShipment = new HashMap<String, Double>();

		// This list of 1 element will store codAmount of return.
		List<Double> codAmountOfReturn = new ArrayList<Double>(1);

		if (logger.isVerboseEnabled()) {
			logger.verbose("Current value of valueOfSOnROnCOD:"
					+ orderLineKeyCODAmountPerShipment);
		}

		populateROInvoiceLinesDetails(env, orderInvoiceEle, outputInvHeaderEle,
				isLIT, soOrderLineKeyForRO, orderLineKeyCODAmountPerShipment,
				codAmountOfReturn, listOfOrderLineKeyDispCodeMap);

		// if (logger.isVerboseEnabled()) {
		logger.verbose("Updated value of valueOfSOnROnCOD:"
				+ orderLineKeyCODAmountPerShipment);
		// }
		headerChargesNL = XPathAPI.selectNodeList(orderInvoiceEle,
				"HeaderCharges/HeaderCharge");

		String strEleKey = "InvoiceHeaderCharge";
		boolean isInvoicingEvent = true;
		//GST start:
		//Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getHeaderTaxPercentageAndType(env, orderInvoiceEle.getOwnerDocument().getDocumentElement(),
		//				isInvoicingEvent);
		Object[] headerTaxPercentageAndType = ABOFFinanceUtils.getExtHdrTaxPercentAndType(env, 
				orderInvoiceEle.getOwnerDocument().getDocumentElement(),isInvoicingEvent);
		//GST end:
		populateHeaderChargesDuringInvoiceCreation(env, headerChargesNL,
				outputInvHeaderEle, strEleKey, headerTaxPercentageAndType);
		// check why this condition is being used also merge the LIT handling
		if (isInvoiceCreationEvent) {

			paymentMethodAmountMap = setInvoicePaymentDetails(env,
					orderInvoiceEle, outputInvHeaderEle,
					isInvoiceCreationEvent, codAmountOfReturn);
			if (logger.isVerboseEnabled()) {
				logger.verbose("output document after sendInvoice"
						+ XMLUtil.getXmlString(invoiceHeaderDoc
								.getDocumentElement()));
			}

		} else {

			paymentMethodAmountMap = setInvoicePaymentDetails(env,
					orderInvoiceEle, outputInvHeaderEle,
					isInvoiceCreationEvent, codAmountOfReturn);
		}
		outputInvHeaderEle.setAttribute(FinanceConstants.STORE_CREDIT_AMOUNT,
				"" + paymentMethodAmountMap.get("STORE_CREDIT"));
		outputInvHeaderEle.setAttribute(
				FinanceConstants.QWIKCILVER_WALLET_AMOUNT,
				""
						+ paymentMethodAmountMap
								.get(FinanceConstants.QWIKCILVER_WALLET));

		outputInvHeaderEle.setAttribute(FinanceConstants.PREPAID_AMOUNT, ""
				+ paymentMethodAmountMap.get("PREPAID"));
		outputInvHeaderEle.setAttribute(FinanceConstants.COD_AMOUNT, ""
				+ paymentMethodAmountMap.get("COD"));

		if (logger.isVerboseEnabled()) {
			logger.verbose("output document after sendInvoice"
					+ XMLUtil.getXmlString(invoiceHeaderDoc
							.getDocumentElement()));
		}

		String strDerFromOHKey = orderInvoiceEle
				.getAttribute(FinanceConstants.DERIVED_FROM_OH_KEY);
		ABOFFinanceUtils.setSalesOrderValues(env, strDerFromOHKey,
				outputInvHeaderEle);
		// setAssocSalesAtHeaderLevel(env, strDerFromOHKey, outputInvHeaderEle,
		// soOrderLineKeyForRO);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document after sendInvoice"
					+ XMLUtil.getXmlString(invoiceHeaderDoc
							.getDocumentElement()));
		}
		if (YFCCommon.equals(bDebitCourier, true)) {
			invoiceHeaderDoc.getDocumentElement().setAttribute(
					FinanceConstants.DOCUMENT_TYPE,
					FinanceConstants.DOCUMENT_TYPE_CLOSED);
		}
		logger.endTimer("sendInvoice");
		return invoiceHeaderDoc;
	}

	/**
	 * This method fetches the Receipt
	 * 
	 * @param env
	 * @param strOrderHeaderKey
	 * @param outputInvHeaderEle
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private List<Map<String, Double>> updateReturnDispositionCode(
			YFSEnvironment env, String strOrderHeaderKey,
			Element outputInvHeaderEle) throws IllegalArgumentException,
			Exception {
		// stores the entry - OrderLineKey with Qty associated to QC_Passed,
		// QC_Failed and Req_Inspct
		// Qty
		logger.beginTimer("updateReturnDispositionCode");
		List<Map<String, Double>> listOfOrderLineKeyDispCodeMap = new ArrayList<Map<String, Double>>(
				3);

		Map<String, Double> orderLineKeyQCPassedQty = new HashMap<String, Double>();
		Map<String, Double> orderLineKeyQCFailedQty = new HashMap<String, Double>();
		Map<String, Double> orderLineKeyReqInsQty = new HashMap<String, Double>();

		if (!YFCCommon.isVoid(strOrderHeaderKey)) {
			logger.verbose("Found valid OrderHeaderKey");
			Document getReceiptListInDoc = XMLUtil
					.getDocument("<Receipt><Order OrderHeaderKey='"
							+ strOrderHeaderKey + "'/></Receipt>");

			Document getReceiptListTempDoc = XMLUtil
					.getDocument("<ReceiptList><Receipt ReceiptHeaderKey='' ReceiptNo='' ReceivingNode='' ShipmentKey=''><ReceiptLines><ReceiptLine  DispositionCode='' Quantity='' ><OrderLine OrderLineKey=''/></ReceiptLine></ReceiptLines></Receipt></ReceiptList>");

			Document getReceiptListOpDoc = XMLUtil.invokeAPI(env,
					FinanceConstants.GET_RECEIPT_LIST_API, getReceiptListInDoc,
					getReceiptListTempDoc);
			if (logger.isVerboseEnabled()) {
				logger.verbose("input document for getReceiptList"
						+ XMLUtil.getXmlString(getReceiptListInDoc
								.getDocumentElement()));
				logger.verbose("template document for getReceiptList"
						+ XMLUtil.getXmlString(getReceiptListTempDoc
								.getDocumentElement()));
				logger.verbose("output document for getReceiptList"
						+ XMLUtil.getXmlString(getReceiptListOpDoc
								.getDocumentElement()));
			}
			List<Element> receiptLineEleList = XMLUtil.getElementsByXpath(
					getReceiptListOpDoc,
					"//ReceiptList/Receipt/ReceiptLines/ReceiptLine");
			for (int i = 0; i < receiptLineEleList.size(); i++) {
				Element receiptLineEle = receiptLineEleList.get(i);
				String dispCode = receiptLineEle
						.getAttribute(FinanceConstants.DISPOSITION_CODE);
				String qty = receiptLineEle
						.getAttribute(FinanceConstants.QUANTITY);
				double dQty = YFCCommon.isVoid(qty) ? 0.0 : Double.valueOf(qty);
				if (logger.isVerboseEnabled()) {
					logger.verbose("Found DispositionCode:" + dispCode
							+ " Qty:" + dQty);
					logger.verbose("For ReceiptLine:"
							+ XMLUtil.getXmlString(receiptLineEle));
				}

				Element orderLineEle = (Element) XPathAPI.selectSingleNode(
						receiptLineEle, FinanceConstants.ORDER_LINE);
				if (dQty > 0 && !YFCCommon.isVoid(orderLineEle)) {
					String orderLineKey = orderLineEle
							.getAttribute(FinanceConstants.ORDER_LINE_KEY);
					logger.verbose("Found DispositionCode:" + dispCode
							+ " Qty:" + dQty + " OrderLineKey:" + orderLineKey);
					if (!YFCCommon.isVoid(orderLineKey)) {
						if (FinanceConstants.DISP_CODE_QC_PASSED
								.equalsIgnoreCase(dispCode)) {
							double currentQty = orderLineKeyQCPassedQty
									.containsKey(orderLineKey) ? orderLineKeyQCPassedQty
									.get(orderLineKey) : 0.0;
							dQty = dQty + currentQty;
							logger.verbose("Going to insert in QCPassed Map as: OrderLineKey"
									+ orderLineKey + " Qty:" + dQty);
							orderLineKeyQCPassedQty.put(orderLineKey, dQty);
						}
						if (FinanceConstants.DISP_CODE_QC_FAILED
								.equalsIgnoreCase(dispCode)) {
							double currentQty = orderLineKeyQCFailedQty
									.containsKey(orderLineKey) ? orderLineKeyQCFailedQty
									.get(orderLineKey) : 0.0;
							dQty = dQty + currentQty;
							logger.verbose("Going to insert in QCFailed Map as: OrderLineKey"
									+ orderLineKey + " Qty:" + dQty);
							orderLineKeyQCFailedQty.put(orderLineKey, dQty);
						}
						if (FinanceConstants.DISP_CODE_REQ_INS
								.equalsIgnoreCase(dispCode)) {
							double currentQty = orderLineKeyReqInsQty
									.containsKey(orderLineKey) ? orderLineKeyReqInsQty
									.get(orderLineKey) : 0.0;
							dQty = dQty + currentQty;
							logger.verbose("Going to insert in ReqInsp Map as: OrderLineKey"
									+ orderLineKey + " Qty:" + dQty);
							orderLineKeyReqInsQty.put(orderLineKey, dQty);
						}
					}
				}
			}
		}
		listOfOrderLineKeyDispCodeMap.add(0, orderLineKeyQCPassedQty);
		listOfOrderLineKeyDispCodeMap.add(1, orderLineKeyQCFailedQty);
		listOfOrderLineKeyDispCodeMap.add(2, orderLineKeyReqInsQty);
		if (logger.isVerboseEnabled()) {
			logger.verbose("Returing the map as: orderLineKeyQCPassedQty:"
					+ orderLineKeyQCPassedQty + " orderLineKeyQCFailedQty:"
					+ orderLineKeyQCFailedQty + " orderLineKeyReqInsQty:"
					+ orderLineKeyReqInsQty);
			logger.verbose("And list listOfOrderLineKeyDispCodeMap:"
					+ listOfOrderLineKeyDispCodeMap);
		}
		logger.endTimer("updateReturnDispositionCode");
		return listOfOrderLineKeyDispCodeMap;
	}

	/**
	 * In case of Return Lost in Transit, OrderType of return orde is not
	 * updated with Return Lost in Transit value. So getAuditList is called to
	 * make sure that the Return Order had the status Return Lost In Transit in
	 * its life cycle
	 * 
	 * @param env
	 * @param strOrderHeaderKey
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private boolean isReturnLostInTransit(YFSEnvironment env,
			String strOrderHeaderKey) throws IllegalArgumentException,
			Exception {
		logger.beginTimer("isReturnLostInTransit");
		if (YFCCommon.isVoid(strOrderHeaderKey)) {
			logger.verbose("Did not find OrderHeaderKey. So returning false");
			return false;
		}
		Document orderAuditInDoc = XMLUtil
				.createDocument(FinanceConstants.ORDER_AUDIT);
		Element orderAuditInEle = orderAuditInDoc.getDocumentElement();
		orderAuditInEle.setAttribute(FinanceConstants.ORDER_HEADER_KEY,
				strOrderHeaderKey);
		orderAuditInEle.setAttribute(FinanceConstants.REASON_CODE,
				FinanceConstants.Return_LIT_REASON_CODE);
		Document getAuditListTempDoc = XMLUtil
				.getDocument("<OrderAuditList LastOrderAuditKey='' ><OrderAudit OrderAuditKey='' OrderHeaderKey='' ReasonCode='' /></OrderAuditList>");

		Document getAuditListOpDoc = XMLUtil.invokeAPI(env,
				FinanceConstants.GET_ORDER_AUDIT_LIST, orderAuditInDoc,
				getAuditListTempDoc);
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for getOrderAuditList"
					+ XMLUtil.getXmlString(orderAuditInDoc.getDocumentElement()));
			logger.verbose("template document for getOrderAuditList"
					+ XMLUtil.getXmlString(getAuditListTempDoc
							.getDocumentElement()));
			logger.verbose("output document for getOrderAuditList"
					+ XMLUtil.getXmlString(getAuditListOpDoc
							.getDocumentElement()));
		}
		Element auditListEle = getAuditListOpDoc.getDocumentElement();
		Node orderAuditEle = XPathAPI.selectSingleNode(auditListEle,
				"OrderAudit");
		if (!YFCCommon.isVoid(orderAuditEle)) {
			logger.verbose("Found one Order Audit. So returning true");
			return true;
		}
		logger.verbose("Did not find Order Audit. So returning false");
		logger.endTimer("isReturnLostInTransit");
		return false;
	}

	/**
	 * This method takes the list of HeaderCharges and HeaderTaxes and stamp the
	 * HeaderCharges and corresponding tax details to Invoice document (output
	 * document)
	 * 
	 * @param env
	 * @param headerChargesNL
	 * @param headerTaxesNL
	 * @param referenceKey
	 * @param outputInvHeaderEle
	 * @param headerTaxPercentageAndType
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public static void populateHeaderChargesDuringInvoiceCreation(
			YFSEnvironment env, NodeList headerChargesNL,
			Element outputInvHeaderEle, String strEleKey,
			Object[] headerTaxPercentageAndType)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("populateHeaderChargesDuringInvoiceCreation");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document after populateHeaderChargesDuringInvoiceCreation"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
		}
		if (YFCCommon.isVoid(headerChargesNL)) {
			return;
		}
		Document invoiceHeaderDoc = outputInvHeaderEle.getOwnerDocument();
		Element invoiceHeaderChargesEle = null;
		Element invoiceHeaderChargeEle = null;
		if (FinanceConstants.HEADER_CHARGE.equalsIgnoreCase(strEleKey)) {
			invoiceHeaderChargesEle = invoiceHeaderDoc
					.createElement(FinanceConstants.ORDER_HEADER_CHARGES);
		} else {
			invoiceHeaderChargesEle = invoiceHeaderDoc
					.createElement(FinanceConstants.INVOICE_HEADER_CHARGES);
		}
		outputInvHeaderEle.appendChild(invoiceHeaderChargesEle);

		for (int i = 0; i < headerChargesNL.getLength(); i++) {
			Element headerCharge = (Element) headerChargesNL.item(i);
			String strChargeName = headerCharge
					.getAttribute(FinanceConstants.CHARGE_NAME);
			if (FinanceConstants.HEADER_CHARGE.equalsIgnoreCase(strEleKey)) {
				invoiceHeaderChargeEle = invoiceHeaderDoc
						.createElement(FinanceConstants.ORDER_HEADER_CHARGE);
				invoiceHeaderChargesEle.appendChild(invoiceHeaderChargeEle);
				invoiceHeaderChargeEle
						.setAttribute(
								FinanceConstants.ORDER_HEADER_KEY,
								outputInvHeaderEle
										.getAttribute(FinanceConstants.ORDER_HEADER_KEY));
				invoiceHeaderChargeEle.setAttribute(
						FinanceConstants.CHARGE_TYPE, strChargeName);
			} else {
				invoiceHeaderChargeEle = invoiceHeaderDoc
						.createElement(FinanceConstants.INVOICE_HEADER_CHARGE);
				invoiceHeaderChargesEle.appendChild(invoiceHeaderChargeEle);
				invoiceHeaderChargeEle
						.setAttribute(
								FinanceConstants.INVOICE_HEADER_KEY,
								outputInvHeaderEle
										.getAttribute(FinanceConstants.INVOICE_HEADER_KEY));
				invoiceHeaderChargeEle.setAttribute(
						FinanceConstants.CHARGE_NAME, strChargeName);
			}
			String chargeAmount = headerCharge
					.getAttribute(FinanceConstants.CHARGE_AMOUNT);
			double dChargeAmount = YFCCommon.isVoid(chargeAmount) ? 0.0
					: Double.valueOf(chargeAmount);
			invoiceHeaderChargeEle.setAttribute(FinanceConstants.CHARGE_AMOUNT,
					chargeAmount);

			invoiceHeaderChargeEle
					.setAttribute(
							FinanceConstants.CHARGE_CATEGORY,
							headerCharge
									.getAttribute(FinanceConstants.CHARGE_CATEGORY));
			invoiceHeaderChargeEle.setAttribute(FinanceConstants.TAX_TYPE,
					(String) headerTaxPercentageAndType[1]);
			invoiceHeaderChargeEle.setAttribute(FinanceConstants.TAX_RATE, ""
					+ headerTaxPercentageAndType[0]);
			//GST Change start:
			double dTotalTaxPct=0.0;
			if(YFCObject.isVoid(headerTaxPercentageAndType[2]))
			{
				dTotalTaxPct=(Double)headerTaxPercentageAndType[0];
			}else
			{
				dTotalTaxPct=(Double)headerTaxPercentageAndType[0]+(Double) headerTaxPercentageAndType[2];
			}
			
			double taxAmount = ABOFFinanceUtils.getTaxAmountFromPercentage(
					dChargeAmount, (Double)headerTaxPercentageAndType[0],dTotalTaxPct);
			logger.verbose("Got the tax amount as:" + taxAmount);
			invoiceHeaderChargeEle.setAttribute(FinanceConstants.TAX_AMOUNT, ""
					+ taxAmount);

			if(!YFCObject.isVoid(headerTaxPercentageAndType[2]))
			{
			invoiceHeaderChargeEle.setAttribute(FinanceConstants.EXT_TAX_TYPE,
					(String) headerTaxPercentageAndType[3]);
			invoiceHeaderChargeEle.setAttribute(FinanceConstants.EXT_TAX_RATE, ""
					+ headerTaxPercentageAndType[2]);
			double extTaxAmount = ABOFFinanceUtils.getTaxAmountFromPercentage(
					dChargeAmount, (Double) headerTaxPercentageAndType[2],dTotalTaxPct);
			logger.verbose("Got the tax amount as:" + extTaxAmount);
			invoiceHeaderChargeEle.setAttribute(FinanceConstants.EXT_TAX_AMOUNT, ""
					+ extTaxAmount);
			}
			//GST Change end:

		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document after populateHeaderChargesDuringInvoiceCreation"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
		}
		logger.endTimer("populateHeaderChargesDuringInvoiceCreation");
	}

	/**
	 * This method sets the invoice payment details when return invoicing is
	 * performed
	 * 
	 * @param outputInvHeaderEle
	 * @param inputDoc
	 * @param env
	 * @param valueOfSOnROnCOD
	 * @throws Exception
	 */
	public static Map<String, Double> setInvoicePaymentDetails(
			YFSEnvironment env, Element orderInvoiceEle,
			Element outputInvHeaderEle, boolean isInvoiceCreationEvent,
			List<Double> codAmountOfReturn) throws Exception {
		logger.beginTimer("setInvoicePaymentDetails");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input element for setInvoicePaymentDetails"
					+ XMLUtil.getXmlString(orderInvoiceEle));
			logger.verbose("input element for setInvoicePaymentDetails"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
			logger.verbose("input condition for setInvoicePaymentDetails"
					+ isInvoiceCreationEvent);
		}
		Map<String, Double> paymentMethodAmountMap = new HashMap<String, Double>();

		double storeCreditPayment = 0.0;
		double prepaidPayment = 0.0;
		double codAmount = 0.0;
		double QwikcilverWalletAmount = 0.0;
		String returnType = outputInvHeaderEle
				.getAttribute(FinanceConstants.RETURN_TYPE);
		if (FinanceConstants.FAILED_DELIVERY.equalsIgnoreCase(returnType)
				|| FinanceConstants.LOST_IN_TRANSIT
						.equalsIgnoreCase(returnType)) {
			codAmount = codAmountOfReturn.get(0);
		}

		if (logger.isVerboseEnabled()) {
			logger.verbose("CODAmount:" + codAmountOfReturn
					+ " for return type:" + returnType);
		}
		// should be in send condition
		Document invoiceHeaderDoc = outputInvHeaderEle.getOwnerDocument();
		Element invoicePaymentsEle = invoiceHeaderDoc
				.createElement(FinanceConstants.INVOICE_PAYMENTS);
		outputInvHeaderEle.appendChild(invoicePaymentsEle);
		if (!isInvoiceCreationEvent) {
			NodeList collectionDtlsEleList = XPathAPI.selectNodeList(
					orderInvoiceEle, "CollectionDetails/CollectionDetail");
			boolean paymentDetailsUpdated = false;
			for (int i = 0; i < collectionDtlsEleList.getLength(); i++) {
				Element collectionDtlsEle = (Element) collectionDtlsEleList
						.item(i);
				if (logger.isVerboseEnabled()) {
					logger.verbose("Found collectionDtlsEleList"
							+ XMLUtil.getXmlString(collectionDtlsEle));
				}

				String strChargeType = collectionDtlsEle
						.getAttribute(FinanceConstants.CHARGE_TYPE);
				Element paymentMethodEle = (Element) XPathAPI.selectSingleNode(
						collectionDtlsEle, FinanceConstants.PAYMENT_METHOD);
				/* Start :JIRA ABOF-12303
				 * String amountCollected = collectionDtlsEle.getAttribute(FinanceConstants.DISTRIBUTED_AMOUNT);
				double dblAmtCollected = Double.parseDouble(amountCollected)* -1;
				amountCollected = String.valueOf(dblAmtCollected);
				*/
				String amountCollected = collectionDtlsEle.getAttribute(FinanceConstants.AMOUNT_COLLECTED);
				//End :JIRA ABOF-12303
				
				String paymentType = "";

				Element invoicePaymentEle = invoiceHeaderDoc
						.createElement(FinanceConstants.INVOICE_PAYMENT);
				invoicePaymentsEle.appendChild(invoicePaymentEle);
				if (!YFCCommon.isVoid(strChargeType)
						&& strChargeType.indexOf("TRANSFER_OUT") >= 0) {
					paymentType = "ADJUSTED_TO_EXCHANGE";
				} else if (!YFCCommon.isVoid(strChargeType)
						&& strChargeType.indexOf("TRANSFER_IN") >= 0) {
					paymentType = "ADJUSTED_TO_RETURN";
				} else if (!YFCCommon.isVoid(paymentMethodEle)) {
					paymentType = paymentMethodEle
							.getAttribute(FinanceConstants.PAYMENT_TYPE);
					String creditCardType = paymentMethodEle
							.getAttribute(FinanceConstants.CREDIT_CARD_TYPE);
					double dAmountCollected = YFCCommon.isVoid(amountCollected) ? 0.0
							: Double.valueOf(amountCollected);
					if ("STORE_CREDIT".equalsIgnoreCase(paymentType)) {
						storeCreditPayment = storeCreditPayment
								+ dAmountCollected;
					} else if (FinanceConstants.QWIKCILVER_WALLET
							.equalsIgnoreCase(paymentType)) {
						QwikcilverWalletAmount = QwikcilverWalletAmount
								+ dAmountCollected;
				//		System.out.println("QwikcilverWalletAmount: Condition"+QwikcilverWalletAmount);
					} else {
						prepaidPayment = prepaidPayment + dAmountCollected;
					}
					invoicePaymentEle.setAttribute(
							FinanceConstants.PAYMENT_TENDER, creditCardType);
					invoicePaymentEle
							.setAttribute(
									FinanceConstants.PAYMENT_GATEWAY,
									paymentMethodEle
											.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SIX));
					invoicePaymentEle
							.setAttribute(
									FinanceConstants.PAYMENT_REFERENCE,
									paymentMethodEle
											.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SEVEN));
				}

				invoicePaymentEle
						.setAttribute(
								FinanceConstants.INVOICE_HEADER_KEY,
								outputInvHeaderEle
										.getAttribute(FinanceConstants.INVOICE_HEADER_KEY));
				invoicePaymentEle.setAttribute(
						FinanceConstants.AMOUNT_RECEIVED, amountCollected);
				invoicePaymentEle.setAttribute(FinanceConstants.PAYMENT_TYPE,
						paymentType);

				invoicePaymentEle.setAttribute(FinanceConstants.PAYMENT_MODE,
						FinanceConstants.PREPAID);

				paymentDetailsUpdated = true;
			}
			if (!paymentDetailsUpdated) {
				if (logger.isVerboseEnabled()) {
					logger.verbose("Could not found collection details element so setting all payments to Adjusted to Exchange");
				}
				Element invoicePaymentEle = invoiceHeaderDoc
						.createElement(FinanceConstants.INVOICE_PAYMENT);
				invoicePaymentsEle.appendChild(invoicePaymentEle);
				invoicePaymentEle
						.setAttribute(
								FinanceConstants.INVOICE_HEADER_KEY,
								outputInvHeaderEle
										.getAttribute(FinanceConstants.INVOICE_HEADER_KEY));
				Double dblAmount = Double.parseDouble(orderInvoiceEle
						.getAttribute("TotalAmount")) * -1;
				invoicePaymentEle.setAttribute(
						FinanceConstants.AMOUNT_RECEIVED,
						String.valueOf(dblAmount));
				invoicePaymentEle.setAttribute(FinanceConstants.PAYMENT_TYPE,
						"ADJUSTED_TO_EXCHANGE");
				invoicePaymentEle.setAttribute(FinanceConstants.PAYMENT_MODE,
						FinanceConstants.PREPAID);
			}

		}

		if (isInvoiceCreationEvent) {
			if (logger.isVerboseEnabled()) {
				logger.verbose("This is happening during Invoice creation so making everything as Refund due");
			}
			String amountCollected = orderInvoiceEle
					.getAttribute("TotalAmount");
			double dAmountCollected = YFCCommon.isVoid(amountCollected) ? 0.0
					: Double.valueOf(amountCollected);
			double netAmount = dAmountCollected + codAmount;
			logger.verbose("netAmount:" + netAmount);
			if (netAmount != 0) {
				Element invoicePaymentEle = invoiceHeaderDoc
						.createElement(FinanceConstants.INVOICE_PAYMENT);
				invoicePaymentsEle.appendChild(invoicePaymentEle);
				invoicePaymentEle
						.setAttribute(
								FinanceConstants.INVOICE_HEADER_KEY,
								outputInvHeaderEle
										.getAttribute(FinanceConstants.INVOICE_HEADER_KEY));

				invoicePaymentEle.setAttribute(
						FinanceConstants.AMOUNT_RECEIVED, "" + netAmount);
				invoicePaymentEle.setAttribute(FinanceConstants.PAYMENT_TYPE,
						"REFUND_DUE");
				invoicePaymentEle.setAttribute(FinanceConstants.PAYMENT_MODE,
						FinanceConstants.PREPAID);
			}
		}

		if ((FinanceConstants.FAILED_DELIVERY.equalsIgnoreCase(returnType) || FinanceConstants.LOST_IN_TRANSIT
				.equalsIgnoreCase(returnType)) && codAmount > 0) {
			logger.verbose("FailedDelivery scenario");
			Element invoicePaymentEle = invoiceHeaderDoc
					.createElement(FinanceConstants.INVOICE_PAYMENT);
			invoicePaymentsEle.appendChild(invoicePaymentEle);
			invoicePaymentEle.setAttribute(FinanceConstants.INVOICE_HEADER_KEY,
					outputInvHeaderEle
							.getAttribute(FinanceConstants.INVOICE_HEADER_KEY));
			invoicePaymentEle.setAttribute(FinanceConstants.AMOUNT_RECEIVED, ""
					+ (-codAmount));
			invoicePaymentEle.setAttribute(FinanceConstants.PAYMENT_TYPE,
					FinanceConstants.COD);
			invoicePaymentEle.setAttribute(FinanceConstants.PAYMENT_MODE,
					FinanceConstants.COD);
		}
		paymentMethodAmountMap.put("COD", codAmount);
		paymentMethodAmountMap.put("STORE_CREDIT", storeCreditPayment);
		paymentMethodAmountMap.put(FinanceConstants.QWIKCILVER_WALLET,
				QwikcilverWalletAmount);
			paymentMethodAmountMap.put("PREPAID", prepaidPayment);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output element for setInvoicePaymentDetails"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
			logger.verbose("output map for setInvoicePaymentDetails"
					+ paymentMethodAmountMap);
		}
		logger.endTimer("setInvoicePaymentDetails");
		return paymentMethodAmountMap;
	}

	/**
	 * Based upon serialNos and other line level invoice details, this method
	 * creates InvoiceLine to publish the details to financial system
	 * 
	 * @param env
	 * @param orderInvoiceEle
	 * @param outputInvHeaderEle
	 * @param derivedFromOLKeyCOGSMap
	 * @param isLIT
	 * @param soOrderLineKeyForRO
	 * @param codAmountOfReturn
	 * @param listOfOrderLineKeyDispCodeMap
	 * @param orderLineKeyCarrierDtlsPerShipment
	 * @param valueOfSOnROnCOD
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private void populateROInvoiceLinesDetails(YFSEnvironment env,
			Element orderInvoiceEle, Element outputInvHeaderEle, boolean isLIT,
			Set<String> soOrderLineKeyForRO,
			Map<String, Double> orderLineKeyCODAmountPerShipment,
			List<Double> codAmountOfReturn,
			List<Map<String, Double>> listOfOrderLineKeyDispCodeMap)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("populateROInvoiceLinesDetails");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input element for populateROInvoiceLinesDetails"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
			logger.verbose("input element for populateROInvoiceLinesDetails"
					+ XMLUtil.getXmlString(orderInvoiceEle));
			logger.verbose("boolean input for populateROInvoiceLinesDetails"
					+ isLIT);
		}

		// Map<String, String> itemIDDescriptionMap = new HashMap<String,
		// String>();

		String strDerFromOHKey = orderInvoiceEle
				.getAttribute(FinanceConstants.DERIVED_FROM_OH_KEY);
		Map<String, String> orderLineKeyCarrierDtlsPerShipment = new HashMap<String, String>();
		Map<String, Double> orderLineKeyCOGSMap = ABOFFinanceUtils
				.getCOGSForOrderLinesForReturn(env, strDerFromOHKey,
						orderLineKeyCODAmountPerShipment,
						orderLineKeyCarrierDtlsPerShipment);
		NodeList nlLineDetails = XPathAPI.selectNodeList(orderInvoiceEle,
				"LineDetails/LineDetail");

		if (logger.isVerboseEnabled()) {
			logger.verbose("set of Sales OrderLineKey for RO"
					+ soOrderLineKeyForRO);
		}
		List<Map<String, HashSet<String>>> listOfHashMap = ABOFFinanceUtils
				.getAssSalesInvoiceNos(env, strDerFromOHKey,
						outputInvHeaderEle, soOrderLineKeyForRO);

		Map<String, HashSet<String>> hmInvoiceNo = listOfHashMap.get(0);
		Map<String, HashSet<String>> hmDateInvoiced = listOfHashMap.get(1);

		setAssocSalesAtHeaderLevel(env, strDerFromOHKey, outputInvHeaderEle,
				soOrderLineKeyForRO, listOfHashMap);
		// Process to fetch item details such as Total unit cost, no. of qty in
		// PO, description ends

		Document invoiceHeaderDoc = outputInvHeaderEle.getOwnerDocument();
		Element invoiceLinesEle = invoiceHeaderDoc
				.createElement(FinanceConstants.INVOICE_LINES);
		outputInvHeaderEle.appendChild(invoiceLinesEle);
		// Process to stamp line level details at InvoiceLine (output to
		// financial system) starts

		double codReturnAmount = 0.0;
		String carrierDtls = "";
		// List<String> derivedFromOLKey =
		// getListOfDerivedFromOLKey(nlLineDetails);
		Map<String, String> roOLKeyAndDFOLKeyMap = new HashMap<String, String>();
		String returnType = outputInvHeaderEle
				.getAttribute(FinanceConstants.RETURN_TYPE);
		for (int i = 0; i < nlLineDetails.getLength(); i++) {
			Element lineDetailEle = (Element) nlLineDetails.item(i);
			Element eleOrderLine = (Element) lineDetailEle
					.getElementsByTagName(FinanceConstants.ORDER_LINE).item(0);
			Element invoiceLineEle = invoiceHeaderDoc
					.createElement(FinanceConstants.INVOICE_LINE);
			String invoiceNo = orderInvoiceEle
					.getAttribute(FinanceConstants.INVOICE_NO);
			String primeLineNo = eleOrderLine
					.getAttribute(FinanceConstants.PRIME_LINE_NO);
			invoiceLinesEle.appendChild(invoiceLineEle);
			String strOLKey = eleOrderLine
					.getAttribute(FinanceConstants.DERIVED_FROM_OL_KEY);
			String returnOLKey = eleOrderLine
					.getAttribute(FinanceConstants.ORDER_LINE_KEY);
			if (YFCCommon.isVoid(strOLKey) && roOLKeyAndDFOLKeyMap.isEmpty()) {
				roOLKeyAndDFOLKeyMap = getOrderLineKeyVsDerivedFromOLKeyForRO(
						env, orderInvoiceEle);
			}
			if (YFCCommon.isVoid(strOLKey)) {

				if (!YFCCommon.isVoid(returnOLKey)) {
					strOLKey = roOLKeyAndDFOLKeyMap.get(returnOLKey);
				}
			}

			Double dblCOGS = orderLineKeyCOGSMap.get(strOLKey);
			if (dblCOGS == null) {
				dblCOGS = 0.0;
			}
			if (logger.isVerboseEnabled()) {
				logger.verbose("currently codReturnAmount:" + codReturnAmount
						+ " for DerivedFromOrderLineKey:" + strOLKey);
			}
			if (codReturnAmount == 0.0
					&& orderLineKeyCODAmountPerShipment.containsKey(strOLKey)) {
				codReturnAmount = orderLineKeyCODAmountPerShipment
						.get(strOLKey);
				logger.verbose("Fetching COD Return Amount as:"
						+ codReturnAmount);
			}

			double qcPassedQty = 0.0;
			double qcFailedQty = 0.0;
			double reqInsQty = 0.0;
			if (!YFCCommon.isVoid(listOfOrderLineKeyDispCodeMap)) {
				logger.verbose("Going to fetch QC qty for orderLineKey:"
						+ returnOLKey);
				Map<String, Double> orderLineKeyQCPassedQty = listOfOrderLineKeyDispCodeMap
						.get(0);
				if (!YFCCommon.isVoid(orderLineKeyQCPassedQty)) {
					qcPassedQty = orderLineKeyQCPassedQty
							.containsKey(returnOLKey) ? orderLineKeyQCPassedQty
							.get(returnOLKey) : 0.0;
					logger.verbose("qcPassedQty:" + qcPassedQty);
				}

				Map<String, Double> orderLineKeyQCFailedQty = listOfOrderLineKeyDispCodeMap
						.get(1);
				if (!YFCCommon.isVoid(orderLineKeyQCFailedQty)) {
					qcFailedQty = orderLineKeyQCFailedQty
							.containsKey(returnOLKey) ? orderLineKeyQCFailedQty
							.get(returnOLKey) : 0.0;
					logger.verbose("qcFailedQty:" + qcFailedQty);
				}
				Map<String, Double> orderLineKeyReqInsQty = listOfOrderLineKeyDispCodeMap
						.get(2);
				if (!YFCCommon.isVoid(orderLineKeyReqInsQty)) {
					reqInsQty = orderLineKeyReqInsQty.containsKey(returnOLKey) ? orderLineKeyReqInsQty
							.get(returnOLKey) : 0.0;
					logger.verbose("reqInsQty:" + reqInsQty);
				}
			}
			invoiceLineEle.setAttribute(FinanceConstants.QC_PASSED_QTY, ""
					+ qcPassedQty);
			invoiceLineEle.setAttribute(FinanceConstants.QC_FAILED_QTY, ""
					+ qcFailedQty);
			invoiceLineEle.setAttribute(FinanceConstants.REQ_INS_QTY, ""
					+ reqInsQty);

			if ((FinanceConstants.FAILED_DELIVERY.equalsIgnoreCase(returnType) || FinanceConstants.LOST_IN_TRANSIT
					.equalsIgnoreCase(returnType))
					&& YFCCommon.isVoid(carrierDtls)) {
				if (!YFCCommon.isVoid(orderLineKeyCarrierDtlsPerShipment)) {

					carrierDtls = orderLineKeyCarrierDtlsPerShipment
							.get(strOLKey);
					int delimtersIndex = 0;
					if (!YFCCommon.isVoid(carrierDtls)) {
						delimtersIndex = carrierDtls.indexOf(':');
					}
					if (delimtersIndex > 0) {
						String awbNo = carrierDtls.substring(0, delimtersIndex);
						String scac = carrierDtls.substring(delimtersIndex + 1);
							outputInvHeaderEle.setAttribute(
								FinanceConstants.CARRIER_NAME, scac);
						outputInvHeaderEle.setAttribute(
								FinanceConstants.AWB_NO_FOR_CAMEL, awbNo);
					}
				}

			}

			if (!isLIT
					&& FinanceConstants.FAILED_DELIVERY
							.equalsIgnoreCase(returnType)) {
				isLIT = isRTOLostInTransit(env, strOLKey);
			}

			if (hmInvoiceNo.containsKey(strOLKey)) {
				HashSet<String> hsInvoiceNo = hmInvoiceNo.get(strOLKey);
				HashSet<String> hsDateInvoiced = hmDateInvoiced.get(strOLKey);
				Iterator<String> itr = hsInvoiceNo.iterator();
				Iterator<String> itrDate = hsDateInvoiced.iterator();
				StringBuilder sbInvoice = new StringBuilder();
				StringBuilder sbDate = new StringBuilder();
				String sep = "";
				String sepDate = "";
				while (itr.hasNext()) {
					sbInvoice.append(sep);
					sep = ",";
					sbInvoice.append(itr.next());
				}
				while (itrDate.hasNext()) {
					sbDate.append(sepDate);
					sepDate = ",";
					sbDate.append(itrDate.next());
				}
				invoiceLineEle.setAttribute(FinanceConstants.SALES_INVOICE_NO,
						sbInvoice.toString());
				invoiceLineEle.setAttribute(
						FinanceConstants.SALES_INVOICE_DATE, sbDate.toString());
			}
			invoiceLineEle.setAttribute(FinanceConstants.INVOICE_LINE_KEY,
					ABOFFinanceUtils.getPrimaryKey(invoiceNo + primeLineNo));
			invoiceLineEle.setAttribute(FinanceConstants.INVOICE_HEADER_KEY,
					outputInvHeaderEle
							.getAttribute(FinanceConstants.INVOICE_HEADER_KEY));

			// Element eleItem = (Element)
			// XPathAPI.selectSingleNode(lineDetailEle, "OrderLine/Item");
			Element eleItemDetails = (Element) XPathAPI.selectSingleNode(
					lineDetailEle, "OrderLine/ItemDetails");
			if (!YFCCommon.isVoid(eleItemDetails)) {
				String itemID = eleItemDetails.getAttribute("ItemID");
				invoiceLineEle.setAttribute(FinanceConstants.SKU_CODE, itemID);
			}
			invoiceLineEle.setAttribute(FinanceConstants.MRP, XPathAPI
					.selectSingleNode(eleOrderLine, "LinePriceInfo/@UnitPrice")
					.getNodeValue());
			invoiceLineEle.setAttribute(FinanceConstants.PRIME_LINE_NO,
					primeLineNo);
			if (!YFCCommon.isVoid(eleItemDetails)) {
				Element elePrimeInfo = (Element) eleItemDetails
						.getElementsByTagName("PrimaryInformation").item(0);
				invoiceLineEle.setAttribute(FinanceConstants.PRODUCT_DESC,
						elePrimeInfo.getAttribute("ShortDescription"));
			}
			String qty = lineDetailEle
					.getAttribute(FinanceConstants.SHIPPED_QTY);
			invoiceLineEle.setAttribute(FinanceConstants.QUANTITY, qty);
			/*GST Start:
			if (null != XPathAPI.selectSingleNode(lineDetailEle,
					"LineTaxes/LineTax")) {
				NodeList nlLineTaxes = XPathAPI.selectNodeList(lineDetailEle,
						"LineTaxes/LineTax");
				for (int j = 0; j < nlLineTaxes.getLength(); j++) {
					Element lineTaxEle = (Element) nlLineTaxes.item(j);
					String taxName = lineTaxEle
							.getAttribute(FinanceConstants.REFERENCE_1);
					String taxPercentage = lineTaxEle
							.getAttribute(FinanceConstants.TAX_PERCENTAGE);
					invoiceLineEle.setAttribute(FinanceConstants.TAX_TYPE,
							taxName);
					invoiceLineEle.setAttribute(FinanceConstants.TAX_RATE,
							taxPercentage);
					invoiceLineEle
							.setAttribute(
									FinanceConstants.TAX_AMOUNT,
									ABOFFinanceUtils.getLineTax(
											String.valueOf(Double.parseDouble(lineDetailEle
													.getAttribute(FinanceConstants.LINE_TOTAL))
													* -1), taxPercentage));
				}
			}*/
			//Fetching LineTax elements for OrderLine
			NodeList nlLineTax = XPathAPI.selectNodeList(lineDetailEle, "LineTaxes/LineTax");
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
					invoiceLineEle.setAttribute(FinanceConstants.EXT_TAX_TYPE,
							eleLineTax.getAttribute(FinanceConstants.REFERENCE_1));
					invoiceLineEle.setAttribute(FinanceConstants.EXT_TAX_RATE,
							eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
					invoiceLineEle.setAttribute(FinanceConstants.EXT_TAX_AMOUNT,
							ABOFFinanceUtils.getLineTax(String.valueOf(Double.parseDouble(lineDetailEle.
									getAttribute(FinanceConstants.LINE_TOTAL))
											* -1),eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE),Double.toString(dblTotalTaxPct)));
				}else
				{
					invoiceLineEle.setAttribute(FinanceConstants.TAX_TYPE,
							eleLineTax.getAttribute(FinanceConstants.REFERENCE_1));
					invoiceLineEle.setAttribute(FinanceConstants.TAX_RATE,
							eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
					invoiceLineEle.setAttribute(FinanceConstants.TAX_AMOUNT,
							ABOFFinanceUtils.getLineTax(String.valueOf(Double.parseDouble(lineDetailEle.
									getAttribute(FinanceConstants.LINE_TOTAL))
											* -1),eleLineTax.getAttribute(FinanceConstants.TAX_PERCENTAGE),Double.toString(dblTotalTaxPct)));
					
				}
			
			}
			//GST End:
			invoiceLineEle.setAttribute(FinanceConstants.COST_OF_GOODS_SOLD, ""
					+ dblCOGS);
			Double dblLineTotal = Double.parseDouble(lineDetailEle
					.getAttribute(FinanceConstants.LINE_TOTAL)) * -1;
			invoiceLineEle.setAttribute(FinanceConstants.LINE_TOTAL, ""
					+ dblLineTotal);
			Double dblUnitPrice = Double.parseDouble(lineDetailEle
					.getAttribute("UnitPrice")) * -1;
			invoiceLineEle.setAttribute("UnitPrice", "" + dblUnitPrice);
			if (isLIT) {
				invoiceLineEle.setAttribute("InventoryBucket", "LIT");
			} else {
				invoiceLineEle.setAttribute("InventoryBucket", "");
			}
			populateROInvoiceLineDiscounts(env, lineDetailEle, invoiceLineEle);
		}
		codAmountOfReturn.add(0, codReturnAmount);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output element for populateROInvoiceLinesDetails"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
		}
		// Process to stamp line level details at InvoiceLine (output to
		// financial system) ends
		logger.endTimer("populateROInvoiceLinesDetails");
	}

	/**
	 * This method uses DerivedFromOrderLineKey of RO i.e. OrderLineKey of the
	 * Sales Order and fetches the Shipment details (using getShipmentList) to
	 * check if it was RTO Lost In Transit case
	 * 
	 * @param env
	 * @param strOLKey
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private boolean isRTOLostInTransit(YFSEnvironment env, String strOLKey)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("isRTOLostInTransit");
		if (YFCCommon.isVoid(strOLKey)) {
			logger.verbose("strOLKey is blank so returning false");
			logger.endTimer("isRTOLostInTransit");
			return false;
		}
		Document shipmentInDoc = XMLUtil
				.getDocument("<Shipment><ShipmentLines><ShipmentLine OrderLineKey='"
						+ strOLKey + "'/></ShipmentLines></Shipment>");
		if (logger.isVerboseEnabled()) {
			logger.verbose("Now input document for getShipmentList to fetch RTO_LIT"
					+ XMLUtil.getXmlString(shipmentInDoc.getDocumentElement()));
		}
		// System.out.println("+++Inside
		// shipmentInDoc++++"+XMLUtil.getXmlString(shipmentInDoc.getDocumentElement()));
		Document shipmentListTempDoc = XMLUtil
				.getDocument("<Shipments><Shipment ShipmentKey=''><ShipmentStatusAudits><ShipmentStatusAudit  NewStatus='' NewStatusDate='' ></ShipmentStatusAudit></ShipmentStatusAudits></Shipment></Shipments>");

		if (logger.isVerboseEnabled()) {
			logger.verbose("template for getShipmentList to fetch RTO_LIT"
					+ XMLUtil.getXmlString(shipmentListTempDoc
							.getDocumentElement()));
		}

		Document shipmentListOpDoc = XMLUtil.invokeAPI(env,
				FinanceConstants.GET_SHIPMENT_LIST, shipmentInDoc,
				shipmentListTempDoc);
		Element shipmentListOpElement = shipmentListOpDoc.getDocumentElement();
		// System.out.println("+++Output
		// shipmentListOpElement++++"+XMLUtil.getXmlString(shipmentListOpDoc.getDocumentElement()));
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document from getShipmentList to fetch RTO_LIT"
					+ XMLUtil.getXmlString(shipmentListOpDoc
							.getDocumentElement()));
		}

		NodeList nlShipmentStatusAudit = XPathAPI.selectNodeList(
				shipmentListOpElement,
				"/Shipments/Shipment/ShipmentStatusAudits/ShipmentStatusAudit");

		for (int i = 0; i < nlShipmentStatusAudit.getLength(); i++) {
			Element shipmentStatusAudit = (Element) nlShipmentStatusAudit
					.item(i);
			String newStatus = shipmentStatusAudit
					.getAttribute(FinanceConstants.NEW_STATUS);
			logger.verbose("newStatus:" + newStatus);
			if (FinanceConstants.RTO_LIT_STATUS.equals(newStatus)) {
				logger.verbose("Found one RTO_LIT status");
				logger.endTimer("isRTOLostInTransit");
				return true;

			}
		}
		logger.endTimer("isRTOLostInTransit");
		return false;
	}

	/**
	 * This method returns OrderLineKey vs DerivedFromOrderLineKey for a RO
	 * 
	 * @param env
	 * @param orderInvoiceEle
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private Map<String, String> getOrderLineKeyVsDerivedFromOLKeyForRO(
			YFSEnvironment env, Element orderInvoiceEle)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("getOrderLineKeyVsDerivedFromOLKeyForRO");
		Map<String, String> roOLKeyAndDFOLKeyMap = new HashMap<String, String>();
		Node orderNode = XPathAPI.selectSingleNode(orderInvoiceEle, "Order");
		if (!YFCCommon.isVoid(orderNode)) {
			Element orderEle = (Element) orderNode;
			String orderHeaderKey = orderEle
					.getAttribute(FinanceConstants.ORDER_HEADER_KEY);
			if (YFCCommon.isVoid(orderHeaderKey)) {
				logger.verbose("Going to return map roOLKeyAndDFOLKeyMap as orderheader key is blank:"
						+ roOLKeyAndDFOLKeyMap);
				return roOLKeyAndDFOLKeyMap;
			}

			Document inDocGetOrderList = XMLUtil
					.createDocument(FinanceConstants.ORDER);
			Element inEleGetOrderList = inDocGetOrderList.getDocumentElement();
			inEleGetOrderList.setAttribute(FinanceConstants.ORDER_HEADER_KEY,
					orderHeaderKey);

			String tmplGetOrderList = "<OrderList><Order OrderHeaderKey=''><OrderLines><OrderLine OrderLineKey='' DerivedFromOrderLineKey=''/></OrderLines></Order></OrderList>";
			if (logger.isVerboseEnabled()) {
				logger.verbose("Calling inDocGetOrderList with input"
						+ XMLUtil.getXmlString(inDocGetOrderList
								.getDocumentElement()));
			}
			Document outDocGetOrderList = XMLUtil.invokeAPI(env,
					FinanceConstants.GET_ORDER_LIST, inDocGetOrderList,
					XMLUtil.getDocument(tmplGetOrderList));
			if (logger.isVerboseEnabled()) {
				logger.verbose("getOrderList output"
						+ XMLUtil.getXmlString(outDocGetOrderList
								.getDocumentElement()));
			}
			Element eleGetOrderList = outDocGetOrderList.getDocumentElement();
			NodeList nlOrderLines = XPathAPI.selectNodeList(eleGetOrderList,
					"Order/OrderLines/OrderLine");
			for (int i = 0; i < nlOrderLines.getLength(); i++) {
				Element orderLine = (Element) nlOrderLines.item(i);
				String orderLineKey = orderLine
						.getAttribute(FinanceConstants.ORDER_LINE_KEY);
				String derivedFromOLKey = orderLine
						.getAttribute(FinanceConstants.DERIVED_FROM_OL_KEY);
				roOLKeyAndDFOLKeyMap.put(orderLineKey, derivedFromOLKey);
			}
		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("Going to return map roOLKeyAndDFOLKeyMap:"
					+ roOLKeyAndDFOLKeyMap);
		}
		return roOLKeyAndDFOLKeyMap;
	}

	/**
	 * This method is used to get invoice number of the associated sales order
	 * 
	 * @param env
	 * @param strDerFromOHKey
	 * @param outputInvHeaderEle
	 * @param salesOrderLineKeyForRO
	 * @param listOfHashMap
	 * @throws Exception
	 */
	private void setAssocSalesAtHeaderLevel(YFSEnvironment env,
			String strDerFromOHKey, Element outputInvHeaderEle,
			Set<String> salesOrderLineKeyForRO,
			List<Map<String, HashSet<String>>> listOfHashMap) throws Exception {
		logger.beginTimer("setAssocSalesAtHeaderLevel");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input element for setAssocSalesAtHeaderLevel"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
			logger.verbose("input string for setAssocSalesAtHeaderLevel"
					+ strDerFromOHKey);
			logger.verbose("List of sales invoice no" + listOfHashMap);
		}

		Map<String, HashSet<String>> hmInvoiceNo = listOfHashMap.get(0);
		// Map<String, HashSet<String>> hmDateInvoiced = listOfHashMap.get(1);

		HashSet<String> setInvoiceNo = hmInvoiceNo.get("Key1");
		HashSet<String> setDateInvoiced = hmInvoiceNo.get("Key2");
		if (logger.isVerboseEnabled()) {
			logger.verbose("List of inoice no." + setInvoiceNo);
			logger.verbose("List of setDateInvoiced" + setDateInvoiced);
		}
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
		outputInvHeaderEle.setAttribute(FinanceConstants.SALES_INVOICE_DATE,
				String.valueOf(sbInvoiceDate));
		outputInvHeaderEle.setAttribute(FinanceConstants.SALES_INVOICE_NO,
				String.valueOf(sbSalesInvoice));
		if (logger.isVerboseEnabled()) {
			logger.verbose("output element for setAssocSalesAtHeaderLevel"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
		}
		logger.endTimer("setAssocSalesAtHeaderLevel");
	}

	/**
	 * This method populates the line discount (if > 0 ) to resepective invoice
	 * line
	 * 
	 * @param env
	 * @param lineDetailEle
	 * @param invoiceLineEle
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private void populateROInvoiceLineDiscounts(YFSEnvironment env,
			Element lineDetailEle, Element invoiceLineEle)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("populateROInvoiceLineDiscounts");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input element for populateROInvoiceLineDiscounts"
					+ XMLUtil.getXmlString(lineDetailEle));
			logger.verbose("input element for populateROInvoiceLineDiscounts"
					+ XMLUtil.getXmlString(invoiceLineEle));
		}
		NodeList nlLineCharges = XPathAPI.selectNodeList(lineDetailEle,
				"LineCharges/LineCharge");
		Document invoiceHeaderDoc = invoiceLineEle.getOwnerDocument();
		Element invoiceLineDiscountsEle = invoiceHeaderDoc
				.createElement(FinanceConstants.INVOICE_LINE_DISCOUNTS);
		invoiceLineEle.appendChild(invoiceLineDiscountsEle);

		Element invoiceLineChargesEle = invoiceHeaderDoc
				.createElement(FinanceConstants.INVOICE_LINE_CHARGES);
		invoiceLineEle.appendChild(invoiceLineChargesEle);

		for (int i = 0; i < nlLineCharges.getLength(); i++) {
			Element lineCharge = (Element) nlLineCharges.item(i);
			Double chargeAmount = Double.parseDouble(lineCharge
					.getAttribute(FinanceConstants.CHARGE_AMOUNT)) * -1;
			String strChargeCategory = lineCharge
					.getAttribute(FinanceConstants.CHARGE_CATEGORY);
			String strChargeName = lineCharge
					.getAttribute(FinanceConstants.CHARGE_NAME);
			// Double chargeAmount = YFCCommon.isVoid(strChargeAmount) ? 0.0 :
			// Double.valueOf(strChargeAmount);
			if (strChargeCategory.indexOf("Discount") >= 0 && chargeAmount != 0) {
				Element invoiceLineDiscountEle = invoiceHeaderDoc
						.createElement(FinanceConstants.INVOICE_LINE_DISCOUNT);
				invoiceLineDiscountsEle.appendChild(invoiceLineDiscountEle);
				invoiceLineDiscountEle
						.setAttribute(
								FinanceConstants.INVOICE_LINE_KEY,
								invoiceLineEle
										.getAttribute(FinanceConstants.INVOICE_LINE_KEY));
				invoiceLineDiscountEle.setAttribute(
						FinanceConstants.DISCOUNT_NAME, strChargeName);
				invoiceLineDiscountEle.setAttribute(
						FinanceConstants.DISCOUNT_AMOUNT, "" + chargeAmount);
				invoiceLineDiscountEle
						.setAttribute(
								FinanceConstants.INVOICE_HEADER_KEY,
								invoiceLineEle
										.getAttribute(FinanceConstants.INVOICE_HEADER_KEY));

			} else if (strChargeCategory.indexOf("VAT") >= 0
					&& chargeAmount != 0) {
				Element invoiceLineChargeEle = invoiceHeaderDoc
						.createElement(FinanceConstants.INVOICE_LINE_CHARGE);
				invoiceLineChargesEle.appendChild(invoiceLineChargeEle);
				invoiceLineChargeEle
						.setAttribute(
								FinanceConstants.INVOICE_LINE_KEY,
								invoiceLineEle
										.getAttribute(FinanceConstants.INVOICE_LINE_KEY));
				invoiceLineChargeEle.setAttribute(FinanceConstants.CHARGE_NAME,
						strChargeName);
				invoiceLineChargeEle.setAttribute(
						FinanceConstants.CHARGE_AMOUNT, "" + chargeAmount);
				invoiceLineChargeEle
						.setAttribute(
								FinanceConstants.INVOICE_HEADER_KEY,
								invoiceLineEle
										.getAttribute(FinanceConstants.INVOICE_HEADER_KEY));

			}
		}
		if (logger.isVerboseEnabled()) {
			logger.verbose("output element for populateROInvoiceLineDiscounts"
					+ XMLUtil.getXmlString(invoiceHeaderDoc
							.getDocumentElement()));
		}
		logger.endTimer("populateROInvoiceLineDiscounts");
	}

	/**
	 * This method fetches header level attributes from event document and
	 * populate the output header element
	 * 
	 * @param orderInvoiceEle
	 * @param outputInvHeaderEle
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private void setInvHeaderAttrFromInvoiceDetails(Element orderInvoiceEle,
			Element outputInvHeaderEle) throws IllegalArgumentException,
			Exception {
		logger.beginTimer("setInvHeaderAttrFromInvoiceDetails");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input element for setInvHeaderAttrFromInvoiceDetails"
					+ XMLUtil.getXmlString(orderInvoiceEle));
		}
		String invoiceNo = orderInvoiceEle
				.getAttribute(FinanceConstants.INVOICE_NO);
		outputInvHeaderEle.setAttribute(FinanceConstants.INVOICE_HEADER_KEY,
				ABOFFinanceUtils.getPrimaryKey(invoiceNo));
		outputInvHeaderEle.setAttribute(FinanceConstants.INVOICE_NO, invoiceNo);
		outputInvHeaderEle.setAttribute(FinanceConstants.DOCUMENT_TYPE, "RO");
		outputInvHeaderEle
				.setAttribute(FinanceConstants.INVOICE_TYPE, "Return");
		outputInvHeaderEle.setAttribute(FinanceConstants.INVOICE_VALUE,
				orderInvoiceEle.getAttribute(FinanceConstants.TOTAL_AMOUNT));
		outputInvHeaderEle.setAttribute(FinanceConstants.INVOICE_DATE,
				orderInvoiceEle.getAttribute(FinanceConstants.DATE_INVOICED));
		if (logger.isVerboseEnabled()) {
			logger.verbose("input element for setInvHeaderAttrFromInvoiceDetails"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
		}
		logger.endTimer("setInvHeaderAttrFromInvoiceDetails");
	}

	/**
	 * This method fetches return order details and populate header attributes
	 * (of output xml) with return order details. This also returns the list of
	 * Sales Order Line Key associated to this Return Order. This list of keys
	 * can be used further to fetch other required details
	 * 
	 * @param env
	 * @param orderHeaderKey
	 * @param outputInvHeaderEle
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	private Set<String> setInvHeaderAttrFromRODetails(YFSEnvironment env,
			String orderHeaderKey, Element outputInvHeaderEle)
			throws IllegalArgumentException, Exception {
		logger.beginTimer("setInvHeaderAttrFromRODetails");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input string for setInvHeaderAttrFromRODetails"
					+ orderHeaderKey);
		}
		Document getOrderListInput = XMLUtil
				.createDocument(FinanceConstants.ORDER);
		Element getOrderListInputEle = getOrderListInput.getDocumentElement();
		getOrderListInputEle.setAttribute(FinanceConstants.ORDER_HEADER_KEY,
				orderHeaderKey);
		Document getOrderListTemplate = XMLUtil
				.getDocument(FinanceConstants.GET_RETURN_ORDER_LIST_TEMP_FOR_RO_INVOICE);
		Document returnOrderList = XMLUtil.invokeAPI(env,
				FinanceConstants.GET_ORDER_LIST_API, getOrderListInput,
				getOrderListTemplate);

		Element orderListEle = returnOrderList.getDocumentElement();
		Element orderDtlsEle = (Element) orderListEle.getElementsByTagName(
				"Order").item(0);
		outputInvHeaderEle.setAttribute(FinanceConstants.ORDER_NO,
				orderDtlsEle.getAttribute(FinanceConstants.ORDER_NO));
		outputInvHeaderEle.setAttribute(FinanceConstants.ORDER_DATE,
				orderDtlsEle.getAttribute(FinanceConstants.ORDER_DATE));
		outputInvHeaderEle.setAttribute(FinanceConstants.RETURN_TYPE,
				orderDtlsEle.getAttribute(FinanceConstants.ORDER_TYPE));
		String strReturnReason = "";
		NodeList nlOrderLine = XPathAPI.selectNodeList(orderDtlsEle,
				"OrderLines/OrderLine");
		Set<String> derviedFromOLKey = new HashSet<String>();
		for (int i = 0; i < nlOrderLine.getLength(); i++) {
			Element eleOrderLine = (Element) nlOrderLine.item(i);
			strReturnReason = eleOrderLine
					.getAttribute(FinanceConstants.RETURN_REASON);
			String strDerivedFromOLKey = eleOrderLine
					.getAttribute(FinanceConstants.DERIVED_FROM_OL_KEY);
			derviedFromOLKey.add(strDerivedFromOLKey);
		}

		outputInvHeaderEle.setAttribute(FinanceConstants.RETURN_REASON,
				strReturnReason);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output element for setInvHeaderAttrFromRODetails"
					+ XMLUtil.getXmlString(outputInvHeaderEle));
			logger.verbose("output set for setInvHeaderAttrFromRODetails"
					+ derviedFromOLKey);
		}
		logger.endTimer("setInvHeaderAttrFromRODetails");
		return derviedFromOLKey;
	}

}
