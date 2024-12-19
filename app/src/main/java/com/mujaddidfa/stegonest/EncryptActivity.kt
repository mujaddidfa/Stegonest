package com.mujaddidfa.stegonest

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mujaddidfa.stegonest.databinding.ActivityEncryptBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Suppress("DEPRECATION")
class EncryptActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEncryptBinding
    private lateinit var builder: AlertDialog.Builder
    private var btm: Bitmap? = null
    private var bString = ""

    @SuppressLint("IntentReset")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEncryptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        builder = AlertDialog.Builder(this)

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val dialogView: View = inflater.inflate(R.layout.process_dialog, null)

        builder.setView(dialogView)
        builder.setTitle("Encrypting...")
        builder.setCancelable(false)

        val dialog: Dialog = builder.create()

        binding.rlImgInput.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, 1)
        }

        binding.btnEncrypt.setOnClickListener {
            if (binding.edKey.text.toString().length == 16) {
                if (binding.edText.text.toString().isNotEmpty()) {
                    if (btm != null) {
                        Thread {
                            runOnUiThread { dialog.show() }
                            val s = encryptingData()
                            val bitmap = dataHidingInImg(s, btm!!)
                            bString = ""
                            saveMediaToStorage(bitmap)
                            runOnUiThread { dialog.dismiss() }
                        }.start()
                    }
                    else Toast.makeText(this, "ADD AN IMAGE FIRST", Toast.LENGTH_SHORT).show()
                }
                else Toast.makeText(this, "Text to hide is empty", Toast.LENGTH_SHORT).show()
            }
            else {
                val keyLength = binding.edKey.text.toString().length
                Toast.makeText(this, "Key must be of 16 digits not $keyLength", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
                    val cursor: Cursor? = contentResolver.query(
                        uri,
                        filePathColumn, null, null, null
                    )
                    cursor!!.moveToFirst()
                    val columnIndex: Int = cursor.getColumnIndex(filePathColumn[0])
                    val picturePath: String = cursor.getString(columnIndex)
                    cursor.close()

                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(picturePath, options)
                    Log.d("uri path", picturePath)
                    options.inSampleSize = calculateInSampleSize(options)
                    options.inJustDecodeBounds = false
                    btm = BitmapFactory.decodeFile(picturePath, options)

                    binding.imgPreview.setImageBitmap(btm)
                    binding.imgPreview.visibility= View.VISIBLE
                    binding.tvImageInput.text = "Change Image"
                }
                catch (e: Exception) {
                    Toast.makeText(this, "Can't catch image. Try again!", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("GetInstance")
    private fun encryptingData(): String {
        val key = binding.edKey.text.toString()
        val text = binding.edText.text.toString()

        val sKey = SecretKeySpec(key.toByteArray(), "AES")
        print(sKey.toString())

        val c = Cipher.getInstance("AES")
        c.init(Cipher.ENCRYPT_MODE, sKey)

        val re = c.doFinal(text.toByteArray())

        val reBase64 = Base64.encodeToString(re, Base64.NO_WRAP or Base64.NO_PADDING)
        Log.d("aaAA", reBase64.toString())

        for(i in reBase64){
            var singleBString = Integer.toBinaryString((i.toInt()))

            if (singleBString.length < 8){
                for (j in 1..(8 - singleBString.length)) {
                    singleBString = "0$singleBString"
                }
            }
            bString += singleBString
        }
        Log.d("barraylength", bString)
        Log.d("barray", bString.length.toString())
        return bString
    }

    private fun dataHidingInImg(s: String, btm: Bitmap): Bitmap {
        val terminateString = "0001011100011110"
        val startingString = "011010010110111001100110011010010110111001101001"
        val strToEncode = startingString + s + terminateString

        val w: Int = btm.width
        val h: Int = btm.height
        val data = IntArray(w * h)

        btm.getPixels(data, 0, w, 0, 0, w, h)
        Log.d("w", w.toString())
        Log.d("h", h.toString())

        var count = 0

        Log.d("r be", (data[1] shr 8 and 0xff).toString())
        Log.d("r2 before", (data[2] shr 8 and 0xff).toString())

        for (y in 0 until h) {
            if (count > strToEncode.length - 1) {
                break
            } else {
                for (x in 0 until w) {
                    if (count > strToEncode.length - 1) {
                        break
                    } else {
                        val index: Int = y * w + x
                        var r = data[index] shr 16 and 0xff
                        var g = data[index] shr 8 and 0xff
                        var b = data[index] and 0xff

                        r = encode(r, count, strToEncode)
                        count++
                        if (count < strToEncode.length){
                            g = encode(g, count, strToEncode)
                            count++
                        }
                        if (count < strToEncode.length){
                            b = encode(b, count, strToEncode)
                            count++
                        }

                        data[index] = -0x1000000 or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }
        Log.d("r after", (data[1] shr 8 and 0xff).toString())
        Log.d("r2 afte", (data[2] shr 8 and 0xff).toString())

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(data, 0, w, 0, 0, w, h)

        return bitmap
    }

    private fun encode(co: Int, count: Int, strToEncode: String): Int {
        var b = Integer.toBinaryString(co)
        if (b.length < 8){
            for (j in 1..(8 - b.length)){
                b = "0$b"
            }
        }
        b = b.slice(0..(b.length - 2)) + strToEncode[count]
        val d = Integer.parseInt(b, 2)
        return d
    }

    private fun saveMediaToStorage(bitmap: Bitmap) {
        try {
            val filename = "${System.currentTimeMillis()}.png"
            var fos: OutputStream? = null
            val directoryName = "EncryptedImages"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d("SaveMedia", "SDK >= Q")
                contentResolver?.also { resolver ->
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$directoryName")
                    }
                    val imageUri: Uri? =
                        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                    runOnUiThread { Toast.makeText(this, "Process Done!! Image saved to Internal/Pictures/$directoryName", Toast.LENGTH_SHORT).show() }
                    Log.d("SaveMedia", "Image saved to Internal/Pictures/$directoryName")
                }
            } else {
                Log.d("SaveMedia", "SDK < Q")
                val imagesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), directoryName)
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                val image = File(imagesDir, filename)
                fos = FileOutputStream(image)
                runOnUiThread { Toast.makeText(this, "Process Done!! Image saved to ${imagesDir.absolutePath}", Toast.LENGTH_SHORT).show() }
                Log.d("SaveMedia", "Image saved to ${imagesDir.absolutePath}")
            }
            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread { Toast.makeText(this, "Error!! Image not Saved", Toast.LENGTH_SHORT).show() }
            Log.e("SaveMedia", "Error saving image", e)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options): Int {

        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > 400 || width > 400) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= 400 && halfWidth / inSampleSize >= 400) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}