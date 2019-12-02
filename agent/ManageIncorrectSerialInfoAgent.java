/**
 * 
 */
package com.abof.sterling.agent;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.abof.sterling.util.ExceptionHandler;
import com.abof.sterling.util.XmlConstants;
import com.bridge.sterling.framework.agent.AbstractCustomBaseAgent;
import com.yantra.yfc.core.YFCIterable;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;

/**
 * @author Harish HN
 *
 */
public class ManageIncorrectSerialInfoAgent extends AbstractCustomBaseAgent {

	private static YFCLogCategory logger = YFCLogCategory.instance(ManageIncorrectSerialInfoAgent.class);

	@Override
	public List<YFCDocument> getJobs(YFCDocument msgXml, YFCDocument lastMsgXml) {
		String methodName = "getJobs";
		logger.beginTimer(methodName);

		List<YFCDocument> listSerialInfo = new ArrayList<YFCDocument>();
		String NoOfRecords = "";
		String fromDate="";
		String toDate="";
		String lastOrderHeaderKey="";
		String sDocumentType="";
		String sFromStatus="";
		String sToStatus="";

		YFCElement eleCriteria = msgXml.getDocumentElement();
		if(!YFCObject.isVoid(eleCriteria)){
			NoOfRecords = eleCriteria.getAttribute("NoOfRecords");
			fromDate=eleCriteria.getAttribute("FromDate");
			toDate=eleCriteria.getAttribute("ToDate");
			sDocumentType=eleCriteria.getAttribute("DocType");
			sFromStatus=eleCriteria.getAttribute("FromStatus");
			sToStatus=eleCriteria.getAttribute("ToStatus");

		}

		if(YFCObject.isVoid(fromDate)||YFCObject.isVoid(toDate)){
			throw ExceptionHandler.handleException("UserInputError","Either From or To Date is null or blank");
		}

		validateDateFormat(fromDate,toDate);

		if (!YFCObject.isVoid(lastMsgXml)) {
			logger.verbose("lastMsgXml ::"+lastMsgXml);
			YFCElement sSerialInfo = lastMsgXml.getDocumentElement();
			lastOrderHeaderKey=sSerialInfo.getAttribute("OrderHeaderKey");
		}

		YFCDocument InOrderList = YFCDocument.getDocumentFor("<Order DocumentType='"+sDocumentType+"' StatusQryType='BETWEEN' MaximumRecords='"+NoOfRecords+ "' "
				+(null==(lastOrderHeaderKey)||""==(lastOrderHeaderKey)?" ":"OrderHeaderKey='"+lastOrderHeaderKey+"' OrderHeaderKeyQryType='GT' ")
				+ "FromStatus='"+sFromStatus+"' ToStatus='"+sToStatus+"' OrderDateQryType='DATERANGE' FromOrderDate= '"
				+ fromDate + "' ToOrderDate='" + toDate + "' />");
		YFCDocument OrderListTemp = YFCDocument.getDocumentFor("<OrderList><Order/></OrderList>");

		YFCDocument outOrderList = invokeYantraApi("getOrderList", InOrderList,OrderListTemp);
		YFCElement eleOrderList = outOrderList.getDocumentElement();
		Iterator<YFCElement> itOrder = eleOrderList.getChildren("Order");
		YFCDocument docMultiApi = YFCDocument.createDocument(XmlConstants.E_MULTI_API);
		YFCElement eleMultiApi = docMultiApi.getDocumentElement();
		YFCDocument multiApiTemp = YFCDocument.getDocumentFor("<Shipments><Shipment><Containers><Container><ContainerDetails>"
				+ "<ContainerDetail><ShipmentTagSerials/><ShipmentLine/></ContainerDetail></ContainerDetails>"
				+ "</Container></Containers></Shipment></Shipments>");

		while(itOrder.hasNext()){
			YFCElement eleOrder = itOrder.next();
			YFCElement eleApi = eleMultiApi.createChild(XmlConstants.E_API);
			eleApi.setAttribute(XmlConstants.A_NAME, "getShipmentListForOrder");
			YFCElement eleInput = eleApi.createChild(XmlConstants.E_INPUT);
			eleInput.importNode(eleOrder);
			YFCElement eleTemplate = eleApi.createChild("Template");
			eleTemplate.importNode(multiApiTemp.getDocumentElement());
		}


		YFCDocument outMultiApi = invokeYantraApi("multiApi",docMultiApi);
		YFCElement eleMultiApiOut = outMultiApi.getDocumentElement();

		YFCIterable<YFCElement> itApi = eleMultiApiOut.getChildren("API");
		while(itApi.hasNext()){
			YFCElement eleApi = itApi.next();
			Iterator<YFCElement> itContDet = eleApi.getElementsByTagName("ContainerDetail").iterator();
			while(itContDet.hasNext()){
				YFCElement eleContDet = itContDet.next();				
				Iterator<YFCElement> itShipTagSer = eleContDet.getElementsByTagName("ShipmentTagSerial").iterator();
				while(itShipTagSer.hasNext()){
					YFCDocument inSerialInfo = YFCDocument.createDocument("SerialInfo");
					YFCElement eleSerialInfo = inSerialInfo.getDocumentElement();
					eleSerialInfo.setAttribute("SalesOrderNo", eleContDet.getChildElement("ShipmentLine").getAttribute("OrderNo"));
					eleSerialInfo.setAttribute("OrderHeaderKey", eleContDet.getChildElement("ShipmentLine").getAttribute("OrderHeaderKey"));
					YFCElement eleShipTagSer = itShipTagSer.next();
					eleSerialInfo.setAttribute("SerialNo", eleShipTagSer.getAttribute("SerialNo"));
					eleSerialInfo.setAttribute("ItemID", eleContDet.getAttribute("ItemID"));
					listSerialInfo.add(inSerialInfo);
				}
			}
		}	

		logger.endTimer(methodName);
		return listSerialInfo;
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
	public void executeJob(YFCDocument executeJobXml) {

		YFCElement eleSerialInfo = executeJobXml.getDocumentElement();
		YFCDocument inRecLineList =  YFCDocument.createDocument("ReceiptLine");
		YFCElement eleInRecLineList = inRecLineList.getDocumentElement();
		eleInRecLineList.setAttribute("Quantity", "1");
		eleInRecLineList.setAttribute("ItemID", eleSerialInfo.getAttribute("ItemID"));
		eleInRecLineList.setAttribute("SerialNo", eleSerialInfo.getAttribute("SerialNo"));
		YFCElement eleOrderBy = eleInRecLineList.createChild("OrderBy");
		YFCElement eleAttribute = eleOrderBy.createChild("Attribute");
		eleAttribute.setAttribute("Name", "ReceiptLineKey");
		eleAttribute.setAttribute("Desc", "N");
		YFCDocument recLineTemp = YFCDocument.getDocumentFor("<ReceiptLineList><ReceiptLine><Receipt/><ShipmentLine/>"
				+ "<OrderLine/></ReceiptLine></ReceiptLineList>");
		YFCDocument outRecLineList = invokeYantraApi("getReceiptLineList", inRecLineList,recLineTemp);
		YFCElement eleOutRecLineList = outRecLineList.getDocumentElement();
		if(!YFCObject.isVoid(eleOutRecLineList.getChildElement("ReceiptLine"))){
			Iterator<YFCElement> itRecLine = eleOutRecLineList.getChildren("ReceiptLine");
			boolean receiptFound = false;
			YFCElement eleReturnReceiptLine = null;
			while(itRecLine.hasNext()){
				YFCElement eleRecLine = itRecLine.next();
				YFCElement eleRec = eleRecLine.getChildElement("Receipt");
				if("0005".equalsIgnoreCase(eleRec.getAttribute("DocumentType"))){
					setSerialInfoInput(eleSerialInfo,eleRecLine);
					createSerialInfo(executeJobXml);
					receiptFound = true;
					break;
				}else{
					if(null == eleReturnReceiptLine){
						eleReturnReceiptLine = eleRecLine;
					}
					continue;
				}
			}

			YFCElement eleLoopReturnReceiptLine = eleReturnReceiptLine;
			while(!receiptFound){		
				YFCElement eleOrderLine = eleLoopReturnReceiptLine.getChildElement("OrderLine");
				String sDerivedFromOrderHeaderKey = eleOrderLine.getAttribute("DerivedFromOrderHeaderKey");
				String sDerivedFromOrderLineKey = eleOrderLine.getAttribute("DerivedFromOrderLineKey");
				eleLoopReturnReceiptLine = null;
				YFCDocument inLoopRecLineList = YFCDocument.createDocument("ReceiptLine");
				YFCElement eleInLoopRecLineList = inLoopRecLineList.getDocumentElement();
				eleInLoopRecLineList.setAttribute("Quantity", "1");
				eleInLoopRecLineList.setAttribute("ItemID", eleSerialInfo.getAttribute("ItemID"));
				YFCElement eleInLoopRecLineOrderBy = eleInLoopRecLineList.createChild("OrderBy");
				YFCElement eleInLoopRecLineAttribute = eleInLoopRecLineOrderBy.createChild("Attribute");
				eleInLoopRecLineAttribute.setAttribute("Name", "ReceiptLineKey");
				eleInLoopRecLineAttribute.setAttribute("Desc", "N");

				YFCDocument inShipListOrder = YFCDocument.createDocument("Order");
				YFCElement eleOrder = inShipListOrder.getDocumentElement();
				eleOrder.setAttribute("OrderHeaderKey",sDerivedFromOrderHeaderKey);
				YFCDocument shipListTemp = YFCDocument.getDocumentFor("<Shipments><Shipment><Containers><Container><ContainerDetails>"
						+ "<ContainerDetail><ShipmentTagSerials/><ShipmentLine/></ContainerDetail></ContainerDetails>"
						+ "</Container></Containers></Shipment></Shipments>");
				YFCDocument outShipListOrder = invokeYantraApi("getShipmentListForOrder",inShipListOrder,shipListTemp);
				YFCElement eleOutShip = outShipListOrder.getDocumentElement().getChildElement("Shipment");
				Iterator<YFCElement> itContDet = eleOutShip.getElementsByTagName("ContainerDetail").iterator();

				while(itContDet.hasNext()){
					YFCElement eleContDet = itContDet.next();
					YFCElement eleShipLine = eleContDet.getChildElement("ShipmentLine");
					if(!eleShipLine.getAttribute("OrderLineKey").equalsIgnoreCase(sDerivedFromOrderLineKey)){
						continue;
					}

					double dShipLineQty = eleShipLine.getDoubleAttribute("Quantity");

					if(dShipLineQty > 1){
						HashMap<String,YFCElement> hmSerRecLine = new HashMap<String,YFCElement>();
						Iterator<YFCElement> itShipTagSer = eleContDet.getElementsByTagName("ShipmentTagSerial").iterator();
						while(itShipTagSer.hasNext()){						
							YFCElement eleShipTagSer = itShipTagSer.next();

							if(eleSerialInfo.getAttribute("SerialNo").equalsIgnoreCase(eleShipTagSer.getAttribute("SerialNo"))){
								eleSerialInfo.setAttribute("ReasonCode", "M");
								eleSerialInfo.setAttribute("ReasonText", "Manually Adjusted Serial");
								createSerialInfo(executeJobXml);
								receiptFound = true;
								break;
							}

							eleInLoopRecLineList.setAttribute("SerialNo", eleShipTagSer.getAttribute("SerialNo"));
							YFCDocument loopRecLineTemp = YFCDocument.getDocumentFor("<ReceiptLineList><ReceiptLine><Receipt/><ShipmentLine/>"
									+ "<OrderLine/></ReceiptLine></ReceiptLineList>");
							YFCDocument outLoopRecLineList = invokeYantraApi("getReceiptLineList", inLoopRecLineList,loopRecLineTemp);
							YFCElement  eleOutLoopRecLineList = outLoopRecLineList.getDocumentElement();


							if(!YFCObject.isVoid(eleOutLoopRecLineList.getChildElement("ReceiptLine"))){
								Iterator<YFCElement> itRecLines = eleOutLoopRecLineList.getChildren();
								while(itRecLines.hasNext()){
									YFCElement eleRecLine = itRecLines.next();
									if(!hmSerRecLine.containsKey(eleRecLine.getAttribute("SerialNo"))){
										hmSerRecLine.put(eleRecLine.getAttribute("SerialNo"),
												eleRecLine);
									}else{											
										if("0005".equalsIgnoreCase(eleRecLine.getChildElement("Receipt").getAttribute("DocumentType"))){
											hmSerRecLine.put(eleRecLine.getAttribute("SerialNo"),eleRecLine);
										}
									}
								}
							}
						}

						if(receiptFound){
							break;
						}

						if(hmSerRecLine.isEmpty()){
							eleSerialInfo.setAttribute("ReasonCode", "M");
							eleSerialInfo.setAttribute("ReasonText", "Manually Adjusted Serial");
							createSerialInfo(executeJobXml);
							receiptFound = true;
						}else if(dShipLineQty != hmSerRecLine.size()){
							eleSerialInfo.setAttribute("ReasonCode", "C");
							eleSerialInfo.setAttribute("ReasonText", "Cannot Determine Receipt Info.");
							createSerialInfo(executeJobXml);
							receiptFound = true;
						}else{
							String sRecHdrKey = "";
							boolean allSame = false;
							YFCElement eleFinalRecLine = null; 
							Iterator<String> itHmSerRecLine = hmSerRecLine.keySet().iterator();
							while(itHmSerRecLine.hasNext()){
								String sSerial = itHmSerRecLine.next();
								YFCElement eleMapRecLine = hmSerRecLine.get(sSerial);
								YFCElement eleMapRec = eleMapRecLine.getChildElement("Receipt");
								if("".equalsIgnoreCase(sRecHdrKey)){
									sRecHdrKey = eleMapRec.getAttribute("ReceiptHeaderKey");
								}else if(sRecHdrKey.equalsIgnoreCase(eleMapRec.getAttribute("ReceiptHeaderKey"))){
									allSame = true;
									eleFinalRecLine = eleMapRecLine;
								}else if(!sRecHdrKey.equalsIgnoreCase(eleMapRec.getAttribute("ReceiptHeaderKey"))){
									allSame = false;
									break;
								}
							}
							if(allSame){
								if("0005".equalsIgnoreCase(eleFinalRecLine.getChildElement("Receipt").getAttribute("DocumentType"))){
									setSerialInfoInput(eleSerialInfo,eleFinalRecLine);
								}else{
									eleLoopReturnReceiptLine = eleFinalRecLine;
									break;
								}				    
							}else{
								eleSerialInfo.setAttribute("ReasonCode", "C");
								eleSerialInfo.setAttribute("ReasonText", "Cannot Determine Receipt Info.");
							}
							createSerialInfo(executeJobXml);
							receiptFound = true;
						}

					}else if(dShipLineQty == 1){
						YFCElement eleShipTagSer = eleContDet.getElementsByTagName("ShipmentTagSerial").item(0);

						if(eleSerialInfo.getAttribute("SerialNo").equalsIgnoreCase(eleShipTagSer.getAttribute("SerialNo"))){
							eleSerialInfo.setAttribute("ReasonCode", "M");
							eleSerialInfo.setAttribute("ReasonText", "Manually Adjusted Serial");
							createSerialInfo(executeJobXml);
							receiptFound = true;
							break;
						}

						eleInLoopRecLineList.setAttribute("SerialNo", eleShipTagSer.getAttribute("SerialNo"));
						YFCDocument loopRecLineTemp = YFCDocument.getDocumentFor("<ReceiptLineList><ReceiptLine><Receipt/><ShipmentLine/>"
								+ "<OrderLine/></ReceiptLine></ReceiptLineList>");
						YFCDocument outLoopRecLineList = invokeYantraApi("getReceiptLineList", inLoopRecLineList,loopRecLineTemp);
						YFCElement  eleOutLoopRecLineList = outLoopRecLineList.getDocumentElement();
						if(!YFCObject.isVoid(eleOutLoopRecLineList.getChildElement("ReceiptLine"))){
							Iterator<YFCElement> itRecLines = eleOutLoopRecLineList.getChildren();
							while(itRecLines.hasNext()){
								YFCElement eleRecLine = itRecLines.next();
								if("0005".equalsIgnoreCase(eleRecLine.getChildElement("Receipt").getAttribute("DocumentType"))){
									setSerialInfoInput(eleSerialInfo,eleRecLine);
									createSerialInfo(executeJobXml);
									receiptFound = true;
									break;
								}else{
									if(null == eleLoopReturnReceiptLine){
										eleLoopReturnReceiptLine = eleRecLine;
									}
									continue;
								}
							}
						}else{
							eleSerialInfo.setAttribute("ReasonCode", "M");
							eleSerialInfo.setAttribute("ReasonText", "Manually Adjusted Serial");
							createSerialInfo(executeJobXml);
							receiptFound = true;
						}
						break;
					}			
				}
			}
		}else{
			eleSerialInfo.setAttribute("ReasonCode", "M");
			eleSerialInfo.setAttribute("ReasonText", "Manually Adjusted Serial");
			createSerialInfo(executeJobXml);
		}

	}

	private void setSerialInfoInput(YFCElement eleSerialInfo, YFCElement eleRecLine) {
		String sShipKey="";
		YFCElement eleShipLine = eleRecLine.getChildElement("ShipmentLine");
		YFCElement eleRec = eleRecLine.getChildElement("Receipt");

		eleSerialInfo.setAttribute("ReceiptNo", eleRec.getAttribute("ReceiptNo"));
		eleSerialInfo.setAttribute("ReceiptDate", eleRec.getAttribute("ReceiptDate"));

		if(YFCObject.isVoid(eleShipLine)){
			eleSerialInfo.setAttribute("PurchaseOrderNo",eleRecLine.getAttribute("OrderNo"));
			sShipKey =eleRec.getAttribute("ShipmentKey");

		}else{
			eleSerialInfo.setAttribute("PurchaseOrderNo",eleShipLine.getAttribute("OrderNo"));
			sShipKey =eleShipLine.getAttribute("ShipmentKey");
		}

		YFCDocument inShipList = YFCDocument.getDocumentFor("<Shipment ShipmentKey='"+sShipKey+"'/>");
		YFCDocument shipListTemp = YFCDocument.getDocumentFor("<Shipments><Shipment><Extn/></Shipment></Shipments>");
		YFCDocument outShipList = invokeYantraApi("getShipmentList", inShipList, shipListTemp);
		YFCElement eleShip = outShipList.getDocumentElement().getChildElement("Shipment");
		eleSerialInfo.setAttribute("InvoiceNo", eleShip.getAttribute("PickticketNo"));
		eleSerialInfo.setAttribute("InvoiceDate", eleShip.getChildElement("Extn").getAttribute("ExtnInvoiceDate"));
		eleSerialInfo.setAttribute("VendorCode", eleShip.getAttribute("SellerOrganizationCode"));
	}

	private void createSerialInfo(YFCDocument inSerialInfo) {
		invokeYantraService("createIncorrectSerialInfo", inSerialInfo);
	}
}
