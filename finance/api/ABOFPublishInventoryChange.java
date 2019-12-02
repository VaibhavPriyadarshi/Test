package com.abof.finance.api;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.abof.finance.utils.ABOFFinanceUtils;
import com.abof.finance.utils.FinanceConstants;
import com.abof.sterling.util.XmlConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

public class ABOFPublishInventoryChange implements YIFCustomApi {

  private static YFCLogCategory logger = YFCLogCategory.instance(ABOFPublishInventoryChange.class);

  /**
   * This method is invoked to publish inventory changes during RTV. This is invoked through ChangeShipmentStatus. On success
   * @param env
   * @param inputDoc
   * @return
   * @throws Exception
   * @throws IllegalArgumentException
   */
  public Document populateInventoryChangesOnRTV(YFSEnvironment env, Document inputDoc) throws IllegalArgumentException, Exception {
    logger.beginTimer("populateInventoryChangesOnRTV");
    Element shipmentEle = inputDoc.getDocumentElement();


    String shipmentKey = shipmentEle.getAttribute(FinanceConstants.SHIPMENT_KEY);
    String orderAvInSystem = "";
    String strStatus[] = getShipmentStatus(env, shipmentKey);
    if (!shipmentEle.hasAttribute("OrderAvailableOnSystem")) {
      orderAvInSystem = strStatus[1];
    } else {
      orderAvInSystem = shipmentEle.getAttribute("OrderAvailableOnSystem");
    }

	String docType = shipmentEle.getAttribute("DocumentType");
    if (!(docType.equals("0008.ex") || FinanceConstants.NO.equals(orderAvInSystem))) {
      // This is normal outbound shipment
      logger.verbose("This is normal Outbound shipment so not publishing RTV details to Finance");
      return inputDoc;
    }
    Set<String> serialNoList = new HashSet<String>();
    if (FinanceConstants.RTV_DISPATCHED_STATUS.equals(strStatus[0])) {
      serialNoList = ABOFFinanceUtils.getSerialNumberListOfShipment(env, shipmentKey);
      logger.verbose("This is RTV shipment so going to publish RTV details to Finance");
      if (serialNoList.isEmpty()) {
        logger
        .verbose("Although it is an RTV shipment, shipment does not have serials. Hence not going to publish any details to Finance");
        return inputDoc;
      }
      List<Document> grnHeaderDocList = ABOFFinanceUtils.publishInventoryUpdateForSerialNos(env, serialNoList, 0);
      for (int i = 0; i < grnHeaderDocList.size(); i++) {
        Document outDoc = grnHeaderDocList.get(i);
        if (logger.isVerboseEnabled()) {
          logger.verbose("output document for populateInventoryChangesOnRTV"
              + XMLUtil.getXmlString(outDoc.getDocumentElement()));
        }
        XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q, outDoc);
      }
    } else {
      logger.verbose("This is normal Outbound shipment so not publishing RTV details to Finance");
      return inputDoc;
    }

    logger.endTimer("populateInventoryChangesOnRTV");
    return inputDoc;
  }

  /**
   * This method is used to return the shipment status
   * @param env
   * @param shipmentKey
   * @return
   * @throws ParserConfigurationException
   * @throws Exception
   */
  private String[] getShipmentStatus(YFSEnvironment env, String shipmentKey) throws ParserConfigurationException,
  Exception {
    logger.beginTimer("getShipmentStatus");
    if (logger.isVerboseEnabled()) {
      logger.verbose("input string for getShipmentStatus" + shipmentKey);
    }
    Document docShipments = XMLUtil.createDocument(FinanceConstants.SHIPMENT);
    Element eleShipment = docShipments.getDocumentElement();
    eleShipment.setAttribute(FinanceConstants.SHIPMENT_KEY, shipmentKey);

    Document docShipmentList =
        XMLUtil.getDocument("<Shipments><Shipment Status='' OrderAvailableOnSystem='' ></Shipment></Shipments>");
    Document docShipmentop = XMLUtil.invokeAPI(env, FinanceConstants.GET_SHIPMENT_LIST, docShipments, docShipmentList);
    if (logger.isVerboseEnabled()) {
      logger.verbose("get shipment list output for getShipmentStatus" + XMLUtil.getXmlString(docShipmentop.getDocumentElement()));
    }
    Element shipmentList = docShipmentop.getDocumentElement();
    Element elemshipment = (Element) shipmentList.getElementsByTagName(FinanceConstants.SHIPMENT).item(0);
    String[] strStatus = new String[2];
    strStatus[0] = elemshipment.getAttribute("Status");
    strStatus[1] = elemshipment.getAttribute("OrderAvailableOnSystem");
    logger.endTimer("getShipmentStatus");
    return strStatus;
  }

  /**
   * This method is used to check the quarantine items
   *
   * @param env
   * @param inputDoc
   * @param invMovementValue
   * @param serialNoList
   * @throws Exception
   */
  public static void updateInventoryChangeInQuarantine(YFSEnvironment env, Set<String> serialNoList,
      int invMovementValue) throws Exception {
    logger.beginTimer("updateInventoryChangeInQuarantine");
    if (logger.isVerboseEnabled()) {
      logger.verbose("List of Serial No to publish inventory update" + serialNoList);
    }

    List<Document> grnHeaderDocList = ABOFFinanceUtils.publishInventoryUpdateForSerialNos(env, serialNoList, invMovementValue);
    for (int i = 0; i < grnHeaderDocList.size(); i++) {
      Document outDoc = grnHeaderDocList.get(i);
      if (logger.isVerboseEnabled()) {
        logger.verbose("output document for updateInventoryChangeInQuarantine" + XMLUtil.getXmlString(outDoc.getDocumentElement()));
      }
      XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q, outDoc);
    }
    logger.endTimer("updateInventoryChangeInQuarantine");
  }



  @Override
  public void setProperties(Properties arg0) throws Exception {
  }
}
