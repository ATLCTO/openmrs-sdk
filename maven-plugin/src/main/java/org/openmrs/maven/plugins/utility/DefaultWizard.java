package org.openmrs.maven.plugins.utility;

import com.google.common.collect.Lists;
import org.apache.commons.collections.ArrayStack;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.openmrs.maven.plugins.model.Artifact;
import org.openmrs.maven.plugins.model.DistroProperties;
import org.openmrs.maven.plugins.model.Server;
import org.openmrs.maven.plugins.model.UpgradeDifferential;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.Map.Entry;

/**
 * Class for attribute helper functions
 */
@Component(role=Wizard.class)
public class DefaultWizard implements Wizard {
    private static final String EMPTY_STRING = "";
    private static final String NONE = "(none)";
    private static final String DEFAULT_CHOICE_TMPL = "Which one do you choose?";
    private static final String DEFAULT_OPTION_TMPL = "%d) %s";
    private static final String DEFAULT_CUSTOM_OPTION_TMPL = "%d) Custom";
    private static final String DEFAULT_SERVER_NAME = "server";
    private static final String DEFAULT_VALUE_TMPL = "Please specify '%s'";
    private static final String DEFAULT_VALUE_TMPL_WITH_DEFAULT = "Please specify '%s': (default: '%s')";
    private static final String DEFAULT_FAIL_MESSAGE = "Server with such serverId is not exists";
    private static final String INVALID_SERVER = "Invalid server Id";
    private static final String YESNO = " [Y/n]";
    private static final String REFERENCEAPPLICATION_2_3 = "org.openmrs.distro:referenceapplication-package:2.3.1";
    private static final String DEFAULT_CUSTOM_DIST_ARTIFACT = "Please specify custom distribution artifact%s (default: '%s')";
    private static final String REFAPP_OPTION_TMPL = "Reference Application %s";
    private static final String REFAPP_ARTIFACT_TMPL = "org.openmrs.distro:referenceapplication-package:%s";
    private static final String JDK_ERROR_TMPL = "\n\nThe JDK %s is not compatible with OpenMRS Platform %s. " +
            "Please use %s to run this server.\n\nIf you are running " +
            "in a forked mode, correct the jdk property in %s\n\n";
    private static final String UPGRADE_CONFIRM_TMPL = "The %s %s introduces the following changes";
    private static final String UPDATE_MODULE_TMPL = "^ upgrades %s %s to %s";
    private static final String ADD_MODULE_TMPL = "+ adds %s %s";
    private static final String NO_DIFFERENTIAL = "\nNo modules to update or add found";
    public static final String PLATFORM_VERSION_PROMPT = "You can deploy the following versions of a platform";
    public static final String DISTRIBUTION_VERSION_PROMPT = "You can deploy the following versions of distribution";

    @Requirement
    Prompter prompter;

    Log log;

    private boolean interactiveMode = true;

    public DefaultWizard(){};

    public DefaultWizard(Prompter prompter) {
        this.prompter = prompter;
    }

    @Override
    public boolean isInteractiveMode() {
        return interactiveMode;
    }

    @Override
    public void setInteractiveMode(boolean interactiveMode) {
        this.interactiveMode = interactiveMode;
    }

    /**
     * Prompt for serverId, and get default serverId which is not exists,
     * if serverId is not set before
     *
     * @param server @return
     * @throws PrompterException
     */
    @Override
    public void promptForNewServerIfMissing(Server server) {
        String defaultServerId = DEFAULT_SERVER_NAME;
        int indx = 0;
        while (new File(Server.getServersPath(), defaultServerId).exists()) {
            indx++;
            defaultServerId = DEFAULT_SERVER_NAME + String.valueOf(indx);
        }
        String serverId =  promptForValueIfMissingWithDefault("Specify server id (-D%s) (default: '%s')", server.getServerId(), "serverId", defaultServerId);
        server.setServerId(serverId);
    }



    /**
     * Prompt for a value if it not set, and default value is set
     *
     * @param message
     * @param value
     * @param parameterName
     * @param defValue
     * @return value
     * @throws PrompterException
     */
    @Override
    public String promptForValueIfMissingWithDefault(String message, String value, String parameterName, String defValue) {
        if (value != null) {
            return value;
        }
        String textToShow;
        // check if there no default value
        if (StringUtils.isBlank(defValue)){
            textToShow = String.format(message != null ? message : DEFAULT_VALUE_TMPL, parameterName);
        }
        else {
            textToShow = String.format(message != null? message : DEFAULT_VALUE_TMPL_WITH_DEFAULT, parameterName, defValue);
        }
        String val = prompt(textToShow);
        if (StringUtils.isBlank(val)) {
            val = defValue;
        }
        return val;
    }

    @Override
    public String promptForMissingValueWithOptions(String message, String value, String parameterName, List<String> options, String customMessage, String customDefault){
        if (value != null) {
            return value;
        }
        String question = String.format(message != null? message : DEFAULT_VALUE_TMPL_WITH_DEFAULT, parameterName, options.get(0));

        System.out.println("\n" + question + ":");
        List<Integer> choices = new ArrayList<>();
        int i = 0;
        for(String option : options){
            i++;
            System.out.println(String.format(DEFAULT_OPTION_TMPL, i, option));
            choices.add(i);
        }
        if(customMessage != null){
            i++;
            System.out.println(String.format(DEFAULT_CUSTOM_OPTION_TMPL, i));
            choices.add(i);
        }

        String choice = prompt(DEFAULT_CHOICE_TMPL + " [" + StringUtils.join(choices.iterator(), "/") + "]");
        int chosenIndex = -1;
        if(!StringUtils.isBlank(choice) && StringUtils.isNumeric(choice)) {
            chosenIndex = Integer.parseInt(choice) - 1;
        }

        if(chosenIndex >= 0) {
            if (chosenIndex < options.size()){
                return options.get(chosenIndex);
            } else if(chosenIndex == options.size() && customMessage != null) {
                return promptForValueIfMissingWithDefault(customMessage, null, "", customDefault);
            }
        }

        System.out.println("\nYou must specify " + StringUtils.join(choices.iterator(), " or ") + ".");
        return promptForMissingValueWithOptions(message, value, parameterName, options, customMessage, customDefault);
    }


    private String prompt(String textToShow){
        try {
            return prompter.prompt("\n" + textToShow);
        } catch (PrompterException e) {
            throw new RuntimeException(e);
        }
    }
    public void showMessage(String textToShow){
        System.out.println("\n" + textToShow);
    }

    /**
     * Prompt for a value with list of proposed values
     * @param value
     * @param parameterName
     * @param values
     * @return value
     * @throws PrompterException
     */
    @Override
    public String promptForValueWithDefaultList(String value, String parameterName, List<String> values) {
        if (value != null) return value;
        String defaultValue = values.size() > 0 ? values.get(0) : NONE;
        final String text = DEFAULT_VALUE_TMPL_WITH_DEFAULT + " (possible: %s)";
        String val = prompt(String.format(text, parameterName, defaultValue, StringUtils.join(values.toArray(), ", ")));
        if (val.equals(EMPTY_STRING)) val = defaultValue;
        return val;
    }

    /**
     * Prompt for a value if it not set, and default value is NOT set
     * @param value
     * @param parameterName
     * @return
     * @throws PrompterException
     */
    @Override
    public String promptForValueIfMissing(String value, String parameterName) {
        return promptForValueIfMissingWithDefault(null, value, parameterName, EMPTY_STRING);
    }

    /**
     * Print dialog Yes/No
     * @param text - text to display
     * @return
     */
    @Override
    public boolean promptYesNo(String text) {
        if(interactiveMode){
            String yesNo = null;
            yesNo = prompt(text.concat(YESNO));
            return yesNo.equals("") || yesNo.toLowerCase().equals("y");
        } else {
            return true;
        }
    }

    /**
     * Check if value is submit
     * @param value
     * @return
     */
    @Override
    public boolean checkYes(String value) {
        String val = value.toLowerCase();
        return val.equals("true") || val.equals("yes");
    }

    /**
     * Get path to server by serverId and prompt if missing
     * @return
     * @throws MojoFailureException
     */
    @Override
    public String promptForExistingServerIdIfMissing(String serverId) {
        File omrsHome = new File(Server.getServersPath());
        List<String> servers = getListOf5RecentServers();
        serverId = promptForValueWithDefaultList(serverId, "serverId", servers);
        if (serverId.equals(NONE)) {
            throw new RuntimeException(INVALID_SERVER);
        }
        File serverPath = new File(omrsHome, serverId);
        if (!serverPath.exists()) {
            throw new RuntimeException("There is no server with the given server id. Please create it first using openmrs-sdk:setup.");
        }
        return serverId;
    }

    @Override
    public void promptForPlatformVersionIfMissing(Server server, List<String> versions) {
        String version = promptForMissingValueWithOptions(PLATFORM_VERSION_PROMPT,
                server.getVersion(), "version", versions, "Please specify platform version", null);
        server.setVersion(version);
    }

	@Override
	public String promptForPlatformVersion(List<String> versions) {
        String version = promptForMissingValueWithOptions(PLATFORM_VERSION_PROMPT,
                null, "version", versions, "Please specify platform version", null);
        return version;
	}

	@Override
    public void promptForDistroVersionIfMissing(Server server) throws MojoExecutionException {
        if(server.getVersion()==null){
            String choice = promptForDistroVersion();
            Artifact distro = DistroHelper.parseDistroArtifact(choice);
            if(distro != null){
                server.setVersion(distro.getVersion());
                server.setDistroArtifactId(distro.getArtifactId());
                server.setDistroGroupId(distro.getGroupId());
            } else {
                server.setDistroArtifactId(SDKConstants.REFERENCEAPPLICATION_ARTIFACT_ID);
                server.setDistroGroupId(Artifact.GROUP_DISTRO);
                server.setVersion(choice);
            }
        }
    }

    public String promptForDistroVersion() {
        Map<String, String> optionsMap = new HashMap<>();
        for(String version : SDKConstants.SUPPPORTED_REFAPP_VERSIONS_2_3_1_OR_LOWER){
            optionsMap.put(String.format(REFAPP_OPTION_TMPL, version), String.format(REFAPP_ARTIFACT_TMPL, version));
        }
        optionsMap.put(String.format(REFAPP_OPTION_TMPL, "2.4-SNAPSHOT"), String.format(REFAPP_ARTIFACT_TMPL, "2.4-SNAPSHOT"));

        String version = promptForMissingValueWithOptions(DISTRIBUTION_VERSION_PROMPT,
                null, "version", Lists.newArrayList(optionsMap.keySet()), "Please specify distribution artifact", REFERENCEAPPLICATION_2_3);
        return optionsMap.get(version);
    }

    @Override
    public void promptForMySQLDb(Server server) {
        if(server.getDbDriver() == null){
            server.setDbDriver(SDKConstants.DRIVER_MYSQL);
        }
        String dbUri = promptForValueIfMissingWithDefault(
                "The distribution requires MySQL database. Please specify database uri (-D%s) (default: '%s')",
                server.getDbUri(), "dbUri", SDKConstants.URI_MYSQL);
        if (dbUri.startsWith("jdbc:mysql:")) {
            dbUri = addMySQLParamsIfMissing(dbUri);
        }
        server.setDbUri(dbUri);
        promptForDbCredentialsIfMissing(server);
    }

    @Override
    public void promptForH2Db(Server server) {
        boolean h2 = promptYesNo(
                "Would you like to use the h2 database (-DdbDriver) (note that some modules do not support it)?");
        if(h2) {
            server.setDbDriver(SDKConstants.DRIVER_H2);
            if (server.getDbUri() == null) {
                server.setDbUri(SDKConstants.URI_H2);
            }
            server.setDbUser("root");
            server.setDbPassword("root");
        } else {
            promptForMySQLDb(server);
        }
    }

    @Override
    public void promptForDbCredentialsIfMissing(Server server) {
        String defaultUser = "root";
        String user = promptForValueIfMissingWithDefault(
                "Please specify database username (-D%s) (default: '%s')",
                server.getDbUser(), "dbUser", defaultUser);
        server.setDbUser(user);
        //set password
        String dbPassword = promptForValueIfMissingWithDefault(
                "Please specify database password (-D%s) (default: '')",
                server.getDbPassword(), "dbPassword", "");
        server.setDbPassword(dbPassword);
    }

    /**
     * Check if we are currenly inside "server" folder and get path
     * @return
     */
    @Override
    public File getCurrentServerPath() throws MojoExecutionException {
        File currentFolder = new File(System.getProperty("user.dir"));
        File openmrsHome = Server.getServersPathFile();
        File current = new File(currentFolder, SDKConstants.OPENMRS_SERVER_PROPERTIES);
        File parent = new File(currentFolder.getParent(), SDKConstants.OPENMRS_SERVER_PROPERTIES);
        File propertiesFile = null;
        if (current.exists()) propertiesFile = current;
        else if (parent.exists()) propertiesFile = parent;
        if (propertiesFile != null) {
            File server = propertiesFile.getParentFile();
            if (!server.getParentFile().equals(openmrsHome)) return null;
            Server properties = Server.loadServer(server);
            if (properties.getParam(Server.PROPERTY_SERVER_ID) != null) return propertiesFile.getParentFile();
        }
        return null;
    }

    /**
     * Get 5 last modified servers
     * @return
     */
    @Override
    public List<String> getListOf5RecentServers() {
        final int count = 5;
        String home = System.getProperty("user.home");
        File openMRS = new File(home, SDKConstants.OPENMRS_SERVER_PATH);
        Map<Long, String> sortedMap = new TreeMap<Long, String>(Collections.reverseOrder());
        File [] list = (openMRS.listFiles() == null) ? new File[0] : openMRS.listFiles();
        for (File f: list) {
            if (f.isDirectory()) sortedMap.put(f.lastModified(), f.getName());
        }
        int length = sortedMap.size() < count ? sortedMap.size() : count;
        return new ArrayList<String>(sortedMap.values()).subList(0, length);
    }

    @Override
    public String addMySQLParamsIfMissing(String dbUri) {
		Map<String, String> mysqlParams = new LinkedHashMap<String, String>();
		mysqlParams.put("autoReconnect", "true");
		mysqlParams.put("sessionVariables", "storage_engine=InnoDB");
		mysqlParams.put("useUnicode", "true");
		mysqlParams.put("characterEncoding", "UTF-8");

		int querySeparator = dbUri.indexOf("?");

		String query = querySeparator > 0 ? dbUri.substring(querySeparator + 1) : null;
		if (query != null) {
			String[] params = query.split("&");
			for (String param : params) {
				int valueSeparator = param.indexOf("=");
			    try {
					String key = valueSeparator > 0 ? URLDecoder.decode(param.substring(0, valueSeparator), "UTF-8") : param;
					String value = valueSeparator > 0 ? URLDecoder.decode(param.substring(valueSeparator + 1), "UTF-8") : "";
					mysqlParams.put(key, value);
				}
				catch (UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
			}
		}

		StringBuilder newUri = new StringBuilder(querySeparator > 0 ? dbUri.substring(0, querySeparator) : dbUri);
		newUri.append("?");
		for (Entry<String, String> param : mysqlParams.entrySet()) {
			try {
				newUri.append(URLEncoder.encode(param.getKey(), "UTF-8")).append("=").append(URLEncoder.encode(param.getValue(), "UTF-8")).append("&");
			}
			catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
		dbUri = newUri.toString();
		dbUri = dbUri.substring(0, dbUri.length() - 1);
		return dbUri;
	}

    public Log getLog() {
        if(log == null){
            log = new SystemStreamLog();
        }
        return log;
    }

    @Override
    public void showJdkErrorMessage(String jdk, String platform, String recommendedJdk, String pathToServerProperties) {
        System.out.print(String.format(JDK_ERROR_TMPL, jdk, platform, recommendedJdk, pathToServerProperties));
    }

    /**
     * Show confirmation prompt if there is any change besides updating modules with SNAPSHOT versions
     * @return
     */
    @Override
    public boolean promptForConfirmDistroUpgrade(UpgradeDifferential upgradeDifferential, Server server, DistroProperties distroProperties){
        if(upgradeDifferential.isEmpty()){
            showMessage(NO_DIFFERENTIAL);
            return false;
        }

        boolean needConfirmation = false;

        if(upgradeDifferential.getPlatformArtifact() !=null){
            if(!needConfirmation){
                System.out.println(String.format(UPGRADE_CONFIRM_TMPL, distroProperties.getName(), distroProperties.getServerVersion()));
                needConfirmation = true;
            }
            System.out.println(String.format(UPDATE_MODULE_TMPL,
                    upgradeDifferential.getPlatformArtifact().getArtifactId(),
                    server.getPlatformVersion(),
                    upgradeDifferential.getPlatformArtifact().getVersion()));
        }
        for(Entry<Artifact, Artifact> updateEntry : upgradeDifferential.getUpdateOldToNewMap().entrySet()){
            //update map should contain entry with equal versions only when they are same snapshots
            //(e.g. update 'appui 0.2-SNAPSHOT' to 'appui 0.2-SNAPSHOT')
            //updating to same SNAPSHOT doesn't require confirmation, they are not shown
            if(!updateEntry.getKey().getVersion().equals(updateEntry.getValue().getVersion())){
                if(!needConfirmation){
                    System.out.println(String.format(UPGRADE_CONFIRM_TMPL, distroProperties.getName(), distroProperties.getServerVersion()));
                    needConfirmation = true;
                }
                System.out.println(String.format(UPDATE_MODULE_TMPL,
                        updateEntry.getKey().getArtifactId(),
                        updateEntry.getKey().getVersion(),
                        updateEntry.getValue().getVersion()));
            }
        }

        for(Artifact addArtifact : upgradeDifferential.getModulesToAdd()){
            if(!needConfirmation){
                System.out.println(String.format(UPGRADE_CONFIRM_TMPL, distroProperties.getName(), distroProperties.getServerVersion()));
                needConfirmation = true;
            }
            System.out.println(String.format(ADD_MODULE_TMPL,
                    addArtifact.getArtifactId(),
                    addArtifact.getVersion()));
        }

        if(needConfirmation){
            return promptYesNo(String.format("Would you like to apply those changes to the %s server?", server.getServerId()));
        }
        else return true;
    }
}
