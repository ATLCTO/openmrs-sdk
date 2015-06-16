package org.openmrs.maven.plugins.utility;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.openmrs.maven.plugins.model.Artifact;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Class for reading/writing pom.xml
 */
public class ConfigurationManager {
    private String path;
    private Model model;
    private Log log;

    /**
     * Default constructor
     */
    private ConfigurationManager() {
        model = new Model();
    }

    /**
     * Create new configuration object by the path
     * @param projectPath - path to project
     */
    public ConfigurationManager(String projectPath, Log log) {
        this();
        this.log = log;
        this.path = new File(projectPath, SDKConstants.OPENMRS_SERVER_POM).getPath();
        File conf = new File(path);
        FileReader reader = null;
        if (conf.exists()) {
            try {
                reader = new FileReader(path);
                model = new MavenXpp3Reader().read(reader);
                reader.close();
            } catch (IOException e) {
                log.error(e.getMessage());
            } catch (XmlPullParserException e) {
                log.error(e.getMessage());
            } finally {
                IOUtils.closeQuietly(reader);
            }
        }
    }

    /**
     * Get list of model profiles
     * @return list of profiles
     */
    public List<Profile> getProfiles() {
        return model.getProfiles();
    }

    /**
     * Add profile to model
     * @param p - profile to add
     */
    public void addProfile(Profile p) {
        model.addProfile(p);
    }

    /**
     * Get profile by id
     * @param id - key for search
     * @return Profile with given id
     */
    public Profile getProfile(String id) {
        for (Profile p: model.getProfiles()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    /**
     * Check if configuration exists, and add artifact list to configuration
     * @param list - artifact list
     */
    public void addArtifactsToConfiguration(List<Artifact> list) {
        PluginExecution execution = model.getBuild().getPlugins().get(0).getExecutions().get(0);
        Xpp3Dom config = (Xpp3Dom) execution.getConfiguration();
        if (config == null) config = new Xpp3Dom("configuration");
        if (config.getChild("artifactItems") == null) config.addChild(new Xpp3Dom("artifactItems"));
        Xpp3Dom artifactItems = config.getChild("artifactItems");
        for (Artifact artifact: list) {
            Xpp3Dom item = new Xpp3Dom("artifactItem");
            // create groupId
            Xpp3Dom groupId = new Xpp3Dom("groupId");
            groupId.setValue(artifact.getGroupId());
            item.addChild(groupId);
            // create artifactId
            Xpp3Dom artifactId = new Xpp3Dom("artifactId");
            artifactId.setValue(artifact.getArtifactId());
            item.addChild(artifactId);
            // create destFileName
            Xpp3Dom destFileName = new Xpp3Dom("destFileName");
            destFileName.setValue(artifact.getDestFileName());
            item.addChild(destFileName);
            // create version
            Xpp3Dom version = new Xpp3Dom("version");
            version.setValue(artifact.getVersion());
            item.addChild(version);
            // create type (if it set)
            if (artifact.getType() != null) {
                Xpp3Dom type = new Xpp3Dom("type");
                type.setValue(artifact.getType());
                item.addChild(type);
            }
            // add artifact to list
            artifactItems.addChild(item);
        }
        // set config to POM
        execution.setConfiguration(config);
    }

    /**
     * Set pom version
     * @param version - server version
     */
    public void setVersion(String version) {
        model.setVersion(version);
    }

    /**
     * Write model to pom file
     */
    public void apply() {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        FileWriter fileWrite = null;
        try {
            fileWrite = new FileWriter(path);
            writer.write(fileWrite, model);
            fileWrite.close();
        } catch (IOException e) {
            log.error(e.getMessage());
        } finally {
            IOUtils.closeQuietly(fileWrite);
        }
    }
}
