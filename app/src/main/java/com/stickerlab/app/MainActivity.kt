package com.stickerlab.app

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.*
import com.stickerlab.app.data.StickerRepository
import com.stickerlab.app.model.StickerPack
import com.stickerlab.app.ui.screens.EditorScreen
import com.stickerlab.app.ui.screens.HomeScreen
import com.stickerlab.app.ui.theme.StickerLabTheme
import com.stickerlab.app.whatsapp.WhatsAppBridge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = StickerRepository(applicationContext)

        setContent {
            StickerLabTheme {
                var packs by remember { mutableStateOf(repository.getPacks()) }
                var editingPack by remember { mutableStateOf<StickerPack?>(null) }

                val waLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        Toast.makeText(this, "Added to WhatsApp!", Toast.LENGTH_SHORT).show()
                        packs = repository.getPacks()
                    } else {
                        val err = result.data?.getStringExtra("validation_error")
                        Toast.makeText(
                            this,
                            err ?: "Cancelled or WhatsApp not installed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                AnimatedContent(targetState = editingPack, label = "screen_nav") { currentPack ->
                    if (currentPack == null) {
                        HomeScreen(
                            packs = packs,
                            repository = repository,
                            onCreatePack = { name ->
                                repository.createPack(name)
                                packs = repository.getPacks()
                            },
                            onDeletePack = { id ->
                                repository.deletePack(id)
                                packs = repository.getPacks()
                            },
                            onOpenEditor = { pack ->
                                editingPack = pack
                            },
                            onAddToWhatsApp = { pack ->
                                try {
                                    waLauncher.launch(WhatsAppBridge.createAddPackIntent(pack))
                                } catch (_: Exception) {
                                    Toast.makeText(this@MainActivity, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        EditorScreen(
                            pack = currentPack,
                            repository = repository,
                            onBack = { editingPack = null },
                            onSaved = {
                                packs = repository.getPacks()
                                editingPack = null
                                Toast.makeText(this@MainActivity, "Saved to ${currentPack.name}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
