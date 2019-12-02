package com.abof.sterling.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.bridge.sterling.framework.agent.AbstractCustomBaseAgent;
import com.bridge.sterling.utils.XMLUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;

public class SendLinesToBusiness extends AbstractCustomBaseAgent {
    private static YFCLogCategory logger = YFCLogCategory.instance(SendLinesToBusiness.class);
    private YFCDocument tempXML=YFCDocument.getDocumentFor("<TaskList><Task TargetLocationId=''/></TaskList>");
    @Override
    public List<YFCDocument> getJobs(YFCDocument msgXml, YFCDocument lastMsgXml) {
	
	YFCElement root=msgXml.getDocumentElement();
	String sMaximumRecords="5000";
	String sStatus=root.getAttribute("FromStatus");
	String sNode=root.getAttribute("Node");
	String lastDiscrepancyLineKey = null;
	
	if(!YFCObject.isVoid(root.getAttribute("MaximumRecords"))){
	    sMaximumRecords = root.getAttribute("MaximumRecords");
	}
	
	logger.debug("lastMsgXml : "+lastMsgXml);
	
	if ( !YFCObject.isVoid(lastMsgXml)) {
	    YFCElement sDiscrepancyLine = lastMsgXml.getDocumentElement(); 
	    lastDiscrepancyLineKey=sDiscrepancyLine.getAttribute("DiscrepancyLineKey");
	    
	}
	
	YFCDocument inDoc= YFCDocument.createDocument("DiscrepancyLine");
	 YFCElement inElem=inDoc.getDocumentElement();
	 inElem.setAttribute("MaximumRecords", sMaximumRecords);
	 inElem.setAttribute("Status", sStatus);
	 
	 if(!YFCObject.isVoid(lastDiscrepancyLineKey)){
	     inElem.setAttribute("DiscrepancyLineKeyQryType", "GT");
	     inElem.setAttribute("DiscrepancyLineKey", lastDiscrepancyLineKey);
	     
	 }
	 
	 logger.debug("input to getDiscrepancyLineList : "+inDoc);
	 
	 YFCDocument outDoc=invokeYantraService("getDiscrepancyLineList", inDoc);
	 
	 for(YFCElement discrline:outDoc.getDocumentElement().getElementsByTagName("DiscrepancyLine")){
		    
	     discrline.setAttribute("Node", sNode);
	}
	 
	 return XMLUtil.getDocumentsForChildElements(outDoc.getDocumentElement(), "DiscrepancyLine");

    }

    @Override
    public void executeJob(YFCDocument executeJobXml) {
	HashMap<String, String> moveReqStatusMap=new HashMap<String,String>();
	HashMap<String, String> serialLocMap=new HashMap<String,String>();
	
	logger.debug("executeJobXml : "+executeJobXml);
	
	YFCElement jobElem=executeJobXml.getDocumentElement();
		
	YFCDocument template=YFCDocument.getDocumentFor("<MoveRequests>" +
                                        			"<MoveRequest MoveRequestNo='' Node='' Status=''>" +
                                        				"<MoveRequestLines>" +
                                        					"<MoveRequestLine MoveRequestLineKey='' SerialNo='' TargetLocationId=''  CancelledFlag='' />" +
                                        				"</MoveRequestLines>" +
                                        			"</MoveRequest>" +
                                        		"</MoveRequests>");

	for(YFCElement frwrd:jobElem.getElementsByTagName("Forward")){
	    if(!YFCObject.isVoid(frwrd.getAttribute("MoveRequestNo"))){
		
		moveReqStatusMap.put(frwrd.getAttribute("MoveRequestNo"),"");
		
	    }
	}
	
	logger.debug("moveReqStatusMap : "+moveReqStatusMap);
	
	for (Entry<String, String> entry : moveReqStatusMap.entrySet()){
	    String sReqNo=entry.getKey();
	    YFCDocument in =YFCDocument.getDocumentFor("<MoveRequest MoveRequestNo='"+sReqNo+"' " +
	    		"Node='"+jobElem.getAttribute("Node")+"' />");
	    
	    logger.debug("input to getMoveRequestList : "+in);
	    
	    YFCDocument out=invokeYantraApi("getMoveRequestList", in,template);
	    
	    String status=getMoveRequestStatus(out.getDocumentElement().getElementsByTagName("MoveRequest").item(0));
	    
	    entry.setValue(status);
	    
	    serialLocMap=getserialLocMap(out.getDocumentElement().getElementsByTagName("MoveRequest").item(0));
	    
	}
	
	//for(YFCElement discrline:jobElem.getElementsByTagName("DiscrepancyLine")){
	    Boolean changeStatus=true;
	    String sDiscrLineKey=jobElem.getAttribute("DiscrepancyLineKey");
	    for(YFCElement frwrd:jobElem.getElementsByTagName("Forward")){
		String sMoveNo=frwrd.getAttribute("MoveRequestNo");
		String sStatus=moveReqStatusMap.get(sMoveNo);
		if(!"CLOSED".equalsIgnoreCase(sStatus)){
		    changeStatus=false;
		    break;
		}
	    }
	    
	    YFCDocument in=YFCDocument.getDocumentFor("<DiscrepancyLine DiscrepancyLineKey='"+sDiscrLineKey+"' Status='2000' />");
	    
	    if(changeStatus){
		
		logger.debug("input to changeDiscrepancyLine : "+in);
		
		invokeYantraService("changeDiscrepancyLine", in);
		
		for(YFCElement lineDtl:jobElem.getElementsByTagName("LineDetail")){
		    String sLineDetailKey=lineDtl.getAttribute("LineDetailKey");
		    String sSerial=lineDtl.getAttribute("SerialNo");
		  
		    YFCDocument inlineDtl=YFCDocument.getDocumentFor("<LineDetail LineDetailKey='"+sLineDetailKey+"' BinLocationId='"+serialLocMap.get(sSerial)+"' />");
		    
		    logger.debug("input to changeLineDtl : "+inlineDtl);
		    
		    invokeYantraService("changeLineDtl", inlineDtl);
		    
		}
	    }
	    
	//}
	
    }

    private HashMap<String, String> getserialLocMap(YFCElement moveRequest) {
	HashMap<String, String> serialLocMap=new HashMap<String,String>();
	
	for(YFCElement moveLine:moveRequest.getElementsByTagName("MoveRequestLine")){
	    String xTargetLoc="";
	    String xSerial=moveLine.getAttribute("SerialNo");
	    String sMoveRequestLineKey=moveLine.getAttribute("MoveRequestLineKey");
	    
	    YFCDocument in=YFCDocument.getDocumentFor("<Task><TaskReferences MoveRequestLineKey='"+sMoveRequestLineKey+"'/></Task>");
	    YFCDocument docTaskList=invokeYantraApi("getTaskList", in,tempXML);
	    
	    if(docTaskList.hasChildNodes()){
		YFCElement taskElem=docTaskList.getDocumentElement().getElementsByTagName("Task").item(0);
		
		xTargetLoc=taskElem.getAttribute("TargetLocationId");
	    }
	    
	    if(YFCObject.isVoid(xTargetLoc)){
		xTargetLoc=" ";
	    }
	    serialLocMap.put(xSerial, xTargetLoc);
	    
	}
	
	return serialLocMap;
    }

    private String getMoveRequestStatus(YFCElement moveRequest) {
	boolean allLinesCancelled=true;
	String status=moveRequest.getAttribute("Status");
	for(YFCElement moveLine:moveRequest.getElementsByTagName("MoveRequestLine")){
	    if(!"Y".equalsIgnoreCase(moveLine.getAttribute("CancelledFlag"))){
		allLinesCancelled=false;
		break;
	    }
	    
	}
	
	if(allLinesCancelled){
	    return "CANCELLED";
	}
	
	return status;
    }

}
