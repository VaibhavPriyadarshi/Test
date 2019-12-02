package com.abof.finance.api;

import java.util.Properties;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.abof.finance.utils.ABOFFinanceUtils;
import com.abof.finance.utils.FinanceConstants;
import com.cts.sterling.custom.accelerators.util.XMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

public class ABOFPODetailsOnCreation implements YIFCustomApi {

    private static YFCLogCategory logger = YFCLogCategory.instance(ABOFPODetailsOnCreation.class);

    @Override
    public void setProperties(Properties arg0) throws Exception {
    }

    /**
     * This method is invoked on Purchase order creation event and publishes PO Creation XML to Camel
     * @param env
     * @param inputDoc
     * @throws Exception
     */
    public Document populatePODetailsOnCreation(YFSEnvironment env, Document inputDoc) throws Exception {
        logger.beginTimer("populatePODetailsOnCreation");
        if (logger.isVerboseEnabled()) {
            logger.verbose("input document for populatePODetailsOnCreation" + XMLUtil.getXmlString(inputDoc.getDocumentElement()));
        }

        Element eleRootIn = inputDoc.getDocumentElement();
        String strOrderNo = eleRootIn.getAttribute(FinanceConstants.ORDER_NO);

        String strPOHeaderKey = ABOFFinanceUtils.getPrimaryKey(strOrderNo);

        Document outDoc = XMLUtil.createDocument(FinanceConstants.PO_HEADER);
        Element eleRootOut = outDoc.getDocumentElement();

        eleRootOut.setAttribute(FinanceConstants.PO_HEADER_KEY, strPOHeaderKey);

        eleRootOut.setAttribute(FinanceConstants.VOUCHER_NO, strOrderNo);
        eleRootOut.setAttribute(FinanceConstants.VOUCHER_DATE, eleRootIn.getAttribute(FinanceConstants.ORDER_DATE));
        eleRootOut.setAttribute(FinanceConstants.VENDOR_CODE, eleRootIn.getAttribute(FinanceConstants.SELLER_ORG_CODE));
        eleRootOut.setAttribute(FinanceConstants.PO_NUMBER, strOrderNo);
        eleRootOut.setAttribute(FinanceConstants.DESTINATION,
                XPathAPI.selectSingleNode(eleRootIn, "OrderStatuses/OrderStatus/@ReceivingNode").getNodeValue());
        eleRootOut.setAttribute(FinanceConstants.PO_TYPE, eleRootIn.getAttribute(FinanceConstants.ORDER_TYPE));
        eleRootOut.setAttribute(FinanceConstants.DELIVERY_DUE_DATE, eleRootIn.getAttribute(FinanceConstants.REQ_DELIVERY_DATE));
        eleRootOut.setAttribute(FinanceConstants.CREATE_TIME_STAMP, ABOFFinanceUtils.getCreateTimeStamp());
        eleRootOut.setAttribute(FinanceConstants.TAX_TYPE, FinanceConstants.BLANK);
        eleRootOut.setAttribute(FinanceConstants.TAX_RATE, FinanceConstants.BLANK);
        eleRootOut.setAttribute(FinanceConstants.TAX_AMOUNT, FinanceConstants.BLANK);
        eleRootOut.setAttribute(FinanceConstants.IS_PROCESSED, FinanceConstants.NO);

        // Setting line level attributes
        Element elePOLines = outDoc.createElement(FinanceConstants.PO_LINES);
        eleRootOut.appendChild(elePOLines);
        NodeList nlOrderLine = XPathAPI.selectNodeList(eleRootIn, "OrderLines/OrderLine");
        for (int intOL = 0; intOL < nlOrderLine.getLength(); intOL++) {
            Element eleOrderLine = (Element) nlOrderLine.item(intOL);
            Element elePOLine = outDoc.createElement(FinanceConstants.PO_LINE);
            elePOLines.appendChild(elePOLine);
            String strLineKEy = ABOFFinanceUtils.getPrimaryKey(eleOrderLine.getAttribute(FinanceConstants.PRIME_LINE_NO));
            Element eleItemIn = (Element) eleOrderLine.getElementsByTagName(FinanceConstants.ITEM).item(0);
            Element eleTax = (Element) XPathAPI.selectSingleNode(eleOrderLine, "LineTaxes/LineTax");
            elePOLine.setAttribute(FinanceConstants.PO_LINE_KEY, strLineKEy);
            elePOLine.setAttribute(FinanceConstants.PO_HEADER_KEY, strPOHeaderKey);
            elePOLine.setAttribute(FinanceConstants.SKU_CODE, eleItemIn.getAttribute(FinanceConstants.ITEM_ID));
            elePOLine.setAttribute(FinanceConstants.QUANTITY, eleOrderLine.getAttribute(FinanceConstants.ORDERED_QTY));
            elePOLine.setAttribute(FinanceConstants.MRP, eleItemIn.getAttribute(FinanceConstants.UNIT_COST));
            elePOLine.setAttribute(FinanceConstants.UOM, eleItemIn.getAttribute(FinanceConstants.UNIT_OF_MEASURE));
            elePOLine.setAttribute(FinanceConstants.TAX_TYPE, eleTax.getAttribute(FinanceConstants.REFERENCE_1));
            elePOLine.setAttribute(FinanceConstants.TAX_RATE, eleTax.getAttribute(FinanceConstants.TAX_PERCENTAGE));
            elePOLine.setAttribute(FinanceConstants.TAX_AMOUNT, eleTax.getAttribute(FinanceConstants.TAX));
            elePOLine.setAttribute(FinanceConstants.RATE, XPathAPI.selectSingleNode(eleOrderLine, "LinePriceInfo/@UnitPrice")
                    .getNodeValue());
            elePOLine.setAttribute(FinanceConstants.STYLE_CODE, eleItemIn.getAttribute(FinanceConstants.MANUFACTURER_NAME));
            elePOLine.setAttribute(FinanceConstants.EAN_CODE, eleItemIn.getAttribute(FinanceConstants.ISBN));
            elePOLine.setAttribute(FinanceConstants.BRAND_STYLE_CODE, eleItemIn.getAttribute(FinanceConstants.MANUFACTURER_NAME));
            elePOLine.setAttribute(FinanceConstants.COLOR_DESC, eleItemIn.getAttribute(FinanceConstants.ITEM_SHORT_DESC));
            elePOLine.setAttribute(FinanceConstants.PRODUCT_DESC, eleItemIn.getAttribute(FinanceConstants.ITEM_DESC));

            // Creating Line Level Discounts
            // Creating order line level discounts element
            Element eleSOLineDists = outDoc.createElement(FinanceConstants.PO_LINE_DISCOUNTS);
            elePOLine.appendChild(eleSOLineDists);

            NodeList nlLineDist = XPathAPI.selectNodeList(eleOrderLine, "LineCharges/LineCharge");
            for (int intLC = 0; intLC < nlLineDist.getLength(); intLC++) {
                Element eleLineCharge = (Element) nlLineDist.item(intLC);
                String strChargeCategory = eleLineCharge.getAttribute(FinanceConstants.CHARGE_CATEGORY);
                if (FinanceConstants.DISCOUNT.equalsIgnoreCase(strChargeCategory)) {
                    Element eleSOLineDist = outDoc.createElement(FinanceConstants.PO_LINE_DISCOUNT);
                    eleSOLineDists.appendChild(eleSOLineDist);
                    eleSOLineDist.setAttribute(FinanceConstants.DISCOUNT_NAME, eleLineCharge.getAttribute(FinanceConstants.CHARGE_NAME));
                    eleSOLineDist
                            .setAttribute(FinanceConstants.DISCOUNT_AMOUNT, eleLineCharge.getAttribute(FinanceConstants.CHARGE_AMOUNT));
                    eleSOLineDist.setAttribute(FinanceConstants.DISCOUNT_PERCENTAGE, FinanceConstants.BLANK);
                    eleSOLineDist.setAttribute(FinanceConstants.PO_HEADER_KEY, strPOHeaderKey);
                    eleSOLineDist.setAttribute(FinanceConstants.PO_LINE_KEY, strLineKEy);
                }
            }
        }
        // Setting attributes for header charge in publish document
        Element eleHeaderChargesOut = outDoc.createElement(FinanceConstants.PO_HEADER_DISCOUNTS);
        eleRootOut.appendChild(eleHeaderChargesOut);
        // Getting Header Charge element from input document
        NodeList nlHeaderChargeIn = XPathAPI.selectNodeList(eleRootIn, "HeaderCharges/HeaderCharge");
        for (int intHC = 0; intHC < nlHeaderChargeIn.getLength(); intHC++) {
            Element eleHeaderChargeIn = (Element) nlHeaderChargeIn.item(intHC);
            String strChargeCat = eleHeaderChargeIn.getAttribute(FinanceConstants.CHARGE_CATEGORY);
            if (FinanceConstants.DISCOUNT.equalsIgnoreCase(strChargeCat)) {
                Element eleHeaderChargeOut = outDoc.createElement(FinanceConstants.PO_HEADER_DISCOUNT);
                eleHeaderChargesOut.appendChild(eleHeaderChargeOut);
                eleHeaderChargeOut.setAttribute(FinanceConstants.PO_HEADER_KEY, strPOHeaderKey);
                eleHeaderChargeOut.setAttribute(FinanceConstants.DISCOUNT_NAME,
                        eleHeaderChargeIn.getAttribute(FinanceConstants.CHARGE_NAME));
                eleHeaderChargeOut.setAttribute(FinanceConstants.DISCOUNT_AMOUNT,
                        eleHeaderChargeIn.getAttribute(FinanceConstants.CHARGE_AMOUNT));
                eleHeaderChargeOut.setAttribute(FinanceConstants.DISCOUNT_PERCENTAGE, "");
            }
        }
        if (logger.isVerboseEnabled()) {
            logger.verbose("output document for populatePODetailsOnCreation" + XMLUtil.getXmlString(outDoc.getDocumentElement()));
        }
        XMLUtil.invokeService(env, FinanceConstants.ABOF_PUBLISH_FINANCIAL_DATA_TO_Q, outDoc);
        logger.endTimer("populatePODetailsOnCreation");
        return outDoc;
    }
}
