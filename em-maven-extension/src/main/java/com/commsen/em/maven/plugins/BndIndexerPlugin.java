package com.commsen.em.maven.plugins;

import static com.commsen.em.maven.extension.Constants.DEFAULT_TMP_BUNDLES;
import static com.commsen.em.maven.extension.Constants.PROP_CONFIG_TMP_BUNDLES;
import static com.commsen.em.maven.extension.Constants.VAL_BND_VERSION;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(role = BndIndexerPlugin.class)
public class BndIndexerPlugin extends DynamicMavenPlugin {

	private Logger logger = LoggerFactory.getLogger(BndIndexerPlugin.class);


	public void addToPom(MavenProject project) throws MavenExecutionException {

		String configuration = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" //
				+ "<configuration>" //
				+ "		<localURLs>ALLOWED</localURLs>" //
				+ "		<includeJar>true</includeJar>" //
				+ "		<outputFile>${project.build.directory}/index/index.xml</outputFile>" //
				+ "</configuration>"; //

		Plugin plugin = createPlugin("biz.aQute.bnd", "bnd-indexer-maven-plugin", VAL_BND_VERSION, configuration,
				"index", "index", null);

		project.getBuild().getPlugins().add(0, plugin);

	}

	public void addToPomForIndexingTmpBundles(MavenProject project) throws MavenExecutionException {

		String configuration = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" //
				+ "<configuration>" //
				+ "		<inputDir>"
				+ project.getProperties().getProperty(PROP_CONFIG_TMP_BUNDLES, DEFAULT_TMP_BUNDLES)
				+ "</inputDir>" //
				+ "		<outputFile>${project.build.directory}/index/index.xml</outputFile>" //
				+ "</configuration>"; //

		Plugin plugin = createPlugin("biz.aQute.bnd", "bnd-indexer-maven-plugin", VAL_BND_VERSION, configuration,
				"index", "local-index", null);

		project.getBuild().getPlugins().add(0, plugin);

		logger.info("Added `bnd-indexer-maven-plugin` to genrate an index of detected modules!");

	}

	public void configureForIndexGeneration(MavenProject project) throws MavenExecutionException {

		Plugin plugin = getPlugin(project, "biz.aQute.bnd:bnd-indexer-maven-plugin");

		if (plugin != null) {

			String configuration = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" //
					+ "<configuration>" //
					+ "		<localURLs>ALLOWED</localURLs>" //
					+ "		<includeJar>false</includeJar>" //
					+ "		<outputFile>${project.build.directory}/index.xml</outputFile>" //
					+ "</configuration>"; //
			
			configurePlugin(plugin, "default-index", configuration);

			logger.info("Configuring `bnd-indexer-maven-plugin` to genrate an index from project's dependencies!");

		}
	}
}
