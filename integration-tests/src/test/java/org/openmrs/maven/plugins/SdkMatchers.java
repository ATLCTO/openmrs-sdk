package org.openmrs.maven.plugins;

import org.hamcrest.CustomMatcher;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.openmrs.maven.plugins.bintray.BintrayId;
import org.openmrs.maven.plugins.bintray.BintrayPackage;
import org.openmrs.maven.plugins.model.Artifact;
import org.openmrs.maven.plugins.model.Server;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.startsWith;

public class SdkMatchers {

    public static Matcher<Server> serverHasVersion(final String version) {
        return new FeatureMatcher<Server, String>(equalTo(version), "server version", "server version") {
            @Override
            protected String featureValueOf(final Server actual) {
                return actual.getVersion();
            }
        };
    }
    public static Matcher<Server> hasUserOwa(final BintrayId owa) {
        return new FeatureMatcher<Server, List<BintrayId>>(hasItem(owa), "user owas", "server user owas") {
            @Override
            protected List<BintrayId> featureValueOf(Server actual) {
                return actual.getUserOWAs();
            }
        };
    }
    public static Matcher<BintrayPackage> hasOwner(final String owner) {
        return new FeatureMatcher<BintrayPackage, String>(equalTo(owner), "server version", "server version") {
            @Override
            protected String featureValueOf(final BintrayPackage actual) {
                return actual.getOwner();
            }
        };
    }
    public static Matcher<BintrayPackage> hasRepository(final String repository) {
        return new FeatureMatcher<BintrayPackage, String>(equalTo(repository), "server version", "server version") {
            @Override
            protected String featureValueOf(final BintrayPackage actual) {
                return actual.getRepository();
            }
        };
    }
    public static Matcher<File> hasNameStartingWith(final String namePrefix) {
        return new FeatureMatcher<File, String>(startsWith(namePrefix), "file with name", "file with name") {
            @Override
            protected String featureValueOf(final File actual) {
                return actual.getName();
            }
        };
    }
    public static Matcher<Server> hasPropertyEqualTo(final String propertyName, final String propertyValue) {
        return new FeatureMatcher<Server, String>(equalTo(propertyValue), "property value", "property value") {
            @Override
            protected String featureValueOf(final Server actual) {
                return actual.getCustomProperties().get(propertyName);
            }
        };
    }
}
