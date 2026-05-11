package com.simple.player

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.simple.player.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer

    private val videos = mutableListOf<File>()
    private var currentIndex = 0

    private val pickVideos =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->

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

        binding.btnPlay.setOnClickListener {

            if (player.isPlaying) {

                player.pause()

            } else {

                player.play()
            }
        }

        binding.btnNext.setOnClickListener {
            nextVideo()
        }

        binding.btnPrev.setOnClickListener {
            prevVideo()
        }

        binding.btnImport.setOnClickListener {

            pickVideos.launch(
                arrayOf("video/*")
            )
        }
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

        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))

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

        val cursor = contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )

        cursor?.use {

            if (it.moveToFirst()) {

                val index =
                    it.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                if (index >= 0) {

                    name = it.getString(index)
                }
            }
        }

        return name
    }

    override fun onDestroy() {

        super.onDestroy()

        player.release()
    }
}
