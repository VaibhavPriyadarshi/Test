package com.abof.finance.api;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.abof.finance.utils.FinanceConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;

public class ABOFPublishInventoryMovement implements YIFCustomApi {

  private static YFCLogCategory logger = YFCLogCategory.instance(ABOFPublishInventoryMovement.class);

  /**
   * This method is used to publish inventory change information
   * @param env
   * @param inputDoc
   * @return
   * @throws Exception
   */
  public Document populateInventoryChanges(YFSEnvironment env, Document inputDoc) throws Exception {
    logger.beginTimer("populateInventoryChanges");
    if (logger.isVerboseEnabled()) {
      logger.verbose("input document for populateInventoryChanges" + XMLUtil.getXmlString(inputDoc.getDocumentElement()));
    }
    Element taskEle = inputDoc.getDocumentElement();
    String strTaskKey = taskEle.getAttribute(FinanceConstants.TASK_KEY);

    Document docTaskList = GetTaskList(env, strTaskKey);
    if (logger.isVerboseEnabled()) {
      logger.verbose("task list document for populateInventoryChanges" + XMLUtil.getXmlString(docTaskList.getDocumentElement()));
    }

    Element eleTasklist = docTaskList.getDocumentElement();
    Element eleTask = (Element) eleTasklist.getElementsByTagName(FinanceConstants.TASK).item(0);


    String srcZoneId = eleTask.getAttribute(FinanceConstants.SOURCE_ZONE_ID);
    String targetZoneId = eleTask.getAttribute(FinanceConstants.TARGET_ZONE_ID);
    String node = eleTask.getAttribute(FinanceConstants.NODE);


    if (YFCCommon.isVoid(srcZoneId)) {
      logger.verbose("Found Source Zone Id blank so going to fetch it using getLocationList from LocationId");
      srcZoneId = getZoneIdForLocation(env, eleTask.getAttribute(FinanceConstants.SOURCE_LOCATION_ID), node);
    }

    if (YFCCommon.isVoid(targetZoneId)) {
      logger.verbose("Found Source Zone Id blank so going to fetch it using getLocationList from LocationId");
      targetZoneId = getZoneIdForLocation(env, eleTask.getAttribute(FinanceConstants.TARGET_LOCATION_ID), node);
    }

    if (FinanceConstants.QUARANTINE_LOCATION.equalsIgnoreCase(srcZoneId)
        && !FinanceConstants.QUARANTINE_LOCATION.equalsIgnoreCase(targetZoneId)) {
      logger.verbose("Found Source Zone Id=" + srcZoneId + " and  targetZoneId" + targetZoneId
          + " So it is Quarantine to Normal Zone movement");
      Set<String> serialNoList = getListOfSerialNo(env, inputDoc, eleTask);
      if (logger.isVerboseEnabled()) {
        logger.verbose("List of Serial No before publishing inventory update" + serialNoList);
      }
      if (!serialNoList.isEmpty()) {
        logger.verbose("Serial No list is not empty");
        ABOFPublishInventoryChange.updateInventoryChangeInQuarantine(env, serialNoList, 1);
      }

    } else if (!FinanceConstants.QUARANTINE_LOCATION.equalsIgnoreCase(srcZoneId)
        && FinanceConstants.QUARANTINE_LOCATION.equalsIgnoreCase(targetZoneId)) {
      logger.verbose("Found Source Zone Id=" + srcZoneId + " and  targetZoneId" + targetZoneId
          + " So it is Normal Zone to Quarantine movement");
      Set<String> serialNoList = getListOfSerialNo(env, inputDoc, eleTask);
      if (logger.isVerboseEnabled()) {
        logger.verbose("List of Serial No before publishing inventory update" + serialNoList);
      }
      if (!serialNoList.isEmpty()) {
        logger.verbose("Serial No list is not empty");
        ABOFPublishInventoryChange.updateInventoryChangeInQuarantine(env, serialNoList, 2);
      }
    }
    logger.endTimer("populateInventoryChanges");
    return inputDoc;
  }

  /**
   * This method fetches list of Serial No. from 1. SerialNumber details of task, 2. Inventory level
   * of Task, 3. YFS_GLOBAL_SERIAL_NUM. Hence not going publish inventory movement
   *
   * @param env
   * @param inputDoc
   * @param eleTask
   * @return
   * @throws Exception
   * @throws IllegalArgumentException
   */
  private Set<String> getListOfSerialNo(YFSEnvironment env, Document inputDoc, Element eleTask)
      throws IllegalArgumentException, Exception {
    logger.beginTimer("getListOfSerialNo");
    if (logger.isVerboseEnabled()) {
      logger.verbose("inputDoc:" + XMLUtil.getXmlString(inputDoc.getDocumentElement()));
      logger.verbose("eleTask:" + XMLUtil.getXmlString(eleTask));
    }

    Set<String> serialNoList = new HashSet<String>();
    List<Element> inventoryEleList = XMLUtil.getElementsByXpath(inputDoc, "//Task/Inventory/SerialNumberDetails/SerialNumberDetail");

    for (int i = 0; i < inventoryEleList.size(); i++) {
      String serialNo = inventoryEleList.get(i).getAttribute(FinanceConstants.SERIAL_NO);
      logger.verbose("Got SerialNo:" + serialNo);
      if (!YFCCommon.isVoid(serialNo)) {
        serialNoList.add(serialNo);
      }
    }

    if (logger.isVerboseEnabled()) {
      logger.verbose("List of Serial No after fetching from Serial details of Task" + serialNoList);
    }

    if(serialNoList.size()>0){
      logger.verbose("Reterived SerialNo from SerialNo details of Task");
      logger.endTimer("getListOfSerialNo");
      return serialNoList;
    }

    Element eleInv = (Element) eleTask.getElementsByTagName(FinanceConstants.INVENTORY).item(0);
    String serialNo = "";
    if (!YFCCommon.isVoid(eleInv)) {
      serialNo = eleInv.getAttribute(FinanceConstants.SERIAL_NO);
      if(!YFCCommon.isVoid(serialNo)){
        serialNoList.add(serialNo);
        if (logger.isVerboseEnabled()) {
          logger.verbose("List of Serial No after fetching from Inventory element" + serialNoList);
          logger.verbose("Going to return as we got the SerialNo to process inventory publish details");
        }
        logger.endTimer("getListOfSerialNo");
        return serialNoList;
      }
    }

    logger
    .verbose(" Serial No is neither coming in BeforeTaskCompletionUE nor in TaskDetails. Hence going to get SerialNo for this Task from YFS_GLOBAL_SERIAL_NUM using getSerialList API");
    getSerialNumFromGlobalSerialNum(env, eleTask, serialNoList);
    if (logger.isVerboseEnabled()) {
      logger.verbose("List of Serial No after fetching from Global Serial Num table" + serialNoList);
    }
    logger.endTimer("getListOfSerialNo");
    return serialNoList;

  }

  /**
   * This method calls getSerialList with TaskId and ShipNode and fetches the SerialNo associated to
   * this task.
   *
   * @param env
   * @param eleTask
   * @param serialNoList
   * @throws Exception
   * @throws IllegalArgumentException
   */

  private void getSerialNumFromGlobalSerialNum(YFSEnvironment env, Element eleTask, Set<String> serialNoList)
      throws IllegalArgumentException, Exception {
    logger.beginTimer("getSerialNumFromGlobalSerialNum");
    if (logger.isVerboseEnabled()) {
      logger.verbose("eleTask in getSerialNumFromGlobalSerialNum:" + XMLUtil.getXmlString(eleTask));
    }
    // String node = eleTask.getAttribute(FinanceConstants.NODE);
    String taskId = eleTask.getAttribute(FinanceConstants.TASK_ID);
    if (YFCCommon.isVoid(taskId)) {
      logger.verbose("TaskId  is blank. i.e. taskId:" + taskId
          + " Hence not going to call getSerialList");
      return;
    }
    Document docGetSerialListInput = XMLUtil.createDocument(FinanceConstants.SERIAL);
    Element eleGetSerialList = docGetSerialListInput.getDocumentElement();
    eleGetSerialList.setAttribute(FinanceConstants.TASK_ID, taskId);
    // eleGetSerialList.setAttribute(FinanceConstants.SHIPNODE, node);

    if (logger.isVerboseEnabled()) {
      logger.verbose("Input to getSerialList:" + XMLUtil.getXmlString(eleGetSerialList));
    }

    Document doGetSerialListTemp =
        XMLUtil
        .getDocument("<SerialList><Serial FifoNo='' GlobalSerialKey=''  LocationId='' SerialNo='' ShipNode='' /></SerialList>");

    Document docGetSerialListOutput =
        XMLUtil.invokeAPI(env, FinanceConstants.GET_SERIAL_LIST_API, docGetSerialListInput, doGetSerialListTemp);
    if (logger.isVerboseEnabled()) {
      logger.verbose("output document from getSerialList"
          + XMLUtil.getXmlString(docGetSerialListOutput.getDocumentElement()));
    }

    Element serialListEle = docGetSerialListOutput.getDocumentElement();

    NodeList nlSerial = serialListEle.getElementsByTagName(FinanceConstants.SERIAL);
    for (int i = 0; i < nlSerial.getLength(); i++) {
      Element serialEle = (Element) nlSerial.item(i);
      String serialNo = serialEle.getAttribute(FinanceConstants.SERIAL_NO);
      logger.verbose("Got SerialNo:" + serialNo);
      if (!YFCCommon.isVoid(serialNo)) {
        serialNoList.add(serialNo);
      }
    }
    logger.endTimer("getSerialNumFromGlobalSerialNum");
  }
  /**
   * This method fetches the ZoneId with provide Node and LocationId
   *
   * @param env
   * @param node
   * @param attribute
   * @return
   * @throws Exception
   * @throws IllegalArgumentException
   */
  private String getZoneIdForLocation(YFSEnvironment env, String locationId, String node)
      throws IllegalArgumentException, Exception {
    logger.beginTimer("getZoneIdForLocation");
    if (YFCCommon.isVoid(locationId)) {
      logger.verbose("Location id is blank. So returning the ZoneId as blank");
      return "";
    }
    Document inLocationDoc = XMLUtil.createDocument(FinanceConstants.LOCATION);
    Element inLocationEle = inLocationDoc.getDocumentElement();
    inLocationEle.setAttribute(FinanceConstants.LOCATION_ID, locationId);
    inLocationEle.setAttribute(FinanceConstants.NODE, node);
    Document getLocationListTempDoc =
        XMLUtil
        .getDocument("<Locations><Location LocationId='' LocationKey='' LocationType='' Node='' ZoneId=''/></Locations>");
    Document getLocationListOutDoc =
        XMLUtil.invokeAPI(env, FinanceConstants.GET_LOCATION_LIST_API, inLocationDoc, getLocationListTempDoc);

    Node nLocation = XPathAPI.selectSingleNode(getLocationListOutDoc, "Locations/Location");
    String zoneId = "";
    if (!YFCCommon.isVoid(nLocation)) {
      Element eleLocation = (Element) nLocation;
      zoneId = eleLocation.getAttribute(FinanceConstants.ZONE_ID);
    }
    logger.verbose("Going to return ZoneId=" + zoneId + " For locationId=" + locationId + " and Node=" + node);
    logger.endTimer("getZoneIdForLocation");
    return zoneId;
  }

  /**
   * This method is used to get the list of tasks for an item
   *
   * @param env
   * @param strTaskKey
   * @throws ParserConfigurationException
   * @throws Exception
   */
  private Document GetTaskList(YFSEnvironment env, String strTaskKey) throws ParserConfigurationException, Exception {
    logger.beginTimer("GetTaskList");
    if (logger.isVerboseEnabled()) {
      logger.verbose("input string for GetTaskList" + strTaskKey);
    }
    Document docGetOrderInvInput = XMLUtil.createDocument(FinanceConstants.TASK);
    Element eleGetOrderInvRoot = docGetOrderInvInput.getDocumentElement();
    eleGetOrderInvRoot.setAttribute(FinanceConstants.TASK_KEY, strTaskKey);

    Document docTemplatexml =
        XMLUtil
        .getDocument("<TaskList><Task TaskId='' SourceZoneId='' TargetZoneId='' TargetLocationId='' SourceLocationId='' Node=''><Inventory SerialNo=''/></Task></TaskList>");

    Document docGetOrderInvOutput = XMLUtil.invokeAPI(env, FinanceConstants.GET_TASK_LIST, docGetOrderInvInput, docTemplatexml);
    if (logger.isVerboseEnabled()) {
      logger.verbose("output document from GetTaskList"
          + XMLUtil.getXmlString(docGetOrderInvOutput.getDocumentElement()));
    }
    logger.endTimer("GetTaskList");
    return docGetOrderInvOutput;
  }

  @Override
  public void setProperties(Properties arg0) throws Exception {
  }

}
