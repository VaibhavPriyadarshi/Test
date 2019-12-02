package com.abof.sterling.agent;


import java.rmi.RemoteException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import org.w3c.dom.Document;
import com.abof.sterling.util.ExceptionHandler;
import com.yantra.interop.japi.YIFApi;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.interop.japi.YIFClientFactory;
import com.yantra.shared.ycp.YFSContext;
import com.yantra.ycp.japi.util.YCPBaseAgent;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

public class ABOFStoreTriplicateCopy extends YCPBaseAgent {

    private static YFCLogCategory logger = YFCLogCategory.instance(ABOFStoreTriplicateCopy.class);
    
    public List<Document> getJobs(YFSEnvironment env, Document inXML,Document lastXml) throws YFSException, RemoteException, YIFClientCreationException{
	logger.beginTimer("ABOFStoreTriplicateCopy.getJobs");
	
	List<Document> shipmentKeyList = new ArrayList<Document>();
	String sMaximumRecords="";
	String sGetPrinterForDoc="";
	String fromDate="";
	String toDate="";
	String documentType="0001";
	String lastShipmentKey="";
	YIFApi api=YIFClientFactory.getInstance().getLocalApi();
	
	YFCDocument xCriteria=YFCDocument.getDocumentFor(inXML);
	YFCElement xRoot=xCriteria.getDocumentElement();
	YFCDocument lastMsgXml=null;
	
	if(!YFCObject.isVoid(lastXml)){
	    lastMsgXml=YFCDocument.getDocumentFor(lastXml);
	}
	if(null!=xRoot){
		sMaximumRecords=xRoot.getAttribute("NumRecordsToBuffer");
		sGetPrinterForDoc=xRoot.getAttribute("PrintDocumentId");
		fromDate=xRoot.getAttribute("FromDate");
		toDate=xRoot.getAttribute("ToDate");
		if(!YFCObject.isVoid(xRoot.getAttribute("Document"))){
		    documentType=xRoot.getAttribute("Document");
		}
	}
	
	if(YFCObject.isVoid(fromDate)||YFCObject.isVoid(toDate)){
	    throw ExceptionHandler.handleException("UserInputError","Either From or To Date is null or blank");

	}
	
	String sDirectory=getDirectory(env,sGetPrinterForDoc);
	validateDateFormat(fromDate,toDate);
	
	logger.debug("LastMsgXml: "+lastMsgXml);
	
	if (lastMsgXml != null && !YFCObject.isVoid(lastMsgXml.getDocumentElement().getAttribute("ShipmentKey"))) {
	    YFCElement sShipment = lastMsgXml.getDocumentElement();
	    lastShipmentKey=sShipment.getAttribute("ShipmentKey");
	    
	}
	
	YFCDocument getShipmentListInp=YFCDocument.getDocumentFor("<Shipment SelectMethod='NO_LOCK' DocumentType='"+documentType+"' MaximumRecords='"+sMaximumRecords+"' " +
			(null==(lastShipmentKey)||""==(lastShipmentKey)?" ":"ShipmentKey='"+lastShipmentKey+"' ShipmentKeyQryType='GT'")+" >" +
			"<ShipmentStatusAudits>" +
				"<ShipmentStatusAudit NewStatus='1300' NewStatusDateQryType='DATERANGE' " +
				"FromNewStatusDate='"+fromDate+"' " +
				"ToNewStatusDate='"+toDate+"' />" +
			"</ShipmentStatusAudits>" +
			"</Shipment>");
	
	YFCDocument getShipmentListTemplate=YFCDocument.getDocumentFor("<Shipments>" +
									"<Shipment ShipmentKey='' ShipmentNo=''>" +
										"<ShipmentLines>" +
											"<ShipmentLine>" +
												"<Order OrderNo=''/>" +
											"</ShipmentLine>" +
										"</ShipmentLines>" +
										"<Extn ExtnInvoiceDate=''/>" +
									"</Shipment>" +
									"</Shipments>");
	
	env.setApiTemplate("getShipmentList", getShipmentListTemplate.getDocument());
	
	YFCDocument out=YFCDocument.getDocumentFor(api.getShipmentList(env, getShipmentListInp.getDocument()));
	
	env.clearApiTemplate("getShipmentList");
	
	for(YFCElement shipment:out.getDocumentElement().getElementsByTagName("Shipment")){
	    String xShipmentNo=shipment.getAttribute("ShipmentNo");
	    String xOrderNo=shipment.getElementsByTagName("Order").item(0).getAttribute("OrderNo");
	    YFCDocument temp=YFCDocument.getDocumentFor("<Shipment ShipmentKey='"+shipment.getAttribute("ShipmentKey")+"' " +
	    							"FileName='"+xOrderNo+"_BLR-"+xShipmentNo+"' " +
	    							"Directory='"+sDirectory+"'  />");
	    shipmentKeyList.add(temp.getDocument());
	}
	
	logger.endTimer("ABOFStoreTriplicateCopy.getJobs");
	return shipmentKeyList;
	
    }
    
    private void validateDateFormat(String fromDate, String toDate) {

	 SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
	 try {
	    dateFormat.parse(fromDate.trim());
	    dateFormat.parse(toDate.trim());
	} catch (ParseException e) {	    
	    e.printStackTrace();
	    throw ExceptionHandler.handleException("UserInputError","Invalid Date format: Please pass Date in yyyyMMdd format");
	}
	
    }

    @Override
    public void executeJob(YFSEnvironment env,
	    Document inXML) throws Exception {
	logger.beginTimer("ABOFStoreTriplicateCopy.executeJob");
	
	YIFApi api=YIFClientFactory.getInstance().getLocalApi();
	
	env.setTxnObject("OnlyTriplicate", "Y");
	
	YFSContext oCtx=(YFSContext)env;
	
	YFCDocument inDoc=YFCDocument.getDocumentFor(inXML);
	YFCElement inElem=inDoc.getDocumentElement();
	
	String fileName=new StringBuilder(String.valueOf(inElem.getAttribute("Directory"))).append(inElem.getAttribute("FileName")).append(".pdf").toString();
	
	api.executeFlow(oCtx,"ABOF_PrintPackingSlip", inXML);
	
	Object jasperPrintObj=oCtx.getUserObject("Yantra_PDFFile");
	JasperPrint temp = (JasperPrint) jasperPrintObj;
	
	logger.debug("fileName: "+fileName);
	logger.debug("Exporting Jasper Object to PDF file");
	
	JasperExportManager.exportReportToPdfFile(temp, fileName);
	
	logger.debug("Exported Jasper Object to PDF file");
	logger.endTimer("ABOFStoreTriplicateCopy.executeJob");
	
	
    }
    
    private String getDirectory(YFSEnvironment env,String getPrinterForDoc) throws YFSException, RemoteException, YIFClientCreationException {
	YIFApi api=YIFClientFactory.getInstance().getLocalApi();
	
	String sPrintDocumentListIp = "<PrintDocument PrintDocumentId='"+getPrinterForDoc+"'  />";
	
	
	YFCDocument docPrintDocumentList = YFCDocument.getDocumentFor(sPrintDocumentListIp);
	YFCDocument PrintDocTemplate=YFCDocument.getDocumentFor("<PrintDocuments> <PrintDocument PrintDocumentId='' PrintDocumentKey=''> <PrintDocumentTranAttribute SaveDirectory='' /> </PrintDocument> </PrintDocuments>");
	
	env.setApiTemplate("getPrintDocumentList", PrintDocTemplate.getDocument());
	
	YFCDocument docPrintDocumentOut = YFCDocument.getDocumentFor(api.getPrintDocumentList(env, docPrintDocumentList.getDocument()));
	
	env.clearApiTemplate("getPrintDocumentList");
	YFCElement elePrintDocumentDetail =null;
	try{
	    YFCElement elePrintDocumentOut = docPrintDocumentOut.getDocumentElement();
	    elePrintDocumentDetail = elePrintDocumentOut.getElementsByTagName("PrintDocumentTranAttribute").item(0);
	}catch(Exception e){
	    throw ExceptionHandler.handleException("UserInputError","Invalid PrintDocumentId passed");

	}
	    String printDir = elePrintDocumentDetail.getAttribute("SaveDirectory");

	return (new StringBuilder(String.valueOf(printDir))).append("/").toString();
}

    

}
