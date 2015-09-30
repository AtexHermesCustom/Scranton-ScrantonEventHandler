package com.atex.h11.custom.scranton.event;

import com.unisys.media.cr.adapter.polopoly.model.data.datasource.PolopolyDataSource;
import com.unisys.media.cr.adapter.ncm.common.business.interfaces.INCMObjectExtNodeManager;
import com.unisys.media.cr.adapter.ncm.common.data.pk.NCMObjectPK;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMObjectJournal;
import com.unisys.media.cr.adapter.ncm.common.data.types.NCMObjectNodeType;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMMetadataPropertyValue;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMObjectBuildProperties;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMStatusPropertyValue;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMLayoutValueClient;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMMetadataPropertyValueClient;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMObjectValueClient;
import com.unisys.media.cr.adapter.ncm.common.business.interfaces.INCMMetadataNodeManager;
import com.unisys.media.cr.adapter.ncm.common.data.pk.NCMCustomMetadataPK;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMCustomMetadataJournal;
import com.unisys.media.cr.common.data.types.IPropertyDefType;
import com.unisys.media.cr.common.data.interfaces.INodePK;
import com.unisys.media.cr.common.data.interfaces.INodeValue;
import com.unisys.media.cr.common.data.interfaces.IUrlNode;
import com.unisys.media.cr.common.data.values.Journal;
import com.unisys.media.cr.common.web.business.interfaces.IWebObjectNodeManager;
import com.unisys.media.cr.common.web.data.values.WebObjectNodeValue;
import com.unisys.media.extension.common.exception.NodeAlreadyLockedException;
import com.unisys.media.ncm.cfg.common.data.values.MetadataSchemaValue;
import com.unisys.media.ncm.cfg.model.values.UserHermesCfgValueClient;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class PackageWebHandler {

	public enum ExportMode {
		PACKAGE, 
		PAGE;
	}
	
	private static final Logger logger = Logger.getLogger(PackageWebHandler.class.getName());
	private int pkgId = 0;
	private String pkgName = null;
	private NCMObjectValueClient sp = null;
	private ExportMode expMode = null; 
	
	public PackageWebHandler(int pkgId, String pkgName, ExportMode expMode) {
		this.pkgId = pkgId;
		this.pkgName = pkgName;
		this.expMode = expMode;
	}
	
	private NCMObjectValueClient getPackage() {
		if (sp == null) {
			NCMObjectBuildProperties objProps = new NCMObjectBuildProperties();
			objProps.setGetByObjId(true);
			objProps.setIncludeSpChild(true);
			objProps.setDoNotChekPermissions(true);
			objProps.setIncludeMetadataGroups(new Vector<String>());		
			
			// get package object
			INodePK nodePK = NCMObjectPK.createFromString(Integer.toString(pkgId));
			NCMDataSource ds = Config.getHermesDataSource();
			sp = (NCMObjectValueClient) ds.getNode(nodePK, objProps);
		}
		
		return sp;
	}
	
	private NCMLayoutValueClient getLayout() {
		NCMObjectValueClient sp = getPackage();
		return sp.getLayout();
	}
	
	/* not used
	private int getObjIdFromPK(INodePK pk) {
		String s = pk.toString();
		int delimIdx = s.indexOf(":");
		if (delimIdx >= 0)
			s = s.substring(0, delimIdx);
		return Integer.parseInt(s);
	}
	*/
		
	private boolean isForWeb() {
		// perform check if package is meant for web:
		// check if WEB.NOWEB metadata field is set to YES/Y/1
		NCMObjectValueClient sp = getPackage();
		NCMMetadataPropertyValueClient meta = 
			(NCMMetadataPropertyValueClient) sp.getProperty(Config.getProperty("noweb.metadata.group"));
		if (meta != null) {
			NCMMetadataPropertyValue field = (NCMMetadataPropertyValue) meta.getValue();
			if (field != null) {
				String value = field.getMetadataValue(Config.getProperty("noweb.metadata.field")).getValue().toString();
				String checkValues = Config.getProperty("noweb.metadata.checkvalues");
				if (value != null && value.length() > 0 && checkValues != null) {
					String[] checks = checkValues.split(",");
					for (int i = 0; i < checks.length; i++) {
						if (value.equalsIgnoreCase(checks[i])) {
							logger.debug("Package [" + ((NCMObjectPK)sp.getPK()).getObjId() + "," + sp.getNCMName() + "] will not be sent to web. Metadata: " + 
								Config.getProperty("noweb.metadata.group") + "." +  Config.getProperty("noweb.metadata.field") + 
								"=" + value + ".");
							return false;	// not meant for web
						}
					}
				}
			}
		}
		
		logger.debug("Package [" + ((NCMObjectPK)sp.getPK()).getObjId() + "," + sp.getNCMName() + "] ok for web. Metadata: " + 
				Config.getProperty("noweb.metadata.group") + "." +  Config.getProperty("noweb.metadata.field") + 
				" is not set.");
		return true;	// meant for web 
	}
	
	private boolean isInExportLevel() 
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
		// perform check if package's level is configured for sending to the web

        // get package's level
		NCMDataSource hermesDS = Config.getHermesDataSource();
		UserHermesCfgValueClient cfg = hermesDS.getUserHermesCfg();
		NCMObjectValueClient sp = getPackage();
		String levelPath = cfg.getLevelPath(sp.getLevelId(), '/').toUpperCase();
		
		// Prepare a document builder.
	    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	    dbf.setNamespaceAware(true);
	    DocumentBuilder db = dbf.newDocumentBuilder();
	    XPathFactory xpf = XPathFactory.newInstance();
	    XPath xp = xpf.newXPath();
	
	    // load list of export levels
	    Document doc = db.parse(Config.getProperty("package.exportlevels.list"));
		
    	NodeList nl = (NodeList) xp.evaluate("/levels/level", doc.getDocumentElement(), XPathConstants.NODESET);
    	for (int i = 0; i < nl.getLength(); i++) {
    		String levelRegex = nl.item(i).getTextContent().toUpperCase();
    		levelRegex = "^" + levelRegex + ".*";
    		if (levelPath.matches(levelRegex)) {
				logger.debug("Package [" + ((NCMObjectPK)sp.getPK()).getObjId() + "," + sp.getNCMName() + "] ok for web." + 
					" Package level is configured for web export. Level=" + levelPath);     			
    			return true; // package is an export level
    		}
    	}		
		
		logger.debug("Package [" + ((NCMObjectPK)sp.getPK()).getObjId() + "," + sp.getNCMName() + "] will not be sent to web." +
				" Package level is not configured for web export. Level=" + levelPath);
		return false; // package not on an export level
	}	
	
	private void handleCaptions()
			throws RemoteException {
		NCMObjectValueClient sp = getPackage();		
		NCMDataSource ds = Config.getHermesDataSource();		
		INCMObjectExtNodeManager objMgr = ds.getObjectManager();
		
		String sourceVarName = Config.getProperty("caption.variant.source");
		String destVarName = Config.getProperty("caption.variant.destination");
		short sourceVarType = Config.getHermesCfgValue().getVariantIdByName(NCMObjectNodeType.OBJ_DID, sourceVarName);
		short destVarType = Config.getHermesCfgValue().getVariantIdByName(NCMObjectNodeType.OBJ_DID, destVarName);		
		
		// get child objects
		INodePK[] childPKs = sp.getChildPKs();
		ArrayList<NCMObjectValueClient> captions = new ArrayList<NCMObjectValueClient>();
		
		boolean updateSP = false;
		
		if (childPKs != null) {
			NCMObjectBuildProperties objProps = new NCMObjectBuildProperties();
			objProps.setGetByObjId(true);
			objProps.setDoNotChekPermissions(true);
			objProps.setIncludeMetadataGroups(new Vector<String>());	
			objProps.setIncludeVariants(true);
			
			for (int i = 0; i < childPKs.length; i++ ) {
				NCMObjectPK childPK = new NCMObjectPK(((NCMObjectPK)childPKs[i]).getObjId());
				NCMObjectValueClient child = (NCMObjectValueClient) ds.getNode(childPK, objProps);		
				
				// get captions (DID object type) in the master channel
				if (child.getType() == NCMObjectNodeType.OBJ_DID && child.getVariantType() == 0) {
					captions.add(child);
				}
			}
			
			for (int i = 0; i < captions.size(); i++) {
				NCMObjectValueClient cap = captions.get(i);
							
				logger.debug("handleCaptions: Caption [" + ((NCMObjectPK)cap.getPK()).getObjId() + "," + cap.getNCMName() + "]");
				NCMObjectJournal objJournal = new NCMObjectJournal();
				String sourceVarStr;				
				
				ArrayList<NCMObjectValueClient> sourceVars = new ArrayList<NCMObjectValueClient>();
				ArrayList<NCMObjectValueClient> destVars = new ArrayList<NCMObjectValueClient>();
				
				NCMObjectValueClient[] vars = cap.getVariants();
				if (vars != null) {
					for (NCMObjectValueClient var : vars) {
						if (var.getVariantType() == sourceVarType) {
							sourceVars.add(var);
						}
						if (var.getVariantType() == destVarType) {
							destVars.add(var);
						}	    							
					}
				}
				
				if (sourceVars.size() > 0) {
					sourceVarStr = sourceVarName + " variant";
				}
				else {
					sourceVars.add(cap);	// just use the master as the source variant, if source variant is not present		
					sourceVarStr = "master";					
				}
				
				// delete destination variant objects
				for (int j = 0; j < destVars.size(); j++) {
					logger.debug("handleCaptions: Delete [" + ((NCMObjectPK)destVars.get(j).getPK()).getObjId() + "," + destVars.get(j).getNCMName() + "] " + 
						destVarName + " variant.");
					objMgr.deleteNode(destVars.get(j).getPK());
				}
				
				// look for the source variant to copy to the destination variant
				// it should be the one with the same page id as the parent SP's page id
				int sourceIdx = -1;
				for (int j = 0; j < sourceVars.size(); j++) {
					if (sourceVars.get(j).getLayout().getPageId() == sp.getLayout().getPageId()) {	// laid out on same page
						sourceIdx = j;
						break;
					}
				}
				if (sourceIdx == -1) {
					sourceIdx = 0; 	// use the first one (just making sure something will be copied)
				}
				
				logger.debug("handleCaptions: Copy [" + ((NCMObjectPK)sourceVars.get(sourceIdx).getPK()).getObjId() + "," + sourceVars.get(sourceIdx).getNCMName() + "] " + 
					sourceVarStr + " to " + destVarName + " variant.");
				objMgr.copyAsVariant((INodeValue) sourceVars.get(sourceIdx).getValue(), destVarType, (Journal) objJournal);
				
				// need to reload SP because of changes
				updateSP = true;			
			}
		}
		
		if (updateSP) {		// reload SP
			this.sp = null;		
			sp = getPackage();
		}
	}
	
	private void setElementsPublishState(String metaGroup) {
		NCMObjectValueClient sp = getPackage();
		NCMDataSource ds = Config.getHermesDataSource();
				
		// get child objects
		INodePK[] childPKs = sp.getChildPKs();
		if (childPKs != null) {
			NCMObjectBuildProperties objProps = new NCMObjectBuildProperties();
			objProps.setGetByObjId(true);
			objProps.setDoNotChekPermissions(true);
			objProps.setIncludeMetadataGroups(new Vector<String>());		
			
			for (int i = 0; i < childPKs.length; i++ ) {
				NCMObjectPK childPK = new NCMObjectPK(((NCMObjectPK)childPKs[i]).getObjId());
				NCMObjectValueClient child = (NCMObjectValueClient) ds.getNode(childPK, objProps);

				// check only if child element is in the master channel 
				// or if the channel to be exported matches the child's variant type
				if (child.getVariantType() == 0 
					|| child.getVariantType() == Config.getHermesCfgValue().getVariantIdByName(child.getType(), metaGroup)) {  
					boolean updateField = true;
					
					/*  channel matches the metadata group for the PUBLISH_STATE metadata field
					NCMMetadataPropertyValueClient meta = 
						(NCMMetadataPropertyValueClient) child.getProperty(Config.getProperty("publish_state.metadata.group")); */
					NCMMetadataPropertyValueClient meta = 
						(NCMMetadataPropertyValueClient) child.getProperty(metaGroup);
					if (meta != null) {
						NCMMetadataPropertyValue field = (NCMMetadataPropertyValue) meta.getValue();
						if (field != null) {
							String value =
								field.getMetadataValue(Config.getProperty("publish_state.metadata.field")).getValue().toString();
							if (value != null && value.length() > 0 && value.equals(Config.getProperty("publish_state.metadata.value"))) {
								updateField = false; // no need to update
							}
						}
					}
					
					if (updateField) {
						// update metadata
						/*  channel matches the metadata group for the PUBLISH_STATE metadata field
						setMetadata(child, 
    						Config.getProperty("publish_state.metadata.group"), Config.getProperty("publish_state.metadata.field"), 
    						Config.getProperty("publish_state.metadata.value")); */
    					setMetadata(child, 
    							metaGroup, Config.getProperty("publish_state.metadata.field"), 
        						Config.getProperty("publish_state.metadata.value"));
					}			
				}
			}
		}		
	}
	
	private void setMetadata(NCMObjectValueClient objVC, String metaGroup, String metaField, String metaValue) {
		String objName = objVC.getNCMName();
		Integer objId = ((NCMObjectPK)objVC.getPK()).getObjId();
		logger.debug("setMetadata: Object [" + objId.toString() + "," + objName + "," + Integer.toString(objVC.getType()) + "]" +
			", Meta=" + metaGroup + "." + metaField + ", Value=" + metaValue);
		
		NCMDataSource hermesDS = Config.getHermesDataSource();
		UserHermesCfgValueClient cfg = hermesDS.getUserHermesCfg();
		
		// Get from configuration the schemaId using schemaName for metadata
		MetadataSchemaValue schema = cfg.getMetadataSchemaByName(metaGroup);
		int schemaId = schema.getId();
		
		// Get metadata property
		IPropertyDefType metaGroupDefType = hermesDS.getPropertyDefType(metaGroup);
		//IPropertyValueClient metaGroupPK = objVC.getProperty(metaGroupDefType.getPK());		
		
		INCMMetadataNodeManager metaMgr = Config.getMetadataManager();
		NCMMetadataPropertyValue pValue = new NCMMetadataPropertyValue(
				metaGroupDefType.getPK(), null, schema);
		pValue.setMetadataValue(metaField, metaValue);
		NCMCustomMetadataPK cmPk = new NCMCustomMetadataPK(
				objId, (short) objVC.getType(), schemaId);
		schemaId = schema.getId();
		NCMCustomMetadataPK[] nodePKs = new NCMCustomMetadataPK[] { cmPk };
		
		try {
			try {
				metaMgr.lockMetadataGroup(schemaId, nodePKs);
			} catch (NodeAlreadyLockedException e) {
			}
			NCMCustomMetadataJournal j = new NCMCustomMetadataJournal();
			j.setCreateDuringUpdate(true);
			metaMgr.updateMetadataGroup(schemaId, nodePKs, pValue, j);
			logger.debug("setMetadata: Update metadata successful for [" + objId.toString() + "," + objName + "," + Integer.toString(objVC.getType()) + "]");
		} catch (Exception e) {
			logger.error("setMetadata: Update metadata failed for [" + objId.toString() + "," + objName + "," + Integer.toString(objVC.getType()) + "]: " + 
				e.toString());
		} finally {
			try {
				metaMgr.unlockMetadataGroup(schemaId, nodePKs);
			} catch (Exception e) {
			}
		}			
	}
	
	private void changeStatus(Short newStatusValue) {
		NCMObjectValueClient sp = getPackage();
		boolean spLocked = false;
		
		try {
			try {
				sp.lock();	// try to lock object
				spLocked = true;
			}
			catch (Exception e) {
				logger.error("changeStatus: Unable to lock package [" + ((NCMObjectPK)sp.getPK()).getObjId() + "," + sp.getNCMName() + 
					"]. Cannot update status.");
				e.printStackTrace();
			}
			
			if (spLocked) {
				// get object current status
				NCMStatusPropertyValue curStatus = 
					(NCMStatusPropertyValue) sp.getLayout().getStatus().getValue();
				
				sp.changeStatus(sp.getPK(), newStatusValue, curStatus.getExtStatus().shortValue(), 
					curStatus.getComplexStatus().intValue(), curStatus.getAttribute().shortValue(), 
					new short[0]);
				logger.debug("changeStatus: Updated status of package [" + ((NCMObjectPK)sp.getPK()).getObjId() + "," + sp.getNCMName() +
					"]. New status=" + Short.toString(newStatusValue));
			}	
		} catch(Exception e) {
			logger.error("changeStatus: Error encountered while changing status of package [" + ((NCMObjectPK)sp.getPK()).getObjId() + "," + sp.getNCMName() + "].");			
			e.printStackTrace();
		} finally {
			try {
				if (spLocked) {				
					sp.unlock(false);	// unlock
				}				
			} catch(Exception e) {
				logger.error("changeStatus: Error encountered while unlocking package [" + ((NCMObjectPK)sp.getPK()).getObjId() + "," + sp.getNCMName() + "].");					
				e.printStackTrace();
			}
		}
	}	
	
	private int getDelaySeconds() {
		int delaySec = 0;
		
		String delayProp = Config.getProperty("delay.package.mode");
		if (!delayProp.isEmpty() && delayProp.matches("^\\d+$")) {
			delaySec = Integer.parseInt(delayProp);
		}
		
		return delaySec;
	}
	
	public int sendToWeb() 
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
		logger.info("sendToWeb: Package [" + pkgId + "," + pkgName + "]. expMode=" + expMode.toString());
		
		if (Config.getProperty("check.for.web").equalsIgnoreCase("true")) {
			// check if this package is meant for the web; if it is not, do not continue			
			if (! isForWeb()) {
				return -1;
			}
		}
		
		if (Config.getProperty("check.packagelevel").equalsIgnoreCase("true")) {
			// check if this package is meant for the web; if it is not, do not continue			
			if (! isInExportLevel()) {
				return -1;
			}
		}		
		
		if (expMode.equals(ExportMode.PACKAGE)) {	// PAGE export mode would have started the delay in the LogPageWebHandler class
			int delaySec = getDelaySeconds();
			if (delaySec > 0) {			
				logger.debug("Delay for " + delaySec + " seconds...");
				try {
					Thread.sleep(delaySec * 1000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
		
		// determine whether the package is paginated or not
		// channel name matches the metadata group name for the PUBLISH_STATE metadata field
		String channel = null;
		Boolean updatePublishState = false;
		Boolean handleCaptions = false;
		NCMLayoutValueClient layoutVC = getLayout();
		if (layoutVC.getPageId() > 0) {
			channel = Config.getProperty("channel.paginated");
			updatePublishState = Config.getProperty("update.publish_state.paginated").equalsIgnoreCase("true");
			handleCaptions = Config.getProperty("handle.captions.paginated").equalsIgnoreCase("true");
		}
		else {
			channel = Config.getProperty("channel.notpaginated");
			updatePublishState = Config.getProperty("update.publish_state.notpaginated").equalsIgnoreCase("true");
			handleCaptions = Config.getProperty("handle.captions.notpaginated").equalsIgnoreCase("true");
		}

		if (handleCaptions) {
			// additional handling for captions
			handleCaptions();
		}
		
		if (updatePublishState) {
			// make sure PUBLISH_STATE of package children is set correctly
			setElementsPublishState(channel);
		}
		
		// get object
		INodePK nodePK = NCMObjectPK.createFromString(Integer.toString(pkgId));
		
		PolopolyDataSource polopolyDS = Config.getPolopolyDataSource();
		IWebObjectNodeManager wonm = polopolyDS.getWebObjectNodeManager();
		
		WebObjectNodeValue onv = new WebObjectNodeValue();
		onv.setSP(nodePK);
		
		// export the appropriate variant depending on whether the package is paginated or not
		if (layoutVC.getPageId() > 0) {
			logger.debug("sendToWeb: Package [" + pkgId + "," + pkgName + "]" 
				+ " paginated on Page [" + Integer.toString(layoutVC.getPageId()) + "," + layoutVC.getPageName() + "]."
				+ " Export " + channel + " variant.");			
		}
		else {
			logger.debug("sendToWeb: Package [" + pkgId + "," + pkgName + "]"
				+ " not paginated."
				+ " Export " + channel + " variant.");	 		
		}
		onv.setChannelName(channel);

		// send to Polopoly
		IUrlNode iUrlNode = wonm.sendToWeb(onv);
		logger.debug("sendToWeb: Package [" + pkgId + "," + pkgName + "] sent.");
		String contentId = iUrlNode.getWebPk().toString();
		logger.info("sendToWeb: Polopoly article created for Package [" + pkgId + "," + pkgName + "]. contentId=" + contentId);
		
		// change status of package, if it is configured
		String newStatus = null;
		if (expMode.equals(ExportMode.PACKAGE)) {
			newStatus = Config.getProperty("status.after.export.package.mode");
		}
		else if (expMode.equals(ExportMode.PAGE)) {
			newStatus = Config.getProperty("status.after.export.page.mode");
		}
		if (!newStatus.isEmpty() && newStatus.matches("^\\d+$")) {
			changeStatus(Short.parseShort(newStatus));	// update status
		}
		
		return 1;
	}
}
