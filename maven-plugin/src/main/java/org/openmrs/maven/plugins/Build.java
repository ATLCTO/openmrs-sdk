package org.openmrs.maven.plugins;

import org.apache.commons.io.FileUtils;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.openmrs.maven.plugins.model.Server;
import org.openmrs.maven.plugins.utility.Project;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * @goal build
 * @requiresProject false
 */
public class Build extends AbstractTask {

    /**
     * @parameter expression="${serverId}"
     */
    private String serverId;

    public Build(){}

    public Build(AbstractTask other) { super(other); }

    public Build(AbstractTask other, String serverId){
        super(other);
        this.serverId = serverId;
    }

    @Override
    public void executeTask() throws MojoExecutionException, MojoFailureException {
        serverId = wizard.promptForExistingServerIdIfMissing(serverId);
        Server server = Server.loadServer(serverId);


        File tempFolder = createTempReactorProject(server);
        try {
            cleanInstallServerProject(tempFolder.getAbsolutePath());
        } catch (VerificationException e) {
            deleteTempReactorProject(tempFolder);
            throw new RuntimeException("Failed to install watched modules", e);
        }
        deleteTempReactorProject(tempFolder);
        try {
            deployWatchedModules(server);
        } catch (MavenInvocationException e) {
            throw new MojoFailureException("Failed to deploy watched modules", e);
        }

    }

    /**
     * Creates temporary Maven reactor project to build dependencies in the correct order
     *
     * @param server
     * @return
     * @throws MojoFailureException
     * @throws MojoExecutionException
     */
    private File createTempReactorProject(Server server) throws MojoFailureException, MojoExecutionException {
        File tempFolder = new File(server.getServerDirectory(), "temp-project");
        if (!tempFolder.exists()) {
            tempFolder.mkdir();
        }
        Model tempModel = createModel();
        Set<Project> watchedModules = server.getWatchedProjects();
        for(Project module: watchedModules){
            Path newLink = Paths.get(new File(tempFolder, module.getArtifactId()).getAbsolutePath());
            Path existingfile = Paths.get(module.getPath());
            try {
                Files.createSymbolicLink(newLink, existingfile);
            } catch (IOException e) {
                copyModuleToTempServer(module.getPath(), newLink.toString());
            }  finally {
                tempModel.addModule(module.getArtifactId());
            }
        }

        try {
            Writer writer = new FileWriter(new File(tempFolder, "pom.xml"));
            new MavenXpp3Writer().write(writer, tempModel);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write pom.xml", e);
        }

        return tempFolder;
    }

    private void copyModuleToTempServer(String orginalPath, String newPath){
        File module = new File(orginalPath);
        File copiedModule = new File(newPath);

        try {
            FileUtils.copyDirectory(module, copiedModule);
        } catch (IOException e) {
            throw new RuntimeException("Could not copy modules", e);
        }
    }

    /**
     * Deletes temporary Maven reactor project
     *
     * @param tempFolder
     */
    private void deleteTempReactorProject(File tempFolder) {
        FileUtils.deleteQuietly(tempFolder);
    }

    /**
     * Creates Model to generate pom.xml for temporary project
     *
     * @return
     */
    private Model createModel(){
        Model model = new Model();
        model.setArtifactId(String.format("openmrs-sdk-server-%s", serverId));
        model.setVersion("1.0.0-SNAPSHOT");
        model.setGroupId("org.openmrs");
        model.setPackaging("pom");
        model.setModelVersion("4.0.0");

        return model;
    }

    /**
     * Deploy all watched modules to server
     *
     * @param server
     * @throws MojoFailureException
     * @throws MojoExecutionException
     * @throws MavenInvocationException
     */
    private void deployWatchedModules(Server server) throws MojoFailureException, MojoExecutionException, MavenInvocationException {
        Set<Project> watchedProject = server.getWatchedProjects();
        for (Project module: watchedProject) {
            Project project = Project.loadProject(new File(module.getPath()));
            new Deploy(this).deployModule(project.getGroupId(), project.getArtifactId(), project.getVersion(), server);
        }
    }

    /**
     * Run "mvn clean install -DskipTests" command in the given directory
     * @param directory
     * @throws MojoFailureException
     */
    private void cleanInstallServerProject(String directory) throws MojoFailureException, VerificationException {
        Verifier verifier = new Verifier(directory, true);
        verifier.setAutoclean(true);
        verifier.addCliOption("-DskipTests");
        verifier.executeGoal("install");
    }
}
