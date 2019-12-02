package com.abof.finance.api;

import java.util.ArrayList;
import java.util.HashMap;
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

public class ABOFPublishRTVPackedSerialToFinance implements YIFCustomApi {

	private static YFCLogCategory logger = YFCLogCategory.instance(ABOFPublishRTVPackedSerialToFinance.class);

	/**
	 * This method is invoked to publish inventory changes during RTV. This is invoked through ChangeShipmentStatus. On success
	 * @param env
	 * @param inputDoc
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("unchecked")
	public Document populateInventoryChangesOnRTV(YFSEnvironment env, Document inputDoc) throws IllegalArgumentException, Exception {
		logger.beginTimer("populateInventoryChangesOnRTV");
		YFCDocument input=YFCDocument.getDocumentFor(inputDoc);
		YFCElement inDocElem=input.getDocumentElement();
		YFCElement shipmentEle = inDocElem.getElementsByTagName("Shipment").item(0);
		String shipmentKey = shipmentEle.getAttribute(FinanceConstants.SHIPMENT_KEY);
		String documentType = shipmentEle.getAttribute(FinanceConstants.DOCUMENT_TYPE);
		/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - Start*/
		ArrayList<String> shipList = isRTVDocumentType(env,shipmentKey);
		String isRTV=shipList.get(0);
		if(!"0008.ex".equals(documentType)|| !"true".equalsIgnoreCase(isRTV)){
		/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 - End*/
			
			logger.verbose("Not a RTV Shipment");
			return inputDoc;

		}

		Set<String> ItemserialList = new HashSet<String>();

		ItemserialList=(Set<String>) env.getTxnObject(shipmentKey.trim());

		logger.verbose("This is RTV shipment so going to publish RTV details to Finance");

		if (null==ItemserialList || ItemserialList.isEmpty()) {
			logger
			.verbose("Although it is an RTV shipment, shipment does not have serials. Hence not going to publish any details to Finance");
			return inputDoc;
		}
		
		env.setTxnObject("isRTV", true);
		
		List<Document> grnHeaderDocList = ABOFFinanceUtils.publishInventoryUpdateForSerialNos(env, ItemserialList, 0);
		for (int i = 0; i < grnHeaderDocList.size(); i++) {
			Document outDoc = grnHeaderDocList.get(i);
			/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854 -Start*/
			Element eleOut = outDoc.getDocumentElement();
			eleOut.setAttribute("ShipmentNo", (String) shipList.get(1));
			eleOut.setAttribute("ShipmentPacked", FinanceConstants.NO);
			 NodeList nlGRNLine = eleOut.getElementsByTagName("GRNLine");
		        for (int grnLineCount=0; grnLineCount<nlGRNLine.getLength();grnLineCount++){
		        	Element eleGRNLine = (Element) nlGRNLine.item(grnLineCount);
		        	eleGRNLine.setAttribute("ShipmentNo", (String) shipList.get(1));
		        }
			logger.verbose("ShipmentNo and ShipmentPacked appended to input");
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

	public Document putSerialsInMemory(YFSEnvironment env, Document addToContainerInput) throws Exception {
		logger.beginTimer("putSerialsInMemory");

		Set<String> packedSrls = new HashSet<String>();
		YFCDocument inDoc=YFCDocument.getDocumentFor(addToContainerInput);
		YFCElement inElem=inDoc.getDocumentElement();
		//YFCElement xAuditElem=inElem.getElementsByTagName("Shipment").item(0);
		if(null!=inElem && inElem.hasAttribute("ShipmentKey")){

			String xShipmentKey=inElem.getAttribute("ShipmentKey");
			
			/*Modified by Harish as part of RTV Debit Note Integration- JIRA 5853,5854*/
			ArrayList<String> shipList = isRTVDocumentType(env,xShipmentKey);
			String isRTV= shipList.get(0);

			if(!"true".equalsIgnoreCase(isRTV)){

				logger.verbose("Not going to store for NON-RTV Shipment operations");
				return addToContainerInput; 

			}

			
			for(YFCElement inv:inElem.getElementsByTagName("Inventory")){
			    logger.verbose("inventory Element "+inv);
				String sItemId=inv.getElementsByTagName("InventoryItem").item(0).getAttribute("ItemID");
				
				for(YFCElement srlDtl:inv.getElementsByTagName("SerialDetail")){
				    
				    logger.verbose("serialDetail Element "+srlDtl);
				    
					packedSrls.add(sItemId+","+srlDtl.getAttribute("SerialNo"));

				}

			}
			


			if(null!=xShipmentKey && xShipmentKey.trim().length()>0){

				env.setTxnObject(xShipmentKey, packedSrls);

			}else{

				logger.verbose("Unable to find the Shipment number for the RTV adjustment, Financial update will not occur");
			}
		}

		logger.endTimer("putSerialsInMemory");

		return addToContainerInput;	  

	}

	/*Return Type Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854*/
	private ArrayList<String> isRTVDocumentType(YFSEnvironment env, String xShipmentKey) throws Exception {

		/*ArrayList added by Harish for RTV Debit Note Integration- JIRA 5853,5854*/
		ArrayList<String> shiplist= new ArrayList<String>();
		Document inXML=XMLUtil.createDocument(FinanceConstants.SHIPMENT);
		Element eleGetShipListRoot = inXML.getDocumentElement();
		eleGetShipListRoot.setAttribute(FinanceConstants.SHIPMENT_KEY, xShipmentKey.trim());

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

					String documentType=xShipment.getAttribute("DocumentType");

					if("0008.ex".equals(documentType)){

						/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854*/
						shiplist.add(0,"true");
						shiplist.add(1,xShipment.getAttribute("ShipmentNo"));
						return shiplist;

					}else{
						logger.verbose("Non RTV shipment, Hence exiting the flow");
						/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854*/
						shiplist.add(0,"false");
						shiplist.add(1,"");
						return shiplist;
					}
				}

			}
		}
		/*Modified by Harish for RTV Debit Note Integration- JIRA 5853,5854*/
		shiplist.add(0,"false");
		shiplist.add(1,"");
		return shiplist;

	}

	@Override
	public void setProperties(Properties arg0) throws Exception {
	}

}
