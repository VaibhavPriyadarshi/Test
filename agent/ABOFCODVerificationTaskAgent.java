/*##########################################################################################
 *
 * Project Name 		 : ABOF
 * 

 * Module        	 	 : OMS - COD Verification
 * Author        	 	 : Bhamar Singh
 * Date          	 	 : Mar 24, 2016 
 *
 * Description   	 	 : 
 *
 * Change Revision
 * -------------------------------------------------------------------------------------------
 * Date         	Author    				Version#	Remarks/Description                      
 * -------------------------------------------------------------------------------------------
 * 03-24-2016       Bhamar Singh				1.0		this class is used from custom agent ABOFCODVerificationAgentServer
 * 05-20-2016       Bhagyamma R					1.1     Added 9AM to 9PM logic
 *  			
###########################################################################################*/

package com.abof.sterling.agent;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.json.utils.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.abof.sterling.util.XmlConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.yantra.ycp.japi.util.YCPBaseTaskAgent;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/**
 * This class is invoked from custom transaction COD_VERIFICATION.0001.ex
 * 
 * @author Bhamar Singh
 * 
 */
public class ABOFCODVerificationTaskAgent extends YCPBaseTaskAgent {
	private static YFCLogCategory logger = YFCLogCategory
			.instance(ABOFCODVerificationTaskAgent.class);

	public Document executeTask(YFSEnvironment env, Document docTaskQInput)
			throws Exception {

		logger.beginTimer("**** ABOFCODVerificationTaskAgent.executeTask() - START ****");
		logger.debug("Input XML to ABOFCODVerificationTaskAgent.executeTask() :\n\n"
				+ XMLUtil.serialize(docTaskQInput));
		Document docTaskQInputClone = XMLUtil.getDocument(
				docTaskQInput.getDocumentElement(), true);

		final String strDataKey = XMLUtil.getFirstAttributeValue(docTaskQInput,
				XmlConstants.A_DATA_KEY);
		final String strCreatets = XMLUtil.getFirstAttributeValue(
				docTaskQInput, XmlConstants.A_CREATETS);
		String strAvailableDate = XMLUtil.getFirstAttributeValue(docTaskQInput,
				XmlConstants.A_AVAILABLE_DATE);

		// Read the criteria parameters.
		final String strTimeNoHold = XMLUtil.getFirstAttributeValue(
				docTaskQInput, XmlConstants.A_TIME_NO_HOLD);
		final String strTimeLowRiskHold = XMLUtil.getFirstAttributeValue(
				docTaskQInput, XmlConstants.A_TIME_LOW_RISK_HOLD);
		final String strTimeHighRiskHold = XMLUtil.getFirstAttributeValue(
				docTaskQInput, XmlConstants.A_TIME_HIGH_RISK_HOLD);

		// Parse the values to get the integers.
		final int iTimeNoHold = Integer.parseInt(strTimeNoHold);
		final int iTimeLowRiskHold = Integer.parseInt(strTimeLowRiskHold);
		final int iTimeHighRiskHold = Integer.parseInt(strTimeHighRiskHold);

		// Prepare input to GetOrdListCODVerification service.
		Document docInputGOL = XMLUtil.createDocument(XmlConstants.E_ORDER);
		Element eleGOLInputOrder = docInputGOL.getDocumentElement();
		eleGOLInputOrder.setAttribute(XmlConstants.A_ORDER_HEADER_KEY,
				strDataKey);
		String strSystemTime = XMLUtil
				.getSystemTime(XmlConstants.STERLING_DATE_FORMAT);
		double dDiffInMins = XMLUtil.getDateDiffInMins(strCreatets,
				strSystemTime);

		// Invoke GetOrdListCODVerification service.
		Document docGetOrderListOutput = XMLUtil.invokeService(env,
				XmlConstants.GET_ORDER_LIST_SERVICE_COD, docInputGOL);
		logger.debug("Output XML from ABOFCODVerificationTaskAgent.executeTask() :\n\n"
				+ XMLUtil.serialize(docGetOrderListOutput));

		// Process the response from the service-GetOrdListCODVerification.
		Element eleOrder = XMLUtil.getFirstElementByName(
				docGetOrderListOutput.getDocumentElement(),
				XmlConstants.E_ORDER);
		String strOrderType = eleOrder.getAttribute(XmlConstants.A_ORDER_TYPE);
		if (XmlConstants.ORDER_TYPE_B2B.equalsIgnoreCase(strOrderType)){
			registerProcessCompletion(env, docTaskQInputClone,
					strAvailableDate, XmlConstants.NO);
		}else{
		String strStatus = eleOrder.getAttribute(XmlConstants.ATTR_STATUS);
		String strHoldFlag = eleOrder.getAttribute(XmlConstants.A_HOLD_FLAG);
		NodeList nlOrderHoldType = eleOrder
				.getElementsByTagName(XmlConstants.ELE_HOLD_TYPE);
		boolean blIsLowRiskHold = false;
		boolean blIsHighRiskHold = false;
		double dDiffInMinsForHold = 0.0;

		// Verifying hold related conditions.
		String strHoldDate = "";
		for (int i = 0; i < nlOrderHoldType.getLength(); i++) {
			Element eleOrderHoldType = (Element) nlOrderHoldType.item(i);
			String strHoldStatus = eleOrderHoldType
					.getAttribute(XmlConstants.A_STATUS);
			String strHoldType = eleOrderHoldType
					.getAttribute(XmlConstants.A_HOLD_TYPE);
			strHoldDate = eleOrderHoldType
					.getAttribute(XmlConstants.A_LAST_HOLD_TYPE_DATE);
			// yyyy-MM-dd'T'HH:mm:ss
			if (XmlConstants.HOLD_STATUS_CREATED
					.equalsIgnoreCase(strHoldStatus)
					&& XmlConstants.HOLD_TYPE_LOW_RISK_HOLD
							.equalsIgnoreCase(strHoldType)) {
				blIsLowRiskHold = true;
				dDiffInMinsForHold = XMLUtil.getDateDiffInMins(strHoldDate,
						strSystemTime);
				break;
			}
			if (XmlConstants.HOLD_STATUS_CREATED
					.equalsIgnoreCase(strHoldStatus)
					&& XmlConstants.HOLD_TYPE_HIGH_RISK_HOLD
							.equalsIgnoreCase(strHoldType)) {
				blIsHighRiskHold = true;
				dDiffInMinsForHold = XMLUtil.getDateDiffInMins(strHoldDate,
						strSystemTime);
				break;
			}
		}



		// Check if order is still stuck in created status and there is no hold
		// on the order.
		if (XmlConstants.ORDER_STATUS_CREATED.equalsIgnoreCase(strStatus)
				&& XmlConstants.NO.equalsIgnoreCase(strHoldFlag)) {
			if (dDiffInMins >= iTimeNoHold) {

				// call changeOrderStatus to verify the order
				callChangeOrderStatus(env, strDataKey);
				// call registerProcessCompletion to remove the entry from
				// yfs_task_q.
				registerProcessCompletion(env, docTaskQInputClone,
						strAvailableDate, XmlConstants.NO);
			} else {

				// call registerprocessCompletion to set AvailableDate to
				// sysdate + 10min
				strAvailableDate = XMLUtil.addSecs(XMLUtil
						.getSystemTime(XmlConstants.STERLING_DATE_FORMAT),
						XmlConstants.STERLING_DATE_FORMAT, iTimeNoHold * 60);
				registerProcessCompletion(env, docTaskQInputClone,
						strAvailableDate, XmlConstants.YES);
			}

		} else {
			
			// Invoke getCommonCodeList Method
			Document getCommonCodeList = getCommoncodeList(env);

			String sStartTime = XMLUtil.getAttributeFromXPath(getCommonCodeList,
					XmlConstants.XPATH_COMMOMCODE_CODEVALUE
							+ XmlConstants.START_TIME + "']/@CodeShortDescription");

			String sEndTime = XMLUtil.getAttributeFromXPath(getCommonCodeList,
					XmlConstants.XPATH_COMMOMCODE_CODEVALUE + XmlConstants.END_TIME
							+ "']/@CodeShortDescription");
			

		
			Date startTimeDate = new SimpleDateFormat(XmlConstants.STERLING_TIME_FORMAT).parse(sStartTime);
			Calendar calendar1 = Calendar.getInstance();
			calendar1.setTime(startTimeDate);
			logger.debug("startTimeDate:" + calendar1.getTime());

			Date endTimeDate = new SimpleDateFormat(XmlConstants.STERLING_TIME_FORMAT).parse(sEndTime);
			Calendar calendar2 = Calendar.getInstance();
			calendar2.setTime(endTimeDate);
			logger.debug("endTimeDate:" + calendar2.getTime());
			
			String strHoldTime = strHoldDate.substring(11, 19);
			logger.debug("strHoldTime:" + strHoldTime);
			Date holdDate = new SimpleDateFormat(XmlConstants.STERLING_TIME_FORMAT).parse(strHoldTime);
			Calendar calendar3 = Calendar.getInstance();
			calendar3.setTime(holdDate);
			logger.debug("calendar3.getTime():" + calendar3.getTime());
			Date holdDateformatted = calendar3.getTime();


			String strCutoffTime = XmlConstants.CUT_OFF_TIME;
			Date CutoffTimeDate = new SimpleDateFormat(XmlConstants.STERLING_TIME_FORMAT)
					.parse(strCutoffTime);
			Calendar calendar4 = Calendar.getInstance();
			calendar4.setTime(CutoffTimeDate);
			logger.debug("CutoffTimeDate:" + calendar4.getTime());

			if (blIsLowRiskHold) {
				if (dDiffInMinsForHold >= iTimeLowRiskHold) {
					// call changeOrderStatus to verify the order and call
					// changeOrder to resolve the hold.
					resolveHold(env, strDataKey,
							XmlConstants.HOLD_TYPE_LOW_RISK_HOLD);
					callChangeOrderStatus(env, strDataKey);
					// call registerProcessCompletion to remove the entry from
					// yfs_task_q.
					registerProcessCompletion(env, docTaskQInputClone,
							strAvailableDate, XmlConstants.NO);
				} else {
					// checks whether the hold time is not in between
					// 09:00:00 and 21:00:00

					if (!(holdDateformatted.after(calendar1.getTime()) && holdDateformatted
							.before(calendar2.getTime()))) {
					
						logger.debug("Hold time is not in between 09:00:00 and 21:00:00");

						if (holdDateformatted.after(calendar2.getTime())
								&& holdDateformatted
										.before(calendar4.getTime())) {
							logger.debug("hold time is  in between 21:00:00 and 23:59:59");

							// call registerprocessCompletion to set
							// AvailableDate to next day + 9AM +4 hours
							String strCCStartTime = XMLUtil.addDays(
									strHoldDate, 1) + 'T' + sStartTime;

							strAvailableDate = XMLUtil.addSecs(strCCStartTime,
									XmlConstants.STERLING_DATE_FORMAT,
									iTimeLowRiskHold * 60);
							logger.debug("strAvailableDate:"+strAvailableDate);
							registerProcessCompletion(env, docTaskQInputClone,
									strAvailableDate, XmlConstants.YES);

						} else {
							
							logger.debug("hold time is  in between 00:00:00 and 09:00:00");
							// call registerprocessCompletion to set
							// AvailableDate to same day + 9AM +4 hours

							String strCCStartTime = (strHoldDate.substring(0,
									11)) + sStartTime;
							strAvailableDate = XMLUtil.addSecs(strCCStartTime,
									XmlConstants.STERLING_DATE_FORMAT,
									iTimeLowRiskHold * 60);
							logger.debug("strAvailableDate:"+strAvailableDate);
							registerProcessCompletion(env, docTaskQInputClone,
									strAvailableDate, XmlConstants.YES);

						}

					}else {
						
						logger.debug("Hold time is  in between 09:00:00 and 21:00:00");
						
						//get the difference between endtime(21:00:00) and holdtime
						
						long diff = endTimeDate.getTime() - holdDate.getTime();
						 long previousDayMins = TimeUnit.MILLISECONDS.toMinutes(diff);
						 logger.debug(previousDayMins + " previousDayMins, ");
						 
						 if(previousDayMins>=iTimeLowRiskHold){
							 
							 logger.debug("previousDayMins is greater than or equal to 4 hours(iLowriskThreshold)");
								// call registerprocessCompletion to set
								// AvailableDate to add 4 hours
					  		  strAvailableDate = XMLUtil.addSecs(strHoldDate, 
										XmlConstants.STERLING_DATE_FORMAT, iTimeLowRiskHold*60) ;
					  		logger.debug("strAvailableDate:"+strAvailableDate);
					  		
							registerProcessCompletion(env, docTaskQInputClone,
									strAvailableDate, XmlConstants.YES);
						 }else{
							 logger.debug("previousDayMins is less than 4 hours(iLowriskThreshold)");
								// call registerprocessCompletion to set
								// AvailableDate to add 1 day +9AM + remanining hours
							 String strcalculatedDate = XMLUtil.addDays(strHoldDate, 1)+'T'+sStartTime;
							 int intpreviousDayMins = (int) previousDayMins;
								
							 int intremainingmins = iTimeLowRiskHold-intpreviousDayMins;
							 logger.debug("intremainingmins:"+intremainingmins);
							 
					  		  strAvailableDate = XMLUtil.addSecs(strcalculatedDate, 
										XmlConstants.STERLING_DATE_FORMAT, intremainingmins*60) ;
					  		 logger.debug("strAvailableDate:"+strAvailableDate);
					 		registerProcessCompletion(env, docTaskQInputClone,
									strAvailableDate, XmlConstants.YES);
						 }
						
						
						
					}

				}
			} else if (blIsHighRiskHold) {
				if (dDiffInMinsForHold >= iTimeHighRiskHold) {
					// call cancelOrder() to cancel the order.
					cancelOrder(env, strDataKey);
				} else {

					if (!(holdDateformatted.after(calendar1.getTime()) && holdDateformatted
							.before(calendar2.getTime()))) {
					
						logger.debug("Hold time is not in between 09:00:00 and 21:00:00");
						if (holdDateformatted.after(calendar2.getTime())
								&& holdDateformatted
										.before(calendar4.getTime())) {
							logger.debug("hold time is  in between 21:00:00 and 23:59:59");

							// call registerprocessCompletion to set
							// AvailableDate to next day + 9AM +12 hours
							String strCCStartTime = XMLUtil.addDays(
									strHoldDate, 1) + 'T' + sStartTime;

							strAvailableDate = XMLUtil.addSecs(strCCStartTime,
									XmlConstants.STERLING_DATE_FORMAT,
									iTimeHighRiskHold * 60);
							logger.debug("strAvailableDate:"+strAvailableDate);

							registerProcessCompletion(env, docTaskQInputClone,
									strAvailableDate, XmlConstants.YES);

						}else {
							
							logger.debug("hold time is  in between 00:00:00 and 09:00:00");
							// call registerprocessCompletion to set
							// AvailableDate to same day + 9AM +12 hours

							String strCCStartTime = (strHoldDate.substring(0,
									11)) + sStartTime;
							strAvailableDate = XMLUtil.addSecs(strCCStartTime,
									XmlConstants.STERLING_DATE_FORMAT,
									iTimeHighRiskHold * 60);
							logger.debug("strAvailableDate:"+strAvailableDate);
							registerProcessCompletion(env, docTaskQInputClone,
									strAvailableDate, XmlConstants.YES);

						}
}else {
						
						logger.debug("Hold time is  in between 09:00:00 and 21:00:00");
						
						//get the difference between endtime(21:00:00) and holdtime
						
						long diff = endTimeDate.getTime() - holdDate.getTime();
						 long previousDayMins = TimeUnit.MILLISECONDS.toMinutes(diff);
						 logger.debug(previousDayMins + " previousDayMins, ");
						 
						 if(previousDayMins>=iTimeHighRiskHold){
							 
							 logger.debug("previousDayMins is greater than or equal to  12 hours(iTimeHighRiskHold)");
								// call registerprocessCompletion to set
								// AvailableDate to add 12 hours
					  		  strAvailableDate = XMLUtil.addSecs(strHoldDate, 
										XmlConstants.STERLING_DATE_FORMAT, iTimeHighRiskHold*60) ;
					  		logger.debug("strAvailableDate:"+strAvailableDate);
					  		
					  		registerProcessCompletion(env, docTaskQInputClone,
									strAvailableDate, XmlConstants.YES);
					  		
						 }else{
							 logger.debug("previousDayMins is less than 12 hours(iTimeHighRiskHold)");
							 
								// call registerprocessCompletion to set
								// AvailableDate to add 1 day +9AM + remanining hours
							 
							 String strcalculatedDate = XMLUtil.addDays(strHoldDate, 1)+'T'+sStartTime;
							 int intpreviousDayMins = (int) previousDayMins;
								
							 int intremainingmins = iTimeHighRiskHold-intpreviousDayMins;
							 logger.debug("intremainingmins:"+intremainingmins);
							 
					  		  strAvailableDate = XMLUtil.addSecs(strcalculatedDate, 
										XmlConstants.STERLING_DATE_FORMAT, intremainingmins*60) ;
					  		 logger.debug("strAvailableDate:"+strAvailableDate);
					  		 
					  		registerProcessCompletion(env, docTaskQInputClone,
									strAvailableDate, XmlConstants.YES);
						 }
						
					}
					
				}
			} else {
				// call registerprocessCompletion to remove the record from
				// YFS_TASK_Q table
				registerProcessCompletion(env, docTaskQInputClone,
						strAvailableDate, XmlConstants.NO);
			}
		}
		}
		logger.debug("Output XML from ABOFCODVerificationTaskAgent.executeTask() :\n\n"
				+ XMLUtil.serialize(docTaskQInput));
		logger.endTimer("**** ABOFCODVerificationTaskAgent.executeTask() - Success - END ****");
		return docTaskQInput;
	}

	/**
	 * This method invokes the registerProcessCompletion API to keep the task
	 * open and updated the NewTaskDate accordingly
	 * 
	 * @param yfsEnv
	 * @param docTaskQInput
	 * @param strAvailableDate
	 * @throws Exception
	 */
	private void registerProcessCompletion(YFSEnvironment yfsEnv,
			Document docTaskQInput, String strAvailableDate,
			String strKeepTaskOpen) throws Exception {

		logger.debug("**** Method - ABOFCODVerificationTaskAgent.registerProcessCompletion() - START **** ");

		logger.debug("Task Q input XML to ABOFCODVerificationTaskAgent.registerProcessCompletion() :\n\n"
				+ XMLUtil.serialize(docTaskQInput));

		Document docInput = XMLUtil
				.createDocument(XmlConstants.E_REGISTER_PROCESS_COMPLETION_INPUT);

		// Stamp KeepTaskOpne="Y" to keep the task q entry in the table.
		docInput.getDocumentElement().setAttribute(
				XmlConstants.A_KEEP_TASK_OPEN, strKeepTaskOpen);
		docInput.getDocumentElement().setAttribute(
				XmlConstants.A_NEW_TASK_DATE, strAvailableDate);

		Element eleCurrentTask = docInput
				.createElement(XmlConstants.E_CURRENT_TASK);
		eleCurrentTask
				.setAttribute(XmlConstants.A_DATA_KEY, XMLUtil
						.getFirstAttributeValue(docTaskQInput,
								XmlConstants.A_DATA_KEY));
		eleCurrentTask.setAttribute(XmlConstants.A_TASK_Q_KEY, XMLUtil
				.getFirstAttributeValue(docTaskQInput,
						XmlConstants.A_TASK_Q_KEY));
		eleCurrentTask.setAttribute(XmlConstants.A_DATA_TYPE,
				XMLUtil.getFirstAttributeValue(docTaskQInput,
						XmlConstants.A_DATA_TYPE));
		eleCurrentTask.setAttribute(XmlConstants.A_TRANSACTION_KEY, XMLUtil
				.getFirstAttributeValue(docTaskQInput,
						XmlConstants.A_TRANSACTION_KEY));
		docInput.getDocumentElement().appendChild(eleCurrentTask);
		logger.debug("ABOFCODVerificationTaskAgent:registerProcessCompletion:Input xml for 'registerProcessCompletion' API "
				+ XMLUtil.serialize(docInput));

		// Invoke registerProcessCompletion API.
		XMLUtil.invokeAPI(yfsEnv, XmlConstants.API_REGISTER_PROCESS_COMPLETION,
				docInput);

		logger.debug(" **** Method - ABOFCODVerificationTaskAgent.registerProcessCompletion() - END **** ");
	}

	/**
	 * This method invokes the changeOrderStatus API to change the order status
	 * as Verified
	 * 
	 * @param env
	 * @param strOrderHeaderKey
	 * @throws Exception
	 */
	private void callChangeOrderStatus(YFSEnvironment env,
			String strOrderHeaderKey) throws Exception {

		String sMethodName = "ABOFCODVerificationTaskAgent.callChangeOrderStatus()";
		logger.beginTimer("Begin " + sMethodName);

		// Create document which acts as input to ChangeOrderStatus API
		Document docInputCOS = XMLUtil
				.createDocument(XmlConstants.E_ORDER_STATUS_CHANGE);
		Element eleCOSInputOrderStatusChange = docInputCOS.getDocumentElement();
		eleCOSInputOrderStatusChange.setAttribute(
				XmlConstants.A_ORDER_HEADER_KEY, strOrderHeaderKey);

		/**** Change the transaction id ***/
		eleCOSInputOrderStatusChange.setAttribute(
				XmlConstants.A_TRANSACTION_ID, XmlConstants.A_CHANGE_SO_STATUS);
		eleCOSInputOrderStatusChange
				.setAttribute(XmlConstants.A_BASE_DROP_STATUS,
						XmlConstants.A_STATUS_VERIFIED);
		eleCOSInputOrderStatusChange.setAttribute(
				XmlConstants.A_CHANGE_FOR_ALL_AVAILABLE_QTY, XmlConstants.YES);
		eleCOSInputOrderStatusChange.setAttribute(
				XmlConstants.A_IGNORE_TRANSACTION_DEPENDENCIES,
				XmlConstants.YES);

		// Call changeOrderStatus API
		logger.debug("Input to changeOrderStatus API: ",
				XMLUtil.getString(docInputCOS));
		Document docChangeOrderStatusOutput = XMLUtil.invokeAPI(env,
				XmlConstants.API_CHANGE_ORDER_STATUS, docInputCOS);
		logger.debug("Output of changeOrderStatus API : ",
				XMLUtil.getString(docChangeOrderStatusOutput));

		logger.endTimer("End " + sMethodName);
	}

	/**
	 * This method invokes the changeOrder API to cancel the Order.
	 * 
	 * @param env
	 * @param strOrderHeaderKey
	 * @throws Exception
	 */
	private void cancelOrder(YFSEnvironment env, String strOrderHeaderKey)
			throws Exception {
		String sMethodName = "ABOFCODVerificationTaskAgent.cancelOrder()";
		logger.beginTimer("Begin " + sMethodName);
		// Forming input to changeOrder API
		Document docInForChangeOrder = XMLUtil
				.createDocument(XmlConstants.A_ORDER);
		Element eleRootChangeOrder = docInForChangeOrder.getDocumentElement();
		eleRootChangeOrder.setAttribute(XmlConstants.A_ACTION,
				XmlConstants.A_ACTION_CANCEL);
		eleRootChangeOrder.setAttribute(XmlConstants.A_ORDER_HEADER_KEY,
				strOrderHeaderKey);
		Element eleNotes = docInForChangeOrder
				.createElement(XmlConstants.E_NOTES);
		Element eleNote = docInForChangeOrder
				.createElement(XmlConstants.E_NOTE);
		eleNote.setAttribute(XmlConstants.REASON_CODE,
				XmlConstants.A_REASON_CODE_RISK);
		// Changes For OMS R-15 JIRA ABOF-9948 Start:
		eleNote.setAttribute(XmlConstants.A_CONTACT_USER,
				XmlConstants.CVS);
		//Changes For OMS R-15 JIRA ABOF-9948 End
		eleNote.setAttribute(XmlConstants.A_NOTE_TEXT,
				XmlConstants.A_NOTE_TEXT_HIGH_RISK);
		eleNotes.appendChild(eleNote);
		eleRootChangeOrder.appendChild(eleNotes);

		// Call changeOrder API
		logger.debug("Input to changeOrder API: ",
				XMLUtil.getString(docInForChangeOrder));
		XMLUtil.invokeAPI(env, XmlConstants.CHANGE_ORDER_API,
				docInForChangeOrder);

		logger.endTimer("End " + sMethodName);
	}

	/**
	 * This method invokes the changeOrder API to resolve the hold.
	 * 
	 * @param env
	 * @param strOrderHeaderKey
	 * @param strHoldType
	 * @throws Exception
	 */
	private void resolveHold(YFSEnvironment env, String strOrderHeaderKey,
			String strHoldType) throws Exception {

		String sMethodName = "ABOFCODVerificationTaskAgent.resolveHold()";
		logger.beginTimer("Begin " + sMethodName);

		// Forming input to changeOrder API
		Document docInForChangeOrder = XMLUtil
				.createDocument(XmlConstants.A_ORDER);
		Element eleRootChangeOrder = docInForChangeOrder.getDocumentElement();
		eleRootChangeOrder.setAttribute(XmlConstants.A_ORDER_HEADER_KEY,
				strOrderHeaderKey);
		Element eleOrderHoldTypes = docInForChangeOrder
				.createElement(XmlConstants.E_ORDER_HOLD_TYPES);
		eleRootChangeOrder.appendChild(eleOrderHoldTypes);
		Element eleOrderHoldType = docInForChangeOrder
				.createElement(XmlConstants.ELE_HOLD_TYPE);
		eleOrderHoldType.setAttribute(XmlConstants.A_HOLD_TYPE, strHoldType);
		eleOrderHoldType.setAttribute(XmlConstants.A_STATUS,
				XmlConstants.STR_HOLD_STATUS);
		eleOrderHoldTypes.appendChild(eleOrderHoldType);

		// Call changeOrder API
		logger.debug("Input to changeOrder API: ",
				XMLUtil.getString(docInForChangeOrder));
		Document changeOrderOp = XMLUtil.invokeAPI(env, XmlConstants.CHANGE_ORDER_API,
				docInForChangeOrder);
		String orderNo = changeOrderOp.getDocumentElement().getAttribute(XmlConstants.A_ORDER_NO);
		// OMS-R16 changes to post LOW RISK Auto Verification to RN START 
		if(XmlConstants.HOLD_TYPE_LOW_RISK_HOLD.equalsIgnoreCase(strHoldType)){
			postMessageToQueue(orderNo , env);
		}
		logger.endTimer("End " + sMethodName);
	}
	/**
	  * This method calls service PostMessageToCloseORNTicket.This service pushes
	  * the order numbers that resolved to queue CODVerificationCloseORNTicketQ
	  * @param strOrderNo
	  * @param env
	  * @throws Exception 
	  */
	 public static void postMessageToQueue(String strOrderNo, YFSEnvironment env) throws Exception {
	  logger.beginTimer("ABOFCODVerificationTaskAgent.postMessageToQueue : Start");
	  Document docOrder=XMLUtil.createDocument(XmlConstants.E_ORDER);
	  Element rootEle = docOrder.getDocumentElement();
	  rootEle.setAttribute(XmlConstants.A_ORDER_NO, strOrderNo);
	  rootEle.setAttribute(XmlConstants.A_ACTION,XmlConstants.LOGISTICS_ACTION_VERIFY);
	  logger.info("OrderNo Being posted to CODVerificationCloseORNTicketQ :  "+strOrderNo);
	  XMLUtil.invokeService(env, XmlConstants.SERV_POST_MSG_TO_CLOSE_ORN_TKT, docOrder);
	  logger.endTimer("ABOFCODVerificationTaskAgent.postMessageToQueue : End");
	 }
	 // OMS-R16 changes to post LOW RISK Auto Verification to RN END 

	/**
	 * This method invokes the getCommonCodeList API.
	 * 
	 * @param env
	 * @return getCommonCodeList
	 * @throws Exception
	 */
	private Document getCommoncodeList(YFSEnvironment env) throws Exception {

		// Prepare input to getCommonCodeList API.
		Document docCommonCodeInput = XMLUtil
				.createDocument(XmlConstants.E_COMMON_CODE);
		Element eleCommonCodeInput = docCommonCodeInput.getDocumentElement();
		eleCommonCodeInput.setAttribute(XmlConstants.A_CODE_TYPE,
				XmlConstants.COMMON_CODE_CC_WORKING_HOURS);
		eleCommonCodeInput.setAttribute(XmlConstants.A_ORG_CODE,
				XmlConstants.ENTERPRISE_CODE);

		// Input to getCommonCodeList api

		logger.debug("Input to getCommonCodeList api :",
				XMLUtil.getString(docCommonCodeInput));
		Document getCommonCodeList = XMLUtil.invokeAPI(env,
				XmlConstants.API_GET_COMMON_CODE_LIST, docCommonCodeInput);
		logger.debug("Ouput of getCommonCodeList api:",
				XMLUtil.getString(getCommonCodeList));

		return getCommonCodeList;

	}

}
