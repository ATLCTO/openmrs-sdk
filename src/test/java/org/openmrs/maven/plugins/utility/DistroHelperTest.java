package org.openmrs.maven.plugins.utility;

import org.junit.Test;
import org.openmrs.maven.plugins.model.Artifact;
import org.openmrs.maven.plugins.model.UpgradeDifferential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;

/**
 *
 */
public class DistroHelperTest {

    @Test
    public void parseDistroArtifactShouldInferArtifactIdForRefapp() throws Exception{
        String distro = "referenceapplication:2.3";
        Artifact artifact = DistroHelper.parseDistroArtifact(distro);

        assertThat(artifact.getGroupId(), is(Artifact.GROUP_DISTRO));
        assertThat(artifact.getArtifactId(), is(SDKConstants.REFERENCEAPPLICATION_ARTIFACT_ID));
    }
    @Test
    public void parseDistroArtifactShouldSetDefaultGroupIdIfNotSpecified() throws Exception{
        String distro = "otherdistro:2.3";
        Artifact artifact = DistroHelper.parseDistroArtifact(distro);

        assertThat(artifact.getGroupId(), is(Artifact.GROUP_DISTRO));
    }
    @Test
    public void parseDistroArtifactShouldReturnNullIfInvalidFormat() throws Exception{
        String distro = "referenceapplication:2.3:fsf:444";
        Artifact artifact = DistroHelper.parseDistroArtifact(distro);
        assertThat(artifact, is(nullValue()));
    }
    @Test
    public void parseDistroArtifactShouldCreateProperArtifact() throws Exception{
        String distro = "org.openmrs.distromock:refapp:2.3";
        Artifact artifact = DistroHelper.parseDistroArtifact(distro);

        assertThat(artifact.getGroupId(), is("org.openmrs.distromock"));
        assertThat(artifact.getArtifactId(), is("refapp"));
        assertThat(artifact.getVersion(), is("2.3"));
    }
    @Test
    public void calculateUpdateDifferentialShouldFindArtifactsToAddList() throws Exception{
        UpgradeDifferential upgradeDifferential = DistroHelper.calculateUpdateDifferential(getMockOldArtifactList(), getMockNewArtifactList());

        assertThat(upgradeDifferential.getModulesToAdd(), hasItem(new Artifact("drugs", "0.2-SNAPSHOT")));
        assertThat(upgradeDifferential.getModulesToAdd(), hasSize(1));
    }
    @Test
    public void calculateUpdateDifferentialShouldAddSnapshotToUpdateMap() throws Exception{
        UpgradeDifferential upgradeDifferential = DistroHelper.calculateUpdateDifferential(getMockOldArtifactList(), getMockNewArtifactList());

        assertThat(upgradeDifferential.getUpdateOldToNewMap().keySet(), hasItem(new Artifact("appui", "0.1-SNAPSHOT")));
        assertThat(upgradeDifferential.getUpdateOldToNewMap().keySet(), hasItem(new Artifact("webservices","1.0")));
        assertThat(upgradeDifferential.getUpdateOldToNewMap().values(), hasItem(new Artifact("webservices","1.2")));
        assertThat(upgradeDifferential.getUpdateOldToNewMap().keySet(), hasSize(2));
    }

    @Test
    public void calculateUpdateDifferentialShouldFindPlatformUpdate() throws Exception{
        Artifact oldPlatform = new Artifact("openmrs-webapp", "10.7", Artifact.GROUP_WEB, Artifact.TYPE_WAR);
        Artifact newPlatform = new Artifact("openmrs-webapp", "12.0", Artifact.GROUP_WEB, Artifact.TYPE_WAR);

        UpgradeDifferential upgradeDifferential = DistroHelper.calculateUpdateDifferential(Arrays.asList(oldPlatform), Arrays.asList(newPlatform));
        assertThat(upgradeDifferential.getPlatformArtifact(), is(newPlatform));
    }

    @Test
    public void calculateUpgradeDifferentialShouldReturnEmptyListIfOlderModules() throws Exception{
        UpgradeDifferential upgradeDifferential = DistroHelper.calculateUpdateDifferential(getMockOldArtifactList(), getMockOldestArtifactList());

        assertThat(upgradeDifferential.getPlatformArtifact(), is(nullValue()));
        assertThat(upgradeDifferential.getModulesToAdd(), is(empty()));
        assertThat(upgradeDifferential.getUpdateOldToNewMap().values(), is(empty()));
    }

    @Test
    public void calculateUpgradeDifferentialShouldReturnOnlyUpdateSnapshotsIfSameList() throws Exception{
        UpgradeDifferential upgradeDifferential = DistroHelper.calculateUpdateDifferential(getMockOldArtifactList(), getMockOldArtifactList());

        assertThat(upgradeDifferential.getPlatformArtifact(), is(nullValue()));
        assertThat(upgradeDifferential.getModulesToAdd(), is(empty()));
        assertThat(upgradeDifferential.getUpdateOldToNewMap().keySet(), hasSize(1));
        assertThat(upgradeDifferential.getUpdateOldToNewMap().keySet(), hasItem(new Artifact("appui", "0.1-SNAPSHOT")));
    }


    private List<Artifact> getMockOldestArtifactList(){
        List<Artifact> oldList = new ArrayList<>();
        oldList.addAll(Arrays.asList(
                new Artifact("webservices","0.7"),
                new Artifact("webapp", "1.7"),
                new Artifact("openmrs-webapp", "10.7", Artifact.GROUP_WEB, Artifact.TYPE_WAR)
        ));

        return oldList;
    }

    private List<Artifact> getMockOldArtifactList(){
        List<Artifact> oldList = new ArrayList<>();
        oldList.addAll(Arrays.asList(
                new Artifact("webservices","1.0"),
                new Artifact("webapp", "1.12"),
                new Artifact("appui", "0.1-SNAPSHOT"),
                new Artifact("openmrs-webapp", "10.7", Artifact.GROUP_WEB, Artifact.TYPE_WAR)
        ));

        return oldList;
    }

    private List<Artifact> getMockNewArtifactList(){
        List<Artifact> oldList = new ArrayList<>();
        oldList.addAll(Arrays.asList(
                new Artifact("webservices","1.2"),
                new Artifact("webapp", "1.12"),
                new Artifact("appui", "0.1-SNAPSHOT"),
                new Artifact("drugs", "0.2-SNAPSHOT"),
                new Artifact("openmrs-webapp", "12.0", Artifact.GROUP_WEB, Artifact.TYPE_WAR)
        ));
        return oldList;
    }
}
