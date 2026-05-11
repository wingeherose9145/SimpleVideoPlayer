package com.simple.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.simple.player.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer

    private val videos = mutableListOf<File>()
    private var currentIndex = 0

    private val handler = Handler(Looper.getMainLooper())

    private var startX = 0f

    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    private val pickVideos =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->

            uris.forEach { uri ->

                val name = getFileName(uri) ?: return@forEach

                val dir = File(filesDir, "videos")
                if (!dir.exists()) dir.mkdirs()

                val dest = File(dir, name)

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            loadVideos()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        loadVideos()

        hideControls()

        // 横竖屏自动适配
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                requestedOrientation =
                    if (videoSize.width > videoSize.height)
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        })

        // 点击显示控制层
        binding.playerView.setOnClickListener {
            showControls()
        }

        // 手势快进
        binding.playerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startX = event.x
                MotionEvent.ACTION_UP -> {
                    val delta = event.x - startX
                    if (abs(delta) > 150) {
                        val seek = player.currentPosition + (delta * 120)
                        player.seekTo(seek.toLong())
                    }
                }
            }
            false
        }

        // 播放
        binding.btnPlay.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
            showControls()
        }

        // 下一个
        binding.btnNext.setOnClickListener {
            nextVideo()
            showControls()
        }

        // 上一个
        binding.btnPrev.setOnClickListener {
            prevVideo()
            showControls()
        }

        // 导入
        binding.btnImport.setOnClickListener {
            pickVideos.launch(arrayOf("video/*"))
            showControls()
        }
    }

    private fun showControls() {

        binding.btnPlay.visibility = View.VISIBLE
        binding.btnNext.visibility = View.VISIBLE
        binding.btnPrev.visibility = View.VISIBLE
        binding.btnImport.visibility = View.VISIBLE

        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 1500)
    }

    private fun hideControls() {

        binding.btnPlay.visibility = View.GONE
        binding.btnNext.visibility = View.GONE
        binding.btnPrev.visibility = View.GONE
        binding.btnImport.visibility = View.GONE
    }

    private fun loadVideos() {

        val dir = File(filesDir, "videos")
        if (!dir.exists()) dir.mkdirs()

        videos.clear()
        dir.listFiles()?.forEach { if (it.isFile) videos.add(it) }
    }

    private fun playVideo(file: File) {

        val item = MediaItem.fromUri(Uri.fromFile(file))

        player.stop()
        player.clearMediaItems()

        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    private fun nextVideo() {
        if (videos.isEmpty()) return
        currentIndex = (currentIndex + 1) % videos.size
        playVideo(videos[currentIndex])
    }

    private fun prevVideo() {
        if (videos.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) videos.size - 1 else currentIndex - 1
        playVideo(videos[currentIndex])
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = it.getString(index)
            }
        }
        return name
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
