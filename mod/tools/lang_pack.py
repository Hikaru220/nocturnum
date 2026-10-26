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


def anarchy(a, mod, st, opt, scale):
    mod("esp", "ESP", "ESP", "Подсвечивает игроков и мобов рамкой, в том числе за стенами", "Outlines players and mobs, also behind walls")
    st("esp", "style", "Стиль", "Style")
    opt("esp", "style", "frame2d", "2D-рамка", "2D frame")
    opt("esp", "style", "box3d", "3D-рамка", "3D box")
    for k, ru, en in [("players", "Игроки", "Players"), ("mobs", "Мобы", "Mobs"), ("animals", "Животные", "Animals"), ("items", "Предметы", "Items")]:
        st("esp", k, ru, en)
    for k, ru, en in [("playerColor", "Цвет игроков", "Player colour"), ("mobColor", "Цвет мобов", "Mob colour"), ("animalColor", "Цвет животных", "Animal colour"), ("itemColor", "Цвет предметов", "Item colour")]:
        st("esp", k, ru, en)
    st("esp", "range", "Дальность", "Range")
    st("esp", "thickness", "Толщина", "Thickness")
    mod("targetesp", "Target ESP", "Target ESP", "Выделяет игрока, которого ты бьёшь: вращающийся круг или маркер над ним", "Marks the player you hit: a rotating ring or a marker above")
    st("targetesp", "marker", "Маркер", "Marker")
    opt("targetesp", "marker", "ring", "Кольцо", "Ring")
    opt("targetesp", "marker", "diamond", "Ромб", "Diamond")
    opt("targetesp", "marker", "glow", "Свечение", "Glow")
    st("targetesp", "color", "Цвет", "Colour")
    st("targetesp", "speed", "Скорость вращения", "Spin speed")
    st("targetesp", "hold", "Время после удара", "Time after hit")
    mod("hitboxes", "Хитбоксы", "Hitboxes", "Рисует хитбоксы существ; цвет меняется от зелёного к красному по здоровью", "Draws entity hitboxes; colour runs from green to red with health")
    st("hitboxes", "onlyPlayers", "Только игроки", "Players only")
    st("hitboxes", "thickness", "Толщина линий", "Line thickness")
    st("hitboxes", "lookLine", "Линия взгляда", "Look line")
    st("hitboxes", "range", "Дальность", "Range")
    mod("minimap", "Миникарта", "Minimap", "Карта местности вокруг с точками игроков и мобов", "A map of the terrain around you with dots for players and mobs")
    scale("minimap")
    st("minimap", "shape", "Форма", "Shape")
    opt("minimap", "shape", "square", "Квадратная", "Square")
    opt("minimap", "shape", "round", "Круглая", "Round")
    st("minimap", "radius", "Радиус", "Radius")
    st("minimap", "size", "Размер", "Size")
    st("minimap", "showPlayers", "Игроки", "Players")
    st("minimap", "showMobs", "Мобы", "Mobs")
    st("minimap", "showAnimals", "Животные", "Animals")
    st("minimap", "names", "Ники", "Names")
    st("minimap", "followLook", "По взгляду", "Follow look", "Карта вращается по направлению взгляда, иначе север сверху", "The map rotates with your view; otherwise north is up")


def sounds(a, mod, st, opt, scale):
    mod("uisounds", "Звуки интерфейса", "UI sounds", "Короткие звуки меню: открытие, переключатели, уведомления", "Short menu sounds: open, switches, notifications")
    st("uisounds", "volume", "Громкость", "Volume")
    st("uisounds", "menu", "Открытие и закрытие меню", "Menu open / close")
    st("uisounds", "toggle", "Переключатели", "Switches", "Два разных щелчка: включение выше, выключение ниже", "Two clicks: on is higher, off is lower")
    st("uisounds", "notify", "Уведомления", "Notifications")
    st("uisounds", "hover", "Наведение", "Hover", "Очень тихий тик при наведении, по умолчанию выключен", "A very quiet tick on hover, off by default")
    for m, ru, en, dru, den in [("hitsound", "Звук попадания", "Hit sound", "Звук, когда ты бьёшь игрока или моба", "A sound when you hit a player or mob"),
                                ("killsound", "Звук убийства", "Kill sound", "Звук, когда противник погиб от твоего удара", "A sound when the enemy dies from your hit")]:
        mod(m, ru, en, dru, den)
        st(m, "set", "Набор", "Set")
        for o, oru, oen in [("classic", "Классика", "Classic"), ("click", "Щелчок", "Click"), ("bell", "Колокол", "Bell"), ("bass", "Бас", "Bass"), ("silence", "Тишина", "Silence")]:
            opt(m, "set", o, oru, oen)
        st(m, "volume", "Громкость", "Volume")


def view(a, mod, st, opt, scale):
    mod("fovstates", "FOV при беге", "Sprint FOV", "Свой угол обзора при беге, замедлении и полёте вместо ванильного рывка", "Own field of view for sprinting, slowness and flying instead of the vanilla dash")
    st("fovstates", "mode", "Режим", "Mode")
    opt("fovstates", "mode", "vanilla", "Ванильный", "Vanilla")
    opt("fovstates", "mode", "none", "Без изменения FOV", "No FOV change")
    opt("fovstates", "mode", "custom", "Свой", "Custom")
    st("fovstates", "sprint", "FOV при беге", "Sprint FOV")
    st("fovstates", "slow", "FOV при замедлении", "Slowness FOV")
    st("fovstates", "fly", "FOV в полёте", "Flying FOV")
    st("fovstates", "smooth", "Плавность", "Smoothness")
    mod("nohurtshake", "Без тряски при уроне", "No hurt shake", "Убирает или уменьшает дёргание экрана, когда тебя ударили", "Removes or reduces the screen shake when you are hit")
    st("nohurtshake", "strength", "Сила тряски", "Shake strength")
    mod("lowfire", "Низкий огонь", "Low fire", "Опускает огонь на экране при горении, чтобы он не закрывал обзор", "Lowers the fire overlay so it does not block your view")
    st("lowfire", "height", "Высота огня", "Fire height")


def social(a, mod, st, opt, scale):
    a("waypoint.death", "Место смерти", "Death point")
    a("waypoint.name", "Метка {0}", "Waypoint {0}")
    a("waypoint.added", "Метка сохранена", "Waypoint saved")
    a("waypoint.removed", "Ближайшая метка удалена", "Nearest waypoint removed")
    a("friends.added", "{0} добавлен в друзья", "{0} added to friends")
    a("friends.removed", "Игрок убран из друзей", "Player removed from friends")
    mod("waypoints", "Метки", "Waypoints", "Сохраняет точки (база, ферма, место смерти) и показывает их на экране с расстоянием", "Saves points (base, farm, death place) and shows them on screen with the distance")
    st("waypoints", "addKey", "Добавить метку здесь", "Add waypoint here")
    st("waypoints", "removeKey", "Удалить ближайшую", "Remove nearest", "Удаляет метку в радиусе 5 блоков", "Removes a waypoint within 5 blocks")
    st("waypoints", "hideKey", "Скрыть / показать все", "Hide / show all")
    st("waypoints", "deathPoint", "Метка смерти", "Death waypoint")
    st("waypoints", "color", "Цвет", "Colour")
    st("waypoints", "maxDistance", "Макс. расстояние", "Max distance", "0 — без ограничения", "0 = unlimited")
    mod("friends", "Друзья", "Friends", "Отмечает ромбом друзей из твоего списка — только тех, кого ты и так видишь", "Marks friends from your list with a diamond; only players you already see")
    st("friends", "addKey", "Добавить / убрать друга", "Add / remove friend", "Наведи прицел на игрока и нажми клавишу", "Aim at a player and press the key")
    st("friends", "color", "Цвет", "Colour")
    st("friends", "sound", "Звук", "Sound")


def labels(a, mod, st, opt, scale):
    mod("tnttimer", "TNT Timer", "TNT Timer", "Таймер над подожжённым динамитом — сколько секунд до взрыва", "A timer above lit TNT: seconds until it explodes")
    st("tnttimer", "warnColor", "Красный в конце", "Red at the end")
    mod("selfnametag", "SelfNameTag", "SelfNameTag", "Показывает твой ник над головой в виде от третьего лица", "Shows your own name above your head in third person")
    st("selfnametag", "background", "Фон", "Background")
