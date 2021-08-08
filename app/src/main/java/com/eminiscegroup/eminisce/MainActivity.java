package com.eminiscegroup.eminisce;

import com.eminiscegroup.eminisce.caching.SaveBioData;
import com.eminiscegroup.eminisce.server.JsonBioApi;
import com.eminiscegroup.eminisce.server.LibraryUserBioResponse;
import com.eminiscegroup.eminisce.R;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.zkteco.android.biometric.core.device.ParameterHelper;
import com.zkteco.android.biometric.core.device.TransportType;
import com.zkteco.android.biometric.core.utils.LogHelper;
import com.zkteco.android.biometric.core.utils.ToolUtils;
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintCaptureListener;
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintSensor;
import com.zkteco.android.biometric.module.fingerprintreader.FingprintFactory;
import com.zkteco.android.biometric.module.fingerprintreader.ZKFingerService;
import com.zkteco.android.biometric.module.fingerprintreader.exception.FingerprintException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        setContentView(R.layout.activity_main);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();


        Methods = retrofit.create(Methods.class);
        //Intent intent = getIntent();
        dataTextView = (TextView)findViewById(R.id.dataTextView);
        fingerprintImage = (ImageView)findViewById(R.id.fingerprintImage);
        fingerprintImage2 = (ImageView)findViewById(R.id.fingerprintImage2);

        //Initiate Device
        InitDevice();
        //Start fingerprintSensor
        startFingerprintSensor();

        btn = (Button)findViewById(R.id.galleryBtn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownloadBioData();
            }
        });
    }

    private void startDownloadBioData()
    {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://eminisce.herokuapp.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        JsonBioApi service = retrofit.create(JsonBioApi.class);
        Call<List<LibraryUserBioResponse>> call = service.getBios();

        call.enqueue(new Callback<List<LibraryUserBioResponse>>() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(Call<List<LibraryUserBioResponse>> call, Response<List<LibraryUserBioResponse>> response) {
                if (response.code() == 200) {
                    Log.d("DB", "Download from DB success");
                    List<LibraryUserBioResponse> bios = (List<LibraryUserBioResponse>) response.body();
                    SaveBioData save_bio_data = new SaveBioData(MainActivity.this, bios);
                    save_bio_data.Save();
                    loadFPData();
                }
                else{
                    Log.e("DB", response.errorBody().toString());
                }
            }
            @Override
            public void onFailure(Call<List<LibraryUserBioResponse>> call, Throwable t) {
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void loadFPData()
    {
        try {
            File dir = new File(this.getFilesDir().toString() + File.separator + "BIO_FINGERPRINTS");
            Log.d("LOAD FP", "Does FP folder exist: " + dir.exists());
            DocumentFile tree = DocumentFile.fromFile(dir);
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

    public void convertToByte(Bitmap bmp){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
        verifyBuffer = baos.toByteArray();
        int len = verifyBuffer.length;
        String strBase2 = Base64.encodeToString(verifyBuffer, 0, len, Base64.NO_WRAP);
        dataTextView.setText(verifyBuffer.toString());
        //String encodedImage = android.util.Base64.encodeToString(imageBytes, Base64.DEFAULT);
        //return encodedImage;
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



    public void saveBitmap(Bitmap bm) {
        String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Internal storage";
        File f = new File(fullPath, "fingerprint2.bmp");
        //f.mkdirs();

        if (f.exists()) {
            f.delete();
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }catch (IOException e) {
            e.printStackTrace();
        }
    }



    /*
    public byte[] convertImageToByte(Uri uri){
        byte[] data = null;
        try {
            ContentResolver cr = getBaseContext().getContentResolver();
            InputStream inputStream = cr.openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            data = baos.toByteArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return data;
    }
    */

    public Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }


    /** Called when the user taps the button */
    public void tempButton(View view) {
        Intent intent = new Intent(this, mainPageActivity.class);
        startActivity(intent);
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
                                saveBitmap(bitmapFp);
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


}