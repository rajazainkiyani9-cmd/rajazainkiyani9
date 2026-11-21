package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.AURA_FILTERS
import com.example.ui.components.AuraImage
import com.example.ui.components.compressBitmapToBase64DataUri
import com.example.ui.components.compressUriToBase64DataUri
import com.example.ui.components.renderTextStoryDataUri
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraButtonGradient
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraMagenta
import com.example.ui.theme.AuraViolet
import kotlinx.coroutines.launch

@Composable
fun CreatePostScreen(
  initialMode: String = "Post",
  highQualityUpload: Boolean = true,
  onPublishPost: (
    caption: String,
    mediaType: String,
    imageData: String,
    filterName: String,
    location: String,
    audioTitle: String,
    onDone: () -> Unit,
  ) -> Unit,
  onPublishStory: (
    imageData: String,
    captionText: String,
    backgroundHex: String,
    onDone: () -> Unit,
  ) -> Unit,
  onPublishedNavigateHome: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val modes = remember { listOf("Post", "Reel", "Story") }
  var selectedMode by remember(initialMode) { mutableStateOf(initialMode) }

  val storyPalettes = remember {
    listOf(
      Pair(AuraViolet, AuraCoral),
      Pair(AuraBlue, AuraViolet),
      Pair(AuraAmber, AuraMagenta),
      Pair(Color(0xFF141E30), Color(0xFF243B55)),
    )
  }
  var selectedStoryPaletteIdx by remember { mutableIntStateOf(0) }

  var selectedImageData by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf("Original") }
  var caption by remember { mutableStateOf("") }
  var location by remember { mutableStateOf("") }
  var audioTitle by remember { mutableStateOf("Original Audio") }
  var isProcessingMedia by remember { mutableStateOf(false) }
  var isPublishing by remember { mutableStateOf(false) }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        isProcessingMedia = true
        scope.launch {
          val encoded = compressUriToBase64DataUri(context, uri, highQuality = highQualityUpload)
          if (encoded != null) {
            selectedImageData = encoded
          }
          isProcessingMedia = false
        }
      }
    }

  val cameraLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
      if (bitmap != null) {
        isProcessingMedia = true
        scope.launch {
          val encoded = compressBitmapToBase64DataUri(bitmap, highQuality = highQualityUpload)
          if (encoded != null) {
            selectedImageData = encoded
          }
          isProcessingMedia = false
        }
      }
    }

  val canPublish =
    !isPublishing &&
      !isProcessingMedia &&
      (selectedImageData.isNotBlank() || (selectedMode == "Story" && caption.isNotBlank()))

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
  ) {
    Text(
      text = "Create New",
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      modes.forEachIndexed { idx, mode ->
        SegmentedButton(
          selected = selectedMode == mode,
          onClick = { selectedMode = mode },
          shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
        ) {
          Text(mode)
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Live Media Preview
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(if (selectedMode == "Post") 1f else 0.8f)
          .clip(RoundedCornerShape(16.dp))
          .background(
            if (selectedMode == "Story" && selectedImageData.isBlank()) {
              val p = storyPalettes[selectedStoryPaletteIdx]
              Brush.linearGradient(listOf(p.first, p.second))
            } else {
              Brush.linearGradient(
                listOf(
                  MaterialTheme.colorScheme.surfaceVariant,
                  MaterialTheme.colorScheme.surfaceVariant,
                )
              )
            }
          ),
      contentAlignment = Alignment.Center,
    ) {
      if (selectedImageData.isNotBlank()) {
        AuraImage(
          imageData = selectedImageData,
          contentDescription = "Media preview",
          filterName = selectedFilter,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
          targetSizePx = 1440,
        )
      } else if (selectedMode == "Story") {
        Text(
          text = caption.ifBlank { "Pick a photo or type your Story text below" },
          color = Color.White,
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(24.dp),
        )
      } else {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.AddPhotoAlternate,
            contentDescription = null,
            tint = AuraCoral,
            modifier = Modifier.size(52.dp),
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Select a real photo from your Gallery or Camera",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (isProcessingMedia) {
        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp)) {
          CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = Color.White)
        }
      }

      if (selectedMode == "Story" && selectedImageData.isNotBlank() && caption.isNotBlank()) {
        Surface(
          color = Color.Black.copy(alpha = 0.55f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) {
          Text(
            text = caption,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Real Gallery Picker & Camera Capture
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = {
          photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("pick_photo_button"),
      ) {
        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = AuraCoral)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Gallery")
      }

      OutlinedButton(
        onClick = { cameraLauncher.launch(null) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("take_photo_button"),
      ) {
        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = AuraCoral)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Camera")
      }
    }

    if (selectedMode == "Story" && selectedImageData.isBlank()) {
      Spacer(modifier = Modifier.height(12.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Story Background:", style = MaterialTheme.typography.labelMedium)
        storyPalettes.forEachIndexed { idx, pair ->
          Box(
            modifier =
              Modifier.size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(pair.first, pair.second)))
                .border(
                  width = if (selectedStoryPaletteIdx == idx) 2.5.dp else 1.dp,
                  color =
                    if (selectedStoryPaletteIdx == idx) MaterialTheme.colorScheme.onBackground
                    else Color.Transparent,
                  shape = CircleShape,
                )
                .clickable { selectedStoryPaletteIdx = idx }
          )
        }
      }
    }

    if (selectedMode != "Story" && selectedImageData.isNotBlank()) {
      Spacer(modifier = Modifier.height(16.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Rounded.AutoAwesome,
          contentDescription = null,
          tint = AuraCoral,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Filters",
          style = MaterialTheme.typography.titleSmall,
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(AURA_FILTERS, key = { it }) { filter ->
          val isSelected = selectedFilter == filter
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { selectedFilter = filter },
          ) {
            Box(
              modifier =
                Modifier.size(64.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .border(
                    width = if (isSelected) 2.5.dp else 0.dp,
                    color = if (isSelected) AuraCoral else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                  )
            ) {
              AuraImage(
                imageData = selectedImageData,
                contentDescription = filter,
                filterName = filter,
                modifier = Modifier.fillMaxSize(),
                targetSizePx = 256,
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = filter,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color =
                if (isSelected) AuraCoral else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
      value = caption,
      onValueChange = { caption = it },
      label = {
        Text(if (selectedMode == "Story") "Story caption text" else "Write a caption... (#tags)")
      },
      minLines = 2,
      maxLines = 4,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().testTag("create_caption_input"),
    )

    if (selectedMode != "Story") {
      Spacer(modifier = Modifier.height(10.dp))
      OutlinedTextField(
        value = location,
        onValueChange = { location = it },
        label = { Text("Add location (optional)") },
        leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (selectedMode == "Reel") {
      Spacer(modifier = Modifier.height(10.dp))
      OutlinedTextField(
        value = audioTitle,
        onValueChange = { audioTitle = it },
        label = { Text("Audio track") },
        leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Button(
      onClick = {
        isPublishing = true
        if (selectedMode == "Story") {
          scope.launch {
            val finalStoryImage =
              selectedImageData.ifBlank {
                val pair = storyPalettes[selectedStoryPaletteIdx]
                renderTextStoryDataUri(pair.first.toArgb(), pair.second.toArgb())
              }
            onPublishStory(finalStoryImage, caption, "#110D1C") {
              isPublishing = false
              caption = ""
              selectedImageData = ""
              onPublishedNavigateHome()
            }
          }
        } else {
          onPublishPost(
            caption,
            if (selectedMode == "Reel") "reel" else "photo",
            selectedImageData,
            selectedFilter,
            location,
            if (selectedMode == "Reel") audioTitle else "",
          ) {
            isPublishing = false
            caption = ""
            location = ""
            selectedImageData = ""
            onPublishedNavigateHome()
          }
        }
      },
      enabled = canPublish,
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("publish_button"),
      shape = RoundedCornerShape(14.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
      contentPadding = PaddingValues(),
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              if (canPublish) AuraButtonGradient
              else Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f))),
              RoundedCornerShape(14.dp),
            ),
        contentAlignment = Alignment.Center,
      ) {
        if (isPublishing) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Color.White,
            strokeWidth = 2.5.dp,
          )
        } else {
          Text(
            text = "Share $selectedMode",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}
