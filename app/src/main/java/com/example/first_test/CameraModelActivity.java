/*
package com.example.first_test;

import static com.example.first_test.ModelActivity.assetFilePath;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;
import java.io.File;
import java.io.IOException;

public class CameraModelActivity extends AppCompatActivity {

    private static final String TAG = "PytorchHelloWorld";
    private Uri photoUri;
    private Module module;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_model_cativity);

        // Load the PyTorch model
        try {
            module = LiteModuleLoader.load(assetFilePath(this, "model.pt"));
        } catch (IOException e) {
            Log.e(TAG, "Error reading assets", e);
            finish();
        }

        // Set up button to take picture
        findViewById(R.id.button_take_picture).setOnClickListener(v -> {
            dispatchTakePictureIntent();
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.e(TAG, "Error creating image file", ex);
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this,
                        "com.example.myapp.provider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            processCapturedPhoto();
        }
    }

    private void processCapturedPhoto() {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(photoUri));

            // Show image on UI
            ImageView imageView = findViewById(R.id.image);
            imageView.setImageBitmap(bitmap);

            // Prepare input tensor
            final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
                    TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);

            // Run the model
            final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();

            // Get tensor content as java array of floats
            final float[] scores = outputTensor.getDataAsFloatArray();

            // Search for the index with maximum score
            float maxScore = -Float.MAX_VALUE;
            int maxScoreIdx = -1;
            for (int i = 0; i < scores.length; i++) {
                if (scores[i] > maxScore) {
                    maxScore = scores[i];
                    maxScoreIdx = i;
                }
            }

            String className = ImageNetClasses.IMAGENET_CLASSES[maxScoreIdx];

            // Show className on UI
            TextView textView = findViewById(R.id.text);
            textView.setText(className);

        } catch (IOException e) {
            Log.e(TAG, "Error processing captured photo", e);
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String imageFileName = "JPEG_" + System.currentTimeMillis() + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,  /* prefix */
  //              ".jpg",         /* suffix */
    //            storageDir      /* directory */
    //    );
   // }
//}

//**/