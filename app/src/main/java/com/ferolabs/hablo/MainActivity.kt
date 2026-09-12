package com.ferolabs.hablo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private var speaker: Speaker? = null
    private var listener: Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sp = Speaker(this)
        speaker = sp
        val li = Listener(this)
        listener = li
        val store = Store(this)
        Course.load(this)

        setContent {
            HabloApp(speaker = sp, listener = li, store = store)
        }
    }

    override fun onStop() {
        speaker?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        listener?.release()
        listener = null
        super.onDestroy()
    }
}

private sealed class Route {
    data object PickTeacher : Route()
    data object Home : Route()
    data class Running(val lessonId: String) : Route()
    data object Settings : Route()
    data object Pronunciation : Route()
}

@Composable
fun HabloApp(speaker: Speaker, listener: Listener, store: Store) {

    var teacherId by remember { mutableStateOf(store.teacherId) }
    var speechScale by remember { mutableStateOf(store.speechScale) }
    var progressTick by remember { mutableStateOf(0) }

    var route by remember {
        mutableStateOf<Route>(if (store.teacherId == null) Route.PickTeacher else Route.Home)
    }

    val teacher = teacherById(teacherId)

    val say: (String, Float) -> Unit = { text, extra ->
        speaker.speak(text, teacher, speechScale * extra)
    }

    HabloTheme(accent = Color(teacher.color)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Cream)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding()
            ) {
                when (val current = route) {

                    is Route.PickTeacher -> {
                        val first = store.teacherId == null
                        BackHandler(enabled = !first) {
                            speaker.stop()
                            route = Route.Home
                        }
                        TeacherPickerScreen(
                            currentId = teacherId,
                            isFirstTime = first,
                            onPreview = { t -> speaker.speak(t.greeting, t, speechScale) },
                            onChoose = { t ->
                                speaker.stop()
                                store.teacherId = t.id
                                teacherId = t.id
                                route = Route.Home
                            },
                            onBack = {
                                speaker.stop()
                                route = Route.Home
                            }
                        )
                    }

                    is Route.Home -> {
                        HomeScreen(
                            teacher = teacher,
                            store = store,
                            refreshKey = progressTick,
                            speaker = speaker,
                            onOpenLesson = { lesson -> route = Route.Running(lesson.id) },
                            onSettings = { route = Route.Settings },
                            onPronunciation = { route = Route.Pronunciation },
                            onGreeting = { say(teacher.greeting, 1f) }
                        )
                    }

                    is Route.Running -> {
                        val lesson = Course.lessonById(current.lessonId)
                        BackHandler {
                            speaker.stop()
                            listener.stopRecording()
                            route = Route.Home
                        }
                        if (lesson == null) {
                            route = Route.Home
                        } else {
                            LessonScreen(
                                lesson = lesson,
                                teacher = teacher,
                                listener = listener,
                                say = say,
                                onFinish = { score, correct ->
                                    store.recordLesson(lesson.id, score, correct * 10)
                                    progressTick += 1
                                    speaker.stop()
                                    listener.stopRecording()
                                    route = Route.Home
                                },
                                onExit = {
                                    speaker.stop()
                                    listener.stopRecording()
                                    route = Route.Home
                                }
                            )
                        }
                    }

                    is Route.Pronunciation -> {
                        BackHandler {
                            speaker.stop()
                            listener.stopRecording()
                            route = Route.Home
                        }
                        PronunciationScreen(
                            teacher = teacher,
                            speaker = speaker,
                            listener = listener,
                            store = store,
                            say = say,
                            onBack = {
                                speaker.stop()
                                listener.stopRecording()
                                route = Route.Home
                            }
                        )
                    }

                    is Route.Settings -> {
                        BackHandler {
                            speaker.stop()
                            route = Route.Home
                        }
                        SettingsScreen(
                            teacher = teacher,
                            store = store,
                            speaker = speaker,
                            onChangeTeacher = { route = Route.PickTeacher },
                            onTestVoice = { s ->
                                speaker.speak(teacher.greeting, teacher, s)
                            },
                            onSpeedChange = { s ->
                                speechScale = s
                                store.speechScale = s
                            },
                            onReset = {
                                store.resetEverything()
                                progressTick += 1
                                teacherId = null
                                route = Route.PickTeacher
                            },
                            onBack = {
                                speaker.stop()
                                route = Route.Home
                            }
                        )
                    }
                }
            }
        }
    }
}
