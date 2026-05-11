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
import androidx.media3.ui.PlayerView
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

        binding.playerView.useController = true

        loadVideos()

        // ⭐ 关键：统一控制器监听（解决重载冲突）
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                handler.removeCallbacks(hideRunnable)
                handler.postDelayed(hideRunnable, 1800)
            }
        )

        // 横竖屏适配（稳定版）
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
            binding.playerView.showController()
        }

        // 手势快进
        binding.playerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startX = event.x
                MotionEvent.ACTION_UP -> {
                    val delta = event.x - startX
                    if (abs(delta) > 120) {
                        player.seekTo(
                            player.currentPosition + (delta * 120).toLong()
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
        }

        binding.btnPrev.setOnClickListener {
            prev()
        }

        binding.btnImport.setOnClickListener {
            pick.launch(arrayOf("video/*"))
        }
    }

    // ⭐ 核心播放函数（稳定版）
    private fun playAt(i: Int) {

        if (videos.isEmpty()) return

        index = i

        val file = videos[index]

        val item = MediaItem.fromUri(Uri.fromFile(file))

        player.stop()
        player.clearMediaItems()

        player.setMediaItem(item)
        player.prepare()
        player.play()

        binding.playerView.showController()
    }

    private fun next() {
        if (videos.isEmpty()) return
        index = (index + 1) % videos.size
        playAt(index)
    }

    private fun prev() {
        if (videos.isEmpty()) return
        index = if (index - 1 < 0) videos.size - 1 else index - 1
        playAt(index)
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
