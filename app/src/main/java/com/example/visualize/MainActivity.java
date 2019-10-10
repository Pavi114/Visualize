package com.example.visualize;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends AppCompatActivity
    implements
        qr_fragment.OnFragmentInteractionListener,
        overlay_fragment.OnFragmentInteractionListener,
        ui_fragment.OnFragmentInteractionListener
{

    private static final String TAG = "Main";
    private static final double MIN_OPENGL_VERSION = 3.0;

    private static final int QR_MODE = 0;
    private static final int DL_MODE = 1;
    private static final int AR_MODE = 2;
    private static final int PEACE_MODE = 3;

    private int MODE;
    private int CURRENT_FRAGMENT;

    private ArFragment arFragment;
    private ModelRenderable model;

    private qr_fragment qrFragment;
    private ui_fragment uiFragment;

    private QRDetector qrDetector;
    private Point qrLocation;

    private boolean isTracking;
    private boolean isHitting;

    private overlay_fragment overlayFragment;


    @Override
    public void onFragmentInteraction(Uri uri){
        //you can leave it empty
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        setContentView(R.layout.activity_main);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);
        qrFragment = new qr_fragment();
        uiFragment = new ui_fragment();

        qrDetector = new QRDetector(this);

        MODE = QR_MODE;
        CURRENT_FRAGMENT = -1;

        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            arFragment.onUpdate(frameTime);
            onUpdate();
            updateUI();
        });
    }

    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        ByteBuffer yBuffer = ByteBuffer.wrap(bytes); // Y

        buffer = image.getPlanes()[0].getBuffer();
        bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        ByteBuffer uBuffer = ByteBuffer.wrap(bytes); // U

        buffer = image.getPlanes()[0].getBuffer();
        bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        ByteBuffer vBuffer = ByteBuffer.wrap(bytes); // V


        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            int yBufferPos = width - rowStride; // not an actual position
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride - width;
                yBuffer.position(yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            vBuffer.put(1, (byte)0);
            if (uBuffer.get(0) == 0) {
                vBuffer.put(1, (byte)255);
                if (uBuffer.get(0) == 255) {
                    vBuffer.put(1, savePixel);
                    vBuffer.get(nv21, ySize, uvSize);

                    return nv21; // shortcut
                }
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row=0; row<height/2; row++) {
            for (int col=0; col<width/2; col++) {
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    private void onUpdate() {
        Log.w("abc", "" + (model == null));
        if (MODE != QR_MODE && MODE != AR_MODE) return;
        updateTracking();
        if(!isTracking) return;
        try {
            Image cameraImage = arFragment.getArSceneView().getArFrame().acquireCameraImage();
            ByteBuffer nv21Image = ByteBuffer.wrap(YUV_420_888toNV21(cameraImage));
            qrDetector.detectQR(nv21Image, cameraImage.getHeight(), cameraImage.getWidth(), ImageFormat.NV21);
            cameraImage.close();
            if(MODE == QR_MODE) {
                if (qrDetector.isQRDetected()) {
                    Barcode qrCode = qrDetector.getQrValue();
                    Rect qrBoundingBox = qrCode.getBoundingBox();
                    // qrLocation = new Point(qrBoundingBox.centerX(), qrBoundingBox.centerY());
                    MODE = DL_MODE;
                    loadObject(Uri.parse(qrCode.displayValue));
                }
            } else if (MODE == AR_MODE) {
                updateHitTest();
                if(isHitting) {
                    MODE = PEACE_MODE;
                }
            }
        } catch (NotYetAvailableException e) {
            return;
        } catch (NullPointerException e) {
            return;
        }
    }

    private void updateUI() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if(CURRENT_FRAGMENT != MODE) {
            if(MODE == QR_MODE) {
                transaction.replace(R.id.fragment_container, qrFragment);
            } else if (MODE == AR_MODE || MODE == PEACE_MODE) {
                transaction.replace(R.id.fragment_container, uiFragment);
            }
            CURRENT_FRAGMENT = MODE;
        }
        transaction.commit();
    }

    private Point getScreenCenter() {
        View view = findViewById(R.id.content);

        return new Point(view.getWidth() / 2, view.getHeight() / 2);
    }

    private void updateHitTest() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        Point point = getScreenCenter();
        List<HitResult> hits;
        isHitting = false;
        if (frame != null && point != null) {
            hits = frame.hitTest((float) point.x, (float) point.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    placeObject(arFragment, hit.createAnchor(), model);
                    isHitting = true;
                    break;
                }
            }
        }
    }

    private void updateTracking() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        if(frame != null) {
            isTracking = frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        }
    }

    private void loadObject(Uri model_uri) {
        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
        ModelRenderable.builder()
                .setSource(this, RenderableSource.builder().setSource(
                        arFragment.getContext(),
                        model_uri,
                        RenderableSource.SourceType.GLTF2).build())
                .setRegistryId(model_uri)
                .build()
                .thenAccept(
                        renderable -> {
                            model = renderable;
                            MODE = AR_MODE;
                            Log.w("abc", "def");
                        })
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });
    }

    private void placeObject(ArFragment arFragment, Anchor anchor, ModelRenderable model) {
        if(model == null) return;

        // Create the Anchor and add it to the ArScene
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable node and add it to the anchor.
        TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());
        transformableNode.setParent(anchorNode);
        transformableNode.setRenderable(model);
        transformableNode.select();
    }

    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * <p>Finishes the activity if Sceneform can not run
     */
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }
}
