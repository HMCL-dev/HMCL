package org.jackhuang.hmcl.util;

import com.sun.javafx.geom.BaseBounds;
import com.sun.javafx.geom.transform.BaseTransform;
import com.sun.javafx.scene.ImageViewHelper;
import com.sun.javafx.sg.prism.NGImageView;
import com.sun.javafx.sg.prism.NGNode;
import com.sun.prism.Graphics;
import com.sun.prism.Texture;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import org.jackhuang.hmcl.ui.FXUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ImageUtils {
    private ImageUtils() {
    }

    public static void enableSmoothImageRendering() {
        FXUtils.checkFxUserThread();

        try {
            new ImageView(); // Trigger class initialization.

            Field field = ImageViewHelper.class.getDeclaredField("imageViewAccessor");
            field.setAccessible(true);

            ImageViewHelper.ImageViewAccessor delegate = (ImageViewHelper.ImageViewAccessor) field.get(null);
            if (delegate == null) {
                throw new IllegalStateException("ImageViewHelper.imageViewAccessor should be set!");
            }

            field.set(null, new SmoothImageViewHelper(delegate));
        } catch (Throwable e) {
            LOG.warning("Cannot enable smooth image rendering.", e);
        }
    }

    private static final class SmoothImageViewHelper implements ImageViewHelper.ImageViewAccessor {
        private static final MethodHandle GET_IMAGE;

        static {
            try {
                Field field = NGImageView.class.getDeclaredField("image");
                field.setAccessible(true);
                GET_IMAGE = MethodHandles.lookup().unreflectGetter(field);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        private final ImageViewHelper.ImageViewAccessor delegate;

        public SmoothImageViewHelper(ImageViewHelper.ImageViewAccessor delegate) {
            this.delegate = delegate;
        }

        @Override
        public NGNode doCreatePeer(Node node) {
            return new NGImageView() {
                @Override
                protected void renderContent(Graphics g) {
                    try {
                        Texture tex = g.getResourceFactory().getCachedTexture((com.sun.prism.Image) GET_IMAGE.invokeExact((NGImageView) this), Texture.WrapMode.CLAMP_TO_EDGE);
                        tex.setLinearFiltering(true);
                        tex.unlock();
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }

                    super.renderContent(g);
                }
            };
        }

        @Override
        public void doUpdatePeer(Node node) {
            delegate.doUpdatePeer(node);
        }

        @Override
        public BaseBounds doComputeGeomBounds(Node node, BaseBounds bounds, BaseTransform tx) {
            return delegate.doComputeGeomBounds(node, bounds, tx);
        }

        @Override
        public boolean doComputeContains(Node node, double localX, double localY) {
            return delegate.doComputeContains(node, localX, localY);
        }
    }
}
