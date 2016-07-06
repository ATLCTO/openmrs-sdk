package org.openmrs.maven.plugins.utility;

import org.apache.maven.plugin.MojoExecutionException;
import org.openmrs.maven.plugins.model.DistroProperties;
import org.openmrs.maven.plugins.model.Server;
import org.openmrs.maven.plugins.model.UpgradeDifferential;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public interface Wizard {
    boolean isInteractiveMode();

    void setInteractiveMode(boolean interactiveMode);

    void promptForNewServerIfMissing(Server server);

    void promptForMySQLDb(Server server);

    void promptForH2Db(Server server);

    void promptForDbCredentialsIfMissing(Server server);

    void promptForPlatformVersionIfMissing(Server server, List<String> versions);

    String promptForPlatformVersion(List<String> versions);

    void promptForDistroVersionIfMissing(Server server) throws MojoExecutionException;

    String promptForDistroVersion();

    String promptForMissingValueWithOptions(String message, String value, String parameterName, List<String> options, String customMessage, String customDefault);

    void showMessage(String message);

    String promptForValueIfMissingWithDefault(String message, String value, String parameterName, String defValue);

    String promptForValueWithDefaultList(String value, String parameterName, List<String> values);

    String promptForValueIfMissing(String value, String parameterName);

    String promptForJdkPath(Server server);

    boolean promptYesNo(String text);

    boolean checkYes(String value);

    File getCurrentServerPath() throws MojoExecutionException;

    String promptForExistingServerIdIfMissing(String serverId);

    List<String> getJdkPaths();

    Properties getSdkProperties() throws IOException;

    public boolean isThereJdkUnderPath(String jdkPath);

    List<String> getListOfServers();

    String addMySQLParamsIfMissing(String dbUri);

    void showJdkErrorMessage(String jdk, String platform, String recommendedJdk, String pathToProps);

    boolean promptForConfirmDistroUpgrade(UpgradeDifferential upgradeDifferential, Server server, DistroProperties distroProperties);

}
