package me.zeroeightsix.kami.plugin.sample

import me.zeroeightsix.kami.command.ClientCommand
import me.zeroeightsix.kami.util.text.MessageSendHelper

object SampleCommand : ClientCommand(
    name = "sample",
    description = "A sample command."
) {
    init {
        executeAsync {
            MessageSendHelper.sendRawChatMessage("E")
        }
    }
}