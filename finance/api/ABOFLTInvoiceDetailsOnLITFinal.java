package com.abof.finance.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.abof.finance.utils.ABOFFinanceUtils;
import com.abof.finance.utils.FinanceConstants;
import com.abof.sterling.util.StringUtils;
import com.abof.sterling.util.XmlConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.sun.tools.internal.ws.util.xml.XmlUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;

public class ABOFLTInvoiceDetailsOnLITFinal implements YIFCustomApi {

	@Override
	public void setProperties(Properties arg0) throws Exception {

	}

	private static YFCLogCategory logger = YFCLogCategory
			.instance(ABOFLTInvoiceDetailsOnLITFinal.class);

	/**
	 * This method is invoked on CHANGE_TO_LIT_FINAL.0003.ex.ON_STATUS_CHANGE
	 * 
	 * @param env
	 * @param inputDoc
	 * @return
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public Document publishLTInvoiceDetailsToQueue(YFSEnvironment env,
			Document inputDoc) throws IllegalArgumentException, Exception {
		logger.beginTimer("publishLTInvoiceDetailsToQueue");
		if (logger.isVerboseEnabled()) {
			logger.verbose("input document for publishLTInvoiceDetailsToQueue"
					+ XMLUtil.getXmlString(inputDoc.getDocumentElement()));
		}
		Document getInvoiceDetailsIp = XMLUtil.createDocument("OrderInvoice");
		Element eleOrderAudit = (Element) inputDoc.getDocumentElement()
				.getElementsByTagName(XmlConstants.ORDER_AUDIT).item(0);
		String sOrderHeaderKey = eleOrderAudit
				.getAttribute(XmlConstants.A_ORDER_HEADER_KEY);
		getInvoiceDetailsIp.getDocumentElement().setAttribute(
				XmlConstants.A_ORDER_HEADER_KEY, sOrderHeaderKey);
		Document invoiceDetailsOutDoc = XMLUtil.invokeAPI(env,
				XmlConstants.API_GET_ORDER_INVOICE_LIST, getInvoiceDetailsIp,
				null);
		Element eleOrderInvoice = (Element) invoiceDetailsOutDoc
				.getDocumentElement()
				.getElementsByTagName(FinanceConstants.ORDER_INVOICE).item(0);
		Document returnDoc = XMLUtil.getDocument(eleOrderInvoice, true);
		returnDoc.getDocumentElement().setAttribute(
				FinanceConstants.DEBIT_COURIER, FinanceConstants.YES);
		if (logger.isVerboseEnabled()) {
			logger.verbose("output document for publishLTInvoiceDetailsToQueue"
					+ XMLUtil.getXmlString(invoiceDetailsOutDoc
							.getDocumentElement()));
		}
		logger.endTimer("publishLTInvoiceDetailsToQueue");
		return returnDoc;
	}
}
