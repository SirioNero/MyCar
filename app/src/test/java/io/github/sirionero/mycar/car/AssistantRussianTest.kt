package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Russian phrasebook on its own, before it has a VoiceLanguage: phrases as a recognizer writes
 * them (capitals, punctuation, "ё" or "е", hyphens), the commands they become, and the answers.
 */
class AssistantRussianTest {

    private fun heard(vararg pairs: Pair<String, String>) {
        for ((said, command) in pairs) assertEquals(said, command, AssistantLanguage.toEnglish(said, RussianPhrases))
    }

    private fun answer(english: String) = AssistantLanguage.answer(english, RussianPhrases)

    @Test
    fun `routes and places`() = heard(
        "Проложи маршрут до вокзала." to "navigate to вокзал",
        "Поехали домой." to "navigate to дом",
        "Отвези меня в аптеку." to "navigate to аптека",
        "Отвезите нас в торговый центр." to "navigate to торговый центр",
        "Едем на работу." to "navigate to работа",
        "Поехали в Москву." to "navigate to москва",
        "Как доехать до Красной площади?" to "navigate to красная площадь",
        "Постройте маршрут до улицы Ленина 5." to "navigate to улица ленина 5",
        "Маршрут до Ленина 5." to "navigate to ленина 5",
        "Довези до Ленинградского шоссе." to "navigate to ленинградское шоссе",
        "Подскажи дорогу до Казани." to "navigate to казань",
        "До Сочи." to "navigate to сочи",
        "В аптеку." to "navigate to аптека",
        "Мне нужно на вокзал." to "navigate to вокзал",
        "Поехали домой через Organic Maps." to "navigate to дом через organic maps",
        // An app after "через" by the start of its name, which finds "Яндекс Карты".
        "Проложи маршрут до вокзала через Яндекс Карты." to "navigate to вокзал через яндекс карт",
        "Навигация до Пулково." to "navigate to пулково",
        // What's near: the kind of place in the nominative, which the map's categories know.
        "Найди ближайшую заправку." to "navigate to the nearest заправка",
        "Найдите ближайшую АЗС." to "navigate to the nearest азс",
        "Проложи маршрут до ближайшего банкомата." to "navigate to the nearest банкомат",
        "Маршрут к ближайшей заправке через Organic Maps." to "navigate to the nearest заправка через organic maps",
        "Где ближайшая аптека?" to "navigate to the nearest аптека",
        "Найди аптеку рядом." to "navigate to the nearest аптека",
        "Найди кафе поблизости." to "navigate to the nearest кафе",
        "Найди аптеку." to "navigate to the nearest аптека",
        "Отвези меня к ближайшей зарядной станции." to "navigate to the nearest зарядная станция",
        "Мне нужно заправиться." to "navigate to the nearest заправка",
        "Заправка рядом." to "navigate to the nearest заправка",
        // The route itself.
        "Останови навигацию." to "stop navigation",
        "Отмените маршрут." to "stop navigation",
        "Сбросьте маршрут." to "stop navigation",
        "Когда приедем?" to "when do i arrive",
        "Сколько ещё ехать?" to "when do i arrive",
        "Сколько еще ехать?" to "when do i arrive",
        "Во сколько мы будем на месте?" to "when do i arrive",
        "Долго ещё?" to "when do i arrive",
    )

    @Test
    fun `calls, with names back as a contact list has them`() = heard(
        "Позвони маме." to "call мама",
        "Позвоните Анне." to "call анна",
        "Позвонить папе." to "call папа",
        "Позвони Ивану на мобильный." to "call иван on mobile",
        "Позвони на работу Сергею." to "call сергей on work",
        "Позвони маме домой." to "call мама on home",
        "Позвони Анне Петровой." to "call анна петрова",
        "Позвоните, пожалуйста, Анне Сергеевне." to "call анна сергеевна",
        "Позвони Артёму." to "call артём",
        "Позвони Артему." to "call артем",
        "Перезвони Анне." to "call анна",
        "Набери Сергею." to "call сергей",
        "Наберите маму." to "call мама",
        "Набери Ивана." to "call иван",
        // Юлию (Юлия) or Дмитрию (Дмитрий): the piece of both, and the screen asks.
        "Набери Юлию." to "call юли",
        "Вызови Дмитрия." to "call дмитрий",
        "Набери номер Ольги." to "call ольга",
        "Свяжись с Сергеем." to "call сергей",
        "Свяжитесь с Анной." to "call анна",
        "Наберите маме." to "call мама",
        "Соедини меня с Анной." to "call анна",
        "Пожалуйста, позвони моей жене." to "call жен",
        "Перезвони." to "call back",
        "Перезвоните ему." to "call back",
        "Перезвони на пропущенный." to "call back",
        "Позвони ещё раз." to "redial",
        "Позвони еще раз." to "redial",
        "Повтори последний звонок." to "redial",
        "Позвони." to "call",
    )

    /** AssistantCommands' match of a spoken name to a contact: 0 the whole name, 1 a whole word, 2 a piece, 3 none. */
    private fun tier(contact: String, spoken: String): Int {
        val full = contact.lowercase().trim()
        return when {
            full == spoken -> 0
            " $full ".contains(" $spoken ") -> 1
            full.contains(spoken) -> 2
            else -> 3
        }
    }

    @Test
    fun `a name said in another case still finds the contact`() {
        // The phrase, the contact as the phone has it, and the worst match allowed: 1 calls at once,
        // 2 asks on the screen first.
        val cases = listOf(
            Triple("Позвони маме.", "Мама", 0),
            Triple("Позвони Анне.", "Анна", 0),
            Triple("Позвони Ивану.", "Иван Петров", 1),
            Triple("Позвони Сергею.", "Сергей", 0),
            Triple("Позвони Юлии.", "Юлия", 0),
            Triple("Набери Юлию.", "Юлия", 2),
            Triple("Набери Дмитрию.", "Дмитрий", 2),
            Triple("Позвони Дмитрию.", "Дмитрий", 0),
            Triple("Набери Анну.", "Анна", 0),
            Triple("Набери Ивана.", "Иван", 0),
            Triple("Набери маме.", "Мама", 0),
            Triple("Позвони Тане.", "Таня", 0),
            Triple("Позвони Маше.", "Маша", 0),
            Triple("Позвони Ольге.", "Ольга", 0),
            Triple("Позвони Наталье.", "Наталья", 0),
            Triple("Позвони Игорю.", "Игорь", 0),
            Triple("Позвони Павлу.", "Павел", 0),
            Triple("Позвони Пете.", "Петя", 0),
            Triple("Позвони Илье.", "Илья", 0),
            Triple("Позвони Николаю.", "Николай", 0),
            Triple("Позвони Любови.", "Любовь", 0),
            Triple("Позвони бабушке.", "Бабушка", 0),
            Triple("Позвони брату.", "Брат", 0),
            Triple("Позвони жене.", "Жена", 2),
            Triple("Позвони Жене.", "Женя", 2),
            Triple("Позвони Анне Петровой.", "Анна Петрова", 0),
            Triple("Позвони Ивану Петровичу.", "Иван Петрович", 0),
            Triple("Набери Игоря.", "Игорь", 0),
            Triple("Набери Олю.", "Оля", 0),
            Triple("Набери номер Тани.", "Таня", 0),
            Triple("Свяжись с Машей.", "Маша", 0),
            Triple("Свяжись с Дмитрием.", "Дмитрий", 0),
            Triple("Ответь Анне.", "Анна", 0),
            Triple("Напиши Сергею.", "Сергей", 0),
            Triple("Ответь на сообщение Марии.", "Мария", 0),
            Triple("Напишите маме, что я опаздываю.", "Мама", 0),
            // Said as it is, without its case: the piece still finds it.
            Triple("Набери Анна.", "Анна", 2),
        )
        for ((said, contact, worst) in cases) {
            val command = AssistantLanguage.toEnglish(said, RussianPhrases)
            val name = command.removePrefix("call ").removePrefix("reply to ").replace(Regex(" on (?:mobile|home|work)$"), "")
            assertNotEquals(said, command, name)
            val t = tier(contact, name)
            assertTrue("$said → «$name» finds $contact at level $t, not $worst", t <= worst)
        }
    }

    @Test
    fun `messages`() = heard(
        "Прочитай сообщения." to "read my messages",
        "Прочитайте мои сообщения." to "read my messages",
        "Прочитай новые сообщения." to "read my new messages",
        "Есть новые сообщения?" to "read my new messages",
        "Прочти последнее сообщение." to "read my last message",
        "Что мне написали?" to "read my new messages",
        "Ответь Анне." to "reply to анна",
        "Напиши Ивану, что я опаздываю." to "reply to иван",
        "Отправь сообщение маме." to "reply to мама",
        "Ответь на сообщение от Марии." to "reply to мария",
        // No name: the last message, read with the offer to reply.
        "Ответь на последнее сообщение." to "read my last message",
        "Ответь ей." to "read my last message",
    )

    @Test
    fun `music`() = heard(
        "Включи Queen." to "play queen",
        "Включите Queen." to "play queen",
        "Поставь песню Группа крови." to "play группа крови",
        "Давай послушаем Земфиру." to "play земфиру",
        "Хочу послушать альбом Кино." to "play кино",
        "Включи Queen на Spotify." to "play queen на spotify",
        "Включи музыку в Spotify." to "play music в spotify",
        // An app written in Cyrillic, and one after "через" by the start of its name.
        "Включи Квин в Спотифае." to "play квин в spotify",
        "Включи Кино через Яндекс Музыку." to "play кино через яндекс музык",
        "Включи что-нибудь через Яндекс Музыку." to "play music через яндекс музык",
        "Включи музыку." to "continue",
        "Сыграй что-нибудь." to "continue",
        "Продолжи." to "continue",
        "Пауза." to "pause",
        "Поставь на паузу." to "pause",
        "Сделайте паузу." to "pause",
        "Выключи музыку." to "pause",
        "Стоп." to "pause",
        "Следующий трек." to "next",
        "Дальше." to "next",
        "Давай следующую." to "next",
        "Пропусти эту." to "next",
        "Предыдущая песня." to "previous",
        "Верни прошлую песню." to "previous",
        "Повтори эту песню." to "play it again",
        "Включи эту песню ещё раз." to "play it again",
        "Что сейчас играет?" to "what's playing",
        "Что за песня?" to "what's playing",
        "Кто поёт?" to "what's playing",
        "Кто поет?" to "what's playing",
    )

    @Test
    fun `screens, apps, the time and the weather`() = heard(
        "Помощь." to "help",
        "Что ты умеешь?" to "help",
        "Что можно сказать?" to "help",
        "Вернись на главный экран." to "go home",
        "На главную." to "go home",
        "Открой настройки." to "open settings",
        "Покажи все приложения." to "open apps",
        "Выход." to "exit",
        "Закрой майкар." to "exit",
        "Назад." to "go back",
        "Открой Spotify." to "open spotify",
        "Откройте Telegram." to "open telegram",
        "Открой Яндекс Музыку." to "open яндекс музык",
        "Открой Спотифай." to "open spotify",
        "Открой Ютуб Музыку." to "open youtube music",
        "Открой карты." to "open карты",
        "Открой телефон." to "open phone",
        "Открой демонстрацию экрана." to "open screen sharing",
        "Запусти браузер." to "open browser",
        "Который час?" to "what time is it",
        "Сколько времени?" to "what time is it",
        "Какая погода?" to "what's the weather",
        "Скажи погоду." to "what's the weather",
        "Будет дождь?" to "what's the weather",
        "Окей, майкар, включи музыку, пожалуйста." to "continue",
        "Ну, давай домой." to "navigate to дом",
    )

    @Test
    fun `what isn't a command passes through, lower case`() = heard(
        "Какой чудесный день!" to "какой чудесный день",
        // "Найди" without anything near isn't a place to drive to.
        "Найди Despacito." to "найди despacito",
        // A light or the heating is for Home Assistant, not a song.
        "Включи свет." to "включи свет",
        "Включи кондиционер." to "включи кондиционер",
        "Открой ворота." to "open ворота",
        "До свидания." to "до свидания",
        "Что рядом?" to "что рядом",
        "Позвони мне." to "позвони мне",
        "Ответь на звонок." to "ответь на звонок",
    )

    @Test
    fun `a destination is said back the way the driver said it`() {
        AssistantLanguage.toEnglish("Проложи маршрут до вокзала.", RussianPhrases)
        assertEquals("Прокладываю маршрут до вокзала через Vela.", answer("Navigating to вокзал with Vela."))
        // AssistantCommands cut the app off the end: the words said lose it too.
        AssistantLanguage.toEnglish("Поехали домой через Organic Maps.", RussianPhrases)
        assertEquals("Прокладываю маршрут домой через Organic Maps.", answer("Navigating to дом with Organic Maps."))
        AssistantLanguage.toEnglish("Отвези меня в аптеку.", RussianPhrases)
        assertEquals("Прокладываю маршрут в аптеку через Vela.", answer("Navigating to аптека with Vela."))
        // Another place than the one said, or one said without a preposition: the map's words.
        assertEquals("Прокладываю маршрут через Vela: москва.", answer("Navigating to москва with Vela."))
        // What's near, in the accusative and its gender.
        AssistantLanguage.toEnglish("Найди ближайшую заправку.", RussianPhrases)
        assertEquals("Ищу ближайшую заправку через Vela.", answer("Finding the nearest заправка with Vela."))
        assertEquals("Ищу ближайший банкомат через Vela.", answer("Finding the nearest банкомат with Vela."))
        assertEquals("Ищу ближайшее кафе через Vela.", answer("Finding the nearest кафе with Vela."))
        assertEquals("Ищу ближайшую зарядную станцию через Vela.", answer("Finding the nearest зарядная станция with Vela."))
        assertEquals("Ищу ближайшую станцию метро через Vela.", answer("Finding the nearest станция метро with Vela."))
        assertEquals("Ищу ближайшую столовую через Vela.", answer("Finding the nearest столовая with Vela."))
        assertEquals("Ищу ближайшую азс через Vela.", answer("Finding the nearest азс with Vela."))
        assertEquals("Ищу ближайшее отделение полиции через Vela.", answer("Finding the nearest отделение полиции with Vela."))
        assertEquals("Ищу ближайший отель через Vela.", answer("Finding the nearest отель with Vela."))
        assertEquals("Ищу поблизости «burger king» через Vela.", answer("Finding the nearest burger king with Vela."))
    }

    @Test
    fun `an app that isn't there is said back as said`() {
        assertEquals("open яндекс музык", AssistantLanguage.toEnglish("Открой Яндекс Музыку.", RussianPhrases))
        assertEquals("Не нахожу приложение «яндекс музыку».", answer("I couldn't find an app called яндекс музык."))
        assertEquals("Не нахожу приложение «foo».", answer("I couldn't find an app called foo."))
    }

    @Test
    fun `answers in Russian`() {
        assertEquals("Сейчас 14:05.", answer("It's 14:05."))
        assertEquals("Примерно в 14:30, через 1 час 5 минут.", answer("Around 14:30, in 1 hour 5 minutes."))
        assertEquals("Примерно в 14:30.", answer("Around 14:30."))
        assertEquals("Примерно через 21 минуту.", answer("In about 21 minutes."))
        assertEquals("Примерно через 2 часа 3 минуты.", answer("In about 2 hours 3 minutes."))
        assertEquals("Примерно через 5 часов 11 минут.", answer("In about 5 hours 11 minutes."))
        assertEquals("Примерно через 25 минут.", answer("In about 25 minutes."))
        assertEquals("Москва: 12°C, переменная облачность.", answer("12°C and Partly cloudy in Москва."))
        assertEquals("Сочи: -3°C.", answer("-3°C and - in Сочи."))
        assertEquals("Включаю музыку.", answer("Playing the music."))
        assertEquals("Включаю музыку в приложении Spotify.", answer("Playing Spotify."))
        assertEquals("Включаю.", answer("Playing."))
        assertEquals("Предыдущий трек.", answer("Playing the previous track."))
        assertEquals("Ищу «queen» в приложении Spotify.", answer("Looking for queen on Spotify."))
        assertEquals("Звоню: Анна.", answer("Calling Анна."))
        assertEquals("Звоню: Анна, мобильный.", answer("Calling Анна, мобильный."))
        assertEquals("Перезваниваю: Анна.", answer("Calling Анна back."))
        assertEquals("Позвонить: Анна?", answer("Call Анна?"))
        assertEquals("Анна: нет рабочего номера. Позвонить на мобильный?", answer("Анна has no work number. Call мобильный instead?"))
        assertEquals("Анна: нет мобильного номера. Позвонить на другой?", answer("Анна has no mobile number. Call the other one instead?"))
        assertEquals("Контакт «Анна» не найден.", answer("I couldn't find анна in your contacts."))
        assertEquals("Анна: нет сообщения, на которое можно ответить.", answer("I couldn't find a message from анна to reply to."))
        assertEquals("Группа крови, исполняет Кино.", answer("Группа крови by Кино"))
        assertEquals("Отправляю в чат «Анна»: буду через десять минут", answer("Sending to Анна: буду через десять минут"))
        assertEquals("Анна: что ответить?", answer("What's your reply to Анна?"))
        assertEquals("Что ответить?", answer("What's your reply?"))
        assertEquals("Не удалось расслышать.", answer("Didn't catch that."))
        // Something it has no words for stays as written.
        assertEquals("Fuck you too.", answer("Fuck you too."))
    }

    @Test
    fun `every answer has Russian words`() {
        for (said in Said.entries) {
            val english = saidSample(said)
            assertNotEquals("no words for $said", english, answer(english))
        }
    }

    @Test
    fun `labels, help and the phone's samples`() {
        for (label in ASSISTANT_LABELS) assertTrue("no label for $label", RussianPhrases.labels[label].orEmpty().isNotBlank())
        for (label in ItalianPhrases.labels.keys) assertTrue("no label for $label", label in RussianPhrases.labels)
        assertEquals(14, RussianPhrases.help.size)
        // What the help says to say is understood, where it's a whole command.
        val commands = RussianPhrases.help.flatMap { it.first.split(" · ") }.filter { !it.endsWith("…") } + RussianPhrases.samples
        for (said in commands) {
            val english = AssistantLanguage.toEnglish(said, RussianPhrases)
            assertNotEquals(said, AssistantLanguage.plain(said), english)
        }
        assertEquals("call мама", AssistantLanguage.toEnglish("Позвонить маме", RussianPhrases))
        assertEquals("play queen", AssistantLanguage.toEnglish("Включить Queen", RussianPhrases))
    }

    /** Whether [text] has one of [phrases] in it as whole words, as VoiceReply hears a yes, a no or "send". */
    private fun says(text: String, phrases: Set<String>): Boolean {
        val said = " " + text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }.joinToString(" ") + " "
        return phrases.any { said.contains(" $it ") }
    }

    private fun isYes(text: String) = !says(text, RussianPhrases.no) && says(text, RussianPhrases.yes)

    @Test
    fun `yes, no, cancel, change and send`() {
        for (set in listOf(RussianPhrases.yes, RussianPhrases.no, RussianPhrases.cancel, RussianPhrases.change, RussianPhrases.send)) {
            assertTrue(set.isNotEmpty())
        }
        assertTrue(isYes("Да."))
        assertTrue(isYes("Да, ответь."))
        assertTrue(isYes("Конечно"))
        assertFalse(isYes("Нет."))
        assertFalse(isYes("Не надо."))
        assertFalse(isYes("Не хочу"))
        assertFalse(isYes("Потом"))
        // As VoiceReply listens after reading the reply back: cancel, then change, then send.
        assertTrue(says("Отмена!", RussianPhrases.cancel))
        assertTrue(says("Не отправляй.", RussianPhrases.cancel))
        assertTrue(says("Измени.", RussianPhrases.change))
        assertTrue(says("Ещё раз", RussianPhrases.change))
        assertTrue(says("Еще раз", RussianPhrases.change))
        assertTrue(says("Отправляй.", RussianPhrases.send))
        assertFalse(says("Отправляй.", RussianPhrases.cancel) || says("Отправляй.", RussianPhrases.change))
    }

    @Test
    fun `a message read aloud`() {
        assertEquals("Анна пишет: буду через десять минут", RussianPhrases.says("Анна", "буду через десять минут"))
        assertEquals("Сэм в чате «Скалолазы»", RussianPhrases.inGroup("Сэм", "Скалолазы"))
        assertEquals(
            "Сэм в чате «Скалолазы» пишет: завтра в восемь",
            RussianPhrases.says(RussianPhrases.inGroup("Сэм", "Скалолазы"), "завтра в восемь"),
        )
    }
}
