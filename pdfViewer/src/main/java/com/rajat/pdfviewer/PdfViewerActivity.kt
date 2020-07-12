package com.rajat.pdfviewer

import android.Manifest.permission
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.rajat.pdfviewer.databinding.ActivityPdfViewerBinding

/**
 * Created by Rajat on 11,July,2020
 */

class PdfViewerActivity : AppCompatActivity() {

    private var permissionGranted: Boolean? = false
    private lateinit var binding: ActivityPdfViewerBinding
    private var menuItem: MenuItem? = null

    companion object {
        const val FILE_URL = "pdf_file_url"
        const val FILE_DIRECTORY = "pdf_file_directory"
        const val FILE_TITLE = "pdf_file_title"
        const val ENABLE_FILE_DOWNLOAD = "enable_download"
        const val IS_GOOGLE_ENGINE = "is_google_engine"
        const val PERMISSION_CODE = 4040
        var engine = PdfEngine.INTERNAL
        var enableDownload = true

        fun buildIntent(
            context: Context?,
            pdfUrl: String?,
            isGoogleEngine: Boolean?,
            pdfTitle: String?,
            directoryName: String?,
            enableDownload: Boolean = true
        ): Intent {
            val intent = Intent(context, PdfViewerActivity::class.java)
            intent.putExtra(FILE_URL, pdfUrl)
            intent.putExtra(FILE_TITLE, pdfTitle)
            intent.putExtra(FILE_DIRECTORY, directoryName)
            intent.putExtra(ENABLE_FILE_DOWNLOAD, enableDownload)
            intent.putExtra(IS_GOOGLE_ENGINE, isGoogleEngine)
            return intent
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Set binding to view
        binding = DataBindingUtil.setContentView(this, R.layout.activity_pdf_viewer)

        setUpToolbar(
            intent.extras!!.getString(
                FILE_TITLE,
                "PDF"
            )
        )

        enableDownload = intent.extras!!.getBoolean(
            ENABLE_FILE_DOWNLOAD,
            true
        )

        engine = if (intent.extras!!.getBoolean(
                IS_GOOGLE_ENGINE,
                true
            )
        ) PdfEngine.GOOGLE else PdfEngine.INTERNAL

        if (intent.extras!!.containsKey(FILE_URL)) {
            val fileUrl = intent.extras!!.getString(FILE_URL)
            loadFileFromNetwork(fileUrl)
        }


    }

    private fun setUpToolbar(toolbarTitle: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = toolbarTitle
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menuItem = menu?.findItem(R.id.download)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menuItem?.isVisible = enableDownload
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.download) checkPermission(PERMISSION_CODE)
        if (item.itemId == android.R.id.home) {
            finish() // close this activity and return to preview activity (if there is any)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadFileFromNetwork(fileUrl: String?) {
        initPdfViewer(
            fileUrl,
            engine
        )
    }

    private fun initPdfViewer(fileUrl: String?, engine: PdfEngine) {
        if (TextUtils.isEmpty(fileUrl)) onPdfError()

        //Initiating PDf Viewer with URL
        try {
            binding.pdfView.initWithUrl(
                fileUrl!!,
                PdfQuality.ENHANCED,
                engine
            )
        } catch (e: Exception) {
            onPdfError()
        }

        //Check permission for download
        checkPermissionOnInit()

        binding.pdfView.statusListener = object : PdfRendererView.StatusCallBack {
            override fun onDownloadStart() {
                true.showProgressBar()
            }

            override fun onDownloadProgress(
                progress: Int,
                downloadedBytes: Long,
                totalBytes: Long?
            ) {
                //Download is in progress
            }

            override fun onDownloadSuccess() {
                false.showProgressBar()
            }

            override fun onError(error: Throwable) {
                onPdfError()
            }

            override fun onPageChanged(currentPage: Int, totalPage: Int) {
                //Page change. Not require
            }

        }

    }

    private fun checkPermissionOnInit() {
        if (ContextCompat.checkSelfPermission(
                this,
                permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            permissionGranted = true
        }
    }

    private fun onPdfError() {
        Toast.makeText(this, "Pdf has been corrupted", Toast.LENGTH_SHORT).show()
        true.showProgressBar()
        finish()
    }

    private fun Boolean.showProgressBar() {
        binding.progressBar.visibility = if (this) View.VISIBLE else GONE
    }

    private var onComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Toast.makeText(
                context,
                "File is Downloaded Successfully",
                Toast.LENGTH_SHORT
            ).show()
            context?.unregisterReceiver(this)
        }
    }

    private fun downloadPdf() {
        try {
            val directoryName = intent.getStringExtra(FILE_DIRECTORY)
            val fileName = intent.getStringExtra(FILE_TITLE)
            val fileUrl = intent.getStringExtra(FILE_URL)
            val filePath =
                if (TextUtils.isEmpty(directoryName)) "/$fileName.pdf" else "/$directoryName/$fileName.pdf"

            try {
                val downloadUrl = Uri.parse(fileUrl)
                val downloadManger = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
                val request = DownloadManager.Request(downloadUrl)
                request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                            DownloadManager.Request.NETWORK_MOBILE
                )
                request.setAllowedOverRoaming(true)
                request.setTitle(fileName)
                request.setDescription("Downloading $fileName")
                request.setVisibleInDownloadsUi(true)
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    filePath
                )
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                registerReceiver(
                    onComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
                if (permissionGranted!!) downloadManger!!.enqueue(request)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Unable to download file",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {
            Log.e("Error", e.toString())
        }
    }

    private fun checkPermission(requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this, permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(permission.WRITE_EXTERNAL_STORAGE),
                requestCode
            )
        } else {
            permissionGranted = true
            downloadPdf()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            permissionGranted = true
            downloadPdf()
        }
    }

}