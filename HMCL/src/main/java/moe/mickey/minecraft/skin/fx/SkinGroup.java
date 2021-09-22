package moe.mickey.minecraft.skin.fx;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;

public class SkinGroup extends Group {
	
	protected Rotate xRotate, yRotate, zRotate;
	
	public SkinGroup(Rotate xRotate, Rotate yRotate, Rotate zRotate, Node... nodes) {
		this.xRotate = xRotate;
		this.yRotate = yRotate;
		this.zRotate = zRotate;
		Group group = new Group();
		group.getChildren().addAll(nodes);
		getChildren().add(addRotate(group, xRotate, yRotate, zRotate));
	}
	
	protected Group addRotate(Group group, Rotate... rotates) {
		for (Rotate rotate : rotates)
			group = addRotate(group, rotate);
		return group;
	}
	
	protected Group addRotate(Group group, Rotate rotate) {
		Group newGroup = new Group();
		group.getTransforms().add(rotate);
		newGroup.getChildren().add(group);
		return newGroup;
	}
	
	public Rotate getXRotate() {
		return xRotate;
	}
	
	public Rotate getYRotate() {
		return yRotate;
	}
	
	public Rotate getZRotate() {
		return zRotate;
	}

}
