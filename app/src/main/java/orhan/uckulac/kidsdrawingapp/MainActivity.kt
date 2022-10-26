package orhan.uckulac.kidsdrawingapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import orhan.uckulac.kidsdrawingapp.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null
    private var customProgressDialog: Dialog? = null


    // create a result launcher to get the image URI from the phone gallery
    // first define what kind of a launcher will it be? 'intent'
    // register for activity result to get result
    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        result ->
            // check the result, if It's okay and if the result data is not empty,
            // then get the location of the data, URI, and assign it as background image
            if (result.resultCode == RESULT_OK && result.data != null){
                val imageBackground: ImageView = binding.ivBackground
                imageBackground.setImageURI(result.data?.data)
            }
    }

    private val requestPermission: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                isGranted ->
                // if permission is granted show a toast and perform operation
                // there is no need to ask for multiple permissions, such as WRITE_EXTERNAL_STORAGE
                if (isGranted ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission granted now you can read the storage files.",
                        Toast.LENGTH_LONG
                    ).show()

                    // start an intent to access Media in the phone
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)

                    //perform operation
                } else {
                    // Displaying another toast if permission is not granted and this time focus on
                        Toast.makeText(
                            this@MainActivity,
                            "Oops you just denied the permission.",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawingView = binding.drawingView
        drawingView?.setSizeForBrush(20.toFloat())

        val linearLayoutPaintColors = binding.llPaintColors  // entire linear layout that contains all the colors
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton  // treat like an image button

        val brushSizeBtn = binding.ibBrush
        brushSizeBtn.setOnClickListener {
            brushSizeChooserDialog()
        }

        val uploadImage = binding.ibGallery
        uploadImage.setOnClickListener {
            requestStoragePermission()
        }

        val undoBtn: ImageButton = binding.ibUndo
        undoBtn.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val saveBtn: ImageButton = binding.ibSave
        saveBtn.setOnClickListener {
            // check if reading storage is allowed
            if (isReadStorageAllowed()){
                lifecycleScope.launch { // create a coroutine
                    showDialogProgress()  // show the progress dialog
                    val flDrawingViewContainer: FrameLayout = binding.flDrawingViewContainer  // get both canvas and background(drawings and uploaded image if there is any)
                    val mBitmap: Bitmap = getBitmapView(flDrawingViewContainer)  // create a bitmap and get bitmapview
                    saveBitmapFile(mBitmap)  // save bitmap file to phone
                }
            }
        }
    }


    private fun brushSizeChooserDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush siz: ")
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.ibSmallBrush)
        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.ibMediumBrush)
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.ibLargeBrush)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    // when a color is clicked, change the brush color
    fun paintClicked(view: View){
        if (view !== mImageButtonCurrentPaint){  // Referential Equality
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(  // make current button pressed
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )

            mImageButtonCurrentPaint?.setImageDrawable(  // make previous button normal
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint = view  // override the view with the current button

        }
    }

    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_EXTERNAL_STORAGE) // this will return granted or not granted, which is int 0 , not granted is int 1
        if (result == 0){
            return result==PackageManager.PERMISSION_GRANTED
        }else
            return false
    }

    private fun requestStoragePermission(){
        //Check if the permission was denied and show rationale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(
                Manifest.permission.CAMERA)
        ){
            //call the rationale dialog to tell the user why they need to allow permission request
            showRationaleDialog("Kids Drawing App","Kids Drawing App " +
                    "needs to Access Your External Storage")
        }
        else {
            // You can directly ask for the permission.
            // if it has not been denied then request for permission
            // The registered ActivityResultCallback gets the result of this request.
            requestPermission.launch(
                (Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }
    }

    /** create rationale dialog
     * Shows rationale dialog for displaying why the app needs permission
     * Only shown if the user has denied the permission request previously
     */
    private fun showRationaleDialog(
        title: String,
        message: String,
    ){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){
                    dialog, _-> dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapView(view: View): Bitmap{
        val returnedBitmap: Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)  // this is what we draw on the screen
        val bgDrawable = view.background  // this is the background which might be an image we upload
        if (bgDrawable != null){ // if we didn't upload anything, than its a white screen
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)  // this draws the canvas onto the view, ESSENTIAL
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String{
        var result = ""
        withContext(Dispatchers.IO){
            if (mBitmap != null){
                try {
                    val bytes = ByteArrayOutputStream()  //This class implements an output stream in which the data is written into a byte array
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)  // pass the bytes here

                    val f = File(externalCacheDir?.absoluteFile.toString() +
                            File.separator + "KidsDrawingApp_" + System.currentTimeMillis()/1000 + ".png" )  // create a file and its location
                    val fo = FileOutputStream(f)  // Creates a file output stream to write to the file with the specified name.
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread {
                        cancelProgressDialog()  // stop the progress dialog
                        if (result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,
                                "File Saved Successfully: $result",
                                Toast.LENGTH_LONG
                            ).show()
                            shareImage(result)  // open the share intent for user
                        }else{
                            Toast.makeText(this@MainActivity,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showDialogProgress() {
        customProgressDialog = Dialog(this@MainActivity)
        // set screen content from a layout resource for the dialog
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if (customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null  // make it null for the next time
        }
    }

    private fun shareImage(ImageDirectory: String){
        MediaScannerConnection.scanFile(this@MainActivity, arrayOf(ImageDirectory), null){
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND // set action to the intent, allows us to send items
            // EXTRA_STEAM = A content: URI holding a stream of data associated with the Intent, used with ACTION_SEND to supply the data being sent.
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)  // basically sharing where the image on our device with the Intent
            shareIntent.type = "image/png" // mimeType
            startActivity(Intent.createChooser(shareIntent, "Share"))  // displays and activity chooser
        }
    }
}