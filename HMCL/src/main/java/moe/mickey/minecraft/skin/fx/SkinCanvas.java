package moe.mickey.minecraft.skin.fx;

import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Shape3D;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import org.jetbrains.annotations.Nullable;

public class SkinCanvas extends Group {

    public static final Image ALEX = new Image(SkinCanvas.class.getResourceAsStream("/assets/img/alex.png"));
    public static final Image STEVE = new Image(SkinCanvas.class.getResourceAsStream("/assets/img/steve.png"));

    public static final SkinCube ALEX_LARM = new SkinCube(3, 12, 4, 14F / 64F, 16F / 64F, 32F / 64F, 48F / 64F, 0F, true);
    public static final SkinCube ALEX_RARM = new SkinCube(3, 12, 4, 14F / 64F, 16F / 64F, 40F / 64F, 16F / 64F, 0F, true);

    public static final SkinCube STEVEN_LARM = new SkinCube(4, 12, 4, 16F / 64F, 16F / 64F, 32F / 64F, 48F / 64F, 0F, false);
    public static final SkinCube STEVEN_RARM = new SkinCube(4, 12, 4, 16F / 64F, 16F / 64F, 40F / 64F, 16F / 64F, 0F, false);

    protected Image srcSkin, skin, srcCape, cape;
    protected boolean isSlim;

    protected double preW, preH;
    protected boolean msaa;

    protected SubScene subScene;
    protected Group root = new Group();

    public final SkinMultipleCubes headOuter = new SkinMultipleCubes(8, 8, 8, 32F / 64F, 0F, 1.125, 0.2);
    public final SkinMultipleCubes bodyOuter = new SkinMultipleCubes(8, 12, 4, 16F / 64F, 32F / 64F, 1, 0.2);
    public final SkinMultipleCubes larmOuter = new SkinMultipleCubes(4, 12, 4, 48F / 64F, 48F / 64F, 1.0625, 0.2);
    public final SkinMultipleCubes rarmOuter = new SkinMultipleCubes(4, 12, 4, 40F / 64F, 32F / 64F, 1.0625, 0.2);
    public final SkinMultipleCubes llegOuter = new SkinMultipleCubes(4, 12, 4, 0F / 64F, 48F / 64F, 1.0625, 0.2);
    public final SkinMultipleCubes rlegOuter = new SkinMultipleCubes(4, 12, 4, 0F / 64F, 32F / 64F, 1.0625, 0.2);

    public final SkinCube headInside = new SkinCube(8, 8, 8, 32F / 64F, 16F / 64F, 0F, 0F, 0F, false);
    public final SkinCube bodyInside = new SkinCube(8, 12, 4, 24F / 64F, 16F / 64F, 16F / 64F, 16F / 64F, 0.03F, false);
    public final SkinCube larmInside = new SkinCube(4, 12, 4, 16F / 64F, 16F / 64F, 32F / 64F, 48F / 64F, 0F, false);
    public final SkinCube rarmInside = new SkinCube(4, 12, 4, 16F / 64F, 16F / 64F, 40F / 64F, 16F / 64F, 0F, false);
    public final SkinCube llegInside = new SkinCube(4, 12, 4, 16F / 64F, 16F / 64F, 16F / 64F, 48F / 64F, 0F, false);
    public final SkinCube rlegInside = new SkinCube(4, 12, 4, 16F / 64F, 16F / 64F, 0F, 16F / 64F, 0F, false);

    public final SkinCube capeCube = new SkinCube(10, 16, 1, 22F / 64F, 17F / 32F, 0F, 0F, 0F, false);

    public final SkinGroup head = new SkinGroup(
            new Rotate(0, 0, headInside.getHeight() / 2, 0, Rotate.X_AXIS),
            new Rotate(0, Rotate.Y_AXIS),
            new Rotate(0, 0, headInside.getHeight() / 2, 0, Rotate.Z_AXIS),
            headOuter, headInside
    );
    public final SkinGroup body = new SkinGroup(
            new Rotate(0, Rotate.X_AXIS),
            new Rotate(0, Rotate.Y_AXIS),
            new Rotate(0, Rotate.Z_AXIS),
            bodyOuter, bodyInside
    );
    public final SkinGroup larm = new SkinGroup(
            new Rotate(0, 0, -larmInside.getHeight() / 2, 0, Rotate.X_AXIS),
            new Rotate(0, Rotate.Y_AXIS),
            new Rotate(0, +larmInside.getWidth() / 2, -larmInside.getHeight() / 2, 0, Rotate.Z_AXIS),
            larmOuter, larmInside
    );
    public final SkinGroup rarm = new SkinGroup(
            new Rotate(0, 0, -rarmInside.getHeight() / 2, 0, Rotate.X_AXIS),
            new Rotate(0, Rotate.Y_AXIS),
            new Rotate(0, -rarmInside.getWidth() / 2, -rarmInside.getHeight() / 2, 0, Rotate.Z_AXIS),
            rarmOuter, rarmInside
    );
    public final SkinGroup lleg = new SkinGroup(
            new Rotate(0, 0, -llegInside.getHeight() / 2, 0, Rotate.X_AXIS),
            new Rotate(0, Rotate.Y_AXIS),
            new Rotate(0, 0, -llegInside.getHeight() / 2, 0, Rotate.Z_AXIS),
            llegOuter, llegInside
    );
    public final SkinGroup rleg = new SkinGroup(
            new Rotate(0, 0, -rlegInside.getHeight() / 2, 0, Rotate.X_AXIS),
            new Rotate(0, Rotate.Y_AXIS),
            new Rotate(0, 0, -rlegInside.getHeight() / 2, 0, Rotate.Z_AXIS),
            rlegOuter, rlegInside
    );

    public final SkinGroup capeGroup = new SkinGroup(
            new Rotate(0, 0, -capeCube.getHeight() / 2, 0, Rotate.X_AXIS),
            new Rotate(0, Rotate.Y_AXIS),
            new Rotate(0, Rotate.Z_AXIS),
            capeCube
    );

    protected PerspectiveCamera camera = new PerspectiveCamera(true);

    protected Rotate xRotate = new Rotate(0, Rotate.X_AXIS);
    protected Rotate yRotate = new Rotate(180, Rotate.Y_AXIS);
    protected Rotate zRotate = new Rotate(0, Rotate.Z_AXIS);
    protected Translate translate = new Translate(0, 0, -80);
    protected Scale scale = new Scale(1, 1);

    protected SkinAnimationPlayer animationPlayer = new SkinAnimationPlayer();

    public SkinAnimationPlayer getAnimationPlayer() {
        return animationPlayer;
    }

    public Image getSrcSkin() {
        return srcSkin;
    }

    public Image getSkin() {
        return skin;
    }

    public void updateSkin(Image skin, boolean isSlim, final @Nullable Image cape) {
        if (SkinHelper.isNoRequest(skin) && SkinHelper.isSkin(skin)) {
            this.srcSkin = skin;
            this.skin = SkinHelper.x32Tox64(skin);
            this.srcCape = cape;
            this.cape = cape == null ? null : cape.getWidth() < 256 ? SkinHelper.enlarge(cape, 4, 8) : cape;
            int multiple = Math.max((int) (1024 / skin.getWidth()), 1);
            if (multiple > 1)
                this.skin = SkinHelper.enlarge(this.skin, multiple, multiple);
            updateSkinModel(isSlim, cape != null);
            bindMaterial(root);
        }
    }

    protected void updateSkinModel(boolean isSlim, boolean hasCape) {
        this.isSlim = isSlim;
        FunctionHelper.alwaysB(SkinMultipleCubes::setWidth, isSlim ? 3 : 4, larmOuter, rarmOuter);
        FunctionHelper.alwaysB(SkinCube::setWidth, isSlim ? 3D : 4D, larmInside, rarmInside);

        FunctionHelper.alwaysB(Node::setTranslateX, -(bodyInside.getWidth() + larmInside.getWidth()) / 2, larm);
        FunctionHelper.alwaysB(Node::setTranslateX, +(bodyInside.getWidth() + rarmInside.getWidth()) / 2, rarm);
        if (isSlim) {
            larmInside.setModel(ALEX_LARM.getModel());
            rarmInside.setModel(ALEX_RARM.getModel());
        } else {
            larmInside.setModel(STEVEN_LARM.getModel());
            rarmInside.setModel(STEVEN_RARM.getModel());
        }

        larm.getZRotate().setPivotX(-larmInside.getWidth() / 2);
        rarm.getZRotate().setPivotX(+rarmInside.getWidth() / 2);

        capeGroup.setVisible(hasCape);
    }

    public SkinCanvas(double preW, double preH) {
        this(STEVE, preW, preH, true);
    }

    public SkinCanvas(Image skin, double preW, double preH, boolean msaa) {
        this.skin = skin;
        this.preW = preW;
        this.preH = preH;
        this.msaa = msaa;

        init();
    }

    protected Material createMaterial(final Image image) {
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseMap(image);
        return material;
    }

    protected void bindMaterial(Group group) {
        Material material = createMaterial(skin);
        for (Node node : group.getChildren())
            if (node instanceof Shape3D)
                ((Shape3D) node).setMaterial(node == capeCube ? createMaterial(cape) : material);
            else if (node instanceof SkinMultipleCubes)
                ((SkinMultipleCubes) node).updateSkin(skin);
            else if (node instanceof Group)
                bindMaterial((Group) node);
    }

    protected Group createPlayerModel() {
        head.setTranslateY(-(bodyInside.getHeight() + headInside.getHeight()) / 2);

        larm.setTranslateX(-(bodyInside.getWidth() + larmInside.getWidth()) / 2);
        rarm.setTranslateX(+(bodyInside.getWidth() + rarmInside.getWidth()) / 2);

        lleg.setTranslateX(-(bodyInside.getWidth() - llegInside.getWidth()) / 2);
        rleg.setTranslateX(+(bodyInside.getWidth() - rlegInside.getWidth()) / 2);

        lleg.setTranslateY(+(bodyInside.getHeight() + llegInside.getHeight()) / 2);
        rleg.setTranslateY(+(bodyInside.getHeight() + rlegInside.getHeight()) / 2);

        capeGroup.setTranslateY(+(capeCube.getHeight() - bodyOuter.getHeight()) / 2);
        capeGroup.setTranslateZ(-(bodyInside.getDepth() + bodyOuter.getDepth()) / 2);

        capeGroup.getTransforms().addAll(new Rotate(180, Rotate.Y_AXIS), new Rotate(10, Rotate.X_AXIS));

        root.getTransforms().addAll(xRotate);

        root.getChildren().addAll(
                head,
                body,
                larm,
                rarm,
                lleg,
                rleg,
                capeGroup
        );
        updateSkin(skin, false, null);

        return root;
    }

    protected SubScene createSubScene() {
        Group group = new Group();

        AmbientLight light = new AmbientLight(Color.WHITE);
        group.getChildren().add(light);

        group.getChildren().add(createPlayerModel());
        group.getTransforms().add(zRotate);

        camera.getTransforms().addAll(yRotate, translate, scale);

        subScene = new SubScene(group, preW, preH, true,
                msaa ? SceneAntialiasing.BALANCED : SceneAntialiasing.DISABLED);
        subScene.setCamera(camera);

        return subScene;
    }

    protected void init() {
        getChildren().add(createSubScene());
    }

    private double lastX, lastY;

    public void enableRotation(double sensitivity) {
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            lastX = -1;
            lastY = -1;
        });
        addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!(lastX == -1 || lastY == -1)) {
                if (e.isAltDown() || e.isControlDown() || e.isShiftDown()) {
                    if (e.isShiftDown())
                        zRotate.setAngle(zRotate.getAngle() - (e.getSceneY() - lastY) * sensitivity);
                    if (e.isAltDown())
                        yRotate.setAngle(yRotate.getAngle() + (e.getSceneX() - lastX) * sensitivity);
                    if (e.isControlDown())
                        xRotate.setAngle(xRotate.getAngle() + (e.getSceneY() - lastY) * sensitivity);
                } else {
                    double yaw = yRotate.getAngle() + (e.getSceneX() - lastX) * sensitivity;
                    yaw %= 360;
                    if (yaw < 0)
                        yaw += 360;

                    int flagX = yaw < 90 || yaw > 270 ? 1 : -1;
                    int flagZ = yaw < 180 ? -1 : 1;
                    double kx = Math.abs(90 - yaw % 180) / 90 * flagX, kz = Math.abs(90 - (yaw + 90) % 180) / 90 * flagZ;

                    xRotate.setAngle(xRotate.getAngle() + (e.getSceneY() - lastY) * sensitivity * kx);
                    yRotate.setAngle(yaw);
                    zRotate.setAngle(zRotate.getAngle() + (e.getSceneY() - lastY) * sensitivity * kz);
                }
            }
            lastX = e.getSceneX();
            lastY = e.getSceneY();
        });
        addEventHandler(ScrollEvent.SCROLL, e -> {
            double delta = (e.getDeltaY() > 0 ? 1 : e.getDeltaY() == 0 ? 0 : -1) / 10D * sensitivity;
            scale.setX(Math.min(Math.max(scale.getX() - delta, 0.1), 10));
            scale.setY(Math.min(Math.max(scale.getY() - delta, 0.1), 10));
        });
    }

}
