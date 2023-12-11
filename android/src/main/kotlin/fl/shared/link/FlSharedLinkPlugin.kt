package fl.shared.link


import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.*


/** FlSharedLinkPlugin */
class FlSharedLinkPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var binding: ActivityPluginBinding? = null
    private var intent: Intent? = null
    private var uriMap = HashMap<String, Uri>()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "fl.shared.link")
        channel.setMethodCallHandler(this)
        context = binding.applicationContext
    }


    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.binding = binding
        binding.addOnNewIntentListener(onNewIntent)
        binding.activity.intent?.let { handlerIntent(it) }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        binding?.removeOnNewIntentListener(onNewIntent)
        binding = null
    }


    override fun onDetachedFromActivity() {
        onDetachedFromActivityForConfigChanges()
    }


    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun handlerIntent(intent: Intent) {
        this.intent = intent
        channel.invokeMethod("onIntent", this.intent?.map)
    }

    private var onNewIntent: PluginRegistry.NewIntentListener =
        PluginRegistry.NewIntentListener { intent ->
            handlerIntent(intent)
            true
        }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getIntent" -> result.success(intent?.map)
            "getRealFilePath" -> {
                val args = call.arguments as Map<*, *>
                val isCopy = args["isCopy"] as Boolean
                val uri = uriMap[args["id"] as String]
                val realPath = if (uri == null) {
                    null
                } else when (uri.scheme) {
                    ContentResolver.SCHEME_CONTENT -> getFilePathFromContentUri(uri, isCopy)
                    ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).absolutePath }

                    else -> uri.path?.let { File(it).absolutePath }
                }
                result.success(realPath)
            }


            else -> {
                result.notImplemented()
            }
        }
    }


    /**
     * 从uri获取path
     *
     * @param uri content://media/external/file/109009
     *
     * FileProvider适配
     * content://com.tencent.mobileqq.fileprovider/external_files/storage/emulated/0/Tencent/QQfile_recv/
     * content://com.tencent.mm.external.fileprovider/external/tencent/MicroMsg/Download/
     */
    private fun getFilePathFromContentUri(uri: Uri?, isCopy: Boolean): String? {
        if (null == uri) return null
        var data: String? = null
        val filePathColumn =
            arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME)
        val cursor: Cursor? = context.contentResolver.query(uri, filePathColumn, null, null, null)
        if (null != cursor) {
            if (cursor.moveToFirst()) {
                val index: Int = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                data = if (index > -1 && !isCopy) {
                    cursor.getString(index)
                } else {
                    val nameIndex: Int = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val fileName: String = cursor.getString(nameIndex)
                    getPathFromInputStreamUri(uri, fileName)
                }
            }
            cursor.close()
        }
        return data
    }

    /**
     * 用流拷贝文件一份到自己APP私有目录下
     *
     * @param uri
     * @param fileName
     */
    private fun getPathFromInputStreamUri(uri: Uri, fileName: String): String? {
        var filePath: String? = null
        if (uri.authority != null) {
            var inputStream: InputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                var file: File? = null
                if (inputStream != null) {
                    var read: Int
                    val buffer = ByteArray(8 * 1024)
                    file = File(context.externalCacheDir, fileName)
                    if (file.exists()) {
                        file.delete()
                    }
                    val outputStream: OutputStream = FileOutputStream(file)
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                    try {
                        outputStream.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                filePath = file?.path
            } catch (e: Exception) {
                println("getPathFromInputStreamUri : ${e.message}")
            } finally {
                inputStream?.close()
            }
        }
        return filePath
    }


    private val Intent.map: Map<String, Any?>
        get() {
            val map = HashMap<String, Any?>()
            map["action"] = action
            map["type"] = type
            map["scheme"] = scheme
            map["extras"] = extras?.map
            var uri = data
            if (uri == null) {
                uri = getParcelableExtra(Intent.EXTRA_STREAM)
            }
            map["url"] = uri?.path
            map["authority"] = uri?.authority
            map["userInfo"] = uri?.userInfo
            val id = uri?.hashCode()?.toString()
            map["id"] = id
            if (uri != null && id != null) {
                uriMap[id] = uri
            }
            return map
        }

    private val Bundle.map: HashMap<String, String?>
        get() {
            val keySet = keySet()
            val map = HashMap<String, String?>()
            for (key in keySet) {
                map[key] = get(key).toString()
            }
            return map
        }
}
