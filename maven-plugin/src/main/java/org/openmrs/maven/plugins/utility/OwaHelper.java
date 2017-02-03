package org.openmrs.maven.plugins.utility;

import com.atlassian.util.concurrent.Nullable;
import com.github.zafarkhaja.semver.Parser;
import com.github.zafarkhaja.semver.Version;
import com.github.zafarkhaja.semver.expr.Expression;
import com.github.zafarkhaja.semver.expr.ExpressionParser;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.openmrs.maven.plugins.model.NodeDistro;
import org.openmrs.maven.plugins.model.PackageJson;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

public class OwaHelper {
	private final static String FRONTEND_BUILDER_GROUP_ID = "com.github.eirslett";
	private final static String FRONTEND_BUILDER_ARTIFACT_ID = "frontend-maven-plugin";
	private final static String FRONTEND_BUILDER_VERSION = "1.3";

	private final static String MAVEN_EXEC_PLUGIN_GROUP_ID = "org.codehaus.mojo";
	private final static String MAVEN_EXEC_PLUGIN_ARTIFACT_ID = "exec-maven-plugin";
	private final static String MAVEN_EXEC_PLUGIN_VERSION = "1.5.0";

	public static final String PACKAGE_JSON_FILENAME = "package.json";
	public static final String NODE_VERSION_KEY = "node";
	public static final String NPM_VERSION_KEY = "npm";

	private MavenSession session;

	private File installationDir;

	private MavenProject mavenProject;

	private BuildPluginManager pluginManager;

	private Wizard wizard;

	//enable chaining
	public OwaHelper setInstallationDir(File installationDir) {
		this.installationDir = installationDir;
		return this;
	}

	public OwaHelper(){}

	public OwaHelper(MavenSession session, MavenProject mavenProject, BuildPluginManager pluginManager, Wizard wizard) {
		this.session = session;
		this.mavenProject = mavenProject;
		this.pluginManager = pluginManager;
		this.wizard = wizard;
	}

	public void createOwaProject() throws MojoExecutionException {
		File owaDir = installationDir != null ? installationDir : prepareOwaDir();

		wizard.showMessage("Creating OWA project in " + owaDir.getAbsolutePath() + "...\n");

		installLocalNodeAndNpm(SemVersion.valueOf(SDKConstants.NODE_VERSION), SemVersion.valueOf(SDKConstants.NPM_VERSION), owaDir.getAbsolutePath());
		runMojoExecutor(Arrays.asList(MojoExecutor.element("arguments", "install -g yo generator-openmrs-owa"), MojoExecutor.element("installDirectory", owaDir.getAbsolutePath())), "npm");
		try {
			runYeoman(owaDir);
			addHelperScripts(owaDir.getAbsolutePath());
		} catch (IOException e) {
			throw new IllegalStateException("Failed starting yeoman", e);
		} catch (InterruptedException e) {
			throw new IllegalStateException("Failed running yeoman", e);
		}
	}

	private File prepareOwaDir() {
		File owaDir = new File(System.getProperty("user.dir"));
		boolean pathCorrect = false;
		do {
			String owaDirValue = wizard.promptForValueIfMissingWithDefault("Please specify a directory for OWA project (a relative or absolute path)", owaDir.getName(), null, null);
			File owaDirPath = new File(owaDirValue);
			if (owaDirPath.isAbsolute()) {
				owaDir = owaDirPath;
			} else {
				owaDir = new File(owaDir, owaDirValue);
			}

			if (owaDir.exists()) {
				if (!owaDir.isDirectory()) {
					wizard.showError("The specified path " + owaDir.getAbsolutePath() + " is not a directory!");
				} else if (owaDir.listFiles().length != 0) {
					wizard.showMessage("The specified directory " + owaDir.getAbsolutePath() + " is not empty.");
					boolean create = wizard.promptYesNo("Are you sure you want to create a new OWA project in it?");
					if (create) {
						//in case someone just deleted the dir after seeing the prompt
						if (!owaDir.exists()) {
							owaDir.mkdir();
						}
						pathCorrect = true;
					}
				}
			} else {
				owaDir.mkdir();
				pathCorrect = true;
			}
		} while (!pathCorrect);

		return owaDir;
	}

	private void addHelperScripts(String path) {
		File npmCmd = new File(path, "npm.cmd");
		URL npmCmdSrc = getClass().getClassLoader().getResource("npm.cmd");
		File npm = new File(path, "npm");
		URL npmSrc = getClass().getClassLoader().getResource("npm");
		try {
			FileUtils.copyURLToFile(npmSrc, npm);
			FileUtils.copyURLToFile(npmCmdSrc, npmCmd);
			npm.setExecutable(true);
			npmCmd.setExecutable(true);
		} catch (IOException e) {
			throw new RuntimeException("Could not copy helper scripts to OWA directory", e);
		}

	}

	private void runYeoman(File directory) throws InterruptedException, IOException {
		ProcessBuilder builder = new ProcessBuilder()
				.directory(directory)
				.command(getYoExecutable(directory))
				.redirectErrorStream(true)
				.inheritIO();

		Process process = builder.start();
		process.waitFor();

	}

	private String[] getYoExecutable(File directory) {
		if (SystemUtils.IS_OS_WINDOWS) {
			return new String[]{new File(directory, "node\\node.exe").getAbsolutePath(), "node\\node_modules\\yo\\lib\\cli.js", "openmrs-owa"};
		} else {
			return new String[]{"node/node", "lib/node_modules/yo/lib/cli.js", "openmrs-owa"};
		}
	}

	private void runMojoExecutor(List<MojoExecutor.Element> configuration, String goal) throws MojoExecutionException {
		executeMojo(
				plugin(
						groupId(FRONTEND_BUILDER_GROUP_ID),
						artifactId(FRONTEND_BUILDER_ARTIFACT_ID),
						version(FRONTEND_BUILDER_VERSION)
				),
				goal(goal),
				configuration(configuration.toArray(new MojoExecutor.Element[0])),
				executionEnvironment(mavenProject, session, pluginManager)
		);
	}

	public String getSystemNpmVersion() {
		String npmExecutable = getNpmSystemExecutable();
		String npm =runProcessAndGetFirstResponseLine(npmExecutable, "-v");
		return StringUtils.isNotBlank(npm) ? npm : null;
	}

	public String getSystemNodeVersion() {
		String node = runProcessAndGetFirstResponseLine("node", "-v");
		return formatNodeVersion(node);
	}

	public String getProjectNodeVersion() {
		String node;
		if (SystemUtils.IS_OS_WINDOWS) {
			node = runProcessAndGetFirstResponseLine("node\\node.exe", "-v");
		} else {
			node = runProcessAndGetFirstResponseLine("node/node", "-v");
		}
		return formatNodeVersion(node);
	}

	public String getProjectNpmVersion() {
		String npm;
		if (SystemUtils.IS_OS_WINDOWS) {
			npm = runProcessAndGetFirstResponseLine("node\\npm.cmd", "-v");
		} else {
			npm = runProcessAndGetFirstResponseLine("node/npm", "-v");
		}
		return StringUtils.isNotBlank(npm) ? npm : null;
	}

	private String formatNodeVersion(String node) {
		if (StringUtils.isNotBlank(node)) {
			if (node.startsWith("v")) {
				node = node.substring(1);
			}

			return node;
		} else {
			return null;
		}
	}

	private String runProcessAndGetFirstResponseLine(String command, @Nullable String params) {
		Process process = null;
		List<String> lines = null;
		try {
			if (params != null) {
				process = new ProcessBuilder(command, params).redirectErrorStream(true).start();
			}
			else {
				process = new ProcessBuilder(command).redirectErrorStream(true).start();
			}
			lines = IOUtils.readLines(process.getInputStream());
			process.waitFor();
		} catch (InterruptedException | IOException e) {
			System.out.println("Cannot run '" + command + "' command due to " + e.getMessage());
		}

		if (process != null && process.exitValue() == 0) {
			if (lines != null) {
				return lines.get(0);
			}
			else {
				return null;
			}
		}
		else {
			return null;
		}
	}

	public void resolveExactVersions(SemVersion node, SemVersion npm) {
		List<NodeDistro> versions = getNodeDistros();

		for (NodeDistro distVersion : versions) {
			if (distVersion.getNpm() == null) {
				//stop processing for old node distros without npm
				return;
			}

			if (!node.isExact() && node.satisfies(distVersion.getVersion())) {
				node.setExactVersion(distVersion.getVersion());
			}

			if (npm != null && !npm.isExact() && npm.satisfies(distVersion.getNpm())) {
				npm.setExactVersion(distVersion.getNpm());
			}

			if (node.isExact() && (npm == null || npm.isExact())) {
				return;
			}
		}
	}

	public List<NodeDistro> getNodeDistros() {
		Gson gson = new Gson();
		HttpURLConnection conn;
		try {
			URL url = new URL("https://nodejs.org/dist/index.json");
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			NodeDistro[] result = gson.fromJson(rd, NodeDistro[].class);
			return new ArrayList<>(Arrays.asList(result));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to fetch node distributions", e);
		}
	}

	public static class SemVersion {
		Expression expression;
		String rawVersion;
		boolean exact;

		public SemVersion(String version) {
			this.rawVersion = version;

			try {
				Version.valueOf(version);
				exact = true;
			} catch (Exception e) {
				//it's an expression
				exact = false;
				Parser<Expression> parser = ExpressionParser.newInstance();
				Expression exp = parser.parse(version);
				expression = exp;
			}
		}

		public static SemVersion valueOf(String version) {
			if (StringUtils.isBlank(version)) {
				return null;
			}

			return new SemVersion(version);
		}

		public boolean isExact() {
			return exact;
		}

		public String getExactVersion() {
			return rawVersion;
		}

		public void setExactVersion(String version) {
			this.rawVersion = version;
			exact = true;
		}

		public boolean satisfies(String version) {
			if (rawVersion.equals(version)) {
				return true;
			} else if (expression != null){
				return Version.valueOf(version).satisfies(expression);
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			return rawVersion;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			SemVersion that = (SemVersion) o;

			return rawVersion.equals(that.rawVersion);
		}

		@Override
		public int hashCode() {
			return rawVersion.hashCode();
		}
	}

	public SemVersion parseVersion(String version) {
		return SemVersion.valueOf(version);
	}

	public SemVersion getProjectNpmFromPackageJson() {
		PackageJson packageJson = getPackageJsonFromJsonFile(PACKAGE_JSON_FILENAME);
		if (packageJson.getEngines() != null) {
			return SemVersion.valueOf(packageJson.getEngines().get(NPM_VERSION_KEY));
		}
		return null;
	}
	public SemVersion getProjectNodeFromPackageJson() {
		PackageJson packageJson = getPackageJsonFromJsonFile(PACKAGE_JSON_FILENAME);
		if (packageJson.getEngines() != null) {
			return SemVersion.valueOf(packageJson.getEngines().get(NODE_VERSION_KEY));
		}
		return null;
	}

	public static PackageJson getPackageJsonFromJsonFile(String jsonFilename) {
		Reader reader = null;
		try {
			if (new File(jsonFilename).exists()) {
				reader = new FileReader(jsonFilename);
			} else {
				InputStream in = OwaHelper.class.getResourceAsStream(jsonFilename);
				reader = new InputStreamReader(in);
			}
			PackageJson result = new Gson().fromJson(reader, PackageJson.class);
			reader.close();
			return result;
		} catch (IOException e) {
			throw new IllegalStateException("Couldn't find " + jsonFilename + " at " + new File(jsonFilename).getAbsolutePath());
		} finally {
			IOUtils.closeQuietly(reader);
		}
	}

	public void runLocalNpmCommandWithArgs(String arg) throws MojoExecutionException {
		List<String> args = new ArrayList<>();
		args.add(arg);
		runLocalNpmCommandWithArgs(args);
	}

	public void runLocalNpmCommandWithArgs(List<String> args) throws MojoExecutionException {
		List<MojoExecutor.Element> configuration = new ArrayList<>();

		StringBuilder argsString = new StringBuilder();

		for (String argument : args) {
			argsString.append(argument);
			argsString.append(" ");
		}

		configuration.add(element("arguments", argsString.toString()));
		executeMojo(
				plugin(
						groupId(FRONTEND_BUILDER_GROUP_ID),
						artifactId(FRONTEND_BUILDER_ARTIFACT_ID),
						version(FRONTEND_BUILDER_VERSION)
				),
				goal("npm"),
				configuration(configuration.toArray(new MojoExecutor.Element[0])),
				executionEnvironment(mavenProject, session, pluginManager)
		);
	}

	public void runSystemNpmCommandWithArgs(String arg) throws MojoExecutionException {
		List<String> args = new ArrayList<>();
		args.add(arg);
		runSystemNpmCommandWithArgs(args);
	}

	public void runSystemNpmCommandWithArgs(List<String> args) throws MojoExecutionException {
		List<MojoExecutor.Element> configuration = new ArrayList<>();
		configuration.add(element("executable", getNpmSystemExecutable()));

		MojoExecutor.Element[] argsele = new MojoExecutor.Element[args.size()];

		for (int i = 0; i < args.size(); i++) {
			argsele[i] = new MojoExecutor.Element("argument", args.get(i));
		}

		MojoExecutor.Element parentArgs = new MojoExecutor.Element("arguments", argsele);

		configuration.add(parentArgs);
		executeMojo(
				plugin(
						groupId(MAVEN_EXEC_PLUGIN_GROUP_ID),
						artifactId(MAVEN_EXEC_PLUGIN_ARTIFACT_ID),
						version(MAVEN_EXEC_PLUGIN_VERSION)
				),
				goal("exec"),
				configuration(configuration.toArray(new MojoExecutor.Element[0])),
				executionEnvironment(mavenProject, session, pluginManager)
		);
	}

	private String getNpmSystemExecutable() {
		String npm;
		if (SystemUtils.IS_OS_WINDOWS) {
			npm = "npm.cmd";
		} else {
			npm = "npm";
		}
		return npm;
	}

	public void installLocalNodeAndNpm(SemVersion node, SemVersion npm, String installDirectory) throws MojoExecutionException {
		String nodeVersion;
		String npmVersion;

		if (!node.isExact() || (npm != null && !npm.isExact())) {
			resolveExactVersions(node, npm);
		}

		if (node.isExact()) {
			nodeVersion = node.getExactVersion();
			if (npm == null) {
				npmVersion = null;
			} else if (npm.isExact()) {
				npmVersion = npm.getExactVersion();
			} else {
				throw new MojoExecutionException("Could not find a matching npm version.");
			}
		} else {
			throw new MojoExecutionException("Could not find a matching node version.");
		}

		runInstallLocalNodeAndNpm(nodeVersion, npmVersion, installDirectory);
	}

	public void runInstallLocalNodeAndNpm(String nodeVersion, String npmVersion, String installDirectory) throws MojoExecutionException {
		List<MojoExecutor.Element> configuration = new ArrayList<>();
		configuration.add(element("nodeVersion", "v" + nodeVersion));
		if (npmVersion != null) {
			configuration.add(element("npmVersion", npmVersion));
		}
		if (installDirectory != null) {
			configuration.add(element("installDirectory", installDirectory));
		}
		executeMojo(
				plugin(
						groupId(FRONTEND_BUILDER_GROUP_ID),
						artifactId(FRONTEND_BUILDER_ARTIFACT_ID),
						version(FRONTEND_BUILDER_VERSION)
				),
				goal("install-node-and-npm"),
				configuration(configuration.toArray(new MojoExecutor.Element[0])),
				executionEnvironment(mavenProject, session, pluginManager)
		);
	}

}