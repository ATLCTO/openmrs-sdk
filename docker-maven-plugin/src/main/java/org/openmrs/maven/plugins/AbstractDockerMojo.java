package org.openmrs.maven.plugins;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.DockerClientBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

abstract class AbstractDockerMojo extends AbstractMojo {

    protected static final String DEFAULT_MYSQL_CONTAINER_ID = "openmrs-mysql";
    protected static final String DEFAULT_MYSQL_PASSWORD = "Admin123";
    protected static final String MYSQL_5_6 = "mysql:5.6";
    protected static final String DEFAULT_MYSQL_EXPOSED_PORT = "3307";
    protected static final String DOCKER_MYSQL_DBURI = "jdbc:mysql://localhost:3307/";
    private static final String DEFAULT_HOST_LINUX = "unix:///var/run/docker.sock";

    /**
     * Option to include demo data
     *
     * @parameter expression="${dockerHost}"
     */
    protected String dockerHost;

    /**
     * @component
     * @required
     */
    protected Prompter prompter;

    protected DockerClient docker;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        resolveDocker();
        executeTask();
        try {
            docker.close();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to close Docker client");
        }
    }

    public abstract void executeTask() throws MojoExecutionException, MojoFailureException;

    protected void resolveDocker() {
        if(dockerHost == null){
            boolean isLinux = SystemUtils.IS_OS_LINUX;
            if(isLinux){
                dockerHost = prompt("Please specify your Docker host URL (either 'tcp://' or 'unix://')", DEFAULT_HOST_LINUX);
            } else {
                dockerHost = prompt("Please specify you Docker Machine host URL (format is: 'tcp://{docker-machine url}')","");
            }
        }
        docker = DockerClientBuilder.getInstance(dockerHost).build();
    }

    protected Container findContainerById(String id){
        List<Container> containers = docker.listContainersCmd().withShowAll(true).exec();
        for(Container container : containers){
            if (container.getId().equals(id)){
                return container;
            }
        }
        return null;
    }

    protected Container findContainerByLabel(String label) {
        List<Container> containers = docker.listContainersCmd().withShowAll(true).withLabelFilter(label).exec();
        if(containers.size() > 0) return containers.get(0);
        else return null;
    }

    protected String prompt(String message, String defaultValue) {
        try {
            if(StringUtils.isNotBlank(defaultValue)){
                message = message+String.format(" (default: '%s')", defaultValue);
            }
            String answer = prompter.prompt("\n"+message);
            if(StringUtils.isNotBlank(answer)){
                return answer;
            } else {
                return defaultValue;
            }
        } catch (PrompterException e) {
            throw new RuntimeException("Failed to prompt", e);
        }
    }

    protected String prompt(String message) {
        try {
            String answer = prompter.prompt(message);
            if(StringUtils.isBlank(answer)) return prompt(message);
            else return answer;
        } catch (PrompterException e) {
            throw new RuntimeException("Failed to prompt", e);
        }
    }

    protected void showMessage(String message) {
        System.out.println(message);
    }
}


