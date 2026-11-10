package org.phalanx.android.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ClaudeEventTest {
    @Test
    fun `the request carries the two messages, streamed, and nothing about who sends them`() {
        val body = Json.parseToJsonElement(claudeRequest(Prompt("Sum it up.", "Ana: hi"))).jsonObject
        assertEquals(setOf("model", "max_tokens", "stream", "system", "messages", "output_config"), body.keys)
        assertEquals("claude-haiku-5-5", body.getValue("model").jsonPrimitive.content)
        assertEquals("Sum it up.", body.getValue("system").jsonPrimitive.content)
        assertEquals("true", body.getValue("stream").jsonPrimitive.content)
        val message = body.getValue("messages").jsonArray.single().jsonObject
        assertEquals("user", message.getValue("role").jsonPrimitive.content)
        assertEquals("Ana: hi", message.getValue("content").jsonPrimitive.content)
        assertFalse(body.getValue("output_config").jsonObject.containsKey("format"))
    }

    @Test
    fun `a prompt with a schema holds the reply to it`() {
        val schema = buildJsonObject { put("type", "object") }
        val request = claudeRequest(Prompt("s", "u", schema = schema, schemaRule = "r"))
        val body = Json.parseToJsonElement(request).jsonObject
        val format = body.getValue("output_config").jsonObject.getValue("format").jsonObject
        assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
        assertEquals(schema, format.getValue("schema"))
        assertEquals("s\nr", body.getValue("system").jsonPrimitive.content)
    }

    @Test
    fun `a rule for the schema is left out without one`() {
        val body = Json.parseToJsonElement(claudeRequest(Prompt("s", "u", schemaRule = "r"))).jsonObject
        assertEquals("s", body.getValue("system").jsonPrimitive.content)
    }

    @Test
    fun `text arrives piece by piece`() = assertEquals(
        ClaudeEvent.Text("Bonjour"),
        claudeEventOf(
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Bonjour"}}"""
        )
    )

    @Test
    fun `thinking and the lines that carry no data are passed over`() {
        assertNull(claudeEventOf("event: content_block_delta"))
        assertNull(claudeEventOf(""))
        assertNull(claudeEventOf("data: not json"))
        assertNull(
            claudeEventOf("""data: {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"x"}}""")
        )
        assertNull(claudeEventOf("""data: {"type":"message_stop"}"""))
    }

    @Test
    fun `the end of the reply says why it stopped`() {
        assertEquals(
            ClaudeEvent.Stopped("end_turn"),
            claudeEventOf("""data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{}}""")
        )
        assertEquals(
            ClaudeEvent.Stopped("refusal"),
            claudeEventOf("""data: {"type":"message_delta","delta":{"stop_reason":"refusal"}}""")
        )
    }

    @Test
    fun `only a whole reply is kept`() {
        assertNull(ClaudeEvent.Stopped("end_turn").failure())
        assertNull(ClaudeEvent.Text("a").failure())
        assertEquals("max_tokens", ClaudeEvent.Stopped("max_tokens").failure()?.message)
        assertEquals("refusal", ClaudeEvent.Stopped("refusal").failure()?.message)
        assertEquals("overloaded_error", ClaudeEvent.Failed("overloaded_error").failure()?.message)
    }

    @Test
    fun `an error midway is told by its type`() = assertEquals(
        ClaudeEvent.Failed("overloaded_error"),
        claudeEventOf("""data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
    )
}
