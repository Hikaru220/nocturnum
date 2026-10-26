"""Language entries for package 2 (extra HUD, comfort, performance, anarchy)."""


def pack(a, mod, st, opt, scale):
    a("category.anarchy", "Анархия", "Anarchy")
    for k, ru, en in [
        ("hud.food", "Сытость", "Hunger"),
        ("hud.saturation", "Насыщение", "Saturation"),
        ("hud.combo", "комбо", "combo"),
        ("hud.blocks", "бл.", "blocks"),
        ("hud.session", "Сессия", "Session"),
        ("hud.total", "Всего", "Total"),
        ("hud.timerDone", "Таймер закончился", "Timer finished"),
        ("hud.confirmPress", "Нажми ещё раз, чтобы подтвердить", "Press again to confirm"),
        ("hint.memory", "Мало памяти: увеличь её в лаунчере", "Low memory: give the game more in the launcher"),
        ("hint.lowFps", "FPS низкий: включи режим «Буст»", "Low FPS: turn on Boost mode"),
        ("hint.ok", "Всё в порядке", "All good"),
        ("warn.serverRules", "На части серверов запрещено. Проверь правила.", "Banned on some servers. Check the rules."),
        ("anarchy.blocked", "Эта функция заблокирована на этом сервере", "This feature is blocked on this server"),
        ("anarchy.warning", "На некоторых серверах эта функция запрещена. Проверь правила сервера.",
         "On some servers this feature is forbidden. Check the server rules."),
    ]:
        a(k, ru, en)

    mod("itemcounter", "Счётчик предметов", "Item counter", "Сколько у тебя в инвентаре выбранных предметов: жемчуг, яблоки, тотемы, стрелы",
        "How many of the chosen items you carry: pearls, apples, totems, arrows")
    scale("itemcounter")
    for i, (ru, en) in {"pearl": ("Жемчуг Края", "Ender pearls"), "gapple": ("Золотые яблоки", "Golden apples"), "totem": ("Тотемы", "Totems"),
                        "xp": ("Пузырьки опыта", "XP bottles"), "arrow": ("Стрелы", "Arrows"), "firework": ("Фейерверки", "Fireworks"),
                        "splash": ("Взрывные зелья", "Splash potions"), "potion": ("Зелья", "Potions")}.items():
        st("itemcounter", i, ru, en)
    st("itemcounter", "warnBelow", "Предупреждение ниже", "Warn below", "Красное число, если предметов не больше этого", "Number turns red at or below this")

    mod("saturation", "Сытость", "Hunger", "Сытость и насыщение сейчас и сколько восстановит еда в руке", "Hunger and saturation now and what the food in hand restores")
    scale("saturation")
    mod("combo", "Комбо", "Combo", "Сколько ударов подряд ты нанёс без ответного удара", "How many hits in a row you landed without being hit back")
    scale("combo")
    st("combo", "resetAfter", "Сброс через", "Reset after", "Через сколько секунд без ударов комбо сбрасывается", "Seconds without a hit before the combo resets")
    mod("cooldowns", "Перезарядки", "Cooldowns", "Сколько осталось до повторного использования: жемчуг, щит, хорус, яблоки",
        "Time left until reuse: pearl, shield, chorus fruit, apples")
    scale("cooldowns")
    mod("bowpower", "Сила лука", "Bow power", "Насколько натянут лук или арбалет", "How far the bow or crossbow is drawn")
    scale("bowpower")
    mod("memory", "Память ПК", "Memory", "Сколько оперативной памяти занимает игра", "How much memory the game uses")
    scale("memory")
    st("memory", "view", "Вид", "View")
    opt("memory", "view", "number", "Число", "Number")
    opt("memory", "view", "bar", "Полоска", "Bar")
    mod("reach", "Дистанция удара", "Reach display", "Расстояние уже нанесённого удара. На части серверов запрещено", "Distance of the hit you just landed. Banned on some servers")
    scale("reach")
    mod("compass", "Компас", "Compass", "Направление взгляда: С / Ю / З / В и градусы", "Facing direction: N / S / W / E and degrees")
    scale("compass")
    mod("playtime", "Время игры", "Play time", "Сколько ты играешь в этой сессии и всего", "How long you play this session and in total")
    scale("playtime")
    mod("stopwatch", "Секундомер", "Stopwatch", "Секундомер и таймер с запуском по клавише", "Stopwatch and timer started by a key")
    scale("stopwatch")
    st("stopwatch", "startStop", "Старт / стоп", "Start / stop")
    st("stopwatch", "reset", "Сброс", "Reset")
    st("stopwatch", "timer", "Таймер", "Timer", "Секунды обратного отсчёта, 0 — обычный секундомер", "Countdown seconds, 0 = plain stopwatch")
    mod("perfmonitor", "Монитор", "Perf monitor", "FPS, время кадра, память и подсказка, что тормозит", "FPS, frame time, memory and a hint about what slows the game")
    scale("perfmonitor")
    st("perfmonitor", "view", "Вид", "View")
    opt("perfmonitor", "view", "compact", "Компактный", "Compact")
    opt("perfmonitor", "view", "detailed", "Подробный", "Detailed")

    mod("commandbinds", "Бинды команд", "Command binds", "Отправляет команду или сообщение по клавише; опасные команды просят подтверждение",
        "Sends a command or message on a key; dangerous commands ask to confirm")
    for i in range(1, 21):
        st("commandbinds", f"key{i}", f"Клавиша {i}", f"Key {i}", f"Клавиша для бинда {i}", f"Key for bind {i}")
        st("commandbinds", f"text{i}", f"Команда {i}", f"Command {i}", "Текст: /команда или сообщение", "Text: /command or a message")
    mod("bgfps", "FPS в фоне", "Background FPS", "Снижает FPS, когда игра свёрнута — ноутбук меньше греется", "Lowers FPS when the game is in the background")
    st("bgfps", "limit", "Лимит FPS", "FPS limit")
    mod("boost", "Буст", "Boost", "Одной кнопкой режет частицы, тени, облака и дальность — для замесов", "One switch cuts particles, shadows, clouds and distance for big fights")
    st("boost", "autoBelow", "Авто при FPS ниже", "Auto below", "Сам включается, если FPS ниже этого значения 5 секунд, 0 — выкл", "Turns on by itself when FPS stays below this for 5 s, 0 = off")

    mod("fullbright", "Fullbright", "Fullbright", "Делает всё вокруг светлым, даже в тёмных пещерах и ночью", "Makes everything bright, even in dark caves and at night")
    st("fullbright", "method", "Способ", "Method")
    opt("fullbright", "method", "effect", "Эффект", "Effect")
    opt("fullbright", "method", "gamma", "Гамма", "Gamma")
    st("fullbright", "strength", "Сила", "Strength")


def menu(a, mod, st):
    for k, ru, en in [
        ("menu.tagline", "Играй красиво", "Play beautifully"),
        ("menu.singleplayer", "Одиночная игра", "Singleplayer"),
        ("menu.multiplayer", "Сетевая игра", "Multiplayer"),
        ("menu.options", "Настройки", "Options"),
        ("menu.quit", "Выход", "Quit"),
        ("menu.account", "Аккаунт", "Account"),
        ("menu.quick", "Быстрое подключение", "Quick connect"),
    ]:
        a(k, ru, en)
    mod("mainmenu", "Главное меню", "Main menu", "Своё главное меню: ночное небо, быстрые кнопки и быстрое подключение к серверам",
        "Own title screen: night sky, quick buttons and quick connect to servers")


def fx(a, mod, st, opt, scale):
    a("category.anarchy", "Анархия", "Anarchy")
    mod("jumpcircles", "Jump Circles", "Jump Circles", "Круг под ногами, когда прыгаешь", "A ring under your feet when you jump")
    st("jumpcircles", "type", "Тип", "Type")
    opt("jumpcircles", "type", "ring", "Кольцо", "Ring")
    opt("jumpcircles", "type", "wave", "Волна", "Wave")
    opt("jumpcircles", "type", "double", "Двойное", "Double")
    st("jumpcircles", "radius", "Радиус", "Radius")
    st("jumpcircles", "thickness", "Толщина", "Thickness")
    st("jumpcircles", "life", "Время жизни", "Lifetime")
    st("jumpcircles", "color", "Цвет", "Colour")
    st("jumpcircles", "maxCount", "Макс. кругов", "Max circles", "Не больше стольких кругов одновременно", "At most this many circles at once")
    mod("trails", "Trails", "Trails", "Цветной след за твоим персонажем", "A coloured trail behind your character")
    st("trails", "length", "Длина", "Length")
    st("trails", "width", "Ширина", "Width")
    st("trails", "color", "Цвет", "Colour")
    st("trails", "onlySprint", "Только при беге", "Only when sprinting")
    st("trails", "gradient", "Градиент", "Gradient")
    mod("chinahat", "China Hat", "China Hat", "Конусная «шляпа» над твоим персонажем", "A cone hat above your character")
    st("chinahat", "shape", "Форма", "Shape")
    opt("chinahat", "shape", "cone", "Конус", "Cone")
    opt("chinahat", "shape", "halo", "Нимб", "Halo")
    opt("chinahat", "shape", "pyramid", "Пирамида", "Pyramid")
    st("chinahat", "color", "Цвет", "Colour")
    st("chinahat", "rotate", "Вращение", "Rotate")
    st("chinahat", "firstPerson", "От первого лица", "First person")
    mod("blockoverlay", "BlockOverlay", "BlockOverlay", "Красивая обводка блока, на который смотришь", "A nice outline of the block you look at")
    st("blockoverlay", "color", "Цвет", "Colour")
    st("blockoverlay", "thickness", "Толщина", "Thickness")
    st("blockoverlay", "pulse", "Пульс", "Pulse")
    mod("hiteffects", "Hit Bubbles", "Hit Bubbles", "Частицы в месте удара и волна вокруг цели", "Particles at the hit point and a wave around the target")
    st("hiteffects", "shape", "Форма", "Shape")
    opt("hiteffects", "shape", "sparks", "Искры", "Sparks")
    opt("hiteffects", "shape", "hearts", "Сердца", "Hearts")
    opt("hiteffects", "shape", "stars", "Звёзды", "Stars")
    opt("hiteffects", "shape", "dust", "Пыль цвета", "Coloured dust")
    st("hiteffects", "count", "Количество", "Count")
    st("hiteffects", "color", "Цвет", "Colour")
    st("hiteffects", "wave", "Волна", "Wave")
    mod("hitcolor", "HitColor", "HitColor", "Цвет вспышки, когда по кому-то попадаешь", "Flash colour when you hit someone")
    st("hitcolor", "color", "Цвет", "Colour")


def media(a, mod, st, opt, scale):
    a("media.sample", "Название трека", "Track title")
    mod("mediaplayer", "Медиаплеер", "Media player", "Какой трек сейчас играет на ПК (Яндекс Музыка, Spotify, браузер): обложка, название, исполнитель, прогресс. Работает через лаунчер",
        "The track playing on your PC (Yandex Music, Spotify, browser): cover, title, artist, progress. Works through the launcher")
    scale("mediaplayer")
    st("mediaplayer", "view", "Вид", "View")
    opt("mediaplayer", "view", "compact", "Компактный", "Compact")
    opt("mediaplayer", "view", "large", "Большой", "Large")
    st("mediaplayer", "hidePaused", "Скрывать при паузе", "Hide when paused")
    st("mediaplayer", "prevKey", "Клавиша: назад", "Key: previous", "Горячая клавиша предыдущего трека", "Hotkey for the previous track")
    st("mediaplayer", "toggleKey", "Клавиша: пауза", "Key: pause", "Горячая клавиша паузы и воспроизведения", "Hotkey for pause / play")
    st("mediaplayer", "nextKey", "Клавиша: вперёд", "Key: next", "Горячая клавиша следующего трека", "Hotkey for the next track")
