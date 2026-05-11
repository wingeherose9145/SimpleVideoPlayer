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
    private var index = 0

    private val handler = Handler(Looper.getMainLooper())
    private var startX = 0f

    private val hideRunnable = Runnable {
        binding.playerView.hideController()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        loadVideos()

        // ⭐关键：强制控制器可用
        binding.playerView.useController = true

        // ⭐关键：自动显示进度条
        binding.playerView.showController()

        binding.playerView.setControllerVisibilityListener {
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, 2000)
        }

        // 横竖屏控制（稳定版）
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {

                val isLandscape = videoSize.width > videoSize.height

                requestedOrientation =
                    if (isLandscape)
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        })

        // 点击显示控制
        binding.playerView.setOnClickListener {
            binding.playerView.showController()
        }

        // 滑动进度
        binding.playerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startX = event.x
                MotionEvent.ACTION_UP -> {
                    val delta = event.x - startX
                    if (abs(delta) > 120) {
                        player.seekTo(
                            player.currentPosition + (delta * 100).toLong()
                        )
                    }
                }
            }
            false
        }

        binding.btnPlay.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
            binding.playerView.showController()
        }

        binding.btnNext.setOnClickListener {
            next()
            binding.playerView.showController()
        }

        binding.btnPrev.setOnClickListener {
            prev()
            binding.playerView.showController()
        }

        binding.btnImport.setOnClickListener {
            pick.launch(arrayOf("video/*"))
        }

        // ⭐关键：不要自动播放
    }

    private val pick =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->

            uris.forEach { uri ->
                val name = getName(uri) ?: return@forEach

                val dir = File(filesDir, "videos")
                if (!dir.exists()) dir.mkdirs()

                val file = File(dir, name)

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            loadVideos()
        }

    // ⭐核心修复函数（重点）
    private fun play(index: Int) {

        if (videos.isEmpty()) return

        this.index = index

        val file = videos[index]

        val item = MediaItem.fromUri(Uri.fromFile(file))

        player.stop()              // ⭐必须
        player.clearMediaItems()   // ⭐必须

        player.setMediaItem(item)
        player.prepare()
        player.play()

        binding.playerView.showController() // ⭐解决进度条问题
    }

    private fun next() {
        if (videos.isEmpty()) return
        index = (index + 1) % videos.size
        play(index)
    }

    private fun prev() {
        if (videos.isEmpty()) return
        index = if (index - 1 < 0) videos.size - 1 else index - 1
        play(index)
    }

    private fun loadVideos() {
        val dir = File(filesDir, "videos")
        if (!dir.exists()) dir.mkdirs()

        videos.clear()
        dir.listFiles()?.forEach { if (it.isFile) videos.add(it) }
    }

    private fun getName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return it.getString(i)
            }
        }
        return null
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
