package com.abof.finance.api;

import java.util.HashMap;
import java.util.Properties;

import org.w3c.dom.Document;

import com.abof.finance.utils.FinanceConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

public class ABOFPublishManualInventoryUpdate implements YIFCustomApi {
    private static YFCLogCategory logger = YFCLogCategory.instance(ABOFPublishManualInventoryUpdate.class);
    private Properties _properties = null;
    private HashMap<String,Integer> sAdjustmentReasons = new HashMap<String,Integer>();

/* Message to be sent to finance
<AdjustmentList>
<Adjustment ItemID="" ReasonCode="" Quantity="" />
<!--Quantity will be either -ve or +ve -->
</AdjustmentList>

*/
	
	private void publishManualInventoryUpdate(YFSEnvironment env, String xItem,String sReasonCode,String Quantity) throws Exception {
	    logger.beginTimer("publishManualInventoryUpdate");
	    
	    YFCDocument financeDoc=YFCDocument.parse("<AdjustmentList>" +
	    		"<Adjustment ItemID='"+xItem+"' ReasonCode='"+sReasonCode+"' Quantity='"+Quantity+"' />" +
	    		"</AdjustmentList>");
	    logger.verbose("Message to be sent to finance Queue: "+financeDoc);
	    
	    XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q, financeDoc.getDocument());
	    
	    logger.verbose("Message Sent successfully");
	    logger.endTimer("publishManualInventoryUpdate");  
	}
	
	public Document publishSerialArrivedAtNode(YFSEnvironment env, Document inputDoc) throws Exception {
	    logger.beginTimer("publishSerialArrivedAtNode");
	    
	    String sReasonCode=(String) env.getTxnObject("AdjustmentReason");
	    intializeReasonCodes();
	    
	    logger.verbose("sAdjustmentReasons used for publishSerialArrivedAtNode: "+ sAdjustmentReasons.entrySet());
	    
	    if(null!=sReasonCode && sReasonCode.trim().length()>0 && sAdjustmentReasons.containsKey(sReasonCode)){
		    YFCDocument inDoc=YFCDocument.getDocumentFor(inputDoc);
		    YFCElement inElem=inDoc.getDocumentElement();
		    YFCElement xInventoryItem=inElem.getElementsByTagName("InventoryItem").item(0);
		    String xItem=xInventoryItem.getAttribute("ItemID").trim();
		    
		    publishManualInventoryUpdate(env, xItem,sReasonCode,"1");
		    
	    }
	    
	    logger.endTimer("publishSerialArrivedAtNode");  

	    return inputDoc;
	    
	}
	
	public Document publishSerialLeftNode(YFSEnvironment env, Document inputDoc) throws Exception {
	    logger.beginTimer("publishSerialLeftNode");
	    
	    String sReasonCode=(String) env.getTxnObject("AdjustmentReason");
	    intializeReasonCodes();
	    
	    logger.verbose("sAdjustmentReasons used for publishSerialLeftNode: "+ sAdjustmentReasons.entrySet());
	    
	    if(null!=sReasonCode && sReasonCode.trim().length()>0 && sAdjustmentReasons.containsKey(sReasonCode)){
		    YFCDocument inDoc=YFCDocument.getDocumentFor(inputDoc);
		    YFCElement inElem=inDoc.getDocumentElement();
		    YFCElement xInventoryItem=inElem.getElementsByTagName("InventoryItem").item(0);
		    String xItem=xInventoryItem.getAttribute("ItemID").trim();
		    
		    publishManualInventoryUpdate(env, xItem,sReasonCode,"-1");
		    
	    }
	    logger.endTimer("publishSerialLeftNode");  

	    return inputDoc;
	    
	}
	
	public Document putAdjustmentReasonsInMemory(YFSEnvironment env, Document inputDoc) throws Exception {
	    logger.beginTimer("putAdjustmentReasonsInMemory");
	    YFCDocument inDoc=YFCDocument.getDocumentFor(inputDoc);
	    YFCElement inElem=inDoc.getDocumentElement();
	    YFCElement xAudit=inElem.getElementsByTagName("Audit").item(0);
	    
	    String sReasonCode=xAudit.getAttribute("ReasonCode");
	    
	    if(null!=sReasonCode && sReasonCode.trim().length()>0){
		
		env.setTxnObject("AdjustmentReason", sReasonCode);
		
	    }
	    logger.endTimer("putAdjustmentReasonsInMemory");  

	    return inputDoc;
	    
	}
	
	
	public void setProperties(Properties _prop) throws Exception {
	    // TODO Auto-generated method stub
	    _properties = _prop;
	    
	}
	
	private void intializeReasonCodes(){
	    logger.beginTimer("intializeReasonCodes");
	    
	    String sReasonList = this._properties.getProperty("ManualAdjustmentReasons");
		if (null != sReasonList && sReasonList.trim().length()>0) {
		    for(String sReason:sReasonList.split(",")){
			sAdjustmentReasons.put(sReason.trim(), 1);
		    }
		}
	    logger.verbose("Configured sAdjustmentReasons: "+ sReasonList);
	    logger.endTimer("intializeReasonCodes");  

	}

	
	
	
    

}
