package moe.mickey.minecraft.skin.fx.test;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import moe.mickey.minecraft.skin.fx.FunctionHelper;
import moe.mickey.minecraft.skin.fx.SkinCanvas;
import moe.mickey.minecraft.skin.fx.SkinCanvasSupport;
import moe.mickey.minecraft.skin.fx.animation.SkinAniRunning;
import moe.mickey.minecraft.skin.fx.animation.SkinAniWavingArms;

import java.util.function.Consumer;

public class Test extends Application {

    public static final String TITLE = "FX - Minecraft skin preview";

    public static SkinCanvas createSkinCanvas() {
        SkinCanvas canvas = new SkinCanvas(SkinCanvas.STEVE, 400, 400, true);
        canvas.getAnimationplayer().addSkinAnimation(new SkinAniWavingArms(100, 2000, 7.5, canvas), new SkinAniRunning(100, 100, 30, canvas));
        FunctionHelper.alwaysB(Consumer<SkinCanvas>::accept, canvas, new SkinCanvasSupport.Mouse(.5), new SkinCanvasSupport.Drag(TITLE));
        return canvas;
    }

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle(TITLE);
        Scene scene = new Scene(createSkinCanvas());
        stage.setScene(scene);
        stage.show();

    }

    public static void main(String... args) {
        launch(args);
    }

}
