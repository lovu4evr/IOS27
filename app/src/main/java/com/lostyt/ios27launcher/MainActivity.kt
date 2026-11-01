package com.lostyt.ios27launcher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: android.graphics.Bitmap?
)

data class FolderInfo(
    val id: String,
    val name: String,
    val apps: List<AppInfo>
)

sealed class IslandState {
    object Idle : IslandState()
    data class Notification(val title: String, val body: String) : IslandState()
    data class Media(val title: String, val artist: String, val isPlaying: Boolean) : IslandState()
    data class Timer(val time: String) : IslandState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IOS27LauncherTheme {
                LauncherScreen()
            }
        }
    }
}

@Composable
fun IOS27LauncherTheme(content: @Composable () -> Unit) {
    content()
}

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FolderInfo>>(emptyList()) }
    var homeItems by remember { mutableStateOf<List<Any>>(emptyList()) }
    var openFolder by remember { mutableStateOf<FolderInfo?>(null) }
    var islandState by remember { mutableStateOf<IslandState>(IslandState.Idle) }
    var islandExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val apps = loadInstalledApps(context)
        allApps = apps

        val social = apps.take(4)
        val utilities = apps.drop(4).take(5)
        val foldersList = listOf(
            FolderInfo("social", "Social", social),
            FolderInfo("utils", "Utilities", utilities)
        )
        folders = foldersList
        val remaining = apps.drop(9)
        homeItems = foldersList + remaining
    }

    // Demo island cycle
    LaunchedEffect(Unit) {
        while (true) {
            delay(8000)
            islandState = IslandState.Notification("Messages", "New message from Boss")
            islandExpanded = true
            delay(4500)
            islandExpanded = false
            delay(2000)
            islandState = IslandState.Media("Midnight City", "M83", true)
            islandExpanded = true
            delay(5000)
            islandExpanded = false
            delay(2000)
            islandState = IslandState.Timer("14:32")
            islandExpanded = true
            delay(4000)
            islandExpanded = false
            delay(2000)
            islandState = IslandState.Idle
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07070F),
                        Color(0xFF12121F),
                        Color(0xFF1A1A2A)
                    )
                )
            )
    ) {
        // Soft ambient glow behind island
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .size(180.dp, 60.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x336B8CFF),
                            Color.Transparent
                        )
                    )
                )
        )

        DynamicIsland(
            state = islandState,
            expanded = islandExpanded,
            onToggle = { islandExpanded = !islandExpanded },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .zIndex(10f)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(
                start = 22.dp,
                end = 22.dp,
                top = 110.dp,
                bottom = 150.dp
            ),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(homeItems) { item ->
                when (item) {
                    is AppInfo -> AppIcon(app = item) { launchApp(context, item) }
                    is FolderInfo -> FolderIcon(folder = item) { openFolder = item }
                }
            }
        }

        // Liquid Glass Dock
        LiquidGlassDock(
            apps = allApps.take(4),
            onAppClick = { app -> launchApp(context, app) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        )

        openFolder?.let { folder ->
            LiquidGlassFolderOverlay(
                folder = folder,
                onDismiss = { openFolder = null },
                onAppClick = { app ->
                    openFolder = null
                    launchApp(context, app)
                }
            )
        }
    }
}

@Composable
fun DynamicIsland(
    state: IslandState,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val width by animateDpAsState(
        targetValue = when {
            expanded && state is IslandState.Media -> 328.dp
            expanded && state is IslandState.Notification -> 304.dp
            expanded -> 264.dp
            else -> 128.dp
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 260f),
        label = "w"
    )
    val height by animateDpAsState(
        targetValue = when {
            expanded && state is IslandState.Media -> 80.dp
            expanded -> 70.dp
            else -> 37.dp
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 260f),
        label = "h"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(50),
                ambientColor = Color.Black.copy(0.55f),
                spotColor = Color(0xFF5B8CFF).copy(0.25f)
            )
            .clip(RoundedCornerShape(50))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1E).copy(alpha = 0.92f),
                        Color(0xFF0C0C0E).copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = RoundedCornerShape(50)
            )
            .clickable { onToggle() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        // Inner glass highlight
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 1.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    )
                )
        )

        AnimatedContent(
            targetState = Pair(expanded, state),
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(initialScale = 0.9f, animationSpec = tween(240))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 0.9f, animationSpec = tween(160)))
            },
            label = "island"
        ) { (isExpanded, currentState) ->
            if (!isExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2A2C))
                            .border(0.5.dp, Color.White.copy(0.12f), CircleShape)
                    )
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A3C))
                    )
                }
            } else {
                when (currentState) {
                    is IslandState.Idle -> {
                        Text(
                            "iOS 27",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is IslandState.Notification -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                currentState.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                currentState.body,
                                color = Color.White.copy(0.72f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    is IslandState.Media -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF7B6BB5), Color(0xFF3A3A5C))
                                        )
                                    )
                                    .border(0.5.dp, Color.White.copy(0.15f), RoundedCornerShape(12.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    currentState.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    currentState.artist,
                                    color = Color.White.copy(0.65f),
                                    fontSize = 12.sp
                                )
                            }
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(0.12f))
                                    .border(0.5.dp, Color.White.copy(0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (currentState.isPlaying) "❚❚" else "▶",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    is IslandState.Timer -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("⏱", fontSize = 16.sp)
                            Text(
                                currentState.time,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidGlassDock(
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .height(94.dp)
            .shadow(
                elevation = 28.dp,
                shape = RoundedCornerShape(34.dp),
                ambientColor = Color.Black.copy(0.5f),
                spotColor = Color(0xFF5B8CFF).copy(0.15f)
            )
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.10f)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(34.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Top glass shine
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(34.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 90f
                    )
                )
        )

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            apps.forEach { app ->
                AppIcon(app = app, onClick = { onAppClick(app) })
            }
            if (apps.size < 4) {
                repeat(4 - apps.size) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(0.06f))
                    )
                }
            }
        }
    }
}

@Composable
fun AppIcon(app: AppInfo, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 380f),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(12.dp, RoundedCornerShape(17.dp), ambientColor = Color.Black.copy(0.45f))
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFF1C1C1E))
                .border(
                    0.7.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(0.25f), Color.White.copy(0.04f))
                    ),
                    RoundedCornerShape(17.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(17.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(74.dp)
        )
    }
}


@Composable
fun FolderIcon(folder: FolderInfo, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFF30303A))
                .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▦",
                color = Color.White,
                fontSize = 34.sp
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = folder.name,
            color = Color.White,
            fontSize = 11.sp
        )
    }
}

@Composable
fun LiquidGlassFolderOverlay(
    folder: FolderInfo,
    onDismiss: () -> Unit,
    onAppClick: (AppInfo) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF191923))
                .padding(20.dp)
        ) {
            Text(
                folder.name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(folder.apps) { app ->
                    AppIcon(app = app) {
                        onAppClick(app)
                    }
                }
            }
        }
    }
}

fun loadInstalledApps(context: android.content.Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)

    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info ->
            AppInfo(
                label = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                activityName = info.activityInfo.name,
                icon = runCatching {
                    info.loadIcon(pm).toBitmap(96, 96)
                }.getOrNull()
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(context: android.content.Context, app: AppInfo) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setClassName(app.packageName, app.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(intent)
    }
}

 