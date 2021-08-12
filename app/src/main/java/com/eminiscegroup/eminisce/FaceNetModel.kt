/*
 * Copyright 2021 Shubham Panchal
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Code modified by Le Vu Nguyen Khanh
 */
package com.eminiscegroup.eminisce

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer

// Utility class for FaceNet model
class FaceNetModel( private var context : Context ) {

    // Input image size for FaceNet model.
    private val imgSize = 160

    // Output embedding size
    val embeddingDim = 128

    private var interpreter : Interpreter
    private val imageTensorProcessor = ImageProcessor.Builder()
        .add( ResizeOp( imgSize , imgSize , ResizeOp.ResizeMethod.BILINEAR ) )
        .add( CastOp( DataType.FLOAT32 ) )
        .build()

    init {
        // Initialize TFLiteInterpreter
        val interpreterOptions = Interpreter.Options().apply {
            setNumThreads( 4 )
        }
        interpreter = Interpreter(FileUtil.loadMappedFile(context, "model.tflite") , interpreterOptions )
    }


    // Gets an face embedding using FaceNet, use the `crop` rect.
    fun getFaceEmbedding( image : Bitmap , crop : Rect ) : FloatArray {
        return runFaceNet( convertBitmapToBuffer( cropRectFromBitmap( image , crop )))[0]
    }


    // Gets an face embedding using FaceNet, assuming the image contains a cropped face
    fun getCroppedFaceEmbedding( image : Bitmap ) : FloatArray {
        return runFaceNet( convertBitmapToBuffer( image ) )[0]
    }


    // Run the FaceNet model.
    private fun runFaceNet(inputs: Any): Array<FloatArray> {
        val t1 = System.currentTimeMillis()
        val faceNetModelOutputs = Array( 1 ){ FloatArray( embeddingDim ) }
        interpreter.run( inputs, faceNetModelOutputs )
        Log.i( "Performance" , "FaceNet Inference Speed in ms : ${System.currentTimeMillis() - t1}")
        return faceNetModelOutputs
    }


    // Resize the given bitmap and convert it to a ByteBuffer
    private fun convertBitmapToBuffer( image : Bitmap) : ByteBuffer {
        return imageTensorProcessor.process( TensorImage.fromBitmap( image ) ).buffer
    }


    // Crop the given bitmap with the given rect.
    private fun cropRectFromBitmap(source: Bitmap, rect: Rect ): Bitmap {
        var width = rect.width()
        var height = rect.height()
        if ( (rect.left + width) > source.width ){
            width = source.width - rect.left
        }
        if ( (rect.top + height ) > source.height ){
            height = source.height - rect.top
        }
        //Log.d("BITMAPPING", String.format("W: %d H: %d Rect: %s", width, height, rect))
        try {
            val croppedBitmap = Bitmap.createBitmap(source, rect.left, rect.top, width, height)
            // Uncomment the below line if you want to save the input image.
            // BitmapUtils.saveBitmap( context , croppedBitmap , "source" )
            return croppedBitmap
        }
        catch(e : Exception)
        {
            // Sometimes the rect.left value goes negative
            //  Weird error, I have no idea what is happening here
            Logger.log("Encountered problem trying to crop an image...")
            // Just force it to be positive, it doesn't seem to break anything
            val croppedBitmap = Bitmap.createBitmap(source, Math.abs(rect.left), rect.top, width, height)
            // Uncomment the below line if you want to save the input image.
            // BitmapUtils.saveBitmap( context , croppedBitmap , "source" )
            return croppedBitmap
        }

    }
}