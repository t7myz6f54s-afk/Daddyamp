import sys

import gi
gi.require_version("Gst", "1.0")
from gi.repository import Gst
import random

from PySide6.QtCore import Qt, Signal, QThread, QObject, QPointF, QSize
from PySide6.QtCore import QTimer
from PySide6.QtGui import QKeySequence, QShortcut, QPainter, QColor, QPalette, QIcon
from PySide6.QtWidgets import (
    QStyle,
    QApplication,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QPushButton,
    QScrollArea,
    QSlider,
    QVBoxLayout,
    QWidget,
    QSizePolicy,
)

from app.database.database import (
    initialize_database,
    update_play_count,
    update_artwork,
    toggle_favorite,
    get_favorite_songs,
)

from app.database.library import (
    load_library,
    refresh_library,
)

from app.player.audio_engine import AudioEngine

from app.artwork.manager import download_artwork

from app.ui import (
    ArtworkLabel,
    AlbumCard,
    ArtistCard,
    LyricsPanel,
)

from app.lyrics.manager import LyricsManager

from app.theme.theme import get_stylesheet
from app.theme.palette import (
    extract_palette,
    default_palette,
)




class BarSeekWidget(QWidget):
    """
    Poweramp-inspired compact seek/progress control.

    The control deliberately keeps a small QSlider-compatible API so the
    existing playback and seeking code does not need to know that this is
    a custom-painted widget.
    """

    sliderPressed = Signal()
    sliderReleased = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)

        self._value = 0
        self._maximum = 0
        self._pressed = False

        self._accent = QColor("#7C5CFF")
        self._inactive = QColor("#394352")

        # Keep the widget compact. The visual strip itself is intentionally
        # thinner than the available widget height.
        self.setMinimumHeight(24)
        self.setMaximumHeight(30)

        self.setSizePolicy(
            QSizePolicy.Expanding,
            QSizePolicy.Fixed
        )

        self.setMouseTracking(True)

    # ==================================================
    # SLIDER-COMPATIBLE API
    # ==================================================

    def setMinimum(self, value):
        pass

    def setMaximum(self, value):
        self._maximum = max(0, int(value))
        self._value = min(self._value, self._maximum)
        self.update()

    def maximum(self):
        return self._maximum

    def setValue(self, value):
        self._value = max(
            0,
            min(int(value), self._maximum)
        )
        self.update()

    def value(self):
        return self._value

    def setTracking(self, enabled):
        pass

    def blockSignals(self, block):
        return super().blockSignals(block)

    # ==================================================
    # DYNAMIC ARTWORK PALETTE
    # ==================================================

    def set_palette(self, accent, inactive=None):
        self._accent = QColor(accent)

        if inactive:
            self._inactive = QColor(inactive)
        else:
            self._inactive = QColor(accent)
            self._inactive.setAlpha(42)

        self.update()

    # ==================================================
    # POSITION
    # ==================================================

    def _position_from_x(self, x):
        width = max(1, self.width())

        ratio = max(
            0.0,
            min(
                1.0,
                float(x) / float(width)
            )
        )

        return int(ratio * self._maximum)

    # ==================================================
    # PAINT
    # ==================================================

    def paintEvent(self, event):
        painter = QPainter(self)

        painter.setRenderHint(
            QPainter.Antialiasing,
            True
        )

        width = self.width()
        height = self.height()

        if width <= 0 or height <= 0:
            painter.end()
            return

        # --------------------------------------------------
        # PROGRESS POSITION
        # --------------------------------------------------

        progress_ratio = 0.0

        if self._maximum > 0:
            progress_ratio = max(
                0.0,
                min(
                    1.0,
                    float(self._value)
                    / float(self._maximum)
                )
            )

        # Keep the visual strip slightly inset from both ends.
        left = 4
        right = max(
            left + 1,
            width - 4
        )

        usable_width = right - left

        marker_x = (
            left
            + progress_ratio * usable_width
        )

        # --------------------------------------------------
        # REFINED POWERAMP-STYLE STRIP
        #
        # Small vertical ticks provide texture without turning
        # the seek bar into a waveform.
        # --------------------------------------------------

        bar_width = 2
        gap = 4

        count = max(
            1,
            int(
                (usable_width + gap)
                / (bar_width + gap)
            )
        )

        heights = (
            0.34,
            0.48,
            0.62,
            0.42,
            0.72,
            0.52,
            0.38,
            0.64,
        )

        center_y = height / 2.0

        for i in range(count):

            x = left + i * (
                bar_width + gap
            )

            if x > right:
                break

            height_ratio = heights[
                i % len(heights)
            ]

            bar_height = max(
                4,
                int(
                    (height - 10)
                    * height_ratio
                )
            )

            y = int(
                center_y
                - bar_height / 2.0
            )

            bar_center = (
                x + bar_width / 2.0
            )

            if bar_center <= marker_x:

                color = QColor(
                    self._accent
                )

                color.setAlpha(220)

            else:

                color = QColor(
                    self._inactive
                )

                color.setAlpha(
                    55
                )

            painter.setBrush(color)
            painter.setPen(Qt.NoPen)

            painter.drawRoundedRect(
                int(x),
                y,
                bar_width,
                bar_height,
                1.0,
                1.0
            )

        # --------------------------------------------------
        # CURRENT POSITION MARKER
        # --------------------------------------------------

        if self._maximum > 0:

            marker_color = QColor(
                self._accent
            )

            marker_color.setAlpha(255)

            painter.setBrush(
                marker_color
            )

            painter.setPen(
                Qt.NoPen
            )

            marker_width = 3
            marker_height = min(
                height - 4,
                18
            )

            marker_y = int(
                center_y
                - marker_height / 2.0
            )

            painter.drawRoundedRect(
                int(
                    marker_x
                    - marker_width / 2.0
                ),
                marker_y,
                marker_width,
                marker_height,
                1.5,
                1.5
            )

        painter.end()

    def mousePressEvent(self, event):

        if event.button() == Qt.LeftButton:

            self._pressed = True

            self.setValue(
                self._position_from_x(
                    event.position().x()
                )
            )

            self.sliderPressed.emit()

            self.update()

            event.accept()
            return

        super().mousePressEvent(event)

    def mouseMoveEvent(self, event):

        if self._pressed:

            self.setValue(
                self._position_from_x(
                    event.position().x()
                )
            )

            self.update()

            event.accept()
            return

        super().mouseMoveEvent(event)

    def mouseReleaseEvent(self, event):

        if (
            event.button() == Qt.LeftButton
            and self._pressed
        ):

            self.setValue(
                self._position_from_x(
                    event.position().x()
                )
            )

            self._pressed = False

            self.sliderReleased.emit()

            self.update()

            event.accept()
            return

        super().mouseReleaseEvent(event)


class MarqueeLabel(QLabel):

    def __init__(self, text="", parent=None):
        super().__init__(parent)

        self._full_text = text
        self._offset = 0
        self._text_width = 0
        self._scroll_distance = 0

        self._timer = QTimer(self)
        self._timer.setInterval(30)
        self._timer.timeout.connect(self._scroll_step)

        self.setTextInteractionFlags(Qt.NoTextInteraction)

        self._pause_timer = QTimer(self)
        self._pause_timer.setSingleShot(True)
        self._pause_timer.timeout.connect(self._start_scrolling)

        self.set_text(text)

    def set_text(self, text):
        self._full_text = text or ""
        self._offset = 0

        self._timer.stop()
        self._pause_timer.stop()

        font_metrics = self.fontMetrics()
        self._text_width = font_metrics.horizontalAdvance(
            self._full_text
        )

        self._scroll_distance = max(
            0,
            self._text_width - self.width()
        )

        self.update()

        if self._scroll_distance > 0:
            self._pause_timer.start(1200)

    def resizeEvent(self, event):
        super().resizeEvent(event)

        font_metrics = self.fontMetrics()
        self._text_width = font_metrics.horizontalAdvance(
            self._full_text
        )

        self._scroll_distance = max(
            0,
            self._text_width - self.width()
        )

        self._offset = 0
        self._timer.stop()
        self._pause_timer.stop()

        if self._scroll_distance > 0:
            self._pause_timer.start(1200)

        self.update()

    def _start_scrolling(self):
        if self._scroll_distance > 0:
            self._timer.start()

    def _scroll_step(self):
        self._offset += 1

        if self._offset >= self._scroll_distance:
            self._timer.stop()
            self._pause_timer.start(1200)

            # After reaching the end, return to the beginning.
            self._offset = 0

            # Pause before starting again.
            self._pause_timer.timeout.disconnect()
            self._pause_timer.timeout.connect(
                self._start_scrolling
            )

        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.TextAntialiasing)

        painter.setPen(self.palette().color(
            self.foregroundRole()
        ))

        painter.setFont(self.font())

        painter.setClipRect(
            0,
            0,
            self.width(),
            self.height()
        )

        y = (
            (self.height() - self.fontMetrics().height()) // 2
            + self.fontMetrics().ascent()
        )

        if self._scroll_distance <= 0:
            painter.drawText(
                0,
                y,
                self._full_text
            )
        else:
            painter.drawText(
                -self._offset,
                y,
                self._full_text
            )


class LyricsWorker(QObject):

    finished = Signal(object, object)
    failed = Signal(object, object)

    def __init__(self, lyrics_manager, song, request_id):
        super().__init__()
        self.lyrics_manager = lyrics_manager
        self.song = song
        self.request_id = request_id

    def run(self):

        try:

            lyrics = self.lyrics_manager.get_lyrics(
                self.song.get("title", ""),
                self.song.get("artist", ""),
                self.song.get("album", ""),
                self.song.get("duration")
            )

            self.finished.emit(
                self.request_id,
                lyrics
            )

        except Exception as error:

            print(
                f"Lyrics worker error: {error}"
            )

            self.failed.emit(
                self.request_id,
                error
            )


class ArtworkWorker(QObject):

    finished = Signal(object, object)

    def __init__(self, song):
        super().__init__()
        self.song = song

    def run(self):

        try:

            artist = self.song.get(
                "artist",
                "Unknown Artist"
            )

            title = self.song.get(
                "title",
                "Unknown Title"
            )

            artwork = download_artwork(
                artist,
                title
            )

            self.finished.emit(
                self.song,
                artwork
            )

        except Exception as error:

            print(
                "Artwork worker error:",
                error
            )

            self.finished.emit(
                self.song,
                None
            )





class VolumeControl(QWidget):
    volume_changed = Signal(int)
    muted_changed = Signal(bool)

    def __init__(self, parent=None):
        super().__init__(parent)

        self._muted = False
        self._last_volume = 75

        self.setFixedWidth(180)
        self.setFixedHeight(42)

        layout = QHBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(9)

        # ----------------------------------------------------
        # Volume / mute button
        # ----------------------------------------------------

        self.knob = QPushButton()
        self.knob.setFixedSize(34, 34)
        self.knob.setCursor(
            Qt.PointingHandCursor
        )
        self.knob.setObjectName(
            "volumeKnob"
        )

        self.knob.clicked.connect(
            self.toggle_mute
        )

        # ----------------------------------------------------
        # Volume slider
        # ----------------------------------------------------

        self.slider = QSlider(
            Qt.Horizontal
        )

        self.slider.setRange(
            0,
            100
        )

        self.slider.setValue(
            self._last_volume
        )

        self.slider.setCursor(
            Qt.PointingHandCursor
        )

        self.slider.setObjectName(
            "volumeBar"
        )

        self.slider.setTracking(
            True
        )

        self.slider.valueChanged.connect(
            self._volume_changed
        )

        layout.addWidget(
            self.knob,
            0,
            Qt.AlignVCenter
        )

        layout.addWidget(
            self.slider,
            1,
            Qt.AlignVCenter
        )

        self._update_icon()

    def _update_icon(self):

        if self._muted:

            self.knob.setText(
                "🔇"
            )

        elif self.slider.value() < 35:

            self.knob.setText(
                "🔈"
            )

        elif self.slider.value() < 70:

            self.knob.setText(
                "🔉"
            )

        else:

            self.knob.setText(
                "🔊"
            )

    def _volume_changed(self, value):

        if value > 0:
            self._last_volume = value

        self._muted = value == 0

        self._update_icon()

        self.volume_changed.emit(
            value
        )

    def set_volume(self, value):

        value = max(
            0,
            min(
                100,
                int(value)
            )
        )

        self.slider.blockSignals(
            True
        )

        self.slider.setValue(
            value
        )

        self.slider.blockSignals(
            False
        )

        if value > 0:
            self._last_volume = value

        self._muted = value == 0

        self._update_icon()

    def toggle_mute(self):

        if self._muted:

            self._muted = False

            value = self._last_volume

            if value <= 0:
                value = 75

            self.slider.setValue(
                value
            )

        else:

            self._last_volume = (
                self.slider.value()
            )

            if self._last_volume <= 0:
                self._last_volume = 75

            self._muted = True

            self.slider.setValue(
                0
            )

        self._update_icon()

        self.muted_changed.emit(
            self._muted
        )


class AudifyWindow(QMainWindow):

    def __init__(self):

        super().__init__()

        self.setWindowTitle("Audify")
        self.resize(1400, 850)

        initialize_database()

        self.songs = []
        self.filtered_songs = []

        self.current_index = -1
        self.queue = []
        self.is_playing = False
        self.showing_favorites = False
        self.is_seeking = False

        # Right-side progress label:
        # False = total duration
        # True  = remaining duration
        self.show_remaining_time = False

        # Lyrics background worker state
        self._lyrics_request_id = 0
        self._lyrics_thread = None
        self._lyrics_worker = None

        # ==================================================
        # Current artwork-driven UI palette
        self.current_palette = default_palette()

        # Artwork download state
        self._artwork_downloading = False
        self._artwork_queue = []
        self._artwork_total = 0
        self._artwork_completed = 0
        self._artwork_successful = 0
        self._artwork_failed = 0

        # Playback modes
        self.shuffle_enabled = False
        self.repeat_enabled = False

        # ==================================================
        # AUDIO ENGINE
        # ==================================================

        self.audio_engine = AudioEngine()
        self.lyrics_manager = LyricsManager()

        # Position is synchronised by sync_playback_position()
        # below. Do not connect a second position pipeline.
        self.audio_engine.duration_changed.connect(
            self.update_duration
        )

        self.audio_engine.song_finished.connect(
            self.handle_song_finished
        )

        # ==================================================
        # BUILD
        # ==================================================

        self.build_interface()

        # ==================================================
        # DIRECT PLAYBACK POSITION TIMER
        # ==================================================
        # Do NOT depend on AudioEngine.position_changed.
        # The UI directly queries GStreamer every 100 ms.
        self.ui_position_timer = QTimer(self)
        self.ui_position_timer.setInterval(100)
        self.ui_position_timer.timeout.connect(
            self.sync_playback_position
        )
        self.ui_position_timer.start()


        # ==================================================
        # KEYBOARD CONTROLS
        # ==================================================

        self.seek_forward_shortcut = QShortcut(
            QKeySequence("Right"),
            self
        )

        self.seek_forward_shortcut.activated.connect(
            lambda: self.audio_engine.seek_relative(5)
        )

        self.seek_backward_shortcut = QShortcut(
            QKeySequence("Left"),
            self
        )

        self.seek_backward_shortcut.activated.connect(
            lambda: self.audio_engine.seek_relative(-5)
        )

        self.play_pause_shortcut = QShortcut(
            QKeySequence("Space"),
            self
        )

        self.play_pause_shortcut.activated.connect(
            self.toggle_play_pause
        )

        self.load_music()

    # ==================================================
    # INTERFACE
    # ==================================================

    def build_interface(self):

        central = QWidget()
        self.setCentralWidget(central)

        main_layout = QVBoxLayout(central)

        main_layout.setContentsMargins(
            0,
            0,
            0,
            0
        )

        main_layout.setSpacing(0)

        # ==================================================
        # CONTENT
        # ==================================================

        content = QWidget()

        content_layout = QHBoxLayout(content)

        content_layout.setContentsMargins(
            0,
            0,
            0,
            0
        )

        content_layout.setSpacing(0)

        # ==================================================
        # SIDEBAR
        # ==================================================

        sidebar = QFrame()
        sidebar.setFixedWidth(215)

        sidebar_layout = QVBoxLayout(sidebar)

        sidebar_layout.setContentsMargins(
            20,
            25,
            20,
            20
        )

        logo = QLabel("AUDIFY")
        logo.setObjectName("logo")

        sidebar_layout.addWidget(logo)
        sidebar_layout.addSpacing(30)

        self.home_button = QPushButton(
            "⌂   Home"
        )

        self.search_button = QPushButton(
            "⌕   Search"
        )

        self.library_button = QPushButton(
            "♫   Your Library"
        )

        # --------------------------------------------------
        # SIDEBAR NAVIGATION
        # --------------------------------------------------

        self.home_button.clicked.connect(
            self.show_home
        )

        self.search_button.clicked.connect(
            self.focus_search
        )

        self.library_button.clicked.connect(
            self.show_library
        )

        sidebar_layout.addWidget(
            self.home_button
        )

        sidebar_layout.addWidget(
            self.search_button
        )

        sidebar_layout.addWidget(
            self.library_button
        )

        sidebar_layout.addSpacing(25)

        playlist_title = QLabel(
            "PLAYLISTS"
        )

        playlist_title.setObjectName(
            "sectionTitle"
        )

        sidebar_layout.addWidget(
            playlist_title
        )

        self.playlist = QListWidget()

        self.liked_item = QListWidgetItem(
            "♥   Liked Songs"
        )

        self.recent_item = QListWidgetItem(
            "Recently Played"
        )

        self.favorite_item = QListWidgetItem(
            "My Favorites"
        )

        self.workout_item = QListWidgetItem(
            "Workout"
        )

        self.playlist.addItem(
            self.liked_item
        )

        self.playlist.addItem(
            self.recent_item
        )

        self.playlist.addItem(
            self.favorite_item
        )

        self.playlist.addItem(
            self.workout_item
        )

        self.playlist.itemClicked.connect(
            self.playlist_clicked
        )

        sidebar_layout.addWidget(
            self.playlist
        )

        sidebar_layout.addStretch()

        self.refresh_button = QPushButton(
            "↻   Refresh Library"
        )

        self.refresh_button.clicked.connect(
            self.refresh_music_library
        )

        sidebar_layout.addWidget(
            self.refresh_button
        )

        self.download_artwork_button = QPushButton(
            "▣   Download Artwork"
        )

        self.artwork_status_label = QLabel(
            "Artwork ready"
        )

        self.artwork_status_label.setObjectName(
            "artworkStatus"
        )

        self.download_artwork_button.clicked.connect(
            self.download_all_artwork
        )

        sidebar_layout.addWidget(
            self.download_artwork_button
        )

        sidebar_layout.addWidget(
            self.artwork_status_label
        )

        content_layout.addWidget(
            sidebar
        )

        # ==================================================
        # MAIN AREA
        # ==================================================

        main_area = QWidget()

        main_area_layout = QVBoxLayout(
            main_area
        )

        main_area_layout.setContentsMargins(
            26,
            20,
            26,
            14
        )

        self.heading = QLabel(
            "Your Music"
        )

        self.heading.setObjectName(
            "heading"
        )

        main_area_layout.addWidget(
            self.heading
        )

        self.search_box = QLineEdit()

        self.search_box.setPlaceholderText(
            "Search songs, artists or albums..."
        )

        self.search_box.setObjectName(
            "searchBox"
        )

        self.search_box.textChanged.connect(
            self.search_music
        )

        main_area_layout.addWidget(
            self.search_box
        )

        # ==================================================
        # MUSIC SCROLL AREA
        # ==================================================

        self.scroll_area = QScrollArea()

        self.scroll_area.setWidgetResizable(
            True
        )

        self.scroll_area.setFrameShape(
            QFrame.NoFrame
        )

        self.scroll_area.setHorizontalScrollBarPolicy(
            Qt.ScrollBarAlwaysOff
        )

        self.library_widget = QWidget()

        self.library_layout = QVBoxLayout(
            self.library_widget
        )

        self.library_layout.setContentsMargins(
            0,
            16,
            0,
            12
        )

        self.library_layout.setSpacing(22)

        self.scroll_area.setWidget(
            self.library_widget
        )

        main_area_layout.addWidget(
            self.scroll_area
        )

        content_layout.addWidget(
            main_area,
            1
        )

        # ==================================================
        # LYRICS
        # ==================================================

        self.lyrics_panel = LyricsPanel()

        self.lyrics_panel.setFixedWidth(
            330
        )

        self.lyrics_visible = True

        content_layout.addWidget(
            self.lyrics_panel
        )

        main_layout.addWidget(
            content,
            1
        )

        # ==================================================
        # PLAYER
        # ==================================================

        self.build_player(
            main_layout
        )

        self.setStyleSheet(
            get_stylesheet()
            + self.get_player_stylesheet()
        )

    # ==================================================
    # PLAYER
    # ==================================================

    def get_player_stylesheet(self):
        """
        Player-specific styling using the current artwork palette.
        """

        palette = getattr(
            self,
            "current_palette",
            {}
        )

        accent = palette.get(
            "accent",
            "#7C5CFF"
        )

        accent_secondary = palette.get(
            "accent_secondary",
            accent
        )

        accent_soft = palette.get(
            "accent_soft",
            "#2A2148"
        )

        background = palette.get(
            "background",
            "#08090D"
        )

        border = palette.get(
            "border",
            "#292E39"
        )

        # --------------------------------------------------
        # AUTOMATIC CONTRAST COLOR
        #
        # Dark artwork/player background -> white controls
        # Light artwork/player background -> dark controls
        # --------------------------------------------------

        try:
            background_color = QColor(background)

            control_foreground = (
                "#111318"
                if background_color.lightness() >= 145
                else "#FFFFFF"
            )

        except Exception:
            control_foreground = "#FFFFFF"

        return f"""
/* =========================================================
   AUDIFY PLAYER
   ========================================================= */

QFrame#player {{
    background: {background};
    border-top: 1px solid {border};
}}

/* ---------------------------------------------------------
   SONG INFORMATION
   --------------------------------------------------------- */

QLabel#currentSong {{
    color: #F5F7FA;
    font-size: 14px;
    font-weight: 650;
    padding: 0;
    background: transparent;
}}

QLabel#currentArtist {{
    color: rgba(255, 255, 255, 112);
    font-size: 12px;
    font-weight: 450;
    padding: 0;
    background: transparent;
}}

/* ---------------------------------------------------------
   FAVORITE
   --------------------------------------------------------- */

QPushButton#favoriteButton {{
    border: none;
    border-radius: 18px;
    background: transparent;
    color: rgba(255, 255, 255, 90);
    font-size: 20px;
    font-weight: 400;
    padding: 0;
}}

QPushButton#favoriteButton:hover {{
    border: none;
    background: {accent_soft};
    color: #FFFFFF;
}}

QPushButton#favoriteButton:pressed {{
    border: none;
    background: {accent_soft};
    color: {accent};
}}

QPushButton#favoriteButton:checked {{
    border: none;
    background: {accent_soft};
    color: {accent};
    font-weight: 600;
}}

/* ---------------------------------------------------------
   SHUFFLE / REPEAT
   --------------------------------------------------------- */

QPushButton#shuffleButton,
QPushButton#repeatButton {{
    border: none;
    border-radius: 18px;
    background: transparent;
    color: rgba(255, 255, 255, 135);
    font-size: 17px;
    font-weight: 500;
    padding: 0;
}}

QPushButton#shuffleButton:hover,
QPushButton#repeatButton:hover {{
    border: none;
    background: {accent_soft};
    color: #FFFFFF;
}}

QPushButton#shuffleButton:pressed,
QPushButton#repeatButton:pressed {{
    border: none;
    background: {accent_soft};
    color: {accent};
}}

QPushButton#shuffleButton:checked,
QPushButton#repeatButton:checked {{
    border: none;
    background: {accent_soft};
    color: {accent};
    font-weight: 700;
}}

/* ---------------------------------------------------------
   PREVIOUS / NEXT
   --------------------------------------------------------- */

/* ---------------------------------------------------------
   PREVIOUS / NEXT
   ---------------------------------------------------------
   Styled dynamically by update_playback_control_contrast().
   --------------------------------------------------------- */

/* ---------------------------------------------------------
   PLAY
   --------------------------------------------------------- */

QPushButton#playButton {{
    background: {accent};
    color: #FFFFFF;
    border: none;
    border-radius: 26px;
    padding: 0px;
    margin: 0px;
    min-width: 52px;
    max-width: 52px;
    min-height: 52px;
    max-height: 52px;
    font-size: 21px;
    font-weight: 700;
    text-align: center;
}}

QPushButton#playButton:hover {{
    background: {accent_secondary};
    color: #FFFFFF;
}}

QPushButton#playButton:pressed {{
    background: {accent};
    color: #FFFFFF;
}}

/* ---------------------------------------------------------
   VOLUME CONTROL
   --------------------------------------------------------- */

QPushButton#volumeKnob {{
    border: none;
    border-radius: 17px;
    background: rgba(255, 255, 255, 6);
    color: {accent_secondary};
    font-size: 14px;
    padding: 0;
}}

QPushButton#volumeKnob:hover {{
    border: none;
    background: {accent_soft};
    color: #FFFFFF;
}}

QPushButton#volumeKnob:pressed {{
    border: none;
    background: {accent};
    color: #FFFFFF;
}}

/* ---------------------------------------------------------
   VOLUME BAR
   --------------------------------------------------------- */

QSlider#volumeBar {{
    min-height: 22px;
    max-height: 22px;
}}

QSlider#volumeBar::groove:horizontal {{
    height: 3px;
    border-radius: 2px;
    background: rgba(255, 255, 255, 20);
}}

QSlider#volumeBar::sub-page:horizontal {{
    height: 3px;
    border-radius: 2px;
    background: {accent};
}}

QSlider#volumeBar::add-page:horizontal {{
    height: 3px;
    border-radius: 2px;
    background: rgba(255, 255, 255, 12);
}}

QSlider#volumeBar::handle:horizontal {{
    width: 9px;
    height: 9px;
    margin: -3px 0;
    border-radius: 5px;
    background: {accent_secondary};
    border: 2px solid rgba(12, 16, 23, 210);
}}

QSlider#volumeBar::handle:horizontal:hover {{
    width: 11px;
    height: 11px;
    margin: -4px 0;
    border-radius: 6px;
    background: #FFFFFF;
    border: 2px solid {accent};
}}

/* ---------------------------------------------------------
   TIME LABELS
   --------------------------------------------------------- */



QLabel {{
    background: transparent;
}}
"""

    def build_player(self, main_layout):

        # ==================================================
        # PLAYER CONTAINER
        # ==================================================

        self.player = QFrame()
        self.player.setObjectName("player")
        self.player.setMinimumHeight(125)
        self.player.setMaximumHeight(125)
        self.player.setSizePolicy(
            QSizePolicy.Expanding,
            QSizePolicy.Fixed
        )

        player = self.player

        print(
            "PLAYER CREATED:",
            "visible=", player.isVisible(),
            "height=", player.height(),
            "minHeight=", player.minimumHeight(),
            "maxHeight=", player.maximumHeight()
        )

        player_layout = QVBoxLayout(player)
        player_layout.setContentsMargins(
            22, 6, 22, 8
        )
        player_layout.setSpacing(3)

        # ==================================================
        # MAIN PLAYER ROW
        # ==================================================

        player_row = QHBoxLayout()
        player_row.setContentsMargins(
            0, 0, 0, 0
        )
        player_row.setSpacing(0)

        # ==================================================
        # LEFT: ARTWORK + SONG INFORMATION
        # ==================================================

        song_info = QHBoxLayout()
        song_info.setContentsMargins(
            0, 0, 0, 0
        )
        song_info.setSpacing(12)
        song_info.setAlignment(
            Qt.AlignVCenter
        )

        # --------------------------------------------------
        # ARTWORK
        # --------------------------------------------------

        self.player_artwork = ArtworkLabel(
            None,
            64
        )

        self.player_artwork.setObjectName(
            "artwork"
        )

        self.player_artwork.setFixedSize(
            64,
            64
        )

        self.player_artwork.setStyleSheet(
            """
            QLabel#artwork {
                border-radius: 10px;
                background: rgba(255, 255, 255, 9);
                border: 1px solid rgba(255, 255, 255, 18);
            }

            QLabel#artwork[artworkMissing="true"] {
                color: rgba(255, 255, 255, 90);
                font-size: 21px;
                border: 1px solid rgba(255, 255, 255, 18);
            }
            """
        )

        song_info.addWidget(
            self.player_artwork,
            0,
            Qt.AlignVCenter
        )

        # --------------------------------------------------
        # TITLE + ARTIST
        # --------------------------------------------------

        song_text = QVBoxLayout()
        song_text.setContentsMargins(
            0, 0, 0, 0
        )
        song_text.setSpacing(2)
        song_text.setAlignment(
            Qt.AlignVCenter
        )

        self.current_song = MarqueeLabel(
            "No song playing"
        )

        self.current_song.setObjectName(
            "currentSong"
        )

        self.current_song.setMinimumWidth(
            120
        )

        self.current_song.setMaximumWidth(
            260
        )

        self.current_song.setSizePolicy(
            QSizePolicy.Expanding,
            QSizePolicy.Preferred
        )

        self.current_song.setToolTip(
            "No song playing"
        )

        self.current_artist = QLabel(
            "Unknown Artist"
        )

        self.current_artist.setObjectName(
            "currentArtist"
        )

        self.current_artist.setMinimumWidth(
            100
        )

        self.current_artist.setMaximumWidth(
            260
        )

        self.current_artist.setSizePolicy(
            QSizePolicy.Expanding,
            QSizePolicy.Preferred
        )

        song_text.addWidget(
            self.current_song
        )

        song_text.addWidget(
            self.current_artist
        )

        song_text_widget = QWidget()
        song_text_widget.setLayout(
            song_text
        )

        song_info.addWidget(
            song_text_widget,
            1
        )

        # --------------------------------------------------
        # FAVORITE
        # --------------------------------------------------

        self.favorite_button = QPushButton(
            "♡"
        )

        self.favorite_button.setObjectName(
            "favoriteButton"
        )

        self.favorite_button.setFixedSize(
            36,
            36
        )

        self.favorite_button.setCheckable(
            True
        )

        self.favorite_button.setCursor(
            Qt.PointingHandCursor
        )

        self.favorite_button.clicked.connect(
            self.toggle_current_favorite
        )

        song_info.addWidget(
            self.favorite_button,
            0,
            Qt.AlignVCenter
        )

        song_info_widget = QWidget()
        song_info_widget.setLayout(
            song_info
        )

        song_info_widget.setMinimumWidth(
            350
        )

        song_info_widget.setMaximumWidth(
            500
        )

        song_info_widget.setSizePolicy(
            QSizePolicy.Expanding,
            QSizePolicy.Fixed
        )

        player_row.addWidget(
            song_info_widget,
            1
        )

        # ==================================================
        # CENTER: TRANSPORT CONTROLS
        # ==================================================

        controls_widget = QWidget()

        controls = QHBoxLayout(
            controls_widget
        )

        controls.setContentsMargins(
            8, 0, 8, 0
        )

        controls.setSpacing(3)

        controls.setAlignment(
            Qt.AlignCenter
        )

        # --------------------------------------------------
        # SHUFFLE
        # --------------------------------------------------

        self.shuffle_button = QPushButton()

        self.shuffle_button.setObjectName(
            "shuffleButton"
        )

        self.shuffle_button.setFixedSize(
            36,
            36
        )

        self.shuffle_button.setCheckable(
            True
        )

        self.shuffle_button.setCursor(
            Qt.PointingHandCursor
        )

        self.shuffle_button.setText(
            "⇄"
        )

        self.shuffle_button.toggled.connect(
            self.toggle_shuffle
        )

        # --------------------------------------------------
        # PREVIOUS
        # --------------------------------------------------

        self.previous_button = QPushButton()

        self.previous_button.setObjectName(
            "previousButton"
        )

        self.previous_button.setFixedSize(
            42,
            42
        )

        self.previous_button.setCursor(
            Qt.PointingHandCursor
        )

        self.previous_button.setText(
            "⏮"
        )

        # --------------------------------------------------
        # PLAY / PAUSE
        # --------------------------------------------------

        self.play_button = QPushButton()

        self.play_button.setObjectName(
            "playButton"
        )

        self.play_button.setFixedSize(
            52,
            52
        )

        self.play_button.setCursor(
            Qt.PointingHandCursor
        )

        self.play_button.setText(
            "▶"
        )

        # --------------------------------------------------
        # NEXT
        # --------------------------------------------------

        self.next_button = QPushButton()

        self.next_button.setObjectName(
            "nextButton"
        )

        self.next_button.setFixedSize(
            42,
            42
        )

        self.next_button.setCursor(
            Qt.PointingHandCursor
        )

        self.next_button.setText(
            "⏭"
        )

        # --------------------------------------------------
        # REPEAT
        # --------------------------------------------------

        self.repeat_button = QPushButton()

        self.repeat_button.setObjectName(
            "repeatButton"
        )

        self.repeat_button.setFixedSize(
            36,
            36
        )

        self.repeat_button.setCheckable(
            True
        )

        self.repeat_button.setCursor(
            Qt.PointingHandCursor
        )

        self.repeat_button.setText(
            "↻"
        )

        self.repeat_button.toggled.connect(
            self.toggle_repeat
        )

        # --------------------------------------------------
        # CONNECTIONS
        # --------------------------------------------------

        self.previous_button.clicked.connect(
            self.play_previous
        )

        self.play_button.clicked.connect(
            self.toggle_play_pause
        )

        self.next_button.clicked.connect(
            self.play_next
        )

        # --------------------------------------------------
        # CONTROL ORDER
        # --------------------------------------------------

        controls.addWidget(
            self.shuffle_button
        )

        controls.addWidget(
            self.previous_button
        )

        controls.addWidget(
            self.play_button
        )

        controls.addWidget(
            self.next_button
        )

        controls.addWidget(
            self.repeat_button
        )

        # Give the transport cluster enough room to breathe.
        controls_widget.setMinimumWidth(
            250
        )

        controls_widget.setMaximumWidth(
            290
        )

        # Keep the entire playback-control group visually centered
        # in the available middle zone.
        player_row.addStretch(1)

        player_row.addWidget(
            controls_widget,
            0,
            Qt.AlignCenter
        )

        player_row.addStretch(1)

        # ==================================================
        # RIGHT: LYRICS
        # ==================================================

        self.lyrics_button = QPushButton(
            "♫ Lyrics"
        )

        self.lyrics_button.setObjectName(
            "lyricsButton"
        )

        self.lyrics_button.setCheckable(
            True
        )

        self.lyrics_button.setChecked(
            True
        )

        self.lyrics_button.setCursor(
            Qt.PointingHandCursor
        )

        self.lyrics_button.clicked.connect(
            self.toggle_lyrics
        )

        player_row.addWidget(
            self.lyrics_button,
            0,
            Qt.AlignVCenter
        )

        # ==================================================
        # RIGHT: VOLUME
        # ==================================================

        self.volume_control = VolumeControl()

        self.volume_slider = (
            self.volume_control.slider
        )

        self.volume_control.volume_changed.connect(
            self.change_volume
        )

        self.volume_control.muted_changed.connect(
            self.volume_mute_changed
        )

        player_row.addWidget(
            self.volume_control,
            0,
            Qt.AlignVCenter
        )

        # Apply palette-aware contrast after all controls
        # have been created.
        self.update_playback_control_contrast()

        # ==================================================
        # ADD MAIN ROW
        # ==================================================

        player_layout.addLayout(
            player_row,
            1
        )

        # ==================================================
        # PROGRESS ROW
        # ==================================================

        progress_layout = QHBoxLayout()

        progress_layout.setContentsMargins(
            4, 0, 4, 0
        )

        progress_layout.setSpacing(
            8
        )

        # --------------------------------------------------
        # CURRENT TIME
        # --------------------------------------------------

        self.current_time = QLabel(
            "0:00"
        )

        self.current_time.setMinimumWidth(
            38
        )

        self.current_time.setMaximumWidth(
            48
        )

        self.current_time.setAlignment(
            Qt.AlignLeft | Qt.AlignVCenter
        )

        # --------------------------------------------------
        # CUSTOM SEEK BAR
        # --------------------------------------------------

        self.progress = BarSeekWidget()

        self.progress.setMinimum(
            0
        )

        self.progress.setMaximum(
            0
        )

        self.progress.setTracking(
            True
        )

        self.progress.sliderPressed.connect(
            self.start_seeking
        )

        self.progress.sliderReleased.connect(
            self.seek_song
        )

        # --------------------------------------------------
        # TOTAL / REMAINING TIME
        # --------------------------------------------------

        self.total_time = QLabel(
            "0:00"
        )

        self.total_time.setMinimumWidth(
            38
        )

        self.total_time.setMaximumWidth(
            48
        )

        self.total_time.setAlignment(
            Qt.AlignRight | Qt.AlignVCenter
        )

        self.total_time.setCursor(
            Qt.PointingHandCursor
        )

        self.total_time.mousePressEvent = (
            self.toggle_remaining_time
        )

        progress_layout.addWidget(
            self.current_time,
            0
        )

        progress_layout.addWidget(
            self.progress,
            1
        )

        progress_layout.addWidget(
            self.total_time,
            0
        )

        player_layout.addLayout(
            progress_layout,
            0
        )

        # ==================================================
        # FINISH
        # ==================================================

        main_layout.addWidget(
            player
        )

        QTimer.singleShot(
            1000,
            self.debug_player_geometry
        )

    def volume_mute_changed(self, muted):
        if muted:
            self.audio_engine.set_volume(0.0)
        else:
            self.audio_engine.set_volume(
                self.volume_slider.value() / 100.0
            )

    # ==================================================
    # SHUFFLE / REPEAT
    # ==================================================

    def debug_player_geometry(self):
        print(
            "PLAYER GEOMETRY AFTER LAYOUT:",
            "player=", self.player.geometry(),
            "controls=", self.play_button.parentWidget().geometry(),
            "previous=", self.previous_button.geometry(),
            "play=", self.play_button.geometry(),
            "next=", self.next_button.geometry()
        )

    def toggle_shuffle(self, checked):

        self.shuffle_enabled = checked

        self.shuffle_button.setText(
            "🔀" if checked else "⇄"
        )

    def toggle_repeat(self, checked):

        self.repeat_enabled = checked

        self.repeat_button.setText(
            "🔁" if checked else "↻"
        )

    # ==================================================
    # LYRICS TOGGLE
    # ==================================================

    def toggle_lyrics(self, checked):

        self.lyrics_visible = checked

        self.lyrics_panel.setVisible(
            checked
        )

        self.lyrics_button.setText(
            "♫ Lyrics"
        )


    # ==================================================
    # SIDEBAR NAVIGATION
    # ==================================================

    def show_home(self):
        """
        Return to the normal full-library view.
        """

        self.showing_favorites = False

        self.search_box.clear()

        self.filtered_songs = (
            self.songs.copy()
        )

        self.heading.setText(
            "Your Music"
        )

        self.display_library()

    def show_library(self):
        """
        Return to the normal library view.
        """

        self.showing_favorites = False

        self.search_box.clear()

        self.filtered_songs = (
            self.songs.copy()
        )

        self.heading.setText(
            "Your Music"
        )

        self.display_library()

    def focus_search(self):
        """
        Focus the existing search field instead of creating
        a separate search page.
        """

        self.search_box.setFocus()
        self.search_box.selectAll()


    # ==================================================
    # LOAD MUSIC
    # ==================================================

    def load_music(self):

        print(
            "Loading Audify library..."
        )

        self.songs = load_library()

        self.filtered_songs = (
            self.songs.copy()
        )

        self.display_library()

        print(
            f"Loaded {len(self.songs)} songs."
        )

    # ==================================================
    # REFRESH
    # ==================================================

    def refresh_music_library(self):

        self.refresh_button.setEnabled(
            False
        )

        self.refresh_button.setText(
            "Scanning..."
        )

        QApplication.processEvents()

        try:

            self.songs = refresh_library()

            self.filtered_songs = (
                self.songs.copy()
            )

            self.search_box.clear()

            self.display_library()

        finally:

            self.refresh_button.setEnabled(
                True
            )

            self.refresh_button.setText(
                "↻   Refresh Library"
            )

    # ==================================================
    # DISPLAY LIBRARY
    # ==================================================

    def clear_library_view(self):

        while self.library_layout.count():

            item = self.library_layout.takeAt(0)

            widget = item.widget()

            if widget:
                widget.deleteLater()

    def get_grid_columns(self):
        """
        Calculate how many music cards fit in the
        available library width.
        """

        available_width = (
            self.scroll_area.viewport().width()
        )

        card_width = 190
        spacing = 15

        columns = max(
            1,
            (
                available_width + spacing
            )
            // (
                card_width + spacing
            )
        )

        return int(columns)


    def display_library(self):

        self.clear_library_view()

        # ==================================================
        # RESPONSIVE GRID
        # ==================================================

        columns = self.get_grid_columns()

        # ==================================================
        # ALBUMS
        # ==================================================

        albums = {}

        for song in self.filtered_songs:

            album = song.get(
                "album",
                "Unknown Album"
            )

            artist = song.get(
                "artist",
                "Unknown Artist"
            )

            key = (
                album,
                artist
            )

            if key not in albums:
                albums[key] = song

        if albums:

            album_title = QLabel(
                "Albums"
            )

            album_title.setObjectName(
                "sectionTitle"
            )

            self.library_layout.addWidget(
                album_title
            )

            album_grid_widget = QWidget()

            album_grid = QGridLayout(
                album_grid_widget
            )

            album_grid.setContentsMargins(
                0,
                0,
                0,
                0
            )

            album_grid.setHorizontalSpacing(
                15
            )

            album_grid.setVerticalSpacing(
                15
            )

            for index, (
                key,
                song
            ) in enumerate(
                albums.items()
            ):

                album, artist = key

                card = AlbumCard(
                    album,
                    artist,
                    self.resolve_artwork(song)
                )

                card.clicked.connect(
                    self.album_clicked
                )

                row = index // columns
                column = index % columns

                album_grid.addWidget(
                    card,
                    row,
                    column
                )

            self.library_layout.addWidget(
                album_grid_widget
            )

        # ==================================================
        # ARTISTS
        # ==================================================

        artists = {}

        for song in self.filtered_songs:

            artist = song.get(
                "artist",
                "Unknown Artist"
            )

            if artist not in artists:
                artists[artist] = song

        if artists:

            artist_title = QLabel(
                "Artists"
            )

            artist_title.setObjectName(
                "sectionTitle"
            )

            self.library_layout.addWidget(
                artist_title
            )

            artist_grid_widget = QWidget()

            artist_grid = QGridLayout(
                artist_grid_widget
            )

            artist_grid.setContentsMargins(
                0,
                0,
                0,
                0
            )

            artist_grid.setHorizontalSpacing(
                15
            )

            artist_grid.setVerticalSpacing(
                15
            )

            for index, (
                artist,
                song
            ) in enumerate(
                artists.items()
            ):

                card = ArtistCard(
                    artist,
                    self.resolve_artwork(song)
                )

                card.clicked.connect(
                    self.artist_clicked
                )

                row = index // columns
                column = index % columns

                artist_grid.addWidget(
                    card,
                    row,
                    column
                )

            self.library_layout.addWidget(
                artist_grid_widget
            )

        # ==================================================
        # EMPTY LIBRARY
        # ==================================================

        if not self.filtered_songs:

            empty = QLabel(
                "No music found."
            )

            empty.setAlignment(
                Qt.AlignCenter
            )

            self.library_layout.addWidget(
                empty
            )

        self.library_layout.addStretch()


    def resizeEvent(self, event):

        super().resizeEvent(event)

        if hasattr(
            self,
            "library_layout"
        ):

            self.display_library()



    # ==================================================
    # PLAYLIST
    # ==================================================

    def playlist_clicked(self, item):

        if item == self.liked_item:

            self.show_favorites()

        elif item == self.recent_item:

            self.show_recent()

        else:

            self.playlist.clearSelection()

    # ==================================================
    # FAVORITES
    # ==================================================

    def show_favorites(self):

        self.showing_favorites = True

        self.heading.setText(
            "Liked Songs"
        )

        self.search_box.clear()

        rows = get_favorite_songs()

        self.filtered_songs = [
            dict(row)
            for row in rows
        ]

        self.display_song_list()

    def show_recent(self):

        self.showing_favorites = False
        self.is_seeking = False

        self.heading.setText(
            "Recently Played"
        )

        self.search_box.clear()

        recent = sorted(
            self.songs,
            key=lambda song: (
                song.get(
                    "last_played",
                    ""
                ) or ""
            ),
            reverse=True
        )

        self.filtered_songs = [
            song
            for song in recent
            if song.get("last_played")
        ]

        self.display_song_list()

    def display_song_list(self):

        self.clear_library_view()

        for song in self.filtered_songs:

            row = QFrame()

            row.setObjectName(
                "songRow"
            )

            row_layout = QHBoxLayout(row)

            row_layout.setContentsMargins(
                10,
                6,
                10,
                6
            )

            artwork = ArtworkLabel(
                self.resolve_artwork(song),
                55
            )

            row_layout.addWidget(
                artwork
            )

            text_layout = QVBoxLayout()

            title = QLabel(
                song.get(
                    "title",
                    "Unknown Title"
                )
            )

            title.setObjectName(
                "songTitle"
            )

            subtitle = QLabel(
                f"{song.get('artist', 'Unknown Artist')} • "
                f"{song.get('album', 'Unknown Album')}"
            )

            subtitle.setObjectName(
                "songSubtitle"
            )

            text_layout.addWidget(title)
            text_layout.addWidget(subtitle)

            row_layout.addLayout(
                text_layout
            )

            row_layout.addStretch()

            heart = QPushButton("♥")

            heart.setObjectName(
                "favoriteButton"
            )

            heart.setFixedSize(40, 40)

            song_id = song.get("id")

            heart.clicked.connect(
                lambda checked=False,
                sid=song_id:
                self.favorite_from_list(sid)
            )

            row_layout.addWidget(
                heart
            )

            row.mousePressEvent = (
                lambda event,
                s=song:
                self.play_from_song(s)
            )

            self.library_layout.addWidget(
                row
            )

        if not self.filtered_songs:

            empty = QLabel(
                "No songs here yet."
            )

            empty.setAlignment(
                Qt.AlignCenter
            )

            self.library_layout.addWidget(
                empty
            )

        self.library_layout.addStretch()

    def favorite_from_list(self, song_id):

        if song_id is None:
            return

        toggle_favorite(song_id)

        if self.showing_favorites:
            self.show_favorites()
        else:
            self.load_music()

    def toggle_current_favorite(self):

        if (
            self.current_index < 0
            or self.current_index >= len(self.songs)
        ):
            return

        song = self.songs[
            self.current_index
        ]

        song_id = song.get("id")

        if song_id is None:
            return

        toggle_favorite(song_id)

        song["favorite"] = (
            0
            if song.get("favorite", 0)
            else 1
        )

        self.update_favorite_button(song)

    def update_favorite_button(self, song):

        is_favorite = bool(
            song.get("favorite", 0)
        )

        if is_favorite:

            self.favorite_button.setText(
                "♥"
            )

        else:

            self.favorite_button.setText(
                "♡"
            )

        self.favorite_button.setChecked(
            is_favorite
        )

    # ==================================================
    # ALBUM / ARTIST
    # ==================================================

    def album_clicked(self, card):

        songs = [
            song
            for song in self.songs
            if song.get("album") == card.album
            and song.get("artist") == card.artist
        ]

        if songs:

            self.play_song(
                self.songs.index(songs[0])
            )

    def artist_clicked(self, card):

        songs = [
            song
            for song in self.songs
            if song.get("artist") == card.artist
        ]

        if songs:

            self.play_song(
                self.songs.index(songs[0])
            )

    # ==================================================
    # SEARCH
    # ==================================================

    def search_music(self, text):

        if self.showing_favorites:
            return

        text = text.lower().strip()

        if not text:

            self.filtered_songs = (
                self.songs.copy()
            )

            self.heading.setText(
                "Your Music"
            )

        else:

            self.filtered_songs = []

            for song in self.songs:

                searchable = " ".join(
                    str(
                        song.get(
                            field,
                            ""
                        )
                    )
                    for field in (
                        "title",
                        "artist",
                        "album",
                        "genre",
                        "year",
                    )
                ).lower()

                if text in searchable:

                    self.filtered_songs.append(
                        song
                    )

            self.heading.setText(
                "Search Results"
            )

        self.display_library()

    # ==================================================
    # DYNAMIC ARTWORK PALETTE
    # ==================================================

    def apply_artwork_palette(self, artwork):
        """
        Extract colours from the current artwork and apply
        them to the entire Audify interface.
        """

        try:

            if artwork:

                palette = extract_palette(
                    artwork
                )

            else:

                palette = default_palette()

            self.current_palette = palette

            print(
                "[PALETTE] Applying artwork palette..."
            )

            for name, value in palette.items():

                print(
                    f"[PALETTE] {name}: {value}"
                )

            # --------------------------------------------------
            # APPLICATION
            # --------------------------------------------------

            stylesheet = get_stylesheet(
                self.current_palette
            )

            self.setStyleSheet(
                stylesheet
                + self.get_player_stylesheet()
            )

            # --------------------------------------------------
            # SEEK BAR
            #
            # Use the SAME artwork-derived accent as the rest
            # of the interface.
            # --------------------------------------------------

            if hasattr(
                self,
                "progress"
            ):

                self.progress.set_palette(
                    self.current_palette.get(
                        "accent",
                        "#7C5CFF"
                    ),
                    self.current_palette.get(
                        "accent_soft",
                        "#394352"
                    )
                )

            # --------------------------------------------------
            # PREVIOUS / NEXT CONTRAST
            #
            # Keep navigation controls readable against the
            # current artwork-derived background.
            # --------------------------------------------------

            self.update_playback_control_contrast()

            # --------------------------------------------------
            # LYRICS
            #
            # Give LyricsPanel the SAME artwork palette.
            # LyricsPanel handles the individual lyric colours.
            # --------------------------------------------------

            if hasattr(
                self,
                "lyrics_panel"
            ):

                self.lyrics_panel.set_palette(
                    self.current_palette
                )

            print(
                "[PALETTE] ✓ Complete UI palette applied"
            )

        except Exception as error:

            print(
                "[PALETTE ERROR]",
                type(error).__name__,
                error
            )

            self.current_palette = default_palette()

            self.setStyleSheet(
                get_stylesheet()
                + self.get_player_stylesheet()
            )

            if hasattr(
                self,
                "progress"
            ):

                self.progress.set_palette(
                    self.current_palette.get(
                        "accent",
                        "#7C5CFF"
                    ),
                    self.current_palette.get(
                        "accent_soft",
                        "#394352"
                    )
                )


    def update_artwork_palette(self, artwork):
        """
        Compatibility wrapper.

        Existing playback code calls update_artwork_palette(),
        so keep that name while using the new palette engine.
        """

        self.apply_artwork_palette(
            artwork
        )


    # ==================================================
    # PLAYBACK CONTROL CONTRAST
    # ==================================================

    def update_playback_control_contrast(self):
        """
        Keep previous/next controls readable against the
        current artwork-derived player background.
        """

        palette = getattr(
            self,
            "current_palette",
            {}
        )

        background = palette.get(
            "background",
            "#08090D"
        )

        try:
            bg = QColor(background)

            # Light background -> dark controls.
            # Dark background -> white controls.
            foreground = (
                "#111318"
                if bg.lightness() >= 145
                else "#FFFFFF"
            )

            hover_background = (
                "rgba(0, 0, 0, 18)"
                if bg.lightness() >= 145
                else "rgba(255, 255, 255, 18)"
            )

        except Exception:
            foreground = "#FFFFFF"
            hover_background = "rgba(255, 255, 255, 18)"

        control_style = f"""
            QPushButton {{
                border: 1px solid transparent;
                border-radius: 22px;
                background: transparent;
                color: {foreground};
                font-size: 29px;
                font-weight: 600;
                padding: 0;
            }}

            QPushButton:hover {{
                border: 1px solid {foreground};
                background: {hover_background};
                color: {foreground};
            }}

            QPushButton:pressed {{
                border: 1px solid {foreground};
                background: {foreground};
                color: {background};
            }}
        """

        # --------------------------------------------------
        # Previous / Next transport buttons
        #
        # These buttons use Qt standard QIcons. A stylesheet
        # "color" rule does not reliably recolor QIcon artwork,
        # so keep the button styling here and explicitly set the
        # icon palette.
        # --------------------------------------------------

        transport_style = f"""
            QPushButton {{
                border: none;
                border-radius: 21px;
                background: transparent;
                padding: 0px;
                margin: 0px;
                font-size: 27px;
                font-weight: 500;
            }}

            QPushButton:hover {{
                border: none;
                background: {hover_background};
            }}

            QPushButton:pressed {{
                border: none;
                background: {hover_background};
            }}
        """

        if hasattr(self, "previous_button"):
            self.previous_button.setStyleSheet(
                transport_style
            )

            palette_obj = self.previous_button.palette()
            palette_obj.setColor(
                QPalette.ButtonText,
                QColor(foreground)
            )
            palette_obj.setColor(
                QPalette.WindowText,
                QColor(foreground)
            )
            self.previous_button.setPalette(
                palette_obj
            )

        if hasattr(self, "next_button"):
            self.next_button.setStyleSheet(
                transport_style
            )

            palette_obj = self.next_button.palette()
            palette_obj.setColor(
                QPalette.ButtonText,
                QColor(foreground)
            )
            palette_obj.setColor(
                QPalette.WindowText,
                QColor(foreground)
            )
            self.next_button.setPalette(
                palette_obj
            )


    # ==================================================
    # PLAY SONG
    # ==================================================

    def play_from_song(self, song):

        if song not in self.songs:
            self.songs.append(song)

        self.play_song(
            self.songs.index(song)
        )

    def resolve_artwork(self, song):
        """
        Return cached artwork only.

        Library rendering never starts network requests.
        Artwork downloading is started explicitly by the user.
        """

        return song.get("artwork")


    def update_artwork_button(self):
        """
        Update the artwork button based on how many songs
        are currently missing artwork.
        """

        if getattr(self, "_artwork_downloading", False):
            return

        missing = [
            song
            for song in self.songs
            if not song.get("artwork")
        ]

        count = len(missing)

        if count == 0:

            self.download_artwork_button.setText(
                "✓   Artwork Complete"
            )

            self.download_artwork_button.setEnabled(
                False
            )

            self.artwork_status_label.setText(
                "All artwork is downloaded"
            )

        else:

            self.download_artwork_button.setText(
                f"▣   Download Artwork ({count} Missing)"
            )

            self.download_artwork_button.setEnabled(
                True
            )

            self.artwork_status_label.setText(
                f"{count} songs are missing artwork"
            )


    def download_all_artwork(self):
        """
        Start downloading artwork only when explicitly requested.
        """

        if getattr(self, "_artwork_downloading", False):
            return

        missing = [
            song
            for song in self.songs
            if not song.get("artwork")
        ]

        if not missing:

            self.update_artwork_button()
            return

        self._artwork_queue = list(missing)

        self._artwork_total = len(
            self._artwork_queue
        )

        self._artwork_completed = 0
        self._artwork_successful = 0
        self._artwork_failed = 0
        self._artwork_downloading = True

        self.download_artwork_button.setEnabled(
            False
        )

        self.artwork_status_label.setText(
            f"Preparing {self._artwork_total} artwork downloads..."
        )

        self._download_next_artwork()


    def _download_next_artwork(self):
        """
        Download one artwork at a time in a background thread.
        """

        if not self._artwork_queue:

            self._artwork_downloading = False

            successful = self._artwork_successful
            failed = self._artwork_failed
            total = self._artwork_total

            self.download_artwork_button.setEnabled(
                True
            )

            if failed == 0:

                self.download_artwork_button.setText(
                    "✓   Artwork Complete"
                )

                self.artwork_status_label.setText(
                    f"Downloaded {successful}/{total} artwork covers"
                )

            else:

                self.download_artwork_button.setText(
                    f"▣   Retry Artwork ({failed} Missing)"
                )

                self.artwork_status_label.setText(
                    f"Downloaded {successful}, failed {failed}"
                )

            self.update_artwork_button()

            return

        song = self._artwork_queue.pop(0)

        current = (
            self._artwork_completed + 1
        )

        total = self._artwork_total

        self.download_artwork_button.setText(
            f"Downloading Artwork "
            f"({current}/{total})"
        )

        self.artwork_status_label.setText(
            f"{song.get('artist', 'Unknown Artist')} - "
            f"{song.get('title', 'Unknown Title')}"
        )

        thread = QThread(self)
        worker = ArtworkWorker(song)

        worker.moveToThread(thread)

        thread.started.connect(
            worker.run
        )

        worker.finished.connect(
            self.artwork_download_finished
        )

        worker.finished.connect(
            thread.quit
        )

        worker.finished.connect(
            worker.deleteLater
        )

        thread.finished.connect(
            thread.deleteLater
        )

        if not hasattr(
            self,
            "_artwork_threads"
        ):
            self._artwork_threads = []

        self._artwork_threads.append(
            thread
        )

        thread.finished.connect(
            lambda:
                self._artwork_threads.remove(thread)
                if thread in self._artwork_threads
                else None
        )

        thread.finished.connect(
            self._artwork_download_thread_finished
        )

        thread.start()


    def _artwork_download_thread_finished(self):
        """
        Continue the artwork queue after a download finishes.
        """

        self._artwork_completed += 1

        self._download_next_artwork()


    def artwork_download_finished(self, song, artwork):
        """
        Store downloaded artwork and update the current UI.

        This function does not start another download.
        """

        if not artwork:
            return

        song["artwork"] = artwork

        # Persist the artwork path in the database so the
        # downloaded artwork survives application restarts.
        try:
            update_artwork(
                song.get("id"),
                artwork
            )
        except Exception as error:
            print(
                "Artwork database persistence error:",
                error
            )

        # Update the player if this is currently playing.
        if (
            self.current_index >= 0
            and self.current_index < len(self.songs)
            and self.songs[self.current_index] is song
        ):
            self.player_artwork.set_artwork(
                artwork
            )

        # Refresh the visible library so the new artwork appears.
        if not getattr(self, "_artwork_downloading", False):
            self.display_library()


    def load_lyrics_async(self, song):

        # ==================================================
        # NEW LYRICS REQUEST
        # ==================================================

        self._lyrics_request_id += 1
        request_id = self._lyrics_request_id

        print()
        print(
            "[LYRICS] Starting request:",
            request_id
        )

        # --------------------------------------------------
        # Cleanly forget old worker references.
        # The QThread remains owned by the window until it
        # finishes.
        # --------------------------------------------------

        self._lyrics_thread = None
        self._lyrics_worker = None

        thread = QThread(self)

        worker = LyricsWorker(
            self.lyrics_manager,
            song,
            request_id
        )

        worker.moveToThread(thread)

        thread.started.connect(
            worker.run
        )

        worker.finished.connect(
            self.lyrics_loaded
        )

        worker.failed.connect(
            self.lyrics_failed
        )

        worker.finished.connect(
            thread.quit
        )

        worker.failed.connect(
            thread.quit
        )

        thread.finished.connect(
            worker.deleteLater
        )

        thread.finished.connect(
            thread.deleteLater
        )

        self._lyrics_thread = thread
        self._lyrics_worker = worker

        thread.start()

        print(
            "[LYRICS] Worker started:",
            request_id
        )



    def lyrics_loaded(self, request_id, lyrics):

        print()
        print(
            "[LYRICS] RESULT RECEIVED"
        )

        print(
            "[LYRICS] request:",
            request_id
        )

        print(
            "[LYRICS] current request:",
            self._lyrics_request_id
        )

        print(
            "[LYRICS] result type:",
            type(lyrics)
        )

        # --------------------------------------------------
        # Ignore genuinely old requests.
        # --------------------------------------------------

        if request_id != self._lyrics_request_id:

            print(
                "[LYRICS] Ignoring stale request:",
                request_id
            )

            return

        # --------------------------------------------------
        # Validate result.
        # --------------------------------------------------

        if lyrics is None:

            print(
                "[LYRICS] No lyrics returned."
            )

            self.lyrics_panel.set_lyrics(
                None
            )

            return

        if isinstance(lyrics, dict):

            lyric_text = lyrics.get(
                "lyrics",
                ""
            )

            print(
                "[LYRICS] synced:",
                repr(
                    lyrics.get("synced")
                )
            )

            print(
                "[LYRICS] lyric length:",
                len(lyric_text)
            )

            print(
                "[LYRICS] first line:",
                repr(
                    lyric_text.splitlines()[0]
                    if lyric_text.splitlines()
                    else ""
                )
            )

        else:

            lyric_text = str(
                lyrics
            )

            print(
                "[LYRICS] string length:",
                len(lyric_text)
            )

        # --------------------------------------------------
        # INSTALL DIRECTLY INTO THE VISIBLE PANEL.
        # --------------------------------------------------

        print(
            "[LYRICS] Installing lyrics into panel..."
        )

        self.lyrics_panel.set_lyrics(
            lyrics
        )

        print(
            "[LYRICS] Panel installation complete."
        )

        # --------------------------------------------------
        # Immediately synchronise with the actual current
        # GStreamer position.
        # --------------------------------------------------

        try:

            if hasattr(
                self,
                "audio_engine"
            ):

                position_ok, position = (
                    self.audio_engine.player.query_position(
                        Gst.Format.TIME
                    )
                )

                if position_ok:

                    current_seconds = (
                        position / Gst.SECOND
                    )

                    print(
                        "[LYRICS] Initial sync:",
                        f"{current_seconds:.3f}s"
                    )

                    self.lyrics_panel.update_position(
                        current_seconds
                    )

        except Exception as error:

            print(
                "[LYRICS] Initial position sync error:",
                error
            )



    def lyrics_failed(self, request_id, error):

        if request_id != self._lyrics_request_id:
            return

        print(
            "[LYRICS] Worker failed:",
            error
        )

        self.lyrics_panel.set_lyrics(
            None
        )



    def update_player_song_text(self, song):

        title = song.get(
            "title",
            "Unknown Title"
        )

        artist = song.get(
            "artist",
            "Unknown Artist"
        )

        album = song.get(
            "album",
            "Unknown Album"
        )

        text = f"{title} • {artist}"

        self.current_song.setToolTip(
            text
        )

        self.current_song.set_text(
            title
        )

        secondary = artist

        if album and album != "Unknown Album":
            secondary = f"{artist}  ·  {album}"

        self.current_artist.setText(
            secondary
        )

        self.current_artist.setToolTip(
            secondary
        )

    def play_song(self, index):

        if (
            not self.songs
            or index < 0
            or index >= len(self.songs)
        ):
            return

        self.current_index = index

        # Every new track starts in total-duration mode.
        self.show_remaining_time = False

        song = self.songs[index]

        if not self.audio_engine.play(
            song["path"]
        ):
            print(
                "Could not start playback:",
                song.get("path", "")
            )
            self.is_playing = False
            self.play_button.setText("▶")
            return

        # Verify the actual GStreamer state immediately after play().
        try:

            gst_state = (
                self.audio_engine.player
                .get_state(0)[1]
            )

            print(
                f"[PLAY SONG] GStreamer state after play(): "
                f"{gst_state.value_nick}"
            )

        except Exception as error:

            print(
                f"[PLAY SONG] State check failed: {error}"
            )

        if song.get("id") is not None:

            update_play_count(
                song["id"]
            )

            # Keep the in-memory song synchronized
            # with the database immediately.
            from datetime import datetime

            song["play_count"] = (
                song.get("play_count", 0) + 1
            )

            song["last_played"] = (
                datetime.now().isoformat()
            )

        self.update_player_song_text(song)

        artwork = self.resolve_artwork(
            song
        )

        self.player_artwork.set_artwork(
            artwork
        )

        # ==================================================
        # ARTWORK -> DYNAMIC UI PALETTE
        # ==================================================

        self.update_artwork_palette(
            artwork
        )

        self.update_favorite_button(
            song
        )

        # ==================================================
        # LOAD LYRICS IN BACKGROUND
        # ==================================================

        self.lyrics_panel.set_lyrics(
            None
        )

        self.load_lyrics_async(
            song
        )

        self.play_button.setText("⏸")

        self.is_playing = True

    # ==================================================
    # PLAY / PAUSE
    # ==================================================

    def toggle_play_pause(self):

        # No song selected yet.
        if self.current_index == -1:

            if self.songs:
                self.play_song(0)

            return

        # Ask GStreamer for the REAL playback state.
        try:

            state = self.audio_engine.player.get_state(0)[1]

        except Exception as error:

            print(
                f"Playback state error: {error}"
            )

            return

        print(
            f"[PLAYBACK BUTTON] GStreamer state: "
            f"{state.value_nick}"
        )

        # --------------------------------------------------
        # CURRENTLY PLAYING -> PAUSE
        # --------------------------------------------------

        if state == Gst.State.PLAYING:

            self.audio_engine.pause()

            self.play_button.setText("▶")

            self.is_playing = False

            print(
                "[PLAYBACK BUTTON] PAUSED"
            )

            return

        # --------------------------------------------------
        # PAUSED / READY / OTHER -> PLAY
        # --------------------------------------------------

        result = self.audio_engine.resume()

        self.play_button.setText("⏸")

        self.is_playing = True

        print(
            "[PLAYBACK BUTTON] PLAYING"
        )

    # ==================================================
    # NEXT / PREVIOUS
    # ==================================================

    def update_duration(self, seconds):

        seconds = int(float(seconds))

        # Keep the real track duration available to the
        # progress/time display.
        self.track_duration = max(
            seconds,
            0
        )

        self.progress.setMaximum(
            max(seconds, 1)
        )

        self.update_duration_label(
            self.progress.value()
        )


    def update_duration_label(self, position):

        duration = getattr(
            self,
            "track_duration",
            self.progress.maximum()
        )

        position = max(
            0,
            min(
                int(float(position)),
                duration
            )
        )

        if self.show_remaining_time:

            remaining = max(
                0,
                duration - position
            )

            minutes = remaining // 60
            secs = remaining % 60

            self.total_time.setText(
                f"-{minutes}:{secs:02d}"
            )

        else:

            minutes = duration // 60
            secs = duration % 60

            self.total_time.setText(
                f"{minutes}:{secs:02d}"
            )
    def handle_song_finished(self):

        # --------------------------------------------------
        # REPEAT CURRENT SONG
        # --------------------------------------------------

        if self.repeat_button.isChecked():

            if self.current_index >= 0:
                self.play_song(
                    self.current_index
                )

            return

        # --------------------------------------------------
        # PLAY QUEUED SONG
        # --------------------------------------------------

        if self.queue:

            next_song = self.queue.pop(0)

            if next_song in self.songs:

                self.play_song(
                    self.songs.index(next_song)
                )

            return

        # --------------------------------------------------
        # NORMAL PLAYBACK
        # --------------------------------------------------

        self.play_next()


    def play_next(self):

        if not self.songs:
            return

        # Repeat the currently playing song.
        if (
            self.repeat_enabled
            and self.current_index >= 0
        ):
            self.play_song(
                self.current_index
            )
            return

        # Pick a random song when shuffle is enabled.
        if self.shuffle_enabled:

            if len(self.songs) == 1:
                index = 0

            else:

                available = [
                    i
                    for i in range(len(self.songs))
                    if i != self.current_index
                ]

                index = random.choice(
                    available
                )

        else:

            index = (
                self.current_index + 1
            ) % len(self.songs)

        self.play_song(index)

    def play_previous(self):

        if not self.songs:
            return

        index = (
            self.current_index - 1
        ) % len(self.songs)

        self.play_song(index)

    # ==================================================
    # QUEUE
    # ==================================================

    def add_to_queue(self, song):

        if song not in self.queue:

            self.queue.append(song)

            print(
                "✓ Added to queue:",
                song.get(
                    "artist",
                    "Unknown Artist"
                ),
                "-",
                song.get(
                    "title",
                    "Unknown Title"
                )
            )

        else:

            print(
                "Already in queue:",
                song.get(
                    "artist",
                    "Unknown Artist"
                ),
                "-",
                song.get(
                    "title",
                    "Unknown Title"
                )
            )


    def play_next_from_queue(self, song):

        if song in self.queue:

            self.queue.remove(song)

        self.queue.insert(
            0,
            song
        )

        print(
            "✓ Will play next:",
            song.get(
                "artist",
                "Unknown Artist"
            ),
            "-",
            song.get(
                "title",
                "Unknown Title"
            )
        )


    def remove_from_queue(self, song):

        if song in self.queue:

            self.queue.remove(song)

            print(
                "✓ Removed from queue:",
                song.get(
                    "artist",
                    "Unknown Artist"
                ),
                "-",
                song.get(
                    "title",
                    "Unknown Title"
                )
            )


    def clear_queue(self):

        self.queue.clear()

        print(
            "✓ Queue cleared"
        )


    # ==================================================
    # POSITION
    # ==================================================

    # ==================================================
    # DIRECT PLAYBACK POSITION SYNC
    # ==================================================

    def sync_playback_position(self):

        try:

            player = self.audio_engine.player

            # ==================================================
            # GET REAL GSTREAMER POSITION
            # ==================================================

            state_result = player.get_state(0)
            current_state = state_result[1]

            success_position, position = (
                player.query_position(
                    Gst.Format.TIME
                )
            )

            success_duration, duration = (
                player.query_duration(
                    Gst.Format.TIME
                )
            )

            if not success_position:
                return

            # ==================================================
            # KEEP SUB-SECOND PRECISION FOR SYNCED LYRICS
            # ==================================================

            raw_seconds = position / Gst.SECOND

            # UI display uses whole seconds.
            display_seconds = int(raw_seconds)

            # ==================================================
            # UPDATE PROGRESS SLIDER
            # ==================================================

            if not self.is_seeking:

                self.progress.blockSignals(True)

                try:

                    maximum = self.progress.maximum()

                    self.progress.setValue(
                        max(
                            0,
                            min(
                                display_seconds,
                                maximum
                            )
                        )
                    )

                finally:

                    self.progress.blockSignals(False)

            # ==================================================
            # UPDATE CURRENT TIME
            # ==================================================

            minutes = display_seconds // 60
            secs = display_seconds % 60

            self.current_time.setText(
                f"{minutes}:{secs:02d}"
            )

            # ==================================================
            # SYNC LYRICS USING PRECISE POSITION
            # ==================================================

            if hasattr(self, "lyrics_panel"):

                self.lyrics_panel.update_position(
                    raw_seconds
                )

            # ==================================================
            # DEBUG
            # ==================================================

            print(
                f"[POSITION] "
                f"{raw_seconds:.3f}s | "
                f"state={current_state.value_nick}"
            )

        except Exception as error:

            print(
                f"[UI POSITION ERROR] "
                f"{type(error).__name__}: {error}"
            )


    def update_position(self, seconds):

        if self.is_seeking:
            return

        try:

            seconds = float(seconds)

        except (
            TypeError,
            ValueError
        ):

            return

        # ==================================================
        # PROGRESS SLIDER
        # ==================================================

        self.progress.blockSignals(True)

        try:

            maximum = self.progress.maximum()

            self.progress.setValue(
                max(
                    0,
                    min(
                        int(seconds),
                        maximum
                    )
                )
            )

        finally:

            self.progress.blockSignals(False)

        # ==================================================
        # CURRENT TIME
        # ==================================================

        minutes = int(seconds) // 60
        secs = int(seconds) % 60

        self.current_time.setText(
            f"{minutes}:{secs:02d}"
        )

        # Update total/remaining duration display.
        self.update_duration_label(
            display_seconds
        )

        # ==================================================
        # SYNCHRONIZED LYRICS
        # ==================================================

        self.lyrics_panel.update_position(
            seconds
        )


    def toggle_remaining_time(self, event):

        self.show_remaining_time = (
            not self.show_remaining_time
        )

        self.update_duration_label(
            self.progress.value()
        )


    def start_seeking(self):

        self.is_seeking = True


    def seek_song(self):

        seconds = int(
            self.progress.value()
        )

        print(
            f"[SEEK] Requesting seek to {seconds:.3f}s"
        )

        # ==================================================
        # SEEK IN GSTREAMER
        # ==================================================

        success = self.audio_engine.seek(
            seconds
        )

        if success:

            print(
                f"[SEEK] ✓ Seek accepted: {seconds:.3f}s"
            )

            # ==================================================
            # IMMEDIATELY SYNC LYRICS
            # ==================================================
            # Do not wait for the next 100 ms timer tick.
            # This makes the lyric highlight jump immediately
            # to the correct line after seeking.

            if hasattr(
                self,
                "lyrics_panel"
            ):

                self.lyrics_panel.update_position(
                    float(seconds)
                )

        else:

            print(
                f"[SEEK] ✗ Seek failed: {seconds:.3f}s"
            )

        self.is_seeking = False


    # ==================================================
    # VOLUME
    # ==================================================

    def change_volume(self, value):

        self.audio_engine.set_volume(
            value / 100
        )

    # ==================================================
    # FORMAT TIME
    # ==================================================

    def format_time(self, seconds):

        seconds = int(seconds)

        minutes = seconds // 60
        seconds %= 60

        return f"{minutes}:{seconds:02d}"


# ======================================================
# START
# ======================================================

app = QApplication(sys.argv)

window = AudifyWindow()

window.show()

sys.exit(app.exec())
