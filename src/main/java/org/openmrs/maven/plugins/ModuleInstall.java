package org.openmrs.maven.plugins;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.openmrs.maven.plugins.model.Artifact;
import org.openmrs.maven.plugins.utility.AttributeHelper;
import org.openmrs.maven.plugins.utility.ConfigurationManager;
import org.openmrs.maven.plugins.utility.SDKConstants;

import java.io.File;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * @goal install-module
 * @requiresProject false
 */
public class ModuleInstall extends AbstractMojo {

    private static final String DEFAULT_FAIL_MESSAGE = "Server with such serverId is not exists";
    private static final String DEFAULT_OK_MESSAGE = "Module '%s' installed successfully";
    private static final String DEFAULT_UPDATE_MESSAGE = "Module '%s' was updated to version '%s'";

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
     * The Maven BuildPluginManager component.
     *
     * @component
     * @required
     */
    private BuildPluginManager pluginManager;

    /**
     * @parameter expression="${serverId}"
     */
    private String serverId;

    /**
     * @parameter expression="${artifactId}"
     */
    private String artifactId;

    /**
     * @parameter expression="${groupId}" default-value="org.openmrs.module"
     */
    private String groupId;

    /**
     * @parameter expression="${version}"
     */
    private String version;

    /**
     * @component
     */
    private Prompter prompter;

    public ModuleInstall() {};

    public ModuleInstall(Prompter prompter) {
        this.prompter = prompter;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        installModule(serverId, groupId, artifactId, version);
    }

    /**
     * Install module to selected server
     * @param serverId
     * @param groupId
     * @param artifactId
     * @param version
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    public void installModule(String serverId, String groupId, String artifactId, String version) throws MojoExecutionException, MojoFailureException {
        AttributeHelper helper = new AttributeHelper(prompter);
        File serverPath = getServerPath(helper, serverId);
        Artifact artifact = getArtifactForSelectedParameters(helper, groupId, artifactId, version);
        artifact.setArtifactId(artifact.getArtifactId() + "-omod");
        File modules = new File(serverPath, SDKConstants.OPENMRS_SERVER_MODULES);
        Element[] artifactItems = new Element[1];
        artifactItems[0] = artifact.toElement(modules.getPath());
        executeMojo(
                plugin(
                        groupId(SDKConstants.PLUGIN_DEPENDENCIES_GROUP_ID),
                        artifactId(SDKConstants.PLUGIN_DEPENDENCIES_ARTIFACT_ID),
                        version(SDKConstants.PLUGIN_DEPENDENCIES_VERSION)
                ),
                goal("copy"),
                configuration(
                        element(name("artifactItems"), artifactItems)
                ),
                executionEnvironment(mavenProject, mavenSession, pluginManager)
        );

        File[] listOfModules = modules.listFiles();
        boolean versionUpdated = false;
        boolean removed = false;
        for (File itemModule : listOfModules) {
            if (itemModule.getName().startsWith(artifact.getArtifactId()) && (!itemModule.getName().equals(artifact.getDestFileName()))) {
                versionUpdated = true;
                removed = itemModule.delete();
                break;
            }
        }
        if (versionUpdated) {
            if (removed) getLog().info(String.format(DEFAULT_UPDATE_MESSAGE, artifact.getArtifactId(), artifact.getVersion()));
        }
        else getLog().info(String.format(DEFAULT_OK_MESSAGE, artifact.getArtifactId()));
    }

    /**
     * Get attribute values and prompt if not selected
     * @param helper
     * @param groupId
     * @param artifactId
     * @param version
     * @return
     */
    public Artifact getArtifactForSelectedParameters(AttributeHelper helper, String groupId, String artifactId, String version) {
        File pomFile = new File(System.getProperty("user.dir"), "pom.xml");
        String moduleGroupId = null;
        String moduleArtifactId = null;
        String moduleVersion = null;
        if (pomFile.exists() && (artifactId == null) && (version == null)) {
            ConfigurationManager manager = new ConfigurationManager(pomFile.getPath(), getLog());
            if (manager.getParent() != null) {
                moduleGroupId = manager.getParent().getGroupId();
                moduleArtifactId = manager.getParent().getArtifactId();
                moduleVersion = manager.getParent().getVersion();
            }
            else {
                moduleGroupId = manager.getGroupId();
                moduleArtifactId = manager.getArtifactId();
                moduleVersion = manager.getVersion();
            }
        }
        else {
            moduleGroupId = groupId;
            try {
                moduleArtifactId = helper.promptForValueIfMissing(artifactId, "artifactId");
                moduleVersion = helper.promptForValueIfMissing(version, "version");
            } catch (PrompterException e) {
                getLog().error(e.getMessage());
            }
        }
        return new Artifact(moduleArtifactId, moduleVersion, moduleGroupId);
    }

    /**
     * Get path to server by serverId and prompt if missing
     * @param helper
     * @return
     * @throws MojoFailureException
     */
    public File getServerPath(AttributeHelper helper, String serverId, String failureMessage) throws MojoFailureException {
        File omrsHome = new File(System.getProperty("user.home"), SDKConstants.OPENMRS_SERVER_PATH);
        String resultServerId = null;
        try {
            resultServerId = helper.promptForValueIfMissing(serverId, "serverId");
        } catch (PrompterException e) {
            getLog().error(e.getMessage());
        }
        File serverPath = new File(omrsHome, resultServerId);
        if (!serverPath.exists()) {
            throw new MojoFailureException(failureMessage);
        }
        serverId = resultServerId;
        return serverPath;
    }

    /**
     * Get server with default failure message
     * @param helper
     * @param serverId
     * @return
     * @throws MojoFailureException
     */
    public File getServerPath(AttributeHelper helper, String serverId) throws MojoFailureException {
        return getServerPath(helper, serverId, DEFAULT_FAIL_MESSAGE);
    }
}
