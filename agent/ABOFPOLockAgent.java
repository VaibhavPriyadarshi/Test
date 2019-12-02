package com.abof.sterling.agent;


import java.util.HashMap;
import java.util.List;
import com.bridge.sterling.framework.agent.AbstractCustomBaseAgent;
import com.bridge.sterling.utils.GenericUtils;
import com.bridge.sterling.utils.XMLUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCDate;

@SuppressWarnings("deprecation")
public class ABOFPOLockAgent extends AbstractCustomBaseAgent {

    private static YFCLogCategory logger = YFCLogCategory.instance(ABOFPOLockAgent.class);
    private HashMap<String, String>  _properties=new HashMap<String, String>();
    
    @Override
    public List<YFCDocument> getJobs(YFCDocument msgXml, YFCDocument lastMsgXml) {

	logger.beginTimer("ABOFPOLockAgent.getJobs");
	
	//List<YFCDocument> orderList = new ArrayList<YFCDocument>();
	YFCElement root=msgXml.getDocumentElement();
	int sPOLockDays = root.getIntAttribute("POLockDays");
	String sDocumentType=root.getAttribute("DocumentType");
	String sHoldType=root.getAttribute("HoldType");
	String sHoldReasonText=root.getAttribute("HoldReasonText");
	String sMaximumRecords="5000";
	String sExpectedDate;
	String lastOrderHeaderKey = null;
	
	logger.debug("Agent Criteria Input: "+msgXml);
	
	if(YFCObject.isVoid(sPOLockDays) || !root.hasAttribute("POLockDays")){
	    
	    getAgentCriteria(root.getAttribute("ImportAgentCriteriaID"));
	    sPOLockDays=Integer.parseInt(_properties.get("POLockDays"));
	    
	}
	
	if(!YFCObject.isVoid(root.getAttribute("MaximumRecords"))){
	    sMaximumRecords = root.getAttribute("MaximumRecords");
	}
	
	
	logger.debug("LastMsgXml: "+lastMsgXml);
	
	if (!YFCObject.isVoid(lastMsgXml)) {
	    YFCElement sOrder = lastMsgXml.getDocumentElement();
	    lastOrderHeaderKey=sOrder.getAttribute("OrderHeaderKey");
	    
	}
	
	
	 YFCDocument inDoc= YFCDocument.createDocument("Order");
	 YFCElement inElem=inDoc.getDocumentElement();
	 inElem.setAttribute("DocumentType", sDocumentType);
	 inElem.setAttribute("MaximumRecords", sMaximumRecords);
	 inElem.setAttribute("OrderComplete", "N");
	 inElem.setAttribute("HoldFlag", "N");
	 
	 if(!YFCObject.isVoid(lastOrderHeaderKey)){
	     inElem.setAttribute("OrderHeaderKeyQryType", "GT");
	     inElem.setAttribute("OrderHeaderKey", lastOrderHeaderKey);
	     
	 }
	 
	 
	 	 
	 sExpectedDate = getExpectedDate(sPOLockDays);
	 inElem.setAttribute("ReqDeliveryDateQryType", "LT");
	 inElem.setAttribute("ReqDeliveryDate", sExpectedDate);
	 
	 YFCElement extnElem = inElem.createChild("Extn");
	 extnElem.setAttribute("POLockDateQryType", "ISNULL");
	 	 
	 YFCElement orderByElem = inElem.createChild("OrderBy");
	 YFCElement attribElem = orderByElem.createChild("Attribute");
	 attribElem.setAttribute("Name", "OrderHeaderKey");
	
	 logger.debug("calling getOrderList API with Input: "+inDoc);
	 
	YFCDocument outDoc=invokeYantraApi("getOrderList", inDoc,getOrderListTemplate());
	
	/*for(YFCElement orderElem : outDoc.getDocumentElement().getElementsByTagName("Order")){
	    XMLUtil.getDocumentsForChildElements(outDoc.getDocumentElement(), "Order");
	    orderList.add(YFCDocument.parse(orderElem.toString()));
	    
	}*/
	
	
	for(YFCElement children:outDoc.getDocumentElement().getElementsByTagName("Order")){
	    
	    YFCElement orderHoldType=children.createChild("OrderHoldTypes").createChild("OrderHoldType");
	    orderHoldType.setAttribute("HoldType", sHoldType);
	    orderHoldType.setAttribute("ReasonText", sHoldReasonText);
	    orderHoldType.setAttribute("Status", "1100");
	}
	
	logger.endTimer("ABOFPOLockAgent.getJobs");
	return XMLUtil.getDocumentsForChildElements(outDoc.getDocumentElement(), "Order");
	
	//return orderList;
    }

    

    private YFCDocument getOrderListTemplate(){
	
	logger.beginTimer("ABOFPOLockAgent.getOrderListTemplate");
	
	YFCDocument temp= YFCDocument.createDocument("OrderList");
	YFCElement sOrderList=temp.getDocumentElement();
	YFCElement sOrder=sOrderList.createChild("Order");
	sOrder.setAttribute("OrderHeaderKey", "");
	sOrder.setAttribute("OrderNo", "");
	sOrder.setAttribute("DocumentType", "");
		
	logger.endTimer("ABOFPOLockAgent.getOrderListTemplate");
	
	return temp;
    }

  
    private String getExpectedDate(int sDays) {
	
	logger.beginTimer("ABOFPOLockAgent.getExpectedDate");
	
	YFCDate currentDate=new YFCDate();
	currentDate.changeDate(0-sDays);
	
	logger.endTimer("ABOFPOLockAgent.getExpectedDate");
	
	return currentDate.getString("YYYY-MM-dd");
	
    }
    


    //<Order Action="MODIFY" DocumentType="" 
    //OrderHeaderKey="" OrderName="" OrderNo="" Override="" 
    //IgnoreTransactionDependencies="" OrderComplete="Y"/>
    
    @Override
    public void executeJob(YFCDocument executeJobXml) {
	
	logger.beginTimer("ABOFPOLockAgent.executeJob");
	
	logger.debug("Input to execute job"+executeJobXml);
	 
	YFCElement jobElem=executeJobXml.getDocumentElement();
	YFCDocument Order=YFCDocument.createDocument("Order");
	YFCElement OrderElem=Order.getDocumentElement();
	OrderElem.importNode(jobElem.getElementsByTagName("OrderHoldTypes").item(0));
	OrderElem.setAttributes(jobElem.getAttributes());
	OrderElem.setAttribute("Action", "MODIFY");
	OrderElem.setAttribute("Override", "Y");
	OrderElem.setAttribute("IgnoreTransactionDependencies", "Y");
	YFCDate currentDate=new YFCDate();
	YFCElement extn = OrderElem.createChild("Extn");
	extn.setAttribute("POLockDate", currentDate.getString("YYYY-MM-dd"));
	
	 

	
	
	logger.debug("Input to changeOrder API"+Order);
	
	invokeYantraApi("changeOrder",Order);
	
	logger.endTimer("ABOFPOLockAgent.executeJob");
	
	
	
    }
    
private void getAgentCriteria(String agentCriteriaId) {
	
	logger.beginTimer("ABOFPOLockAgent.getAgentCriteria");
	
	if(YFCObject.isVoid(agentCriteriaId)){
		
		throw new RuntimeException("CONFIGERROR: Property not set");
		
	    }
		
	    YFCDocument getCriteriaDoc=YFCDocument.createDocument("AgentCriteria");
	
	    YFCElement  getCriteriaElem=getCriteriaDoc.getDocumentElement();
		
	    getCriteriaElem.setAttribute("AgentCriteriaId",agentCriteriaId );
	    
	    YFCDocument criteriaXML=invokeYantraApi("getAgentCriteriaList",getCriteriaDoc);
	    YFCElement criteriaXMLElement=criteriaXML.getDocumentElement();
	    YFCElement attributes=criteriaXMLElement.getElementsByTagName("Attributes").item(0);
	    
	    for(YFCElement attribute:attributes.getElementsByTagName("Attribute")){
		String sName=attribute.getAttribute("Name");
		String sValue=attribute.getAttribute("Value");
		_properties.put(sName, sValue);
	    }
	    
	    logger.endTimer("ABOFPOLockAgent.getAgentCriteria");   

    }




}
