/**
 * 
 */
package com.abof.sterling.agent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.abof.sterling.util.XmlConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.ycp.japi.util.YCPBaseAgent;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/**
 * 
 * This class will post Email Details to EmailQ for QC fail scenarios. Details
 * of QC failed orders are fetched by running a SQL in Staging DB
 * 
 * @param yfsEnv
 * @param inDoc
 * @throws Exception
 */
public class QCFailedCustomAgent extends YCPBaseAgent {

	private static YFCLogCategory logger = YFCLogCategory
			.instance(QCFailedCustomAgent.class);

	Connection conObj = null;
	Statement statementObj = null;
	ResultSet resultSetObj = null;
	Properties props = null;

	/*
	 * This method will post message to EmailQ from the list which getJobs has
	 * returned
	 * 
	 * @param yfsEnv
	 * 
	 * @param emailHeaderDoc
	 * 
	 * @throws Exception
	 */
	@Override
	public void executeJob(YFSEnvironment yfsEnv, Document emailHeaderDoc)
			throws Exception {
		// post Message to EmailQ
		XMLUtil.invokeService(yfsEnv, "PostQCFailMsgToEmailQ", emailHeaderDoc);
	}

	/**
	 * 
	 * This method will make a call to DB and fetch results and form a
	 * EmailHeader doc.
	 * 
	 * @param yfsEnv
	 * @param docInXML
	 * @throws Exception
	 */
	@Override
	public List<Document> getJobs(YFSEnvironment yfsEnv, Document docInXML,Document lastMsg)
			throws Exception {

		List listQCFail = new ArrayList();

		String sMethodName = "SendEmailOnQCFailure.sendQCEmail()";
		logger.beginTimer("Begin " + sMethodName);

		// LastMsg null to prevent infinite loop
		if(null == lastMsg){
		// read database properties from customer_overrides

		String jdbcClassName = getPropertyValue(yfsEnv,
				"abof.secondarydb.jdbc.classname");
		String url = getPropertyValue(yfsEnv, "abof.secondarydb.jdbc.url");
		String user = getPropertyValue(yfsEnv, "abof.secondarydb.jdbc.user");
		String password = getPropertyValue(yfsEnv,
				"abof.secondarydb.jdbc.password");

		logger.debug("Database ClassName : " + jdbcClassName + "URL : " + url
				+ "User : " + user + "Password : " + password);

		try {
			Class.forName(jdbcClassName);
			conObj = DriverManager.getConnection(url, user, password);

			// Query is saved in a Service QCFailQuery as Template and fetch as is

			Document inDocForQry = XMLUtil.createDocument(XmlConstants.SOURCE);

			Document fetchQueryDoc = XMLUtil.invokeService(yfsEnv, "QCFailQuery",
					inDocForQry);

			String fetchQCFailedOrders = fetchQueryDoc.getDocumentElement()
					.getAttribute("Query");

			statementObj = conObj.createStatement();
			resultSetObj = statementObj.executeQuery(fetchQCFailedOrders);

			while (resultSetObj.next()) {

				String orderNo = resultSetObj.getString(1).trim();
				String emailId = resultSetObj.getString(2).trim();
				String dayPhone = resultSetObj.getString(3).trim();
				String inspectionComments = resultSetObj.getString(4).trim();
				String itemId = resultSetObj.getString(5).trim();
				String itemDescription = resultSetObj.getString(6).trim();

				/*
				 * Create Message with below details to EmailQ <EmailHeader
				 * EventID="qc_fail" BuyerUserId="" CustomerPhoneNo=""
				 * InspectionComments="" ItemShortDescription="" ItemID=""
				 * OrderNo=""/>
				 */
				Document emailHeaderDoc = XMLUtil
						.createDocument(XmlConstants.EMAIL_HEADER);

				Element rootEmail = emailHeaderDoc.getDocumentElement();
				rootEmail.setAttribute(XmlConstants.A_ORDER_NO, orderNo);
				rootEmail.setAttribute(XmlConstants.A_INSPECT_COMM,
						inspectionComments);
				rootEmail.setAttribute(XmlConstants.A_ITEM_ID, itemId);
				rootEmail.setAttribute(XmlConstants.BUYER_USER_ID, emailId);
				rootEmail.setAttribute("DayPhone", dayPhone);
				rootEmail.setAttribute("ItemShortDescription", itemDescription);
				rootEmail.setAttribute("EventID", "qc_fail");

				logger.debug("Email Payload for QC Failed Orders : ",
						XMLUtil.getString(emailHeaderDoc));

				// adding EmailDoc to list of Documents of getJobs
				listQCFail.add(emailHeaderDoc);

			}
		} catch (Exception e) {
			logger.error(e);
			e.printStackTrace();

		} finally {
			logger.debug("Inside Finally block");
			if (null != resultSetObj) {
				logger.debug("ResultSet is null");
				resultSetObj.close();
			}
			if (null != statementObj) {
				logger.debug("Close connection");
				statementObj.close();
			}
		}
		}
		return listQCFail;

	}

	public static String getPropertyValue(YFSEnvironment yfsEnv,
			String sproperty) throws Exception {
		Document inDoc = XMLUtil.createDocument(XmlConstants.E_GET_PROPERTY);
		Element eleroot = inDoc.getDocumentElement();
		eleroot.setAttribute(XmlConstants.A_PROPERTY_NAME, sproperty);

		Document outDoc = XMLUtil.invokeAPI(yfsEnv,
				XmlConstants.API_GET_PROPERTY, inDoc);
		return outDoc.getDocumentElement().getAttribute(
				XmlConstants.A_PROPERTY_VALUE);

	}

}
