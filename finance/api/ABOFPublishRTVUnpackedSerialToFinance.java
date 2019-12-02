package com.abof.finance.api;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.abof.finance.utils.ABOFFinanceUtils;
import com.abof.finance.utils.FinanceConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

public class ABOFPublishRTVUnpackedSerialToFinance implements YIFCustomApi {

  private static YFCLogCategory logger = YFCLogCategory.instance(ABOFPublishRTVUnpackedSerialToFinance.class);


  @SuppressWarnings("unchecked")
public Document populateInventoryChangesOnRTV(YFSEnvironment env, Document inputDoc) throws IllegalArgumentException, Exception {
    logger.beginTimer("populateInventoryChangesOnRTV");
    Element shipmentEle = inputDoc.getDocumentElement();
    String shipmentKey = shipmentEle.getAttribute(FinanceConstants.SHIPMENT_KEY);
    Set<String> ItemserialList = new HashSet<String>();
    /*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854*/
    String shipmentNo = "";
    if(null!=shipmentKey && shipmentKey.trim().length()>0){
    	
    	Document inXML=XMLUtil.createDocument(FinanceConstants.SHIPMENT);
		Element eleGetShipListRoot = inXML.getDocumentElement();
		eleGetShipListRoot.setAttribute(FinanceConstants.SHIPMENT_KEY, shipmentKey.trim());
		
		YFCDocument template=YFCDocument.parse("<Shipments><Shipment DocumentType='' ShipmentNo='' /></Shipments>");
		
		env.setApiTemplate("getShipmentList", template.getDocument());
    	Document shipmentlist=XMLUtil.invokeAPI(env, "getShipmentList", inXML);
    	env.clearApiTemplate("getShipmentList");
    	
    	YFCDocument xShipmentList=YFCDocument.getDocumentFor(shipmentlist);
    	
    	if(null!=xShipmentList){
    		
    		YFCElement xShipmentListElem=xShipmentList.getDocumentElement();
    		
    		if(null!=xShipmentListElem){
    			
    			YFCElement xShipment=xShipmentListElem.getElementsByTagName("Shipment").item(0);
    			
    			if(null!=xShipment){
    				/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854*/
    				shipmentNo=xShipment.getAttribute("ShipmentNo");
    				String documentType=xShipment.getAttribute("DocumentType");
    				
    				if(null!=shipmentNo && shipmentNo.trim().length()>0){
    					
    					if("0008.ex".equals(documentType)){
    						
    					ItemserialList=(Set<String>) env.getTxnObject(shipmentNo.trim());
    			        
    					}else{
    						logger.verbose("Non RTV shipment, Hence exiting the flow");
    						return inputDoc;
    					}
    				}
    			}else{
    				logger.verbose("Non RTV shipment, Hence exiting the flow");
    				return inputDoc;
    			}
    			
    		}else{
    			logger.verbose("Non RTV shipment, Hence exiting the flow");
    			return inputDoc;
    		}
    	}else{
    		logger.verbose("Non RTV shipment, Hence exiting the flow");
    		return inputDoc;
    	}
	 
	 }else{
		 
		 logger.verbose("Unable to find the ShipmentKey for the RTV unpacking, Financial update will not occur");
		 return inputDoc;
	 }
    
    
    
      logger.verbose("This is RTV shipment so going to publish RTV details to Finance");
      
      if (null==ItemserialList || ItemserialList.isEmpty()) {
        logger
        .verbose("Although it is an RTV shipment, shipment does not have serials. Hence not going to publish any details to Finance");
        

        return inputDoc;
      }
      
      env.setTxnObject("isRTV", true);
      
      List<Document> grnHeaderDocList = ABOFFinanceUtils.publishInventoryUpdateForSerialNos(env, ItemserialList, -1);
      for (int i = 0; i < grnHeaderDocList.size(); i++) {
        Document outDoc = grnHeaderDocList.get(i);
        /*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - Start*/
        Element eleOut = outDoc.getDocumentElement();
        eleOut.setAttribute("ShipmentNo", shipmentNo);
        eleOut.setAttribute("ShipmentPacked", FinanceConstants.NO);
        NodeList nlGRNLine = eleOut.getElementsByTagName("GRNLine");
        for (int grnLineCount=0; grnLineCount<nlGRNLine.getLength();grnLineCount++){
        	Element eleGRNLine = (Element) nlGRNLine.item(grnLineCount);
        	eleGRNLine.setAttribute("ShipmentNo", shipmentNo);
        }
        /*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - End*/
        if (logger.isVerboseEnabled()) {
        	
          logger.verbose("output document for populateInventoryChangesOnRTV"
              + XMLUtil.getXmlString(outDoc.getDocumentElement()));
          
        }
        
        XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q, outDoc);
      }


    logger.endTimer("populateInventoryChangesOnRTV");
    return inputDoc;
  }

 
  //plugin to store the serials in memory to be retrieved later by removeFromContainerApi
  //If not retrieved then environment will clear after the transaction completed
  /*
   *<AdjustLocationInventory EnterpriseCode="ABOF" Node="WhiteFieldDC">
    <Source LoadKey="" LocationId="PackingLocation" ShipmentKey="2016070715483361928857">
        <Inventory CountryOfOrigin="" FifoNo="" InventoryStatus="" Quantity="1.00">
            <InventoryItem ItemID="NINEA15AWWWSK9V2417226"
                ProductClass="FQ" UnitOfMeasure="EACH"/>
            <SerialList>
                <SerialDetail AtNode="Y" LocationType="VIRTUAL"
                    RecordFound="Y" SerialNo="100129197" ShipByDate="2500-01-01"/>
            </SerialList>
        </Inventory>
    </Source>
    <Audit AdjustmentType="SHIPMENT" CompleteInprogressTasks="N"
        ContainerNo="100004121" DocumentType="0008.ex" ReasonCode="PACK" ShipmentNo="400180371"/>
</AdjustLocationInventory>
   */
  public Document putSerialsInMemory(YFSEnvironment env, Document adjustLocninventoryInput) {
	 logger.beginTimer("putSerialsInMemory");
	 
	 Set<String> unPackedSrls = new HashSet<String>();
	 
	 YFCDocument inDoc=YFCDocument.getDocumentFor(adjustLocninventoryInput);
	 YFCElement inElem=inDoc.getDocumentElement();
	 YFCElement xAuditElem=inElem.getElementsByTagName("Audit").item(0);
	 if(null!=xAuditElem){
		 String xAdjustmentType=xAuditElem.getAttribute("AdjustmentType");
		 String xDocumentType=xAuditElem.getAttribute("DocumentType");
		 String xReasonCode=xAuditElem.getAttribute("ReasonCode");
		 if("SHIPMENT".equals(xAdjustmentType) && "0008.ex".equals(xDocumentType) &&
				 "PACK".equals(xReasonCode)){
			 
		     for(YFCElement inv:inElem.getElementsByTagName("Inventory")){
			 
			 logger.verbose("inventory Element "+inv);
			 
				String sItemId=inv.getElementsByTagName("InventoryItem").item(0).getAttribute("ItemID");
				
				for(YFCElement srlDtl:inv.getElementsByTagName("SerialDetail")){
				    
				    logger.verbose("serialDetail Element "+srlDtl);
				    unPackedSrls.add(sItemId+","+srlDtl.getAttribute("SerialNo"));

				}

			}
			 
		 }else{
			 
			 logger.verbose("Not going to store for NON-RTV Shipment operations");
			 return adjustLocninventoryInput;
		 }
		 
		 String xShipmentNo=xAuditElem.getAttribute("ShipmentNo");
		 
		 if(null!=xShipmentNo && xShipmentNo.trim().length()>0){
			 
			 env.setTxnObject(xShipmentNo, unPackedSrls);
		 
		 }else{
			 
			 logger.verbose("Unable to find the Shipment number for the RTV adjustment, Financial update will not occur");
		 }
	 }
	 
	 logger.endTimer("putSerialsInMemory");
	 
	return adjustLocninventoryInput;	  
	  
  }
	  

  @Override
  public void setProperties(Properties arg0) throws Exception {
  }
}
