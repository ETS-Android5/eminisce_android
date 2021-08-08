package com.eminiscegroup.eminisce;

import com.eminiscegroup.eminisce.caching.SaveBioData;
import com.eminiscegroup.eminisce.server.JsonBioApi;
import com.eminiscegroup.eminisce.server.LibraryUserBioResponse;

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
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;
import com.zkteco.android.biometric.core.device.ParameterHelper;
import com.zkteco.android.biometric.core.device.TransportType;
import com.zkteco.android.biometric.core.utils.LogHelper;
import com.zkteco.android.biometric.core.utils.ToolUtils;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import kotlin.Pair;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class MainActivity extends AppCompatActivity {

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

    private static String BASE_URL = "https://eminisce.herokuapp.com/";
    private Methods Methods;
    private TextView dataTextView = null;
    private ImageView fingerprintImage = null;
    private ImageView fingerprintImage2 = null;
    private boolean verified = false;
    public static final int PICK_IMAGE = 100;
    private Button btn;
    private Uri selectedImageUri = null;
    private byte[] verifyBuffer;
    private String filePath = null;
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

    private PreviewView previewView;
    private FrameAnalyser frameAnalyser;
    private FaceNetModel model;
    private FileReader fileReader;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private SharedPreferences sharedPreferences;

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
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN );
        setContentView(R.layout.activity_main);

        // Implementation of CameraX preview

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

        frameAnalyser = new FrameAnalyser( this , boundingBoxOverlay);
        model = new FaceNetModel( this );
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

        startLoadImages();

        btn = (Button)findViewById(R.id.debug_button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //startDownloadBioData();
            }
        });
    }

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
                    .setMessage( "Existing image data was found on this device. Would you like to load it?" )
                    .setCancelable( false )
                    .setNegativeButton( "LOAD", (dialog, which) ->
                    {
                        try {
                            dialog.dismiss();
                            frameAnalyser.setFaceList(loadSerializedImageData());
                            Logger.Companion.log("Serialized data loaded.");
                        }
                        catch(Exception e)
                        {
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

    private Bitmap getFixedBitmap( Uri imageFileUri ) {
        Bitmap imageBitmap = BitmapUtils.Companion.getBitmapFromUri( getContentResolver() , imageFileUri );
        ExifInterface exifInterface = null;
        try {
            exifInterface = new ExifInterface(getContentResolver().openInputStream( imageFileUri ));
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private final FileReader.ProcessCallback fileReaderCallback = new FileReader.ProcessCallback() {
        public void onProcessCompleted( ArrayList<kotlin.Pair<String, float[]>> data, int numImagesWithNoFaces) {
            frameAnalyser.setFaceList(data);
            try {
                saveSerializedImageData(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Logger.Companion.log("Images parsed. Found " + numImagesWithNoFaces + " images with no faces.");
        }
    };

    private void saveSerializedImageData( ArrayList<kotlin.Pair<String, float[]>> data ) throws IOException {
        File serializedDataFile = new File( getFilesDir() , SERIALIZED_DATA_FILENAME );
        ObjectOutputStream ostream = new ObjectOutputStream((OutputStream)(new FileOutputStream(serializedDataFile)));
        ostream.writeObject(data);
        ostream.flush();
        ostream.close();
        sharedPreferences.edit().putBoolean( SHARED_PREF_IS_DATA_STORED_KEY , true ).apply();
    }

    private ArrayList<kotlin.Pair<String, float[]>> loadSerializedImageData() throws IOException, ClassNotFoundException {
        File serializedDataFile = new File( getFilesDir() , SERIALIZED_DATA_FILENAME );
        ObjectInputStream objectInputStream = new ObjectInputStream((InputStream)(new FileInputStream(serializedDataFile)));
        ArrayList<kotlin.Pair<String,float[]>> data = (ArrayList<kotlin.Pair<String, float[]>>) objectInputStream.readObject();
        objectInputStream.close();
        return data;
    }

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

    private void startDownloadBioData()
    {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://eminisce.herokuapp.com/")
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
                    List<LibraryUserBioResponse> bios = (List<LibraryUserBioResponse>) response.body();
                    SaveBioData save_bio_data = new SaveBioData(MainActivity.this, bios);
                    save_bio_data.Save();
                    loadBio();
                }
                else{
                    Log.e("DB", response.errorBody().toString());
                }
            }
            @Override
            public void onFailure(Call<List<LibraryUserBioResponse>> call, Throwable t) {
                Log.e("DB", "OnFailure called");
                t.printStackTrace();
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void loadBio()
    {
        Logger.Companion.log("Reading downloaded biometric data to prepare authentication...");
        try {
            // A bit of duplicated code here but I don't want to mix face images and fingerprint templates together
            // Better to keep them separated
            File dir = new File(this.getFilesDir().toString() + File.separator + "BIO_FACES");
            Log.d("LOAD FACE IMAGES", "Does BIO_FACES folder exist: " + dir.exists());
            ArrayList<kotlin.Pair<String,Bitmap>> images = new ArrayList<kotlin.Pair<String,Bitmap>>();
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
                                break;
                            }
                        }
                        Logger.Companion.log("Found " + df.listFiles().length + " images in " + name + " directory");
                    }
                    else
                    {
                        Logger.Companion.log("Invalid BIO_FACES folder structure.");
                    }
                }
            }
            else {
                Logger.Companion.log( "Empty biometric data folder!" );
            }
            fileReader.run( images , fileReaderCallback );
            Logger.Companion.log( "Detecting faces in " + images.size() + " images ..." );

            Logger.Companion.log( "Loading fingerprints ..." );
            dir = new File(this.getFilesDir().toString() + File.separator + "BIO_FINGERPRINTS");
            Log.d("LOAD FP", "Does BIO_FINGERPRINTS folder exist: " + dir.exists());
            tree = DocumentFile.fromFile(dir);
            if (tree.listFiles().length > 0) {
                for (DocumentFile df : tree.listFiles()) {
                    if (df.isDirectory()) {
                        String name = df.getName();
                        for (DocumentFile fp : df.listFiles()) {
                            File file = new File(getPath(fp.getUri()));
                            byte[] bytes = Files.readAllBytes(file.toPath());
                            ZKFingerService.save(bytes, name);
                            Log.d("Verify fp", "Verified");
                        }
                    }
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

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



    /** Called when the user taps the button */
    public void tempButton(View view) {
        Intent intent = new Intent(this, mainPageActivity.class);
        startActivity(intent);
    }

    //region FINGERPRINT FUNCTIONS

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

    //Pressed if user wants to log in using fingerprint authentication
    public void fingerprintBtn (View view) throws FingerprintException
    {
        //Fingerprint Capture Listener
        try {
            if (bstart) return;
            fingerprintSensor.open(0);
            final FingerprintCaptureListener listener = new FingerprintCaptureListener() {
                @Override
                public void captureOK(final byte[] fpImage) {
                    final int width = fingerprintSensor.getImageWidth();
                    final int height = fingerprintSensor.getImageHeight();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(null != fpImage)
                            {
                                ToolUtils.outputHexString(fpImage);
                                LogHelper.i("width=" + width + "\nHeight=" + height);
                                Bitmap bitmapFp = ToolUtils.renderCroppedGreyScaleBitmap(fpImage, width, height);
                                fingerprintImage.setImageBitmap(bitmapFp);
                            }
                            //textView.setText("FakeStatus:" + fingerprintSensor.getFakeStatus());
                        }
                    });
                }
                @Override
                public void captureError(FingerprintException e) {
                    final FingerprintException exp = e;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LogHelper.d("CaptureError  errno=" + exp.getErrorCode() +
                                    ",Internal error code: " + exp.getInternalErrorCode() + ",message=" + exp.getMessage());
                        }
                    });
                }
                @Override
                public void extractError(final int err)
                {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dataTextView.setText("Extract fail, errorcode:" + err);
                        }
                    });
                }

                @Override
                public void extractOK(final byte[] fpTemplate)
                {
                    final byte[] tmpBuffer = fpTemplate;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isRegister) {
                                byte[] bufids = new byte[256];
                                int ret = ZKFingerService.identify(tmpBuffer, bufids, 55, 1);
                                strBase64 = Base64.encodeToString(tmpBuffer, 0, tmpBuffer.length, Base64.NO_WRAP);
                                if (ret > 0)
                                {
                                    String strRes[] = new String(bufids).split("\t");
                                    dataTextView.setText("This fingerprint has already been enrolled by " + strRes[0] + ", please cancel enrollment");
                                    isRegister = false;
                                    enrollidx = 0;
                                    return;
                                }

                                if (enrollidx > 0 && ZKFingerService.verify(regtemparray[enrollidx-1], tmpBuffer) <= 0)
                                {
                                    dataTextView.setText("Please press the same finger 3 times on the sensor for enrollment");
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
                                        strBase64 = Base64.encodeToString(regTemp, 0, ret, Base64.NO_WRAP);

                                        //Convert byte array into bitmap
                                        //bitmap = BitmapFactory.decodeByteArray(regTemp , 0, regTemp.length);
                                        dataTextView.setText("Enrollment success, userid:" + uid + "count:" + ZKFingerService.count());

                                    } else {
                                        dataTextView.setText("Enrollment failed");
                                    }
                                    isRegister = false;
                                } else {
                                    dataTextView.setText("You need to press your finger onto the sensor " + (3 - enrollidx) + "times more");
                                }
                            } else {
                                byte[] bufids = new byte[256];

                                int ret = ZKFingerService.identify(tmpBuffer, bufids, 55, 1);
                                if (ret > 0) {
                                    String strRes[] = new String(bufids).split("\t");
                                    //dataTextView.setText("Identify successful, userid:" + strRes[0] + ", score:" + strRes[1]);
                                    dataTextView.setText("Identify successful, userid:" + strRes[0] + ", score:" + strRes[1]);
                                } else {
                                    dataTextView.setText("Identify fail");
                                    //dataTextView.setText(strBase64);
                                    //dataTextView.setText(tmpBuffer.toString());
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

            dataTextView.setText("FIngerprint Sensor Started Successfully");

        }
        catch (FingerprintException e)
        {
            dataTextView.setText("Fail to begin sensor.errorcode:"+ e.getErrorCode() + "err message:" + e.getMessage() + "inner code:" + e.getInternalErrorCode());
        }

        /*
        if (enrolled == true)
        {
            verify();
        }
        */
    }

    public void enrollBtn (View view)
    {
        if (bstart) {
            isRegister = true;
            enrollidx = 0;
            dataTextView.setText("You need to scan your fingerprint 3 times");
        }
        else
        {
            dataTextView.setText("Please start your fingerprint sensor first");
        }
    }

    //ends the fingerprint sensor
    public void stopSensor()throws FingerprintException
    {
        try {
            if (bstart)
            {
                //stop capture
                fingerprintSensor.stopCapture(0);
                bstart = false;
                fingerprintSensor.close(0);
                dataTextView.setText("Fingerprint Sensor Stopped Successfully");
            }
            else
            {
                dataTextView.setText("Sensor has already stopped");
            }
        } catch (FingerprintException e) {
            dataTextView.setText("Failed to stop fingerprint sensor, errno=" + e.getErrorCode() + "\nmessage=" + e.getMessage());
        }
    }

    // verify the fingerprint
    public void verify() throws FingerprintException {

        if (bstart)
        {
            isRegister = false;
            enrollidx = 0;
            if(verified == true)
            {
                stopSensor();
                Intent intent2 = new Intent(this, mainPageActivity.class);
                startActivity(intent2);
            }
        }
    }

    //endregion

}