package com.atex.h11.custom.scranton.event;

import com.unisys.media.cr.adapter.polopoly.model.data.datasource.PolopolyDataSource;
import com.unisys.media.cr.adapter.ncm.common.business.interfaces.INCMMetadataNodeManager;
import com.unisys.media.cr.adapter.ncm.common.data.datasource.NCMDataSourceDescriptor;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import com.unisys.media.cr.common.data.interfaces.IDataSource;
import com.unisys.media.cr.common.data.values.NodeTypePK;
import com.unisys.media.cr.model.data.datasource.DataSourceManager;
import com.unisys.media.extension.common.constants.ApplicationConstants;
import com.unisys.media.extension.common.security.UPSUser;
import com.unisys.media.extension.common.util.VersionBuilder;
import com.unisys.media.ncm.cfg.common.data.values.UserHermesCfgValue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class Config {

	private static final Logger logger = Logger.getLogger(Config.class.getName());
	private static boolean isInitialized = false;
	private static Properties props = null;
	private static NCMDataSource hermesDS = null;
	private static PolopolyDataSource polopolyDS = null;
	private static UPSUser upsUser = null;
	private static INCMMetadataNodeManager metaMgr = null;
	
	public Config() {
		// none
	}
	
	public static void Initialize(IDataSource ds)
			throws FileNotFoundException, IOException {	
		if (!isInitialized) {
			setLogger();
			loadProperties();
			
			// establish data sources
			hermesDS = (NCMDataSource) ds;
			polopolyDS = setPolopolyDataSource(); 
			
			isInitialized = true;
		}	
	}
	
	private static void setLogger() {
		String log4jFileName = Constants.LOG4J_CONFIG;
		if (log4jFileName != null) {
			File log4jFile = new File (log4jFileName);
			if (log4jFile.canRead()) {
				PropertyConfigurator.configure(log4jFileName);
			} else {
				BasicConfigurator.configure();
			}
		} else {
			BasicConfigurator.configure();
		}
		logger.debug("Logger configured");
	}
	
	private static void loadProperties() 
			throws FileNotFoundException, IOException {
		File confFile = new File(Constants.DEFAULT_CONFIG_FILE);
		if (confFile == null || !confFile.exists())
			throw new RuntimeException("Configuration file " + Constants.DEFAULT_CONFIG_FILE + " not found");
		props = new Properties();
		props.load(new FileInputStream(confFile));	
		logger.debug("Configuration file " + Constants.DEFAULT_CONFIG_FILE + " loaded");
	}
	
	private static PolopolyDataSource setPolopolyDataSource() 
			throws FileNotFoundException, IOException {
		File polJndiFile = new File(getProperty("polopoly.jndi.file"));
		if (polJndiFile == null || !polJndiFile.exists())
			throw new RuntimeException("JNDI file for Polopoly not found");
		Properties polJndiProps = new Properties();
		polJndiProps.load(new FileInputStream(polJndiFile));
		logger.debug("Polopoly JNDI file=" + getProperty("polopoly.jndi.file"));
		
		DataSourceManager dsmgr = DataSourceManager.getInstance(polJndiProps);		
		UPSUser upsUser = getUPSUser();
		
		String jndiName = "ejb/" + VersionBuilder.strUPSVER + "/cr/web/polopoly/DataSourceManager";
		PolopolyDataSource ds = 
			(PolopolyDataSource) dsmgr.getDataSource(jndiName, upsUser, ApplicationConstants.APP_MEDIA_API_ID);
		logger.debug("Polopoly Datasource found");
		
		return ds;
	}
	
	public static String getProperty(String key) {
		String value = props.getProperty(key);
		if (value == null) {
			logger.warn("Could not find property " + key);
			value = "";
		}
		return value.trim();
	}
	
	public static NCMDataSource getHermesDataSource() {
		return hermesDS;
	}
	
	public static PolopolyDataSource getPolopolyDataSource() {
		return polopolyDS;
	}
	
	public static UserHermesCfgValue getHermesCfgValue() {
		return hermesDS.getUserHermesCfg().getUserHermesCfgValue();
	}
	
	public static UPSUser getUPSUser() {
		if (upsUser == null) {
			upsUser = UPSUser.instanceUPSUserForNamedUser(
				getProperty("batch.user"), getProperty("batch.password"), ApplicationConstants.APP_MEDIA_API_ID);
		}
		return upsUser;
	}
	
	public static INCMMetadataNodeManager getMetadataManager() {
		if (metaMgr == null) {
			NodeTypePK PK = new NodeTypePK(NCMDataSourceDescriptor.NODETYPE_NCMMETADATA);
			metaMgr = (INCMMetadataNodeManager) getHermesDataSource().getNodeManager(PK);
		}
		return metaMgr;
	}	
	
}
