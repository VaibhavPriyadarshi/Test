package com.abof.finance.api;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;

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

public class ABOFGRNDetailsOnReceiptReceive implements YIFCustomApi {

  private static YFCLogCategory logger = YFCLogCategory.instance(ABOFPODetailsOnCreation.class);

  @Override
  public void setProperties(Properties arg0) throws Exception {
  }

  /**
   * This method will publish the GRN XML to the camel queue
   * @param env
   * @param inputDoc
   * @throws Exception
   */
  public Document populateGRNDetailsOnReceiptReceive(YFSEnvironment env, Document inputDoc) throws Exception {

    logger.beginTimer("populateGRNDetailsOnReceiptReceive");
    if (logger.isVerboseEnabled()) {
      logger.verbose("input document for populateGRNDetailsOnReceiptReceive" + XMLUtil.getXmlString(inputDoc.getDocumentElement()));
    }

    Element eleRootIn = inputDoc.getDocumentElement();
    Document outDoc = XMLUtil.createDocument(FinanceConstants.GRN_HEADER);
    Element eleRootOut = outDoc.getDocumentElement();

    String strOrderHeaderKey = XPathAPI.selectSingleNode(eleRootIn, "ReceiptLines/ReceiptLine/@OrderHeaderKey").getNodeValue();
    Document ordHeaderList = getOrderListForHeaderCharges(env, strOrderHeaderKey);
    Element strOrderList = ordHeaderList.getDocumentElement();
    String strReceiptNo = eleRootIn.getAttribute(FinanceConstants.RECEIPT_NO);

    String strGrnHeaderKey = ABOFFinanceUtils.getPrimaryKey(strReceiptNo);

    eleRootOut.setAttribute(FinanceConstants.GRN_HEADER_KEY, strGrnHeaderKey);
    eleRootOut.setAttribute(FinanceConstants.VOUCHER_NO, strReceiptNo);
    eleRootOut.setAttribute(FinanceConstants.VOUCHER_DATE, eleRootIn.getAttribute(FinanceConstants.RECEIPT_DATE));
    Node nOrder = XPathAPI.selectSingleNode(strOrderList, "Order");
    if (!YFCCommon.isVoid(nOrder)) {
      Element eleOrder = (Element) nOrder;
      eleRootOut.setAttribute(FinanceConstants.VENDOR_CODE, eleOrder.getAttribute(FinanceConstants.SELLER_ORG_CODE));
      eleRootOut.setAttribute(FinanceConstants.PO_NUMBER, eleOrder.getAttribute(FinanceConstants.ORDER_NO));
      eleRootOut.setAttribute(FinanceConstants.PO_DATE, eleOrder.getAttribute(FinanceConstants.ORDER_DATE));
      eleRootOut.setAttribute(FinanceConstants.PO_TYPE, eleOrder.getAttribute(FinanceConstants.ORDER_TYPE));
      eleRootOut.setAttribute(FinanceConstants.DELIVERY_DUE_DATE, eleOrder.getAttribute("ReqDeliveryDate"));
    }
    // Fetching ShipmentKey to get VDN/ Shipment quantity
    String shipmentKey = "";
    Node nShipmentInReceipt = XPathAPI.selectSingleNode(eleRootIn, "Shipment");
    if (!YFCCommon.isVoid(nShipmentInReceipt)) {
      Element eleShipment = (Element) nShipmentInReceipt;
      eleRootOut.setAttribute(FinanceConstants.DESTINATION, eleShipment.getAttribute("ReceivingNode"));
      eleRootOut.setAttribute(FinanceConstants.DRN_DATE, eleShipment.getAttribute("ActualShipmentDate"));
      shipmentKey = eleShipment.getAttribute(FinanceConstants.SHIPMENT_KEY);
    }
    eleRootOut.setAttribute(FinanceConstants.DELIVERY_DATE, eleRootIn.getAttribute(FinanceConstants.RECEIPT_DATE));
    eleRootOut.setAttribute(FinanceConstants.CREATE_TIME_STAMP, ABOFFinanceUtils.getCreateTimeStamp());
    eleRootOut.setAttribute(FinanceConstants.IS_MODIFID, FinanceConstants.NO);
    eleRootOut.setAttribute(FinanceConstants.IS_RTV, FinanceConstants.NO);
    eleRootOut.setAttribute(FinanceConstants.Is_PROCESSED, FinanceConstants.NO);
    eleRootOut.setAttribute(FinanceConstants.TAX_TYPE, "");
    eleRootOut.setAttribute(FinanceConstants.TAX_RATE, "");
    eleRootOut.setAttribute(FinanceConstants.TAX_AMOUNT, "");
    eleRootOut.setAttribute(FinanceConstants.REMARKS, "");

    String strFreightChgAmount = null;
    Element eleGRNLines = outDoc.createElement(FinanceConstants.GRN_LINES);
    eleRootOut.appendChild(eleGRNLines);

    Map<String, Element> maplinekey = new HashMap<String, Element>();
    Map<String, Double> lineKeyQtyMap = new HashMap<String, Double>();

    NodeList nlReceiptLine = XPathAPI.selectNodeList(eleRootIn, "ReceiptLines/ReceiptLine");

    if (logger.isVerboseEnabled()) {
      logger.verbose("Current Document for staging table is " + XMLUtil.getXmlString(eleRootOut));
    }

    for (int intOL = 0; intOL < nlReceiptLine.getLength(); intOL++) {
      Element receiptLine = (Element) nlReceiptLine.item(intOL);
      if (logger.isVerboseEnabled()) {
        logger.verbose("Iterating for ReceiptLine " + XMLUtil.getXmlString(receiptLine));
      }

      String strQty = receiptLine.getAttribute(FinanceConstants.QUANTITY);
      double dQty = YFCCommon.isVoid(strQty) ? 0.0 : Double.valueOf(strQty);
      if (dQty <= 0) {
        logger.verbose("Going for next receiptline as this ReceiptLine has qty=" + dQty);
        continue;
      }
      Node orderLineNode = XPathAPI.selectSingleNode(receiptLine, "OrderLine");
      if (YFCCommon.isVoid(orderLineNode)) {
        logger.verbose("No OrderLine found for this ReceiptLine. So going for the next ReceiptLine");
        continue;
      }

      Element eleOrderLine = (Element) orderLineNode;

      String strOrderLineKEy = eleOrderLine.getAttribute(FinanceConstants.ORDER_LINE_KEY);

      logger.verbose("Going to iterate for strOrderLineKEy:" + strOrderLineKEy);

      if (!(maplinekey.containsKey(strOrderLineKEy))) {
        Element eleGRNLine = getGRNLine(outDoc, eleOrderLine, strGrnHeaderKey, strReceiptNo);

        maplinekey.put(strOrderLineKEy, eleGRNLine);
        lineKeyQtyMap.put(strOrderLineKEy, dQty);
        if (logger.isVerboseEnabled()) {
          logger.verbose("Could not find the map entry for OrderLineKey:" + strOrderLineKEy + " so made an entry");
          logger.verbose("Now map maplinekey is:" + maplinekey + "\n and map lineKeyQtyMap" + lineKeyQtyMap);
        }

      } else {

        double qty = lineKeyQtyMap.get(strOrderLineKEy);
        logger.verbose("Found qty for OrderLineKey:" + strOrderLineKEy + " as " + qty + ". Going to increment it as="
            + (qty + dQty));
        qty = qty + dQty;
        logger.verbose("Found qty for OrderLineKey:" + strOrderLineKEy + " as " + qty + ". Going to increment it");
        lineKeyQtyMap.put(strOrderLineKEy, qty);
        if (logger.isVerboseEnabled()) {
          logger.verbose("Found the map entry for OrderLineKey:" + strOrderLineKEy + " so updated the entry");
          logger.verbose("Now map maplinekey is:" + maplinekey + "\n and map lineKeyQtyMap" + lineKeyQtyMap);
        }
      }
    }

    Iterator<Entry<String, Element>> it = maplinekey.entrySet().iterator();
    if (logger.isVerboseEnabled()) {
      logger.verbose("Going to iterate further for Qty and TaxAmount with map maplinekey :" + maplinekey + "\n and map lineKeyQtyMap"
          + lineKeyQtyMap);
    }

    // Added to get VDN quantity
    Map<String, Double> orderLineKeyShipmentQty = new HashMap<String, Double>();
    if (!YFCCommon.isVoid(shipmentKey)) {
      getShipmentQtyForOrderLines(env, shipmentKey, orderLineKeyShipmentQty);
    }

    while (it.hasNext()) {

      Map.Entry<String, Element> grnEntry = it.next();
      String strOrderLineKey = grnEntry.getKey();
      Element eleGRNLine = grnEntry.getValue();
      if (logger.isVerboseEnabled()) {
        logger.verbose("For OrderLineKey:" + strOrderLineKey + "Found GRNLine stored in map as"
            + XMLUtil.getXmlString(eleGRNLine));
      }
      double dQty = lineKeyQtyMap.get(strOrderLineKey);
      eleGRNLine.setAttribute(FinanceConstants.QUANTITY, "" + dQty);
      String strUnitPrice = eleGRNLine.getAttribute(FinanceConstants.RATE);
      double dUnitPrice = YFCCommon.isVoid(strUnitPrice) ? 0.0 : Double.parseDouble(strUnitPrice);
	  double dVDNQty =0.0;
		if(orderLineKeyShipmentQty.isEmpty())
		{
		dVDNQty = dQty;
		}else{
       dVDNQty = orderLineKeyShipmentQty.get(strOrderLineKey);
	  }
      if (!YFCCommon.isVoid(dVDNQty)) {
        eleGRNLine.setAttribute(FinanceConstants.VDN_QTY, "" + dVDNQty);
      }

      String strTaxPercentage = eleGRNLine.getAttribute(FinanceConstants.TAX_RATE);
      double dTaxPercentage = YFCCommon.isVoid(strTaxPercentage) ? 0.0 : Double.parseDouble(strTaxPercentage);

      double dTaxAmt = (dQty * dTaxPercentage * dUnitPrice) / 100;
      if (logger.isVerboseEnabled()) {
        logger.verbose("After tax calculation: Qty:" + dQty + ": UnitPrice:" + dUnitPrice + ":dTaxPercentage:" + dTaxPercentage
            + ":dTaxAmt" + dTaxAmt);
      }
      eleGRNLine.setAttribute(FinanceConstants.TAX_AMOUNT, "" + dTaxAmt);
      eleGRNLines.appendChild(eleGRNLine);
      if (logger.isVerboseEnabled()) {
        logger.verbose("Updated output doc" + XMLUtil.getXmlString(eleRootOut));
      }
    }

    Element eleHeaderChargesOut = outDoc.createElement(FinanceConstants.GRN_HEADER_DISCOUNTS);
    eleRootOut.appendChild(eleHeaderChargesOut);

    Element eleHeaderChangres = (Element) ordHeaderList.getElementsByTagName("HeaderCharges").item(0);
    NodeList NoHeadercChanges = eleHeaderChangres.getElementsByTagName("HeaderCharge");
    for (int len = 0; len < NoHeadercChanges.getLength(); len++) {
      Element eleHeaderChargeIn = (Element) NoHeadercChanges.item(len);
      String strChargeCat = eleHeaderChargeIn.getAttribute(FinanceConstants.CHARGE_CATEGORY);
      if (FinanceConstants.DISCOUNT.equalsIgnoreCase(strChargeCat)) {
        Element eleHeaderChargeOut = outDoc.createElement(FinanceConstants.GRN_HEADER_DISCOUNT);
        eleHeaderChargesOut.appendChild(eleHeaderChargeOut);
        eleHeaderChargeOut.setAttribute(FinanceConstants.GRN_HEADER_KEY, strGrnHeaderKey);
        eleHeaderChargeOut.setAttribute(FinanceConstants.DISCOUNT_NAME,
            eleHeaderChargeIn.getAttribute(FinanceConstants.CHARGE_NAME));
        eleHeaderChargeOut.setAttribute(FinanceConstants.DISCOUNT_AMOUNT,
            eleHeaderChargeIn.getAttribute(FinanceConstants.CHARGE_AMOUNT));
        eleHeaderChargeOut.setAttribute(FinanceConstants.DISCOUNT_PERCENTAGE, "");
      }
      if (FinanceConstants.SHIPPING.equalsIgnoreCase(strChargeCat)) {
        strFreightChgAmount = eleHeaderChargeIn.getAttribute(FinanceConstants.CHARGE_AMOUNT);
      }
    }

    if (!(null == strFreightChgAmount)) {
      eleRootOut.setAttribute(FinanceConstants.FREIGHT_CHARGE_NAME, FinanceConstants.SHIPPING);
      eleRootOut.setAttribute(FinanceConstants.FREIGHT_CHARGE_AMOUNT, strFreightChgAmount);
    }

    if (logger.isVerboseEnabled()) {
      logger.verbose("output document for populatePODetailsOnCreation" + XMLUtil.getXmlString(outDoc.getDocumentElement()));
    }
    if (logger.isVerboseEnabled()) {
      logger.verbose("output document for populateGRNDetailsOnReceiptReceive" + XMLUtil.getXmlString(outDoc.getDocumentElement()));
    }
    XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q, outDoc);
    logger.endTimer("populatePODetailsOnCreation");
    return outDoc;
  }


  /**
   * This method fetches Shipment Qty which will be used as VDN quantity with provided ShipmentKey
   *
   * @param env
   * @param shipmentKey
   * @param orderLineKeyShipmentQty
   * @throws Exception
   * @throws IllegalArgumentException
   */
  private void getShipmentQtyForOrderLines(YFSEnvironment env, String shipmentKey,
      Map<String, Double> orderLineKeyShipmentQtyMap) throws IllegalArgumentException, Exception {
    logger.beginTimer("getShipmentQtyForOrderLines");
    if (logger.isVerboseEnabled()) {
      logger.verbose("Input to the method is with shipmentKey:" + shipmentKey + " and orderLineKeyShipmentQty:"
          + orderLineKeyShipmentQtyMap);
    }
    Document inDocGetShipmentList = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
    Element inEleGetShipmentList = inDocGetShipmentList.getDocumentElement();
    inEleGetShipmentList.setAttribute(FinanceConstants.SHIPMENT_KEY, shipmentKey);

    String tmplGetShipmentList =
        "<Shipments><Shipment ShipmentKey=''><ShipmentLines><ShipmentLine OrderLineKey='' Quantity=''/></ShipmentLines></Shipment></Shipments>";
    if (logger.isVerboseEnabled()) {
      logger.verbose("Calling getShipmentList with input"
          + XMLUtil.getXmlString(inDocGetShipmentList.getDocumentElement()));
    }
    Document outDocGetShipmentList =
        XMLUtil.invokeAPI(env, FinanceConstants.GET_SHIPMENT_LIST, inDocGetShipmentList,
            XMLUtil.getDocument(tmplGetShipmentList));
    if (logger.isVerboseEnabled()) {
      logger.verbose("getShipmentList output" + XMLUtil.getXmlString(outDocGetShipmentList.getDocumentElement()));
    }
    NodeList nlShipmentLines =
        XPathAPI.selectNodeList(outDocGetShipmentList, "Shipments/Shipment/ShipmentLines/ShipmentLine");
    for (int i = 0; i < nlShipmentLines.getLength(); i++) {
      Element shipmentLine = (Element) nlShipmentLines.item(i);
      String orderLineKey = shipmentLine.getAttribute(FinanceConstants.ORDER_LINE_KEY);
      String qty = shipmentLine.getAttribute(FinanceConstants.QUANTITY);
      double dQty = YFCCommon.isVoid(qty)?0.0:Double.valueOf(qty);
      Double existingQty = orderLineKeyShipmentQtyMap.get(orderLineKey);
      if (existingQty != null) {
        dQty = dQty + existingQty;
      }
      orderLineKeyShipmentQtyMap.put(orderLineKey, dQty);
    }
    if (logger.isVerboseEnabled()) {
      logger.verbose("returning with orderLineKeyShipmentQty:" + orderLineKeyShipmentQtyMap);
    }
    logger.endTimer("getShipmentQtyForOrderLines");
  }

  /**
   * This method appends GRN Lines to the output document
   * @param outDoc
   * @param eleGRNLines
   * @param eleOrderLine
   * @return
   * @throws Exception
   * @throws IllegalArgumentException
   */
  private Element getGRNLine(Document outDoc, Element eleOrderLine, String strGrnHeaderKey, String strReceiptNo)
      throws IllegalArgumentException, Exception {
    logger.beginTimer("getGRNLine");
    if (logger.isVerboseEnabled()) {
      logger.verbose("input element for getGRNLine" + XMLUtil.getXmlString(eleOrderLine));
    }
    Element eleGRNLine = outDoc.createElement(FinanceConstants.GRN_LINE);
    Element eleItemIn = (Element) eleOrderLine.getElementsByTagName(FinanceConstants.ITEM).item(0);

    Element eleTax = (Element) XPathAPI.selectSingleNode(eleOrderLine, "LineTaxes/LineTax");
    if(null==eleTax || !eleTax.hasAttributes()){
    	eleTax=XMLUtil.createDocument(FinanceConstants.LINE_TAX).getDocumentElement();
    	eleTax.setAttribute(FinanceConstants.REFERENCE_1,"CST");
    	eleTax.setAttribute(FinanceConstants.TAX_PERCENTAGE,"0.00");
    }
    String strLineKey = ABOFFinanceUtils.getPrimaryKey(strReceiptNo + eleOrderLine.getAttribute(FinanceConstants.PRIME_LINE_NO));
    eleGRNLine.setAttribute(FinanceConstants.GRN_LINE_KEY, strLineKey);
    eleGRNLine.setAttribute(FinanceConstants.GRN_HEADER_KEY, strGrnHeaderKey);
    eleGRNLine.setAttribute(FinanceConstants.SKU_CODE, eleItemIn.getAttribute(FinanceConstants.ITEM_ID));
    eleGRNLine.setAttribute(FinanceConstants.MRP, eleItemIn.getAttribute(FinanceConstants.UNIT_COST));
    eleGRNLine.setAttribute(FinanceConstants.UOM, eleItemIn.getAttribute(FinanceConstants.UNIT_OF_MEASURE));
    eleGRNLine.setAttribute(FinanceConstants.TAX_TYPE, eleTax.getAttribute(FinanceConstants.REFERENCE_1));
    eleGRNLine.setAttribute(FinanceConstants.TAX_RATE, eleTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
    eleGRNLine.setAttribute(FinanceConstants.RATE, XPathAPI.selectSingleNode(eleOrderLine, "LinePriceInfo/@UnitPrice").getNodeValue());
    eleGRNLine.setAttribute(FinanceConstants.STYLE_CODE, eleItemIn.getAttribute(FinanceConstants.MANUFACTURER_NAME));
    eleGRNLine.setAttribute(FinanceConstants.EAN_CODE, eleItemIn.getAttribute(FinanceConstants.ISBN));
    eleGRNLine.setAttribute(FinanceConstants.BRAND_STYLE_CODE, eleItemIn.getAttribute(FinanceConstants.MANUFACTURER_NAME));
    eleGRNLine.setAttribute(FinanceConstants.COLOR_DESC, eleItemIn.getAttribute(FinanceConstants.ITEM_SHORT_DESC));
    eleGRNLine.setAttribute(FinanceConstants.PRODUCT_DESC, eleItemIn.getAttribute(FinanceConstants.ITEM_DESC));
    eleGRNLine.setAttribute(FinanceConstants.INVENTORY_STATUS, FinanceConstants.GOOD);
    eleGRNLine.setAttribute(FinanceConstants.VOUCHER_NO, strReceiptNo);
    eleGRNLine.setAttribute(FinanceConstants.IS_MODIFID, FinanceConstants.NO);
    eleGRNLine.setAttribute(FinanceConstants.IS_RTV, FinanceConstants.NO);

    Element eleGRNLineDists = outDoc.createElement(FinanceConstants.GRN_LINE_DISCOUNTS);
    eleGRNLine.appendChild(eleGRNLineDists);

    NodeList nlLineDist = XPathAPI.selectNodeList(eleOrderLine, "LineCharges/LineCharge");
    for (int intLC = 0; intLC < nlLineDist.getLength(); intLC++) {
      Element eleLineCharge = (Element) nlLineDist.item(intLC);
      String strChargeCategory = eleLineCharge.getAttribute(FinanceConstants.CHARGE_CATEGORY);
      if (FinanceConstants.DISCOUNT.equalsIgnoreCase(strChargeCategory)) {
        Element eleGRNLineDist = outDoc.createElement(FinanceConstants.GRN_LINE_DISCOUNT);
        eleGRNLineDists.appendChild(eleGRNLineDist);
        eleGRNLineDist.setAttribute(FinanceConstants.DISCOUNT_NAME, eleLineCharge.getAttribute(FinanceConstants.CHARGE_NAME));
        eleGRNLineDist.setAttribute(FinanceConstants.DISCOUNT_AMOUNT, eleLineCharge.getAttribute(FinanceConstants.CHARGE_AMOUNT));
        eleGRNLineDist.setAttribute(FinanceConstants.DISCOUNT_PERCENTAGE, "");
        eleGRNLineDist.setAttribute(FinanceConstants.GRN_HEADER_KEY, strGrnHeaderKey);
        eleGRNLineDist.setAttribute(FinanceConstants.GRN_LINE_KEY, strLineKey);

      }
    }
    logger.endTimer("getGRNLine");
    if (logger.isVerboseEnabled()) {
      logger.verbose("output element for getGRNLine" + XMLUtil.getXmlString(eleGRNLine));
    }
    return eleGRNLine;
  }

  /**
   * This method returns output of the get order list api
   * @param env
   * @param outDoc
   * @param strOrderHeaderKey
   * @throws ParserConfigurationException
   * @throws Exception
   */
  private Document getOrderListForHeaderCharges(YFSEnvironment env, String strOrderHeaderKey) throws ParserConfigurationException,
  Exception {
    logger.beginTimer("getOrderListForHeaderCharges");
    if (logger.isVerboseEnabled()) {
      logger.verbose("input string for getOrderListForHeaderCharges" + strOrderHeaderKey);
    }
    Document orderDoc = XMLUtil.createDocument(FinanceConstants.ORDER);
    Element eleRoot = orderDoc.getDocumentElement();
    if (null != strOrderHeaderKey) {
      eleRoot.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strOrderHeaderKey);
    }

    Document orderListTempDoc = XMLUtil
        .getDocument("<OrderList><Order OrderNo=''  ReqDeliveryDate='' OrderDate='' OrderType='' SellerOrganizationCode='' ><HeaderCharges><HeaderCharge/></HeaderCharges></Order></OrderList>");
    Document orderListopDOc = XMLUtil.invokeAPI(env, FinanceConstants.GET_ORDER_LIST_API, orderDoc, orderListTempDoc);
    if (logger.isVerboseEnabled()) {
      logger.verbose("output document for getOrderListForHeaderCharges" + XMLUtil.getXmlString(orderListopDOc.getDocumentElement()));
    }
    logger.endTimer("getOrderListForHeaderCharges");
    return orderListopDOc;
  }

}
