package io.github.sirionero.mycar.car

/**
 * The assistant in Chinese: Mandarin as spoken in mainland China, in simplified characters (see
 * [AssistantLanguage]). Chinese has no spaces between words: the fillers come off with none
 * ([wordGap]) and the rules match the characters themselves, with room for a space only where the
 * recognizer leaves one, around a Latin word ("打开 Spotify"). The recognizer sometimes writes
 * traditional characters: the command words are heard in both forms ([both]), while names,
 * places and songs pass through as written.
 */
internal object ChinesePhrases : Phrasebook() {

    /**
     * The characters of the words below that traditional Chinese writes differently, each followed
     * by its traditional forms (OpenCC's s2t, s2tw and s2hk). A new word may need its characters here.
     */
    private val TRADITIONAL: Map<Char, String> = """
        专專 个個 么麼 乐樂 从從 们們 会會 儿兒 关關 写寫 况況 几幾 划劃 刚剛 别別 办辦 动動 单單 发發 号號 后後 吗嗎 听聽 启啓啟
        喽嘍 围圍 复覆復 头頭 妇婦 妈媽 学學 宝寶 宾賓 对對 寻尋 导導 岳嶽 带帶 帮幫 干幹 应應 开開 弃棄 当當 录錄 才纔 报報 拨撥
        换換 据據 数數 时時 显顯 暂暫 机機 条條 来來 板闆 样樣 检檢 气氣 没沒 浏瀏 温溫 点點 烦煩 热熱 爷爺 现現 电電 画畫 盘盤
        着著 码碼 确確 离離 简簡 系繫 紧緊 约約 级級 线線 终終 结結 给給 络絡 统統 继繼 续續 网網 联聯 见見 规規 览覽 计計 认認
        讯訊 设設 识識 诉訴 话話 该該 语語 说說説 请請 读讀 谁誰 调調 谢謝 贝貝 赶趕 车車 软軟 辆輛 辑輯 边邊 达達 过過 运運 还還
        这這 进進 远遠 里裏裡 钟鐘鍾 错錯 键鍵 镜鏡 长長 闭閉 问問 间間 静靜 页頁 预預 题題 额額 馆館 马馬 驾駕
    """.trim().split(Regex("\\s+")).associate { it[0] to it.substring(1) }

    private val SIMPLIFIED: Map<Char, Char> = TRADITIONAL.flatMap { (s, t) -> t.map { it to s } }.toMap()

    /** [pattern] with each command character also heard in its traditional form: "导航" → "[导導]航". */
    private fun both(pattern: String): String = buildString {
        for (c in pattern) TRADITIONAL[c]?.let { append('[').append(c).append(it).append(']') } ?: append(c)
    }

    /** [word] in every spelling the recognizer may use: simplified, traditional, or a mix. */
    private fun forms(word: String): List<String> =
        word.fold(listOf("")) { spelt, c -> spelt.flatMap { start -> (c + TRADITIONAL[c].orEmpty()).map { start + it } } }

    /** [text] with its traditional characters made simplified, to look a word up. */
    private fun simplified(text: String): String = text.map { SIMPLIFIED[it] ?: it }.joinToString("")

    private fun rule(pattern: String, to: String) = Rule(both(pattern), to)
    private fun rule(pattern: String, to: (MatchResult) -> String?) = Rule(both(pattern), to)

    override val wordGap = ""

    override val leadFillers = listOf(
        "mycar", "my car", "hey ", "okay ", "ok ", "你好", "哈喽", "嘿", "嗨", "喂", "嗯", "呃", "额", "好的", "好吧", "好",
        "请你帮我", "请帮我", "麻烦你帮我", "麻烦帮我", "麻烦你", "麻烦", "请问", "请你", "请", "你帮我", "帮我", "帮忙", "替我",
        "你能不能", "能不能", "你可以", "可以", "能否", "你能", "我想要", "我想", "我要", "我需要", "我们", "咱们",
        "现在", "马上", "立刻", "赶紧",
    ).flatMap(::forms).distinct()

    // Longest first: "好吗" before "吗", or "…好" would stay.
    override val tailFillers = listOf(
        "谢谢你", "谢谢", "多谢", "感谢", "好吗", "好不好", "好吧", "可以吗", "行吗", "行不行", "行吧", "吧", "吗", "呢", "啊", "呀", "哦", "嘛", "啦",
    ).flatMap(::forms).distinct()

    /** "导航到北京用Vela": AssistantCommands cuts the app off only when it knows one by that name. "使用" before "用". */
    override val appWords = listOf("使用", "通过", "透过", "用").flatMap(::forms).distinct()

    /** "最近的加油站", "离我最近的一家超市", "附近的星巴克": what's near. */
    private val NEAR = Regex(
        both("(?:离(?:我|我们|这|这里|这儿)?最近的?|最近的?|附近的?|周边的?|周围的?|就近的?)(?:一个|一家|一间|一座)?(.+)"),
    )

    /** "最近的加油站" → "the nearest 加油站", "北京那边" → "北京", "家里" → "家", "3 号楼" → "3号楼": the place, for the map. */
    private fun place(said: String): String {
        val t = said.trim().replace(Regex(both(" ?(?:去|那里|那儿|那边)$")), "").trim()
            .replace(Regex("(?<=\\p{IsHan}) (?=\\d)|(?<=\\d) (?=\\p{IsHan})"), "")
        NEAR.matchEntire(t)?.let { return "the nearest " + it.groupValues[1].trim() }
        return if (simplified(t) == "家里") "家" else t
    }

    private fun navigate(said: String) = place(said).takeIf { it.isNotEmpty() }?.let { "navigate to $it" }

    /** Where a place is, or where one can be found: the nearest one. Not a question: "附近有什么好吃的". */
    private fun nearest(said: String): String? {
        if (Regex("什么|啥|哪").containsMatchIn(simplified(said))) return null
        val p = place(said).takeIf { it.isNotEmpty() } ?: return null
        return "navigate to " + if (p.startsWith("the nearest ")) p else "the nearest $p"
    }

    /** Family as contacts are saved, in simplified characters: "我妈" → "妈妈", "爺爺" → "爷爷". */
    private val FAMILY = mapOf("妈" to "妈妈", "爸" to "爸爸", "哥" to "哥哥", "姐" to "姐姐", "弟" to "弟弟", "妹" to "妹妹") +
        listOf(
            "妈妈", "爸爸", "哥哥", "姐姐", "弟弟", "妹妹", "老妈", "老爸", "爷爷", "奶奶", "外公", "外婆", "姥姥", "姥爷",
            "老婆", "老公", "儿子", "女儿", "宝贝", "老板", "媳妇", "岳父", "岳母", "公公", "婆婆", "叔叔", "阿姨", "舅舅", "姑姑",
        ).associateWith { it }

    /** Who, the way AssistantCommands finds them in the contacts: "我妈" → "妈妈", "张伟的电话" → "张伟". */
    private fun who(raw: String): String {
        val t = raw.trim().replace(Regex(both("^(?:我的|我)(?=.)")), "")
            .replace(Regex(both(" ?的?(?:电话号码|电话|号码|手机号)$")), "").trim()
        return FAMILY[simplified(t)] ?: t
    }

    private fun call(raw: String) = who(raw).takeIf { it.isNotEmpty() }?.let { "call $it" }

    /** The kinds of number after a name ("妈妈的手机"), longest first. */
    private const val TYPES = "手机号码|手机号|手机|移动电话|行动电话|家庭电话|家里电话|住宅电话|固定电话|住宅|家庭|家里|座机|固话|宅电|家|" +
        "公司电话|单位电话|办公室电话|办公电话|工作电话|公司|单位|办公室|工作"

    private fun callOn(m: MatchResult): String? {
        val name = who(m.groupValues[1]).takeIf { it.isNotEmpty() } ?: return null
        val type = when (simplified(m.groupValues[2])) {
            "手机号码", "手机号", "手机", "移动电话", "行动电话" -> "mobile"
            "公司电话", "单位电话", "办公室电话", "办公电话", "工作电话", "公司", "单位", "办公室", "工作" -> "work"
            else -> "home"
        }
        return "call $name on $type"
    }

    /** Not a name to reply to: "回复消息", "回复他". */
    private val NOT_A_NAME = setOf("消息", "信息", "短信", "微信", "简讯", "讯息", "一下", "他", "她", "它", "ta", "对方", "这条", "这个")

    private fun reply(raw: String) = who(raw).takeUnless { it.isEmpty() || simplified(it) in NOT_A_NAME }?.let { "reply to $it" }

    /** Music in general rather than a song: "放首歌", "播放音乐". */
    private val MUSIC = setOf("", "音乐", "歌", "歌曲", "首歌", "点歌", "点音乐", "些歌", "音乐吧", "我的音乐", "我的歌", "我的歌单", "曲子")

    /** "一首周杰伦的歌给我听" → "周杰伦"; null for music in general ("首歌听听"). */
    private fun song(raw: String): String? {
        val t = raw.trim().replace(Regex(both("^(?:一首|一曲|一下|一点|一些|一个)")), "").trim()
            .replace(Regex(both(" ?(?:给我们听|给我听|来听听|听听|听一听|听一下)$|(?<=歌|乐|曲)听$")), "").trim()
            .replace(Regex(both(" ?的(?:歌|歌曲|音乐|专辑|歌单|新歌|曲子|作品)$")), "").trim()
            .replace(Regex(both("^(?:歌曲|歌手|专辑)(?=.)")), "").trim()
        return t.takeUnless { simplified(it) in MUSIC }
    }

    /** "高德地图应用" → "高德地图": the app's name as its label has it. */
    private fun appName(said: String): String =
        said.trim().replace(Regex(both("^(?:一下|我的)")), "").replace(Regex(both(" ?(?:这个)?(?:应用程序|应用|app|软件|程序)$")), "").trim()

    /** myCar's own apps by their Chinese names: the car's screen labels them in English. */
    private fun app(name: String) = when (simplified(name)) {
        "电话", "拨号", "拨号盘", "拨号键盘", "拨号器" -> "phone"
        "浏览器", "网页", "上网" -> "browser"
        "屏幕共享", "共享屏幕", "投屏", "手机投屏", "屏幕镜像", "镜像" -> "screen sharing"
        "车辆数据", "汽车数据", "车辆信息", "车况" -> "vehicle data"
        "设置", "设定" -> "settings"
        "影院", "电影院" -> "cinema"
        else -> name
    }

    override val commands = listOf(
        rule(
            "帮助|求助|使用帮助|使用说明|说明|语音帮助|命令|命令列表|指令|语音命令|语音指令|有(?:哪些|什么)(?:命令|指令|功能)|" +
                "(?:你)?(?:都)?(?:能|会|可以)?(?:做|干)(?:些)?(?:什么|啥)|你(?:都)?会(?:些)?(?:什么|啥)|(?:我)?(?:能|可以|该)?说(?:些)?(?:什么|啥)|" +
                "怎么用|你有(?:什么|哪些)功能",
            "help",
        ),
        // Screens of myCar's own, before "去…" is taken for a place.
        rule("(?:回到|返回|回|去|打开|显示|切换到|切到|进入)?(?:主页|主屏幕|主屏|首页|主界面|桌面|主画面|主菜单|主页面)", "go home"),
        rule("(?:打开|进入|去|显示|看看|回到|切换到|切到)? ?(?:mycar ?的?)? ?(?:设置|设定)(?:页面|界面|菜单)?", "open settings"),
        rule(
            "(?:打开|显示|看看|去|进入|列出|查看)?(?:我的|所有的?|全部的?)? ?(?:应用程序|应用程式|应用|程序|软件|apps|app) ?(?:列表)?|应用列表",
            "open apps",
        ),
        rule(
            "退出 ?(?:mycar|my car|程序|应用)?|(?:关闭|关掉|离开) ?(?:mycar|my car)|" +
                "(?:回到|返回|切换到|切回|切换回|退回)(?:原车|车机|汽车|车辆)的?(?:系统|界面|屏幕|主页)?",
            "exit",
        ),
        rule("返回|后退|退回|回退|(?:返回|回到)?上一(?:页|级)", "go back"),
        // The route.
        rule(
            "(?:停止|结束|取消|退出|关闭|关掉|终止|停掉|暂停)(?:当前)?的?(?:导航|路线|行程|路线引导|导航路线)|导航(?:结束|停止|取消|关闭)|" +
                "(?:不用|不要|不需要|别)(?:再)?导航了?",
            "stop navigation",
        ),
        rule(
            "(?:我们|咱们)?(?:大概|大约|预计|还有)?(?:什么时候|几点|多久|多长时间|多少时间)(?:能|可以|会|才能|才)?(?:到|到达|抵达).*|" +
                "还(?:有|要|得|需要)?(?:多久|多长时间|多少时间|多远|几分钟|多少分钟|几公里|多少公里).*|" +
                "(?:预计)?的?(?:到达|抵达)时间(?:是)?(?:几点|多少)?|(?:是不是)?快到了(?:没有?)?|到了没有?|还没到|到哪(?:了|儿了|里了)|" +
                "离目的地还有(?:多远|多久)?",
            "when do i arrive",
        ),
        // Messages, before "播放…" is taken for a song.
        rule(
            "(?:读|读读|念|念念|朗读|播报|播放|查看|看看|看|检查|听|听听)(?:一下)?(?:我的)?(.*?)的?(?:消息|信息|短信|微信|简讯|讯息)|" +
                "(?:我)?(?:有|有没有)(.*?)的?(?:消息|信息|短信|微信|简讯|讯息)|(?:新|未读|最新|最后一条|最近的|最近一条)?的?(?:消息|信息|短信|简讯|讯息)",
        ) { m ->
            val said = simplified(m.value)
            when {
                listOf("最后", "最新", "最近", "上一条", "刚才", "刚刚").any { it in said } -> "read my last message"
                listOf("新", "未读", "没读", "没看").any { it in said } || said.startsWith("有") || said.startsWith("我有") -> "read my new messages"
                else -> "read my messages"
            }
        },
        rule("(?:回复|答复)(?:一下)? ?(?:给)? ?(.+?)(?: ?的)?(?:消息|信息|短信|微信|简讯|讯息)?") { m -> reply(m.groupValues[1]) },
        rule("回(?!复|拨|电) ?(.+?) ?的?(?:消息|信息|短信|微信|简讯|讯息)") { m -> reply(m.groupValues[1]) },
        rule("给 ?(.+?) ?(?:回复|回|发送|发|写|留言|留)(?:一)?(?:条|个|句)?(?:消息|信息|短信|微信|简讯|讯息|话)?") { m -> reply(m.groupValues[1]) },
        rule("(?:发送|发|写)(?:一)?(?:条|个)?(?:消息|信息|短信|微信|简讯|讯息)(?:给|到) ?(.+)") { m -> reply(m.groupValues[1]) },
        // Calls.
        rule(
            "(?:给(?:他|她|它|ta|对方))?(?:回拨|回电话|回个电话|回一个电话|回电|打回去|拨回去)(?:一下)?(?:给(?:他|她|对方))?|" +
                "回拨(?:一下)?(?:刚才|刚刚|最后|最近|上一个|最近一个)(?:的|那个|一个|那通)?(?:电话|来电|未接来电|号码)|" +
                "回拨(?:未接|漏接)的?(?:电话|来电)",
            "call back",
        ),
        rule(
            "(?:重拨|重新拨打|重新拨号|重新打|再拨一次|再打一次|再打一遍|再拨一遍|再打过去|再拨过去|再呼叫一次)(?:一下)?" +
                "(?:上一个|最后一个|刚才的|刚才那个|刚才)?(?:号码|电话)?|(?:拨打|打给|呼叫|拨|打)(?:上一个|最后一个|刚才那个|刚才的|刚刚的|刚刚那个)(?:号码|电话)",
            "redial",
        ),
        rule("打电话|打个电话|打一个电话|拨电话|拨打电话|拨号|呼叫", "call"),
        rule("打(?:个)?电话回家(?:里)?", "call 家"),
        rule("(?:给|跟) ?(.+?) ?的? ?($TYPES) ?(?:打|拨)(?:一)?(?:个|通)?电话") { m -> callOn(m) },
        rule("(?:打电话|拨电话|打个电话|拨个电话|打一个电话|拨打电话)(?:给|到) ?(.+?) ?的? ?($TYPES)(?:上|里)?") { m -> callOn(m) },
        rule("(?:打给|拨给|拨打|呼叫|拨|打(?!开|算|电话|个电话)) ?(.+?) ?的? ?($TYPES)的?(?:电话|号码|号)?") { m -> callOn(m) },
        rule("(?:给|跟|和) ?(.+?) ?(?:打|拨)(?:一)?(?:个|通)?电话(?:过去)?") { m -> call(m.groupValues[1]) },
        rule("给 ?(.+?) ?回(?:一)?(?:个)?电话") { m -> call(m.groupValues[1]) },
        rule("(?:打电话|拨电话|打个电话|拨个电话|打一个电话|拨打电话)(?:给|到) ?(.+)") { m -> call(m.groupValues[1]) },
        rule("(?:打给|拨给|拨打|呼叫|致电|联系(?!人)|联络|回拨) ?(.+)") { m -> call(m.groupValues[1]) },
        rule("(?:打(?!开|算|电话|个电话)|拨) ?(.+?) ?的?(?:电话|号码|手机号)") { m -> call(m.groupValues[1]) },
        // The music, before "播放…" is taken for a song.
        rule(
            "(?:先)?(?:暂停|停止|停下|停掉|停)(?:一下)?(?:播放|音乐|歌曲|歌|放歌)?(?:了)?|(?:关闭|关掉|关上|关)(?:音乐|歌)|" +
                "(?:把)?(?:音乐|歌)(?:暂停|停|停下|停下来|关掉|关了|停了|停掉|关上)(?:一下)?|(?:别|不要|不)(?:放|播|唱|听)了?|不听了|" +
                "安静(?:一下|点|一点)?|闭嘴|静音|音乐暂停",
            "pause",
        ),
        rule(
            "(?:给我)?(?:继续|恢复|接着|开始)(?:播放|放|播|听|唱)?(?:音乐|歌曲|歌)?|" +
                "(?:给我们|给我)?(?:播放|放|播|听|来|开|打开)(?:一)?(?:首|点|些|个)?(?:音乐|歌曲|歌)|播放|音乐|play|" +
                "(?:把)?音乐(?:打开|开起来|放起来|继续|开)",
            "continue",
        ),
        rule(
            "(?:播放|放|来|切到|切换到|切换|换到|跳到|切|换)?下一(?:首|曲|个)(?:歌)?|切歌|切一首|(?:换|切)(?:一)?(?:首|个)(?:歌)?|" +
                "换(?:一)?首别的|换(?:点|个)别的|换歌|跳过(?:这首|这一首|这个)?(?:歌)?|(?:放|来|播放)(?:点)?别的(?:歌)?|" +
                "再来(?:一)?首|不(?:想)?听这(?:首|个)(?:歌)?(?:了)?|这首(?:歌)?不好听",
            "next",
        ),
        rule(
            "(?:播放|放|回到|返回|切到|切换到|切换|切回|回|换回|退回)?(?:上一|前一)(?:首|曲|个)(?:歌)?|" +
                "(?:播放|放|再放|回到|回)?刚才(?:那|的|那一)首(?:歌)?",
            "previous",
        ),
        rule(
            "(?:把)?(?:这首歌|这一首|这首|这个)?(?:再|重新)(?:放|播放|播|听|来|唱)(?:一遍|一次|一下)?(?:这首歌|这一首|这首)?(?:一遍|一次)?|" +
                "重播|重放|重新开始|" +
                "从头(?:开始|播放|放|听|再来)?|再来一遍|重来一遍|重来|单曲重播",
            "play it again",
        ),
        rule(
            "(?:这|这个|现在|刚才|正在|在)?(?:放|播放|播|唱|放着|播着)?的?(?:是)?(?:什么|啥|哪首|哪一首)(?:歌|歌曲|音乐|曲子|歌儿)|" +
                "这(?:首|个)?(?:歌|歌曲|曲子)?(?:叫什么|叫啥|是什么|是哪首|名字是什么|是什么名字)(?:名字|歌)?|" +
                "(?:这|这首|这首歌|这歌|这个)?(?:是)?谁(?:唱|在唱)?的(?:歌)?|(?:歌名|歌手|歌的名字)(?:是|叫)?(?:什么|谁|啥)?|" +
                "(?:现在|正在|在)?(?:放|播放|播)的?(?:是)?什么|听歌识曲",
            "what's playing",
        ),
        // Music on an app named first: "用Spotify播放周杰伦" (one character is a place: "在车上听歌").
        rule("(?:给我)?(?:用|使用|在|打开) ?(.+?) ?(?:上|里|里面|中)? ?(?:播放|放|播|听|来)(?:一首|一曲|首|点|一点|一下)? ?(.*)") { m ->
            val app = m.groupValues[1].trim().takeIf { it.length > 1 } ?: return@rule null
            "play ${song(m.groupValues[2]) ?: "music"} on $app"
        },
        rule("(?:给我们|给我)?(?:来|放|播)(?:一)?(?:首|曲|点|些|个|段) ?(.+)") { m -> song(m.groupValues[1])?.let { "play $it" } ?: "continue" },
        rule("(?:给我们|给我)?(?:播放|播(?!报|放)|放(?!大|小|慢|弃|心|松|假|学|过|开|下|回|在|到|进|入|着)|听听|听(?!不|见|到|说|懂|清)) ?(.+)") { m ->
            song(m.groupValues[1])?.let { "play $it" } ?: "continue"
        },
        // The time and the weather, before "打开…" is taken for an app.
        rule(
            "(?:现在)?(?:是)?(?:几点|几点钟|多少点|什么时间|什么时候了)(?:了)?(?:现在)?|(?:现在的?)?时间(?:是)?(?:多少|几点)?|报时|报一下时间|" +
                "告诉我(?:现在)?的?时间|看(?:一下|看)?时间",
            "what time is it",
        ),
        rule(
            ".*(?:天气|气温).*|(?:现在|外面|今天|室外)?的?温度(?:是)?(?:多少|几度|怎么样)?(?:度)?|(?:现在|外面|今天|室外)?(?:几度|多少度)(?:了)?|" +
                "(?:今天|明天|等会|待会|一会儿|现在|外面)?(?:会|要|会不会|要不要|是不是|还在|在)?下(?:雨|雪)(?:了|没有?)?|" +
                "(?:外面|今天|现在)?(?:冷不冷|热不热|冷|热)",
            "what's the weather",
        ),
        // Places: "找…" only for what's near; "去…" a place or an app; the rest a place.
        rule("(?:找找|查找|寻找|搜索|找|搜)(?:一下)? ?(一个|一家|一间|个|间)? ?(.+)") { m ->
            val p = place(m.groupValues[2])
            when {
                p.startsWith("the nearest ") -> "navigate to $p"
                m.groupValues[1].isNotEmpty() -> "navigate to the nearest $p"
                else -> null
            }
        },
        rule("(?:哪里|哪儿|哪)有 ?(.+)") { m -> nearest(m.groupValues[1]) },
        rule("(?:附近|周边|周围)(?:有没有|有) ?(.+)") { m -> nearest(m.groupValues[1]) },
        rule("(.+?) ?(?:在哪里|在哪儿|在哪|在什么地方)") { m ->
            m.groupValues[1].takeUnless { Regex(both("^(?:我|你|他|她|它|我们|你们|他们)")).containsMatchIn(it) }?.let(::nearest)
        },
        // Said alone: "附近的加油站", "最近的停车场" ("最近" with no "的" is "lately").
        rule("(?:离(?:我|我们|这|这里|这儿)?最近的|最近的|附近的?|周边的|周围的|就近的)(?:一个|一家|一间|一座)? ?(.+)") { m -> nearest(m.groupValues[1]) },
        // Navigation with the app first: "用Vela导航到北京". Not "打开空调到26度": that's for the house.
        rule("(?:给我)?(?:用|使用|通过) ?(.+?) ?(?:导航到|导航去|导航至|导航回|导航|带我去|带我到|带我回|去|到|回|前往) ?(.+)") { m ->
            navigate(m.groupValues[2])?.let { "use ${m.groupValues[1].trim()} to $it" }
        },
        rule("(?:打开|在) ?(.+?) ?(?:上|里)? ?(?:导航到|导航去|导航至|导航回|导航) ?(.+)") { m ->
            navigate(m.groupValues[2])?.let { "use ${m.groupValues[1].trim()} to $it" }
        },
        rule("(?:用|使用|打开) ?(.+?) ?导航") { m -> "open " + m.groupValues[1].trim() },
        rule("(?:打开|开启|启动|运行|进入|切换到|切到|显示|调出) ?(.+)") { m -> appName(m.groupValues[1]).takeIf { it.isNotEmpty() }?.let { "open " + app(it) } },
        rule("回(家|家里|公司|单位|学校|老家|宿舍|酒店|宾馆)(?:去)?(?:怎么走)?") { m -> navigate(m.groupValues[1]) },
        rule("(?:去|到)? ?(.+?) ?怎么走") { m -> navigate(m.groupValues[1]) },
        rule("(?:去|前往|回到|到(?!哪)) ?(.+)") { m ->
            val said = m.groupValues[1]
            if ("哪" in simplified(said) || said.endsWith("了")) return@rule null
            val p = place(said)
            when {
                p.isEmpty() -> null
                p.startsWith("the nearest ") -> "navigate to $p"
                else -> "go to " + app(p)
            }
        },
        rule(
            "(?:给我)?(?:导航到|导航去|导航至|导航回|导航|开导航去|开导航到|带我去|带我到|带我回|带我们去|带我们到|带我们回|送我去|送我到|送我回|" +
                "开车去|开车到|开车回|开到|开去|驾车去|驾车到|怎么去|怎么到|怎么走到|规划(?:一下)?(?:去|到)|开往) ?(.+?)(?:的路线|的路)?",
        ) { m -> navigate(m.groupValues[1]) },
    )

    // VoiceReply takes a run of Chinese without punctuation as one word: whole short answers, then.
    override val yes = setOf(
        "是", "是的", "是啊", "是呀", "对", "对的", "对啊", "对呀", "没错", "好", "好的", "好啊", "好呀", "好吧", "好嘞", "行", "行啊", "行吧",
        "可以", "可以的", "可以啊", "嗯", "嗯嗯", "嗯好", "要", "要的", "要啊", "回", "回吧", "回复", "回复吧", "回复一下", "要回复", "我要回复",
        "好的回复", "当然", "当然要", "没问题", "确定", "ok", "okay",
    ).flatMap(::forms).toSet()
    override val no = setOf(
        "不", "不要", "不用", "不用了", "不用啦", "不了", "不必", "不需要", "不想", "算了", "算了吧", "算啦", "不回", "不回了", "不回复", "不用回",
        "不用回了", "不用回复", "不要回复", "不想回复", "先不回", "先不回复", "先不用", "暂时不用", "等会再说", "等一下再说", "以后再说", "回头再说",
        "等会再回", "没事", "没事了", "取消", "不是", "否", "别", "别回", "no",
    ).flatMap(::forms).toSet()
    override val cancel = setOf(
        "取消", "取消吧", "取消发送", "算了", "算了吧", "算啦", "不发了", "不发啦", "不发", "不发送", "别发了", "别发", "别发送", "不要发", "不要发送",
        "不用发了", "不要了", "不用了", "停", "停止", "不", "不要",
    ).flatMap(::forms).toSet()
    override val change = setOf(
        "修改", "改", "改一下", "改一改", "改一下吧", "更改", "重说", "重说一遍", "重新说", "重新说一遍", "我重新说", "重来", "重新来", "再说一遍",
        "重新录", "说错了", "错了", "不对",
    ).flatMap(::forms).toSet()
    override val send = setOf(
        "发送", "发送吧", "发", "发吧", "发了吧", "发出去", "发出去吧", "发过去", "发给他", "发给她", "可以发了", "好的发送", "好的发吧", "可以发送",
        "确认发送", "确定发送", "直接发送", "就这样", "就这样发", "确定", "确认", "好", "好的", "可以", "行", "是", "是的", "对", "没错", "嗯",
        "没问题", "ok",
    ).flatMap(::forms).toSet()

    /** "14:05" → "14点05分", "下午2:30" → "下午2点30分", "2:05 PM" → "下午2点05分": the time as it's said. */
    private fun clock(time: String): String =
        Regex("(\\d{1,2}):(\\d{2})(?:[\\s\\u00A0\\u202F]*([AaPp])\\.?[Mm]\\.?)?").replace(time) { m ->
            val (hour, minute, half) = m.destructured
            val before = when (half.lowercase()) { "a" -> "上午"; "p" -> "下午"; else -> "" }
            before + hour.trimStart('0').ifEmpty { "0" } + "点" + if (minute == "00") "" else "${minute}分"
        }

    /** "1 hour 5 minutes" → "1小时5分钟": the time left, with no spaces. */
    private fun left(english: String): String {
        val h = Regex("(\\d+) hours?").find(english)?.groupValues?.get(1)?.let { "${it}小时" }
        val m = Regex("(\\d+) minutes?").find(english)?.groupValues?.get(1)?.let { "${it}分钟" }
        return listOfNotNull(h, m).joinToString("").ifEmpty { english }
    }

    /** myCar's own apps, which the car's screen labels in English, in Chinese. */
    private val OWN_APPS = mapOf(
        "Browser" to "浏览器", "Phone" to "电话", "Screen sharing" to "屏幕共享", "Cinema" to "影院", "Vehicle data" to "车辆数据", "Settings" to "设置",
    )

    /** A kind of number, as the phone's Chinese labels have it ("手机", "住宅", "单位"). */
    private fun kind(type: String) = when (type) {
        "mobile", "cell" -> "手机"
        "home" -> "住宅"
        "work" -> "单位"
        "phone" -> "电话"
        "other" -> "其他"
        else -> type
    }

    override val answers: Map<Said, (List<String>) -> String> = mapOf(
        Said.HELP to t("你可以这样说。"),
        Said.UNKNOWN to t("抱歉，这个我还不会。"),
        Said.WHERE_TO to t("你想去哪里？"),
        Said.NO_NAV_APPS to t("你还没有安装导航应用。"),
        Said.NO_NAV_APP_CALLED to t("找不到叫$1的导航应用。"),
        Said.NOT_NAV_APP to t("这不是导航应用。"),
        Said.FINDING_NEAREST to t("好的，用$2查找最近的$1。"),
        Said.NAVIGATING to { g -> if (g[1] == "家") "好的，用${g[2]}导航回家。" else "好的，用${g[2]}导航到${g[1]}。" },
        Said.OPENING_SETTINGS to t("正在打开设置。"),
        Said.OPENING_VEHICLE_DATA to t("正在打开车辆数据。"),
        Said.OPENING to { g -> "正在打开${OWN_APPS[g[1]] ?: g[1]}。" },
        Said.OPEN_MUSIC_FIRST to t("请先打开一个音乐应用，然后再问我一次。"),
        Said.MUSIC_APP_LATE to t("音乐应用没能及时打开，请再试一次。"),
        Said.IGNORES_SEARCH to t("$1不支持语音搜索，请直接在它的界面上选择。"),
        Said.LOOKING_FOR to t("正在$2中查找$1。"),
        Said.NEEDS_NOTIFICATIONS to t("myCar需要通知使用权才能读消息，请在手机上允许。"),
        Said.NO_MESSAGE_FROM to t("没有找到$1发来的消息，无法回复。"),
        Said.NO_MESSAGES_YET to t("还没有消息。"),
        Said.NO_NEW_MESSAGES to t("没有新消息。"),
        Said.ENDING_ROUTE to t("好的，导航已结束。"),
        Said.PAUSED to t("已暂停。"),
        Said.NOTHING_PLAYING to t("现在没有在播放。"),
        Said.SKIPPING to t("下一首。"),
        Said.PREVIOUS_TRACK to t("上一首。"),
        Said.PLAYING to t("继续播放。"),
        Said.PLAYING_APP to { g -> if (g[1] == "the music") "开始播放音乐。" else "好的，用${g[1]}播放音乐。" },
        Said.GOING_BACK to t("返回。"),
        Said.BACK_TO_CAR to t("回到车机界面。"),
        Said.HEADING_HOME to t("回到主页。"),
        Said.YOUR_APPS to t("这是你的应用。"),
        Said.VEHICLE_DATA_OFF to t("车辆数据已关闭。请在设置的实验性功能里打开。"),
        Said.TIME to { g -> "现在是${clock(g[1])}。" },
        Said.WEATHER to { g -> "${g[3]}现在${g[1]}" + (sky(g[2])?.let { "，$it" } ?: "") + "。" },
        Said.NO_WEATHER to t("还没有天气信息。"),
        Said.NO_ROUTE to t("地图上没有路线。"),
        Said.ARRIVE_AT_IN to { g -> "预计${clock(g[1])}到达，还有${left(g[2])}。" },
        Said.ARRIVE_AT to { g -> "预计${clock(g[1])}到达。" },
        Said.ARRIVE_IN to { g -> "大约${left(g[1])}后到达。" },
        Said.MAP_HASNT_SAID to t("地图还没有给出到达时间。"),
        Said.NEEDS_PHONE to t("myCar需要先获得电话权限，请在手机屏幕上允许。"),
        Said.NEEDS_CALL_LOG to t("myCar需要先获得通话记录权限，请在手机上允许。"),
        Said.NO_REDIAL to t("没有可以重拨的电话。"),
        Said.NO_CALL_BACK to t("没有可以回拨的电话。"),
        Said.PRIVATE_NUMBER to t("那通电话是隐藏号码打来的，无法回拨。"),
        Said.CALLING_AGAIN to t("正在重拨$1。"),
        Said.CALLING_BACK to t("正在回拨$1。"),
        Said.WHO_TO_CALL to t("要打给谁？"),
        Said.NEEDS_CONTACTS to t("myCar需要先获得电话和通讯录权限，请在手机屏幕上允许。"),
        Said.NOT_IN_CONTACTS to t("通讯录里没有找到$1。"),
        Said.NO_SUCH_NUMBER to { g ->
            "${g[1]}没有${kind(g[2])}号码。改打${if (g[3] == "the other one") "另一个" else kind(g[3])}号码吗？"
        },
        Said.CALL_QUESTION to t("要打给$1吗？"),
        Said.CALLING_TYPE to { g -> "正在拨打${g[1]}的${kind(g[2])}号码。" },
        Said.CALLING to t("正在呼叫$1。"),
        Said.NO_APP_CALLED to t("找不到叫$1的应用。"),
        Said.SOMETHING_WRONG to t("出了点问题，请再试一次。"),
        Said.DONE to t("已完成。"),
        Said.HOME_ASSISTANT_SILENT to t("Home Assistant没有回应。"),
        Said.NEEDS_MIC to t("需要麦克风权限。"),
        Said.AFTER_CALL to t("通话结束后才能使用语音指令。"),
        Said.DIDNT_CATCH to t("抱歉，没听清。"),
        Said.RECOGNIZER_BUSY to t("语音识别正忙。"),
        Said.RECOGNIZER_NETWORK to t("语音识别需要联网，但现在没有网络。"),
        Said.RECOGNITION_FAILED to t("语音识别失败了。"),
        Said.SPEECH_NOT_READY to t("语音功能还没准备好。"),
        Said.MODEL_NOT_LOADED to t("语音模型没有加载成功。"),
        Said.MODEL_NOT_DOWNLOADED to t("请先在手机上的myCar设置里下载语音模型。"),
        Said.MIC_SILENCED to t("手机的麦克风被静音了。请解锁手机后再试一次。"),
        Said.MIC_UNAVAILABLE to t("麦克风现在不可用。"),
        Said.MISSED_CALL_FROM to t("来自$1的未接来电。"),
        Said.WANT_TO_REPLY to t("要回复吗？"),
        Said.GO_AHEAD to t("请说。"),
        Said.NO_CAR_REPLIES to t("$1不支持在车上回复。"),
        Said.REPLY_AFTER_CALL to t("通话结束后才能语音回复。"),
        Said.YOUR_REPLY_TO to t("要回复$1什么？"),
        Said.YOUR_REPLY to t("要回复什么？"),
        Said.SENDING_TO to t("发送给$1：$2"),
        Said.SENDING to t("发送：$1"),
        Said.NOT_SENT to t("未发送。"),
        Said.MESSAGE_SENT to t("消息已发送。"),
        Said.REPLY_WITHDRAWN to t("$1撤回了回复功能。请在手机上打开应用回复。"),
        Said.NOW_PLAYING to t("这是$2的《$1》。"),
    )

    /** The sky as Weather says it, in the words of a Chinese forecast; null for none ("-"). */
    private fun sky(english: String): String? = when (english) {
        "Clear" -> "晴"
        "Mostly clear" -> "少云"
        "Partly cloudy" -> "多云"
        "Overcast" -> "阴天"
        "Fog" -> "有雾"
        "Drizzle" -> "毛毛雨"
        "Rain" -> "有雨"
        "Snow" -> "有雪"
        "Rain showers" -> "阵雨"
        "Snow showers" -> "阵雪"
        "Thunderstorm" -> "雷阵雨"
        "-" -> null
        else -> english
    }

    override val samples = listOf("播放周杰伦的歌", "这是什么歌？", "下一首", "给妈妈打电话", "打开Organic Maps", "暂停", "帮助")

    override fun says(who: String, text: String) = "${who}说：$text"
    override fun inGroup(sender: String, group: String) = "${sender}在${group}里"

    override val help = listOf(
        "导航到…" to "规划路线。说“用Organic Maps导航到…”可以选择应用",
        "播放…" to "在打开的音乐应用里播放歌曲、歌手或专辑",
        "暂停 · 继续 · 下一首 · 上一首" to "控制音乐",
        "这是什么歌？" to "歌名和歌手",
        "给…打电话" to "按名字呼叫联系人。说“给…的手机打电话”可以选择号码",
        "回拨 · 重拨" to "最近的来电，或最近拨出的电话",
        "读消息 · 回复…" to "朗读新消息，或回复给你发消息的人",
        "结束导航" to "结束当前路线",
        "什么时候到？" to "到达时间和剩余时间",
        "打开…" to "打开应用，比如浏览器、电话或屏幕共享",
        "退出" to "回到车机界面",
        "回到主页 · 所有应用 · 设置" to "在myCar里切换界面",
        "现在几点？ · 天气怎么样？" to "时间或天气",
        "帮助" to "这个列表",
    )

    override val labels = mapOf(
        "Listening…" to "我在听…",
        "One moment…" to "稍等…",
        "You can say" to "你可以说",
        "Say “cancel” or “change it”" to "说“取消”或“修改”",
        "Do you want to reply? Say yes or no." to "要回复吗？请说“好”或“不用”。",
        "Get ready to say your reply." to "请准备说出你的回复。",
        "Say your reply" to "请说出你的回复",
        "Anything else" to "其他事情",
        "Home Assistant answers it: your home, and any question its voice assistant knows." to "由Home Assistant回答：家里的设备，以及它的语音助手能回答的任何问题。",
    )
}
