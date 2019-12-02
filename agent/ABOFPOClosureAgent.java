package com.abof.sterling.agent;


import java.util.List;
import com.bridge.sterling.framework.agent.AbstractCustomBaseAgent;
import com.bridge.sterling.utils.XMLUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCDate;

@SuppressWarnings("deprecation")
public class ABOFPOClosureAgent extends AbstractCustomBaseAgent {

    private static YFCLogCategory logger = YFCLogCategory.instance(ABOFPOClosureAgent.class);
    
    @Override
    public List<YFCDocument> getJobs(YFCDocument msgXml, YFCDocument lastMsgXml) {

	logger.beginTimer("ABOFPOClosureAgent.getJobs");
	
	//List<YFCDocument> orderList = new ArrayList<YFCDocument>();
	YFCElement root=msgXml.getDocumentElement();
	int sPOLockDays = root.getIntAttribute("POLockDays");
	int sPOUnlockDays = root.getIntAttribute("POUnlockDays");
	int sPOCloseDays = root.getIntAttribute("POCloseDays");
	String sDocumentType=root.getAttribute("DocumentType");
	String sModificationReasonCode=root.getAttribute("ModificationReasonCode");
	String sModificationReasonText=root.getAttribute("ModificationReasonText");
	String sRetentionAttrib = root.getAttribute("RetentionAttribute");
	String sMaximumRecords="5000";
	String sExpectedDate;
	String lastOrderHeaderKey = null;
	
	logger.debug("Agent Criteria Input: "+msgXml);
	
	if(!YFCObject.isVoid(root.getAttribute("MaximumRecords"))){
	    sMaximumRecords = root.getAttribute("MaximumRecords");
	}
	
	
	logger.debug("LastMsgXml: "+lastMsgXml);
	
	if (!YFCObject.isVoid(lastMsgXml)) {
	    YFCElement sOrder = lastMsgXml.getDocumentElement();
	    lastOrderHeaderKey=sOrder.getAttribute("OrderHeaderkey");
	    
	}
	
	
	 YFCDocument inDoc= YFCDocument.createDocument("Order");
	 YFCElement inElem=inDoc.getDocumentElement();
	 inElem.setAttribute("DocumentType", sDocumentType);
	 inElem.setAttribute("MaximumRecords", sMaximumRecords);
	 inElem.setAttribute("OrderComplete", "N");
	 
	 if(!YFCObject.isVoid(lastOrderHeaderKey)){
	     inElem.setAttribute("OrderHeaderKeyQryType", "GT");
	     inElem.setAttribute("OrderHeaderKey", lastOrderHeaderKey);
	     
	 }
	 
	 if(!YFCObject.isVoid(sRetentionAttrib)){
	     if("ReqDeliveryDate".equalsIgnoreCase(sRetentionAttrib)){
		 sExpectedDate = getExpectedDate(sPOLockDays+sPOUnlockDays+sPOCloseDays);
		 inElem.setAttribute("ReqDeliveryDateQryType", "LT");
		 inElem.setAttribute("ReqDeliveryDate", sExpectedDate);
	     }
	     
	     if("POLockDate".equalsIgnoreCase(sRetentionAttrib)){
		 sExpectedDate = getExpectedDate(sPOUnlockDays+sPOCloseDays);
		 YFCElement extn=inElem.createChild("Extn");
		 extn.setAttribute("POLockDateQryType", "LT");
		 extn.setAttribute("POLockDate", sExpectedDate);
	     }
	     
	 }
	 
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
	    
	    children.setAttribute("ModificationReasonCode", sModificationReasonCode);
	    children.setAttribute("ModificationReasonText", sModificationReasonText);
	}
	
	logger.endTimer("ABOFPOClosureAgent.getJobs");
	
	return XMLUtil.getDocumentsForChildElements(outDoc.getDocumentElement(), "Order");
	
	//return orderList;
    }

    

    private YFCDocument getOrderListTemplate(){
	
	logger.beginTimer("ABOFPOClosureAgent.getOrderListTemplate");
	
	YFCDocument temp= YFCDocument.createDocument("OrderList");
	YFCElement sOrderList=temp.getDocumentElement();
	YFCElement sOrder=sOrderList.createChild("Order");
	sOrder.setAttribute("OrderHeaderKey", "");
	sOrder.setAttribute("OrderNo", "");
	sOrder.setAttribute("DocumentType", "");
	
	logger.endTimer("ABOFPOClosureAgent.getOrderListTemplate");
	
	return temp;
    }

  
    private String getExpectedDate(int sDays) {
	
	logger.beginTimer("ABOFPOClosureAgent.getExpectedDate");
	
	YFCDate currentDate=new YFCDate();
	currentDate.changeDate(0-sDays);
	
	logger.endTimer("ABOFPOClosureAgent.getExpectedDate");
	
	return currentDate.getString("YYYY-MM-dd");
	
    }
    


    //<Order Action="MODIFY" DocumentType="" 
    //OrderHeaderKey="" OrderName="" OrderNo="" Override="" 
    //IgnoreTransactionDependencies="" OrderComplete="Y"/>
    
    @Override
    public void executeJob(YFCDocument executeJobXml) {
	
	logger.beginTimer("ABOFPOClosureAgent.executeJob");
	
	logger.debug("Input to execute job"+executeJobXml);
	 
	YFCElement jobElem=executeJobXml.getDocumentElement();
	YFCDocument Order=YFCDocument.createDocument("Order");
	YFCElement OrderElem=Order.getDocumentElement();
	
	OrderElem.setAttributes(jobElem.getAttributes());
	OrderElem.setAttribute("Action", "MODIFY");
	OrderElem.setAttribute("Override", "Y");
	OrderElem.setAttribute("IgnoreTransactionDependencies", "Y");
	OrderElem.setAttribute("OrderComplete", "Y");
	
	logger.debug("Input to changeOrder API"+Order);
	
	invokeYantraApi("changeOrder",Order);
	
	logger.endTimer("ABOFPOClosureAgent.executeJob");
	
	
	
    }




}
