package com.eminiscegroup.eminisce.caching;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.eminiscegroup.eminisce.server.LibraryUserBioResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class SaveBioData {
    private final String fpDirUri = "BIO_FINGERPRINTS";
    private final String faceDirUri = "BIO_FACES";

    private List<LibraryUserBioResponse> bios;

    private Context context;

    public SaveBioData(Context context, List<LibraryUserBioResponse> bios)
    {
        this.context = context;
        this.bios = bios;
        Log.d("CACHING", "Initialized save bio with " + bios.size() + " records.");
    }

    public void Save()
    {
        // Save the biometric data files in the following structure:
        //  - BIO_FACES
        //      - <user_id1>
        //          - <user_id1>.png
        //      - <user_id2>
        //          - <user_id2>.png
        //  - BIO_FINGERPRINTS
        //      - <user_id1>
        //          - <user_id1>.fp
        //      - <user_id2>
        //          - <user_id2>.fp
        Log.d("CACHING", "Starting to save fetched data from database");
        for ( LibraryUserBioResponse bio : bios)
        {
            String name = bio.getUser();
            File face_dir = new File(context.getFilesDir() + File.separator + faceDirUri + File.separator + name);
            File fp_dir = new File(context.getFilesDir() + File.separator + fpDirUri + File.separator + name);

            if(!face_dir.exists())
                face_dir.mkdirs();

            if(!fp_dir.exists())
                fp_dir.mkdirs();

            byte[] face_img_bytes = Base64.decode(bio.getFace_b64(), 0);
            byte[] fingerprint_bytes = Base64.decode(bio.getFingerprint_b64(), 0);

            File face_img = new File(face_dir, name + ".png");
            File fp = new File(fp_dir, name + ".fp");

            Log.d("CACHING", "Saving " + name);

            if(face_img.exists())
                face_img.delete();
            if(fp.exists())
                fp.delete();

            try {
                Log.d("CACHING", "Saving2 " + name);

                FileOutputStream fos = new FileOutputStream(face_img, false);
                fos.write(face_img_bytes);
                fos.flush();
                fos.close();

                fos = new FileOutputStream(fp, false);
                fos.write(fingerprint_bytes);
                fos.flush();
                fos.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
