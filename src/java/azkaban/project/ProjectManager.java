/*
 * Copyright 2012 LinkedIn Corp.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.project;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipFile;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import azkaban.flow.CommonJobProperties;
import azkaban.flow.Flow;
import azkaban.project.ProjectLogEvent.EventType;
import azkaban.user.Permission;
import azkaban.user.User;
import azkaban.user.Permission.Type;
import azkaban.utils.DirectoryFlowLoader;
import azkaban.utils.Props;
import azkaban.utils.Utils;

public class ProjectManager {
	private static final Logger logger = Logger.getLogger(ProjectManager.class);
	
	private ConcurrentHashMap<Integer, Project> projectsById = new ConcurrentHashMap<Integer, Project>();
	private ConcurrentHashMap<String, Project> projectsByName = new ConcurrentHashMap<String, Project>();
	private final ProjectLoader projectLoader;
	private final Props props;
	private final File tempDir;
	private final int projectVersionRetention;
	private final boolean creatorDefaultPermissions;
	
	public ProjectManager(ProjectLoader loader, Props props) {
		this.projectLoader = loader;
		this.props = props;
		this.tempDir = new File(this.props.getString("project.temp.dir", "temp"));
		this.projectVersionRetention = (props.getInt("project.version.retention", 3));
		logger.info("Project version retention is set to " + projectVersionRetention);
		
		this.creatorDefaultPermissions = props.getBoolean("creator.default.proxy", true);
		
		if (!tempDir.exists()) {
			tempDir.mkdirs();
		}
		
		loadAllProjects();
	}

	private void loadAllProjects() {
		List<Project> projects;
		try {
			projects = projectLoader.fetchAllActiveProjects();
		} catch (ProjectManagerException e) {
			throw new RuntimeException("Could not load projects from store.", e);
		}
		for (Project proj: projects) {
			projectsByName.put(proj.getName(), proj);
			projectsById.put(proj.getId(), proj);
		}
		
		for (Project proj: projects) {
			loadAllProjectFlows(proj);
		}
	}
	
	private void loadAllProjectFlows(Project project) {
		try {
			List<Flow> flows = projectLoader.fetchAllProjectFlows(project);
			Map<String, Flow> flowMap = new HashMap<String, Flow>();
			for (Flow flow: flows) {
				flowMap.put(flow.getId(), flow);
			}
			
			project.setFlows(flowMap);
		}
		catch (ProjectManagerException e) {
			throw new RuntimeException("Could not load projects flows from store.", e);
		}
	}
	
	public List<String> getProjectNames() {
		return new ArrayList<String>(projectsByName.keySet());
	}

	public List<Project> getUserProjects(User user) {
		ArrayList<Project> array = new ArrayList<Project>();
		for (Project project : projectsById.values()) {
			Permission perm = project.getUserPermission(user);

			if (perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ))) {
				array.add(project);
			}
		}
		return array;
	}

  public List<Project> getGroupProjects(User user) {
    List<Project> array = new ArrayList<Project>();
    for (Project project : projectsById.values()) {
      if (project.hasGroupPermission(user, Type.READ)) {
        array.add(project);
      }
    }
    return array;
  }

	public List<Project> getUserProjectsByRegex(User user, String regexPattern) {
		List<Project> array = new ArrayList<Project>();
		Pattern pattern;
		try {
			pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
		} catch (PatternSyntaxException e) {
			logger.error("Bad regex pattern " + regexPattern);
			return array;
		}
		
		
		for (Project project : projectsById.values()) {
			Permission perm = project.getUserPermission(user);

			if (perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ))) {
				if(pattern.matcher(project.getName()).find() ) {
					array.add(project);
				}
			}
		}
		return array;
	}

	public List<Project> getProjects() {
		return new ArrayList<Project>(projectsById.values());
	}

	public List<Project> getProjectsByRegex(String regexPattern) {
		List<Project> allProjects = new ArrayList<Project>();
		Pattern pattern;
		try {
			pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
		} catch (PatternSyntaxException e) {
			logger.error("Bad regex pattern " + regexPattern);
			return allProjects;
		}
		for(Project project : getProjects()) {
			if(pattern.matcher(project.getName()).find()) {
				allProjects.add(project);
			}
		}
		return allProjects;
	}

	public Project getProject(String name) {
		return projectsByName.get(name);
	}

	public Project getProject(int id) {
		return projectsById.get(id);
	}

	public Project createProject(String projectName, String description, User creator) throws ProjectManagerException {
		if (projectName == null || projectName.trim().isEmpty()) {
			throw new ProjectManagerException("Project name cannot be empty.");
		} 
		else if (description == null || description.trim().isEmpty()) {
			throw new ProjectManagerException("Description cannot be empty.");
		} 
		else if (creator == null) {
			throw new ProjectManagerException("Valid creator user must be set.");
		} 
		else if (!projectName.matches("[a-zA-Z][a-zA-Z_0-9|-]*")) {
			throw new ProjectManagerException("Project names must start with a letter, followed by any number of letters, digits, '-' or '_'.");
		}

		if (projectsByName.contains(projectName)) {
			throw new ProjectManagerException("Project already exists.");
		}
		
		logger.info("Trying to create " + projectName + " by user " + creator.getUserId());
		Project newProject = projectLoader.createNewProject(projectName, description, creator);
		projectsByName.put(newProject.getName(), newProject);
		projectsById.put(newProject.getId(), newProject);
		
		if(creatorDefaultPermissions) {
		// Add permission to project
			projectLoader.updatePermission(newProject, creator.getUserId(), new Permission(Permission.Type.ADMIN), false);
			
			// Add proxy user 
			newProject.addProxyUser(creator.getUserId());
			try {
				updateProjectSetting(newProject);
			} catch (ProjectManagerException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		projectLoader.postEvent(newProject, EventType.CREATED, creator.getUserId(), null);
		
		return newProject;
	}

	public synchronized Project removeProject(Project project, User deleter) throws ProjectManagerException {
		projectLoader.removeProject(project, deleter.getUserId());
		projectLoader.postEvent(project, EventType.DELETED, deleter.getUserId(), null);
		
		projectsByName.remove(project.getName());
		projectsById.remove(project.getId());
		
		return project;
	}

	public void updateProjectDescription(Project project, String description, User modifier) throws ProjectManagerException {
		projectLoader.updateDescription(project, description, modifier.getUserId());
		projectLoader.postEvent(project, EventType.DESCRIPTION, modifier.getUserId(), "Description changed to " + description);
	}

	public List<ProjectLogEvent> getProjectEventLogs(Project project, int results, int skip) throws ProjectManagerException {
		return projectLoader.getProjectEvents(project, results, skip);
	}
	
	public Props getProperties(Project project, String source) throws ProjectManagerException {
		return projectLoader.fetchProjectProperty(project, source);
	}
	
	public Props getJobOverrideProperty(Project project, String jobName) throws ProjectManagerException {
		return projectLoader.fetchProjectProperty(project, jobName+".jor");
	}
	
	public void setJobOverrideProperty(Project project, Props prop, String jobName) throws ProjectManagerException {
		prop.setSource(jobName+".jor");
		Props oldProps = projectLoader.fetchProjectProperty(project, prop.getSource());
		if(oldProps == null) {
			projectLoader.uploadProjectProperty(project, prop);
		}
		else {
			projectLoader.updateProjectProperty(project, prop);
		}
		return;
	}

	public void updateProjectSetting(Project project) throws ProjectManagerException {
		projectLoader.updateProjectSettings(project);		
	}
	
	public void addProjectProxyUser(Project project, String proxyName, User modifier) throws ProjectManagerException {
		logger.info("User " + modifier.getUserId() + " adding proxy user " + proxyName + " to project " + project.getName());
		project.addProxyUser(proxyName);
		
		projectLoader.postEvent(project, EventType.PROXY_USER, modifier.getUserId(), "Proxy user " + proxyName + " is added to project.");
		updateProjectSetting(project);
	}
	
	public void removeProjectProxyUser(Project project, String proxyName, User modifier) throws ProjectManagerException {
		logger.info("User " + modifier.getUserId() + " removing proxy user " + proxyName + " from project " + project.getName());
		project.removeProxyUser(proxyName);
		
		projectLoader.postEvent(project, EventType.PROXY_USER, modifier.getUserId(), "Proxy user " + proxyName + " has been removed form the project.");
		updateProjectSetting(project);
	}
	
	public void updateProjectPermission(Project project, String name, Permission perm, boolean group, User modifier) throws ProjectManagerException {
		logger.info("User " + modifier.getUserId() + " updating permissions for project " + project.getName() + " for " + name + " " + perm.toString());
		projectLoader.updatePermission(project, name, perm, group);
		if (group) {
			projectLoader.postEvent(project, EventType.GROUP_PERMISSION, modifier.getUserId(), "Permission for group " + name + " set to " + perm.toString());
		}
		else {
			projectLoader.postEvent(project, EventType.USER_PERMISSION, modifier.getUserId(), "Permission for user " + name + " set to " + perm.toString());
		}
	}
	
	public void removeProjectPermission(Project project, String name, boolean group, User modifier) throws ProjectManagerException {
		logger.info("User " + modifier.getUserId() + " removing permissions for project " + project.getName() + " for " + name);
		projectLoader.removePermission(project, name, group);
		if (group) {
			projectLoader.postEvent(project, EventType.GROUP_PERMISSION, modifier.getUserId(), "Permission for group " + name + " removed.");
		}
		else {
			projectLoader.postEvent(project, EventType.USER_PERMISSION, modifier.getUserId(), "Permission for user " + name + " removed.");
		}
	}
	
	public void uploadProject(Project project, File archive, String fileType, User uploader) throws ProjectManagerException {
		logger.info("Uploading files to " + project.getName());
		
		// Unzip.
		File file = null;
		try {
			if (fileType == null) {
				throw new ProjectManagerException("Unknown file type for " + archive.getName());
			}
			else if ("zip".equals(fileType)) {
				file = unzipFile(archive);
			}
			else {
				throw new ProjectManagerException("Unsupported archive type for file " + archive.getName());
			}
		} catch(IOException e) {
			throw new ProjectManagerException("Error unzipping file.", e);
		}

		logger.info("Validating Flow for upload " + archive.getName());
		DirectoryFlowLoader loader = new DirectoryFlowLoader(logger);
		loader.loadProjectFlow(file);
		if(!loader.getErrors().isEmpty()) {
			logger.error("Error found in upload to " + project.getName() + ". Cleaning up.");
			
			try {
				FileUtils.deleteDirectory(file);
			} catch (IOException e) {
				file.deleteOnExit();
				e.printStackTrace();
			}
			
			StringBuffer errorMessage = new StringBuffer();
			errorMessage.append("Error found in upload. Cannot upload.\n");
			for (String error: loader.getErrors()) {
				errorMessage.append(error);
				errorMessage.append('\n');
			}

			throw new ProjectManagerException(errorMessage.toString());
		}
		
		Map<String, Props> jobProps = loader.getJobProps();
		List<Props> propProps = loader.getProps();
		
		synchronized(project) {
			int newVersion = projectLoader.getLatestProjectVersion(project) + 1;
			Map<String, Flow> flows = loader.getFlowMap();
			for (Flow flow: flows.values()) {
				flow.setProjectId(project.getId());
				flow.setVersion(newVersion);
			}
			
			logger.info("Uploading file to db " + archive.getName());
			projectLoader.uploadProjectFile(project, newVersion, fileType, archive.getName(), archive, uploader.getUserId());
			logger.info("Uploading flow to db " + archive.getName());
			projectLoader.uploadFlows(project, newVersion, flows.values());
			logger.info("Changing project versions " + archive.getName());
			projectLoader.changeProjectVersion(project, newVersion, uploader.getUserId());
			project.setFlows(flows);
			logger.info("Uploading Job properties");
			projectLoader.uploadProjectProperties(project, new ArrayList<Props>(jobProps.values()));
			logger.info("Uploading Props properties");
			projectLoader.uploadProjectProperties(project, propProps);
		}
	
		//TODO: find something else to load triggers
//		if(loadTriggerFromFile) {
//			logger.info("Loading triggers.");
//			Props triggerProps = new Props();
//			triggerProps.put("projectId", project.getId());
//			triggerProps.put("projectName", project.getName());
//			triggerProps.put("submitUser", uploader.getUserId());
//			try {
//				triggerManager.loadTriggerFromDir(file, triggerProps);
//			} catch (Exception e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//				logger.error("Failed to load triggers.", e);
//			}
//		}
		
		logger.info("Uploaded project files. Cleaning up temp files.");
		projectLoader.postEvent(project, EventType.UPLOADED, uploader.getUserId(), "Uploaded project files zip " + archive.getName());
		try {
			FileUtils.deleteDirectory(file);
		} catch (IOException e) {
			file.deleteOnExit();
			e.printStackTrace();
		}

		logger.info("Cleaning up old install files older than " + (project.getVersion() - projectVersionRetention));
		projectLoader.cleanOlderProjectVersion(project.getId(), project.getVersion() - projectVersionRetention);
	}

	public void getWorkflow(Project project, HashMap<String, Object> ret, HttpServletResponse resp) throws ProjectManagerException, IOException {
		int version = projectLoader.getLatestProjectVersion(project);
		ProjectFileHandler projectFileHandler = null;
		try {
			projectFileHandler = projectLoader.getUploadedFile(project.getId(), version);
			if (projectFileHandler == null) {
				resp.reset();
				String emptyWorkflow = "{\"nodes\":[],\"edges\":[]}";

				resp.addHeader("Content-type", "text/plain; charset=UTF-8");
				resp.addHeader("Content-Length", "" + emptyWorkflow.length());
				OutputStream output = new BufferedOutputStream(resp.getOutputStream());

				byte[] emptyWorkflowBytes = emptyWorkflow.getBytes();

				for (byte workflowByte : emptyWorkflowBytes) {
					output.write(workflowByte);
				}
				output.flush();
				if (output != null) {
					output.close();
				}
			}
			else if ("zip".equals(projectFileHandler.getFileType())) {
				logger.info("Downloading zip file.");
				ZipFile zip = new ZipFile(projectFileHandler.getLocalFile());
				File unzipped = Utils.createTempDir(tempDir);
				Utils.unzip(zip, unzipped);

				File workflowFile = new File(unzipped, "workflow.json");
				if(!workflowFile.exists()) {
					Set<String> duplicateJobs = new HashSet<String>();
					HashMap<String, Props> jobPropsMap = new HashMap<String, Props>();
					HashMap<String, ProjectNode> nodeMap = new HashMap<String, ProjectNode>();
					Integer index = 1;
					loadProject(unzipped, duplicateJobs, jobPropsMap, nodeMap, index);
					getDependent(nodeMap);
					generateGraph(nodeMap);

					String jsonString = graphToJsonString(nodeMap);

					writeJsonStringToFile(workflowFile,jsonString);
				}

				resp.reset();

				resp.addHeader("Content-Disposition", "attachment;filename=workflow.json");
				resp.addHeader("Content-Length", "" + workflowFile.length());
				resp.setContentType("application/octet-stream");

				OutputStream output = new BufferedOutputStream(resp.getOutputStream());
				InputStream input = new BufferedInputStream(new FileInputStream(workflowFile));

				IOUtils.copy(input, output);
				output.flush();

				if(input != null) {
					input.close();
				}
				if (output != null) {
					output.close();
				}
				if (unzipped.exists()) {
					FileUtils.deleteDirectory(unzipped);
				}
			}
			else {
				throw new IOException("The file type hasn't been decided yet.");
			}
		}
		finally {
			if (projectFileHandler != null) {
				projectFileHandler.deleteLocalFile();
			}
		}
	}

	public void updateFlow(Project project, Flow flow) throws ProjectManagerException {
		projectLoader.updateFlow(project, flow.getVersion(), flow);
	}

	private File unzipFile(File archiveFile) throws IOException {
		ZipFile zipfile = new ZipFile(archiveFile);
		File unzipped = Utils.createTempDir(tempDir);
		Utils.unzip(zipfile, unzipped);

		return unzipped;
	}

	private void loadProject(File base, Set<String> duplicateJobs, HashMap<String, Props> jobPropsMap, HashMap<String, ProjectNode> nodeMap, Integer index) {
		File[] jobFiles = base.listFiles(new SuffixFilter(".job"));
		for (File file: jobFiles) {
			String jobName = getNameWithoutExtension(file);
				if (!duplicateJobs.contains(jobName)) {
					if (jobPropsMap.containsKey(jobName)) {
		 				duplicateJobs.add(jobName);
						jobPropsMap.remove(jobName);
						nodeMap.remove(jobName);
					}
					else {
						try {
							Props prop = new Props(null, file);
							ProjectNode node = new ProjectNode(jobName, prop);
							String type = prop.getString("type", "");
							node.setType(type);
							node.setIndex(index);
							index++;
							nodeMap.put(jobName, node);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
		}
		File[] subDirs = base.listFiles(new DirFilter());
		for (File file: subDirs) {
			loadProject(file, duplicateJobs, jobPropsMap, nodeMap, index);
		}
	}

	private String getNameWithoutExtension(File file) {
		String filename = file.getName();
		int index = filename.lastIndexOf('.');

		return index < 0 ? filename : filename.substring(0, index);
	}

	private void getDependent(HashMap<String, ProjectNode> nodeMap) {
		for(ProjectNode node : nodeMap.values()) {
			Props props = node.getProps();
			if(props == null)
				continue;
			List<String> dependencyList = props.getStringList(CommonJobProperties.DEPENDENCIES, (List<String>)null);
			if(dependencyList != null) {
				for(String dependency : dependencyList) {
					dependency = dependency == null ? null : dependency.trim();
					if(dependency == null || dependency.isEmpty())
						continue;
					ProjectNode dependencyNode = nodeMap.get(dependency);
					if(dependencyNode != null) {
						dependencyNode.addDependent(node);
						node.addDependencies(dependencyNode);
					}
				}
			}
		}
	}

	private void generateGraph(HashMap<String, ProjectNode> nodeMap) {
		int currentX = 0, currentY = 30;
		ArrayList<ProjectNode> currentNodes = new ArrayList<ProjectNode>();
		for(ProjectNode node : nodeMap.values()) {
			if(node.readyToSetLocation()) {
				currentNodes.add(node);
			}
		}
		ArrayList<ProjectNode> nextNodes = new ArrayList<ProjectNode>();
		while(!currentNodes.isEmpty()) {
			nextNodes.clear();
			for(ProjectNode currentNode : currentNodes) {
				currentX = Math.max(850, 120 * (currentNodes.size() + 1)) / (currentNodes.size() + 1) * (currentNodes.indexOf(currentNode) + 1);
				currentNode.setLocationX(currentX);
				currentNode.setLocationY(currentY);
				Set<ProjectNode> projectNodes = currentNode.getDependent();
				for(ProjectNode nextNode : projectNodes) {
					nextNode.removeDependencies(currentNode);
					if(nextNode.readyToSetLocation())
						nextNodes.add(nextNode);
				}
			}
			currentY = currentY + 70;
			currentNodes.clear();
			currentNodes.addAll(nextNodes);
		}
	}

	private String graphToJsonString(HashMap<String, ProjectNode> nodeMap) {
		StringBuilder jsonStringBuilder = new StringBuilder("");
		for(ProjectNode node : nodeMap.values()) {
			if(jsonStringBuilder.length() == 0)
				jsonStringBuilder.append("{\"nodes\":[");
			else jsonStringBuilder.append(",");
			jsonStringBuilder.append("{\"id\":").append(node.getIndex());
			jsonStringBuilder.append(",\"x\":").append(node.getLocationX());
			jsonStringBuilder.append(",\"y\":").append(node.getLocationY());
			String nodeName = node.getName().replace("\"", "\\\"");
			jsonStringBuilder.append(",\"name\":\"").append(nodeName).append("\"");
			String nodeType = node.getType().replace("\"", "\\\"");
			jsonStringBuilder.append(",\"type\":\"").append(nodeType).append("\"");
			Props props = node.getProps();
			Set<String> keySet = props.getKeySet();
			StringBuilder propStringBuilder = new StringBuilder("");
			for(String key : keySet) {
				if(key.compareTo("dependencies") != 0 && key.compareTo("type") != 0) {
					if(propStringBuilder.length() == 0)
						propStringBuilder.append("{");
					else propStringBuilder.append(",");
					propStringBuilder.append("\"").append(key).append("\":\"");
					String valueString = props.get(key).replace("\"", "\\\"");
					propStringBuilder.append(valueString).append("\"");
				}
			}
			if(propStringBuilder.length() == 0)
				propStringBuilder.append("{");
			propStringBuilder.append("}");
			jsonStringBuilder.append(",\"props\":").append(propStringBuilder);
			jsonStringBuilder.append(",\"dependencies\":\"");
			if(props.get("dependencies") != null) {
				String propsString = props.get("dependencies").replace("\"", "\\\"");
				jsonStringBuilder.append(propsString);
			}
			jsonStringBuilder.append("\"}");
		}
		if(jsonStringBuilder.length() == 0)
			jsonStringBuilder.append("{\"nodes\":[],");
		else jsonStringBuilder.append("],");

		StringBuilder edgesStringBuilder = new StringBuilder("");
		for(ProjectNode sourceNode : nodeMap.values()) {
			for(ProjectNode targetNode : sourceNode.getDependent()) {
				if(edgesStringBuilder.length() == 0) {
					edgesStringBuilder.append("\"edges\":[");
				}
				else edgesStringBuilder.append(",");
				edgesStringBuilder.append("{\"source\":").append(sourceNode.getIndex());
				edgesStringBuilder.append(",\"target\":").append(targetNode.getIndex());
				edgesStringBuilder.append("}");
			}
		}
		if(edgesStringBuilder.length() == 0)
			edgesStringBuilder.append("\"edges\":[]");
		else edgesStringBuilder.append("]");
		jsonStringBuilder.append(edgesStringBuilder).append("}");
		return jsonStringBuilder.toString();
	}

	private void writeJsonStringToFile(File file, String jsonString) throws IOException {
		PrintWriter printWriter = null;
		try {
			printWriter = new PrintWriter(file);
			printWriter.print(jsonString);
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(printWriter != null)
				printWriter.close();
		}

	}


	public void postProjectEvent(Project project, EventType type, String user,String message) {
		projectLoader.postEvent(project, type, user, message);
	}

	private static class DirFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory();
		}
	}

	private static class SuffixFilter implements FileFilter {
		private String suffix;

		public SuffixFilter(String suffix) {
			this.suffix = suffix;
		}

		@Override
		public boolean accept(File pathname) {
			String name = pathname.getName();

			return pathname.isFile() && !pathname.isHidden() && name.length() > suffix.length() && name.endsWith(suffix);
		}
	}


}
