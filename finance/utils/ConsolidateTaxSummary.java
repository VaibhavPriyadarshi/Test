package com.abof.finance.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import com.abof.sterling.agent.ABOFStoreTriplicateCopy;
import com.bridge.sterling.utils.GenericUtils;
import com.bridge.sterling.utils.service.SterlingAbstractCustomApi;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;

public class ConsolidateTaxSummary extends SterlingAbstractCustomApi {
    private static final String consolidateAttributes = "CONSOLIDATE_ATTRIB";
    private static YFCLogCategory logger = YFCLogCategory.instance(ABOFStoreTriplicateCopy.class);
    @Override
    public YFCDocument invoke(YFCDocument inXml) throws Exception {
	
	logger.beginTimer("ConsolidateTaxSummary.invoke");

	YFCElement  xInElem=inXml.getDocumentElement();
	YFCElement xLineTaxSummary=xInElem.getElementsByTagName("LineTaxSummary").item(0);
	YFCDocument lineTax=YFCDocument.createDocument("LineTaxSummary");
	HashMap<String, YFCElement>  taxMap=new HashMap<String, YFCElement>();
	String attribs = GenericUtils.getProperty(getProperties(),consolidateAttributes, true);
	
	YFCElement lineTaxElem=lineTax.getDocumentElement();
	lineTaxElem.setAttributes(xLineTaxSummary.getAttributes());
	
	for(YFCElement xTaxSummary:xLineTaxSummary.getElementsByTagName("TaxSummary")){
	    String igstTaxPer=xTaxSummary.getAttribute("IGSTTaxPercentage");
	    String cgstTaxPer=xTaxSummary.getAttribute("CGSTTaxPercentage");
	    String sgstTaxPer=xTaxSummary.getAttribute("SGSTTaxPercentage");
	    String sKey=igstTaxPer+","+cgstTaxPer+","+sgstTaxPer;
	    if(taxMap.containsKey(sKey)){
	   	    
	   	    YFCElement sTax=taxMap.get(sKey);
	   	    /*<TaxSummary TaxPercentage="18.00" 
	   	    TotalTaxCalculated="364.580" 
	   		    TaxType="CGST/SGST" 
	   		    LineTotalWithoutTax="2390.00" 
	   		    IGSTTaxPercentage="0.00" 
	   		    CGSTTaxPercentage="9.00" 
	   		    SGSTTaxPercentage="9.00" 
	   		    SGSTTax="182.29" 
	   		    CGSTTax="182.29" IGSTTax="0.00"/>*/
	   	    /*updateDoubleAttribute(sTax,xTaxSummary,"TotalTaxCalculated");
	   	    updateDoubleAttribute(sTax,xTaxSummary,"LineTotalWithoutTax");
	   	    updateDoubleAttribute(sTax,xTaxSummary,"SGSTTax");
	   	    updateDoubleAttribute(sTax,xTaxSummary,"CGSTTax");
	   	    updateDoubleAttribute(sTax,xTaxSummary,"IGSTTax");*/
	   	    
	   	    for(String attribname:attribs.split(",")){
	   		
	   		updateDoubleAttribute(sTax,xTaxSummary,attribname);
	   		
	   	    }
	   	    taxMap.put(sKey, sTax);
	   	    
	   	}else{

	   	 taxMap.put(sKey, xTaxSummary);
	   	}
	    
	}
	
	Iterator<Entry<String, YFCElement>> itMap = taxMap.entrySet().iterator();
	
	    while(itMap.hasNext()){
		Entry<String, YFCElement> e=itMap.next();
		YFCElement oTaxSummary= e.getValue();
		lineTaxElem.importNode(oTaxSummary);
	    }
	    xInElem.removeChild(xLineTaxSummary);
	    xInElem.importNode(lineTaxElem);
	    
	logger.endTimer("ConsolidateTaxSummary.invoke");
	
	return inXml;
    }

    private void updateDoubleAttribute(YFCElement sTax, YFCElement xTaxSummary, String attributeName) {
	
	sTax.setDoubleAttribute(attributeName,sTax.getDoubleAttribute(attributeName)+
		xTaxSummary.getDoubleAttribute(attributeName));
	
    }

 

}
