package org.openmrs.maven.plugins.utility;

import java.io.File;

/**
 * Class for handling static values
 */
public class SDKValues {
    // archetype
    public static final String ARCH_CATALOG = "http://mavenrepo.openmrs.org/nexus/service/local/repositories/releases/content/archetype-catalog.xml";
    public static final String ARCH_GROUP_ID = "org.apache.maven.plugins";
    public static final String ARCH_ARTIFACT_ID = "maven-archetype-plugin";
    public static final String ARCH_VERSION = "2.3";
    // archetype project options
    public static final String ARCH_PROJECT_GROUP_ID = "org.openmrs.maven.archetypes";
    public static final String ARCH_PROJECT_ARTIFACT_ID = "maven-archetype-openmrs-project";
    public static final String ARCH_PROJECT_VERSION = "1.0.1";
    // project options
    public static final String PROJECT_GROUP_ID = "org.openmrs.distro";
    public static final String PROJECT_PACKAGE = "org.openmrs";
    // archetype module options
    public static final String ARCH_MODULE_GROUP_ID = "org.openmrs.maven.archetypes";
    public static final String ARCH_MODULE_ARTIFACT_ID = "maven-archetype-openmrs-module-2.x";
    public static final String ARCH_MODULE_VERSION = "1.1";
    // plugin module wizard
    public static final String WIZARD_GROUP_ID = "org.openmrs.maven.plugins";
    public static final String WIZARD_ARTIFACT_ID = "module-wizard-plugin";
    public static final String WIZARD_VERSION = "1.1.1";
    // default path to projects
    public static final String OPENMRS_SERVER_PATH = "openmrs";
    public static final String OPENMRS_SERVER_PROPERTIES = "server" + File.separator + "installation.h2.properties";
    public static final String OPENMRS_SERVER_POM = "server" + File.separator + "pom.xml";
    // dbUri for different db
    public static final String URI_MYSQL = "jdbc:mysql://localhost:3131";
    public static final String URI_POSTGRESQL = "jdbc:postgresql://localhost:5740";
    public static final String URI_H2 = "jdbc:h2://localhost";
    // dbDriver class for different db
    public static final String DRIVER_MYSQL = "com.mysql.jdbc.Driver";
    public static final String DRIVER_POSTGRESQL = "org.postgresql.Driver";
    public static final String DRIVER_H2 = "org.h2.Driver";
}