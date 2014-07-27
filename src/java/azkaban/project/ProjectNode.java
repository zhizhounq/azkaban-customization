package azkaban.project;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import azkaban.utils.Props;

public class ProjectNode {

	private final String name;
	private Integer index = 0;
	private String type = null;
	private Props props = null;
	private List<String> dependenciesList = new ArrayList<String>();
	private Set<ProjectNode> dependent = new HashSet<ProjectNode>();
	private Set<ProjectNode> dependencies = new HashSet<ProjectNode>();
	private int locationX = 0;
	private int locationY = 0;

	public ProjectNode(String name, Props props) {
		this.name = name;
		this.props = props;
	}

	public ProjectNode(String name) {
		this.name = name;
	}

	public void setProps(Props props) {
		this.props = props;
	}

	public Props getProps() {
		return props;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getType() {
		return type;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}

	public Integer getIndex() {
		return index;
	}

	public String getName() {
		return name;
	}

	public void setLocationX(int locationX) {
		this.locationX = locationX;
	}

	public int getLocationX() {
		return locationX;
	}

	public void setLocationY(int locationY) {
		this.locationY = locationY;
	}

	public int getLocationY() {
		return locationY;
	}

	public void setDependencies(List<String> dependencieList) {
		this.dependenciesList = dependencieList;
	}

	public List<String> getDependenciesList() {
		return dependenciesList;
	}

	public void addDependent(ProjectNode node) {
		dependent.add(node);
	}

	public Set<ProjectNode> getDependent() {
		return dependent;
	}

	public void addDependencies(ProjectNode node) {
		dependencies.add(node);
	}

	public void removeDependencies(ProjectNode node) {
		if(dependencies.contains(node))
			dependencies.remove(node);
	}

	public Set<ProjectNode> getDependencies() {
		return dependencies;
	}

	public boolean readyToSetLocation(){
		return dependencies.isEmpty();
	}


}
