package org.ei.opensrp.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.ImageView;

import com.android.volley.*;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HttpClientStack;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

import org.apache.http.HttpResponse;
import org.ei.opensrp.AllConstants;
import org.ei.opensrp.R;
import org.ei.opensrp.domain.ProfileImage;
import org.ei.opensrp.repository.ImageRepository;
import org.ei.opensrp.view.activity.DrishtiApplication;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * A class that wraps up remote image loading requests using the Volley library combined with a memory cache. A single instance of this class should be created
 * once when your Activity or Fragment is created, then use {@link #get(String, ImageView)} if the image doesn't exist locally or one of the variations to queue the image to be
 * fetched and loaded from the network. Loading images in a {@link android.widget.ListView} or {@link android.widget.GridView} is also supported but you must
 * store the {@link com.android.volley.Request} in your ViewHolder type class and pass it into loadImage to ensure the request is canceled as views are
 * recycled.
 */
public class OpenSRPImageLoader extends ImageLoader {

    private static final String TAG = "OpenSRPImageLoader";

    private static final ColorDrawable transparentDrawable = new ColorDrawable(Color.BLACK);

    private Resources mResources;
    private ArrayList<Drawable> mPlaceHolderDrawables;
    private boolean mFadeInImage = true;
    private int mMaxImageHeight = 0;
    private int mMaxImageWidth = 0;
    private static final int HALF_FADE_IN_TIME = AllConstants.ANIMATION_FADE_IN_TIME / 2;


    /**
     * Creates an ImageLoader with Bitmap memory cache. No default placeholder image will be shown while the image is being fetched and loaded.
     */
    public OpenSRPImageLoader(Activity activity) {
        super(newRequestQueue(activity), DrishtiApplication.getMemoryCacheInstance());
        mResources = activity.getResources();
    }

    /**
     * Creates an ImageLoader with Bitmap memory cache. No default placeholder image will be shown while the image is being fetched and loaded.
     */
    public OpenSRPImageLoader(Service service, int defaultPlaceHolderResId) {
        super(newRequestQueue(service), DrishtiApplication.getMemoryCacheInstance());
        mResources = service.getResources();


        mPlaceHolderDrawables = new ArrayList<Drawable>(1);
        mPlaceHolderDrawables.add(defaultPlaceHolderResId == -1 ? null : mResources.getDrawable(defaultPlaceHolderResId));
    }

    public OpenSRPImageLoader(Context context, int defaultPlaceHolderResId) {
        super(newRequestQueue(context), DrishtiApplication.getMemoryCacheInstance());
        mResources = DrishtiApplication.getInstance().getResources();

        mPlaceHolderDrawables = new ArrayList<Drawable>(1);
        mPlaceHolderDrawables.add(defaultPlaceHolderResId == -1 ? null : mResources.getDrawable(defaultPlaceHolderResId));
    }

    /**
     * Creates an ImageLoader with Bitmap memory cache and a default placeholder image while the image is being fetched and loaded.
     */
    public OpenSRPImageLoader(Activity activity, int defaultPlaceHolderResId) {
        this(activity);

        mPlaceHolderDrawables = new ArrayList<Drawable>(1);
        mPlaceHolderDrawables.add(defaultPlaceHolderResId == -1 ? null : mResources.getDrawable(defaultPlaceHolderResId));
    }

    /**
     * Creates an ImageLoader with Bitmap memory cache and a list of default placeholder drawables.
     */
    public OpenSRPImageLoader(Activity activity, ArrayList<Drawable> placeHolderDrawables) {
        this(activity);
        mPlaceHolderDrawables = placeHolderDrawables;
    }




    public OpenSRPImageLoader setFadeInImage(boolean fadeInImage) {
        mFadeInImage = fadeInImage;
        return this;
    }

    public OpenSRPImageLoader setMaxImageSize(int maxImageWidth, int maxImageHeight) {
        mMaxImageWidth = maxImageWidth;
        mMaxImageHeight = maxImageHeight;
        return this;
    }

    public OpenSRPImageLoader setMaxImageSize(int maxImageSize) {
        return setMaxImageSize(maxImageSize, maxImageSize);
    }

    /**
     * Retrieves a locally stored image using the id of the image from the images db table. If the file is not present, this function will also attempt to
     * retrieve it using url of the source image.
     * The assumption here is that this method will be used to fetch profile images whereby the name of the file is equals to the client's base entity id
     *
     * @param entityId- The id of the image to be retrieved
     * @return ImageContainer that will contain either the specified default bitmap or the loaded bitmap. If the default was returned, the
     * {@link OpenSRPImageLoader} will be invoked when the request is fulfilled.
     */
    public void getImageByClientId(final String entityId, final OpenSRPImageListener opensrpImageListener) {

        try {
            if (entityId == null || entityId.isEmpty()) {

                /***
                 * If imageId is NULL, just return the image with resource id "defaultImageResId"
                 */
                ImageContainer imgContainer = new ImageContainer(null, null, null, opensrpImageListener);

                opensrpImageListener.onResponse(imgContainer, true);
                return;

            } else {
                //get image record from the db
                ImageRepository imageRepo = (ImageRepository)org.ei.opensrp.Context.imageRepository();
                ProfileImage imageRecord = imageRepo.findByEntityId(entityId);
                if(imageRecord!=null) {
                    get(imageRecord, opensrpImageListener);
                }else{
                    String url= FileUtilities.getImageUrl(entityId);
                    get(url,opensrpImageListener);

                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public void get(final ProfileImage image, final OpenSRPImageListener opensrpImageListener) {

        try {
            /***
             * Non existent image record, display image with defaultImageResId
             */
            if (image == null) {
                ImageContainer imgContainer = new ImageContainer(null, null, null, opensrpImageListener);
                opensrpImageListener.onResponse(imgContainer, true);
                return;
            }
            opensrpImageListener.setAbsoluteFileName(image.getFilepath());

            LoadBitmapFromDiskTask loadBitmap = new LoadBitmapFromDiskTask(opensrpImageListener, image, this);
            loadBitmap.execute(image.getFilepath());

        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private class LoadBitmapFromDiskTask extends AsyncTask<String, Void, Bitmap> {

        private OpenSRPImageListener opensrpImageListener;
        private ProfileImage imageRecord;
        private ImageView imageView;
        private OpenSRPImageLoader cachedImageLoader;

        public LoadBitmapFromDiskTask(OpenSRPImageListener opensrpImageListener, ProfileImage imageRecord, OpenSRPImageLoader cachedImageLoader) {
            this.opensrpImageListener = opensrpImageListener;
            this.imageRecord = imageRecord;
            this.imageView = opensrpImageListener.getImageView();
            this.cachedImageLoader = cachedImageLoader;
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            return FileUtilities.retrieveStaticImageFromDisk(params[0]);
        }

        @Override
        protected void onPostExecute(Bitmap result) {

            try {

                /***
                 * Display image loaded from disk if reference is not NULL
                 */
                if (result != null) {
                    Log.i(TAG, "Found image on local storage, no download needed");
                    ImageContainer imgContainer = new ImageContainer(result, null, null, opensrpImageListener);
                    if (opensrpImageListener != null) {
                        if (opensrpImageListener.getHasImageViewTag()) {
                            String imageId = opensrpImageListener.getImageView().getTag().toString();
                            if (imageRecord.getEntityID().equalsIgnoreCase(imageId))
                                opensrpImageListener.onResponse(imgContainer, true);
                        } else
                            opensrpImageListener.onResponse(imgContainer, true);
                    }
                    return;
                }
                /***
                 * ProfileImage not found on disk, we need to get it from the network, here we piggyback on Volley library functionality to retrieve the image from the
                 * network after a couple of sanity checks
                 */
                else {
                    // Find any old image load request pending on this ImageView (in case this view was recycled)
                    ImageContainer imageContainer = imageView.getTag() != null && imageView.getTag() instanceof ImageContainer ? (ImageContainer) imageView
                            .getTag() : null;

                    // Find image url from prior request
                    String recycledImageUrl = imageContainer != null ? imageContainer.getRequestUrl() : null;

                    // get this from the database based on imageId
                    String requestUrl = imageRecord.getImageUrl();

                    // If the new requestUrl is null or the new requestUrl is different to the previous recycled requestUrl
                    if (requestUrl == null || !requestUrl.equals(recycledImageUrl)) {
                        if (imageContainer != null) {
                            // Cancel previous image request
                            imageContainer.cancelRequest();
                            imageView.setTag(null);
                        }
                        if (requestUrl != null) {
                            // Queue new request to fetch image
                            imageContainer = cachedImageLoader.get(requestUrl, opensrpImageListener, 0, 0);
                            // Store request in ImageView tag
                            imageView.setTag(imageContainer);
                        } else {
                            // Use default image
                            imageContainer = new ImageContainer(null, null, null, opensrpImageListener);
                            opensrpImageListener.onResponse(imageContainer, true);
                            // Nullify ImageView tag
                            imageView.setTag(null);
                        }
                    }

                    return;
                }

            } catch (Exception exc) {

                Log.e(TAG, exc.getMessage(), exc);

            }

        }
    }

    public ImageContainer get(String requestUrl, ImageView imageView) {
        return get(requestUrl, imageView, 0);
    }

    public ImageContainer get(String requestUrl, ImageView imageView, int placeHolderIndex) {
        return get(requestUrl, imageView, mPlaceHolderDrawables.get(placeHolderIndex), mMaxImageWidth, mMaxImageHeight);
    }

    public ImageContainer get(String requestUrl, ImageView imageView, Drawable placeHolder) {
        return get(requestUrl, imageView, placeHolder, mMaxImageWidth, mMaxImageHeight);
    }

    public ImageContainer get(String requestUrl, ImageView imageView, Drawable placeHolder, int maxWidth, int maxHeight) {

        // Find any old image load request pending on this ImageView (in case this view was
        // recycled)
        ImageContainer imageContainer = imageView.getTag() != null && imageView.getTag() instanceof ImageContainer ? (ImageContainer) imageView.getTag() : null;

        // Find image url from prior request
        String recycledImageUrl = imageContainer != null ? imageContainer.getRequestUrl() : null;

        // If the new requestUrl is null or the new requestUrl is different to the previous recycled requestUrl
        if (requestUrl == null || !requestUrl.equals(recycledImageUrl)) {
            if (imageContainer != null) {
                // Cancel previous image request
                imageContainer.cancelRequest();
                imageView.setTag(null);
            }
            if (requestUrl != null) {
                // Queue new request to fetch image
                imageContainer = get(requestUrl, getImageListener(mResources, imageView, placeHolder, mFadeInImage), maxWidth, maxHeight);
                // Store request in ImageView tag
                imageView.setTag(imageContainer);
            } else {
                imageView.setImageDrawable(placeHolder);
                imageView.setTag(null);
            }
        }

        return imageContainer;
    }

    private static ImageListener getImageListener(final Resources resources, final ImageView imageView, final Drawable placeHolder, final boolean fadeInImage) {
        return new ImageListener() {
            @Override
            public void onResponse(ImageContainer response, boolean isImmediate) {
                imageView.setTag(null);
                if (response.getBitmap() != null) {
                    setImageBitmap(imageView, response.getBitmap(), resources, fadeInImage && !isImmediate);
                } else {
                    imageView.setImageDrawable(placeHolder);
                }
            }

            @Override
            public void onErrorResponse(VolleyError volleyError) {
            }
        };
    }

    private static RequestQueue newRequestQueue(Context context) {

        // On HoneyComb+ use HurlStack which is based on HttpURLConnection. Otherwise fall back on
        // AndroidHttpClient (based on Apache DefaultHttpClient) which should no longer be used
        // on newer platform versions where HttpURLConnection is simply better.
        RequestQueue requestQueue;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            HurlStack stack = new HurlStack() {
                @Override
                public HttpResponse performRequest(Request<?> request, Map<String, String> headers)
                        throws IOException, AuthFailureError {

                    headers.putAll(org.ei.opensrp.Context.getInstance().allSettings().getAuthParams());

                    return super.performRequest(request, headers);
                }
            };

            requestQueue = Volley.newRequestQueue(context, stack);

        } else {
//            HttpClientStack stack = new HttpClientStack(AndroidHttpClient.newInstance(FileUtilities
//                    .getUserAgent(context))) {
//                @Override
//                public HttpResponse performRequest(Request<?> request, Map<String, String> headers)
//                        throws IOException, AuthFailureError {
//
//                     headers.putAll(org.ei.opensrp.Context.getInstance().allSettings().getAuthParams());
//
//                    return super.performRequest(request, headers);
//                }
//            };

            // Instantiate the cache
            com.android.volley.Cache cache = new DiskBasedCache(context.getCacheDir(), 1024 * 1024); // 1MB cap

            // Set up the network to use HttpURLConnection as the HTTP client.
            Network network = new BasicNetwork(new HurlStack());

            // Instantiate the RequestQueue with the cache and network.
            requestQueue = new RequestQueue(cache, network);

//            requestQueue = Volley.newRequestQueue(context, stack);
        }
        return requestQueue;
    }

    /**
     * Sets a {@link Bitmap} to an {@link ImageView} using a fade-in animation. If there is a
     * {@link Drawable} already set on the ImageView then use that as the image to fade from. Otherwise fade in from a transparent
     * Drawable.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private static void setImageBitmap(final ImageView imageView, final Bitmap bitmap, Resources resources, boolean fadeIn) {

        // If we're fading in and on HC MR1+
        if (fadeIn && Build.VERSION.SDK_INT >= 12) {
            // Use ViewPropertyAnimator to run a simple fade in + fade out animation to update the
            // ImageView
            imageView.animate().scaleY(0.95f).scaleX(0.95f).alpha(0f).setDuration(imageView.getDrawable() == null ? 0 : HALF_FADE_IN_TIME)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            imageView.setImageBitmap(bitmap);
                            imageView.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(HALF_FADE_IN_TIME).setListener(null);
                        }
                    });
        } else if (fadeIn) {
            // Otherwise use a TransitionDrawable to fade in
            Drawable initialDrawable;
            if (imageView.getDrawable() != null) {
                initialDrawable = imageView.getDrawable();
            } else {
                initialDrawable = transparentDrawable;
            }
            BitmapDrawable bitmapDrawable = new BitmapDrawable(resources, bitmap);
            // Use TransitionDrawable to fade in
            final TransitionDrawable td = new TransitionDrawable(new Drawable[]{initialDrawable, bitmapDrawable});
            imageView.setImageDrawable(td);
            td.startTransition(AllConstants.ANIMATION_FADE_IN_TIME);
        } else {
            // No fade in, just set bitmap directly
            imageView.setImageBitmap(bitmap);
        }
    }

    /**
     * Get a usable cache directory (external if available, internal otherwise).
     *
     * @param context    The context to use
     * @param uniqueName A unique directory name to append to the cache dir
     * @return The cache dir
     */
    public static File getDiskCacheDir(Context context, String uniqueName) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir


        final String cachePath = context.getCacheDir().getPath();

        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * Get the external app cache directory.
     *
     * @param context The context to use
     * @return The external cache dir
     */
    private static File getExternalCacheDir(Context context) {

        return context.getExternalCacheDir();
    }

    /**
     * Interface an activity can implement to provide an ImageLoader to its children fragments.
     */
    public interface ImageLoaderProvider {
        public OpenSRPImageLoader getImageLoaderInstance();
    }

    /**
     * Custom implementation of ImageListener which encapsulates basic functionality of showing a default image until the network response is received, at which
     * point it will switch to either the actual image or the error image. Additional functionality also includes saving the received image to disk for future
     * use
     *
     * @param defaultImageResId Default image resource ID to use, or 0 if it doesn't exist.
     * @param errorImageResId   Error image resource ID to use, or 0 if it doesn't exist.
     */
    public static OpenSRPImageListener getStaticImageListener(final ImageView view, final int defaultImageResId, final int errorImageResId) {

        return new OpenSRPImageListener(view, defaultImageResId, errorImageResId) {

            @Override
            public void onErrorResponse(VolleyError error) {
                if (errorImageResId != 0) {
                    view.setImageResource(errorImageResId);
                }
            }

            @Override
            public void onResponse(final ImageContainer response, final boolean isImmediate) {
                if (response.getBitmap() != null) {
                    view.setImageBitmap(response.getBitmap());

                    // perform I/O on non UI thread
                    if (!isImmediate) {
                    //pass the entity id to act as the file name . Remember to always set this value as a tag in the image view
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                OpenSRPImageLoader.saveStaticImageToDisk(view.getTag(R.id.entity_id).toString(), response.getBitmap());
                            }
                        }).start();
                    }
                } else if (defaultImageResId != 0) {
                    view.setImageResource(defaultImageResId);
                }
            }
        };
    }

    private static CompressFormat getCompressFormat(String absoluteFileName) {
        String[] parts = absoluteFileName.split("\\.");
        String imgExtension = parts[parts.length - 1];
        if (imgExtension.equalsIgnoreCase("png")) {
            return CompressFormat.PNG;
        } else if (imgExtension.equalsIgnoreCase("JPG") || imgExtension.equalsIgnoreCase("JPEG")) {
            return CompressFormat.JPEG;
        } else {
            return null;
        }
    }

    /**
     * Save image to the local storage.If an image is downloaded from the server it's compressed to jpeg format and the entityid
     * becomes the file name
     * @param entityId
     * @param image
     */

    public static void saveStaticImageToDisk(String entityId, Bitmap image) {
        if (image != null) {
            OutputStream os = null;
            try {

                if (entityId != null && !entityId.isEmpty()) {
                    final String absoluteFileName = DrishtiApplication.getAppDir() + File.separator + entityId+".JPEG";

                    File outputFile = new File(absoluteFileName);
                    os = new FileOutputStream(outputFile);
                    CompressFormat compressFormat = OpenSRPImageLoader.getCompressFormat(absoluteFileName);
                    if (compressFormat != null) {
                        image.compress(compressFormat, 100, os);
                    } else {
                        throw new IllegalArgumentException("Failed to save static image, could not retrieve image compression format from name "
                                + absoluteFileName);
                    }
                    // insert into the db
                    ProfileImage profileImage= new ProfileImage();
                    profileImage.setImageid(UUID.randomUUID().toString());
                    profileImage.setEntityID(entityId);
                    profileImage.setFilepath(absoluteFileName);
                    profileImage.setFilecategory("profilepic");
                    profileImage.setSyncStatus(ImageRepository.TYPE_Synced);
                    ImageRepository imageRepo = (ImageRepository) org.ei.opensrp.Context.imageRepository();
                    imageRepo.add(profileImage);
                }

            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to save static image to disk");
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close static images output stream after attempting to write image");
                    }
                }
            }
        }
    }

}
