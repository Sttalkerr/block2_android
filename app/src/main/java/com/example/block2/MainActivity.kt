package com.example.block2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.block2.diary.DiaryViewModel
import com.example.block2.gallery.GalleryViewModel
import com.example.block2.ui.theme.Block2Theme

class MainActivity : ComponentActivity() {
    private val diaryViewModel by viewModels<DiaryViewModel>()
    private val galleryViewModel by viewModels<GalleryViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Block2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Block2App(
                        diaryViewModel = diaryViewModel,
                        galleryViewModel = galleryViewModel
                    )
                }
            }
        }
    }
}
