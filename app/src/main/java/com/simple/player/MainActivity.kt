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
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            uris.forEach { uri ->

                val name = getFileName(uri)

                if (name != null) {

                    val dir = File(filesDir, "videos")

                    if (!dir.exists()) {
                        dir.mkdirs()
                    }

                    val destFile = File(dir, name)

                    contentResolver.openInputStream(uri)?.use { input ->

                        FileOutputStream(destFile).use { output ->

                            input.copyTo(output)
                        }
                    }
                }
            }

            loadVideos()

            if (videos.isNotEmpty()) {

                currentIndex = 0

                playVideo(videos[currentIndex])
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        player = ExoPlayer.Builder(this).build()

        binding.playerView.player = player

        loadVideos()

        if (videos.isNotEmpty()) {

            playVideo(videos[currentIndex])
        }

        hideControls()

        player.addListener(object : Player.Listener {

            override fun onVideoSizeChanged(videoSize: VideoSize) {

                val width = videoSize.width

                val height = videoSize.height

                if (width > height) {

                    requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

                } else {

                    requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        })

        binding.playerView.setOnClickListener {

            showControls()
        }

        binding.playerView.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    startX = event.x
                }

                MotionEvent.ACTION_UP -> {

                    val deltaX = event.x - startX

                    if (abs(deltaX) > 150) {

                        val seekPosition =
                            player.currentPosition + (deltaX * 100)

                        player.seekTo(seekPosition.toLong())
                    }
                }
            }

            false
        }

        binding.btnPlay.setOnClickListener {

            if (player.isPlaying) {

                player.pause()

            } else {

                player.play()
            }

            showControls()
        }

        binding.btnNext.setOnClickListener {

            nextVideo()

            showControls()
        }

        binding.btnPrev.setOnClickListener {

            prevVideo()

            showControls()
        }

        binding.btnImport.setOnClickListener {

            pickVideos.launch(
                arrayOf("video/*")
            )

            showControls()
        }
    }

    private fun showControls() {

        binding.btnPlay.visibility = View.VISIBLE
        binding.btnNext.visibility = View.VISIBLE
        binding.btnPrev.visibility = View.VISIBLE
        binding.btnImport.visibility = View.VISIBLE

        handler.removeCallbacks(hideControlsRunnable)

        handler.postDelayed(hideControlsRunnable, 2000)
    }

    private fun hideControls() {

        binding.btnPlay.visibility = View.GONE
        binding.btnNext.visibility = View.GONE
        binding.btnPrev.visibility = View.GONE
        binding.btnImport.visibility = View.GONE
    }

    private fun loadVideos() {

        val dir = File(filesDir, "videos")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        videos.clear()

        dir.listFiles()?.forEach {

            if (it.isFile) {

                videos.add(it)
            }
        }
    }

    private fun playVideo(file: File) {

        val mediaItem = MediaItem.fromUri(
            Uri.fromFile(file)
        )

        player.setMediaItem(mediaItem)

        player.prepare()

        player.play()
    }

    private fun nextVideo() {

        if (videos.isEmpty()) return

        currentIndex++

        if (currentIndex >= videos.size) {

            currentIndex = 0
        }

        playVideo(videos[currentIndex])
    }

    private fun prevVideo() {

        if (videos.isEmpty()) return

        currentIndex--

        if (currentIndex < 0) {

            currentIndex = videos.size - 1
        }

        playVideo(videos[currentIndex])
    }

    private fun getFileName(uri: Uri): String? {

        var name: String? = null

        val cursor =
            contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

        cursor?.use {

            if (it.moveToFirst()) {

                val index =
                    it.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (index >= 0) {

                    name = it.getString(index)
                }
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
