package com.abof.finance.api;

import java.util.Properties;

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

/**
 * This class sends the inventory change and payment update when an order is cancelled
 * @author Expicient
 */
public class ABOFOrderDetailsOnCancellation implements YIFCustomApi {
  private static YFCLogCategory logger = YFCLogCategory.instance(ABOFOrderDetailsOnCancellation.class);

  @Override
  public void setProperties(Properties arg0) throws Exception {
  }

  /**
   * This method is invoked on ChangeOrder.ON_CANCEL transaction
   * @param env
   * @param orderDetailsDoc
   * @throws Exception
   */
  public Document populateOrderDetailsOnCancel(YFSEnvironment env, Document orderDetailsDoc) throws Exception {
    logger.beginTimer("populateOrderDetailsOnCancel");
    Element eleRootIn = orderDetailsDoc.getDocumentElement();
    if (logger.isVerboseEnabled()) {
      logger.verbose("input document for populateOrderDetailsOnCancel" + XMLUtil.getXmlString(eleRootIn));
    }
    // Fetching Order Header Key from input
    String strOrderHKey = XPathAPI.selectSingleNode(eleRootIn, "@OrderHeaderKey").getNodeValue();
    Document docGetOrderListOP = callGetOrderList(env, strOrderHKey);
    Element eleRoot = docGetOrderListOP.getDocumentElement();
    Element eleorder = (Element) eleRoot.getElementsByTagName(FinanceConstants.ORDER).item(0);
    // Creating document of output with root element as order
    Document docGetOrderList = XMLUtil.getDocument(eleorder, true);
    if (logger.isVerboseEnabled()) {
      logger.verbose("input document for sending to method populateOrderDetailsOnCancel"
          + XMLUtil.getXmlString(eleorder));
    }
    // Creating output document and publishing it to JMS queue
    Document outDoc = ABOFOrderDetailsOnCreation.createAndPostOrderSnapshotData(env, docGetOrderList);
    XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q, outDoc);
    if (logger.isVerboseEnabled()) {
      logger.verbose("output document for populateOrderDetailsOnCancel" + XMLUtil.getXmlString(orderDetailsDoc.getDocumentElement()));
    }
    logger.endTimer("populateOrderDetailsOnCancel");
    return orderDetailsDoc;
  }

  /**
   * This method is used to call the get order list output of the order
   * @param env
   * @param strOrderHeaderKey
   * @return
   * @throws Exception
   */
  private Document callGetOrderList(YFSEnvironment env, String strOrderHeaderKey) throws Exception {
    logger.beginTimer("callGetOrderList");
    if (logger.isVerboseEnabled()) {
      logger.verbose("input string for callGetOrderList" + strOrderHeaderKey);
    }
    // Preparing input document to call get order list
    Document docGetOrderListIP = XMLUtil.createDocument(FinanceConstants.ORDER);
    Element eleGetOrderListRoot = docGetOrderListIP.getDocumentElement();
    eleGetOrderListRoot.setAttribute(FinanceConstants.ORDER_HEADER_KEY, strOrderHeaderKey);
    // Calling get order list service with prepared template
    Document docGetOrderListOP = XMLUtil.invokeService(env, FinanceConstants.GET_ORDER_LIST_SERVICE_FOR_FINANCE, docGetOrderListIP);
    if (logger.isVerboseEnabled()) {
      logger.verbose("output document for callGetOrderList" + XMLUtil.getXmlString(docGetOrderListOP.getDocumentElement()));
    }
    logger.endTimer("callGetOrderList");
    return docGetOrderListOP;
  }

  /**
   * This method is used to add payment type details to the documents
   * @param elePaymentMethod
   * @param eleOut
   * @throws Exception
   * @throws IllegalArgumentException
   */
  private void populatePaymentMethods(Element elePaymentMethod, Element eleOut) throws IllegalArgumentException, Exception {
    logger.beginTimer("populatePaymentMethods");
    if (logger.isVerboseEnabled()) {
      logger.verbose("input element for populatePaymentMethods" + XMLUtil.getXmlString(elePaymentMethod));
      logger.verbose("input element for populatePaymentMethods" + XMLUtil.getXmlString(eleOut));
    }
    String strPaymentType = elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_TYPE);
    String strPmtRefFive = elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SEVEN);
    String strPmtRefSix = elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_REFERENCE_SIX);
    String strPmntTender = FinanceConstants.BLANK;
    if (null != elePaymentMethod.getAttribute(FinanceConstants.CREDIT_CARD_TYPE)) {
      strPmntTender = elePaymentMethod.getAttribute(FinanceConstants.CREDIT_CARD_TYPE);
    }
    eleOut.setAttribute(FinanceConstants.PAYMENT_TYPE, strPaymentType);
    eleOut.setAttribute(FinanceConstants.PAYMENT_GATEWAY, strPmtRefSix);
    eleOut.setAttribute(FinanceConstants.PAYMENT_REFERENCE, strPmtRefFive);
    eleOut.setAttribute(FinanceConstants.PAYMENT_TENDER, strPmntTender);

    if (logger.isVerboseEnabled()) {
      logger.verbose("output element for populatePaymentMethods" + XMLUtil.getXmlString(eleOut));
    }
    logger.endTimer("populatePaymentMethods");
  }

  /**
   * This method is invoked on the transaction PaymentCollection.ON_LIABILITY_TRANSFER
   * @param env
   * @param orderDoc
   * @throws Exception
   */
  public Document populateLiabilityChangesOnCancel(YFSEnvironment env, Document orderDoc) throws Exception {
    logger.beginTimer("populateLiabilityChangesOnCancel");
    Element eleRootIn = orderDoc.getDocumentElement();
    if (logger.isVerboseEnabled()) {
      logger.verbose("input document for populateLiabilityChangesOnCancel" + XMLUtil.getXmlString(eleRootIn));
    }
    // Creating publish document
    Document outDoc = XMLUtil.createDocument(FinanceConstants.INVOICE_HEADER);
    Element eleRootOut = outDoc.getDocumentElement();
    // Creating primary key for header table
    String strOrderNo = eleRootIn.getAttribute(FinanceConstants.ORDER_NO);
    String strPrimaryKey = ABOFFinanceUtils.getPrimaryKey(strOrderNo);
    // Setting header level attributes
    eleRootOut.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
    eleRootOut.setAttribute(FinanceConstants.ORDER_NO, strOrderNo);
    eleRootOut.setAttribute(FinanceConstants.ORDER_DATE, eleRootIn.getAttribute(FinanceConstants.ORDER_DATE));

    String orderType = eleRootIn.getAttribute(FinanceConstants.ORDER_TYPE);
    String documentType = eleRootIn.getAttribute(FinanceConstants.DOCUMENT_TYPE);
    if (FinanceConstants.DOC_TYPE_RETURN_ORDER.equals(documentType)) {
      eleRootOut.setAttribute(FinanceConstants.DOCUMENT_TYPE, FinanceConstants.RO);
    } else if (FinanceConstants.RETURN_EXCHANGE.equals(orderType)) {
      eleRootOut.setAttribute(FinanceConstants.DOCUMENT_TYPE, FinanceConstants.EO);
    } else {
      eleRootOut.setAttribute(FinanceConstants.DOCUMENT_TYPE, FinanceConstants.SO);
    }

    // Adding time stamp and is processed values
    eleRootOut.setAttribute(FinanceConstants.CREATETS, ABOFFinanceUtils.getCreateTimeStamp());
    eleRootOut.setAttribute(FinanceConstants.IS_PROCESSED, FinanceConstants.NO);
    String strOrderHKey = eleRootIn.getAttribute(FinanceConstants.ORDER_HEADER_KEY);
    Document docGetOrderListOP = callGetOrderList(env, strOrderHKey);
    populateHedaerDetails(docGetOrderListOP.getDocumentElement(), eleRootOut);
    // Setting Invoice Payment attributes
    Element eleInvoicePmnts = outDoc.createElement(FinanceConstants.INVOICE_PAYMENTS);
    eleRootOut.appendChild(eleInvoicePmnts);
    double storeCreditPayment=0.0;
    double prepaidPayment =0.0;
    double codPayment = 0.0;
    double dQwikcilverWalletAmount=0.0;

    // Finding all payment types
    NodeList nlChargeTranDist = XPathAPI.selectNodeList(eleRootIn, "//ChargeTranDistributions/ChargeTranDistribution");
    for (int i = 0; i < nlChargeTranDist.getLength(); i++) {
      Element eleChargeTranDist = (Element) nlChargeTranDist.item(i);
      Element eleDistributedToChargeTran = (Element) XPathAPI.selectSingleNode(eleChargeTranDist, FinanceConstants.DIST_TO_CHG_TRAN);
      NodeList nlPaymentMethod = eleDistributedToChargeTran.getElementsByTagName(FinanceConstants.PAYMENT_METHOD);
      if (YFCCommon.isVoid(nlPaymentMethod) || nlPaymentMethod.getLength() == 0) {
        logger.verbose("Did not find Payment method element so going for next iteration");
        continue;
      }
      Element elePaymentMethod = (Element) nlPaymentMethod.item(0);
      // Creating invoice payment element for payment snapshot
      Element eleInvoicePmnt = outDoc.createElement(FinanceConstants.INVOICE_PAYMENT);
      eleInvoicePmnts.appendChild(eleInvoicePmnt);

      // Calculating distributed amount value
      double distAmt = Double.valueOf(eleDistributedToChargeTran.getAttribute(FinanceConstants.DISTRIBUTED_AMOUNT));

      // Setting payment details for payment snapshot
      eleInvoicePmnt.setAttribute(FinanceConstants.INVOICE_HEADER_KEY, strPrimaryKey);
      populatePaymentMethods(elePaymentMethod, eleInvoicePmnt);
      eleInvoicePmnt.setAttribute(FinanceConstants.AMOUNT_RECEIVED, String.valueOf(distAmt * -1));
      //eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
      String paymentType = elePaymentMethod.getAttribute(FinanceConstants.PAYMENT_TYPE);
      //B2B Start
      if(FinanceConstants.PAYMENT_TYPE_CREDIT.equalsIgnoreCase(paymentType))
      {
    	  eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PAYMENT_TYPE_CREDIT);
      }
      else
      {
    	  eleInvoicePmnt.setAttribute(FinanceConstants.PAYMENT_MODE, FinanceConstants.PREPAID);
      }
      //B2B end
      if ("COD".equalsIgnoreCase(paymentType)) {
        codPayment = codPayment + (distAmt * -1);
      } else if ("STORE_CREDIT".equalsIgnoreCase(paymentType)) {
        storeCreditPayment = storeCreditPayment + (distAmt * -1);
      } else if (FinanceConstants.QWIKCILVER_WALLET.equalsIgnoreCase(paymentType)) {
    	  dQwikcilverWalletAmount = dQwikcilverWalletAmount + (distAmt * -1);
        }else {
        prepaidPayment = prepaidPayment + (distAmt * -1);
      }
    }
    eleRootOut.setAttribute(FinanceConstants.STORE_CREDIT_AMOUNT, "" + storeCreditPayment);
    eleRootOut.setAttribute(FinanceConstants.PREPAID_AMOUNT, "" + prepaidPayment);
    eleRootOut.setAttribute(FinanceConstants.COD_AMOUNT, "" + codPayment);
	eleRootOut.setAttribute(FinanceConstants.QWIKCILVER_WALLET_AMOUNT, "" + dQwikcilverWalletAmount);

    if (logger.isVerboseEnabled()) {
      logger.verbose("output document for populateLiabilityChangesOnCancel" + XMLUtil.getXmlString(eleRootOut));
    }
    XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q, outDoc);

    logger.endTimer("populateLiabilityChangesOnCancel");
    return orderDoc;
  }

  /**
   * This method populated header details of invoice table in case of any cancellation
   *
   * @param orderListEle
   * @param eleRootOut
   * @throws IllegalArgumentException
   * @throws Exception
   */
  private void populateHedaerDetails(Element orderListEle, Element eleRootOut) throws IllegalArgumentException,
  Exception {
    logger.beginTimer("populateHedaerDetails");
    Element eleOrder = (Element) XPathAPI.selectSingleNode(orderListEle, "Order");
    ABOFFinanceUtils.setRootLevelAttributes(eleOrder, eleRootOut);
    logger.endTimer("populateHedaerDetails");

  }
}
