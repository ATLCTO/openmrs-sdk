package org.openmrs.maven.plugins;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.openmrs.maven.plugins.model.Server;
import org.openmrs.maven.plugins.utility.PropertyManager;
import org.openmrs.maven.plugins.utility.SDKConstants;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 *
 * @goal setup-platform
 * @requiresProject false
 *
 */
public class SetupPlatform extends AbstractMojo {

    /**
     * Default constructor
     */
    public SetupPlatform() {};

    /**
     * Constructor to use this class in other mojos
     * @param mavenProject - outer project
     * @param mavenSession - outer session
     * @param prompter - outer prompter
     * @param pluginManager - outer pluginManager
     */
    public SetupPlatform(MavenProject mavenProject,
                         MavenSession mavenSession,
                         Prompter prompter,
                         BuildPluginManager pluginManager) {
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.prompter = prompter;
        this.pluginManager = pluginManager;
    }

    /**
     * The project currently being build.
     *
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject mavenProject;

    /**
     * The current Maven session.
     *
     * @parameter expression="${session}"
     * @required
     */
    private MavenSession mavenSession;

    /**
     * Interactive mode param
     *
     * @parameter expression="${interactiveMode}" default-value="true"
     */
    private String interactiveMode;

    /**
     * Server id (folder name)
     *
     * @parameter expression="${serverId}"
     */
    private String serverId;

    /**
     * Platform version
     *
     * @parameter expression="${version} default-value="1.11.2"
     */
    private String version;

    /**
     * DB Driver type
     *
     * @parameter expression="${dbDriver}
     */
    private String dbDriver;

    /**
     * DB Uri
     *
     * @parameter expression="${dbUri}
     */
    private String dbUri;

    /**
     * DB User
     *
     * @parameter expression="${dbUser}
     */
    private String dbUser;

    /**
     * DB Pass
     *
     * @parameter expression="${dbPassword}
     */
    private String dbPassword;

    /**
     * Component for user prompt
     *
     * @component
     */
    private Prompter prompter;

    /**
     * The Maven BuildPluginManager component.
     *
     * @component
     * @required
     */
    private BuildPluginManager pluginManager;

    /**
     * Create and setup server with following parameters
     * @param serverId
     * @param version
     * @param dbDriver
     * @param dbUri
     * @param dbUser
     * @param dbPassword
     * @param interactiveMode
     * @throws MojoExecutionException
     */
    public Server setup(String serverId,
                      String version,
                      String dbDriver,
                      String dbUri,
                      String dbUser,
                      String dbPassword,
                      String interactiveMode) throws MojoExecutionException {
        // path to omrs home
        File omrsPath = new File(System.getProperty("user.home"), SDKConstants.OPENMRS_SERVER_PATH);
        // check if user not set serverId parameter
        if (serverId == null) try {
            // default server name
            String defaultId = "server";
            // index for search unused server name
            int indx = 0;
            // find unused name for server
            while (new File(omrsPath, defaultId).exists()) {
                indx++;
                defaultId = "server" + String.valueOf(indx);
            }
            // prompt this param
            serverId = prompter.prompt("Define value for property 'serverId': (default: '" + defaultId + "')");
            // check if user pressed 'enter' (default value)
            if (serverId.equals("")) serverId = defaultId;
        } catch (PrompterException e) {
            e.printStackTrace();
        }
        // path to server with serverId
        File serverPath = new File(omrsPath, serverId);
        // check existence
        if (serverPath.exists()) throw new MojoExecutionException("Server with same id already created");
        // execute plugin for create server
        executeMojo(
                plugin(
                        groupId(SDKConstants.ARCH_GROUP_ID),
                        artifactId(SDKConstants.ARCH_ARTIFACT_ID),
                        version(SDKConstants.ARCH_VERSION)
                ),
                goal("generate"),
                configuration(
                        element(name("archetypeCatalog"), SDKConstants.ARCH_CATALOG),
                        element(name("interactiveMode"), interactiveMode),
                        //element(name("package"), SDKConstants.PROJECT_PACKAGE),
                        element(name("archetypeGroupId"), SDKConstants.ARCH_PROJECT_GROUP_ID),
                        element(name("archetypeArtifactId"), SDKConstants.ARCH_PROJECT_ARTIFACT_ID),
                        element(name("archetypeVersion"), SDKConstants.ARCH_PROJECT_VERSION)
                        //element(name("groupId"), SDKConstants.PROJECT_GROUP_ID),
                        //element(name("artifactId"), serverId),
                        //element(name("version"), version)
                ),
                executionEnvironment(mavenProject, mavenSession, pluginManager)
        );
        // move server after creating
        File currentPath = new File(System.getProperty("user.dir"), serverId);
        try {
            // check if paths are not equal
            if (!currentPath.equals(serverPath)) FileUtils.moveDirectory(currentPath, serverPath);
            getLog().info("Server created successfully, path: " + serverPath.getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // check if any db parameter is set
        if ((dbDriver != null) || (dbUser != null) || (dbPassword != null) || (dbUri != null)) {
            // configure properties
            File propertiesFile = new File(serverPath.getPath(), SDKConstants.OPENMRS_SERVER_PROPERTIES);
            PropertyManager properties = new PropertyManager(propertiesFile.getPath());
            try {
                // prompt dbDriver if not set
                String defaultDriver = "mysql";
                if (dbDriver == null) dbDriver = prompter.prompt("Define value for property 'dbDriver': (default: 'mysql')");
                // check if default was set
                if ((dbDriver == null) || (dbDriver.equals(""))) dbDriver = defaultDriver;
                // get default uri for selected dbDriver
                String defaultUri = SDKConstants.URI_MYSQL;
                // postgres shortcut
                if ((dbDriver.equals("postgresql")) || (dbDriver.equals(SDKConstants.DRIVER_POSTGRESQL))) {
                    properties.setParam("dbDriver", SDKConstants.DRIVER_POSTGRESQL);
                    // update default uri for postgres
                    defaultUri = SDKConstants.URI_POSTGRESQL;
                }
                // h2 shortcut
                else if ((dbDriver.equals("h2")) || (dbDriver.equals(SDKConstants.DRIVER_H2))) {
                    properties.setParam("dbDriver", SDKConstants.DRIVER_H2);
                    // update default value for h2
                    defaultUri = SDKConstants.URI_H2;
                }
                // mysql shortcut
                else if (dbDriver.equals("mysql")) properties.setParam("dbDriver", SDKConstants.DRIVER_MYSQL);
                    // any other drivers
                else properties.setParam("dbDriver", dbDriver);
                // set dbUri if not set
                if (dbUri == null) dbUri = prompter.prompt("Define value for property 'dbUri': (default: '" + defaultUri + "')");
                // check if user choose default uri ('enter' pressed)
                if ((dbUri == null) || (dbUri.equals(""))) dbUri = defaultUri;
                // set dbUser property
                String defaultUser = "root";
                if (dbUser == null) dbUser = prompter.prompt("Define value for property 'dbUser': (default: '" + defaultUser + "')");
                // check if default was set
                if ((dbUser == null) || (dbUser.equals(""))) dbUser = defaultUser;
                // set dbPassword value
                if (dbPassword == null) dbPassword = prompter.prompt("Define value for property 'dbPassword'");
                // set other params
                properties.setParam("dbDriver", dbDriver);
                properties.setParam("dbUser", dbUser);
                properties.setParam("dbPassword", dbPassword);
                properties.setParam("dbUri", dbUri);
                properties.apply();
            } catch (PrompterException e) {
                e.printStackTrace();
            }
        }
        return new Server(serverId, serverPath.getPath(), dbDriver, dbUri, dbUser, dbPassword);
    }

    public void execute() throws MojoExecutionException {
        // create and configure server
        setup(serverId, version, dbDriver, dbUri, dbUser, dbPassword, interactiveMode);
    }
}