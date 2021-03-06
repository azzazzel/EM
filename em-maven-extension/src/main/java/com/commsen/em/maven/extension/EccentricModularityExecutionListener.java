package com.commsen.em.maven.extension;

import static com.commsen.em.maven.util.Constants.PROP_ACTION_AUGMENT;
import static com.commsen.em.maven.util.Constants.PROP_ACTION_EXECUTABLE;
import static com.commsen.em.maven.util.Constants.PROP_ACTION_MODULE;
import static com.commsen.em.maven.util.Constants.PROP_ACTION_MODULE_OLD;
import static com.commsen.em.maven.util.Constants.PROP_ACTION_RESOLVE;
import static com.commsen.em.maven.util.Constants.PROP_CONFIG_INDEX;
import static com.commsen.em.maven.util.Constants.PROP_PREFIX;
import static com.commsen.em.maven.util.Constants.PROP_PREFIX_OLD;
import static com.commsen.em.maven.util.Constants.VAL_BND_VERSION;
import static com.commsen.em.maven.util.Constants.VAL_INDEX_TYPE;

import java.util.LinkedList;
import java.util.List;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.commsen.em.maven.plugins.BndExportPlugin;
import com.commsen.em.maven.plugins.BndIndexerPlugin;
import com.commsen.em.maven.plugins.BndPlugin;
import com.commsen.em.maven.plugins.DistroPlugin;
import com.commsen.em.maven.plugins.EmRegisterContractPlugin;
import com.commsen.em.maven.plugins.LinkPlugin;

@Component(role = ExecutionListener.class, hint = "em")
public class EccentricModularityExecutionListener extends AbstractExecutionListener {

	@Requirement
	private BndPlugin bndPlugin;

	@Requirement
	private BndIndexerPlugin bndIndexerPlugin;

	@Requirement
	private BndExportPlugin bndExportPlugin;

	@Requirement
	private DistroPlugin distroPlugin;

	@Requirement
	private EmRegisterContractPlugin contractExporterPlugin;

	@Requirement
	private LinkPlugin linkPlugin;

	@Requirement(role = ArtifactRepositoryLayout.class, hint = "default")
	private ArtifactRepositoryLayout defaultLayout;

	private ExecutionListener delegate;

	private Logger logger = LoggerFactory.getLogger(EccentricModularityExecutionListener.class);

	public void setDelegate(ExecutionListener executionListener) {
		this.delegate = executionListener;
	}

	@Override
	public void projectStarted(ExecutionEvent event) {
		delegate.projectStarted(event);
		projectStarted(event.getSession().getProjectBuildingRequest(), event.getProject());
	}

	public void projectStarted(ProjectBuildingRequest projectBuildingRequest, MavenProject project) {
		
		boolean hasOld = project.getProperties().keySet().stream() //
				.filter(p -> p.toString().startsWith(PROP_PREFIX_OLD)) //
				.peek(p -> logger.error("Property '{}' uses old prefix! Use '{}' instead!", p,
						p.toString().replace(PROP_PREFIX_OLD, PROP_PREFIX))) //
				.count() > 0;

		if (hasOld) {
			throw new RuntimeException("Can't build project using old properties!");
		}

		hasOld = project.getProperties().keySet().stream() //
				.filter(p -> p.toString().startsWith(PROP_ACTION_MODULE_OLD))
				.peek(p -> logger.error("Property '{}' uses old syntax! Use '{}' instead!", p,
						p.toString().replace(PROP_ACTION_MODULE_OLD, PROP_ACTION_MODULE)))
				.count() > 0;

		if (hasOld) {
			throw new RuntimeException("Can't build project using old properties!");
		}

		if (VAL_BND_VERSION.toLowerCase().contains("snapshot")) {
			addBndSnapshotRepo(project);
		}

		boolean generateIndex = project.getProperties().containsKey(PROP_CONFIG_INDEX);
		boolean actionFound = false;

		/*
		 * TODO: figure out what to do if more than one action is provided! For now all
		 * will be executed which may have weird results
		 */
		logger.info("Adding plugins and adapting project configuration based on provided '" + PROP_PREFIX
				+ "*' properties!");

		try {
			linkPlugin.addToBuild(project);
		} catch (MavenExecutionException e) {
			throw new RuntimeException("Failed to add em-maven-plugin!", e);
		}

		if (project.getProperties().containsKey(PROP_ACTION_MODULE)) {
			actionFound = true;
			try {
				contractExporterPlugin.addToPom(project);
				bndPlugin.addToBuild(project);
			} catch (MavenExecutionException e) {
				throw new RuntimeException("Failed to add bnd-maven-plugin!", e);
			}
		}

		if (project.getProperties().containsKey(PROP_ACTION_AUGMENT)) {
			actionFound = true;
			try {
				bndPlugin.addToBuildForAugment(project);
			} catch (MavenExecutionException e) {
				throw new RuntimeException("Failed to add bnd-maven-plugin!", e);
			}

		}

		if (VAL_INDEX_TYPE.equals(project.getPackaging())) {
			actionFound = true;
			/*
			 * BND indexer plug-in is already added in the custom lifecycle for "index"
			 * type, so we only need to configure it
			 */
			try {
				bndIndexerPlugin.configureForIndexGeneration(project);
			} catch (MavenExecutionException e) {
				throw new RuntimeException("Failed to configure bnd-indexer-maven-plugin!", e);
			}
		}

		if (project.getProperties().containsKey(PROP_ACTION_RESOLVE)) {
			actionFound = true;

			String resolveTarget = project.getProperties().getProperty(PROP_ACTION_RESOLVE, "");
			if (!resolveTarget.trim().isEmpty()) {
				try {
					distroPlugin.createDistroJar(project, resolveTarget);
				} catch (MavenExecutionException e) {
					throw new RuntimeException("Failed to extract metadata from the target runtime '" + resolveTarget + "'!", e);
				}
			}
			try {
				/*
				 * TODO: Need a better way to add multiple plugins! For now add plugins in
				 * reverse order since they are added to the beginning of the list
				 */
				contractExporterPlugin.addToPom(project);
				if (generateIndex)
					bndIndexerPlugin.addToPomForIndexingTmpBundles(project);
				bndExportPlugin.addToPomForExport(project);
				bndPlugin.addToBuild(project);
			} catch (MavenExecutionException e) {
				throw new RuntimeException("Failed to add one of the required bnd plugins!", e);
			}
		}

		if (project.getProperties().containsKey(PROP_ACTION_EXECUTABLE)) {
			actionFound = true;
			try {
				/*
				 * TODO: Need a better way to add multiple plugins! For now add plugins in
				 * reverse order since they are added to the beginning of the list
				 */
				contractExporterPlugin.addToPom(project);
				if (generateIndex)
					bndIndexerPlugin.addToPomForIndexingTmpBundles(project);
				bndExportPlugin.addToPomForExecutable(project);
				bndPlugin.addToBuild(project);
			} catch (MavenExecutionException e) {
				throw new RuntimeException("Failed to add one of the required bnd plugins!", e);
			}
		}

		if (!actionFound) {
			logger.info("No '" + PROP_PREFIX + "*' action found! Project will be executed AS IS!");
		}
	}

	@Override
	public void projectDiscoveryStarted(ExecutionEvent event) {
		delegate.projectDiscoveryStarted(event);
	}

	@Override
	public void sessionStarted(ExecutionEvent event) {

		delegate.sessionStarted(event);
	}

	@Override
	public void sessionEnded(ExecutionEvent event) {

		delegate.sessionEnded(event);
	}

	@Override
	public void projectSkipped(ExecutionEvent event) {

		delegate.projectSkipped(event);
	}

	@Override
	public void projectSucceeded(ExecutionEvent event) {

		delegate.projectSucceeded(event);
	}

	@Override
	public void projectFailed(ExecutionEvent event) {

		delegate.projectFailed(event);
	}

	@Override
	public void forkStarted(ExecutionEvent event) {

		delegate.forkStarted(event);
	}

	@Override
	public void forkSucceeded(ExecutionEvent event) {

		delegate.forkSucceeded(event);
	}

	@Override
	public void forkFailed(ExecutionEvent event) {

		delegate.forkFailed(event);
	}

	@Override
	public void mojoSkipped(ExecutionEvent event) {

		delegate.mojoSkipped(event);
	}

	@Override
	public void mojoStarted(ExecutionEvent event) {

		delegate.mojoStarted(event);
	}

	@Override
	public void mojoSucceeded(ExecutionEvent event) {

		delegate.mojoSucceeded(event);
	}

	@Override
	public void mojoFailed(ExecutionEvent event) {

		delegate.mojoFailed(event);
	}

	@Override
	public void forkedProjectStarted(ExecutionEvent event) {

		delegate.forkedProjectStarted(event);
	}

	@Override
	public void forkedProjectSucceeded(ExecutionEvent event) {

		delegate.forkedProjectSucceeded(event);
	}

	@Override
	public void forkedProjectFailed(ExecutionEvent event) {

		delegate.forkedProjectFailed(event);
	}

	private void addBndSnapshotRepo(MavenProject project) {

		ArtifactRepository ar = new MavenArtifactRepository();
		ar.setId("bnd-snapshots");
		ar.setUrl("https://bndtools.ci.cloudbees.com/job/bnd.master/lastSuccessfulBuild/artifact/dist/bundles/");
		ar.setLayout(defaultLayout);

		List<ArtifactRepository> pluginRepos = new LinkedList<>();
		pluginRepos.addAll(project.getPluginArtifactRepositories());
		pluginRepos.add(ar);
		project.setPluginArtifactRepositories(pluginRepos);

		List<ArtifactRepository> repos = new LinkedList<>();
		repos.addAll(project.getRemoteArtifactRepositories());
		repos.add(ar);
		project.setRemoteArtifactRepositories(repos);

	}

}
