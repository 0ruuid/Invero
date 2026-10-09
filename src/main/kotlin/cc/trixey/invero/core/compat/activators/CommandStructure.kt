@file:OptIn(ExperimentalSerializationApi::class)

package cc.trixey.invero.core.compat.activators

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Invero
 * cc.trixey.invero.core.compat.activators.CommandStructure
 *
 * @author Arasple
 * @since 2023/2/25 15:22
 */
@Serializable
data class CommandStructure(
    @SerialName("name")
    val rawName: String,
    val aliases: List<String>?,
    val description: String?,
    val usage: String?,
    val permission: String?,
    val permissionMessage: String?,
    @JsonNames("argument", "args")
    val arguments: List<@Serializable(CommandArgumentSerializer::class) CommandArgument>?
) {

    @Transient
    val name = rawName.lowercase()

}


@Serializable
data class CommandArgument(
    @JsonNames("key", "name", "label")
    val id: String,
    val type: Type?,
    val suggest: List<String>?,
    val restrict: Boolean = false,
    val optional: Boolean = true,
    val default: JsonPrimitive?,
    val incorrectMessage: String?
) {

    enum class Type {

        ANY,

        DECIMAL,

        INTEGER,

        BOOLEAN,

        PLAYER,

        WORLD

    }

}

internal object CommandArgumentSerializer : JsonTransformingSerializer<CommandArgument>(serializer()) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        return if (element is JsonPrimitive) {
            buildJsonObject {
                put("id", element.content)
                put("type", "ANY")
            }
        } else {
            element
        }
    }

}

internal val commandBindingNames = setOf("command", "commands", "cmd", "cmds")

internal fun commandBindingValue(bindings: JsonObject?): JsonElement? {
    val values = bindings?.entries.orEmpty().filter { it.key.lowercase() in commandBindingNames }
        .flatMap { (_, value) -> if (value is JsonArray) value.toList() else listOf(value) }
        .filterNot { it is JsonPrimitive && it.contentOrNull == null }
    return if (values.isEmpty()) null else JsonArray(values)
}

internal fun parseCommandBindings(value: JsonElement, json: Json): List<CommandStructure> {
    val values = if (value is JsonArray) value.toList() else listOf(value)
    val labels = mutableSetOf<String>()
    return values.filterNot { it is JsonPrimitive && it.contentOrNull == null }.map { element ->
        val input = if (element is JsonPrimitive) buildJsonObject { put("name", element.content) } else element
        val parsed = json.decodeFromJsonElement<CommandStructure>(input)
        val normalized = parsed.copy(
            rawName = parsed.name,
            aliases = parsed.aliases.orEmpty().map { it.lowercase() }.distinct().sorted().filter { it != parsed.name },
            description = parsed.description.orEmpty(), usage = parsed.usage.orEmpty(),
            permission = parsed.permission.orEmpty(), permissionMessage = parsed.permissionMessage.orEmpty(),
            arguments = parsed.arguments.orEmpty().map { it.copy(
                type = it.type ?: CommandArgument.Type.ANY, suggest = it.suggest.orEmpty(),
                incorrectMessage = it.incorrectMessage.orEmpty()
            ) }
        )
        val argumentIds = normalized.arguments.orEmpty().map { it.id }
        require(argumentIds.all { it.isNotBlank() } && argumentIds.distinct().size == argumentIds.size) {
            "Blank or duplicate argument IDs in command ${normalized.name}"
        }
        (listOf(normalized.name) + normalized.aliases.orEmpty()).forEach { label ->
            require(label.isNotBlank() && label.none(Char::isWhitespace)) { "Invalid command label: '$label'" }
            require(labels.add(label)) { "Duplicate menu command label: $label" }
        }
        normalized
    }
}


internal fun CommandStructure.minimumArguments(): Int = arguments.orEmpty().indexOfLast { !it.optional } + 1

/** 读取命令参数并补全默认值。 */
internal fun commandVariables(arguments: List<CommandArgument>, read: (String) -> String?): Map<String, Any> = buildMap {
    arguments.forEach { argument ->
        val value = read(argument.id) ?: argument.default?.content
        require(value != null || argument.optional) { "Missing required argument: ${argument.id}" }
        if (value != null) put(argument.id, value)
    }
}
