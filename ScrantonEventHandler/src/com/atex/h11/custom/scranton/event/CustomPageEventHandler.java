package com.atex.h11.custom.scranton.event;

import com.unisys.media.cr.adapter.ncm.common.event.config.PageEventHandler;
import com.unisys.media.cr.adapter.ncm.common.event.interfaces.IPageEvent;
import com.unisys.media.cr.adapter.ncm.common.event.interfaces.IPageFullEvent;
import com.unisys.media.cr.common.data.interfaces.IDataSource;
import org.apache.log4j.Logger;

public class CustomPageEventHandler extends PageEventHandler {

    private static final Logger logger = Logger.getLogger(CustomPageEventHandler.class.getName());	
	
	@Override
	public void handleEvent(IDataSource ds, IPageEvent event) {
    	try {
    		Config.Initialize(ds);
    		logger.debug("Handle Page Event: " + event.toString());    		
    		LogPageWebHandler handler = 
    			new LogPageWebHandler(event.getPageId(), event.getPageName(), event.getPubDate(), 
    				event.getEditionName(), event.getPageLevelName());
    		handler.sendPackagesToWeb();
    	}
    	catch (Exception e) {
			logger.error("Error encountered:", e);	
    	}		
	}
	
	@Override
	public void handleEvent(IDataSource ds, IPageFullEvent event) {

	}
			
}