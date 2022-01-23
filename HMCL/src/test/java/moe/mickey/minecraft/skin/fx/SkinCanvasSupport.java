package moe.mickey.minecraft.skin.fx;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.stage.Stage;
import moe.mickey.minecraft.skin.fx.test.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.function.Consumer;

public abstract class SkinCanvasSupport implements Consumer<SkinCanvas> {

    public static final class Mouse extends SkinCanvasSupport {

        private double lastX, lastY, sensitivity;

        public void setSensitivity(double sensitivity) {
            this.sensitivity = sensitivity;
        }

        public double getSensitivity() {
            return sensitivity;
        }

        public Mouse(double sensitivity) {
            this.sensitivity = sensitivity;
        }

        @Override
        public void accept(SkinCanvas t) {
            t.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                lastX = -1;
                lastY = -1;
            });
            t.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
                if (!(lastX == -1 || lastY == -1)) {
                    if (e.isAltDown() || e.isControlDown() || e.isShiftDown()) {
                        if (e.isShiftDown())
                            t.zRotate.setAngle(t.zRotate.getAngle() - (e.getSceneY() - lastY) * sensitivity);
                        if (e.isAltDown())
                            t.yRotate.setAngle(t.yRotate.getAngle() + (e.getSceneX() - lastX) * sensitivity);
                        if (e.isControlDown())
                            t.xRotate.setAngle(t.xRotate.getAngle() + (e.getSceneY() - lastY) * sensitivity);
                    } else {
                        double yaw = t.yRotate.getAngle() + (e.getSceneX() - lastX) * sensitivity;
                        yaw %= 360;
                        if (yaw < 0)
                            yaw += 360;

                        int flagX = yaw < 90 || yaw > 270 ? 1 : -1;
                        int flagZ = yaw < 180 ? -1 : 1;
                        double kx = Math.abs(90 - yaw % 180) / 90 * flagX, kz = Math.abs(90 - (yaw + 90) % 180) / 90 * flagZ;

                        t.xRotate.setAngle(t.xRotate.getAngle() + (e.getSceneY() - lastY) * sensitivity * kx);
                        t.yRotate.setAngle(yaw);
                        t.zRotate.setAngle(t.zRotate.getAngle() + (e.getSceneY() - lastY) * sensitivity * kz);
                    }
                }
                lastX = e.getSceneX();
                lastY = e.getSceneY();
            });
            t.addEventHandler(ScrollEvent.SCROLL, e -> {
                double delta = (e.getDeltaY() > 0 ? 1 : e.getDeltaY() == 0 ? 0 : -1) / 10D * sensitivity;
                t.scale.setX(Math.min(Math.max(t.scale.getX() - delta, 0.1), 10));
                t.scale.setY(Math.min(Math.max(t.scale.getY() - delta, 0.1), 10));
            });
        }

    }

    public static class Drag extends SkinCanvasSupport {

        private String title;

        public Drag(String title) {
            this.title = title;
        }

        @Override
        public void accept(SkinCanvas t) {
            t.addEventHandler(DragEvent.DRAG_OVER, e -> {
                if (e.getDragboard().hasFiles()) {
                    File file = e.getDragboard().getFiles().get(0);
                    if (file.getAbsolutePath().endsWith(".png"))
                        e.acceptTransferModes(TransferMode.COPY);
                }
            });
            t.addEventHandler(DragEvent.DRAG_DROPPED, e -> {
                if (e.isAccepted()) {
                    File skin = e.getDragboard().getFiles().get(0);
                    Platform.runLater(() -> {
                        try {
                            FileInputStream input = new FileInputStream(skin);
                            Stage stage = new Stage();
                            stage.setTitle(title);
                            SkinCanvas canvas = Test.createSkinCanvas();
                            canvas.updateSkin(new Image(input), skin.getName().contains("[alex]"), null);
                            Scene scene = new Scene(canvas);
                            stage.setScene(scene);
                            stage.show();
                        } catch (FileNotFoundException ex) {
                            ex.printStackTrace();
                        }
                    });
                }
            });
        }

    }

}
