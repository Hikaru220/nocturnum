#!/usr/bin/env python3
"""Generates assets/nocturnum/lang/{ru,en}.json from one table so both languages stay in sync.

Table format: key -> (ru, en). Missing keys show up as the raw key in game and are caught
by tools/check_lang.py in CI.
"""
import json, os, sys

T = {}


def a(k, ru, en):
    T[k] = (ru, en)


def mod(i, ru, en, dru, den):
    a(f"module.{i}", ru, en)
    a(f"module.{i}.desc", dru, den)


def st(m, s, ru, en, dru="", den=""):
    a(f"module.{m}.{s}", ru, en)
    a(f"module.{m}.{s}.desc", dru or ru, den or en)


def opt(m, s, o, ru, en):
    a(f"module.{m}.{s}.{o}", ru, en)


def scale(m):
    st(m, "scale", "Масштаб", "Scale", "Размер элемента на экране", "Size of the element on screen")


# ---- categories
a("category.hud", "HUD", "HUD")
a("category.visuals", "Визуалы", "Visuals")
a("category.camera", "Камера", "Camera")
a("category.sounds", "Звуки", "Sounds")
a("category.comfort", "Удобства", "Comfort")
a("category.other", "Прочее", "Other")
a("category.favorites", "Избранное", "Favorites")

# ---- gui strings
for k, ru, en in [
    ("gui.search", "Поиск", "Search"),
    ("gui.dragHint", "Тяни, чтобы переместить. Клик — свернуть.", "Drag to move. Click to collapse."),
    ("gui.moveOnScreen", "Переместить на экране", "Move on screen"),
    ("gui.advanced", "Дополнительно", "Advanced"),
    ("gui.resetModule", "Сбросить модуль", "Reset module"),
    ("gui.reset", "Сбросить к стандартному", "Reset to default"),
    ("gui.key", "Клавиша", "Key"),
    ("gui.key.desc", "Клавиша включения модуля", "Key that toggles the module"),
    ("gui.key.hint", "Средний клик по модулю — назначить. Esc — убрать.", "Middle click a module to bind. Esc clears."),
    ("gui.none", "нет", "none"),
    ("gui.hold", "зажать", "hold"),
    ("gui.toggle", "перекл.", "toggle"),
    ("gui.accent", "Акцент", "Accent"),
    ("gui.alpha", "Прозр.", "Alpha"),
    ("gui.rainbow", "Радуга", "Rainbow"),
    ("gui.disableVisuals", "Выкл. визуалы", "Visuals off"),
    ("gui.profiles", "Профили", "Profiles"),
    ("gui.profileName", "Имя профиля", "Profile name"),
    ("gui.profileLoaded", "Профиль загружен", "Profile loaded"),
    ("gui.profileSaved", "Профиль сохранён", "Profile saved"),
    ("gui.copyCode", "Копировать код", "Copy code"),
    ("gui.pasteCode", "Вставить код", "Paste code"),
    ("gui.codeCopied", "Код скопирован", "Code copied"),
    ("gui.codeLoaded", "Настройки загружены", "Settings loaded"),
    ("gui.codeBad", "Неверный код", "Invalid code"),
    ("gui.hudEditor", "Редактор HUD", "HUD editor"),
    ("gui.editorHint", "Тяни элементы • колесо — масштаб • ПКМ — настройки • Ctrl+Z — отмена",
     "Drag elements • wheel = scale • right click = settings • Ctrl+Z = undo"),
    ("gui.off", "выкл", "off"),
    ("notify.enabled", "Модуль включён", "Module enabled"),
    ("notify.disabled", "Модуль выключен", "Module disabled"),
    ("notify.sample", "Пример уведомления", "Sample notification"),
    ("layout.minimal", "Минимал", "Minimal"),
    ("layout.pvp", "PvP", "PvP"),
    ("layout.streamer", "Стример", "Streamer"),
    ("preset.minimal", "Минимал", "Minimal"),
    ("preset.bright", "Ярко", "Bright"),
    ("preset.pvp", "Как в PvP-клиентах", "PvP style"),
    ("hud.overworld", "Верхний мир", "Overworld"),
    ("hud.nether", "Незер", "Nether"),
    ("hud.facing", "Куда смотришь", "Facing"),
    ("hud.biome", "Биом", "Biome"),
    ("hud.lmb", "ЛКМ", "LMB"),
    ("hud.rmb", "ПКМ", "RMB"),
    ("comfort.died", "Ты умер на координатах", "You died at"),
    ("common.done", "Готово", "Done"),
    ("common.save", "Сохр.", "Save"),
]:
    a(k, ru, en)

# ---- ClickGUI
mod("clickgui", "ClickGUI", "ClickGUI", "Настройки самого меню: тема, цвет, клавиша открытия, размытие",
    "Menu settings: theme, colour, open key, blur")
st("clickgui", "theme", "Тема", "Theme", "Готовая цветовая тема всего мода", "Colour theme for the whole mod")
for o, ru, en in [("nocturnum", "Nocturnum", "Nocturnum"), ("ice", "Ice", "Ice"), ("toxic", "Toxic", "Toxic"),
                  ("sakura", "Sakura", "Sakura"), ("mono", "Mono", "Mono"), ("red", "Красная", "Red"),
                  ("blue", "Синяя", "Blue"), ("custom", "Своя", "Custom")]:
    opt("clickgui", "theme", o, ru, en)
st("clickgui", "customAccent", "Свой акцент", "Custom accent", "Цвет для темы «Своя»", "Colour used by the Custom theme")
st("clickgui", "blur", "Размытие фона", "Background blur", "Размывать игру за меню", "Blur the game behind the menu")
st("clickgui", "animSpeed", "Скорость анимаций", "Animation speed", "Общий множитель скорости всех анимаций",
   "Global speed multiplier of all animations")
st("clickgui", "noAnim", "Без анимаций", "No animations", "Отключает движение — для слабых ПК", "Turns motion off for weak PCs")
st("clickgui", "lowEnd", "Слабый ПК", "Low-end mode", "Отключает свечение и размытие одной кнопкой",
   "Disables glow and blur in one click")
st("clickgui", "language", "Язык мода", "Mod language", "Русский или английский (авто — как в игре)",
   "Russian or English (auto follows the game)")
opt("clickgui", "language", "auto", "Авто", "Auto")
opt("clickgui", "language", "ru", "Русский", "Russian")
opt("clickgui", "language", "en", "English", "English")
st("clickgui", "customFont", "Свой шрифт", "Custom font", "Сглаженный шрифт Manrope вместо пиксельного",
   "Smooth Manrope font instead of the pixel font")
st("clickgui", "hudOpacity", "Прозрачность плашек", "Plate opacity", "Непрозрачность тёмного «стекла» HUD",
   "Opacity of the dark glass of HUD plates")
st("clickgui", "hudBorder", "Обводка", "Border", "Толщина обводки плашек, 0 — без неё", "Plate border thickness, 0 = none")
st("clickgui", "hudRadius", "Скругление", "Corner radius", "Скругление углов плашек", "Corner radius of plates")
st("clickgui", "hudGlow", "Свечение", "Glow", "Сила свечения вокруг активных плашек", "Strength of the glow around active plates")

# ---- HUD modules
mod("watermark", "Watermark", "Watermark", "Плашка в углу экрана с логотипом, ником, FPS, пингом и временем",
    "Corner plate with logo, nickname, FPS, ping and time")
scale("watermark")
st("watermark", "showName", "Ник", "Nickname")
st("watermark", "showFps", "FPS", "FPS")
st("watermark", "showPing", "Пинг", "Ping")
st("watermark", "showTime", "Время", "Time")
st("watermark", "style", "Стиль", "Style", "Плашка или только текст", "Plate or text only")
opt("watermark", "style", "plate", "Плашка", "Plate")
opt("watermark", "style", "text", "Текст", "Text")

mod("arraylist", "ArrayList", "ArrayList", "Список включённых модулей справа сверху", "List of enabled modules at the top right")
scale("arraylist")
st("arraylist", "sort", "Сортировка", "Sort")
opt("arraylist", "sort", "length", "По длине", "By length")
opt("arraylist", "sort", "alphabet", "По алфавиту", "Alphabetical")
st("arraylist", "gradient", "Градиент", "Gradient", "Плавный переход между двумя цветами темы", "Smooth blend between two theme colours")
st("arraylist", "includeHud", "Показывать HUD-модули", "Include HUD modules", "Добавлять в список и HUD-элементы", "Also list HUD elements")
st("arraylist", "background", "Фон", "Background", "Подложка под строками", "Plate behind rows")

mod("keybinds", "Keybinds", "Keybinds", "Показывает, какие клавиши назначены на модули", "Shows which keys are bound to modules")
scale("keybinds")
st("keybinds", "onlyEnabled", "Только включённые", "Only enabled", "Скрыть выключенные модули", "Hide disabled modules")

mod("armor", "Броня", "Armor", "Надетая броня и её прочность; мигает, когда вещь скоро сломается",
    "Worn armour and durability; blinks when about to break")
scale("armor")
st("armor", "layout", "Расположение", "Layout")
opt("armor", "layout", "vertical", "Вертикально", "Vertical")
opt("armor", "layout", "horizontal", "Горизонтально", "Horizontal")
st("armor", "format", "Формат", "Format")
opt("armor", "format", "percent", "Проценты", "Percent")
opt("armor", "format", "numbers", "Числа", "Numbers")
st("armor", "warn", "Предупреждение", "Warning", "Мигать при прочности ниже 15%", "Blink under 15% durability")

mod("potions", "Зелья", "Potions", "Активные эффекты, их уровень и сколько осталось", "Active effects, their level and time left")
scale("potions")
st("potions", "icons", "Иконки", "Icons")
st("potions", "colorByType", "Цвет по типу", "Colour by type", "Подсветка в цвет эффекта", "Tint by effect colour")

mod("keystrokes", "Keystrokes", "Keystrokes", "Показывает нажатия WASD, пробела и кнопок мыши", "Shows WASD, space and mouse button presses")
scale("keystrokes")
st("keystrokes", "showCps", "CPS в кнопках", "CPS in buttons")
st("keystrokes", "showSpace", "Пробел", "Space bar")
st("keystrokes", "showMouse", "Кнопки мыши", "Mouse buttons")

mod("cps", "CPS", "CPS", "Сколько кликов в секунду ты делаешь", "How many clicks per second you make")
scale("cps")
st("cps", "show", "Что показывать", "Show")
opt("cps", "show", "left", "ЛКМ", "LMB")
opt("cps", "show", "right", "ПКМ", "RMB")
opt("cps", "show", "both", "Обе", "Both")

mod("fpsping", "FPS и пинг", "FPS & ping", "Кадры в секунду и задержка до сервера", "Frames per second and latency to the server")
scale("fpsping")
st("fpsping", "fps", "FPS", "FPS")
st("fpsping", "ping", "Пинг", "Ping")

mod("coords", "Координаты", "Coordinates", "X / Y / Z, направление взгляда и биом", "X / Y / Z, facing direction and biome")
scale("coords")
st("coords", "direction", "Направление", "Direction")
st("coords", "biome", "Биом", "Biome")
st("coords", "netherPortal", "Координаты портала", "Portal coordinates", "В Незере и верхнем мире — координаты для портала",
   "In the Nether / Overworld shows the matching portal position")

mod("notifications", "Уведомления", "Notifications", "Всплывающие плашки: модуль включён, профиль сохранён и т. п.",
    "Pop-up plates: module enabled, profile saved, etc.")
scale("notifications")
st("notifications", "duration", "Длительность", "Duration")
st("notifications", "onToggle", "При переключении модуля", "On module toggle")
st("notifications", "sound", "Звук", "Sound")

mod("targethud", "Target HUD", "Target HUD", "Ник, здоровье и броня игрока, которого ты ударил. На части серверов запрещён",
    "Name, health and armour of whoever you hit. Banned on some servers")
scale("targethud")
st("targethud", "style", "Стиль", "Style")
opt("targethud", "style", "compact", "Компактный", "Compact")
opt("targethud", "style", "large", "Большой", "Large")
st("targethud", "hold", "Показывать, с", "Hold, s", "Сколько секунд держать плашку после удара", "How long the plate stays after a hit")

mod("itemhand", "Предмет", "Held item", "Название, прочность и количество предмета в руке", "Name, durability and count of the held item")
scale("itemhand")
mod("totems", "Тотемы", "Totem counter", "Сколько тотемов осталось в инвентаре", "How many totems are left in your inventory")
scale("totems")
st("totems", "warnZero", "Предупреждение при 0", "Warn at 0")
mod("clock", "Часы", "Clock & session", "Реальное время и время в игре", "Real time and play time")
scale("clock")
st("clock", "hour12", "12 часов", "12-hour")
st("clock", "session", "Время сессии", "Session time")
mod("speed", "Скорость", "Speed", "Текущая скорость движения", "Current movement speed")
scale("speed")
st("speed", "unit", "Единицы", "Unit")
opt("speed", "unit", "bps", "блоков/с", "blocks/s")
opt("speed", "unit", "kmh", "км/ч", "km/h")

# ---- visuals / camera / comfort / other
mod("crosshair", "Crosshair", "Crosshair", "Свой прицел: форма, цвет, размер, точка в центре", "Custom crosshair: shape, colour, size, centre dot")
st("crosshair", "shape", "Форма", "Shape")
for o, ru, en in [("cross", "Крест", "Cross"), ("dot", "Точка", "Dot"), ("circle", "Круг", "Circle"), ("square", "Квадрат", "Square")]:
    opt("crosshair", "shape", o, ru, en)
st("crosshair", "color", "Цвет", "Colour")
st("crosshair", "size", "Размер", "Size")
st("crosshair", "thickness", "Толщина", "Thickness")
st("crosshair", "gap", "Зазор", "Gap")
st("crosshair", "centerDot", "Точка в центре", "Centre dot")
st("crosshair", "outline", "Обводка", "Outline")
st("crosshair", "expand", "Расширение при ударе", "Expand on attack", "Прицел чуть расширяется, когда бьёшь", "Crosshair widens when you attack")

mod("zoom", "Zoom", "Zoom", "Приближение по клавише, как подзорная труба", "Zoom with a key, like a spyglass")
st("zoom", "factor", "Кратность", "Factor")
st("zoom", "smooth", "Плавно", "Smooth")
st("zoom", "scroll", "Колесо меняет кратность", "Wheel changes factor")
mod("togglesprint", "Toggle Sprint", "Toggle Sprint", "Бег без зажатой клавиши", "Sprint without holding the key")
st("togglesprint", "sneakToggle", "Toggle Sneak", "Toggle Sneak", "Приседание по нажатию, без удержания", "Sneak on press, no holding")
mod("deathcoords", "Death Coords", "Death Coords", "После смерти пишет в чат координаты, где ты умер", "After death prints where you died in your chat")
mod("discordrpc", "Discord RPC", "Discord RPC", "Показывает в Discord, что ты играешь через лаунчер", "Shows in Discord that you play through the launcher")
st("discordrpc", "showServer", "Показывать сервер", "Show server")
mod("streamer", "Streamer", "Streamer", "Скрывает твой ник, IP сервера и координаты — для стримов и видео",
    "Hides your nickname, server IP and coordinates for streams and videos")
st("streamer", "hideName", "Скрывать ник", "Hide name")
st("streamer", "hideServer", "Скрывать сервер", "Hide server")
st("streamer", "hideCoords", "Скрывать координаты", "Hide coordinates")


mod("loading", "Экран загрузки", "Loading screen", "Свой экран загрузки вместо красного экрана Mojang: логотип, полоска и совет дня",
    "Own start-up screen instead of the red Mojang one: logo, progress bar and a tip")
st("loading", "tips", "Совет дня", "Tip of the day")
st("loading", "glow", "Свечение", "Glow")
a("loading.caption", "Загрузка ресурсов", "Loading resources")
for i, (ru, en) in enumerate([
    ("Right Shift открывает меню мода — настраивай всё, не выходя из игры.", "Press Right Shift to open the mod menu — tune everything without leaving the game."),
    ("Колёсико в редакторе HUD меняет размер элемента, Ctrl+Z отменяет.", "In the HUD editor the wheel resizes an element, Ctrl+Z undoes."),
    ("Правый клик по модулю раскрывает его настройки, средний — назначает клавишу.", "Right click a module for its settings, middle click to bind a key."),
    ("Профили настроек можно скопировать кодом и отправить другу.", "Settings profiles can be copied as a code and sent to a friend."),
    ("Режим «Слабый ПК» одним кликом отключает свечение и размытие.", "Low-end mode turns glow and blur off in one click."),
    ("Streamer скрывает ник, IP сервера и координаты для стримов.", "Streamer hides your name, server IP and coordinates for streams."),
    ("Поиск в меню находит и отдельные настройки, например «цвет».", "Menu search also finds single settings, e.g. \"colour\"."),
    ("Все визуалы только для глаз: мод ничего не нажимает за тебя.", "All visuals are for the eyes only: the mod never presses anything for you."),
], start=1):
    a(f"loading.tip.{i}", ru, en)


import lang_pack
lang_pack.pack(a, mod, st, opt, scale)
lang_pack.menu(a, mod, st)
lang_pack.fx(a, mod, st, opt, scale)
lang_pack.media(a, mod, st, opt, scale)
lang_pack.anarchy(a, mod, st, opt, scale)
lang_pack.sounds(a, mod, st, opt, scale)
lang_pack.view(a, mod, st, opt, scale)
lang_pack.social(a, mod, st, opt, scale)
lang_pack.labels(a, mod, st, opt, scale)


def write(root):
    d = os.path.join(root, "assets", "nocturnum", "lang")
    os.makedirs(d, exist_ok=True)
    for i, lang in enumerate(("ru", "en")):
        with open(os.path.join(d, f"{lang}.json"), "w", encoding="utf-8") as f:
            json.dump({k: v[i] for k, v in sorted(T.items())}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    write(sys.argv[1] if len(sys.argv) > 1 else "../common/src/main/resources")
    print(len(T), "keys")
