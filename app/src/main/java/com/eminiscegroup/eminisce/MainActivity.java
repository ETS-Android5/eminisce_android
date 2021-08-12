package com.eminiscegroup.eminisce;

import com.eminiscegroup.eminisce.caching.SaveBioData;
import com.eminiscegroup.eminisce.server.JsonBioApi;
import com.eminiscegroup.eminisce.server.LibraryUserBioResponse;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.zkteco.android.biometric.core.device.ParameterHelper;
import com.zkteco.android.biometric.core.device.TransportType;
import com.zkteco.android.biometric.core.utils.LogHelper;
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintCaptureListener;
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintSensor;
import com.zkteco.android.biometric.module.fingerprintreader.FingprintFactory;
import com.zkteco.android.biometric.module.fingerprintreader.ZKFingerService;
import com.zkteco.android.biometric.module.fingerprintreader.exception.FingerprintException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class MainActivity extends AppCompatActivity {

    // Change this if you want to enable debug features
    private static final Boolean DEBUG_MODE = false;
    // Change this to your own server URL
    public static String BASE_URL = "https://eminisce.herokuapp.com/";

    private static final int VID = 6997;
    private static final int PID = 288;
    private boolean bstart = false;
    private boolean isRegister = false;
    private int uid = 1;
    private byte[][] regtemparray = new byte[3][2048];  //register template buffer array
    private int enrollidx = 0;
    private byte[] lastRegTemp = new byte[2048];

    private FingerprintSensor fingerprintSensor = null;

    private final String ACTION_USB_PERMISSION = "com.zkteco.silkiddemo.USB_PERMISSION";

    private Methods Methods;
    public static final int PICK_IMAGE = 100;
    private Button btn;
    private String strBase64;

    private static final int CHOOSE_FILE_REQUESTCODE = 8777;
    private static final int ACTIVITY_CHOOSE_FILE = 100;

    private final int REQUEST_CAMERA_PERMISSION = 101;
    private final int REQUEST_DIRECTORY_ACCESS  = 102;
    private boolean isSerializedDataStored = false;

    // Serialized data will be stored ( in app's private storage ) with this filename.
    private final String SERIALIZED_DATA_FILENAME = "image_data";

    // Shared Pref key to check if the data was stored.
    private final String SHARED_PREF_IS_DATA_STORED_KEY = "is_data_stored";

    public static TextView logTextView;

    public static void setMessage(String message)
    {
        logTextView.setText(message);
    }

    private ProgressBar progressBar;
    private PreviewView previewView;
    private FrameAnalyser frameAnalyser;
    private FaceNetModel model;
    private FileReader fileReader;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private SharedPreferences sharedPreferences;

    // Ask for USB permission
    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action))
            {
                synchronized (this)
                {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    {
                        LogHelper.i("have permission!");
                    }
                    else
                    {
                        LogHelper.e("not permission!");
                        Toast toast = Toast.makeText(MainActivity.this, "Please allow USB connection to connect your fingeprrint scanner.", Toast.LENGTH_LONG);
                        toast.show();
                    }
                }
            }
        }
    };

    //Authentication variables
    private final double face_DistanceThreshold = 0.6f; //Euclidean distance threshold for face recognition
    private final int fp_ScoreThreshold = 55; //Score threshold for fingerprint
    private String last_face_identifiedID = null;
    private String face_identifiedID = null; // The ID of the user recognized by face recognition
    private String fp_identifiedID = null; // The ID of the user recognized by fingerprint scanning
    private long faceRecognizedTime = 0; // How long a single face has been recognized (in ms)
    private final long faceRecognizedTimeThreshold = 2000; // How long a single face should have been recognized for before being accepted (in ms)
    private Instant lastFaceRecognizedInstant = null; // Instant for time related authentication checks
    private Instant lastFPRecognizedInstant = null; // Instant for time related authentication checks

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        // Assign UI variables
        progressBar = findViewById(R.id.progressBar);
        previewView = findViewById( R.id.preview_view );
        logTextView = findViewById( R.id.log_textview );
        logTextView.setMovementMethod(new ScrollingMovementMethod());
        // Necessary to keep the Overlay above the PreviewView so that the boxes are visible.
        BoundingBoxOverlay boundingBoxOverlay = (BoundingBoxOverlay) findViewById( R.id.bbox_overlay );
        boundingBoxOverlay.setWillNotDraw( false );
        boundingBoxOverlay.setZOrderOnTop( true );

        //Initiate Fingerprint Scanner Device
        InitDevice();
        //Start fingerprintSensor
        startFingerprintSensor();

        // Start the frame analyser to do face recognition
        frameAnalyser = new FrameAnalyser( this , boundingBoxOverlay);
        frameAnalyser.setCallback(new FrameAnalyser.FaceCallback() {
                                      @Override
                                      public void onRecognizedFace(@Nullable String userid, double distance) {
                                          MainActivity.this.onRecognizedFace(userid, distance);
                                      }

                                      @Override
                                      public void onNoFace() {
                                          MainActivity.this.onNoFace();
                                      }
                                  });
        model = new FaceNetModel(this);
        frameAnalyser.disableLogging();
        // Start file reader to save and read serialized face recognition data
        fileReader = new FileReader( this );


        // We'll only require the CAMERA permission from the user.
        // For scoped storage, particularly for accessing documents, we won't require WRITE_EXTERNAL_STORAGE or
        // READ_EXTERNAL_STORAGE permissions. See https://developer.android.com/training/data-storage
        if ( ActivityCompat.checkSelfPermission( this , Manifest.permission.CAMERA ) != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions( this , new String[]{ Manifest.permission.CAMERA, } , REQUEST_CAMERA_PERMISSION );
        }
        else {
            startCameraPreview();
        }

        // Start the process of loading the serialized face images and the fingerprint templates
        // Or download if not present
        startLoadImages();

        // Start fingerprint scanning continnuously
        try {
            startFingerprintScan();
        } catch (FingerprintException e) {
            e.printStackTrace();
        }

        // Debug button to allow testing without fingerprint scanner
        btn = (Button)findViewById(R.id.debug_button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(face_identifiedID != null && !face_identifiedID.isEmpty())
                    handleAuthenticationPassed();
                else {
                    Toast toast = Toast.makeText(MainActivity.this, "Scan your face first.", Toast.LENGTH_SHORT);
                    toast.show();
                }

            }
        });

        // Update progress bar and check authentication every 50 ms
        final Handler ha=new Handler();
        ha.postDelayed(new Runnable() {

            @Override
            public void run() {
                progressBar.setProgress((int) (((float)faceRecognizedTime / (float)faceRecognizedTimeThreshold) * 100));
                finalAuthenticationCheck();
                ha.postDelayed(this, 50);
            }
        }, 50);

        // Disable debug related UI elements if DEBUG_MDOE is false
        if(!DEBUG_MODE)
        {
            logTextView.setVisibility(View.GONE);
            boundingBoxOverlay.setWillNotDraw(true);
            findViewById(R.id.debug_button).setVisibility(View.GONE);
        }
    }

    //region SETTING UP

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startLoadImages()
    {
        sharedPreferences = getSharedPreferences( getString( R.string.app_name ) , Context.MODE_PRIVATE );
        isSerializedDataStored = sharedPreferences.getBoolean( SHARED_PREF_IS_DATA_STORED_KEY , false );
        if ( !isSerializedDataStored ) {
            Logger.Companion.log( "No serialized data was found. Downloading from database.");
            startDownloadBioData();
        }
        else {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder( this );
            alertDialog.setTitle( "Serialized Data")
                    .setMessage( "Existing bio data was found on this device. Would you like to load it?" )
                    .setCancelable( false )
                    .setNegativeButton( "LOAD", (dialog, which) ->
                    {
                        dialog.dismiss();
                        try {
                            frameAnalyser.setFaceList(loadSerializedImageData());
                            loadFPBio();
                            Logger.Companion.log("Serialized data loaded.");
                            Toast toast = Toast.makeText(this, "Initialization complete.", Toast.LENGTH_SHORT);
                            toast.show();
                        }
                        catch(Exception e)
                        {
                            Toast toast = Toast.makeText(MainActivity.this, "ERROR: Failed to load biometric data. Please check log.", Toast.LENGTH_LONG);
                            toast.show();
                            e.printStackTrace();

                        }
                    })
                    .setPositiveButton( "REDOWNLOAD FROM DATABASE", (dialog, which) ->
                    {
                        dialog.dismiss();
                        startDownloadBioData();
                    }).create();
            alertDialog.show();
        }
    }

    // Get processed bitmap from the saved face image files (accounting for rotation)
    private Bitmap getFixedBitmap( Uri imageFileUri ) {
        Bitmap imageBitmap = BitmapUtils.Companion.getBitmapFromUri( getContentResolver() , imageFileUri );
        ExifInterface exifInterface = null;
        try {
            exifInterface = new ExifInterface(getContentResolver().openInputStream( imageFileUri ));
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Rotate the image to make it upright depending on the original rotation
        switch(exifInterface.getAttributeInt( ExifInterface.TAG_ORIENTATION ,
                ExifInterface.ORIENTATION_UNDEFINED ))
        {
            case ExifInterface.ORIENTATION_ROTATE_180:
                imageBitmap = BitmapUtils.Companion.rotateBitmap(imageBitmap, 180.0F);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                imageBitmap = BitmapUtils.Companion.rotateBitmap(imageBitmap, 90.0F);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                imageBitmap = BitmapUtils.Companion.rotateBitmap(imageBitmap, 270.0F);
                break;
        }
        return imageBitmap;
    }

    // Initiate the callback for the file reader
    private final FileReader.ProcessCallback fileReaderCallback = new FileReader.ProcessCallback() {
        public void onProcessCompleted( ArrayList<kotlin.Pair<String, float[]>> data, int numImagesWithNoFaces) {
            // When the biometric files have been successfully read, we will assign the faces to the face recognition model
            frameAnalyser.setFaceList(data);
            try {
                // Then save the serialized data for reuse later
                saveSerializedImageData(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Logger.Companion.log("Images parsed. Found " + numImagesWithNoFaces + " images with no faces.");
        }
    };

    // Save serialized data
    private void saveSerializedImageData( ArrayList<kotlin.Pair<String, float[]>> data ) throws IOException {
        File serializedDataFile = new File( getFilesDir() , SERIALIZED_DATA_FILENAME );
        ObjectOutputStream ostream = new ObjectOutputStream((OutputStream)(new FileOutputStream(serializedDataFile)));
        ostream.writeObject(data);
        ostream.flush();
        ostream.close();
        sharedPreferences.edit().putBoolean( SHARED_PREF_IS_DATA_STORED_KEY , true ).apply();
    }

    // Load serialized data and return the data as a pair for each person (user ID) and the associate float array (facial data)
    private ArrayList<kotlin.Pair<String, float[]>> loadSerializedImageData() throws IOException, ClassNotFoundException {
        File serializedDataFile = new File( getFilesDir() , SERIALIZED_DATA_FILENAME );
        ObjectInputStream objectInputStream = new ObjectInputStream((InputStream)(new FileInputStream(serializedDataFile)));
        ArrayList<kotlin.Pair<String,float[]>> data = (ArrayList<kotlin.Pair<String, float[]>>) objectInputStream.readObject();
        objectInputStream.close();
        return data;
    }

    //endregion

    //region CAMERA PREVIEW

    // Attach the camera stream to the PreviewView.
    private void startCameraPreview() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
        Log.d("CAMERA", "1");

    }
    // Bind the preview to the UI element
    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing( CameraSelector.LENS_FACING_FRONT )
                .build();
        preview.setSurfaceProvider( previewView.getSurfaceProvider() );
        ImageAnalysis imageFrameAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size( 480, 640 ) )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyser );
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview , imageFrameAnalysis);
        Log.d("CAMERA", "2");
    }

    //endregion

    //region DOWNLOADING AND LOADING BIO DATA

    private void startDownloadBioData()
    {
        // We use Retrofit to call Django Rest Framework API
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        JsonBioApi service = retrofit.create(JsonBioApi.class);
        Call<List<LibraryUserBioResponse>> call = service.getBios();

        Log.d("DB", "Downloading biometric data from database...");

        call.enqueue(new Callback<List<LibraryUserBioResponse>>() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(Call<List<LibraryUserBioResponse>> call, Response<List<LibraryUserBioResponse>> response) {
                if (response.code() == 200) {
                    Log.d("DB", "Download from DB success");
                    try {
                        // It will return a list of library user info where fingerprint and face are encoded in base64
                        List<LibraryUserBioResponse> bios = (List<LibraryUserBioResponse>) response.body();
                        // Save the files to the internal storage so we can read later
                        SaveBioData save_bio_data = new SaveBioData(MainActivity.this, bios);
                        save_bio_data.Save();
                        // Load the files
                        loadFacesBio();
                        loadFPBio();
                        Toast toast = Toast.makeText(MainActivity.this, "Initialization complete.", Toast.LENGTH_SHORT);
                        toast.show();
                    }
                    catch(Exception e)
                    {
                        Toast toast = Toast.makeText(MainActivity.this, "ERROR: Failed to load biometric data. Please check log.", Toast.LENGTH_LONG);
                        toast.show();
                    }
                }
                else{
                    Toast toast = Toast.makeText(MainActivity.this, "ERROR: Failed to download biometric data. Please check log.\nCode: " + response.code(), Toast.LENGTH_LONG);
                    toast.show();
                    Log.e("DB", response.errorBody().toString());
                }
            }
            @Override
            public void onFailure(Call<List<LibraryUserBioResponse>> call, Throwable t) {
                Log.e("DB", "OnFailure called");
                Toast toast = Toast.makeText(MainActivity.this, "Please check Internet connection! " /* + t.getMessage() */, Toast.LENGTH_LONG);
                toast.show();
                t.printStackTrace();
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void loadFacesBio() throws Exception
    {
        Logger.Companion.log("Reading downloaded facial biometric data to prepare authentication...");
        // We will read from the root folder BIO_FACES inside which each person has a separate folder named as their user ID
        // and inside each person's folder is image files of that person's face
        File dir = new File(this.getFilesDir().toString() + File.separator + "BIO_FACES");
        Log.d("LOAD FACE IMAGES", "Does BIO_FACES folder exist: " + dir.exists());
        ArrayList<kotlin.Pair<String,Bitmap>> images = new ArrayList<kotlin.Pair<String,Bitmap>>();
        // We use DocumentFile to easily read the tree structure
        DocumentFile tree = DocumentFile.fromFile(dir);
        if (tree.listFiles().length > 0) {
            for (DocumentFile df : tree.listFiles()) {
                if (df.isDirectory()) {
                    String name = df.getName();
                    for (DocumentFile face : df.listFiles()) {
                        try {
                            images.add(new kotlin.Pair(name, getFixedBitmap(face.getUri())));
                        }
                        catch(Exception e)
                        {
                            Logger.Companion.log("Could not parse an image in " + name + " directory.");
                            throw e;
                        }
                    }
                    Logger.Companion.log("Found " + df.listFiles().length + " images in " + name + " directory");
                }
                else
                {
                    Logger.Companion.log("Invalid BIO_FACES folder structure.");
                    throw new Exception("Invalid BIO_FACES folder structure");
                }
            }
        }
        else {
            Logger.Companion.log( "Empty biometric data folder!" );
        }
        fileReader.run( images , fileReaderCallback );
        Logger.Companion.log( "Detecting faces in " + images.size() + " images ..." );

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void loadFPBio() throws Exception
    {
        Logger.Companion.log("Loading fingerprints ...");
        // We will read from the root folder BIO_FINGERPRINTS inside which each person has a separate folder named as their user ID
        // and inside each person's folder is fingerprint template file of that person's fingerprint
        File dir = new File(this.getFilesDir().toString() + File.separator + "BIO_FINGERPRINTS");
        Log.d("LOAD FP", "Does BIO_FINGERPRINTS folder exist: " + dir.exists());
        // We use DocumentFile to easily read the tree structure
        DocumentFile tree = DocumentFile.fromFile(dir);
        if (tree.listFiles().length > 0) {
            for (DocumentFile df : tree.listFiles()) {
                if (df.isDirectory()) {
                    String name = df.getName();
                    for (DocumentFile fp : df.listFiles()) {
                        File file = new File(getPath(fp.getUri()));
                        // Read the raw bytes of the file
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        // Use the fingerprint SDK to save the fingerprint to memory for recognizing
                        ZKFingerService.save(bytes, name);
                        //Logger.Companion.log("Loaded fingerprint for " + name);
                    }
                }
                else
                    throw new Exception("Invalid BIO_FINGERPRINT folder structure");
            }
        }
        else {
            Logger.Companion.log( "Empty biometric data folder!" );
            //throw new Exception("Empty biometric data folder");
        }
        Logger.Companion.log("Successfully loaded fingerprint data");
    }
    // Helper method to get file absolute path from uri
    public String getPath(Uri uri) {

        String path = null;
        String[] projection = { MediaStore.Files.FileColumns.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

        if(cursor == null){
            path = uri.getPath();
        }
        else{
            cursor.moveToFirst();
            int column_index = cursor.getColumnIndexOrThrow(projection[0]);
            path = cursor.getString(column_index);
            cursor.close();
        }

        return ((path == null || path.isEmpty()) ? (uri.getPath()) : path);
    }

    //endregion

    //region AUTHENTICATION

    // Callback function when the frame analyser and the face recognition model successfully recognizes a face
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onRecognizedFace(String userid, Double distance)
    {
        // Save the recognized user id
        face_identifiedID = userid;
        // Below we do checks for the following situations where we reset the face recognition process:
        // - Another face was detected
        if(face_identifiedID != last_face_identifiedID && last_face_identifiedID != null)
        {
            faceRecognizedTime = 0;
            Logger.Companion.log("Another face detected, resetting timer");
        }
        else
        {
            if(lastFaceRecognizedInstant == null) {
                lastFaceRecognizedInstant = Instant.now();
                faceRecognizedTime = 0;
            }
            // If the euclidean distance is larger than the threshold
            if(distance > face_DistanceThreshold)
            {
                // We will not count towards the recognized time
                Logger.Companion.log("Recognized " + userid + " but distance " + distance + " is larger than threshold! Rejecting");
                // I thought about resetting the process here, but if we only cross the threshold once or twice
                // or if the face recognition is unsure for a while but then becomes certain then it should still be fine
            }
            else {
                // Count towards the recognized time
                faceRecognizedTime += Duration.between(lastFaceRecognizedInstant, Instant.now()).toMillis();
                //Logger.Companion.log(userid + " has been detected for " + faceRecognizedTime + " ms");
            }

        }
        lastFaceRecognizedInstant = Instant.now();
        last_face_identifiedID = face_identifiedID;
    }

    // Handle on no face was detected
    private void onNoFace() {
        // If the identified ID is not null it means there was a face and it's now lost
        if(face_identifiedID != null)
            Logger.Companion.log("Face lost, resetting timer");
        face_identifiedID = null;
        lastFaceRecognizedInstant = null;
        faceRecognizedTime = 0;
    }

    // Final authentication check function, called periodically as determined in onCreate()
    //Should pass when satisfying:
    // faceRecognizedTime larger than faceRecognizedTimeThreshold
    // Fingerprint identified and score larger than score threshold
    // face and finger id match
    // if finger scanned first, face scanned not longer than 4 seconds afterwards
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void finalAuthenticationCheck()
    {
        // Either face or fingerprint not recognized yet
        if (face_identifiedID == null || fp_identifiedID == null)
        {
            return;
        }
        // Face has not been recognized for longer than threshold
        if(faceRecognizedTime < faceRecognizedTimeThreshold)
        {
            return;
        }
        // Face and fingerprint recognize different people, reject
        if(!face_identifiedID.equals(fp_identifiedID))
        {
            Logger.Companion.log("Discrepancy between face and fingerprint, rejected\n" +
                    "face_identifiedID is '" + face_identifiedID + "' fp_identifiedID is '" + fp_identifiedID + ";");
            Toast toast = Toast.makeText(this, "Please retry!", Toast.LENGTH_LONG);
            toast.show();
            onNoFace();
            return;
        }
        // In case the fingerprint was scanned first but the face took longer than 4 seconds since the fingerprint was scanned, reject
        if(Duration.between(lastFPRecognizedInstant, Instant.now()).getSeconds() > 4 )
        {
            onNoFace();
            // Also reset fingerprint identified ID
            fp_identifiedID = null;
            Logger.Companion.log("Face took longer than 4 seconds to scan after fingerprint scanned, rejected");
            Toast toast = Toast.makeText(this, "Please scan your fingerprint again!", Toast.LENGTH_LONG);
            toast.show();
            return;
        }
        // If all checks have passed then we can let the user in to the second page
        handleAuthenticationPassed();

    }

    private void handleAuthenticationPassed()
    {
        goToMain();
    }

    //endregion

    // Go to the second page for scanning books
    public void goToMain() {
        //Stop fingerprint scanner
        try {
            stopSensor();
        } catch (FingerprintException e) {
            e.printStackTrace();
        }
        // Start new intent
        Intent intent = new Intent(this, mainPageActivity.class);
        // Pass the identified user ID to the next page
        intent.putExtra("userid", face_identifiedID);
        startActivity(intent);
    }

    //region FINGERPRINT FUNCTIONS

    // Start fingerprint sensor
    private void startFingerprintSensor() {
        // Define output log level
        LogHelper.setLevel(Log.VERBOSE);
        // Start fingerprint sensor
        Map fingerprintParams = new HashMap();
        //set vid
        fingerprintParams.put(ParameterHelper.PARAM_KEY_VID, VID);
        //set pid
        fingerprintParams.put(ParameterHelper.PARAM_KEY_PID, PID);
        fingerprintSensor = FingprintFactory.createFingerprintSensor(this, TransportType.USB, fingerprintParams);
    }

    // Init the fingerprint scanner USB connection
    private void InitDevice()
    {
        UsbManager musbManager = (UsbManager)this.getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        Context context = this.getApplicationContext();
        context.registerReceiver(mUsbReceiver, filter);

        for (UsbDevice device : musbManager.getDeviceList().values())
        {
            if (device.getVendorId() == VID && device.getProductId() == PID)
            {
                if (!musbManager.hasPermission(device))
                {
                    Intent intent = new Intent(ACTION_USB_PERMISSION);
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
                    musbManager.requestPermission(device, pendingIntent);
                }
            }
        }
    }

    // Start the fingerprint scanning process and its callbacks
    public void startFingerprintScan () throws FingerprintException
    {
        //Fingerprint Capture Listener
        try {
            if (bstart) return;
            fingerprintSensor.open(0);
            final FingerprintCaptureListener listener = new FingerprintCaptureListener() {
                @Override
                public void captureOK(final byte[] fpImage) {
//                    final int width = fingerprintSensor.getImageWidth();
//                    final int height = fingerprintSensor.getImageHeight();
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            if(null != fpImage)
//                            {
//                                ToolUtils.outputHexString(fpImage);
//                                LogHelper.i("width=" + width + "\nHeight=" + height);
//                                Bitmap bitmapFp = ToolUtils.renderCroppedGreyScaleBitmap(fpImage, width, height);
//                                //fingerprintImage.setImageBitmap(bitmapFp);
//                            }
//                            //textView.setText("FakeStatus:" + fingerprintSensor.getFakeStatus());
//                        }
//                    });
                }
                @Override
                public void captureError(FingerprintException e) {
                    final FingerprintException exp = e;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.e("FINGERPRINT", "CaptureError  errno=" + exp.getErrorCode() +
                                    ",Internal error code: " + exp.getInternalErrorCode() + ",message=" + exp.getMessage());
                            //Toast toast = Toast.makeText(MainActivity.this, "Please try scanning your fingerprint again!", Toast.LENGTH_SHORT);
                            //toast.show();
                        }
                    });
                }
                @Override
                public void extractError(final int err)
                {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Logger.Companion.log("Extract fail, errorcode:" + err);
                            Toast toast = Toast.makeText(MainActivity.this, "Please try scanning your fingerprint again!", Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    });
                }

                @Override
                public void extractOK(final byte[] fpTemplate)
                {
                    final byte[] tmpBuffer = fpTemplate;
                    runOnUiThread(new Runnable() {
                        @RequiresApi(api = Build.VERSION_CODES.O)
                        @Override
                        public void run() {
                            if (isRegister) {
                                byte[] bufids = new byte[256];
                                int ret = ZKFingerService.identify(tmpBuffer, bufids, 55, 1);
                                //strBase64 = Base64.encodeToString(tmpBuffer, 0, tmpBuffer.length, Base64.NO_WRAP);
                                if (ret > 0)
                                {
                                    String strRes[] = new String(bufids).split("\t");
                                    Logger.Companion.log("This fingerprint has already been enrolled by " + strRes[0] + ", please cancel enrollment");
                                    isRegister = false;
                                    enrollidx = 0;
                                    return;
                                }

                                if (enrollidx > 0 && ZKFingerService.verify(regtemparray[enrollidx-1], tmpBuffer) <= 0)
                                {
                                    Logger.Companion.log("Please press the same finger 3 times on the sensor for enrollment");
                                    return;
                                }
                                System.arraycopy(tmpBuffer, 0, regtemparray[enrollidx], 0, 2048);
                                enrollidx++;
                                if (enrollidx == 3) {
                                    byte[] regTemp = new byte[2048];
                                    Bitmap bitmap = null;
                                    if (0 < (ret = ZKFingerService.merge(regtemparray[0], regtemparray[1], regtemparray[2], regTemp))) {
                                        ZKFingerService.save(regTemp, "test" + uid++);
                                        System.arraycopy(regTemp, 0, lastRegTemp, 0, ret);
                                        //Base64 Template
                                        //strBase64 = Base64.encodeToString(regTemp, 0, ret, Base64.NO_WRAP);

                                        //Convert byte array into bitmap
                                        //bitmap = BitmapFactory.decodeByteArray(regTemp , 0, regTemp.length);
                                        Logger.Companion.log("Enrollment success, userid:" + uid + "count:" + ZKFingerService.count());

                                    } else {
                                        Logger.Companion.log("Enrollment failed");
                                    }
                                    isRegister = false;
                                } else {
                                    Logger.Companion.log("You need to press your finger onto the sensor " + (3 - enrollidx) + "times more");
                                }
                            } else {
                                byte[] bufids = new byte[256];

                                int ret = ZKFingerService.identify(tmpBuffer, bufids, fp_ScoreThreshold, 1);
                                if (ret > 0) {
                                    String strRes[] = new String(bufids).split("\t");
                                    float score = Float.valueOf(strRes[1]);
                                    //Logger.Companion.log("Identify successful, userid:" + strRes[0] + ", score:" + strRes[1]);
                                    Logger.Companion.log("Identify successful, userid:" + strRes[0] + ", score: " + score);

                                    if(score >= Math.round(fp_ScoreThreshold)) {
                                        fp_identifiedID = strRes[0];
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            lastFPRecognizedInstant = Instant.now();
                                        }
                                    }
                                    else
                                    {
                                        Logger.Companion.log("FP Score " + score + " for " + strRes[0] + " + is too low, rejecting");
                                        Toast toast = Toast.makeText(MainActivity.this, "Unable to identify, please try scanning your fingerprint again!", Toast.LENGTH_SHORT);
                                        toast.show();
                                    }

                                } else {
                                    Logger.Companion.log("Identify fail");
                                    Toast toast = Toast.makeText(MainActivity.this, "Unable to identify, please try scanning your fingerprint again!", Toast.LENGTH_SHORT);
                                    toast.show();
                                    //Logger.Companion.log(strBase64);
                                    //Logger.Companion.log(tmpBuffer.toString());
                                }
                                //Base64 Template
                                //String strBase64 = Base64.encodeToString(tmpBuffer, 0, fingerprintSensor.getLastTempLen(), Base64.NO_WRAP);
                            }
                        }
                    });
                }


            };
            fingerprintSensor.setFingerprintCaptureListener(0, listener);
            fingerprintSensor.startCapture(0);
            bstart = true;

            Logger.Companion.log("FIngerprint Sensor Started Successfully");

        }
        catch (FingerprintException e)
        {
            Logger.Companion.log("Fail to begin sensor.errorcode:"+ e.getErrorCode() + "err message:" + e.getMessage() + "inner code:" + e.getInternalErrorCode());
            Toast toast = Toast.makeText(this, "Cannot detect fingerprint scanner. Please connect a fingerprint scanner and restart the app!", Toast.LENGTH_LONG);
            toast.show();
        }
    }

    // ends the fingerprint sensor
    public void stopSensor()throws FingerprintException
    {
        try {
            if (bstart)
            {
                //stop capture
                fingerprintSensor.stopCapture(0);
                bstart = false;
                fingerprintSensor.close(0);
                Logger.Companion.log("Fingerprint Sensor Stopped Successfully");
            }
            else
            {
                Logger.Companion.log("Sensor has already stopped");
            }
        } catch (FingerprintException e) {
            Logger.Companion.log("Failed to stop fingerprint sensor, errno=" + e.getErrorCode() + "\nmessage=" + e.getMessage());
        }
    }

    //endregion

}