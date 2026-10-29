package io.github.sirionero.mycar.car

import java.util.Locale

/**
 * The assistant in Portuguese (see [AssistantLanguage]), for Brazil and Portugal alike: it hears
 * both ("celular", "telemóvel"), and answers in words both use ("Vou ligar para…").
 */
internal object PortuguesePhrases : Phrasebook() {

    override val leadFillers = listOf(
        "ei mycar", "ok mycar", "olá mycar", "ola mycar", "ei my car", "ok my car", "mycar", "my car", "ei", "olá", "ola", "ok", "okay",
        "então", "entao", "por favor", "você pode", "voce pode", "pode", "podes", "poderia", "consegue", "eu quero", "quero",
        "queria", "gostaria de", "agora",
    )
    override val tailFillers = listOf("por favor", "obrigado", "obrigada", "valeu", "agora")
    override val appWords = listOf(" com ", " no ", " na ", " pelo ", " usando ")

    private val NEAR = Regex(
        "^(.+?) (?:mais próxim[oa]s?|mais proxim[oa]s?|mais perto|perto daqui|perto de mim|aqui perto|nas proximidades|por perto)" +
            "((?: (?:com|no|na|pelo|usando) .+)?)$",
    )

    /** "para o posto mais próximo" → "the nearest posto", "para Lisboa" → "lisboa": the place, for the map. */
    private fun place(said: String): String {
        var t = said.trim()
        t = t.replace(Regex("^(?:para o|para a|para os|para as|pro|pra|para|até o|até a|até ao|até à|até|ao|à|aos|às|no|na|em)\\s+"), "")
        t = t.replace(Regex("^(?:o|a|os|as|um|uma)\\s+"), "")
        NEAR.matchEntire(t)?.let { return "the nearest " + it.groupValues[1] + it.groupValues[2] }
        return t
    }

    /** "o posto mais próximo" out of "para o posto mais próximo": what's near, with its article. */
    private fun withArticle(said: String): String {
        val articles = listOf(
            "para o " to "o ", "para a " to "a ", "para os " to "os ", "para as " to "as ", "ao " to "o ", "à " to "a ",
            "aos " to "os ", "às " to "as ", "pro " to "o ", "até o " to "o ", "até a " to "a ",
        )
        for ((from, to) in articles) if (said.startsWith(from)) return to + said.removePrefix(from)
        return said
    }

    /** Where to, said back: "o centro" (no "para" heard) becomes "para o centro", "minha casa" is "sua casa". */
    private fun there(said: String): String {
        val t = said.replace(Regex("\\bminha casa\\b"), "sua casa")
        for ((from, to) in listOf("o " to "para o ", "a " to "para a ", "os " to "para os ", "as " to "para as ")) {
            if (t.startsWith(from)) return to + t.removePrefix(from)
        }
        return t
    }

    private fun navigate(said: String) = "navigate to " + remember(place(said), said)

    private fun who(raw: String): String = raw.trim().replace(Regex("^(?:para o|para a|pro|pra|para|ao|à|a|o)\\s+"), "").trim()

    private val numberType = mapOf(
        "celular" to "mobile", "telemóvel" to "mobile", "telemovel" to "mobile", "móvel" to "mobile", "movel" to "mobile",
        "fixo" to "home", "casa" to "home", "residencial" to "home",
        "trabalho" to "work", "escritório" to "work", "escritorio" to "work", "comercial" to "work",
    )

    private fun app(name: String) = when (name) {
        "telefone" -> "phone"
        "compartilhar tela", "compartilhamento de tela", "partilha de ecrã", "partilha de ecra" -> "screen sharing"
        "dados do carro", "dados do veículo", "dados do veiculo" -> "vehicle data"
        "configurações", "configuracoes", "definições", "definicoes", "ajustes" -> "settings"
        "navegador" -> "browser"
        else -> name
    }

    private const val TYPES =
        "celular|telemóvel|telemovel|móvel|movel|fixo|casa|residencial|trabalho|escritório|escritorio|comercial"

    override val commands = listOf(
        Rule("(?:ajuda|o que eu posso dizer|o que posso dizer|o que você sabe fazer|o que voce sabe fazer|o que sabes fazer|comandos|comandos de voz)", "help"),
        Rule(
            "(?:(?:vá para|va para|vai para|volte para|volta para|abra|abre) (?:a |o )?)?(?:tela inicial|ecrã inicial|ecra inicial|início|inicio)",
            "go home",
        ),
        Rule(
            "(?:(?:abra|abre|abrir|mostre|mostra|vá para|vai para) )?(?:as )?(?:configurações|configuracoes|definições|definicoes|ajustes)",
            "open settings",
        ),
        Rule("(?:(?:mostre|mostra|abra|abre) )?(?:os meus |meus |os |todos os )?(?:apps|aplicativos|aplicações|aplicacoes)", "open apps"),
        Rule("sair|sai|sair do mycar|fechar mycar|fecha o mycar|feche o mycar", "exit"),
        Rule("voltar|volta|volte|voltar atrás|volta atrás|para trás|para tras", "go back"),
        Rule(
            "(?:pare|para|parar|termine|termina|cancele|cancela|encerre|encerra)(?: a| o)? (?:navegação|navegacao|rota|percurso|trajeto)|" +
                "parar de navegar",
            "stop navigation",
        ),
        Rule(
            "(?:quando eu chego|quando chego|quando a gente chega|quando chegamos|a que horas eu chego|a que horas chego|" +
                "que horas eu chego|que horas chego|quanto tempo falta|quanto falta|quanto tempo ainda|hora de chegada)(?: .*)?",
            "when do i arrive",
        ),
        Rule("(?:leia|lê|le|ler|lê me|leia me|verifique|verifica|confere|confira)(?: .*)? (?:mensagens|mensagem|sms)(?: .*)?") { m ->
            val said = m.value
            when {
                "nova" in said || "não lida" in said || "nao lida" in said -> "read my new messages"
                "últim" in said || "ultim" in said -> "read my last message"
                else -> "read my messages"
            }
        },
        Rule(
            "(?:responda|responde|responder|escreva|escreve|escrever|mande uma mensagem|manda uma mensagem|envie uma mensagem|" +
                "envia uma mensagem) (?:para o|para a|para|pro|pra|ao|à|a) (.+)",
        ) { m -> "reply to " + who(m.groupValues[1]) },
        Rule(
            "(?:retorne a ligação|retorna a ligação|retornar a ligação|retorne a ligacao|retorne a chamada|retorna a chamada|" +
                "ligue de volta|liga de volta|devolva a chamada|devolve a chamada|retorne a chamada perdida|retorna a chamada perdida)",
            "call back",
        ),
        Rule(
            "(?:ligue de novo|liga de novo|ligue novamente|liga novamente|ligar de novo|rediscar|rediscagem|" +
                "ligue para o último número|liga para o último número|ligue para o ultimo numero|repetir a última chamada|" +
                "repete a última chamada)",
            "redial",
        ),
        Rule("(?:ligue|liga|ligar|telefone|telefona|telefonar|chame|chama) (.+?) (?:no|na|pelo|para o) ($TYPES)") { m ->
            "call ${who(m.groupValues[1])} on ${numberType.getValue(m.groupValues[2])}"
        },
        Rule("(?:ligue|liga|ligar|telefone|telefona|faça uma ligação|faz uma ligação|fazer uma ligação)", "call"),
        Rule("(?:ligue|liga|ligar|telefone|telefona|telefonar|chame|chama|faça uma ligação|faz uma ligação) (.+)") { m ->
            "call " + who(m.groupValues[1])
        },
        Rule(
            "(?:pausa|pause|pausar|pare a música|para a música|pare a musica|para a musica|parar|pare|silêncio|silencio|" +
                "desliga a música|desligue a música)",
            "pause",
        ),
        Rule(
            "(?:continua|continue|continuar|retoma|retome|volta a tocar|volte a tocar|toca música|toque música|toca a música|" +
                "toque a música|música|musica|play)",
            "continue",
        ),
        Rule(
            "(?:próxima|proxima|próximo|proximo|próxima música|proxima musica|próxima faixa|seguinte|pula|pule|pular|passa|passe|" +
                "avança|avance|outra|outra música|outra musica|toca outra|toque outra|muda de música|muda a música)",
            "next",
        ),
        Rule("(?:anterior|música anterior|musica anterior|faixa anterior|volta uma música|volte uma música|a de antes)", "previous"),
        Rule(
            "(?:repete|repita|repetir|toca de novo|toque de novo|toca outra vez|toque outra vez)(?: essa| esta| a)?(?: música| musica| faixa)?" +
                "(?: do começo| do comeco| do início| do inicio)?",
            "play it again",
        ),
        Rule(
            "(?:que música é essa|que musica e essa|qual é essa música|qual e essa musica|o que está tocando|o que esta tocando|" +
                "o que tá tocando|quem canta|quem está cantando|quem esta cantando|que música está tocando)(?: .*)?",
            "what's playing",
        ),
        Rule("(?:quero ouvir|toca|toque|tocar|coloca|coloque|põe|poe|ponha|reproduz|reproduza|reproduzir|ouvir|bota|bote) (.+)", "play $1"),
        Rule("(?:encontre|encontra|procure|procura|busca|busque|ache|acha) (.+)") { m ->
            m.groupValues[1].takeIf { place(it).startsWith("the nearest ") }?.let(::navigate)
        },
        Rule("(?:abra|abre|abrir|inicia|inicie|lança|lance|mostra|mostre) (.+)") { m -> "open " + app(place(m.groupValues[1])) },
        Rule("(?:vá|va|vai|vamos|ir|volta|volte|voltar) (.+)") { m ->
            val p = place(m.groupValues[1])
            if (app(p) != p) "go to " + app(p) else "go to " + remember(p, m.groupValues[1])
        },
        Rule(
            "(?:leve me|leva me|me leve|me leva|levar|navegue|navega|navegar|rota|como chego|como eu chego|como chegar|direções|" +
                "direcoes|me guie|me guia|guie me) (.+)",
        ) { m -> navigate(m.groupValues[1]) },
        Rule("(?:que horas são|que horas sao|que hora é|que hora e|me diz as horas|diz me as horas|as horas|horas)", "what time is it"),
        Rule(
            "(?:como está o tempo|como esta o tempo|como está o clima|como esta o clima|o tempo|tempo|clima|previsão do tempo|" +
                "previsao do tempo|vai chover|está chovendo|esta chovendo|está a chover)(?: .*)?",
            "what's the weather",
        ),
    )

    private fun left(english: String) = span(english, "hora" to "horas", "minuto" to "minutos", "e")

    /** "das 14:30", "da 1:30". */
    private fun at(time: String) = if (oneOClock(time)) "da ${time.removePrefix("0")}" else "das $time"

    /** Portugal's words or Brazil's, by the phone's country. */
    private fun pt(portugal: String, brazil: String) = if (Locale.getDefault().country == "PT") portugal else brazil

    private val screen get() = pt("ecrã", "tela")
    private val contacts get() = pt("Contactos", "Contatos")
    private val theApp get() = pt("a app", "o app")

    override val answers: Map<Said, (List<String>) -> String> = mapOf(
        Said.HELP to t("Veja o que pode dizer."),
        Said.UNKNOWN to t("Ainda não sei fazer isso."),
        Said.WHERE_TO to t("Para onde vamos?"),
        Said.NO_NAV_APPS to { _ -> pt("Não há nenhuma app de navegação instalada.", "Não há nenhum app de navegação instalado.") },
        Said.NO_NAV_APP_CALLED to { g -> pt("Não encontro uma app de navegação chamada ${g[1]}.", "Não encontro um app de navegação chamado ${g[1]}.") },
        Said.NOT_NAV_APP to { _ -> pt("Não é uma app de navegação.", "Não é um app de navegação.") },
        Said.FINDING_NEAREST to { g -> "Vou procurar ${asSaid(g[1])?.let(::withArticle) ?: "${g[1]} por perto"} com ${g[2]}." },
        Said.NAVIGATING to { g -> "Vamos ${asSaid(g[1])?.let(::there) ?: "para ${g[1]}"} com ${g[2]}." },
        Said.OPENING_SETTINGS to t("Vou abrir as configurações."),
        Said.OPENING_VEHICLE_DATA to t("Vou abrir os dados do carro."),
        Said.OPENING to t("Vou abrir $1."),
        Said.OPEN_MUSIC_FIRST to { _ -> pt("Abra primeiro uma app de música e peça de novo.", "Abra primeiro um app de música e peça de novo.") },
        Said.MUSIC_APP_LATE to { _ -> pt("A app de música não abriu a tempo. Tente de novo.", "O app de música não abriu a tempo. Tente de novo.") },
        Said.IGNORES_SEARCH to { g -> "${g[1]} ignora as pesquisas por voz. Escolha algo no $screen." },
        Said.LOOKING_FOR to t("Vou procurar $1 em $2."),
        Said.NEEDS_NOTIFICATIONS to t("Para ler as mensagens, o myCar precisa de acesso às notificações. Permita no telefone."),
        Said.NO_MESSAGE_FROM to t("Não encontro uma mensagem de $1 para responder."),
        Said.NO_MESSAGES_YET to t("Ainda não há mensagens."),
        Said.NO_NEW_MESSAGES to t("Não há mensagens novas."),
        Said.ENDING_ROUTE to t("Vou terminar a rota."),
        Said.PAUSED to t("Em pausa."),
        Said.NOTHING_PLAYING to t("Não há música agora."),
        Said.SKIPPING to t("Próxima."),
        Said.PREVIOUS_TRACK to t("Anterior."),
        Said.PLAYING to t("Música retomada."),
        Said.PLAYING_APP to { g -> if (g[1] == "the music") "Vou tocar a música." else "Vou tocar ${g[1]}." },
        Said.GOING_BACK to t("De volta."),
        Said.BACK_TO_CAR to t("De volta ao sistema do carro."),
        Said.HEADING_HOME to t("De volta ao início."),
        Said.YOUR_APPS to { _ -> pt("Aqui estão as suas apps.", "Aqui estão os seus apps.") },
        Said.VEHICLE_DATA_OFF to t("Os dados do carro estão desligados. Ative-os nas configurações, nas funções experimentais."),
        Said.TIME to { g -> if (oneOClock(g[1])) "É ${g[1].removePrefix("0")}." else "São ${g[1]}." },
        Said.WEATHER to { g -> listOfNotNull(g[1], sky(g[2])).joinToString(", ") + ", em ${g[3]}." },
        Said.NO_WEATHER to t("Ainda não tenho a previsão do tempo."),
        Said.NO_ROUTE to t("Não há nenhuma rota no mapa."),
        Said.ARRIVE_AT_IN to { g -> "Por volta ${at(g[1])}, daqui a ${left(g[2])}." },
        Said.ARRIVE_AT to { g -> "Por volta ${at(g[1])}." },
        Said.ARRIVE_IN to { g -> "Daqui a cerca de ${left(g[1])}." },
        Said.MAP_HASNT_SAID to t("O mapa ainda não disse."),
        Said.NEEDS_PHONE to { _ -> "O myCar precisa primeiro da permissão de Telefone: permita no $screen do telefone." },
        Said.NEEDS_CALL_LOG to t("O myCar precisa primeiro do histórico de chamadas: permita no telefone."),
        Said.NO_REDIAL to t("Não há nenhuma chamada para repetir."),
        Said.NO_CALL_BACK to t("Não há nenhuma chamada para retornar."),
        Said.PRIVATE_NUMBER to t("Essa chamada era de um número privado: não dá para retornar."),
        Said.CALLING_AGAIN to t("Vou ligar de novo para $1."),
        Said.CALLING_BACK to t("Vou retornar a chamada de $1."),
        Said.WHO_TO_CALL to t("Para quem devo ligar?"),
        Said.NEEDS_CONTACTS to { _ -> "O myCar precisa primeiro das permissões de Telefone e $contacts: permita no $screen do telefone." },
        Said.NOT_IN_CONTACTS to { g -> "Não encontro ${g[1]} nos seus ${contacts.lowercase()}." },
        Said.NO_SUCH_NUMBER to { g ->
            val mobile = pt("de telemóvel", "de celular")
            val kind = mapOf("mobile" to mobile, "cell" to mobile, "phone" to "de telefone", "home" to "de casa", "work" to "do trabalho")
            "${g[1]} não tem número ${kind[g[2]] ?: g[2]}. " + if (g[3] == "the other one") "Ligo para o outro?" else "Ligo para o número de ${g[3]}?"
        },
        Said.CALL_QUESTION to t("Ligo para $1?"),
        Said.CALLING_TYPE to t("Vou ligar para $1, $2."),
        Said.CALLING to t("Vou ligar para $1."),
        Said.NO_APP_CALLED to { g -> pt("Não encontro uma app chamada ${g[1]}.", "Não encontro um app chamado ${g[1]}.") },
        Said.SOMETHING_WRONG to { _ -> pt("Algo correu mal. Tente de novo.", "Algo deu errado. Tente de novo.") },
        Said.DONE to t("Feito."),
        Said.HOME_ASSISTANT_SILENT to t("O Home Assistant não respondeu."),
        Said.NEEDS_MIC to t("É preciso a permissão do microfone."),
        Said.AFTER_CALL to t("Os comandos de voz funcionam quando a chamada terminar."),
        Said.DIDNT_CATCH to t("Não entendi."),
        Said.RECOGNIZER_BUSY to t("O reconhecimento de voz está ocupado."),
        Said.RECOGNIZER_NETWORK to t("O reconhecimento de voz precisa de uma rede que não tem."),
        Said.RECOGNITION_FAILED to t("O reconhecimento de voz falhou."),
        Said.SPEECH_NOT_READY to t("A voz ainda não está pronta."),
        Said.MODEL_NOT_LOADED to t("O modelo de voz não carregou."),
        Said.MODEL_NOT_DOWNLOADED to { _ -> pt("Primeiro, transfira", "Primeiro, baixe") + " um modelo de voz nas configurações do myCar no telefone." },
        Said.MIC_SILENCED to t("O microfone do telefone está silenciado. Desbloqueie o telefone e tente de novo."),
        Said.MIC_UNAVAILABLE to t("O microfone não está disponível agora."),
        Said.MISSED_CALL_FROM to t("Chamada perdida de $1."),
        Said.WANT_TO_REPLY to t("Quer responder?"),
        Said.GO_AHEAD to t("Pode falar."),
        Said.NO_CAR_REPLIES to t("$1 não permite responder a partir do carro."),
        Said.REPLY_AFTER_CALL to t("A resposta por voz funciona quando a chamada terminar."),
        Said.YOUR_REPLY_TO to t("O que quer responder a $1?"),
        Said.YOUR_REPLY to t("O que quer responder?"),
        Said.SENDING_TO to t("Vou enviar para $1: $2"),
        Said.SENDING to t("Vou enviar: $1"),
        Said.NOT_SENT to t("Não enviado."),
        Said.MESSAGE_SENT to t("Mensagem enviada."),
        Said.REPLY_WITHDRAWN to { g -> "${g[1]} retirou a resposta. Abra $theApp no telefone para responder." },
        Said.NOW_PLAYING to t("$1, de $2."),
    )

    private fun sky(english: String): String? = when (english) {
        "Clear" -> "céu limpo"
        "Mostly clear" -> "céu quase limpo"
        "Partly cloudy" -> "parcialmente nublado"
        "Overcast" -> "encoberto"
        "Fog" -> "nevoeiro"
        "Drizzle" -> "chuvisco"
        "Rain" -> "chuva"
        "Snow" -> "neve"
        "Rain showers" -> "aguaceiros"
        "Snow showers" -> "aguaceiros de neve"
        "Thunderstorm" -> "trovoada"
        "-" -> null
        else -> english
    }

    override val yes = setOf("sim", "claro", "ok", "okay", "pode ser", "responde", "responder", "quero", "está bem", "ta bom", "tá bom")
    override val no = setOf("não", "nao", "cancela", "cancelar", "deixa", "deixa para lá", "deixa pra lá", "nada", "pare")
    override val cancel = setOf("cancela", "cancelar", "não", "nao", "pare", "para")
    override val change = setOf("muda", "mudar", "altera", "alterar", "corrige", "corrigir", "de novo", "outra vez", "errado")
    override val send = setOf("envia", "envie", "enviar", "manda", "mande", "sim", "ok", "okay", "pode")

    override val samples = listOf("Toca Anitta", "Que música é essa?", "Próxima música", "Ligue para a mãe", "Abra Organic Maps", "Pausa", "Ajuda")

    override fun says(who: String, text: String) = "$who diz: $text"
    override fun inGroup(sender: String, group: String) = "$sender em $group"

    override val help get() = listOf(
        "Leve-me para…" to "O caminho. Acrescente \"com Organic Maps\" para escolher $theApp",
        "Toca…" to "Uma música, um artista ou um álbum ${pt("na app de música aberta", "no app de música aberto")}",
        "Pausa · Continua · Próxima · Anterior" to "Controlar a música",
        "Que música é essa?" to "A música e quem canta",
        "Ligue para…" to "${pt("Um contacto", "Um contato")} pelo nome. Acrescente \"no ${pt("telemóvel", "celular")}\" para escolher o número",
        "Ligue de volta · Ligue de novo" to "A última chamada recebida, ou a última feita",
        "Leia as mensagens · Responda a…" to "As mensagens novas lidas em voz alta, ou uma resposta a quem escreveu",
        "Pare a navegação" to "Termina a rota",
        "Quando eu chego?" to "A hora de chegada e o tempo que falta",
        "Abra…" to "${pt("Uma app", "Um app")}, como Browser, Telefone ou ${pt("Partilha de ecrã", "Compartilhar tela")}",
        "Sair" to "De volta ao sistema do carro",
        "Início · Configurações" to "Andar pelo myCar",
        "Que horas são? · Como está o tempo?" to "As horas, ou o tempo",
        "Ajuda" to "Esta lista",
    )

    override val labels get() = mapOf(
        "Listening…" to pt("A ouvir…", "Ouvindo…"),
        "One moment…" to "Um momento…",
        "You can say" to "Pode dizer",
        "Say “cancel” or “change it”" to "Diga «cancela» ou «muda»",
        "Do you want to reply? Say yes or no." to "Quer responder? Diga sim ou não.",
        "Get ready to say your reply." to "Prepare-se para dizer a resposta.",
        "Say your reply" to "Diga a resposta",
    )
}
