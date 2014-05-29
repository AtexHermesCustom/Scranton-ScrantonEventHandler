package com.atex.h11.custom.scranton.event;

import com.unisys.media.cr.adapter.ncm.common.event.config.ObjectEventHandler;
import com.unisys.media.cr.adapter.ncm.common.event.interfaces.IObjectEvent;
import com.unisys.media.cr.adapter.ncm.common.event.interfaces.IObjectFullEvent;
import com.unisys.media.cr.adapter.ncm.common.event.interfaces.IObjectMultilinkEvent;
import com.unisys.media.cr.common.data.interfaces.IDataSource;
import org.apache.log4j.Logger;

public class CustomObjectEventHandler extends ObjectEventHandler {

    private static final Logger logger = Logger.getLogger(CustomObjectEventHandler.class.getName());		
    
    @Override
	public void handleEvent(IDataSource ds, IObjectEvent event) {		
    	try {
    		Config.Initialize(ds);
    		logger.debug("Handle Object Event: " + event.toString());
    		PackageWebHandler handler = 
    			new PackageWebHandler(event.getObjId(), event.getObjName(), PackageWebHandler.ExportMode.PACKAGE);
   			handler.sendToWeb();
    	}
    	catch (Exception e) {
			logger.error("Error encountered:", e);	
    	}		
    }	
	
	@Override
	public void handleEvent(IDataSource ds, IObjectFullEvent event) {

	}
	
	@Override
	public void handleEvent(IDataSource ds, IObjectMultilinkEvent event) {

	}	
			
}
