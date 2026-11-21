package com.example.notificationcleaner

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.media.ThumbnailUtils
import android.util.Size
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import java.io.File
import java.text.DateFormat
import java.util.Locale
import java.util.concurrent.Executors

data class StoredFile(val path:String,val name:String,val extension:String,val size:Long,val modified:Long,val category:String)

object StorageRepository {
    val extensions = linkedMapOf(
        "Video" to listOf("mp4","mkv","avi","mov","3gp","webm","m4v","mpeg","mpg","ts","wmv","flv"),
        "Image" to listOf("jpg","jpeg","png","webp","gif","heic","heif","bmp","tif","tiff","dng","raw"),
        "Document" to listOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","rtf","csv","odt","ods","odp","epub"),
        "Compressed" to listOf("zip","rar","7z","tar","gz","bz2","xz","tgz")
    )
    @Volatile var files:List<StoredFile> = emptyList()
    fun scan(onProgress:(Int,Int,String)->Unit):List<StoredFile> {
        val found=ArrayList<StoredFile>()
        val root=android.os.Environment.getExternalStorageDirectory()
        val seen=HashSet<String>(); val stack=ArrayDeque<File>()
        if(root.exists())stack.add(root)
        var count=0
        while(stack.isNotEmpty()){
            val dir=stack.removeLast(); if(!seen.add(dir.absolutePath))continue
            val children=try{dir.listFiles()}catch(_:Exception){null}?:continue
            for(f in children){
                if(f.isDirectory)stack.add(f)
                else if(f.isFile){
                    count++
                    val ext=f.extension.lowercase(Locale.ROOT)
                    val cat=extensions.entries.firstOrNull{ext in it.value}?.key
                    if(cat!=null)found.add(StoredFile(f.absolutePath,f.name,ext,f.length(),f.lastModified(),cat))
                    if(count%250==0)onProgress(count,found.size,f.name)
                }
            }
        }
        files=found;onProgress(count,found.size,"Scan complete");return found
    }
    fun delete(paths:List<String>,progress:(Int,Int,String)->Unit):Pair<Int,Long>{
        var n=0;var bytes=0L
        paths.forEachIndexed{i,p->val f=File(p);val size=f.length();val ok=try{f.delete()}catch(_:Exception){false};if(ok){n++;bytes+=size};progress(i+1,paths.size,f.name)}
        files=files.filter{File(it.path).exists()};return n to bytes
    }
}
fun formatSize(s:Long):String{if(s<1024)return "$s B";val u=arrayOf("KB","MB","GB","TB");var v=s.toDouble();var i=-1;do{v/=1024;i++}while(v>=1024&&i<u.lastIndex);return String.format(Locale.getDefault(),"%.1f %s",v,u[i])}

class MainActivity:Activity(){
    private val selectedExtensions=mutableSetOf<String>();private val selectedPaths=mutableSetOf<String>()
    private var activeCategory="Video";private var gridMode=true;private var sortMode="Date"
    private lateinit var content:LinearLayout;private lateinit var status:TextView;private lateinit var count:TextView;private lateinit var categoryBar:LinearLayout
    private val thumbnailExecutor=Executors.newFixedThreadPool(3)
    private var displayLimit=200
    private val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){when(intent?.action){
        MemoryTaskService.ACTION_PROGRESS->status.text=intent.getStringExtra("text")?:"Working…"
        MemoryTaskService.ACTION_FINISHED->{status.text="Task finished";render()}
    }}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(12,15,19);window.navigationBarColor=Color.rgb(12,15,19)
        val filter=IntentFilter().apply{addAction(MemoryTaskService.ACTION_PROGRESS);addAction(MemoryTaskService.ACTION_FINISHED)}
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(receiver,filter)
        buildUi();checkStoragePermission()
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun checkStoragePermission(){
        if(Build.VERSION.SDK_INT>=30&&!Environment.isExternalStorageManager()){
            AlertDialog.Builder(this).setTitle("Allow storage access")
                .setMessage("To scan shared device storage, enable All files access for Memory Cleaner. Android can still protect some app-private folders.")
                .setPositiveButton("Open settings"){_,_->try{startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:$packageName")))}catch(_:Exception){startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))}}
                .setNegativeButton("Later",null).show()
        }
    }
    private fun actionButton(label:String,click:()->Unit)=Button(this).apply{text=label;textSize=10f;isAllCaps=false;setTextColor(Color.rgb(12,15,19));setBackgroundColor(Color.rgb(113,238,168));setOnClickListener{click()}}
    private fun categoryButton(label:String,selected:Boolean,click:()->Unit)=Button(this).apply{
        text=label.uppercase(Locale.ROOT);textSize=10f;isAllCaps=false
        setTextColor(if(selected)Color.rgb(12,15,19) else Color.rgb(113,238,168))
        background=GradientDrawable().apply{
            cornerRadius=dp(7).toFloat()
            setColor(if(selected)Color.rgb(113,238,168) else Color.rgb(24,29,35))
            setStroke(dp(1),Color.rgb(113,238,168))
        }
        setOnClickListener{click()}
    }
    private fun refreshCategoryButtons(){
        if(!::categoryBar.isInitialized)return
        categoryBar.removeAllViews()
        StorageRepository.extensions.keys.forEach{category->
            categoryBar.addView(categoryButton(category,activeCategory==category){
                activeCategory=category
                selectedExtensions.clear()
                displayLimit=200
                refreshCategoryButtons()
                render()
            },LinearLayout.LayoutParams(0,dp(42),1f).apply{rightMargin=dp(4)})
        }
    }
    private fun ensureNotificationPermission():Boolean{
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),1001)
            toast("Allow notifications, then tap the task button again")
            return false
        }
        return true
    }
    private fun makeThumbnail(file:StoredFile):android.graphics.Bitmap?{
        return try{
            when(file.category){
                "Image"->{
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    BitmapFactory.decodeFile(file.path,bounds)
                    var sample=1
                    while(bounds.outWidth/sample>480 || bounds.outHeight/sample>480)sample*=2
                    val opts=BitmapFactory.Options().apply{inSampleSize=sample}
                    BitmapFactory.decodeFile(file.path,opts)
                }
                "Video"->if(Build.VERSION.SDK_INT>=29)ThumbnailUtils.createVideoThumbnail(File(file.path),Size(320,240),null)
                    else @Suppress("DEPRECATION") ThumbnailUtils.createVideoThumbnail(file.path,android.provider.MediaStore.Images.Thumbnails.MINI_KIND)
                else->null
            }
        }catch(_:Exception){null}
    }
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(20),dp(18),dp(12));setBackgroundColor(Color.rgb(12,15,19))}
        root.addView(TextView(this).apply{text="MEMORY CLEANER";textSize=23f;setTextColor(Color.rgb(113,238,168));typeface=android.graphics.Typeface.DEFAULT_BOLD})
        root.addView(TextView(this).apply{text="Find files. Choose what stays. Clear the rest.";textSize=13f;setTextColor(Color.LTGRAY);setPadding(0,dp(5),0,dp(12))})
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        actions.addView(actionButton("SCAN STORAGE"){startTask(MemoryTaskService.ACTION_SCAN)},LinearLayout.LayoutParams(0,dp(48),1f))
        actions.addView(actionButton("ALL FILE ACCESS"){checkStoragePermission()},LinearLayout.LayoutParams(0,dp(48),1f).apply{leftMargin=dp(8)})
        root.addView(actions)
        status=TextView(this).apply{text="Ready to scan";textSize=13f;setTextColor(Color.LTGRAY);setPadding(0,dp(12),0,dp(8))};root.addView(status)
        count=TextView(this).apply{textSize=14f;setTextColor(Color.WHITE);setPadding(0,0,0,dp(10))};root.addView(count)
        categoryBar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        refreshCategoryButtons();root.addView(categoryBar)
        val tools=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        tools.addView(actionButton("EXTENSIONS"){extensionDialog()},LinearLayout.LayoutParams(0,dp(42),1f))
        tools.addView(actionButton(if(gridMode)"LIST VIEW" else "THUMBNAILS"){gridMode=!gridMode;render()},LinearLayout.LayoutParams(0,dp(42),1f).apply{leftMargin=dp(5)})
        tools.addView(actionButton("SORT"){sortDialog()},LinearLayout.LayoutParams(0,dp(42),1f).apply{leftMargin=dp(5)});root.addView(tools)
        val selection=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        selection.addView(actionButton("SELECT ALL"){visibleFiles().forEach{selectedPaths.add(it.path)};render()},LinearLayout.LayoutParams(0,dp(42),1f))
        selection.addView(actionButton("CLEAR"){selectedPaths.clear();render()},LinearLayout.LayoutParams(0,dp(42),1f).apply{leftMargin=dp(5)})
        selection.addView(actionButton("DELETE SELECTED"){confirmDelete()},LinearLayout.LayoutParams(0,dp(42),1f).apply{leftMargin=dp(5)});root.addView(selection)
        val scroll=ScrollView(this);content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(content)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);render()
    }
    private fun visibleFiles():List<StoredFile>{
        val allowed=if(selectedExtensions.isEmpty())StorageRepository.extensions[activeCategory].orEmpty().toSet() else selectedExtensions.toSet()
        val items=StorageRepository.files.filter{it.category==activeCategory&&it.extension in allowed}
        return when(sortMode){"Name"->items.sortedBy{it.name.lowercase(Locale.ROOT)};"Size"->items.sortedByDescending{it.size};else->items.sortedByDescending{it.modified}}
    }
    private fun extensionDialog(){
        val all=StorageRepository.extensions[activeCategory].orEmpty()
        val checked=BooleanArray(all.size){selectedExtensions.isEmpty()||all[it] in selectedExtensions}
        AlertDialog.Builder(this).setTitle("$activeCategory extensions").setMultiChoiceItems(all.map{".$it"}.toTypedArray(),checked){_,index,isChecked->if(isChecked)selectedExtensions.add(all[index])else selectedExtensions.remove(all[index])}
            .setNeutralButton("ALL"){_,_->selectedExtensions.clear();render()}.setPositiveButton("APPLY"){_,_->render()}.setNegativeButton("CANCEL",null).show()
    }
    private fun sortDialog(){AlertDialog.Builder(this).setTitle("Sort files").setItems(arrayOf("Date (newest first)","Size (largest first)","Name (A–Z)")){_,index->sortMode=arrayOf("Date","Size","Name")[index];render()}.show()}
    private fun confirmDelete(){
        val paths=selectedPaths.toList();if(paths.isEmpty()){toast("Select files first");return}
        AlertDialog.Builder(this).setTitle("Delete ${paths.size} files?").setMessage("This permanently deletes the selected files. This cannot be undone.")
            .setNegativeButton("CANCEL",null).setPositiveButton("DELETE"){_,_->if(!ensureNotificationPermission())return@setPositiveButton;val task=Intent(this,MemoryTaskService::class.java).setAction(MemoryTaskService.ACTION_DELETE).putStringArrayListExtra(MemoryTaskService.EXTRA_PATHS,ArrayList(paths));startServiceCompat(task);selectedPaths.clear();status.text="Preparing deletion…"}.show()
    }
    private fun startTask(action:String){
        if(Build.VERSION.SDK_INT>=30&&!Environment.isExternalStorageManager()){checkStoragePermission();return}
        if(!ensureNotificationPermission())return
        status.text="Starting…";startServiceCompat(Intent(this,MemoryTaskService::class.java).setAction(action))
    }
    private fun startServiceCompat(intent:Intent){try{if(Build.VERSION.SDK_INT>=26)startForegroundService(intent)else startService(intent)}catch(error:Exception){toast("Could not start task: ${error.message}")}}
    private fun render(){
        if(!::content.isInitialized)return
        content.removeAllViews()
        refreshCategoryButtons()
        val items=visibleFiles()
        val bytes=items.sumOf{it.size}
        count.text="${items.size} files • ${formatSize(bytes)}  |  ${selectedPaths.size} selected"
        if(StorageRepository.files.isEmpty()){
            content.addView(TextView(this).apply{
                text="No scan results yet.\n\nTap SCAN STORAGE to find supported files."
                textSize=15f;setTextColor(Color.LTGRAY);setPadding(dp(12),dp(30),dp(12),dp(30))
            })
            return
        }
        if(items.isEmpty()){
            content.addView(TextView(this).apply{text="No ${activeCategory.lowercase()} files match this selection.";setTextColor(Color.LTGRAY);setPadding(10,30,10,30)})
            return
        }
        val shown=items.take(displayLimit)
        shown.forEach{file->
            val chosen=file.path in selectedPaths
            val row=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
                setPadding(dp(8),dp(8),dp(8),dp(8))
                background=GradientDrawable().apply{
                    cornerRadius=dp(9).toFloat()
                    setColor(if(chosen)Color.rgb(31,78,57) else Color.rgb(24,29,35))
                    setStroke(dp(if(chosen)2 else 1),if(chosen)Color.rgb(113,238,168) else Color.rgb(43,51,59))
                }
            }
            val preview=ImageView(this).apply{
                scaleType=ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(12,18,20))
                setImageResource(android.R.drawable.ic_menu_report_image)
                imageTintList=android.content.res.ColorStateList.valueOf(Color.rgb(113,238,168))
                scaleType=ImageView.ScaleType.CENTER_INSIDE
                tag=file.path
                thumbnailExecutor.execute{
                    val bitmap=makeThumbnail(file)
                    if(bitmap!=null)post{if(tag==file.path)setImageBitmap(bitmap)}
                }
            }
            row.addView(preview,LinearLayout.LayoutParams(dp(if(gridMode)88 else 62),dp(if(gridMode)88 else 62)))
            val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,dp(5),0)}
            info.addView(TextView(this).apply{
                text=file.name;textSize=13f;setTextColor(Color.WHITE);maxLines=2
                ellipsize=android.text.TextUtils.TruncateAt.MIDDLE
            })
            info.addView(TextView(this).apply{
                text="Modified: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(java.util.Date(file.modified))}"
                textSize=10f;setTextColor(Color.LTGRAY);maxLines=1
            })
            info.addView(TextView(this).apply{
                text=".${file.extension}  •  ${formatSize(file.size)}"
                textSize=11f;setTextColor(Color.rgb(113,238,168));maxLines=1
            })
            row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(CheckBox(this).apply{
                buttonTintList=android.content.res.ColorStateList.valueOf(if(chosen)Color.rgb(113,238,168) else Color.LTGRAY)
                isChecked=chosen
                setOnCheckedChangeListener{_,checked->
                    if(checked)selectedPaths.add(file.path)else selectedPaths.remove(file.path)
                    render()
                }
            })
            row.setOnClickListener{openFile(file)}
            content.addView(row,LinearLayout.LayoutParams(-1,dp(92)).apply{bottomMargin=dp(7)})
        }
        if(shown.size<items.size){
            content.addView(actionButton("LOAD MORE (${items.size-shown.size} remaining)"){
                displayLimit=(displayLimit+200).coerceAtMost(items.size)
                render()
            },LinearLayout.LayoutParams(-1,dp(46)))
        }
    }
    private fun openFile(file:StoredFile){
        try{val uri=androidx.core.content.FileProvider.getUriForFile(this,"${packageName}.provider",File(file.path));val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)?:"*/*";startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}
        catch(_:Exception){toast("No compatible viewer found")}
    }
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    override fun onDestroy(){
        try{unregisterReceiver(receiver)}catch(_:Exception){}
        thumbnailExecutor.shutdownNow()
        super.onDestroy()
    }
}