package com.burhanrashid52.photoediting

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.burhanrashid52.photoediting.EmojiBSFragment.EmojiListener
import com.burhanrashid52.photoediting.StickerBSFragment.StickerListener
import com.burhanrashid52.photoediting.base.BaseActivity
import com.burhanrashid52.photoediting.filters.FilterListener
import com.burhanrashid52.photoediting.filters.FilterViewAdapter
import com.burhanrashid52.photoediting.tools.EditingToolsAdapter
import com.burhanrashid52.photoediting.tools.ToolType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ja.burhanrashid52.photoeditor.*
import ja.burhanrashid52.photoeditor.PhotoEditor.OnSaveListener
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import java.io.File
import java.io.IOException

class EditImageActivity : BaseActivity(), OnPhotoEditorListener, View.OnClickListener,
    PropertiesBSFragment.Properties, ShapeBSFragment.Properties, EmojiListener, StickerListener,
    FilterListener {

    val mPhotoEditor: PhotoEditor by lazy {
        PhotoEditor.Builder(this@EditImageActivity, mPhotoEditorView)
            .setPinchTextScalable(
                intent.getBooleanExtra(
                    PINCH_TEXT_SCALABLE_INTENT_KEY,
                    true
                )
            ) // set flag to make text scalable when pinch
            //.setDefaultTextTypeface(mTextRobotoTf)
            //.setDefaultEmojiTypeface(mEmojiTypeFace)
            .build() // build photo editor sdk
            .apply { setOnPhotoEditorListener(this@EditImageActivity) }
    }
    private val mPhotoEditorView: PhotoEditorView by lazy { findViewById(R.id.photoEditorView) }
    private val mShapeBSFragment by lazy {
        ShapeBSFragment().apply {
            setPropertiesChangeListener(
                this@EditImageActivity
            )
        }
    }
    private var mShapeBuilder: ShapeBuilder? = null
    private val mEmojiBSFragment by lazy { EmojiBSFragment().apply { setEmojiListener(this@EditImageActivity) } }
    private val mStickerBSFragment by lazy { StickerBSFragment().apply { setStickerListener(this@EditImageActivity) } }
    private val mTxtCurrentTool: TextView by lazy { findViewById(R.id.txtCurrentTool) }
    private val mRvTools: RecyclerView by lazy { findViewById(R.id.rvConstraintTools) }
    private val mRvFilters: RecyclerView by lazy { findViewById(R.id.rvFilterView) }
    private val mEditingToolsAdapter by lazy { EditingToolsAdapter(::onToolSelected) }
    private val mFilterViewAdapter by lazy { FilterViewAdapter(this) }
    private val mRootView: ConstraintLayout by lazy { findViewById(R.id.rootView) }
    private val mConstraintSet = ConstraintSet()
    private var mIsFilterVisible = false
    private val photoUri: Uri by lazy {
        FileProvider.getUriForFile(
            this,
            FILE_PROVIDER_AUTHORITY,
            File(filesDir, IMAGE_FILE_NAME)
        )
    }
    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { saving ->
            if (saving) {
                setImageScene(photoUri)
            } else {
                showSnackbar(getString(R.string.msg_camera_problem))
            }
        }

    private val takeImageFromGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            setImageScene(
                it ?: run {
                    showSnackbar(getString(R.string.gallery_image_load_problem))
                    return@registerForActivityResult
                }
            )
        }

    private val Uri.bitmap
        get() = if (Build.VERSION.SDK_INT < 28) {
            MediaStore.Images.Media.getBitmap(contentResolver, this)
        } else {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(
                    contentResolver,
                    this
                )
            ) { decoder, _, _ -> decoder.isMutableRequired = true }
        }

    @VisibleForTesting
    var mSaveImageUri: Uri? = null

    private val mSaveFileHelper: FileSaveHelper by lazy { FileSaveHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        makeFullScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_image)
        initViews()
        handleIntentImage(mPhotoEditorView.source)

        mRvTools.adapter = mEditingToolsAdapter
        mRvFilters.adapter = mFilterViewAdapter

        //Set Image Dynamically
        mPhotoEditorView.source.setImageResource(R.drawable.paris_tower)
    }

    private fun setImageScene(uri: Uri) {
        mPhotoEditor.clearAllViews()
        mPhotoEditorView.source.setImageBitmap(uri.bitmap)
    }

    private fun handleIntentImage(source: ImageView?) {
        if (intent == null) {
            return
        }

        when (intent.action) {
            Intent.ACTION_EDIT, ACTION_NEXTGEN_EDIT -> {
                try {
                    source?.setImageBitmap(intent.data?.bitmap)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            else -> {
                val intentType = intent.type
                if (intentType != null && intentType.startsWith("image/")) {
                    val imageUri = intent.data
                    if (imageUri != null) {
                        source?.setImageURI(imageUri)
                    }
                }
            }
        }
    }

    private fun initViews() {
        listOf(
            R.id.imgUndo,
            R.id.imgRedo,
            R.id.imgCamera,
            R.id.imgGallery,
            R.id.imgSave,
            R.id.imgClose,
            R.id.imgShare
        )
            .map<Int, View>(::findViewById)
            .forEach { it.setOnClickListener(this) }
    }

    override fun onEditTextChangeListener(rootView: View?, text: String?, colorCode: Int) {
        val textEditorDialogFragment =
            TextEditorDialogFragment.show(this, text.toString(), colorCode)
        textEditorDialogFragment.setOnTextEditorListener(object :
            TextEditorDialogFragment.TextEditorListener {
            override fun onDone(inputText: String?, colorCode: Int) {
                val styleBuilder = TextStyleBuilder()
                styleBuilder.withTextColor(colorCode)
                if (rootView != null) {
                    mPhotoEditor.editText(rootView, inputText, styleBuilder)
                }
                mTxtCurrentTool.setText(R.string.label_text)
            }
        })
    }

    override fun onAddViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
        Log.d(
            TAG,
            "onAddViewListener() called with: viewType = [$viewType], numberOfAddedViews = [$numberOfAddedViews]"
        )
    }

    override fun onRemoveViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
        Log.d(
            TAG,
            "onRemoveViewListener() called with: viewType = [$viewType], numberOfAddedViews = [$numberOfAddedViews]"
        )
    }

    override fun onStartViewChangeListener(viewType: ViewType?) {
        Log.d(TAG, "onStartViewChangeListener() called with: viewType = [$viewType]")
    }

    override fun onStopViewChangeListener(viewType: ViewType?) {
        Log.d(TAG, "onStopViewChangeListener() called with: viewType = [$viewType]")
    }

    override fun onTouchSourceImage(event: MotionEvent?) {
        Log.d(TAG, "onTouchView() called with: event = [$event]")
    }

    @SuppressLint("NonConstantResourceId", "MissingPermission")
    override fun onClick(view: View) {
        when (view.id) {
            R.id.imgUndo -> mPhotoEditor.undo()
            R.id.imgRedo -> mPhotoEditor.redo()
            R.id.imgSave -> saveImage()
            R.id.imgClose -> onBackPressed()
            R.id.imgShare -> shareImage()
            R.id.imgCamera -> takePhoto.launch(photoUri)
            R.id.imgGallery -> takeImageFromGallery.launch("image/*")
        }
    }

    private fun shareImage() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "image/*"
        val saveImageUri = mSaveImageUri
        if (saveImageUri == null) {
            showSnackbar(getString(R.string.msg_save_image_to_share))
            return
        }
        intent.putExtra(Intent.EXTRA_STREAM, buildFileProviderUri(saveImageUri))
        startActivity(Intent.createChooser(intent, getString(R.string.msg_share_image)))
    }

    private fun buildFileProviderUri(uri: Uri): Uri = if (FileSaveHelper.isSdkHigherThan28()) {
        uri
    } else {
        FileProvider.getUriForFile(
            this,
            FILE_PROVIDER_AUTHORITY,
            uri.path?.let(::File) ?: throw IllegalArgumentException("URI Path Expected")
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
    private fun saveImage() {
        val fileName = System.currentTimeMillis().toString() + ".png"
        val hasStoragePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasStoragePermission || FileSaveHelper.isSdkHigherThan28()) {
            showLoading("Saving...")
            mSaveFileHelper.createFile(fileName, object : FileSaveHelper.OnFileCreateResult {

                @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
                override fun onFileCreateResult(
                    created: Boolean,
                    filePath: String?,
                    error: String?,
                    uri: Uri?
                ) {
                    if (created && filePath != null) {
                        val saveSettings = SaveSettings.Builder()
                            .setClearViewsEnabled(true)
                            .setTransparencyEnabled(true)
                            .build()

                        mPhotoEditor.saveAsFile(
                            filePath,
                            saveSettings,
                            object : OnSaveListener {
                                override fun onSuccess(imagePath: String) {
                                    mSaveFileHelper.notifyThatFileIsNowPubliclyAvailable(
                                        contentResolver
                                    )
                                    hideLoading()
                                    showSnackbar("Image Saved Successfully")
                                    mSaveImageUri = uri
                                    mPhotoEditorView.source.setImageURI(mSaveImageUri)
                                }

                                override fun onFailure(exception: Exception) {
                                    hideLoading()
                                    showSnackbar("Failed to save Image")
                                }
                            })
                    } else {
                        hideLoading()
                        error?.let { showSnackbar(error) }
                    }
                }
            })
        } else {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    override fun onColorChanged(colorCode: Int) {
        mPhotoEditor.setShape(mShapeBuilder?.withShapeColor(colorCode))
        mTxtCurrentTool.setText(R.string.label_brush)
    }

    override fun onOpacityChanged(opacity: Int) {
        mPhotoEditor.setShape(mShapeBuilder?.withShapeOpacity(opacity))
        mTxtCurrentTool.setText(R.string.label_brush)
    }

    override fun onShapeSizeChanged(shapeSize: Int) {
        mPhotoEditor.setShape(mShapeBuilder?.withShapeSize(shapeSize.toFloat()))
        mTxtCurrentTool.setText(R.string.label_brush)
    }

    override fun onShapePicked(shapeType: ShapeType?) {
        mPhotoEditor.setShape(mShapeBuilder?.withShapeType(shapeType))
    }

    override fun onEmojiClick(emojiUnicode: String?) {
        mPhotoEditor.addEmoji(emojiUnicode)
        mTxtCurrentTool.setText(R.string.label_emoji)
    }

    override fun onStickerClick(bitmap: Bitmap?) {
        mPhotoEditor.addImage(bitmap)
        mTxtCurrentTool.setText(R.string.label_sticker)
    }

    @SuppressLint("MissingPermission")
    override fun isPermissionGranted(isGranted: Boolean, permission: String?) {
        if (isGranted) {
            saveImage()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showSaveDialog() {
        AlertDialog.Builder(this).apply {
            setMessage(getString(R.string.msg_save_image))
            setPositiveButton("Save") { _: DialogInterface?, _: Int -> saveImage() }
            setNegativeButton("Cancel") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            setNeutralButton("Discard") { _: DialogInterface?, _: Int -> finish() }
        }.create().show()
    }

    override fun onFilterSelected(photoFilter: PhotoFilter?) {
        mPhotoEditor.setFilterEffect(photoFilter)
    }

    private fun onToolSelected(toolType: ToolType) {
        when (toolType) {
            ToolType.SHAPE -> {
                mPhotoEditor.setBrushDrawingMode(true)
                mShapeBuilder = ShapeBuilder()
                mPhotoEditor.setShape(mShapeBuilder)
                mTxtCurrentTool.setText(R.string.label_shape)
                showBottomSheetDialogFragment(mShapeBSFragment)
            }
            ToolType.TEXT -> {
                val textEditorDialogFragment = TextEditorDialogFragment.show(this)
                textEditorDialogFragment.setOnTextEditorListener(object :
                    TextEditorDialogFragment.TextEditorListener {
                    override fun onDone(inputText: String?, colorCode: Int) {
                        val styleBuilder = TextStyleBuilder().apply {
                            withTextColor(colorCode)
                        }
                        mPhotoEditor.addText(inputText, styleBuilder)
                        mTxtCurrentTool.setText(R.string.label_text)
                    }
                })
            }
            ToolType.ERASER -> {
                mPhotoEditor.brushEraser()
                mTxtCurrentTool.setText(R.string.label_eraser_mode)
            }
            ToolType.FILTER -> {
                mTxtCurrentTool.setText(R.string.label_filter)
                showFilter(true)
            }
            ToolType.EMOJI -> showBottomSheetDialogFragment(mEmojiBSFragment)
            ToolType.STICKER -> showBottomSheetDialogFragment(mStickerBSFragment)
        }
    }

    private fun showBottomSheetDialogFragment(fragment: BottomSheetDialogFragment?) {
        if (fragment == null || fragment.isAdded) {
            return
        }
        fragment.show(supportFragmentManager, fragment.tag)
    }

    private fun showFilter(isVisible: Boolean) {
        mIsFilterVisible = isVisible
        mConstraintSet.clone(mRootView)
        val rvFilterId: Int = mRvFilters.id
        if (isVisible) {
            mConstraintSet.clear(rvFilterId, ConstraintSet.START)
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.START,
                ConstraintSet.PARENT_ID, ConstraintSet.START
            )
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.END,
                ConstraintSet.PARENT_ID, ConstraintSet.END
            )
        } else {
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.START,
                ConstraintSet.PARENT_ID, ConstraintSet.END
            )
            mConstraintSet.clear(rvFilterId, ConstraintSet.END)
        }
        val changeBounds = ChangeBounds().apply {
            duration = 350
            interpolator = AnticipateOvershootInterpolator(1.0f)
        }
        mRootView.let { TransitionManager.beginDelayedTransition(it, changeBounds) }
        mConstraintSet.applyTo(mRootView)
    }

    override fun onBackPressed() {
        val isCacheEmpty = mPhotoEditor.isCacheEmpty

        if (mIsFilterVisible) {
            showFilter(false)
            mTxtCurrentTool.setText(R.string.app_name)
        } else if (!isCacheEmpty) {
            showSaveDialog()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private val TAG = EditImageActivity::class.java.simpleName
        const val FILE_PROVIDER_AUTHORITY = "com.burhanrashid52.photoediting.fileprovider"
        const val ACTION_NEXTGEN_EDIT = "action_nextgen_edit"
        const val PINCH_TEXT_SCALABLE_INTENT_KEY = "PINCH_TEXT_SCALABLE"
        private const val IMAGE_FILE_NAME = "picFromCamera"
    }
}