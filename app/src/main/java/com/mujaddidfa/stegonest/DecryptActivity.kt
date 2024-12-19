package com.mujaddidfa.stegonest

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mujaddidfa.stegonest.databinding.ActivityDecryptBinding
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Suppress("DEPRECATION")
class DecryptActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDecryptBinding
    private lateinit var builder: AlertDialog.Builder
    private var btm: Bitmap? = null
    private var decodeStr = ""
    private var finalStr = ""
    private val validImgOrNot = "011010010110111001100110011010010110111001101001"
    private var valid = 1

    @SuppressLint("ServiceCast", "IntentReset")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDecryptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        builder = AlertDialog.Builder(this)

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val dialogView: View = inflater.inflate(R.layout.process_dialog, null)

        builder.setView(dialogView)
        builder.setTitle("Decrypting...")
        builder.setCancelable(false)
        val dialog: Dialog = builder.create()

        binding.decodedText.setOnClickListener {
            val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("text", binding.decodedText.text.toString())
            manager.setPrimaryClip(clipData)
            Toast.makeText(this,"Text Copied to Clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.rlImgInput.setOnClickListener {
            try {
                val intent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivityForResult(intent, 1)
            }
            catch (e: java.lang.Exception){
                Toast.makeText(this, "Can't Open Gallary", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDecrypt.setOnClickListener {
            if (binding.edKey.text.toString().length == 16) {
                if (btm != null) {
                    Log.d("111", "its first")
                    Thread {
                        this.runOnUiThread { dialog.show() }
                        extractStrFromImg(btm!!)
                        Log.d("padddinf", valid.toString())
                        if (valid == 1) {
                            val base64Str = convertToBase64(decodeStr)

                            finalStr = decodeStr(base64Str).toString()

                            this.runOnUiThread {
                                dialog.dismiss()
                                finalStr.let { it1 -> Log.d("final", it1) }

                                binding.decodedText.text = finalStr
                                binding.decodedText.visibility = View.VISIBLE

                                decodeStr = ""
                                finalStr = ""
                                valid = 1
                            }
                        }
                        else {
                            runOnUiThread {
                                dialog.dismiss()
                                Toast.makeText(this, "No data encoded in image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.start()
                }
                else Toast.makeText(this, "Image not added", Toast.LENGTH_SHORT).show()
            }
            else {
                val keyLength = binding.edKey.text.toString().length
                Toast.makeText(this, "Key length must be 16 digits not $keyLength", Toast.LENGTH_SHORT).show()
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
                    btm = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                    val stream = ByteArrayOutputStream()
                    btm!!.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    Toast.makeText(this,"Image added", Toast.LENGTH_SHORT).show()
                    binding.tvImageInput.text = "Change Image to Decrypt"
                } catch (e: Exception) {
                    Toast.makeText(this,"Error! Image not added ", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun extractStrFromImg(btm: Bitmap){
        val w: Int = btm.width
        val h: Int = btm.height
        val data = IntArray(w * h)

        btm.getPixels(data, 0, w, 0, 0, w, h)
        Log.d("w", w.toString())
        Log.d("h", h.toString())

        var count = 0
        var chk = 1

        Log.d("r after", (data[1] shr 8 and 0xff).toString())
        Log.d("r2 afte", (data[2] shr 8 and 0xff).toString())

        for (y in 0 until h) {
            if ((chk == 1) and (valid==1) ){
                for (x in 0 until w) {
                    val index: Int = y * w + x
                    val r: Int = data[index] shr 16 and 0xff
                    val g: Int = data[index] shr 8 and 0xff
                    val b: Int = data[index] and 0xff

                    if (terminate() or (valid == 0)) {
                        chk = 0
                        break
                    } else {
                        terminateDecode(r, count)
                        count++
                    }

                    if (terminate() or (valid == 0)) {
                        chk = 0
                        break
                    }
                    else {
                        terminateDecode(g, count)
                        count++
                    }

                    if (terminate() or (valid == 0)) {
                        chk = 0
                        break
                    } else {
                        terminateDecode(b, count)
                        count++
                    }

                    data[index] = -0x1000000 or (r shl 16) or (g shl 8) or b
                }
            } else break
        }
    }

    private fun terminateDecode(co: Int, count: Int) {
        val b = Integer.toBinaryString(co)

        decodeStr += b[b.length - 1]
        if (decodeStr.length == 48 ) {
            if (decodeStr != validImgOrNot) { valid = 0}
        }
        if (decodeStr.length % 8 == 0) {
            val toInt = Integer.parseInt(
                decodeStr.slice(decodeStr.length - 8..<decodeStr.length),
                2
            )
            val toChar = toInt.toChar()
            if (count < 2000) {
                Log.d("chr", toChar.toString())
            }
        }
    }

    private  fun terminate():Boolean{
        if (decodeStr.length >= 16){
            val terminate1 = decodeStr.slice(decodeStr.length - 16..decodeStr.length - 9)
            val terminate1Int = Integer.parseInt(terminate1, 2)
            val terminate2 = decodeStr.slice(decodeStr.length - 8..<decodeStr.length)
            val terminate2Int = Integer.parseInt(terminate2, 2)
            if ((terminate1Int == 23) and (terminate2Int == 30)){
                decodeStr = decodeStr.slice(48..decodeStr.length - 16)
                return(true)
            }
        }
        return false
    }

    private fun convertToBase64(s: String): String {
        var base64Str = ""
        for (i in 0..s.length - 8 step  8) {
            val bin = s.slice(i..i + 7)
            val toInt = Integer.parseInt(bin, 2)
            val toChar = toInt.toChar()
            base64Str += toChar
        }
        return base64Str
    }

    @SuppressLint("GetInstance")
    private  fun decodeStr(bs: String):String?{
        val dKey = SecretKeySpec(binding.edKey.text.toString().toByteArray(), "AES")
        val c = Cipher.getInstance("AES")
        c.init(Cipher.DECRYPT_MODE, dKey)
        var ree: ByteArray? = null
        try {
            ree = c.doFinal(Base64.decode(bs, Base64.NO_WRAP or Base64.NO_PADDING))
        }
        catch (e: Exception){
            runOnUiThread { Toast.makeText(this, "Wrong security Key", Toast.LENGTH_SHORT).show() }
        }
        val st: String? = ree?.let { String(it) }
        return st
    }
}