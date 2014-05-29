package com.atex.h11.custom.scranton.event;

import java.rmi.RemoteException;

import com.unisys.media.cr.adapter.ncm.common.data.pk.NCMLogicalPagePK;
import com.unisys.media.cr.adapter.ncm.common.data.pk.NCMObjectPK;
import com.unisys.media.cr.adapter.ncm.common.data.types.NCMObjectNodeType;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMLogicalPageBuildProperties;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMLogicalPageValueClient;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMObjectValueClient;
import com.unisys.media.ncm.cfg.model.values.UserHermesCfgValueClient;
import org.apache.log4j.Logger;

public class LogPageWebHandler {

	private static final Logger logger = Logger.getLogger(LogPageWebHandler.class.getName());
	private int pageId = 0;
	private String pageName = null;
	private int pubDate = 0;
	int editionId = 0;
	byte[] levelId = new byte[5];
	
	public LogPageWebHandler(int pageId, String pageName, int pubDate, String editionName, String levelName) {
		this.pageId = pageId;
		this.pageName = pageName;
		this.pubDate = pubDate;

		NCMDataSource hermesDS = Config.getHermesDataSource();
		UserHermesCfgValueClient cfg = hermesDS.getUserHermesCfg();
		this.levelId = cfg.findLevelByName(levelName).getId();
		if (! editionName.equals("*****")) {
			this.editionId = cfg.getEditionByName(levelId, editionName).getEditionId();
		}
	}
	
	public void sendPackagesToWeb()
			throws RemoteException {	
		logger.info("sendPackagesToWeb: Page [" + pageId + "," + pageName + "]");
		
		// get logpage
		NCMLogicalPagePK lpPK = new NCMLogicalPagePK(pageName, pubDate, editionId, levelId,
			NCMObjectPK.LAST_VERSION, NCMObjectPK.ACTIVE);
		NCMLogicalPageBuildProperties props = new NCMLogicalPageBuildProperties();
		props.setIncludeLayoutInPage(true);
		props.setIncludeObjContent(true);
		props.setIncludeLayContent(true);
		NCMDataSource hermesDS = Config.getHermesDataSource();
		NCMLogicalPageValueClient lpVC = (NCMLogicalPageValueClient) hermesDS.getNode(lpPK, props);
		
		// loop through all layouts
		if (lpVC.getLayouts().length > 0) {
			int pkgCount = 0;
			for (int i=0; i < lpVC.getLayouts().length; i++) {
				NCMObjectValueClient objVC = lpVC.getLayouts()[i].getObject();
				// process story packages
				if (objVC != null && objVC.getType() == NCMObjectNodeType.OBJ_STORY_PACKAGE) {
					try {
						NCMObjectPK objPK = (NCMObjectPK) objVC.getPK();
			    		PackageWebHandler handler = 
			    			new PackageWebHandler(objPK.getObjId(), objVC.getNCMName(), PackageWebHandler.ExportMode.PAGE);
		    			if (handler.sendToWeb() > 0) {
		    				pkgCount++;
		    			}
					}
			    	catch (Exception e) {
						logger.error("Error encountered sending package [" + objVC.getNCMName() + "]: ", e);	
			    	}	
				}
			}
			logger.info("sendPackagesToWeb: Sent " + pkgCount + " packages for Page [" + pageId + "," + pageName + "]");
		}
		else {
			logger.info("sendPackagesToWeb: No packages on Page [" + pageId + "," + pageName + "]");
		}
	}
	
}
