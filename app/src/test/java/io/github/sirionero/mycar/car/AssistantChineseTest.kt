package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phrases as Whisper writes Chinese: full-width punctuation, Latin names with or without
 * spaces around them, now and then traditional characters.
 */
class AssistantChineseTest {

    /** Every phrase that isn't heard as its command, all at once. */
    private fun heard(vararg pairs: Pair<String, String>) {
        val wrong = pairs.mapNotNull { (said, command) ->
            AssistantLanguage.toEnglish(said, ChinesePhrases).takeIf { it != command }?.let { "$said → $it, not $command" }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    private fun answer(english: String) = AssistantLanguage.answer(english, ChinesePhrases)

    @Test
    fun places() = heard(
        "导航到北京。" to "navigate to 北京",
        "带我去天安门。" to "navigate to 天安门",
        "我要去机场。" to "go to 机场",
        "导航回家。" to "navigate to 家",
        "回家。" to "navigate to 家",
        "开车去公司。" to "navigate to 公司",
        "去最近的加油站。" to "navigate to the nearest 加油站",
        "帮我找一下附近的加油站。" to "navigate to the nearest 加油站",
        "找找附近的加油站。" to "navigate to the nearest 加油站",
        "找个停车场。" to "navigate to the nearest 停车场",
        "附近的星巴克。" to "navigate to the nearest 星巴克",
        "最近的停车场在哪里？" to "navigate to the nearest 停车场",
        "附近有没有充电桩？" to "navigate to the nearest 充电桩",
        "哪里有药店？" to "navigate to the nearest 药店",
        "西湖怎么走？" to "navigate to 西湖",
        "去机场怎么走？" to "navigate to 机场",
        "用 Organic Maps 导航到上海。" to "use organic maps to navigate to 上海",
        "用Vela导航去最近的超市。" to "use vela to navigate to the nearest 超市",
        // The app at the end: AssistantCommands cuts it off at "用" when it knows the app.
        "导航到上海，用Vela。" to "navigate to 上海用vela",
        "去设置。" to "open settings",
        "结束导航。" to "stop navigation",
        "取消导航" to "stop navigation",
        "什么时候到？" to "when do i arrive",
        "还要多久？" to "when do i arrive",
        "我们几点能到？" to "when do i arrive",
    )

    @Test
    fun calls() = heard(
        "给妈妈打电话。" to "call 妈妈",
        "打电话给张伟。" to "call 张伟",
        "给我妈打个电话。" to "call 妈妈",
        "帮我给老婆打电话。" to "call 老婆",
        "呼叫 Anna。" to "call anna",
        "打张伟的手机。" to "call 张伟 on mobile",
        "给老王的公司打电话。" to "call 老王 on work",
        "打电话给爸爸家里。" to "call 爸爸 on home",
        "打电话。" to "call",
        "打电话回家。" to "call 家",
        "回拨。" to "call back",
        "给他回个电话。" to "call back",
        "回拨刚才的未接来电。" to "call back",
        "给李娜回个电话。" to "call 李娜",
        "重拨。" to "redial",
        "再打一次。" to "redial",
    )

    @Test
    fun music() = heard(
        "播放周杰伦的歌。" to "play 周杰伦",
        "我想听稻香。" to "play 稻香",
        "来一首晴天。" to "play 晴天",
        "放点 Taylor Swift 的歌。" to "play taylor swift",
        "播放首尔夜曲。" to "play 首尔夜曲",
        "用 Spotify 播放 Taylor Swift。" to "play taylor swift on spotify",
        "在QQ音乐上放一首稻香。" to "play 稻香 on qq音乐",
        "打开Spotify放首歌。" to "play music on spotify",
        "放首歌。" to "continue",
        "放首歌听听。" to "continue",
        "放一首周杰伦的歌给我听。" to "play 周杰伦",
        "播放音乐" to "continue",
        "继续播放。" to "continue",
        "暂停。" to "pause",
        "停一下。" to "pause",
        "把音乐关了。" to "pause",
        "下一首。" to "next",
        "切歌。" to "next",
        "换一首歌。" to "next",
        "上一首。" to "previous",
        "再放一遍。" to "play it again",
        "重新播放这首歌。" to "play it again",
        "这是什么歌？" to "what's playing",
        "这首歌是谁唱的？" to "what's playing",
        "现在放的是什么歌？" to "what's playing",
        "这是谁的歌？" to "what's playing",
    )

    @Test
    fun `messages, screens, time and weather`() = heard(
        "读一下我的新消息。" to "read my new messages",
        "有新消息吗？" to "read my new messages",
        "读消息。" to "read my messages",
        "读最后一条消息。" to "read my last message",
        "回复李娜。" to "reply to 李娜",
        "给小王发微信。" to "reply to 小王",
        "发消息给我老婆。" to "reply to 老婆",
        "打开设置。" to "open settings",
        "打开 Spotify。" to "open spotify",
        "打开浏览器。" to "open browser",
        "打开屏幕共享。" to "open screen sharing",
        "请帮我打开所有应用，谢谢。" to "open apps",
        "回到主页。" to "go home",
        "返回。" to "go back",
        "退出。" to "exit",
        "现在几点了？" to "what time is it",
        "请问现在几点？" to "what time is it",
        "几点钟？" to "what time is it",
        "今天天气怎么样？" to "what's the weather",
        "会下雨吗？" to "what's the weather",
        "外面多少度？" to "what's the weather",
        "你能做什么？" to "help",
        "帮助。" to "help",
    )

    @Test
    fun `traditional characters`() = heard(
        "導航到台北。" to "navigate to 台北",
        "帶我去最近的加油站。" to "navigate to the nearest 加油站",
        "打電話給媽媽。" to "call 妈妈",
        "回撥。" to "call back",
        "播放周杰倫的歌。" to "play 周杰倫",
        "下一首。" to "next",
        "暫停。" to "pause",
        "這首歌叫什麼？" to "what's playing",
        "打開設定。" to "open settings",
        "現在幾點？" to "what time is it",
        "天氣怎麼樣？" to "what's the weather",
        "回覆小明。" to "reply to 小明",
        "讀我的新訊息。" to "read my new messages",
        "結束導航。" to "stop navigation",
    )

    @Test
    fun `fillers come off without spaces`() {
        assertEquals("导航到北京", ChinesePhrases.trim("请帮我导航到北京谢谢"))
        assertEquals("导航到北京", ChinesePhrases.trim("你好mycar导航到北京吧"))
        assertEquals("下一首", ChinesePhrases.trim(AssistantLanguage.plain("嗯，麻烦你下一首好吗？")))
        // Alone, a filler is what was said.
        assertEquals("谢谢", ChinesePhrases.trim("谢谢"))
    }

    @Test
    fun `what isn't a command passes through`() {
        assertEquals("今天真不错", AssistantLanguage.toEnglish("今天真不错！", ChinesePhrases))
        // "找…" without "nearby" isn't a place to drive to.
        assertEquals("找周杰伦", AssistantLanguage.toEnglish("找周杰伦。", ChinesePhrases))
        // Not a name to reply to.
        assertEquals("回复消息", AssistantLanguage.toEnglish("回复消息。", ChinesePhrases))
        // "最近" without "的" is "lately"; a question about what's near isn't a place.
        assertEquals("最近怎么样", AssistantLanguage.toEnglish("最近怎么样？", ChinesePhrases))
        assertEquals("附近有什么好吃的", AssistantLanguage.toEnglish("附近有什么好吃的？", ChinesePhrases))
        // Not an app: AssistantCommands finds none, and Home Assistant gets the words as said.
        assertEquals("open 客厅的灯", AssistantLanguage.toEnglish("打开客厅的灯。", ChinesePhrases))
        // Setting the air conditioning isn't a route.
        assertEquals("open 空调到26度", AssistantLanguage.toEnglish("打开空调到26度。", ChinesePhrases))
        assertEquals("把空调温度调到22度", AssistantLanguage.toEnglish("把空调温度调到22度。", ChinesePhrases))
        // "放大" is "zoom in", "听不清" is "can't hear".
        assertEquals("放大地图", AssistantLanguage.toEnglish("放大地图。", ChinesePhrases))
        assertEquals("听不清", AssistantLanguage.toEnglish("听不清。", ChinesePhrases))
        // English passes as it was said, lower case.
        assertEquals("play daft punk", AssistantLanguage.toEnglish("Play Daft Punk.", ChinesePhrases))
    }

    @Test
    fun `answers in Chinese`() {
        assertEquals("现在是14点05分。", answer("It's 14:05."))
        assertEquals("现在是9点。", answer("It's 09:00."))
        assertEquals("现在是下午2点30分。", answer("It's 下午2:30."))
        // Newer Android puts a narrow no-break space before PM.
        assertEquals("现在是下午2点05分。", answer("It's 2:05\u202FPM."))
        assertEquals("预计14点30分到达，还有1小时5分钟。", answer("Around 14:30, in 1 hour 5 minutes."))
        assertEquals("预计14点30分到达。", answer("Around 14:30."))
        assertEquals("大约25分钟后到达。", answer("In about 25 minutes."))
        assertEquals("大约2小时后到达。", answer("In about 2 hours."))
        assertEquals("北京现在12°C，多云。", answer("12°C and Partly cloudy in 北京."))
        assertEquals("哈尔滨现在-3°C。", answer("-3°C and - in 哈尔滨."))
        assertEquals("好的，用Vela导航回家。", answer("Navigating to 家 with Vela."))
        assertEquals("好的，用Organic Maps导航到上海。", answer("Navigating to 上海 with Organic Maps."))
        assertEquals("好的，用Vela查找最近的加油站。", answer("Finding the nearest 加油站 with Vela."))
        assertEquals("正在呼叫妈妈。", answer("Calling 妈妈."))
        assertEquals("正在回拨张伟。", answer("Calling 张伟 back."))
        assertEquals("正在拨打张伟的手机号码。", answer("Calling 张伟, 手机."))
        assertEquals("正在拨打Anna的手机号码。", answer("Calling Anna, mobile."))
        assertEquals("张伟没有单位号码。改打手机号码吗？", answer("张伟 has no work number. Call 手机 instead?"))
        assertEquals("Anna没有住宅号码。改打另一个号码吗？", answer("Anna has no home number. Call the other one instead?"))
        assertEquals("要打给李娜吗？", answer("Call 李娜?"))
        assertEquals("通讯录里没有找到老王。", answer("I couldn't find 老王 in your contacts."))
        assertEquals("这是周杰伦的《稻香》。", answer("稻香 by 周杰伦"))
        assertEquals("正在Spotify中查找周杰伦。", answer("Looking for 周杰伦 on Spotify."))
        assertEquals("开始播放音乐。", answer("Playing the music."))
        assertEquals("好的，用Spotify播放音乐。", answer("Playing Spotify."))
        assertEquals("发送给Anna：我快到了", answer("Sending to Anna: 我快到了"))
        assertEquals("要回复李娜什么？", answer("What's your reply to 李娜?"))
        assertEquals("找不到叫微信的应用。", answer("I couldn't find an app called 微信."))
        // myCar's own apps have English labels on the car's screen, Chinese names out loud.
        assertEquals("正在打开浏览器。", answer("Opening Browser."))
        assertEquals("正在打开网易云音乐。", answer("Opening 网易云音乐."))
        assertEquals("抱歉，没听清。", answer("Didn't catch that."))
        // Something it has no words for stays as written.
        assertEquals("Fuck you too.", answer("Fuck you too."))
    }

    @Test
    fun `every answer has Chinese words`() {
        for (said in Said.entries) {
            val english = saidSample(said)
            assertTrue("no words for $said", answer(english) != english)
        }
    }

    @Test
    fun `labels, help, samples and replies`() {
        for (label in ASSISTANT_LABELS + ItalianPhrases.labels.keys) {
            assertTrue("no label for $label", ChinesePhrases.labels[label].let { it != null && it != label })
        }
        assertEquals(14, ChinesePhrases.help.size)
        assertEquals("帮助", ChinesePhrases.help.last().first)
        // Every sample the phone's introduction shows is a command: it becomes English.
        for (sample in ChinesePhrases.samples) assertTrue(sample, AssistantLanguage.toEnglish(sample, ChinesePhrases).first() in 'a'..'z')
        with(ChinesePhrases) {
            assertTrue(yes.isNotEmpty() && no.isNotEmpty() && cancel.isNotEmpty() && change.isNotEmpty() && send.isNotEmpty())
            assertTrue(yes.intersect(no).isEmpty())
            // The words the screen asks for are the ones heard, in either script.
            assertTrue("好" in yes && "不用" in no && "取消" in cancel && "修改" in change && "发送" in send)
            assertTrue("回覆" in yes && "對" in yes && "算了" in no && "發送" in send && "重說" in change)
            assertEquals("妈妈说：我到了", says("妈妈", "我到了"))
            assertEquals("小李在攀岩群里说：周六见", says(inGroup("小李", "攀岩群"), "周六见"))
        }
    }
}
